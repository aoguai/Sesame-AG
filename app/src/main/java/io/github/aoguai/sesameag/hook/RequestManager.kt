package io.github.aoguai.sesameag.hook

import android.Manifest
import androidx.annotation.RequiresPermission
import io.github.aoguai.sesameag.entity.RpcEntity
import io.github.aoguai.sesameag.hook.rpc.bridge.RpcBridge
import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.util.CoroutineUtils
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.NetworkUtils
import io.github.aoguai.sesameag.util.Notify
import io.github.aoguai.sesameag.util.RpcCache
import io.github.aoguai.sesameag.util.TimeUtil
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * RPC 请求管理器 (带熔断与兜底机制)
 */
object RequestManager {

    private const val TAG = "RequestManager"
    // reOpenApp() 会通过 SmartScheduler 延迟 20s 拉起 Activity。
    // 若恢复冷却时间小于 20s，会导致“每 15s 触发一次恢复 -> 取消并重新调度 20s 任务”，
    // 从而永远无法真正执行 reOpenApp（日志表现为反复 RPC 拦截 + 20s 延迟，直到用户手动重启 App）。
    private const val OFFLINE_RECOVERY_COOLDOWN_MS = 25_000L
    private const val RPC_BRIDGE_NULL_LOG_INTERVAL_MS: Long = 5_000L
    private const val RPC_BLOCKED_LOG_INTERVAL_MS: Long = 5_000L

    // 连续失败计数器
    private val errorCount = AtomicInteger(0)

    private val lastRpcBridgeNullLogAtMs = AtomicLong(0)
    private val lastRpcBlockedLogAtMs = AtomicLong(0)

    private val rpcRequestCount = AtomicLong(0)
    private val rpcBlockedCount = AtomicLong(0)
    private val rpcBridgeNullCount = AtomicLong(0)
    private val inFlightCreatedCount = AtomicLong(0)
    private val inFlightJoinedCount = AtomicLong(0)
    private val rpcCacheHitCount = AtomicLong(0)
    private val rpcCacheMissCount = AtomicLong(0)

    private sealed class RpcResult {
        data class Success(val body: String) : RpcResult()
        data class Failure(val reason: String) : RpcResult()
    }

    private val inFlight = ConcurrentHashMap<String, FutureTask<RpcResult>>()

    @Volatile
    private var lastOfflineRecoveryTime = 0L

    private fun shouldLogRpcBridgeNull(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRpcBridgeNullLogAtMs.get()
        return if (last == 0L || now - last >= RPC_BRIDGE_NULL_LOG_INTERVAL_MS) {
            lastRpcBridgeNullLogAtMs.set(now)
            true
        } else {
            false
        }
    }

    private fun shouldLogRpcBlocked(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRpcBlockedLogAtMs.get()
        return if (last == 0L || now - last >= RPC_BLOCKED_LOG_INTERVAL_MS) {
            lastRpcBlockedLogAtMs.set(now)
            true
        } else {
            false
        }
    }

    private fun shouldUseRpcCache(method: String?): Boolean {
        val m = method?.lowercase() ?: return false
        val allow = m.contains("query") || m.contains("list") || m.contains("get")
        if (!allow) return false

        val deny = m.contains("send") ||
            m.contains("finish") ||
            m.contains("receive") ||
            m.contains("draw") ||
            m.contains("exchange") ||
            m.contains("apply") ||
            m.contains("submit") ||
            m.contains("sign") ||
            m.contains("use")
        return !deny
    }

    private fun buildCacheKeyData(data: String?, relation: String?): String? {
        if (data == null) return null
        if (relation.isNullOrEmpty()) return data
        return data + "\u0001rel=" + relation
    }

    private fun generateKey(method: String?, data: String?): String? {
        if (method.isNullOrBlank()) return null
        val dataHash = data?.hashCode() ?: 0
        return "${method}_${dataHash}"
    }

    private fun requestWithInFlight(key: String, supplier: () -> RpcResult): RpcResult {
        val task = FutureTask(Callable { supplier() })
        val existing = inFlight.putIfAbsent(key, task)
        val toWait = existing ?: task
        if (existing == null) {
            inFlightCreatedCount.incrementAndGet()
            Log.runtime(TAG, "in-flight create: key=$key")
            try {
                task.run()
            } finally {
                inFlight.remove(key, task)
            }
        } else {
            inFlightJoinedCount.incrementAndGet()
            Log.runtime(TAG, "in-flight join: key=$key")
        }

        return try {
            toWait.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            RpcResult.Failure("Interrupted")
        } catch (_: ExecutionException) {
            RpcResult.Failure("ExecutionException")
        } catch (_: Throwable) {
            RpcResult.Failure("Throwable")
        }
    }

    private fun buildFallbackJson(reason: String, method: String?): String {
        val message = "$reason，请稍后再试"
        return try {
            JSONObject().apply {
                put("success", false)
                put("memo", message)
                put("resultDesc", message)
                put("desc", message)
                put("resultCode", "I07")
                if (!method.isNullOrBlank()) {
                    put("rpcMethod", method)
                }
            }.toString()
        } catch (_: Throwable) {
            """{"success":false,"memo":"$message","resultDesc":"$message","desc":"$message","resultCode":"I07"}"""
        }
    }

    /**
     * 核心执行函数 (内联优化)
     * 流程：离线检查 -> 获取 Bridge -> 执行请求 -> 结果校验 -> 错误计数/重置
     */
    private fun tryBlockByOffline(methodLog: String?): RpcResult.Failure? {
        if (!ApplicationHookConstants.shouldBlockRpc()) return null

        rpcBlockedCount.incrementAndGet()

        if (shouldLogRpcBlocked()) {
            val untilMs = ApplicationHookConstants.offlineUntilMs
            val remainMs = if (untilMs > 0L) {
                (untilMs - System.currentTimeMillis()).coerceAtLeast(0L)
            } else {
                -1L
            }
            val reason = ApplicationHookConstants.offlineReason
            val detail = ApplicationHookConstants.offlineReasonDetail

            Log.record(
                TAG,
                "RPC 被离线拦截: $methodLog | remainMs=$remainMs untilMs=$untilMs reason=${reason ?: "null"} detail=${detail ?: "null"}"
            )
            ModuleStatusReporter.requestUpdate(reason = "rpc_blocked")
        }

        handleOfflineRecovery()
        return RpcResult.Failure("离线模式")
    }

    private fun normalizeTryCount(value: Int): Int = value.coerceAtLeast(1)

    private inline fun executeRpcOnce(methodLog: String?, block: (RpcBridge) -> String?): RpcResult {
        val blocked = tryBlockByOffline(methodLog)
        if (blocked != null) return blocked

        // 2. 获取 Bridge (包含网络检查)
        // 如果这里获取失败，也视为一次错误
        val bridge = getRpcBridge()
        if (bridge == null) {
            rpcBridgeNullCount.incrementAndGet()
            if (shouldLogRpcBridgeNull()) {
                Log.record(TAG, "RpcBridge 不可用: $methodLog")
                ModuleStatusReporter.requestUpdate(reason = "rpc_bridge_null")
            }
            handleFailure(methodLog ?: "Network/Bridge Unavailable", "网络或Bridge不可用")
            return RpcResult.Failure("网络或Bridge不可用")
        }

        // 3. 执行请求
        val result = try {
            block(bridge)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "RPC 执行异常: $methodLog", e)
            null // 异常视为 null，触发失败逻辑
        }

        // 4. 结果校验与状态维护
        if (result.isNullOrBlank()) {
            // 失败：增加计数，检查兜底
            handleFailure(methodLog ?: "Unknown", "返回数据为空")
            return RpcResult.Failure("返回数据为空")
        } else {
            // 成功：重置计数器
            if (errorCount.get() > 0) {
                errorCount.set(0)
                Log.record(TAG, "RPC 恢复正常，错误计数重置")
            }
            return RpcResult.Success(result)
        }
    }

    /**
     * 处理失败逻辑：计数、报警、熔断
     */
    private fun handleFailure(method: String, reason: String) {
        val currentCount = errorCount.incrementAndGet()
        // 假设 BaseModel 有个方法获取这个配置，或者直接用常量
        val maxCount = BaseModel.setMaxErrorCount.value ?: 8

        Log.error(TAG, "RPC 失败 ($currentCount/$maxCount) | Method: $method | Reason: $reason")

        // 触发兜底阈值
        if (currentCount >= maxCount) {
            Log.record(TAG, "🔴 连续失败次数达到阈值，触发熔断兜底机制！")
            // 1. 设置离线状态，停止后续任务
            ApplicationHookConstants.setOffline(
                true,
                "rpc_error_threshold",
                "method=$method current=$currentCount threshold=$maxCount reason=$reason"
            )
            // 2. 发送通知 (根据用户配置)
            if (BaseModel.errNotify.value == true) {
                val msg = "${TimeUtil.getTimeStr()} | 网络异常次数超过阈值[$maxCount]"
                Notify.sendNewNotification(msg, "RPC 连续失败，脚本已暂停")
            }
            // 3. 立即尝试一次恢复
            handleOfflineRecovery()
        }
    }

    /**
     * 处理离线恢复逻辑
     * 可以是发送广播、拉起 App 等
     */
    private fun handleOfflineRecovery() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastOfflineRecoveryTime
        if (elapsed in 0 until OFFLINE_RECOVERY_COOLDOWN_MS) {
            Log.record(TAG, "离线恢复冷却中，跳过恢复（${elapsed}ms < ${OFFLINE_RECOVERY_COOLDOWN_MS}ms）")
            return
        }
        lastOfflineRecoveryTime = now

        Log.record(TAG, "正在尝试执行离线恢复策略...")
        // 策略 A: 重新拉起 App (推荐)
        ApplicationHook.reOpenApp()
        // 策略 B: 发送重登录广播 (如果宿主还能响应广播)
        // ApplicationHook.reLoginByBroadcast()
    }

    /**
     * 获取 RpcBridge 实例
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getRpcBridge(): RpcBridge? {
        if (!NetworkUtils.isNetworkAvailable()) {
            Log.record(TAG, "网络不可用，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            if (!NetworkUtils.isNetworkAvailable()) {
                return null
            }
        }

        var bridge = ApplicationHook.rpcBridge
        if (bridge == null) {
            Log.record(TAG, "RpcBridge 未初始化，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            bridge = ApplicationHook.rpcBridge
        }

        return bridge
    }

    @JvmStatic
    fun getMetricsSnapshot(): Map<String, Any?> {
        return linkedMapOf(
            "requestCount" to rpcRequestCount.get(),
            "blockedCount" to rpcBlockedCount.get(),
            "bridgeNullCount" to rpcBridgeNullCount.get(),
            "errorCount" to errorCount.get(),
            "inFlightCurrent" to inFlight.size,
            "inFlightCreated" to inFlightCreatedCount.get(),
            "inFlightJoined" to inFlightJoinedCount.get(),
            "cacheHit" to rpcCacheHitCount.get(),
            "cacheMiss" to rpcCacheMissCount.get()
        )
    }

    private inline fun requestStringWithPolicy(
        method: String?,
        cacheKeyData: String?,
        tryCount: Int,
        retryInterval: Int,
        crossinline block: (RpcBridge, Int, Int) -> String?
    ): String {
        rpcRequestCount.incrementAndGet()

        // 离线优先：不允许 cache 绕过 offline（避免“脚本暂停”时仍继续执行业务）
        val blocked = tryBlockByOffline(method)
        if (blocked != null) {
            return buildFallbackJson(blocked.reason, method)
        }

        val normalizedTryCount = normalizeTryCount(tryCount)

        if (shouldUseRpcCache(method) && cacheKeyData != null) {
            val cached = RpcCache.get(method, cacheKeyData)
            if (cached != null) {
                rpcCacheHitCount.incrementAndGet()
                Log.runtime(TAG, "rpc cache hit: method=$method")
                return cached
            }
            rpcCacheMissCount.incrementAndGet()
            Log.runtime(TAG, "rpc cache miss: method=$method")

            val key = generateKey(method, cacheKeyData)
            if (key != null) {
                val inFlightResult = requestWithInFlight(key) {
                    val outcome = executeRpcOnce(method) { bridge ->
                        block(bridge, normalizedTryCount, retryInterval)
                    }
                    if (outcome is RpcResult.Success) {
                        RpcCache.put(method, cacheKeyData, outcome.body)
                    }
                    outcome
                }

                return when (inFlightResult) {
                    is RpcResult.Success -> inFlightResult.body
                    is RpcResult.Failure -> buildFallbackJson(inFlightResult.reason, method)
                }
            }
        }

        val result = executeRpcOnce(method) { bridge ->
            block(bridge, normalizedTryCount, retryInterval)
        }
        return when (result) {
            is RpcResult.Success -> result.body
            is RpcResult.Failure -> buildFallbackJson(result.reason, method)
        }
    }

    // ================== 公开 API (保持不变) ==================

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity): String {
        val method = rpcEntity.requestMethod
        val cacheKeyData = buildCacheKeyData(rpcEntity.requestData, rpcEntity.requestRelation)
        return requestStringWithPolicy(method, cacheKeyData, RpcBridge.DEFAULT_TRY_COUNT, RpcBridge.DEFAULT_RETRY_INTERVAL) { bridge, tc, ri ->
            bridge.requestString(rpcEntity, tc, ri)
        }
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String {
        val method = rpcEntity.requestMethod
        val cacheKeyData = buildCacheKeyData(rpcEntity.requestData, rpcEntity.requestRelation)
        return requestStringWithPolicy(method, cacheKeyData, tryCount, retryInterval) { bridge, tc, ri ->
            bridge.requestString(rpcEntity, tc, ri)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?): String {
        val cacheKeyData = buildCacheKeyData(data, null)
        return requestStringWithPolicy(method, cacheKeyData, RpcBridge.DEFAULT_TRY_COUNT, RpcBridge.DEFAULT_RETRY_INTERVAL) { bridge, tc, ri ->
            bridge.requestString(method, data, tc, ri)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, relation: String?): String {
        val cacheKeyData = buildCacheKeyData(data, relation)
        return requestStringWithPolicy(method, cacheKeyData, RpcBridge.DEFAULT_TRY_COUNT, RpcBridge.DEFAULT_RETRY_INTERVAL) { bridge, tc, ri ->
            bridge.requestString(method, data, relation, tc, ri)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        appName: String?,
        methodName: String?,
        facadeName: String?
    ): String {
        val cacheKeyData = buildCacheKeyData(data, null)
        return requestStringWithPolicy(method, cacheKeyData, RpcBridge.DEFAULT_TRY_COUNT, RpcBridge.DEFAULT_RETRY_INTERVAL) { bridge, _, _ ->
            bridge.requestString(method, data, appName, methodName, facadeName)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String {
        val cacheKeyData = buildCacheKeyData(data, null)
        return requestStringWithPolicy(method, cacheKeyData, tryCount, retryInterval) { bridge, tc, ri ->
            bridge.requestString(method, data, tc, ri)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        relation: String?,
        tryCount: Int,
        retryInterval: Int
    ): String {
        val cacheKeyData = buildCacheKeyData(data, relation)
        return requestStringWithPolicy(method, cacheKeyData, tryCount, retryInterval) { bridge, tc, ri ->
            bridge.requestString(method, data, relation, tc, ri)
        }
    }

    @JvmStatic
    fun requestObject(rpcEntity: RpcEntity?, tryCount: Int, retryInterval: Int) {
        if (rpcEntity == null) return
        // requestObject 不涉及返回值判断，但同样需要离线检查
        if (ApplicationHookConstants.shouldBlockRpc()) {
            handleOfflineRecovery()
            return
        }

        val bridge = getRpcBridge()
        if (bridge == null) {
            handleFailure("requestObject", "Bridge Unavailable")
            return
        }

        try {
            bridge.requestObject(rpcEntity, normalizeTryCount(tryCount), retryInterval)
            // requestObject 没有返回值，假设只要不抛异常就算成功？
            // 或者保守一点，不重置 errorCount，也不增加 errorCount
            errorCount.set(0)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "requestObject 异常: ${rpcEntity.methodName}", e)
            handleFailure(rpcEntity.methodName ?: "Unknown", "Exception")
        }
    }
}


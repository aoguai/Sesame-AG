package fansirsqi.xposed.sesame.hook

import android.Manifest
import androidx.annotation.RequiresPermission
import fansirsqi.xposed.sesame.entity.RpcEntity
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.NetworkUtils
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.TimeUtil
import java.util.concurrent.atomic.AtomicInteger

/**
 * RPC 请求管理器 (带熔断与兜底机制)
 */
object RequestManager {

    private const val TAG = "RequestManager"

    // 连续失败计数器
    private val errorCount = AtomicInteger(0)

    /**
     * 核心执行函数 (内联优化)
     * 流程：离线检查 -> 获取 Bridge -> 执行请求 -> 结果校验 -> 错误计数/重置
     */
    private inline fun executeRpc(methodLog: String?, block: (RpcBridge) -> String?): String {
        // 1. 【前置检查】如果已经离线，直接中断并尝试恢复
        if (ApplicationHook.offline) {
            Log.record(TAG, "当前处于离线状态，拦截请求: $methodLog")
            handleOfflineRecovery()
            return ""
        }

        // 2. 获取 Bridge (包含网络检查)
        // 如果这里获取失败，也视为一次错误
        val bridge = getRpcBridge()
        if (bridge == null) {
            handleFailure("Network/Bridge Unavailable", "网络或Bridge不可用")
            return ""
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
            return ""
        } else {
            // 成功：重置计数器
            if (errorCount.get() > 0) {
                errorCount.set(0)
                Log.record(TAG, "RPC 恢复正常，错误计数重置")
            }
            return result
        }
    }

    /**
     * 处理失败逻辑：计数、报警、熔断
     */
    private fun handleFailure(method: String, reason: String) {
        val currentCount = errorCount.incrementAndGet()
        // 假设 BaseModel 有个方法获取这个配置，或者直接用常量
        val maxCount = BaseModel.setMaxErrorCount.value

        Log.error(TAG, "RPC 失败 ($currentCount/$maxCount) | Method: $method | Reason: $reason")

        // 触发兜底阈值
        if (currentCount >= maxCount) {
            Log.record(TAG, "🔴 连续失败次数达到阈值，触发熔断兜底机制！")
            // 1. 设置离线状态，停止后续任务
            ApplicationHook.setOffline(true)
            // 2. 发送通知 (根据用户配置)
            if (BaseModel.errNotify.value) {
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
        // 防止短时间内频繁触发恢复逻辑 (可选)
        // 这里简单实现：尝试拉起支付宝或发送重登录广播

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

    // ================== 公开 API (保持不变) ==================

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, 3, 1200)
        }
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, relation: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation)
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
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, appName, methodName, facadeName)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, tryCount, retryInterval)
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
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestObject(rpcEntity: RpcEntity?, tryCount: Int, retryInterval: Int) {
        if (rpcEntity == null) return
        // requestObject 不涉及返回值判断，但同样需要离线检查
        if (ApplicationHook.offline) {
            handleOfflineRecovery()
            return
        }

        val bridge = getRpcBridge()
        if (bridge == null) {
            handleFailure("requestObject", "Bridge Unavailable")
            return
        }

        try {
            bridge.requestObject(rpcEntity, tryCount, retryInterval)
            // requestObject 没有返回值，假设只要不抛异常就算成功？
            // 或者保守一点，不重置 errorCount，也不增加 errorCount
            errorCount.set(0)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "requestObject 异常: ${rpcEntity.methodName}", e)
            handleFailure(rpcEntity.methodName ?: "Unknown", "Exception")
        }
    }
}
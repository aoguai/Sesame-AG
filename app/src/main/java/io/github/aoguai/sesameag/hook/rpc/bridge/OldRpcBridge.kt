package io.github.aoguai.sesameag.hook.rpc.bridge

import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.data.RuntimeInfo
import io.github.aoguai.sesameag.entity.RpcEntity
import io.github.aoguai.sesameag.hook.rpc.intervallimit.RpcIntervalLimit
import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.Notify
import io.github.aoguai.sesameag.util.RandomUtil
import io.github.aoguai.sesameag.util.CoroutineUtils
import io.github.aoguai.sesameag.util.StringUtil
import io.github.aoguai.sesameag.util.TimeUtil
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * 旧版RPC桥接实现
 */
class OldRpcBridge : RpcBridge {
    private var loader: ClassLoader? = null
    private var h5PageClazz: Class<*>? = null
    private var rpcCallMethod: Method? = null
    private var getResponseMethod: Method? = null
    private var curH5PageImpl: Any? = null

    private fun computeRetryDelayMs(retryInterval: Int, attempt: Int): Long {
        val baseMs = when {
            retryInterval > 0 -> retryInterval.toLong()
            else -> 600L
        } + RandomUtil.delay().toLong()

        val attemptExp = (attempt - 1).coerceAtLeast(0).coerceAtMost(4)
        val factor = 1L shl attemptExp
        return minOf(baseMs * factor, 15000L)
    }

    override fun getVersion(): RpcVersion = RpcVersion.OLD

    /**
     * 加载RPC所需的类和方法
     */
    override fun load() {
        loader = io.github.aoguai.sesameag.hook.ApplicationHook.classLoader
        val classLoader = loader ?: run {
            Log.error(TAG, "ClassLoader为null，无法加载OldRpcBridge")
            return
        }
        try {
            h5PageClazz = classLoader.loadClass(General.H5PAGE_NAME)
            Log.runtime(TAG, "RPC 类加载成功")
            loadRpcMethods()
        } catch (e: ClassNotFoundException) {
            Log.runtime(TAG, "加载 RPC 类时出错：")
            Log.printStackTrace(TAG, e)
            throw RuntimeException(e)
        } catch (t: Throwable) {
            Log.runtime(TAG, "加载 RPC 类时发生意外错误：")
            Log.printStackTrace(TAG, t)
            throw t
        }
    }

    /**
     * 使用反射加载RPC方法
     */
    private fun loadRpcMethods() {
        if (rpcCallMethod == null) {
            val classLoader = loader ?: return
            try {
                val rpcUtilClass = classLoader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil")
                val responseClass = classLoader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5Response")
                rpcCallMethod = rpcUtilClass.getMethod(
                    "rpcCall",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    classLoader.loadClass(General.JSON_OBJECT_NAME),
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    h5PageClazz,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                getResponseMethod = responseClass.getMethod("getResponse")
                Log.runtime(TAG, "RPC 调用方法加载成功")
            } catch (e: Exception) {
                Log.runtime(TAG, "加载 RPC 调用方法时出错：")
                Log.printStackTrace(TAG, e)
            }
        }
    }

    override fun unload() {
        getResponseMethod = null
        rpcCallMethod = null
        h5PageClazz = null
        loader = null
    }

    /**
     * 向RPC实体请求字符串响应
     *
     * @param rpcEntity 要发送的RPC实体
     * @param tryCount 重试次数
     * @param retryInterval 重试间隔
     * @return 响应字符串，如果失败则返回null
     */
    override fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String? {
        val responseEntity = requestObject(rpcEntity, tryCount, retryInterval)
        return responseEntity?.responseString
    }

    override fun requestObject(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): RpcEntity? {
        if (io.github.aoguai.sesameag.hook.ApplicationHookConstants.shouldBlockRpc()) {
            return null
        }

        val id = rpcEntity.hashCode()
        val method = rpcEntity.requestMethod
        val args = rpcEntity.requestData

        val rpcMethod = method ?: return null
        val normalizedTryCount = tryCount.coerceAtLeast(1)
        repeat(normalizedTryCount) {
            try {
                RpcIntervalLimit.enterIntervalLimit(rpcMethod)
                val response = invokeRpcCall(method, args)
                return processResponse(rpcEntity, response, id, method, args, retryInterval)
            } catch (t: Throwable) {
                handleError(rpcEntity, t, method, id, args)
                if (it < normalizedTryCount - 1) {
                    CoroutineUtils.sleepCompat(computeRetryDelayMs(retryInterval, it + 1))
                }
            }
        }
        return null
    }

    /**
     * 使用反射调用RPC方法
     *
     * @param method 请求的方法名
     * @param args 请求的参数
     * @return 响应对象
     * @throws Throwable 如果调用过程中出现错误
     */
    @Throws(Throwable::class)
    private fun invokeRpcCall(method: String?, args: String?): Any? {
        val callMethod = rpcCallMethod ?: throw IllegalStateException("rpcCallMethod未初始化")
        return if (callMethod.parameterTypes.size == 12) {
            callMethod.invoke(
                null, method, args, "", true, null, null, 
                false, curH5PageImpl, 0, "", false, -1
            )
        } else {
            callMethod.invoke(
                null, method, args, "", true, null, null, 
                false, curH5PageImpl, 0, "", false, -1, ""
            )
        }
    }

    /**
     * 处理RPC响应
     *
     * @param rpcEntity 要更新的RPC实体
     * @param response 响应对象
     * @param id 唯一请求ID
     * @param method 请求的方法名
     * @param args 请求的参数
     * @param retryInterval 重试间隔
     * @return 更新后的RPC实体
     * @throws Throwable 如果处理过程中出现错误
     */
    @Throws(Throwable::class)
    private fun processResponse(
        rpcEntity: RpcEntity,
        response: Any?,
        id: Int,
        method: String?,
        args: String?,
        retryInterval: Int
    ): RpcEntity? {
        val getMethod = getResponseMethod ?: throw IllegalStateException("getResponseMethod未初始化")
        val resultStr = getMethod.invoke(response) as? String ?: return null
        val resultObject = JSONObject(resultStr)
        rpcEntity.setResponseObject(resultObject, resultStr)

        // 检查响应中的"memo"字段是否包含"系统繁忙"
        if (resultObject.optString("memo", "").contains("系统繁忙")) {
            val cooldownMs = io.github.aoguai.sesameag.hook.ApplicationHookConstants.getOfflineCooldownMs()
            io.github.aoguai.sesameag.hook.ApplicationHookConstants.enterOffline(
                cooldownMs,
                "system_busy",
                "memo contains 系统繁忙"
            )
            Notify.updateStatusText("系统繁忙，可能需要滑动验证")
            Log.record(TAG, "系统繁忙，可能需要滑动验证")
            return null
        }

        if (!resultObject.optBoolean("success")) {
            rpcEntity.setError()
            Log.error(
                TAG,
                "旧 RPC 响应 | id: $id | method: $method args: $args | data: ${rpcEntity.responseString}"
            )
        }
        return rpcEntity
    }

    /**
     * 处理RPC请求过程中发生的错误
     *
     * @param rpcEntity 要更新的RPC实体
     * @param t 发生的异常
     * @param method 请求的方法名
     * @param id 唯一请求ID
     * @param args 请求的参数
     */
    private fun handleError(rpcEntity: RpcEntity, t: Throwable, method: String?, id: Int, args: String?) {
        rpcEntity.setError()
        Log.error(TAG, "旧 RPC 请求 | id: $id | method: $method err:")
        Log.printStackTrace(t)

        if (t is InvocationTargetException) {
            handleInvocationException(rpcEntity, t, method)
        }
    }

    /**
     * 处理调用过程中的特定异常
     *
     * @param rpcEntity 要更新的RPC实体
     * @param e 发生的InvocationTargetException
     * @param method 请求的方法名
     */
    private fun handleInvocationException(rpcEntity: RpcEntity, e: InvocationTargetException, method: String?) {
        val cause = e.cause
        if (cause != null) {
            val msg = cause.message
            if (!msg.isNullOrEmpty()) {
                handleErrorMessage(rpcEntity, msg, method)
            }
        }
    }

    /**
     * 处理特定的错误消息，并根据内容执行相应的操作
     *
     * @param rpcEntity 要更新的RPC实体
     * @param msg 错误消息
     * @param method 请求的方法名
     */
    private fun handleErrorMessage(rpcEntity: RpcEntity, msg: String, method: String?) {
        when {
            msg.contains("登录超时") -> handleLoginTimeout()
            msg.contains("[1004]") && method == "alipay.antmember.forest.h5.collectEnergy" -> 
                handleEnergyCollectException()
            msg.contains("MMTPException") -> handleException(rpcEntity)
        }
    }

    /**
     * 处理登录超时的情况
     */
    private fun handleLoginTimeout() {
        if (!io.github.aoguai.sesameag.hook.ApplicationHookConstants.offline) {
            val cooldownMs = io.github.aoguai.sesameag.hook.ApplicationHookConstants.getOfflineCooldownMs()
            io.github.aoguai.sesameag.hook.ApplicationHookConstants.enterOffline(
                cooldownMs,
                "login_timeout",
                "旧RPC: 登录超时"
            )
            Notify.updateStatusText("登录超时")
            if (BaseModel.timeoutRestart.value == true) {
                Log.record(TAG, "尝试重新登录")
                io.github.aoguai.sesameag.hook.ApplicationHookUtils.reLoginByBroadcast()
            }
        }
    }

    /**
     * 处理能量收集异常的情况
     */
    private fun handleEnergyCollectException() {
        val waitValue = BaseModel.waitWhenException.value ?: 0
        if (waitValue > 0) {
            val waitTime = System.currentTimeMillis() + waitValue
            RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime)
            Notify.updateStatusText("异常")
            Log.record(TAG, "触发异常, 等待至${TimeUtil.getCommonDate(waitTime)}")
        }
    }

    /**
     * 处理MTP异常的情况
     *
     * @param rpcEntity 要更新的RPC实体
     */
    private fun handleException(rpcEntity: RpcEntity) {
        try {
            val jo = JSONObject().apply {
                put("resultCode", "FAIL")
                put("memo", "MMTPException")
                put("resultDesc", "MMTPException")
            }
            val jsonString = jo.toString()
            rpcEntity.setResponseObject(JSONObject(jsonString), jsonString)
        } catch (e: JSONException) {
            Log.printStackTrace(e)
        }
    }

    companion object {
        private val TAG = OldRpcBridge::class.java.simpleName
    }
}


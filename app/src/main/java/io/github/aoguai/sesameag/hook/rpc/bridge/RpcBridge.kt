package io.github.aoguai.sesameag.hook.rpc.bridge

import io.github.aoguai.sesameag.entity.RpcEntity

/**
 * RPC桥接接口
 */
interface RpcBridge {
    companion object {
        const val DEFAULT_TRY_COUNT: Int = 3
        const val DEFAULT_RETRY_INTERVAL: Int = -1
    }

    fun getVersion(): RpcVersion
    
    @Throws(Exception::class)
    fun load()
    
    fun unload()

    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String?
    fun requestObject(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): RpcEntity?

    /**
     * 发送RPC请求并获取响应字符串（使用默认重试参数）
     */
    fun requestString(rpcEntity: RpcEntity): String? = requestString(rpcEntity, DEFAULT_TRY_COUNT, DEFAULT_RETRY_INTERVAL)

    /**
     * 发送RPC请求并获取响应字符串（使用默认重试参数）
     *
     * @param method RPC方法名
     * @param data 请求数据
     * @return 响应字符串，如果请求失败则返回null
     */
    fun requestString(method: String?, data: String?): String? {
        if (method == null || data == null) return null
        return requestString(method, data, DEFAULT_TRY_COUNT, DEFAULT_RETRY_INTERVAL)
    }

    /**
     * 发送带关联数据的RPC请求并获取响应字符串（使用默认重试参数）
     *
     * @param method RPC方法名
     * @param data 请求数据
     * @param relation 关联数据
     * @return 响应字符串，如果请求失败则返回null
     */
    fun requestString(method: String?, data: String?, relation: String?): String? {
        if (method == null || data == null) return null
        return requestString(method, data, relation ?: "", DEFAULT_TRY_COUNT, DEFAULT_RETRY_INTERVAL)
    }

    fun requestString(method: String?, data: String?, appName: String?, methodName: String?, facadeName: String?): String? {
        if (method == null || data == null) return null
        return requestString(RpcEntity(method, data, appName, methodName, facadeName), DEFAULT_TRY_COUNT, DEFAULT_RETRY_INTERVAL)
    }

    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String? {
        if (method == null || data == null) return null
        return requestString(RpcEntity(method, data), tryCount, retryInterval)
    }

    fun requestString(method: String?, data: String?, relation: String?, tryCount: Int, retryInterval: Int): String? {
        if (method == null || data == null) return null
        return requestString(RpcEntity(method, data, relation), tryCount, retryInterval)
    }

    fun requestObject(method: String?, data: String?, relation: String?): RpcEntity? {
        if (method == null || data == null) return null
        return requestObject(method, data, relation ?: "", DEFAULT_TRY_COUNT, DEFAULT_RETRY_INTERVAL)
    }

    fun requestObject(method: String?, data: String?, tryCount: Int, retryInterval: Int): RpcEntity? {
        if (method == null || data == null) return null
        return requestObject(RpcEntity(method, data), tryCount, retryInterval)
    }

    fun requestObject(method: String?, data: String?, relation: String?, tryCount: Int, retryInterval: Int): RpcEntity? {
        if (method == null || data == null) return null
        return requestObject(RpcEntity(method, data, relation), tryCount, retryInterval)
    }
}


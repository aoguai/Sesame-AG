package io.github.aoguai.sesameag.hook.server

import io.github.aoguai.sesameag.util.Log
import fi.iki.elonen.NanoHTTPD

/**
 * HTTP 服务管理器 (单例)
 * 负责控制 Server 的启动和停止
 */
object ModuleHttpServerManager {
    private const val TAG = "ModuleHttpServerManager"
    // 持有 Server 实例
    private var server: ModuleHttpServer? = null

    /**
     * 启动服务 (如果尚未启动)
     */
    @Synchronized
    fun startIfNeeded(
        port: Int,
        secretToken: String,
        currentProcessName: String, // 当前进程名
        mainProcessName: String     // 主进程包名
    ) {
        // 1. 安全检查：仅允许在主进程启动，避免多个进程抢占端口
        if (currentProcessName != mainProcessName) {
            return
        }

        // 2. 如果已经运行，跳过
        if (server != null && server?.isAlive == true) {
            return
        }

        // 3. 启动逻辑
        try {
            stop() // 先尝试停止旧的（如果有）

            val newServer = ModuleHttpServer(port, secretToken)
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) // 启动 NanoHTTPD

            server = newServer
//            Log.record(TAG, "🚀 HTTP 服务已启动: http://127.0.0.1:$port")
//            Log.record(TAG, "🔑 Token: $secretToken")

        } catch (e: Exception) {
            Log.printStackTrace(TAG, "HTTP 服务启动失败", e)
        }
    }

    /**
     * 停止服务
     */
    @Synchronized
    fun stop() {
        try {
            server?.stop()
            server = null
            Log.record(TAG, "HTTP 服务已停止")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "停止服务异常", e)
        }
    }
}

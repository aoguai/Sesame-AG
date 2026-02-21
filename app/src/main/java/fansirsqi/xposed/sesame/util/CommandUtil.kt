package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import fansirsqi.xposed.sesame.IStatusListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 命令服务客户端工具类
 * 负责与 CommandService 建立连接并通过 AIDL 发送指令
 * 支持从宿主应用（目标应用）进程跨进程绑定到模块的 Service
 */
object CommandUtil {

    private const val TAG = "CommandUtil"
    private const val ACTION_BIND = "fansirsqi.xposed.sesame.action.BIND_COMMAND_SERVICE"
    private const val PACKAGE_NAME = "fansirsqi.xposed.sesame"

    private const val BIND_TIMEOUT_MS = 5000L      // 绑定超时时间
    private const val EXEC_TIMEOUT_MS = 15000L     // 命令执行超时时间

    // 全局协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- 状态定义 ---
    sealed class ServiceStatus {
        data object Loading : ServiceStatus()
        data class Active(val type: String) : ServiceStatus() // type = "Root" or "Shizuku"
        data object Inactive : ServiceStatus()
        data class Error(val msg: String) : ServiceStatus()
    }

    // 状态流 (UI 直接观察这个)
    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Loading)
    val serviceStatus = _serviceStatus.asStateFlow()

    // AIDL 接口实例
    private var commandService: ICommandService? = null

    // 连接状态管理
    private val bindMutex = Mutex()
    private val isBound = AtomicBoolean(false)
    private var connectionDeferred: CompletableDeferred<Boolean>? = null

    // --- 监听器实现 ---
    private val statusListener = object : IStatusListener.Stub() {
        override fun onStatusChanged(type: String) {
            Log.i(TAG, "收到服务端状态推送: $type")
            // 更新 Flow (StateFlow 是线程安全的)
            _serviceStatus.value = mapTypeToStatus(type)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "✅ CommandService 已连接: $name")
            try {
                commandService = ICommandService.Stub.asInterface(service)

                // 1. 监听服务端死亡
                service?.linkToDeath({
                    Log.w(TAG, "💀 CommandService 远程进程死亡")
                    handleServiceLost()
                }, 0)

                // 2. 🔥 核心：连接成功后，立即注册状态监听
                // 服务端会在注册时立即回调一次当前状态，所以不需要手动查询
                commandService?.registerListener(statusListener)

                isBound.set(true)
                connectionDeferred?.complete(true)
            } catch (e: Exception) {
                Log.e(TAG, "服务连接初始化失败", e)
                connectionDeferred?.complete(false)
                handleServiceLost()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "❌ CommandService 已断开连接: $name")
            handleServiceLost()
        }
    }

    private fun handleServiceLost() {
        commandService = null
        isBound.set(false)
        connectionDeferred = null
        _serviceStatus.value = ServiceStatus.Inactive // 更新状态为断开
    }

    private fun mapTypeToStatus(typeName: String): ServiceStatus {
        return when (typeName) {
            "SafeRootShell", "RootShell" -> ServiceStatus.Active("Root")
            "ShizukuShell" -> ServiceStatus.Active("Shizuku")
            "no_executor", "Unknown" -> ServiceStatus.Inactive
            else -> ServiceStatus.Inactive
        }
    }

    /**
     * 触发连接 (供 ViewModel 初始化调用)
     */
    fun connect(context: Context) {
        scope.launch {
            ensureServiceBound(context)
        }
    }

    /**
     * 绑定服务 (线程安全)
     */
    @SuppressLint("ObsoleteSdkInt")
    private suspend fun ensureServiceBound(context: Context): Boolean {
        if (isBound.get() && commandService?.asBinder()?.isBinderAlive == true) {
            return true
        }

        return bindMutex.withLock {
            if (isBound.get() && commandService?.asBinder()?.isBinderAlive == true) {
                return@withLock true
            }

            // 开始连接前，状态置为 Loading
            _serviceStatus.value = ServiceStatus.Loading

            handleServiceLost()
            connectionDeferred = CompletableDeferred()

            val intent = Intent().apply {
                action = ACTION_BIND
                setPackage(PACKAGE_NAME)
                component = ComponentName(PACKAGE_NAME, "fansirsqi.xposed.sesame.service.CommandService")
            }

            try {
                // 尝试启动服务
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.applicationContext.startForegroundService(intent)
                    } else {
                        context.applicationContext.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "startService 失败: ${e.message}")
                }

                delay(500) // 稍微缩短等待时间

                val bindResult = context.applicationContext.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                )

                if (!bindResult) {
                    Log.e(TAG, "❌ bindService 返回 false")
                    _serviceStatus.value = ServiceStatus.Error("服务绑定失败 (模块未激活?)")
                    return@withLock false
                }

                val success = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                    connectionDeferred?.await()
                } ?: false

                if (!success) {
                    Log.e(TAG, "❌ 绑定超时")
                    // 超时解绑
                    try { context.applicationContext.unbindService(serviceConnection) } catch (_: Exception) {}
                    _serviceStatus.value = ServiceStatus.Error("连接超时")
                }

                return@withLock success
            } catch (e: Exception) {
                Log.e(TAG, "绑定异常", e)
                _serviceStatus.value = ServiceStatus.Error(e.message ?: "未知错误")
                return@withLock false
            }
        }
    }

    /**
     * 执行命令
     */
    suspend fun executeCommand(context: Context, command: String): String? = withContext(Dispatchers.IO) {
        if (!ensureServiceBound(context)) {
            return@withContext null
        }

        val service = commandService ?: return@withContext null
        val resultDeferred = CompletableDeferred<String?>()

        val callback = object : ICallback.Stub() {
            override fun onSuccess(output: String) {
                resultDeferred.complete(output)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Cmd Error: $error")
                resultDeferred.complete(null)
            }
        }

        try {
            service.executeCommand(command, callback)
            withTimeoutOrNull(EXEC_TIMEOUT_MS) { resultDeferred.await() }
        } catch (e: RemoteException) {
            handleServiceLost()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Cmd Exception", e)
            null
        }
    }

    /**
     * 手动解绑服务
     */
    fun unbind(context: Context) {
        if (isBound.compareAndSet(true, false)) {
            try {
                // 尝试注销监听器 (忽略异常，因为服务可能已死)
                try { commandService?.unregisterListener(statusListener) } catch (_: Exception) {}

                context.applicationContext.unbindService(serviceConnection)
                Log.d(TAG, "已解绑服务")
            } catch (e: Exception) {
                Log.w(TAG, "解绑异常: ${e.message}")
            } finally {
                handleServiceLost()
            }
        }
    }
}

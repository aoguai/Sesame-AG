package fansirsqi.xposed.sesame.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import fansirsqi.xposed.sesame.ICallback
import fansirsqi.xposed.sesame.ICommandService
import fansirsqi.xposed.sesame.IStatusListener
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.ui.MainActivity
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * 命令执行服务（前台服务）
 * 负责通过 ShellManager 执行底层命令
 */
class CommandService : Service() {

    companion object {
        private const val TAG = "CommandService"
        private const val NOTIFICATION_ID = 1001

        // 统一 ID 和 名称
        private const val CHANNEL_ID = "SesameCommandChannel"
        private const val CHANNEL_NAME = "后台命令服务"
        private const val NOTIFICATION_TITLE = "后台命令服务"
        private const val NOTIFICATION_CONTENT = "服务正在运行，等待执行指令..."

        // 设置命令执行超时时间，例如 15 秒
        private const val COMMAND_TIMEOUT_MS = 15000L
    }

    /**
     * 用于管理跨进程回调的列表
     */
    private val listeners = RemoteCallbackList<IStatusListener>()

    // 使用 SupervisorJob，确保单个任务崩溃不影响整个作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandMutex = Mutex()

    // ShellManager 实例
    private var shellManager: ShellManager? = null

    private val binder = object : ICommandService.Stub() {
        override fun executeCommand(command: String, callback: ICallback?) {
            serviceScope.launch {
                commandMutex.withLock {
                    try {
                        ensureShellManager()
                        // 优化: 如果 ShellManager 依然没有 Shell，尝试重置一下（应对 Shizuku 刚授权的情况）
                        if (shellManager?.selectedName == "no_executor") {
                            shellManager?.reset()
                        }

                        // 执行
                        val result = withTimeout(COMMAND_TIMEOUT_MS) {
                            shellManager!!.exec(command)
                        }

                        if (result.isSuccess) {
                            safeCallbackSuccess(callback, result.stdout.trim())
                        } else {
                            // 优化错误信息返回，区分是 Shell 找不到还是命令执行错
                            val errorMsg = if (result.exitCode == -1 && result.stderr.contains("No valid")) {
                                "无 Root/Shizuku 权限"
                            } else {
                                "Code:${result.exitCode}, Err:${result.stderr}"
                            }
                            safeCallbackError(callback, errorMsg)
                        }
                    } catch (e: Exception) {
                        // ... 异常处理 ...
                        Log.e(TAG, "执行异常", e)
                        safeCallbackError(callback, e.message ?: "Service Error")
                    }
                }
            }
        }


        /**
         * 实现注册
         */
        override fun registerListener(listener: IStatusListener?) {
            listeners.register(listener)
            // 💡 注册时立即回调一次当前状态，防止客户端状态不同步
            listener?.onStatusChanged(shellManager?.selectedName)
        }

        /**
         * 实现注销
         */
        override fun unregisterListener(listener: IStatusListener?) {
            listeners.unregister(listener)
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "CommandService onCreate")
        // 立即启动前台服务，避免超时
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        // 延迟初始化 ShellManager（不阻塞前台服务启动）
        serviceScope.launch {
            try {
                ensureShellManager()
                shellManager?.onStateChanged = { newType ->
                    dispatchStatusChange(newType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ShellManager 初始化失败", e)
            }
        }
    }

    /**
     * 分发状态给所有客户端
     */
    private fun dispatchStatusChange(type: String) {
        val count = listeners.beginBroadcast()
        for (i in 0 until count) {
            try {
                listeners.getBroadcastItem(i).onStatusChanged(type)
            } catch (e: Exception) {
                // 客户端可能死掉了，RemoteCallbackList 会自动清理
            }
        }
        listeners.finishBroadcast()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "CommandService onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 如果服务被异常杀死，尝试重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
//        Log.d(TAG, "CommandService onDestroy")
        stopForeground(STOP_FOREGROUND_REMOVE)
        shellManager = null
        serviceScope.cancel() // 销毁时取消所有协程任务
    }

    /**
     * 确保 ShellManager 已初始化
     */
    private fun ensureShellManager() {
        if (shellManager == null) {
            try {
                shellManager = ShellManager(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "ShellManager init error", e)
            }
        }
    }

    /**
     * 安全回调 Success，处理 DeadObjectException
     */
    private fun safeCallbackSuccess(callback: ICallback?, result: String) {
        if (callback == null) return
        try {
            callback.onSuccess(result)
        } catch (e: RemoteException) {
            Log.w(TAG, "回调失败(客户端已死亡): ${e.message}")
        }
    }

    /**
     * 安全回调 Error，处理 DeadObjectException
     */
    private fun safeCallbackError(callback: ICallback?, error: String) {
        if (callback == null) return
        try {
            callback.onError(error)
        } catch (e: RemoteException) {
            Log.w(TAG, "回调失败(客户端已死亡): ${e.message}")
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 需要）
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // 低优先级，不发出声音
        ).apply {
            description = "用于维持后台命令执行服务的运行"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_CONTENT)
            .setSmallIcon(R.drawable.title_logo)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 禁止用户侧滑删除
            .build()
    }
}
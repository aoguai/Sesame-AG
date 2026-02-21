package fansirsqi.xposed.sesame.hook.keepalive

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 协程调度器 - 基于 Coroutines + WakeLock
 *
 * 核心思想：
 * 1. 抛弃 AlarmManager 的广播回调地狱，回归线性代码。
 * 2. 使用 WakeLock 确保在 delay 期间 CPU 保持运行（解决 Doze 导致的时间停滞）。
 * 3. 使用协程结构化并发管理任务。
 */
object SmartSchedulerManager {
    private const val TAG = "SmartScheduler"
    private const val WAKELOCK_TAG = "Sesame:SchedulerLock"

    // 独立的协程作用域，使用 SupervisorJob 确保单个任务崩溃不影响其他任务
    // 改为可重新创建
    private var _scope: CoroutineScope? = null
    private val scope: CoroutineScope
        get() {
            val s = _scope
            if (s != null && s.isActive) return s
            return CoroutineScope(Dispatchers.Default + SupervisorJob()).also { _scope = it }
        }

    // 管理所有正在运行的任务 Job，用于取消
    private val taskMap = ConcurrentHashMap<Int, Job>()
    // 命名任务映射，用于自动替换同名任务，防止重复调度逻辑堆积
    private val namedTasks = ConcurrentHashMap<String, Int>()
    private val taskIdGenerator = AtomicInteger(0)

    @SuppressLint("StaticFieldLeak")
    private var powerManager: PowerManager? = null

    // 初始化检查
    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        // 即使已初始化，如果 scope 被取消了也要允许恢复
        if (isInitialized && _scope?.isActive == true) return
        try {
            val appContext = context.applicationContext ?: context
            powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            isInitialized = true
            Log.record(TAG, "✅ 调度器已初始化 (Coroutines + WakeLock)")
        } catch (e: Exception) {
            Log.error(TAG, "初始化失败: ${e.message}")
        }
    }

    /**
     * 调度任务
     * @param delayMillis 延迟毫秒数
     * @param taskName 任务名称（用于日志和覆盖旧任务）
     * @param block 要执行的代码块
     * @return 任务ID，可用于取消
     */
    fun schedule(delayMillis: Long, taskName: String = "未命名任务", block: () -> Unit): Int {
        if (!isInitialized) {
            Log.error(TAG, "调度失败：未初始化")
            return -1
        }

        // 自动替换同名任务，防止竞争导致的调度混乱
        namedTasks[taskName]?.let { oldTaskId ->
            cancelTask(oldTaskId)
        }

        val taskId = taskIdGenerator.incrementAndGet()
        namedTasks[taskName] = taskId

        val finalDelay = if (delayMillis < 0) 0L else delayMillis

        // 启动协程
        val job = scope.launch {
            val wakeLock = acquireWakeLock(finalDelay + 5000)
            Log.record(TAG, "⏳ 任务调度: [$taskName] | ID:$taskId | 延迟: ${TimeUtil.formatDuration(finalDelay)}")
            Log.record( ">".repeat(40))

            try {
                // 核心：在 WakeLock 保护下进行挂起
                delay(finalDelay)

                if (isActive) {
                    Log.record(TAG, "▶️ 开始执行: [$taskName] | ID:$taskId")
                    // 切换到主线程执行 Hook 逻辑（通常 Hook 需要在主线程）
                    withContext(Dispatchers.Main) {
                        try {
                            block()
                        } catch (e: Exception) {
                            Log.error(TAG, "❌ 任务执行异常 [$taskName]: ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.record(TAG, "🚫 任务已取消: [$taskName] | ID:$taskId")
            } finally {
                // 释放锁和清理 Map
                releaseWakeLock(wakeLock)
                taskMap.remove(taskId)
                if (namedTasks[taskName] == taskId) {
                    namedTasks.remove(taskName)
                }
            }
        }

        taskMap[taskId] = job
        return taskId
    }

    /**
     * 取消特定任务
     */
    fun cancelTask(taskId: Int) {
        taskMap[taskId]?.cancel()
        taskMap.remove(taskId)
    }

    /**
     * 取消所有任务
     */
    fun cancelAll() {
        Log.record(TAG, "正在取消所有任务...")
        taskMap.values.forEach { it.cancel() }
        taskMap.clear()
        namedTasks.clear()
    }

    /**
     * 申请唤醒锁
     * PARTIAL_WAKE_LOCK: 保持 CPU 运行，屏幕可以关闭，键盘灯可以关闭。
     */
    private fun acquireWakeLock(timeout: Long): PowerManager.WakeLock? {
        return try {
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            wakeLock?.setReferenceCounted(false)
            // 设置超时时间，防止逻辑 bug 导致永久持锁耗电
            wakeLock?.acquire(timeout)
            wakeLock
        } catch (e: Exception) {
            Log.error(TAG, "申请 WakeLock 失败: ${e.message}")
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (e: Exception) {
            // 忽略释放异常
        }
    }

    fun cleanup() {
        _scope?.cancel()
        _scope = null
        cancelAll()
    }
}
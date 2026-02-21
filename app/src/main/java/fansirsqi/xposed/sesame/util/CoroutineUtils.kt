package fansirsqi.xposed.sesame.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * 协程工具类
 *
 * 提供协程相关的通用功能，用于替代传统的线程操作
 */
object CoroutineUtils {

    /**
     * 协程安全的延迟方法
     *
     * 在协程环境中使用 delay()，在非协程环境中降级到 Thread.sleep()
     *
     * @param millis 延迟毫秒数
     */
    @JvmStatic
    suspend fun delayCompat(millis: Long) {
        try {
            delay(millis)
        } catch (e: Exception) {
            Log.printStackTrace("协程延迟异常", e)
            // 如果协程延迟失败，降级到线程休眠
            Thread.sleep(millis)
        }
    }

    /**
     * 兼容性延迟方法（同步版本）
     *
     * 在当前线程中执行延迟，自动处理协程和非协程环境
     *
     * @param millis 延迟毫秒数
     */
    @JvmStatic
    fun sleepCompat(millis: Long) {
        try {
            runBlocking {
                delay(millis)
            }
        } catch (e: Exception) {
            // 降级到传统的 Thread.sleep()
            Log.printStackTrace("协程延迟异常,已尝试降级到 Thread.sleep()", e)
            try {
                Thread.sleep(millis)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.record("CoroutineUtils", "延迟被中断: ${ie.message}")
            }
        }
    }

    /**
     * 在指定调度器上运行协程
     */
    @JvmStatic
    fun runOnDispatcher(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return CoroutineScope(dispatcher + SupervisorJob()).launch {
            try {
                block()
            } catch (e: Exception) {
                Log.printStackTrace("协程执行异常", e)
            }
        }
    }

    /**
     * 在IO调度器上运行协程
     */
    @JvmStatic
    fun runOnIO(block: suspend CoroutineScope.() -> Unit): Job {
        return runOnDispatcher(Dispatchers.IO, block)
    }

    /**
     * 在计算调度器上运行协程
     */
    @JvmStatic
    fun runOnComputation(block: suspend CoroutineScope.() -> Unit): Job {
        return runOnDispatcher(Dispatchers.Default, block)
    }

    /**
     * 同步执行协程代码块
     *
     * 警告：此方法会阻塞当前线程，仅在必要时使用
     */
    @JvmStatic
    fun <T> runBlockingSafe(
        timeout: Long = 30000, // 30秒默认超时
        block: suspend CoroutineScope.() -> T
    ): T? {
        return try {
            runBlocking {
                withTimeout(timeout) {
                    block()
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.error("CoroutineUtils", "协程执行超时: ${timeout}ms")
            null
        } catch (e: Exception) {
            Log.printStackTrace("协程同步执行异常", e)
            null
        }
    }
}

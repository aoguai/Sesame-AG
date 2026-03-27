package io.github.aoguai.sesameag.util

/**
 * 统一管理“模拟人为操作并给服务器状态同步时间”的延迟。
 *
 * 不用于随机风控延迟、用户配置延迟或重试退避。
 */
object ActionDelayUtil {
    private const val DEFAULT_HUMAN_ACTION_DELAY_MS = 1200L

    @JvmStatic
    suspend fun humanActionDelay(millis: Long = DEFAULT_HUMAN_ACTION_DELAY_MS) {
        CoroutineUtils.delayCompat(millis)
    }

    @JvmStatic
    fun humanActionSleep(millis: Long = DEFAULT_HUMAN_ACTION_DELAY_MS) {
        CoroutineUtils.sleepCompat(millis)
    }
}

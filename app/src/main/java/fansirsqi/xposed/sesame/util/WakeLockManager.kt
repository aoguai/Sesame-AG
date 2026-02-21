package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

/**
 * 唤醒锁管理器
 *
 * 用于在后台任务执行期间保持 CPU 唤醒，防止设备因 Doze 模式进入休眠而中断任务。
 * AlarmManager 负责准时唤醒设备，而 WakeLockManager 负责在任务执行期间维持唤醒状态。
 */
object WakeLockManager {
    private const val TAG = "WakeLockManager"
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 获取唤醒锁
     *
     * @param context 上下文
     * @param timeout 自动释放的超时时间（毫秒），默认为10分钟
     */
    @SuppressLint("WakelockTimeout")
    @Synchronized
    fun acquire(context: Context, timeout: Long = 600_000L) {
        if (wakeLock?.isHeld == true) {
             Log.record(TAG, "唤醒锁已被持有，无需重复获取")
            return
        }
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sesame::TaskWakeLock").apply {
                acquire(timeout)
                Log.record(TAG, "🔒 唤醒锁已获取，超时时间: ${timeout / 1000}s")
            }
        } catch (e: Exception) {
            Log.error(TAG, "❌ 获取唤醒锁失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 释放唤醒锁
     */
    @Synchronized
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.record(TAG, "🔑 唤醒锁已释放")
            } else {
                 Log.record(TAG, "唤醒锁未被持有，无需释放")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.error(TAG, "❌ 释放唤醒锁失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }
}

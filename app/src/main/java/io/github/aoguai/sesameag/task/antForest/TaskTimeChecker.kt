package io.github.aoguai.sesameag.task.antForest

import android.annotation.SuppressLint
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.TimeTriggerEvaluator
import io.github.aoguai.sesameag.util.TimeTriggerParseOptions
import io.github.aoguai.sesameag.util.TimeTriggerParser
import java.util.Calendar

/**
 * 任务时间检查器。
 *
 * 第一阶段仅作为旧调用点的兼容包装器保留：
 * - 输入仍是单个时间点
 * - 语义固定为“到点后当日可执行”
 * - 内部统一转成 `HHmm-2400` 允许窗口后交给 TimeTriggerEvaluator
 */
object TaskTimeChecker {
    private val TAG = TaskTimeChecker::class.java.simpleName

    fun checkTime(timeStr: String?, defaultTime: String = "0800"): Boolean {
        return try {
            val normalized = TimeTriggerParser.normalize(
                timeStr,
                TimeTriggerParseOptions(
                    allowCheckpoints = true,
                    allowWindows = false,
                    allowBlockedWindows = false,
                    tag = TAG
                ),
                defaultTime
            )
            if (normalized == "-1") {
                return false
            }
            val checkpoint = normalized.split(",").firstOrNull().orEmpty()
            if (checkpoint.isBlank()) {
                return false
            }

            val spec = TimeTriggerParser.parse(
                "$checkpoint-2400",
                TimeTriggerParseOptions(
                    allowCheckpoints = false,
                    allowWindows = true,
                    allowBlockedWindows = false,
                    tag = TAG
                )
            )
            TimeTriggerEvaluator.evaluateNow(spec).allowNow
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            true
        }
    }

    fun isTimeReached(timeStr: String?, defaultTime: String = "0800"): Boolean {
        return checkTime(timeStr, defaultTime)
    }

    fun formatTime(timeStr: String?): String {
        return try {
            val cleanTime = timeStr?.replace(":", "")?.trim() ?: "0800"
            if (cleanTime.length >= 4) {
                "${cleanTime.take(2)}:${cleanTime.substring(2, 4)}"
            } else if (cleanTime.length >= 2) {
                "${cleanTime.take(2)}:00"
            } else {
                "08:00"
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            "08:00"
        }
    }

    @SuppressLint("DefaultLocale")
    fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d%02d", hour, minute)
    }
}

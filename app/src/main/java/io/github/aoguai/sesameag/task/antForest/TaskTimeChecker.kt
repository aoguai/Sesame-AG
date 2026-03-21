package io.github.aoguai.sesameag.task.antForest

import android.annotation.SuppressLint
import io.github.aoguai.sesameag.util.Log
import java.util.Calendar

/**
 * 任务时间检查器
 * 用于检查任务是否到达指定的执行时间
 */
object TaskTimeChecker {
    private val TAG = TaskTimeChecker::class.java.simpleName

    /**
     * 检查是否到达指定的执行时间
     * 
     * @param timeStr 时间字符串，支持格式：0800、08:00 等
     * @param defaultTime 默认时间，当 timeStr 为空或格式错误时使用
     * @return true 如果当前时间在设定时间之后或等于设定时间
     */
    fun checkTime(timeStr: String?, defaultTime: String = "0800"): Boolean {
        try {
            val time = timeStr?.takeIf { it.isNotBlank() } ?: defaultTime
            
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            
            // 清理和验证时间字符串
            val cleanTime = cleanAndValidateTime(time, defaultTime) ?: run {
                return true // 验证失败，使用默认行为（执行）
            }
            
            val setHour = cleanTime.take(2).toInt()
            val setMinute = if (cleanTime.length >= 4) {
                cleanTime.substring(2, 4).toInt()
            } else {
                0
            }
            
            // 判断当前时间是否在设定时间之后
            return if (currentHour > setHour) {
                true
            } else if (currentHour == setHour) {
                currentMinute >= setMinute
            } else {
                false
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            return true // 出错时默认执行
        }
    }
    
    /**
     * 清理和验证时间字符串
     * 
     * @param timeStr 原始时间字符串
     * @param defaultTime 默认时间
     * @return 清理后的时间字符串，如果验证失败返回 null
     */
    private fun cleanAndValidateTime(timeStr: String, defaultTime: String): String? {
        try {
            // 移除所有空格、冒号和逗号
            var cleaned = timeStr.replace(":", "").replace(" ", "").replace(",", "").trim()
            
            // 检查是否包含多个时间（比如 "0810 0830" 或 "0810,0820"）
            if (cleaned.contains(Regex("\\d{4}.*\\d{4}"))) {
                Log.record(TAG, "⚠️ 检测到多个时间值，只使用第一个时间")
                // 提取第一个4位数字
                val match = Regex("\\d{4}").find(cleaned)
                cleaned = match?.value ?: defaultTime
            }
            
            // 只保留数字
            cleaned = cleaned.filter { it.isDigit() }
            
            // 验证长度
            if (cleaned.length !in 2..4) {
                Log.record(TAG, "⚠️ 时间格式错误（长度${cleaned.length}），使用默认时间: $defaultTime")
                return defaultTime.filter { it.isDigit() }
            }
            
            // 补齐到4位
            if (cleaned.length == 2) {
                cleaned += "00"
            } else if (cleaned.length == 3) {
                cleaned = "0$cleaned"
            }
            
            // 验证小时和分钟的有效性
            val hour = cleaned.substring(0, 2).toIntOrNull() ?: run {
                Log.record(TAG, "⚠️ 小时解析失败，使用默认时间")
                return defaultTime.filter { it.isDigit() }
            }
            
            val minute = cleaned.substring(2, 4).toIntOrNull() ?: 0
            
            // 验证范围
            if (hour !in 0..23) {
                Log.record(TAG, "⚠️ 小时[$hour]超出范围(0-23)，使用默认时间: $defaultTime")
                return defaultTime.filter { it.isDigit() }
            }
            
            if (minute !in 0..59) {
                Log.record(TAG, "⚠️ 分钟[$minute]超出范围(0-59)，使用默认时间: $defaultTime")
                return defaultTime.filter { it.isDigit() }
            }
            
            return cleaned
        } catch (e: Exception) {
            Log.record(TAG, "⚠️ 时间验证异常: ${e.message}，使用默认时间")
            return defaultTime.filter { it.isDigit() }
        }
    }

    /**
     * 检查任务是否到达执行时间（通用方法）
     * 
     * @param timeStr 时间字符串
     * @param defaultTime 默认时间
     * @return true 如果当前时间在设定时间之后
     */
    fun isTimeReached(timeStr: String?, defaultTime: String = "0800"): Boolean {
        return checkTime(timeStr, defaultTime)
    }

    /**
     * 格式化时间字符串为标准格式
     * 
     * @param timeStr 原始时间字符串
     * @return 格式化后的时间字符串（HH:mm）
     */
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
            "08:00"
        }
    }

    /**
     * 获取当前时间字符串
     * 
     * @return 当前时间（格式：HHmm）
     */
    @SuppressLint("DefaultLocale")
    fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d%02d", hour, minute)
    }
}



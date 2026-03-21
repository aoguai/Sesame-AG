package io.github.aoguai.sesameag.task

import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.TimeUtil

/**
 * 通用任务工具类
 *
 * 提供任务相关的通用功能，包括时间判断和状态更新。
 */
object TaskCommon {
    
    @Volatile
    @JvmField
    var IS_ENERGY_TIME: Boolean = false
    
    @Volatile
    @JvmField
    var IS_AFTER_8AM: Boolean = false
    
    @Volatile
    @JvmField
    var IS_MODULE_SLEEP_TIME: Boolean = false

    @JvmStatic
    fun update() {
        val currentTimeMillis = System.currentTimeMillis()

        // 只收能量时间检查
        IS_ENERGY_TIME = checkTimeRangeConfig(
            BaseModel.energyTime.value,
            "只收能量时间",
            currentTimeMillis
        )

        // 模块休眠时间检查
        IS_MODULE_SLEEP_TIME = checkTimeRangeConfig(
            BaseModel.modelSleepTime.value,
            "模块休眠时间",
            currentTimeMillis
        )

        // 是否过了 8 点
        IS_AFTER_8AM = TimeUtil.isAfterOrCompareTimeStr(currentTimeMillis, "0800")

        // 输出状态更新日志（已注释）
        /*
        Log.runtime("TaskCommon Update 完成:\n" +
                "只收能量时间配置: $IS_ENERGY_TIME\n" +
                "模块休眠配置: $IS_MODULE_SLEEP_TIME\n" +
                "当前是否过了8点: $IS_AFTER_8AM")
        */
    }

    /**
     * 检查时间配置是否在当前时间范围内
     *
     * @param timeConfig 配置的时间段
     * @param label 配置标签（用于日志输出）
     * @param currentTime 当前时间
     * @return 是否在时间范围内
     */
    private fun checkTimeRangeConfig(
        timeConfig: List<String>?,
        label: String,
        currentTime: Long
    ): Boolean {
        if (isConfigDisabled(timeConfig)) {
            Log.runtime("$label 配置已关闭")
            return false
        }

        Log.runtime("获取 $label 配置: $timeConfig")
        return TimeUtil.checkInTimeRange(currentTime, timeConfig ?: emptyList())
    }

    /**
     * 判断当前配置是否表示"关闭"
     *
     * @param config 输入的字符串列表
     * @return true 表示关闭
     */
    @JvmStatic
    fun isConfigDisabled(config: List<String>?): Boolean {
        if (config.isNullOrEmpty()) return true

        val first = config[0].trim()
        return first == "-1" // 表示配置已关闭
    }
}


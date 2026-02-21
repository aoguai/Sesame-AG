package fansirsqi.xposed.sesame.task;

import java.util.List;

import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.TimeUtil;

/**
 * 通用任务工具类
 * <p>
 * 提供任务相关的通用功能，包括时间判断和状态更新。
 */
public class TaskCommon {
    public static volatile Boolean IS_ENERGY_TIME = false;
    public static volatile Boolean IS_AFTER_8AM = false;
    public static volatile Boolean IS_MODULE_SLEEP_TIME = false;

    public static void update() {
        long currentTimeMillis = System.currentTimeMillis();

        // 只收能量时间检查
        IS_ENERGY_TIME = checkTimeRangeConfig(BaseModel.Companion.getEnergyTime().getValue(), "只收能量时间", currentTimeMillis);

        // 模块休眠时间检查
        IS_MODULE_SLEEP_TIME = checkTimeRangeConfig(BaseModel.Companion.getModelSleepTime().getValue(), "模块休眠时间", currentTimeMillis);

        // 是否过了 8 点
        IS_AFTER_8AM = TimeUtil.isAfterOrCompareTimeStr(currentTimeMillis, "0800");

        // 输出状态更新日志
  /*      Log.runtime("TaskCommon Update 完成:\n" +
                "只收能量时间配置: " + IS_ENERGY_TIME + "\n" +
                "模块休眠配置: " + IS_MODULE_SLEEP_TIME + "\n" +
                "当前是否过了8点: " + IS_AFTER_8AM);*/
    }

    /**
     * 检查时间配置是否在当前时间范围内
     *
     * @param timeConfig 配置的时间段
     * @param label      配置标签（用于日志输出）
     * @param currentTime 当前时间
     * @return 是否在时间范围内
     */
    private static boolean checkTimeRangeConfig(List<String> timeConfig, String label, long currentTime) {
        if (isConfigDisabled(timeConfig)) {
            Log.record(label + " 配置已关闭");
            return false;
        }

        Log.record("获取 " + label + " 配置: " + timeConfig);
        return TimeUtil.checkInTimeRange(currentTime, timeConfig);
    }

    /**
     * 判断当前配置是否表示“关闭”
     *
     * @param config 输入的字符串列表
     * @return true 表示关闭
     */
    public static boolean isConfigDisabled(List<String> config) {
        if (config == null || config.isEmpty()) return true;

        String first = config.get(0).trim();
        return "-1".equals(first);  // 表示配置已关闭
    }
}
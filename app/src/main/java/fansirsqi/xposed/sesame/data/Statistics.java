package fansirsqi.xposed.sesame.data;

import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.File;
import java.util.Calendar;
import java.util.Objects;

import fansirsqi.xposed.sesame.util.Files;
import fansirsqi.xposed.sesame.util.JsonUtil;
import fansirsqi.xposed.sesame.util.Log;
import lombok.Data;

/**
 * 能量统计（按 年/月/日 维度统计：收取/帮助/浇水）
 *
 * 说明：
 * - 该实现参考了 Sesame-TK-Y 中的同名能力，但为兼容多账号，这里将统计文件存储到用户目录下：
 *   `config/{uid}/statistics.json`
 * - 统计对象常驻内存；在 AntForest 任务开始时 load，在结束时 save
 */
@Data
public class Statistics {

    @Data
    public static class TimeStatistics {
        int time;
        int collected, helped, watered;

        public TimeStatistics() {
            reset(0);
        }

        public TimeStatistics(int time) {
            reset(time);
        }

        public void reset(int time) {
            this.time = time;
            collected = 0;
            helped = 0;
            watered = 0;
        }
    }

    private static final String TAG = Statistics.class.getSimpleName();

    public static final Statistics INSTANCE = new Statistics();

    private static String loadedUserId = null;

    private TimeStatistics year = new TimeStatistics();
    private TimeStatistics month = new TimeStatistics();
    private TimeStatistics day = new TimeStatistics();

    private static File getStatisticsFile(String userId) {
        return Files.getTargetFileofUser(userId, "statistics.json");
    }

    private static boolean ensureLoaded(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.error(TAG, "Invalid userId, skip statistics operation");
            return false;
        }
        if (!Objects.equals(userId, loadedUserId)) {
            load(userId);
        }
        return true;
    }

    /**
     * 增加指定数据类型的统计量
     *
     * @param userId 用户ID
     * @param dt     数据类型（收集、帮助、浇水）
     * @param i      增加的数量（g）
     */
    public static synchronized void addData(String userId, DataType dt, int i) {
        if (i == 0) return;
        if (!ensureLoaded(userId)) return;
        Statistics stat = INSTANCE;
        switch (dt) {
            case COLLECTED:
                stat.day.collected += i;
                stat.month.collected += i;
                stat.year.collected += i;
                break;
            case HELPED:
                stat.day.helped += i;
                stat.month.helped += i;
                stat.year.helped += i;
                break;
            case WATERED:
                stat.day.watered += i;
                stat.month.watered += i;
                stat.year.watered += i;
                break;
            case TIME:
                break;
        }
    }

    /**
     * 获取指定时间和数据类型的统计值
     *
     * @param userId 用户ID
     * @param tt     时间类型（年、月、日）
     * @param dt     数据类型（时间、收集、帮助、浇水）
     * @return 统计值
     */
    public static synchronized int getData(String userId, TimeType tt, DataType dt) {
        if (!ensureLoaded(userId)) return 0;
        TimeStatistics ts = switch (tt) {
            case YEAR -> INSTANCE.year;
            case MONTH -> INSTANCE.month;
            case DAY -> INSTANCE.day;
        };
        if (ts == null) return 0;
        return switch (dt) {
            case TIME -> ts.time;
            case COLLECTED -> ts.collected;
            case HELPED -> ts.helped;
            case WATERED -> ts.watered;
        };
    }

    /**
     * 获取统计文本信息
     *
     * @param userId 用户ID
     * @return 包含年、月、日统计信息的字符串
     */
    public static synchronized String getText(String userId) {
        if (!ensureLoaded(userId)) return "";
        return "今年  收: " + getData(userId, TimeType.YEAR, DataType.COLLECTED) +
                " 帮: " + getData(userId, TimeType.YEAR, DataType.HELPED) +
                " 浇: " + getData(userId, TimeType.YEAR, DataType.WATERED) +
                "\n今月  收: " + getData(userId, TimeType.MONTH, DataType.COLLECTED) +
                " 帮: " + getData(userId, TimeType.MONTH, DataType.HELPED) +
                " 浇: " + getData(userId, TimeType.MONTH, DataType.WATERED) +
                "\n今日  收: " + getData(userId, TimeType.DAY, DataType.COLLECTED) +
                " 帮: " + getData(userId, TimeType.DAY, DataType.HELPED) +
                " 浇: " + getData(userId, TimeType.DAY, DataType.WATERED);
    }

    /**
     * 加载统计数据
     *
     * @param userId 用户ID
     * @return 统计实例
     */
    public static synchronized Statistics load(String userId) {
        loadedUserId = userId;
        File statisticsFile = getStatisticsFile(userId);
        try {
            if (statisticsFile == null) {
                resetToDefault();
                return INSTANCE;
            }
            if (statisticsFile.exists() && statisticsFile.length() > 0) {
                String json = Files.readFromFile(statisticsFile);
                if (!json.trim().isEmpty()) {
                    try {
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                        validateAndInitialize();
                        String formatted = JsonUtil.formatJson(INSTANCE);
                        if (formatted != null && !formatted.equals(json)) {
                            Log.record(TAG, "重新格式化 statistics.json");
                            Files.write2File(formatted, statisticsFile);
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(TAG, "statistics.json 解析失败，已重置", e);
                        resetToDefault();
                    }
                } else {
                    resetToDefault();
                }
            } else {
                resetToDefault();
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "统计文件格式有误，已重置统计文件", t);
            resetToDefault();
        }
        return INSTANCE;
    }

    /**
     * 验证并初始化统计数据
     * 确保年、月、日的统计数据都存在且有效
     */
    private static void validateAndInitialize() {
        Calendar now = Calendar.getInstance();
        if (INSTANCE.year == null) INSTANCE.year = new TimeStatistics(now.get(Calendar.YEAR));
        if (INSTANCE.month == null) INSTANCE.month = new TimeStatistics(now.get(Calendar.MONTH) + 1);
        if (INSTANCE.day == null) INSTANCE.day = new TimeStatistics(now.get(Calendar.DAY_OF_MONTH));
        updateDay(now);
    }

    /**
     * 重置统计数据为默认值
     * 使用当前日期初始化新的统计实例
     */
    private static void resetToDefault() {
        try {
            Statistics newInstance = new Statistics();
            Calendar now = Calendar.getInstance();
            newInstance.year = new TimeStatistics(now.get(Calendar.YEAR));
            newInstance.month = new TimeStatistics(now.get(Calendar.MONTH) + 1);
            newInstance.day = new TimeStatistics(now.get(Calendar.DAY_OF_MONTH));
            JsonUtil.copyMapper().updateValue(INSTANCE, newInstance);

            if (loadedUserId != null) {
                File statisticsFile = getStatisticsFile(loadedUserId);
                if (statisticsFile != null) {
                    Files.write2File(JsonUtil.formatJson(INSTANCE), statisticsFile);
                }
            }

            Log.record(TAG, "已重置为默认值");
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, "resetToDefault JsonMappingException", e);
        } catch (Exception e) {
            Log.printStackTrace(TAG, "resetToDefault Exception", e);
        }
    }

    /**
     * 卸载当前统计数据（仅清空内存，不删除文件）
     */
    public static synchronized void unload() {
        loadedUserId = null;
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, "unload JsonMappingException", e);
        }
    }

    /**
     * 保存统计数据
     */
    public static synchronized void save(String userId) {
        save(userId, Calendar.getInstance());
    }

    /**
     * 保存统计数据并更新日期
     *
     * @param userId  用户ID
     * @param nowDate 当前日期
     */
    public static synchronized void save(String userId, Calendar nowDate) {
        if (!ensureLoaded(userId)) return;
        if (updateDay(nowDate)) {
            Log.record(TAG, "重置 statistics.json");
        } else {
            Log.record(TAG, "保存 statistics.json");
        }
        File statisticsFile = getStatisticsFile(userId);
        if (statisticsFile != null) {
            Files.write2File(JsonUtil.formatJson(INSTANCE), statisticsFile);
        }
    }

    /**
     * 更新日期并重置统计数据
     *
     * @param nowDate 当前日期
     * @return 如果日期已更改，返回 true；否则返回 false
     */
    public static Boolean updateDay(Calendar nowDate) {
        int currentYear = nowDate.get(Calendar.YEAR);
        int currentMonth = nowDate.get(Calendar.MONTH) + 1;
        int currentDay = nowDate.get(Calendar.DAY_OF_MONTH);

        if (INSTANCE.year == null) INSTANCE.year = new TimeStatistics(currentYear);
        if (INSTANCE.month == null) INSTANCE.month = new TimeStatistics(currentMonth);
        if (INSTANCE.day == null) INSTANCE.day = new TimeStatistics(currentDay);

        if (currentYear != INSTANCE.year.time) {
            INSTANCE.year.reset(currentYear);
            INSTANCE.month.reset(currentMonth);
            INSTANCE.day.reset(currentDay);
        } else if (currentMonth != INSTANCE.month.time) {
            INSTANCE.month.reset(currentMonth);
            INSTANCE.day.reset(currentDay);
        } else if (currentDay != INSTANCE.day.time) {
            INSTANCE.day.reset(currentDay);
        } else {
            return false;
        }
        return true;
    }

    public enum TimeType {
        YEAR, MONTH, DAY
    }

    public enum DataType {
        TIME, COLLECTED, HELPED, WATERED
    }
}


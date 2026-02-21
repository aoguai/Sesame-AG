package fansirsqi.xposed.sesame.data;

/**
 * 用于统一管理所有【每日 / 状态 Flag】的常量定义。
 *
 * 设计目标：
 * 1. 避免项目中散落字符串常量
 * 2. 统一命名规范，便于搜索和维护
 * 3. 明确业务模块归属
 *
 * 命名规范：
 * - 常量名：全大写 + 下划线（FLAG_XXX）
 * - 常量值：实际存储使用的 Key（保持历史兼容）
 */
public final class StatusFlags {

    private StatusFlags() {
        // 禁止实例化
    }

    // ============================================================
    // Neverland（健康岛）
    // ============================================================

    /** 今日步数任务是否已完成 */
    public static final String FLAG_NEVERLAND_STEP_COUNT =
            "Flag_Neverland_StepCount";


    // ============================================================
    // AntMember（会员频道 / 积分）
    // ============================================================

    /** 是否已执行「领取所有可做芝麻任务」 */
    public static final String FLAG_ANTMEMBER_DO_ALL_SESAME_TASK =
            "AntMember::doAllAvailableSesameTask";

    /** 今日贴纸领取任务 */
    public static final String FLAG_ANTMEMBER_STICKER =
            "Flag_AntMember_Sticker";


    // ============================================================
    // 芝麻信用 / 芝麻粒
    // ============================================================

    /** 芝麻粒炼金：次日奖励是否已领取 */
    public static final String FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD =
            "zmxy::alchemy::nextDayAward";

    /** 信用 2101：图鉴章节任务是否全部完成 */
    public static final String FLAG_CREDIT2101_CHAPTER_TASK_DONE =
            "FLAG_Credit2101_ChapterTask_Done";


    // ============================================================
    // 运动任务（AntSports）
    // ============================================================

    /** 运动任务大厅：今日是否已循环处理 */
    public static final String FLAG_ANTSPORTS_TASK_CENTER_DONE =
            "Flag_AntSports_TaskCenter_Done";

    /** 今日步数同步是否已完成 */
    public static final String FLAG_ANTSPORTS_SYNC_STEP_DONE =
            "FLAG_ANTSPORTS_syncStep_Done";

    /** 今日运动日常任务是否已完成 */
    public static final String FLAG_ANTSPORTS_DAILY_TASKS_DONE =
            "FLAG_ANTSPORTS_dailyTasks_Done";


    // ============================================================
    // 农场 / 新村 / 团队
    // ============================================================

    /** 团队浇水：今日次数统计 */
    public static final String FLAG_TEAM_WATER_DAILY_COUNT =
            "Flag_Team_Weater_Daily_Count";

    /** 农场组件：每日回访奖励 */
    public static final String FLAG_ANTORCHARD_WIDGET_DAILY_AWARD =
            "Flag_Antorchard_Widget_Daily_Award";

    /** 农场：今日施肥次数 */
    public static final String FLAG_ANTORCHARD_SPREAD_MANURE_COUNT =
            "FLAG_Antorchard_SpreadManure_Count";

    /** 蚂蚁新村：今日丢肥料是否达到上限 */
    public static final String FLAG_ANTSTALL_THROW_MANURE_LIMIT =
            "Flag_AntStall_Throw_Manure_Limit";

}

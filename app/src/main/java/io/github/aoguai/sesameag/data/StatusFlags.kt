package io.github.aoguai.sesameag.data

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
object StatusFlags {

    // ============================================================
    // Neverland（健康岛）
    // ============================================================

    /** 今日步数任务是否已完成 */
    const val FLAG_NEVERLAND_STEP_COUNT: String = "Flag_Neverland_StepCount"

    // ============================================================
    // AntForest（蚂蚁森林）
    // ============================================================

    /** 森林 PK：今日已判定无需处理（未加入/赛季未开启），用于避免重复请求触发风控 */
    const val FLAG_ANTFOREST_PK_SKIP_TODAY: String = "AntForest::pkSkipToday"

    // ============================================================
    // AntMember（会员频道 / 积分）
    // ============================================================

    /** 今日是否已处理「会员签到」 */
    const val FLAG_ANTMEMBER_MEMBER_SIGN_DONE: String = "AntMember::memberSignDone"

    /** 今日会员任务已判定无需继续刷新（列表为空/仅剩黑名单/仅剩暂不支持任务） */
    const val FLAG_ANTMEMBER_MEMBER_TASK_EMPTY_TODAY: String = "AntMember::memberTaskEmptyToday"

    /** 今日会员任务因风控/离线止损，不再继续刷新 */
    const val FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY: String = "AntMember::memberTaskRiskStopToday"

    /** 是否已执行「领取所有可做芝麻任务」 */
    const val FLAG_ANTMEMBER_DO_ALL_SESAME_TASK: String = "AntMember::doAllAvailableSesameTask"

    /** 芝麻信用：当日加入任务次数已达上限（PROMISE_TODAY_FINISH_TIMES_LIMIT），用于止损避免重复请求 */
    const val FLAG_ANTMEMBER_SESAME_JOIN_LIMIT_REACHED: String = "AntMember::sesameJoinLimitReached"

    /** 今日是否已处理「芝麻粒福利签到」(zml check-in) */
    const val FLAG_ANTMEMBER_ZML_CHECKIN_DONE: String = "AntMember::zmlCheckInDone"

    /** 今日是否已处理「芝麻粒领取」(credit feedback collect) */
    const val FLAG_ANTMEMBER_COLLECT_SESAME_DONE: String = "AntMember::collectSesameDone"

    /** 今日贴纸领取任务 */
    const val FLAG_ANTMEMBER_STICKER: String = "Flag_AntMember_Sticker"

    // ============================================================
    // 芝麻信用 / 芝麻粒
    // ============================================================

    /** 芝麻粒炼金：次日奖励是否已领取 */
    const val FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD: String = "zmxy::alchemy::nextDayAward"

    /** 信用 2101：图鉴章节任务是否全部完成 */
    const val FLAG_CREDIT2101_CHAPTER_TASK_DONE: String = "FLAG_Credit2101_ChapterTask_Done"

    /** 商家服务：每日签到 */
    const val FLAG_ANTMEMBER_MERCHANT_SIGN_DONE: String = "AntMember::merchantSignDone"

    /** 商家服务：开门打卡签到（06:00-12:00） */
    const val FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE: String = "AntMember::merchantKmdkSignInDone"

    /** 商家服务：开门打卡报名 */
    const val FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNUP_DONE: String = "AntMember::merchantKmdkSignUpDone"

    /** 黄金票：今日是否已处理签到 */
    const val FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE: String = "AntMember::goldTicketSignDone"

    /** 黄金票：首页收取/任务扫描是否已处理 */
    const val FLAG_ANTMEMBER_GOLD_TICKET_HOME_DONE: String = "AntMember::goldTicketHomeDone"

    /** 黄金票：福利中心任务是否已处理 */
    const val FLAG_ANTMEMBER_GOLD_TICKET_WELFARE_DONE: String = "AntMember::goldTicketWelfareDone"

    /** 黄金票：今日是否已完成提取检查，无需再次尝试 */
    const val FLAG_ANTMEMBER_GOLD_TICKET_CONSUME_DONE: String = "AntMember::goldTicketConsumeDone"

    // ============================================================
    // 运动任务（AntSports）
    // ============================================================

    /** 运动任务大厅：今日是否已循环处理 */
    const val FLAG_ANTSPORTS_TASK_CENTER_DONE: String = "Flag_AntSports_TaskCenter_Done"

    /** 今日步数同步是否已完成 */
    const val FLAG_ANTSPORTS_SYNC_STEP_DONE: String = "FLAG_ANTSPORTS_syncStep_Done"

    /** 今日运动日常任务是否已完成 */
    const val FLAG_ANTSPORTS_DAILY_TASKS_DONE: String = "FLAG_ANTSPORTS_dailyTasks_Done"

    // ============================================================
    // 农场 / 新村 / 团队
    // ============================================================

    /** 团队浇水：今日次数统计 */
    const val FLAG_TEAM_WATER_DAILY_COUNT: String = "Flag_Team_Weater_Daily_Count"

    /** 农场组件：每日回访奖励 */
    const val FLAG_ANTORCHARD_WIDGET_DAILY_AWARD: String = "Flag_Antorchard_Widget_Daily_Award"

    /** 农场：今日施肥次数 */
    const val FLAG_ANTORCHARD_SPREAD_MANURE_COUNT: String = "FLAG_Antorchard_SpreadManure_Count"

    /** 蚂蚁新村：今日丢肥料是否达到上限 */
    const val FLAG_ANTSTALL_THROW_MANURE_LIMIT: String = "Flag_AntStall_Throw_Manure_Limit"

    /** 今日小鸡抽抽乐是否已完成 */
    const val FLAG_FARM_CHOUCHOULE_FINISHED = "antFarm::chouChouLeFinished"

    /** 今日改分/小游戏是否已完成 */
    const val FLAG_FARM_GAME_FINISHED = "antFarm::farmGameFinished"

    /** 今日饲料任务是否已完成 */
    const val FLAG_FARM_TASK_FINISHED = "antFarm::farmTaskFinished"

    /** 庄园：加速卡每日次数上限标记 */
    const val FLAG_FARM_ACCELERATE_LIMIT = "antFarm::accelerateLimit"

    /** 庄园：特殊食品今日已使用数量 */
    const val FLAG_FARM_SPECIAL_FOOD_DAILY_COUNT = "antFarm::specialFoodDailyCount"

    /** 庄园：特殊食品今日已达自定义上限 */
    const val FLAG_FARM_SPECIAL_FOOD_LIMIT = "antFarm::specialFoodLimit"

    /** 庄园：今日是否已签到 */
    const val FLAG_FARM_SIGNED = "antFarm::signed"

    /** 庄园：今日帮喂次数已达上限 */
    const val FLAG_FARM_FEED_FRIEND_LIMIT = "antFarm::feedFriendLimit"

    /** 庄园：按好友维度记录帮喂上限的前缀 */
    const val FLAG_FARM_FEED_FRIEND_LIMIT_PREFIX = "antFarm::feedFriendLimit::"

    /** 庄园家庭：今日签到已处理 */
    const val FLAG_FARM_FAMILY_SIGNED = "antFarm::familyDailySign"

    /** 庄园家庭：今日一起睡觉已处理 */
    const val FLAG_FARM_FAMILY_SLEEP_TOGETHER = "antFarm::familySleepTogether"

    /** 庄园家庭：今日道早安已处理 */
    const val FLAG_FARM_FAMILY_DELIVER_MSG_SEND = "antFarm::deliverMsgSend"

    /** 庄园家庭：今日好友分享已处理 */
    const val FLAG_FARM_FAMILY_SHARE_TO_FRIENDS = "antFarm::familyShareToFriends"
}


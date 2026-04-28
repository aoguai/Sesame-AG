package io.github.aoguai.sesameag.task.antSports

import android.annotation.SuppressLint
import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.entity.AlipayUser
import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.hook.ApplicationHookConstants
import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.HourOfDayModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.model.modelFieldExt.StringModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.task.TaskCommon
import io.github.aoguai.sesameag.util.*
import io.github.aoguai.sesameag.util.FriendGuard
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

import kotlin.math.max
import kotlin.math.min

/**
 * @file AntSports.kt
 * @brief 支付宝蚂蚁运动主任务逻辑（Kotlin 重构版）。
 *
 * @details
 * 负责统一调度蚂蚁运动相关的所有自动化逻辑，包括：
 * - 步数同步与行走路线（旧版 & 新版路线）
 * - 运动任务面板任务、首页能量球任务
 * - 首页金币收集、慈善捐步
 * - 文体中心任务 / 行走路线
 * - 抢好友大战（训练好友 + 抢购好友）
 * - 健康岛（Neverland）任务、泡泡、走路建造
 *
 * 所有 RPC 调用均通过 {@link AntSportsRpcCall} 与 {@link AntSportsRpcCall.NeverlandRpcCall} 完成。
 */
@SuppressLint("DefaultLocale")
class AntSports : ModelTask() {

    companion object {
        /** @brief 日志 TAG */
        private val TAG: String = AntSports::class.java.simpleName

        /** @brief 运动任务完成日期缓存键 */
        private const val SPORTS_TASKS_COMPLETED_DATE = "SPORTS_TASKS_COMPLETED_DATE"

        /** @brief 训练好友 0 金币达上限日期缓存键 */
        private const val TRAIN_FRIEND_ZERO_COIN_DATE = "TRAIN_FRIEND_ZERO_COIN_DATE"

        /** @brief 运动任务黑名单模块名 */
        private const val SPORTS_TASK_BLACKLIST_MODULE = "运动"

        /** @brief 训练好友目标变更重试上限 */
        private const val MAX_TRAIN_MEMBER_CHANGED_RETRIES = 5

        /** @brief 运动首页任务最大补拉轮次 */
        private const val MAX_SPORTS_HOME_BUBBLE_ROUNDS = 10

        /** @brief 新版路线单次运行最大推进轮次，避免路线切换/复活异常造成长循环 */
        private const val MAX_ROUTE_WALK_ROUNDS = 8

        /** @brief 步数同步子任务 ID，用于避免同一天重复排队 */
        private const val SYNC_STEP_CHILD_TASK_ID = "syncStep"

        private const val RPC_WALK_QUERY_PATH = "com.alipay.sportsplay.biz.rpc.walk.queryPath"
        private const val RPC_WALK_QUERY_USER = "com.alipay.sportsplay.biz.rpc.walk.queryUser"
        private const val RPC_WALK_QUERY_WORLD_MAP = "com.alipay.sportsplay.biz.rpc.walk.queryWorldMap"
        private const val RPC_WALK_QUERY_CITY_PATH = "com.alipay.sportsplay.biz.rpc.walk.queryCityPath"
        private const val RPC_WALK_QUERY_CITY_KNOWLEDGE_SUMMARY =
            "com.alipay.sportsplay.biz.rpc.walk.queryCityKnowledgeSummary"
        private const val RPC_WALK_QUERY_RECOMMEND_PATH_LIST =
            "com.alipay.sportsplay.biz.rpc.walk.queryRecommendPathList"
        private const val RPC_WALK_REVIVE_QUERY_DETAIL =
            "com.alipay.sportsplay.biz.rpc.walk.steprevive.queryUserReviveStepT2"
        private const val RPC_WALK_REVIVE_QUERY_TASK_LIST =
            "com.alipay.sportsplay.biz.rpc.walk.steprevive.queryTaskList"
        private const val RPC_WALK_REVIVE_QUERY_TASK_FINISH_STATUS =
            "com.alipay.sportsplay.biz.rpc.walk.steprevive.queryTaskFinishStatus"

    }

    private data class SportsHomeRewardCandidate(
        val recordId: String,
        val sourceName: String,
        val taskId: String,
        val coinAmount: Int
    )

    private data class SportsHomeRewardScanResult(
        val candidates: LinkedHashMap<String, SportsHomeRewardCandidate> = LinkedHashMap(),
        var missingRecordIdCount: Int = 0
    )

    private data class SportsTrainTarget(
        val memberId: String,
        val originBossId: String,
        val userName: String
    )

    private data class SportsTrainItemSelection(
        val bizId: String,
        val itemType: String,
        val itemName: String
    )

    private data class ClubMemberCandidate(
        val currentBossId: String,
        val memberId: String,
        val originBossId: String,
        val price: Int
    )

    private data class NeverlandRewardCandidate(
        val rewardId: String,
        val name: String,
        val status: String,
        val prizeStatus: String
    )

    private data class RouteConfig(
        val themeIds: List<String>,
        val pathIds: List<String>,
        val themeLoop: Boolean,
        val routeLoop: Boolean,
        val reviveSteps: Boolean,
        val reviveTask: Boolean
    )

    private data class RouteCandidate(
        val pathId: String,
        val name: String = "",
        val themeId: String? = null,
        val cityId: String? = null,
        val status: String = ""
    )

    private data class RouteDecision(
        val candidate: RouteCandidate,
        val reason: String
    )

    private data class ReviveCandidate(
        val date: String,
        val count: Int
    )

    private enum class RouteWalkOutcome {
        MOVED,
        COMPLETED,
        NO_STEPS,
        STOP
    }

    private enum class SportsPanelTaskCompleteResult {
        SUCCESS,
        FAILED,
        STOP_CURRENT_ROUND
    }

    /** @brief 临时步数缓存（-1 表示未初始化） */
    private var tmpStepCount: Int = -1
    private var cachedOriginDailyStep: Int = -1
    private var cachedTargetDailyStep: Int = -1
    private var syncStepHookLogged: Boolean = false
    private val syncStepLock = Any()

    @Volatile
    private var syncStepInProgress: Boolean = false

    // 配置字段
    internal lateinit var walk: BooleanModelField
    private lateinit var walkPathTheme: ChoiceModelField
    private var walkPathThemeId: String? = null
    private lateinit var walkCustomPath: BooleanModelField
    private lateinit var walkCustomPathId: StringModelField
    private lateinit var walkThemeIds: StringModelField
    private lateinit var walkPathIds: StringModelField
    private lateinit var walkThemeLoop: BooleanModelField
    private lateinit var walkRouteLoop: BooleanModelField
    private lateinit var walkReviveSteps: BooleanModelField
    private lateinit var walkReviveTask: BooleanModelField
    internal lateinit var openTreasureBox: BooleanModelField
    private lateinit var receiveCoinAssetField: BooleanModelField
    internal lateinit var donateCharityCoin: BooleanModelField
    private lateinit var donateCharityCoinType: ChoiceModelField
    private lateinit var donateCharityCoinAmount: IntegerModelField
    internal lateinit var minExchangeCount: IntegerModelField
    internal lateinit var earliestSyncStepTime: HourOfDayModelField
    private lateinit var latestExchangeTime: HourOfDayModelField
    private lateinit var syncStepCount: IntegerModelField
    internal lateinit var tiyubiz: BooleanModelField
    internal lateinit var battleForFriends: BooleanModelField
    private lateinit var battleAutoUnlockRoom: BooleanModelField
    private lateinit var battleForFriendType: ChoiceModelField
    private lateinit var originBossIdList: SelectModelField
    private lateinit var sportsTasksField: BooleanModelField
    private lateinit var sportsEnergyBubble: BooleanModelField

    // 训练好友相关配置
    internal lateinit var trainFriend: BooleanModelField
    private lateinit var zeroCoinLimit: IntegerModelField

    /** @brief 记录训练好友连续获得 0 金币的次数 */
    private var zeroTrainCoinCount: Int = 0
    private val skippedTrainBubbleRecordIds = mutableSetOf<String>()

    // 健康岛任务
    internal lateinit var neverlandTask: BooleanModelField
    internal lateinit var neverlandGrid: BooleanModelField
    private lateinit var neverlandAutoReward: BooleanModelField
    private lateinit var neverlandPreferMedal: BooleanModelField
    private lateinit var neverlandGridStepCount: IntegerModelField


    /**
     * @brief 任务名称
     */
    override fun getName(): String = "运动"

    /**
     * @brief 所属任务分组
     */
    override fun getGroup(): ModelGroup = ModelGroup.SPORTS

    /**
     * @brief 图标文件名
     */
    override fun getIcon(): String = "AntSports.png"

    /**
     * @brief 定义本任务所需的所有配置字段
     */
    override fun getFields(): ModelFields {
        val modelFields = ModelFields()

        // 行走路线
        modelFields.addField(BooleanModelField("walk", "行走路线 | 开启", false).withDesc(
            "开启新版运动路线自动行走，按配置主题或自定义路线推进路线并领取奖励。"
        ).also { walk = it })
        modelFields.addField(
            ChoiceModelField(
                "walkPathTheme",
                "行走路线 | 主题",
                WalkPathTheme.DA_MEI_ZHONG_GUO,
                WalkPathTheme.nickNames
            ).withDesc("选择新版行走路线主题，仅在开启行走路线且未启用自定义路线时生效。").also { walkPathTheme = it }
        )
        modelFields.addField(
            BooleanModelField("walkCustomPath", "行走路线 | 开启自定义路线", false).withDesc(
                "改为使用下方自定义路线代码，不再按主题自动选线。"
            ).also { walkCustomPath = it }
        )
        modelFields.addField(
            StringModelField(
                "walkCustomPathId",
                "行走路线 | 自定义路线代码(debug)",
                "p0002023122214520001"
            ).withDesc("自定义路线调试代码，仅在开启行走路线且启用自定义路线时生效。").also { walkCustomPathId = it }
        )
        modelFields.addField(
            StringModelField(
                "walkThemeIds",
                "行走路线 | 自定义主题ID列表",
                ""
            ).withDesc("新版路线主题 ID 列表，支持逗号、空格或换行分隔；为空时兼容使用旧主题下拉配置。").also { walkThemeIds = it }
        )
        modelFields.addField(
            StringModelField(
                "walkPathIds",
                "行走路线 | 自定义路线ID列表",
                ""
            ).withDesc("新版路线 ID 列表，支持逗号、空格或换行分隔；非空时优先按路线列表选择。").also { walkPathIds = it }
        )
        modelFields.addField(
            BooleanModelField("walkThemeLoop", "行走路线 | 主题循环", false).withDesc(
                "开启后固定在当前主题或配置首个主题内循环所有路线；路线循环开启时优先路线循环。"
            ).also { walkThemeLoop = it }
        )
        modelFields.addField(
            BooleanModelField("walkRouteLoop", "行走路线 | 路线循环", false).withDesc(
                "开启后只循环当前路线；没有当前路线时使用自定义路线或主题中的第一条路线。"
            ).also { walkRouteLoop = it }
        )
        modelFields.addField(
            BooleanModelField("walkReviveSteps", "行走路线 | 复活步数", true).withDesc(
                "路线当日无可用步数时，尝试复活历史步数；耗尽后当天不再重复尝试。"
            ).also { walkReviveSteps = it }
        )
        modelFields.addField(
            BooleanModelField("walkReviveTask", "行走路线 | 复活任务", true).withDesc(
                "复活次数不足时尝试完成复活任务。"
            ).also { walkReviveTask = it }
        )

        // 旧版路线相关
        modelFields.addField(
            BooleanModelField("openTreasureBox", "开启宝箱", false).withDesc(
                "兼容旧版路线入口：在未开启新版行走路线时，自动处理旧版路线的加入、前进和宝箱领取。"
            ).also { openTreasureBox = it }
        )

        // 运动任务 & 能量球
        modelFields.addField(
            BooleanModelField("sportsTasks", "开启运动任务", false).withDesc(
                "执行运动任务面板中的签到、任务完成与奖励领取。"
            ).also { sportsTasksField = it }
        )
        modelFields.addField(
            BooleanModelField(
                "sportsEnergyBubble",
                "运动球任务(开启后有概率出现滑块验证)",
                false
            ).withDesc("处理首页推荐的运动球任务，可能触发滑块验证，不包含任务面板任务。").also { sportsEnergyBubble = it }
        )

        // 首页金币 & 捐步
        modelFields.addField(
            BooleanModelField("receiveCoinAsset", "收能量🎈", false).withDesc(
                "收取首页可领取的能量气球或运动币资源；关闭后不会在运动任务完成后顺带收取。"
            ).also { receiveCoinAssetField = it }
        )
        modelFields.addField(
            BooleanModelField("donateCharityCoin", "捐能量🎈 | 开启", false).withDesc(
                "自动把能量气球捐给公益项目。"
            ).also { donateCharityCoin = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "donateCharityCoinType",
                "捐能量🎈 | 方式",
                DonateCharityCoinType.ONE,
                DonateCharityCoinType.nickNames
            ).withDesc("控制只捐一个项目还是继续处理更多项目，仅在开启捐能量时生效。").also { donateCharityCoinType = it }
        )
        modelFields.addField(
            IntegerModelField("donateCharityCoinAmount", "捐能量🎈 | 数量(每次)", 100).withDesc(
                "每次捐赠的能量气球数量，仅在开启捐能量时生效。"
            )
                .also { donateCharityCoinAmount = it }
        )

        // 健康岛任务
        modelFields.addField(
            BooleanModelField("neverlandTask", "健康岛 | 任务", false).withDesc(
                "执行健康岛签到、任务大厅、浏览任务和泡泡领取。"
            ).also { neverlandTask = it }
        )
        modelFields.addField(
            BooleanModelField("neverlandGrid", "健康岛 | 自动走路建造", false).withDesc(
                "自动在健康岛走路建造，消耗可用步数并受今日走路最大次数限制。"
            ).also { neverlandGrid = it }
        )
        modelFields.addField(
            BooleanModelField("neverlandAutoReward", "健康岛 | 自动领奖", true).withDesc(
                "新游戏模式岛屿完成后自动选择奖励；明确非重试错误会记录并尝试下一个奖励。"
            ).also { neverlandAutoReward = it }
        )
        modelFields.addField(
            BooleanModelField("neverlandPreferMedal", "健康岛 | 优先奖牌", true).withDesc(
                "健康岛自动领奖时优先选择名称包含“奖牌”的奖励，否则选择服务端返回的首个奖励。"
            ).also { neverlandPreferMedal = it }
        )
        modelFields.addField(
            IntegerModelField("neverlandGridStepCount", "健康岛 | 今日走路最大次数", 20).withDesc(
                "健康岛当天最多执行的走路建造次数，仅在开启自动走路建造时生效。"
            )
                .also { neverlandGridStepCount = it }
        )

        // 抢好友相关
        modelFields.addField(
            BooleanModelField("battleForFriends", "抢好友 | 开启", false).withDesc(
                "执行抢好友大战主页收益收取与抢购好友逻辑。"
            ).also { battleForFriends = it }
        )
        modelFields.addField(
            BooleanModelField("battleAutoUnlockRoom", "抢好友 | 自动解锁场地", true).withDesc(
                "抢购好友前，若可购买新训练室且能量足够，则自动解锁并刷新主页状态。"
            ).also { battleAutoUnlockRoom = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "battleForFriendType",
                "抢好友 | 动作",
                BattleForFriendType.ROB,
                BattleForFriendType.nickNames
            ).withDesc("决定好友列表是“选中抢”还是“选中不抢”，仅在开启抢好友时生效。").also { battleForFriendType = it }
        )
        modelFields.addField(
            SelectModelField(
                "originBossIdList",
                "抢好友 | 好友列表",
                LinkedHashSet(),
                AlipayUser::getFriendList
            ).withDesc("配置抢好友规则作用的好友名单，名单解释方式由“抢好友 | 动作”决定。").also { originBossIdList = it }
        )

        // 训练好友相关
        modelFields.addField(
            BooleanModelField("trainFriend", "训练好友 | 开启", false).withDesc(
                "在抢好友大战中自动训练空闲好友，需同时开启“抢好友”。"
            ).also { trainFriend = it }
        )
        modelFields.addField(
            IntegerModelField("zeroCoinLimit", "训练好友 | 0金币上限次数当天关闭", 5).withDesc(
                "仅在开启训练好友时生效；连续收取 0 金币达到次数后，当天停止继续训练好友，设为 0 表示不限制。"
            )
                .also { zeroCoinLimit = it }
        )

        // 文体中心 & 捐步 & 步数同步
        modelFields.addField(BooleanModelField("tiyubiz", "文体中心", false).withDesc(
            "执行文体中心签到、任务、线路推进和走路挑战赛。"
        ).also { tiyubiz = it })
        modelFields.addField(
            IntegerModelField("minExchangeCount", "最小捐步步数", 0).withDesc(
                "旧版捐步兑换的触发阈值；设为 0 关闭该流程，最晚时间前不足阈值会继续累积。"
            ).also { minExchangeCount = it }
        )
        modelFields.addField(
            HourOfDayModelField("earliestSyncStepTime", "同步步数 | 最早同步时间", "0600").withDesc(
                "允许开始同步自定义步数的最早时间，仅在自定义同步步数大于 0 时生效。"
            )
                .also { earliestSyncStepTime = it }
        )
        modelFields.addField(
            HourOfDayModelField("latestExchangeTime", "最晚捐步时间", "2200", allowDayEnd = true).withDesc(
                "旧版捐步兑换的最晚等待时间；支持 24:00 作为日终截止，超过该时间后即使未达到最小捐步步数也会按当前步数尝试处理。"
            )
                .also { latestExchangeTime = it }
        )
        modelFields.addField(
            IntegerModelField("syncStepCount", "自定义同步步数", 22000).withDesc(
                "在当前真实步数基础上额外增加的步数基数，运行时会随机上浮 0~1999，设为 0 关闭自定义同步。"
            ).also { syncStepCount = it }
        )

        return modelFields
    }

    /**
     * @brief Xposed 启动时 hook 步数读取逻辑，实现自定义步数同步
     */
    override fun boot(classLoader: ClassLoader?) {
        if (classLoader == null) {
            Log.error(TAG, "ClassLoader is null, skip hook readDailyStep")
            return
        }
        try {
            val pedometerAgentClass = Class.forName(
                "com.alibaba.health.pedometer.core.datasource.PedometerAgent",
                false,
                classLoader
            )
            val readDailyStepMethod = pedometerAgentClass.getDeclaredMethod("readDailyStep").apply {
                isAccessible = true
            }
            ApplicationHook.requireXposedInterface().hook(readDailyStepMethod).intercept { chain ->
                val originStep = chain.proceed() as Int
                rememberCurrentDailyStep(originStep)
                val targetStep = resolveTargetDailyStep(originStep)
                if (shouldOverrideDailyStep(originStep, targetStep) && canOverrideDailyStepByHook()) {
                    if (!syncStepHookLogged) {
                        syncStepHookLogged = true
                        Log.sports(
                            "同步步数🏃🏻‍♂️[Hook][原始${originStep}步 + 自定义${targetStep - originStep}步 = ${targetStep}步]"
                        )
                    }
                    targetStep
                } else {
                    originStep
                }
            }
            Log.sports(TAG, "hook readDailyStep successfully")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "hook readDailyStep err:", t)
        }
    }

    /**
     * @brief 任务主入口
     */
    override fun runJava() {
        Log.sports(TAG, "执行开始-${getName()}")

        try {
            if (isEnergyOnlyModeNow()) {
                Log.sports(TAG, "⏸ 当前为只收能量时间【${BaseModel.energyTime.value}】，跳过运动任务")
                return
            }

            val loader = ApplicationHook.classLoader
            if (loader == null) {
                Log.error(TAG, "ClassLoader is null, 跳过运动任务")
                return
            }

            runNeverlandWorkflow()
            runStepSyncWorkflow()

            // 运动任务
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE) &&
                sportsTasksField.value == true) {
                sportsTasks()
            }

            // 运动球任务
            if (sportsEnergyBubble.value == true) {
                sportsEnergyBubbleTask()
            }

            runRouteWorkflow(loader)

            // 文体中心
            runSportsCenterWorkflow()

            // 抢好友大战
            runBattleForFriendsWorkflow()

            // 首页金币
            if (receiveCoinAssetField.value == true) {
                receiveCoinAsset()
            }

        } catch (t: Throwable) {
            Log.sports(TAG, "runJava error:")
            Log.printStackTrace(TAG, t)
        } finally {
            Log.sports(TAG, "执行结束-${getName()}")
        }
    }

    private fun tryBeginSyncStepTask(): Boolean {
        synchronized(syncStepLock) {
            if (syncStepInProgress ||
                hasChildTask(SYNC_STEP_CHILD_TASK_ID) ||
                Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
            ) {
                return false
            }
            syncStepInProgress = true
            return true
        }
    }

    private fun finishSyncStepTask() {
        synchronized(syncStepLock) {
            syncStepInProgress = false
        }
    }

    private fun markSyncStepDone() {
        synchronized(syncStepLock) {
            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
        }
    }

    private fun canOverrideDailyStepByHook(): Boolean {
        synchronized(syncStepLock) {
            return !Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
        }
    }

    internal fun isEnergyOnlyModeNow(): Boolean {
        TaskCommon.update()
        return TaskCommon.IS_ENERGY_TIME
    }

    /**
     * 步数同步任务
     */
    internal fun syncStepTask() {
        if (isEnergyOnlyModeNow()) {
            return
        }

        if (!tryBeginSyncStepTask()) {
            return
        }

        addChildTask(
            ChildModelTask(
                SYNC_STEP_CHILD_TASK_ID,
                Runnable {
                    try {
                        val customStep = tmpStepCount()
                        if (customStep <= 0) {
                            Log.sports(TAG, "同步步数已关闭，跳过主动同步")
                            markSyncStepDone()
                            return@Runnable
                        }

                        val loader = ApplicationHook.classLoader
                        if (loader == null) {
                            Log.error(TAG, "ClassLoader is null, 跳过同步步数")
                            return@Runnable
                        }

                        val originStep = queryCurrentWalkStepCount()
                            ?: cachedOriginDailyStep.takeIf { it >= 0 }
                            ?: 0
                        val targetStep = resolveTargetDailyStep(originStep)
                        if (targetStep <= originStep) {
                            Log.sports(
                                TAG,
                                "同步步数无需处理[原始=${originStep}步, 自定义=${customStep}步, 目标=${targetStep}步]"
                            )
                            markSyncStepDone()
                            return@Runnable
                        }

                        if (Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)) {
                            Log.sports(TAG, "今日步数已由其他入口同步，跳过主动提交")
                            return@Runnable
                        }

                        if (syncStepByRpcManager(loader, targetStep)) {
                            val confirmedStep = queryCurrentWalkStepCount()
                            if (confirmedStep != null && confirmedStep >= targetStep) {
                                Log.sports("同步步数🏃🏻‍♂️[原始${originStep}步 + 自定义${targetStep - originStep}步 = ${targetStep}步]")
                                markSyncStepDone()
                            } else {
                                Log.sports(
                                    TAG,
                                    "同步步数提交返回成功但查询未确认[当前=${confirmedStep ?: "未知"}步, 目标=${targetStep}步]，暂不标记今日已同步"
                                )
                            }
                        } else {
                            Log.sports(
                                TAG,
                                "主动同步入口未匹配，保留 readDailyStep Hook 等待运动页读取[原始=${originStep}步, 目标=${targetStep}步]"
                            )
                        }
                    } catch (t: Throwable) {
                        Log.printStackTrace(TAG, t)
                    } finally {
                        finishSyncStepTask()
                    }
                }
            )
        )
    }

    /**
     * @brief 计算今日用于同步的随机步数
     *
     * @return 步数值（最大 100000）
     */
    fun tmpStepCount(): Int {
        if (tmpStepCount >= 0) {
            return tmpStepCount
        }
        tmpStepCount = (syncStepCount.value ?: 0).coerceIn(0, 100_000)
        if (tmpStepCount > 0) {
            tmpStepCount = RandomUtil.nextInt(tmpStepCount, tmpStepCount + 2000)
            if (tmpStepCount > 100_000) {
                tmpStepCount = 100_000
            }
        }
        return tmpStepCount
    }

    private fun rememberCurrentDailyStep(originStep: Int) {
        if (originStep < 0) {
            return
        }
        val safeOriginStep = originStep.coerceAtLeast(0)
        if (cachedTargetDailyStep > 0 && safeOriginStep >= cachedTargetDailyStep) {
            return
        }
        if (safeOriginStep > cachedOriginDailyStep) {
            cachedOriginDailyStep = safeOriginStep
            cachedTargetDailyStep = -1
        }
    }

    private fun resolveTargetDailyStep(originStep: Int): Int {
        val customStep = tmpStepCount()
        val safeOriginStep = originStep.coerceAtLeast(0)
        if (customStep <= 0) {
            return safeOriginStep
        }
        if (cachedTargetDailyStep > 0 && safeOriginStep >= cachedTargetDailyStep) {
            return safeOriginStep
        }
        if (cachedOriginDailyStep < 0 || safeOriginStep > cachedOriginDailyStep) {
            cachedOriginDailyStep = safeOriginStep
            cachedTargetDailyStep = -1
        }
        if (cachedTargetDailyStep < 0) {
            cachedTargetDailyStep = (cachedOriginDailyStep + customStep).coerceAtMost(100_000)
        }
        return cachedTargetDailyStep.coerceAtLeast(safeOriginStep)
    }

    internal fun isSyncStepEnabled(): Boolean {
        return (syncStepCount.value ?: 0) > 0
    }

    private fun getTrainFriendZeroCoinLimit(): Int? {
        if (trainFriend.value != true) {
            return null
        }
        val maxCount = zeroCoinLimit.value ?: return null
        return maxCount.takeIf { it > 0 }
    }

    private fun hasReachedTrainFriendZeroCoinLimit(): Boolean {
        val maxCount = getTrainFriendZeroCoinLimit() ?: return false
        return zeroTrainCoinCount >= maxCount
    }

    private fun shouldOverrideDailyStep(originStep: Int, targetStep: Int): Boolean {
        if (targetStep <= 0 ||
            originStep >= targetStep
        ) {
            return false
        }
        if (isEnergyOnlyModeNow()) {
            return false
        }

        return if (::earliestSyncStepTime.isInitialized) {
            earliestSyncStepTime.hasReachedToday()
        } else {
            HourOfDayModelField("defaultEarliestSyncStepTime", "默认最早同步时间", "0800").hasReachedToday()
        }
    }

    private fun queryCurrentWalkStepCount(): Int? {
        return try {
            RpcCache.invalidate(AntSportsRpcCall.QUERY_WALK_STEP_RPC)
            val response = JSONObject(AntSportsRpcCall.queryWalkStep())
            if (!ResChecker.checkRes(TAG, response)) {
                Log.sports(TAG, "查询当前步数失败，回退到 Hook 实时步数")
                null
            } else {
                val currentStep = AntSportsRpcCall.extractWalkStepCount(response).coerceAtLeast(0)
                rememberCurrentDailyStep(currentStep)
                currentStep
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryCurrentWalkStepCount err:", t)
            null
        }
    }

    private fun syncStepByRpcManager(loader: ClassLoader, step: Int): Boolean {
        val candidateClassNames = listOf(
            "com.alibaba.health.pedometer.intergation.rpc.RpcManager",
            "com.alibaba.health.pedometer.integration.rpc.RpcManager"
        )

        for (className in candidateClassNames) {
            val rpcManagerClass = runCatching { loader.loadClass(className) }.getOrNull() ?: continue
            val syncMethods = findSyncStepMethods(rpcManagerClass)
            if (syncMethods.isEmpty()) {
                continue
            }

            for (method in syncMethods) {
                val targets = if (Modifier.isStatic(method.modifiers)) {
                    listOf<Any?>(null)
                } else {
                    collectRpcManagerInstances(rpcManagerClass)
                }

                for (target in targets) {
                    if (invokeSyncStepMethod(method, target, step)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun findSyncStepMethods(clazz: Class<*>): List<Method> {
        return (clazz.declaredMethods.toList() + clazz.methods.toList())
            .distinctBy { method ->
                buildString {
                    append(method.name)
                    append('#')
                    append(method.parameterTypes.joinToString(",") { it.name })
                }
            }
            .filter { method ->
                val parameterTypes = method.parameterTypes
                if (parameterTypes.size != 3) {
                    return@filter false
                }

                val firstArgMatches = parameterTypes[0] == Int::class.javaPrimitiveType ||
                    parameterTypes[0] == Int::class.javaObjectType ||
                    parameterTypes[0] == Long::class.javaPrimitiveType ||
                    parameterTypes[0] == Long::class.javaObjectType
                val secondArgMatches = parameterTypes[1] == Boolean::class.javaPrimitiveType ||
                    parameterTypes[1] == Boolean::class.javaObjectType
                val thirdArgMatches = parameterTypes[2] == String::class.java ||
                    CharSequence::class.java.isAssignableFrom(parameterTypes[2]) ||
                    parameterTypes[2] == Any::class.java

                firstArgMatches && secondArgMatches && thirdArgMatches
            }
            .sortedByDescending(::scoreSyncStepMethod)
    }

    private fun scoreSyncStepMethod(method: Method): Int {
        var score = 0
        if (method.returnType == Boolean::class.javaPrimitiveType ||
            method.returnType == Boolean::class.javaObjectType
        ) {
            score += 4
        }
        if (method.name == "a") {
            score += 2
        }
        if (Modifier.isPublic(method.modifiers)) {
            score += 1
        }
        return score
    }

    private fun collectRpcManagerInstances(clazz: Class<*>): List<Any?> {
        val instances = LinkedHashSet<Any?>()

        runCatching {
            val instanceField = clazz.getDeclaredField("INSTANCE")
            if (Modifier.isStatic(instanceField.modifiers)) {
                instanceField.isAccessible = true
                instances.add(instanceField.get(null))
            }
        }

        val singletonMethodNames = setOf("a", "getInstance", "instance")
        (clazz.declaredMethods.toList() + clazz.methods.toList())
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType != Void.TYPE &&
                    (method.name in singletonMethodNames || clazz.isAssignableFrom(method.returnType))
            }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    instances.add(method.invoke(null))
                }
            }

        runCatching {
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            instances.add(constructor.newInstance())
        }

        return instances.filterNotNull()
    }

    private fun invokeSyncStepMethod(method: Method, target: Any?, step: Int): Boolean {
        return runCatching {
            method.isAccessible = true
            val stepArg: Any = when (method.parameterTypes[0]) {
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> step.toLong()
                else -> step
            }
            val result = method.invoke(target, stepArg, false, "system")
            when (result) {
                is Boolean -> result
                null -> method.returnType == Void.TYPE
                else -> true
            }
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------------
    // 运动任务面板
    // ---------------------------------------------------------------------

    /**
     * @brief 处理运动任务面板中的任务（含签到、完成、领奖）
     */
    private fun sportsTasks() {
        try {
            sportsCheckIn()
            val failedCompleteTaskIds = mutableSetOf<String>()
            val failedReceiveTaskIds = mutableSetOf<String>()
            var round = 1
            var roundLimit = 1

            while (round <= roundLimit) {
                val jo = JSONObject(AntSportsRpcCall.queryCoinTaskPanel())
                if (!ResChecker.checkRes(TAG, jo)) {
                    return
                }

                val data = jo.optJSONObject("data") ?: return
                val taskList = data.optJSONArray("taskList") ?: return
                if (round == 1) {
                    roundLimit = estimateSportsPanelTaskRoundLimit(taskList)
                }

                var totalTasks = 0
                var completedTasks = 0
                var availableTasks = 0
                var progressed = false
                var stopCurrentRound = false
                var shouldRequeryImmediately = false

                taskLoop@ for (i in 0 until taskList.length()) {
                    val taskDetail = taskList.optJSONObject(i) ?: continue
                    val taskId = taskDetail.optString("taskId", "")
                    val taskName = taskDetail.optString("taskName", taskId)
                    val taskStatus = taskDetail.optString("taskStatus", "")
                    val taskType = taskDetail.optString("taskType", "")

                    if (taskType == "SETTLEMENT") continue

                    val isBlacklisted =
                        TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId) ||
                            TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskName)
                    if (isBlacklisted && taskStatus != "WAIT_RECEIVE") {
                        continue
                    }

                    totalTasks++

                    when (taskStatus) {
                        "HAS_RECEIVED" -> {
                            completedTasks++
                        }
                        "WAIT_RECEIVE" -> {
                            availableTasks++
                            val receiveKey = buildSportsPanelReceiveKey(taskDetail, taskId)
                            if (receiveKey in failedReceiveTaskIds) {
                                Log.sports(TAG, "运动任务面板[本轮已跳过领取失败任务：$taskName]")
                                continue@taskLoop
                            }
                            if (receiveTaskReward(taskDetail, taskName)) {
                                progressed = true
                                shouldRequeryImmediately = true
                            } else {
                                failedReceiveTaskIds.add(receiveKey)
                            }
                        }
                        "WAIT_COMPLETE" -> {
                            availableTasks++
                            val completeKey = buildSportsPanelCompleteKey(taskDetail, taskId)
                            if (completeKey in failedCompleteTaskIds) {
                                Log.sports(TAG, "运动任务面板[本轮已跳过完成失败任务：$taskName]")
                                continue@taskLoop
                            }
                            when (completeTask(taskDetail, taskName)) {
                                SportsPanelTaskCompleteResult.SUCCESS -> {
                                    progressed = true
                                    shouldRequeryImmediately = true
                                }

                                SportsPanelTaskCompleteResult.FAILED -> {
                                    failedCompleteTaskIds.add(completeKey)
                                }

                                SportsPanelTaskCompleteResult.STOP_CURRENT_ROUND -> {
                                    failedCompleteTaskIds.add(completeKey)
                                    if (!stopCurrentRound) {
                                        Log.sports(TAG, "运动任务面板[本轮止损：检测到离线/验证类错误，停止继续执行剩余浏览任务]")
                                    }
                                    stopCurrentRound = true
                                }
                            }
                        }
                        else -> {
                            Log.error(TAG, "做任务得能量🎈[未知状态：$taskName，状态：$taskStatus]")
                        }
                    }

                    if (shouldRequeryImmediately || stopCurrentRound) {
                        break
                    }
                }

                Log.sports(TAG, "运动任务完成情况：$completedTasks/$totalTasks，剩余待处理：$availableTasks，轮次：$round")

                if (totalTasks > 0 && completedTasks >= totalTasks && availableTasks == 0) {
                    val today = TimeUtil.getDateStr2()
                    DataStore.put(SPORTS_TASKS_COMPLETED_DATE, today)
                    Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE)
                    Log.sports(TAG, "✅ 所有运动任务已完成，今日不再执行")
                    return
                }

                if (stopCurrentRound || !progressed) {
                    return
                }

                GlobalThreadPools.sleepCompat(1000)
                round++
            }

            Log.error(TAG, "运动任务面板[达到动态轮次上限$roundLimit，停止以避免重复循环]")
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    private fun estimateSportsPanelTaskRoundLimit(taskList: JSONArray): Int {
        var visibleTaskCount = 0
        var pendingTransitions = 0
        for (i in 0 until taskList.length()) {
            val taskDetail = taskList.optJSONObject(i) ?: continue
            if (taskDetail.optString("taskType", "") == "SETTLEMENT") continue
            visibleTaskCount++

            when (taskDetail.optString("taskStatus", "")) {
                "WAIT_RECEIVE" -> pendingTransitions += 1
                "WAIT_COMPLETE" -> {
                    val currentNum = taskDetail.optInt("currentNum", 0)
                    val limitConfigNum = taskDetail.optInt("limitConfigNum", currentNum + 1)
                    val remainingNum = max(1, limitConfigNum - currentNum)
                    pendingTransitions += remainingNum * 2
                }
            }
        }
        return max(1, pendingTransitions + visibleTaskCount)
    }

    private fun buildSportsPanelReceiveKey(taskDetail: JSONObject, taskId: String): String {
        val assetId = taskDetail.optString("assetId", "").trim()
        return "$taskId:${assetId.ifBlank { "NO_ASSET" }}"
    }

    private fun buildSportsPanelCompleteKey(taskDetail: JSONObject, taskId: String): String {
        val currentNum = taskDetail.optInt("currentNum", 0)
        val bizId = resolveSportsPanelAdTaskBizId(taskDetail).ifBlank { taskDetail.optString("taskAction", "") }
        return "$taskId:$currentNum:$bizId"
    }

    /**
     * @brief 领取单个任务奖励
     *
     * @param taskDetail 任务详情 JSON
     * @param taskName   任务名称
     * @return 是否视为成功
     */
    private fun receiveTaskReward(taskDetail: JSONObject, taskName: String): Boolean {
        return try {
            val assetId = taskDetail.getString("assetId")
            val prizeAmount = taskDetail.getInt("prizeAmount").toString()

            val result = AntSportsRpcCall.pickBubbleTaskEnergy(assetId)
            val resultData = JSONObject(result)

            if (isSportsRpcSuccess(resultData)) {
                Log.sports("做任务得能量🎈[$taskName] +$prizeAmount 能量")
                true
            } else {
                val errorMsg = extractSportsRpcErrorMessage(resultData)
                val errorCode = extractSportsRpcErrorCode(resultData)
                if (errorCode == "RECEIVE_REWARD_REPEATED") {
                    Log.sports(
                        TAG,
                        "做任务得能量🎈[奖励已领取：$taskName，按完成处理：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg}]"
                    )
                    true
                } else if (errorCode == "CAMP_TRIGGER_ERROR") {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[领取失败-业务RPC受限：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg]"
                    )
                    false
                } else if (!isSportsRpcRetryable(resultData)) {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[领取失败-非重试RPC：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg]"
                    )
                    false
                } else {
                    Log.error(TAG, "做任务得能量🎈[领取失败：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg]")
                    false
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "做任务得能量🎈[领取异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /**
     * @brief 推进一次任务完成状态
     */
    private fun completeTask(taskDetail: JSONObject, taskName: String): SportsPanelTaskCompleteResult {
        return try {
            val taskId = taskDetail.getString("taskId")
            val prizeAmount = taskDetail.getString("prizeAmount")
            val currentNum = taskDetail.getInt("currentNum")
            val limitConfigNum = taskDetail.getInt("limitConfigNum")
            val remainingNum = limitConfigNum - currentNum
            val needSignUp = taskDetail.optBoolean("needSignUp", false)
            val taskAction = taskDetail.optString("taskAction", "JUMP").ifBlank { "JUMP" }
            val requestTaskType = resolveSportsPanelCompleteRequestTaskType(taskDetail, taskAction)
            val adTaskBizId = resolveSportsPanelAdTaskBizId(taskDetail)
            val adTaskFinishPayload = resolveSportsPanelAdTaskFinishPayload(taskDetail, adTaskBizId)
            val adTaskPayloadBizId = adTaskFinishPayload.optString("bizId", "").trim()

            if (remainingNum <= 0) {
                return SportsPanelTaskCompleteResult.SUCCESS
            }

            // 需要先签到
            if (needSignUp) {
                if (!signUpForTask(taskId, taskName)) {
                    return SportsPanelTaskCompleteResult.FAILED
                }
                GlobalThreadPools.sleepCompat(2000)
            }

            val useVerifiedNewCompleteRpc =
                taskAction.equals("SHOW_AD", ignoreCase = true) &&
                    !requestTaskType.isNullOrBlank()
            val useAdTaskFinishRpc =
                taskAction.equals("JUMP", ignoreCase = true) &&
                    taskDetail.optBoolean("adTask", false) &&
                    adTaskPayloadBizId.isNotBlank()
            val result = when {
                useVerifiedNewCompleteRpc -> JSONObject(
                    AntSportsRpcCall.completeTask(
                        taskId = taskId,
                        taskAction = taskAction,
                        taskType = requestTaskType
                    )
                )
                useAdTaskFinishRpc -> JSONObject(AntSportsRpcCall.finishAdTask(adTaskFinishPayload))
                else -> JSONObject(AntSportsRpcCall.completeExerciseTasks(taskId))
            }

            val progressText = "#(${min(currentNum + 1, limitConfigNum)}/$limitConfigNum)"
            if (isSportsRpcSuccess(result)) {
                val completeSource = when {
                    useVerifiedNewCompleteRpc -> "completeTask"
                    useAdTaskFinishRpc -> "adtask.finish"
                    else -> "completeTask(JUMP)"
                }
                Log.sports(
                    TAG,
                    "做任务得能量🎈[完成任务：$taskName，得$prizeAmount💰，方式：$completeSource]$progressText"
                )
                SportsPanelTaskCompleteResult.SUCCESS
            } else {
                val errorCode = extractSportsRpcErrorCode(result)
                val errorMsg = extractSportsRpcErrorMessage(result)
                if (shouldTemporarilyStopSportsTask(errorCode, errorMsg)) {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[任务失败-本轮止损：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg}]$progressText"
                    )
                    return SportsPanelTaskCompleteResult.STOP_CURRENT_ROUND
                }

                val shouldAutoBlacklist =
                    errorCode.isNotEmpty() &&
                        errorCode != "CAMP_TRIGGER_ERROR" &&
                        errorCode != "RECEIVE_REWARD_REPEATED"
                if (shouldAutoBlacklist) {
                    TaskBlacklist.autoAddToBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId, taskName, errorCode)
                }
                if (errorCode == "CAMP_TRIGGER_ERROR") {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[任务失败-业务RPC受限：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg}]$progressText"
                    )
                    return SportsPanelTaskCompleteResult.FAILED
                }
                if (!isSportsRpcRetryable(result)) {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[任务失败-非重试RPC：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg}]$progressText"
                    )
                    return SportsPanelTaskCompleteResult.FAILED
                }
                if (errorCode == "RECEIVE_REWARD_REPEATED") {
                    Log.error(
                        TAG,
                        "做任务得能量🎈[任务失败-状态异常：$taskName，completeTask 返回重复领奖错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg}]$progressText"
                    )
                    return SportsPanelTaskCompleteResult.FAILED
                }
                Log.error(
                    TAG,
                    "做任务得能量🎈[任务失败：$taskName，错误：${errorCode.ifEmpty { "UNKNOWN" }} - $errorMsg]$progressText"
                )
                SportsPanelTaskCompleteResult.FAILED
            }
        } catch (e: Exception) {
            Log.error(TAG, "做任务得能量🎈[执行异常：$taskName，错误：${e.message}]")
            SportsPanelTaskCompleteResult.FAILED
        }
    }

    private fun resolveSportsPanelCompleteRequestTaskType(taskDetail: JSONObject, taskAction: String): String? {
        if (taskAction.equals("SHOW_AD", ignoreCase = true) && taskDetail.optBoolean("adTask", false)) {
            return "AD_TASK"
        }
        return null
    }

    private fun resolveSportsPanelAdTaskBizId(taskDetail: JSONObject): String {
        return taskDetail.optJSONObject("bizExtMap")
            ?.optString("bizId", "")
            ?.trim()
            .orEmpty()
    }

    private fun resolveSportsPanelAdTaskFinishPayload(taskDetail: JSONObject, adTaskBizId: String): JSONObject {
        val payloadFromUrl = extractSportsPanelAdTaskOpParam(taskDetail.optString("taskUrl", ""))
        if (payloadFromUrl != null) {
            if (payloadFromUrl.optString("bizId", "").isBlank() && adTaskBizId.isNotBlank()) {
                payloadFromUrl.put("bizId", adTaskBizId)
            }
            return payloadFromUrl
        }

        return JSONObject().apply {
            put("bizId", adTaskBizId)
        }
    }

    private fun extractSportsPanelAdTaskOpParam(taskUrl: String): JSONObject? {
        if (taskUrl.isBlank()) return null
        val opType = extractDecodedUrlParam(taskUrl, "opType")
        if (opType != "com.alipay.adtask.biz.mobilegw.service.task.finish") {
            return null
        }
        val opParam = extractDecodedUrlParam(taskUrl, "opParam")
        if (opParam.isBlank()) return null
        return runCatching { JSONObject(opParam) }.getOrNull()
    }

    private fun extractDecodedUrlParam(rawUrl: String, paramName: String): String {
        for (candidate in listOf(rawUrl, decodeUrlComponentRepeated(rawUrl))) {
            val pattern = Regex("""(?:[?&])${Regex.escape(paramName)}=([^&#]*)""")
            val match = pattern.find(candidate) ?: continue
            return decodeUrlComponentRepeated(match.groupValues[1])
        }
        return ""
    }

    private fun decodeUrlComponentRepeated(value: String, maxRounds: Int = 3): String {
        var current = value
        repeat(maxRounds) {
            val decoded = runCatching { URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
            if (decoded == current) {
                return current
            }
            current = decoded
        }
        return current
    }

    /**
     * @brief 为任务执行报名
     */
    private fun signUpForTask(taskId: String, taskName: String): Boolean {
        return try {
            val result = AntSportsRpcCall.signUpTask(taskId)
            val resultData = JSONObject(result)

            if (ResChecker.checkRes(TAG, resultData)) {
                val data = resultData.optJSONObject("data")
                val taskOrderId = data?.optString("taskOrderId", "") ?: ""
                Log.sports("做任务得能量🎈[签到成功：$taskName，订单：$taskOrderId]")
                true
            } else {
                val errorMsg = resultData.optString("errorMsg", "未知错误")
                Log.error(TAG, "做任务得能量🎈[签到失败：$taskName，错误：$errorMsg]")
                false
            }
        } catch (e: Exception) {
            Log.error(TAG, "做任务得能量🎈[签到异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /**
     * @brief 运动首页推荐能量球任务
     *
     * @details
     * - 使用 {@link AntSportsRpcCall#queryEnergyBubbleModule} 获取首页 recBubbleList
     * - 区分待完成 task_bubble 与待领取 receive_coin_bubble
     * - 浏览类任务统一直接提交完成/领取 RPC，待领取气泡统一走 pickBubbleTaskEnergy 收取
     */
    private fun buildSportsHomeBubbleCooldownFlag(taskId: String): String {
        return StatusFlags.FLAG_ANTSPORTS_HOME_BUBBLE_COOLDOWN_PREFIX + taskId
    }

    private fun isSportsRpcSuccess(result: JSONObject): Boolean {
        if (result.has("success")) {
            return result.optBoolean("success", false)
        }
        if (result.has("isSuccess")) {
            return result.optBoolean("isSuccess", false)
        }

        val resultCode = result.opt("resultCode")
        when (resultCode) {
            is Number -> {
                val code = resultCode.toInt()
                if (code == 200 || code == 100) {
                    return true
                }
                return false
            }

            is String -> {
                if (
                    resultCode.equals("SUCCESS", ignoreCase = true) ||
                    resultCode == "100" ||
                    resultCode == "200"
                ) {
                    return true
                }
                if (resultCode.isNotBlank()) {
                    return false
                }
            }
        }

        val directErrorCode = result.optString("errorCode", "").trim()
        if (directErrorCode.isNotEmpty()) {
            return false
        }
        val errorValue = result.opt("error")?.toString()?.trim().orEmpty()
        if (errorValue.isNotEmpty() && errorValue != "0") {
            return false
        }

        val resultDesc = result.optString("resultDesc", "").trim()
        if (resultDesc == "成功" || resultDesc == "处理成功") {
            return true
        }
        val resultView = result.optString("resultView", "").trim()
        if (resultView == "成功" || resultView == "处理成功") {
            return true
        }

        return result.optString("memo", "").equals("SUCCESS", ignoreCase = true)
    }

    private fun isSportsRpcRetryable(result: JSONObject): Boolean {
        if (result.has("retryable")) {
            return result.optBoolean("retryable", true)
        }
        if (result.has("retriable")) {
            return result.optBoolean("retriable", true)
        }
        return true
    }

    private fun extractSportsRpcErrorCode(result: JSONObject): String {
        val directErrorCode = result.optString("errorCode", "").trim()
        if (directErrorCode.isNotEmpty()) {
            return directErrorCode
        }

        val resultCode = result.optString("resultCode", "").trim()
        if (resultCode.isNotEmpty() && !"SUCCESS".equals(resultCode, ignoreCase = true)) {
            return resultCode
        }

        val errorValue = result.opt("error")?.toString()?.trim().orEmpty()
        if (errorValue.isNotEmpty() && errorValue != "0") {
            return errorValue
        }

        val errorTip = result.optString("errorTip", "").trim()
        if (errorTip.isNotEmpty()) {
            return errorTip
        }

        val errorNo = result.opt("errorNo")?.toString()?.trim().orEmpty()
        if (errorNo.isNotEmpty() && errorNo != "0") {
            return errorNo
        }

        return ""
    }

    private fun extractSportsRpcErrorMessage(result: JSONObject): String {
        return sequenceOf(
            result.optString("errorMsg", "").trim(),
            result.optString("errorDesc", "").trim(),
            result.optString("resultDesc", "").trim(),
            result.optString("resultMessage", "").trim(),
            result.optString("resultMsg", "").trim(),
            result.optString("errorMessage", "").trim(),
            result.optString("desc", "").trim(),
            result.optString("errorTip", "").trim()
        ).firstOrNull { it.isNotEmpty() } ?: "未知错误"
    }

    private fun extractSportsHomeBubbleErrorCode(result: JSONObject): String {
        return extractSportsRpcErrorCode(result)
    }

    private fun extractSportsHomeBubbleErrorMessage(result: JSONObject): String {
        return extractSportsRpcErrorMessage(result)
    }

    private fun shouldTemporarilyStopSportsTask(errorCode: String, errorMsg: String): Boolean {
        if (errorCode == "1009" || errorCode == "I07" || errorCode == "USER_FREQUENTLY_LOCK") {
            return true
        }

        val errorText = "$errorCode $errorMsg"
        return errorText.contains("离线模式") ||
            errorText.contains("離線模式") ||
            errorText.contains("需要验证") ||
            errorText.contains("需要驗證") ||
            errorText.contains("访问被拒绝") ||
            errorText.contains("訪問被拒絕") ||
            errorText.contains("手速太快") ||
            errorText.contains("频繁") ||
            errorText.contains("頻繁")
    }

    private fun shouldCooldownSportsHomeBubbleTask(result: JSONObject): Boolean {
        val errorCode = extractSportsHomeBubbleErrorCode(result)
        val errorMsg = extractSportsHomeBubbleErrorMessage(result)
        if (errorCode == "CAMP_TRIGGER_ERROR" || errorCode == "1009") {
            return true
        }
        if (errorCode != "I07") {
            return false
        }
        if (ApplicationHookConstants.isOffline() && ApplicationHookConstants.offlineReason == "auth_like") {
            return true
        }

        val errorText = "$errorCode $errorMsg"
        return errorText.contains("离线模式") ||
            errorText.contains("離線模式") ||
            errorText.contains("需要验证") ||
            errorText.contains("需要驗證") ||
            errorText.contains("访问被拒绝") ||
            errorText.contains("訪問被拒絕")
    }

    private fun collectSportsHomeRewardScanResult(recBubbleList: JSONArray): SportsHomeRewardScanResult {
        val scanResult = SportsHomeRewardScanResult()
        for (i in 0 until recBubbleList.length()) {
            val bubble = recBubbleList.optJSONObject(i) ?: continue
            val task = bubble.optJSONObject("task")
            val bubbleType = bubble.optString("bubbleType", "")
            val taskStatus = task?.optString("taskStatus", "").orEmpty()
            if (bubbleType != "receive_coin_bubble" && taskStatus != "WAIT_RECEIVE") {
                continue
            }

            val recordId = sequenceOf(
                bubble.optString("assetId", ""),
                task?.optString("assetId", "").orEmpty(),
                bubble.optString("medEnergyBallInfoRecordId", ""),
                task?.optString("medEnergyBallInfoRecordId", "").orEmpty()
            ).map { it.trim() }.firstOrNull { it.isNotEmpty() }

            if (recordId == null) {
                scanResult.missingRecordIdCount++
                continue
            }

            val sourceName = task?.optString(
                "taskName",
                bubble.optString("simpleSourceName", "运动首页")
            ) ?: bubble.optString("simpleSourceName", "运动首页")
            val taskId = task?.optString("taskId", bubble.optString("channel", "")) ?: bubble.optString("channel", "")
            val coinAmount = if (bubble.optInt("coinAmount", 0) > 0) {
                bubble.optInt("coinAmount", 0)
            } else {
                task?.optInt("prizeAmount", 0) ?: 0
            }

            if (!scanResult.candidates.containsKey(recordId)) {
                scanResult.candidates[recordId] = SportsHomeRewardCandidate(
                    recordId = recordId,
                    sourceName = sourceName,
                    taskId = taskId,
                    coinAmount = coinAmount
                )
            }
        }
        return scanResult
    }

    private fun mergeSportsHomeRewardScanResults(
        base: SportsHomeRewardScanResult,
        extra: SportsHomeRewardScanResult
    ): SportsHomeRewardScanResult {
        val merged = SportsHomeRewardScanResult(
            candidates = LinkedHashMap(base.candidates),
            missingRecordIdCount = base.missingRecordIdCount + extra.missingRecordIdCount
        )
        for ((recordId, candidate) in extra.candidates) {
            if (!merged.candidates.containsKey(recordId)) {
                merged.candidates[recordId] = candidate
            }
        }
        return merged
    }

    private fun querySportsHomeRewardScanResult(): SportsHomeRewardScanResult? {
        val response = JSONObject(AntSportsRpcCall.queryEnergyBubbleModule())
        if (!ResChecker.checkRes(TAG, response)) {
            Log.error(TAG, "运动首页任务[刷新奖励气泡失败] raw=$response")
            return null
        }
        val recBubbleList = response.optJSONObject("data")?.optJSONArray("recBubbleList") ?: return SportsHomeRewardScanResult()
        return collectSportsHomeRewardScanResult(recBubbleList)
    }

    private fun receiveSportsHomeRewardCandidates(
        scanResult: SportsHomeRewardScanResult,
        handledRecordIds: MutableSet<String>
    ): Boolean {
        var receivedAny = false
        for (candidate in scanResult.candidates.values) {
            if (candidate.recordId in handledRecordIds) {
                Log.sports(
                    TAG,
                    "运动首页任务[跳过本轮已处理奖励：${candidate.sourceName}，taskId=${candidate.taskId}，recordId=${candidate.recordId}]"
                )
                continue
            }
            val response = JSONObject(AntSportsRpcCall.pickBubbleTaskEnergy(candidate.recordId, false))
            if (isSportsRpcSuccess(response)) {
                receivedAny = true
                handledRecordIds.add(candidate.recordId)
                val dataObj = response.optJSONObject("data")
                val changeAmount =
                    dataObj?.optString("changeAmount", candidate.coinAmount.toString()) ?: candidate.coinAmount.toString()
                val balance = dataObj?.optString("balance", "") ?: ""
                Log.sports(
                    TAG,
                    "运动首页任务[领取奖励：${candidate.sourceName}，taskId=${candidate.taskId}，recordId=${candidate.recordId}，coin=$changeAmount，balance=${balance.ifBlank { "unknown" }}]"
                )
            } else {
                val errorCode = extractSportsRpcErrorCode(response)
                val errorMsg = extractSportsRpcErrorMessage(response)
                Log.error(
                    TAG,
                    "运动首页任务[领取奖励失败：${candidate.sourceName}，taskId=${candidate.taskId}，recordId=${candidate.recordId}，code=${errorCode.ifEmpty { "UNKNOWN" }}，msg=$errorMsg] raw=$response"
                )
                if (!isSportsRpcRetryable(response)) {
                    handledRecordIds.add(candidate.recordId)
                }
            }
        }
        return receivedAny
    }

    private fun processClubRoomBubbleRewards(clubHomeData: JSONObject) {
        processBubbleList(clubHomeData.optJSONObject("mainRoom"))
        if (hasReachedTrainFriendZeroCoinLimit()) {
            return
        }
        val roomList = clubHomeData.optJSONArray("roomList") ?: return
        for (i in 0 until roomList.length()) {
            val room = roomList.optJSONObject(i)
            processBubbleList(room)
            if (hasReachedTrainFriendZeroCoinLimit()) {
                return
            }
        }
    }

    private fun queryClubHomeForTraining(): JSONObject? {
        val clubHomeData = JSONObject(AntSportsRpcCall.queryClubHome())
        if (!isSportsRpcSuccess(clubHomeData)) {
            val errorCode = extractSportsRpcErrorCode(clubHomeData)
            val errorMsg = extractSportsRpcErrorMessage(clubHomeData)
            Log.error(
                TAG,
                "训练好友[queryClubHome失败][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$clubHomeData"
            )
            return null
        }
        return clubHomeData
    }

    private fun findNextTrainTarget(
        clubHomeData: JSONObject,
        skippedOriginBossIds: Set<String> = emptySet()
    ): SportsTrainTarget? {
        val mainRoomTarget = findTrainTargetInRoom(
            clubHomeData.optJSONObject("mainRoom"),
            skippedOriginBossIds
        )
        if (mainRoomTarget != null) {
            return mainRoomTarget
        }

        val roomList = clubHomeData.optJSONArray("roomList") ?: return null
        for (i in 0 until roomList.length()) {
            val room = roomList.optJSONObject(i) ?: continue
            val target = findTrainTargetInRoom(room, skippedOriginBossIds)
            if (target != null) {
                return target
            }
        }
        return null
    }

    private fun findTrainTargetInRoom(
        room: JSONObject?,
        skippedOriginBossIds: Set<String>
    ): SportsTrainTarget? {
        val memberList = room?.optJSONArray("memberList") ?: return null
        for (j in 0 until memberList.length()) {
            val member = memberList.optJSONObject(j) ?: continue
            val trainInfo = member.optJSONObject("trainInfo") ?: continue
            if (trainInfo.optBoolean("training", false)) {
                continue
            }

            val memberId = member.optString("memberId", "")
            val originBossId = member.optString("originBossId", "")
            if (memberId.isBlank() || originBossId.isBlank()) {
                continue
            }
            if (originBossId in skippedOriginBossIds) {
                continue
            }
            if (FriendGuard.shouldSkipFriend(originBossId, TAG, "训练好友")) {
                continue
            }
            val userName = UserMap.getMaskName(originBossId) ?: originBossId
            return SportsTrainTarget(
                memberId = memberId,
                originBossId = originBossId,
                userName = userName
            )
        }
        return null
    }

    private fun queryBestTrainItemSelection(): SportsTrainItemSelection? {
        val responseJson = JSONObject(AntSportsRpcCall.queryTrainItem())
        if (!isSportsRpcSuccess(responseJson)) {
            val errorCode = extractSportsRpcErrorCode(responseJson)
            val errorMsg = extractSportsRpcErrorMessage(responseJson)
            Log.error(
                TAG,
                "训练好友[queryTrainItem失败][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$responseJson"
            )
            return null
        }

        var bizId = responseJson.optString("bizId", "")
        if (bizId.isBlank() && responseJson.has("taskDetail")) {
            bizId = responseJson.optJSONObject("taskDetail")?.optString("taskId", "").orEmpty()
        }

        val trainItemList = responseJson.optJSONArray("trainItemList")
        if (bizId.isBlank() || trainItemList == null || trainItemList.length() == 0) {
            Log.error(TAG, "训练好友[queryTrainItem缺少bizId或trainItemList] raw=$responseJson")
            return null
        }

        var bestItem: JSONObject? = null
        var bestProduction = -1
        for (i in 0 until trainItemList.length()) {
            val item = trainItemList.optJSONObject(i) ?: continue
            val production = item.optInt("production", 0)
            if (production > bestProduction) {
                bestProduction = production
                bestItem = item
            }
        }

        val selected = bestItem ?: return null
        return SportsTrainItemSelection(
            bizId = bizId,
            itemType = selected.optString("itemType", ""),
            itemName = selected.optString("name", "")
        ).takeIf { it.itemType.isNotBlank() && it.itemName.isNotBlank() }
    }

    private fun isSportsRouteBusinessTerminal(errorCode: String, errorMsg: String): Boolean {
        return errorCode == "AE950002" ||
            errorCode == "AE960231" ||
            errorMsg.contains("已参加路线") ||
            errorMsg.contains("路线已完成")
    }

    private fun sportsEnergyBubbleTask() {
        try {
            var round = 1
            val handledSportsHomeRewardRecordIds = mutableSetOf<String>()
            while (round <= MAX_SPORTS_HOME_BUBBLE_ROUNDS) {
                val jo = JSONObject(AntSportsRpcCall.queryEnergyBubbleModule())
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error(TAG, "queryEnergyBubbleModule fail: $jo")
                    return
                }

                val data = jo.optJSONObject("data") ?: return
                if (!data.has("recBubbleList")) return

                val recBubbleList = data.optJSONArray("recBubbleList") ?: return
                if (recBubbleList.length() == 0) return

                var hasCompletedTask = false
                var hasPendingRewardBubble = false
                var receivedRewardThisRound = false
                var rewardScanResult = collectSportsHomeRewardScanResult(recBubbleList)

                for (i in 0 until recBubbleList.length()) {
                    val bubble = recBubbleList.optJSONObject(i) ?: continue

                    val bubbleType = bubble.optString("bubbleType", "")
                    val sourceName = bubble.optString("simpleSourceName", "运动首页")

                    if (bubbleType == "receive_coin_bubble" || bubble.optString("assetId", "").isNotBlank()) {
                        hasPendingRewardBubble = true
                        val pendingTaskId = bubble.optString("channel", "")
                        val pendingRecordId = bubble.optString("assetId", "")
                        val coinAmount = bubble.optInt("coinAmount", 0)
                        Log.sports(
                            TAG,
                            "运动首页任务[待领取气泡：$sourceName，taskId=$pendingTaskId，recordId=${pendingRecordId.ifBlank { "unknown" }}，coin=$coinAmount]"
                        )
                        continue
                    }

                    if (bubbleType != "task_bubble") {
                        continue
                    }

                    val task = bubble.optJSONObject("task")
                    if (task == null) {
                        Log.sports(TAG, "运动首页任务[跳过：$sourceName，无task载荷]")
                        continue
                    }

                    val taskId = task.optString("taskId", "")
                    val taskName = task.optString("taskName", sourceName.ifBlank { taskId })
                    if (taskId.isBlank()) {
                        Log.sports(TAG, "运动首页任务[跳过：$taskName，taskId为空]")
                        continue
                    }

                    val taskStatus = task.optString("taskStatus", "")
                    val isBlacklisted =
                        TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId) ||
                            TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskName)
                    if (isBlacklisted && taskStatus != "WAIT_RECEIVE") {
                        Log.sports(TAG, "运动首页任务[黑名单跳过：$taskName，taskId=$taskId]")
                        continue
                    }

                    val cooldownFlag = buildSportsHomeBubbleCooldownFlag(taskId)
                    if (Status.hasFlagToday(cooldownFlag) && taskStatus != "WAIT_RECEIVE") {
                        Log.sports(TAG, "运动首页任务[今日冷却跳过：$taskName，taskId=$taskId]")
                        continue
                    }

                    if (taskStatus == "WAIT_RECEIVE") {
                        hasPendingRewardBubble = true
                        val rewardRecordId = task.optString("assetId", "")
                        Log.sports(
                            TAG,
                            "运动首页任务[待领取奖励：$taskName，taskId=$taskId，recordId=${rewardRecordId.ifBlank { "unknown" }}]"
                        )
                        continue
                    }
                    if (taskStatus != "WAIT_COMPLETE") {
                        Log.sports(TAG, "运动首页任务[状态跳过：$taskName，taskId=$taskId，status=$taskStatus]")
                        continue
                    }

                    Log.sports(
                        TAG,
                        "运动首页任务[直完成开始：$taskName，taskId=$taskId，taskType=${task.optString("taskType", "")}]"
                    )
                    val completeRes = JSONObject(AntSportsRpcCall.completeHomeBubbleTask(taskId))

                    if (ResChecker.checkRes(TAG, completeRes)) {
                        hasCompletedTask = true
                        hasPendingRewardBubble = true
                        val dataObj = completeRes.optJSONObject("data")
                        val assetCoinAmount =
                            dataObj?.optInt("assetCoinAmount", task.optInt("prizeAmount", 0)) ?: 0
                        Log.sports("运动球任务✅[$taskName]#奖励$assetCoinAmount💰")
                        continue
                    }

                    val errorCode = extractSportsHomeBubbleErrorCode(completeRes)
                    val errorMsg = extractSportsHomeBubbleErrorMessage(completeRes)
                    if (shouldCooldownSportsHomeBubbleTask(completeRes)) {
                        Status.setFlagToday(cooldownFlag)
                        Log.error(
                            TAG,
                            "运动首页任务业务RPC失败[进入冷却：$taskName，taskId=$taskId，code=$errorCode，msg=$errorMsg] 响应：$completeRes"
                        )
                    } else {
                        if (errorCode.isNotBlank()) {
                            TaskBlacklist.autoAddToBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId, taskName, errorCode)
                        }
                        Log.error(
                            TAG,
                            "运动首页任务❌[$taskName][taskId=$taskId][code=$errorCode][msg=$errorMsg] 响应：$completeRes"
                        )
                    }
                }

                if (hasCompletedTask || hasPendingRewardBubble) {
                    if (hasCompletedTask) {
                        val refreshedRewardScanResult = querySportsHomeRewardScanResult()
                        if (refreshedRewardScanResult != null) {
                            rewardScanResult = mergeSportsHomeRewardScanResults(rewardScanResult, refreshedRewardScanResult)
                        } else {
                            rewardScanResult.missingRecordIdCount++
                        }
                    }

                    val receivedByRecordId = receiveSportsHomeRewardCandidates(
                        rewardScanResult,
                        handledSportsHomeRewardRecordIds
                    )
                    if (receivedByRecordId) {
                        receivedRewardThisRound = true
                    }
                    val shouldFallbackPickAll =
                        rewardScanResult.missingRecordIdCount > 0 ||
                            (hasCompletedTask && rewardScanResult.candidates.isEmpty())
                    if (shouldFallbackPickAll) {
                        Log.sports(
                            TAG,
                            "运动首页任务[奖励兜底领取：pickAll，knownRecordIds=${rewardScanResult.candidates.size}，missingRecordIds=${rewardScanResult.missingRecordIdCount}]"
                        )
                        val resultJson = JSONObject(AntSportsRpcCall.pickBubbleTaskEnergy())
                        if (ResChecker.checkRes(TAG, resultJson)) {
                            val dataObj = resultJson.optJSONObject("data")
                            val balance = dataObj?.optString("balance", "0") ?: "0"
                            receivedRewardThisRound = true
                            Log.sports("拾取能量球成功  当前余额: $balance💰")
                        } else {
                            Log.error(TAG, "领取能量球任务失败: ${extractSportsRpcErrorMessage(resultJson)} raw=$resultJson")
                        }
                    } else if (!receivedByRecordId) {
                        Log.sports(TAG, "运动首页任务[无可领取奖励记录，跳过pickAll兜底]")
                    }
                } else if (round == 1) {
                    Log.sports(TAG, "未完成任何任务，跳过领取能量球")
                }

                if (!hasCompletedTask && !receivedRewardThisRound) {
                    break
                }
                round++
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "sportsEnergyBubbleTask err:", t)
        }
    }

    /**
     * @brief 运动签到：先 query 再 signIn
     */
    private fun sportsCheckIn() {
        try {
            val queryJo = JSONObject(AntSportsRpcCall.signInCoinTask("query"))
            if (ResChecker.checkRes(TAG, queryJo)) {
                val data = queryJo.getJSONObject("data")
                val isSigned = data.getBoolean("signed")

                if (!isSigned) {
                    val signConfigList = data.getJSONArray("signConfigList")
                    for (i in 0 until signConfigList.length()) {
                        val configItem = signConfigList.getJSONObject(i)
                        val toDay = configItem.getBoolean("toDay")
                        val itemSigned = configItem.getBoolean("signed")

                        if (toDay && !itemSigned) {
                            val coinAmount = configItem.getInt("coinAmount")
                            val signJo = JSONObject(AntSportsRpcCall.signInCoinTask("signIn"))
                            if (ResChecker.checkRes(TAG, signJo)) {
                                val signData = signJo.getJSONObject("data")
                                val subscribeConfig = if (signData.has("subscribeConfig"))
                                    signData.getJSONObject("subscribeConfig")
                                else JSONObject()

                                val expireDays = if (subscribeConfig.has("subscribeExpireDays"))
                                    subscribeConfig.getString("subscribeExpireDays")
                                else "未知"
                                val toast = if (signData.has("toast")) signData.getString("toast") else ""

                                Log.sports(
                                    "做任务得能量🎈[签到${expireDays}天|" +
                                        coinAmount + "能量，" + toast + "💰]"
                                )
                            } else {
                                Log.sports(TAG, "签到接口调用失败：$signJo")
                            }
                            break
                        }
                    }
                } else {
                    Log.sports(TAG, "运动签到今日已签到")
                }
            } else {
                Log.sports(TAG, "查询签到状态失败：$queryJo")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "sportsCheck_in err", e)
        }
    }

    /**
     * @brief 首页金币收集逻辑
     */
    private fun receiveCoinAsset() {
        if (receiveCoinAssetField.value != true) {
            return
        }
        try {
            val s = AntSportsRpcCall.queryCoinBubbleModule()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val data = jo.getJSONObject("data")
                if (!data.has("receiveCoinBubbleList")) return

                val ja = data.getJSONArray("receiveCoinBubbleList")
                for (i in 0 until ja.length()) {
                    jo = ja.getJSONObject(i)
                    val assetId = jo.getString("assetId")
                    val coinAmount = jo.getInt("coinAmount")
                    val res = JSONObject(AntSportsRpcCall.receiveCoinAsset(assetId, coinAmount))
                    if (ResChecker.checkRes(TAG, res)) {
                        Log.sports("收集金币💰[$coinAmount 个]")
                    } else {
                        Log.sports(TAG, "首页收集金币 $res")
                    }
                }
            } else {
                Log.sports(TAG, s)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveCoinAsset err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 新版行走路线（SportsPlay）
    // ---------------------------------------------------------------------

    /**
     * @brief 新版行走路线主流程 主入口
     */
    internal fun walk() {
        try {
            val config = buildRouteConfig()
            var lastTerminalJoinRouteKey: String? = null
            var repeatedTerminalJoinCount = 0

            for (round in 1..MAX_ROUTE_WALK_ROUNDS) {
                val userData = queryRouteUserData() ?: return
                val joinedPathId = resolveCurrentJoinedPathId(userData)
                val currentPathData = joinedPathId.takeIf { it.isNotBlank() }?.let { queryPath(it) }
                val currentStep = currentPathData?.optJSONObject("userPathStep")
                val currentStatus = currentStep?.optString("pathCompleteStatus", "").orEmpty()
                val currentCandidate = buildRouteCandidateFromPathData(currentPathData)
                    ?: joinedPathId.takeIf { it.isNotBlank() }?.let { RouteCandidate(pathId = it) }
                val currentRouteTerminal = isRouteTerminal(currentPathData, currentStatus)

                if (currentPathData != null && isActiveRouteStatus(currentStatus)) {
                    when (executeCurrentRouteWalk(currentPathData)) {
                        RouteWalkOutcome.MOVED,
                        RouteWalkOutcome.COMPLETED -> {
                            GlobalThreadPools.sleepCompat(500)
                            continue
                        }

                        RouteWalkOutcome.NO_STEPS -> {
                            if (tryReviveRouteSteps(config)) {
                                GlobalThreadPools.sleepCompat(500)
                                continue
                            }
                            Log.sports(TAG, "行走路线🚶🏻‍♂️今日无可用步数，结束本轮路线流程")
                            return
                        }

                        RouteWalkOutcome.STOP -> return
                    }
                }

                val decision = chooseNextRoute(config, currentPathData, joinedPathId)
                if (decision == null) {
                    Log.error(TAG, "行走路线🚶🏻‍♂️未找到可加入路线")
                    return
                }

                Log.sports(
                    TAG,
                    "行走路线🚶🏻‍♂️选择路线[${decision.candidate.name.ifBlank { decision.candidate.pathId }}]#${decision.reason}"
                )
                val shouldGuardTerminalLoop =
                    !config.routeLoop &&
                        !config.themeLoop &&
                        (currentRouteTerminal || !isActiveRouteStatus(currentStatus))
                if (shouldGuardTerminalLoop) {
                    val currentRouteKey = decision.candidate.name.trim()
                        .takeIf { it.isNotEmpty() }
                        ?: decision.candidate.pathId.trim().takeIf { it.isNotEmpty() }
                        ?: currentCandidate?.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: currentCandidate?.pathId?.trim()?.takeIf { it.isNotEmpty() }
                        ?: joinedPathId.trim().takeIf { it.isNotEmpty() }
                        ?: decision.candidate.pathId
                    repeatedTerminalJoinCount = if (lastTerminalJoinRouteKey == currentRouteKey) {
                        repeatedTerminalJoinCount + 1
                    } else {
                        lastTerminalJoinRouteKey = currentRouteKey
                        1
                    }
                    if (repeatedTerminalJoinCount >= 2) {
                        val shouldTryReviveAfterRepeat =
                            isRouteStepUnavailable(currentPathData, currentStep)
                        if (shouldTryReviveAfterRepeat && tryReviveRouteSteps(config)) {
                            repeatedTerminalJoinCount = 0
                            lastTerminalJoinRouteKey = null
                            GlobalThreadPools.sleepCompat(500)
                            continue
                        }
                        Log.sports(
                            TAG,
                            "行走路线🚶🏻‍♂️终态路线重复加入仍未切换[${decision.candidate.name.ifBlank { decision.candidate.pathId }}]，结束本轮以避免无效循环"
                        )
                        return
                    }
                } else {
                    repeatedTerminalJoinCount = 0
                    lastTerminalJoinRouteKey = null
                }
                joinPath(decision.candidate.pathId)
                GlobalThreadPools.sleepCompat(500)
            }
            Log.sports(TAG, "行走路线🚶🏻‍♂️达到单次运行轮次上限$MAX_ROUTE_WALK_ROUNDS，停止以避免循环异常")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "walk err:", t)
        }
    }

    /**
     * @brief 新版路线行走一步
     */
    private fun walkGo(pathId: String, useStepCount: Int, pathName: String): RouteWalkOutcome {
        return try {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd")
            val jo = JSONObject(AntSportsRpcCall.walkGo(sdf.format(date), pathId, useStepCount))
            if (isSportsRpcSuccess(jo)) {
                invalidateRouteStateCache()
                Log.sports(TAG, "行走路线🚶🏻‍♂️路线[$pathName]#前进了${useStepCount}步")
                processRouteEvents(jo.optJSONObject("data"))
                val latestPath = queryPath(pathId)
                if (latestPath?.optJSONObject("userPathStep")?.optString("pathCompleteStatus", "") == "COMPLETED") {
                    RouteWalkOutcome.COMPLETED
                } else {
                    RouteWalkOutcome.MOVED
                }
            } else {
                val errorCode = extractSportsRpcErrorCode(jo)
                val errorMsg = extractSportsRpcErrorMessage(jo)
                invalidateRouteStateCache()
                Log.error(
                    TAG,
                    "walkGo失败[pathId=$pathId][useStepCount=$useStepCount][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$jo"
                )
                if (isRouteStepLimit(errorCode, errorMsg)) RouteWalkOutcome.NO_STEPS else RouteWalkOutcome.STOP
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "walkGo err:", t)
            RouteWalkOutcome.STOP
        }
    }

    private fun buildRouteConfig(): RouteConfig {
        val configuredThemeIds = parseRouteIds(if (::walkThemeIds.isInitialized) walkThemeIds.value else null)
        val fallbackThemeId = walkPathThemeId?.takeIf { it.isNotBlank() }
            ?: WalkPathTheme.themeIds[WalkPathTheme.DA_MEI_ZHONG_GUO]
        val themeIds = configuredThemeIds.ifEmpty { listOf(fallbackThemeId) }

        val configuredPathIds = parseRouteIds(if (::walkPathIds.isInitialized) walkPathIds.value else null)
        val legacyPathIds = if (::walkCustomPath.isInitialized && walkCustomPath.value == true) {
            parseRouteIds(walkCustomPathId.value)
        } else {
            emptyList()
        }

        return RouteConfig(
            themeIds = themeIds,
            pathIds = configuredPathIds.ifEmpty { legacyPathIds },
            themeLoop = ::walkThemeLoop.isInitialized && walkThemeLoop.value == true,
            routeLoop = ::walkRouteLoop.isInitialized && walkRouteLoop.value == true,
            reviveSteps = !::walkReviveSteps.isInitialized || walkReviveSteps.value == true,
            reviveTask = !::walkReviveTask.isInitialized || walkReviveTask.value == true
        )
    }

    private fun parseRouteIds(rawValue: String?): List<String> {
        return rawValue.orEmpty()
            .split(Regex("[,，;；\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun invalidateRouteStateCache(includeRevive: Boolean = false) {
        RpcCache.invalidate(RPC_WALK_QUERY_PATH)
        RpcCache.invalidate(RPC_WALK_QUERY_USER)
        RpcCache.invalidate(RPC_WALK_QUERY_WORLD_MAP)
        RpcCache.invalidate(RPC_WALK_QUERY_CITY_PATH)
        RpcCache.invalidate(RPC_WALK_QUERY_CITY_KNOWLEDGE_SUMMARY)
        RpcCache.invalidate(RPC_WALK_QUERY_RECOMMEND_PATH_LIST)
        if (includeRevive) {
            RpcCache.invalidate(RPC_WALK_REVIVE_QUERY_DETAIL)
            RpcCache.invalidate(RPC_WALK_REVIVE_QUERY_TASK_LIST)
            RpcCache.invalidate(RPC_WALK_REVIVE_QUERY_TASK_FINISH_STATUS)
        }
    }

    private fun queryRouteUserData(): JSONObject? {
        val user = JSONObject(AntSportsRpcCall.queryUser())
        if (!ResChecker.checkRes(TAG, user)) {
            Log.error(TAG, "查询路线用户失败: $user")
            return null
        }
        return user.optJSONObject("data") ?: run {
            Log.error(TAG, "查询路线用户成功但 data 为空: $user")
            null
        }
    }

    private fun resolveCurrentJoinedPathId(userData: JSONObject): String {
        return sequenceOf(
            userData.optString("joinedPathId", "").trim(),
            userData.optString("goingPathId", "").trim(),
            userData.optJSONObject("userPathStep")?.optString("pathId", "")?.trim().orEmpty(),
            userData.optJSONObject("path")?.optString("pathId", "")?.trim().orEmpty()
        ).firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    private fun isActiveRouteStatus(status: String): Boolean {
        return status == "JOIN" || status == "NOT_COMPLETED"
    }

    private fun isRouteCompleted(status: String): Boolean {
        return status.equals("COMPLETED", ignoreCase = true)
    }

    private fun isRouteTerminal(pathData: JSONObject?, status: String): Boolean {
        if (isRouteCompleted(status)) {
            return true
        }
        val userPathStep = pathData?.optJSONObject("userPathStep") ?: return false
        if (userPathStep.optInt("pathProgress", 0) >= 100) {
            return true
        }
        val pathStepCount = pathData.optJSONObject("path")?.optInt("pathStepCount", 0) ?: 0
        val forwardStepCount = userPathStep.optInt("forwardStepCount", 0)
        val remainStepCount = userPathStep.optInt("remainStepCount", -1)
        return pathStepCount > 0 &&
            forwardStepCount >= pathStepCount &&
            (userPathStep.optBoolean("dayLimit", false) || remainStepCount <= 0)
    }

    private fun isRouteStepUnavailable(pathData: JSONObject?, userPathStep: JSONObject?): Boolean {
        if (userPathStep == null) {
            return false
        }
        val minGoStepCount =
            pathData?.optJSONObject("path")?.optInt("minGoStepCount", 1)?.coerceAtLeast(1) ?: 1
        val remainStepCount = userPathStep.optInt("remainStepCount", 0)
        return userPathStep.optBoolean("dayLimit", false) || remainStepCount < minGoStepCount
    }

    private fun executeCurrentRouteWalk(pathData: JSONObject): RouteWalkOutcome {
        val userPathStep = pathData.optJSONObject("userPathStep")
        val pathObj = pathData.optJSONObject("path")
        if (userPathStep == null || pathObj == null) {
            Log.error(TAG, "行走路线🚶🏻‍♂️路线详情缺少 userPathStep/path: $pathData")
            return RouteWalkOutcome.STOP
        }

        val pathId = userPathStep.optString("pathId", pathObj.optString("pathId", ""))
        val pathName = userPathStep.optString("pathName", pathObj.optString("name", pathId))
        val status = userPathStep.optString("pathCompleteStatus", "")
        if (isRouteCompleted(status)) {
            Log.sports(TAG, "行走路线🚶🏻‍♂️路线[$pathName]已完成")
            return RouteWalkOutcome.COMPLETED
        }

        val minGoStepCount = pathObj.optInt("minGoStepCount", 1).coerceAtLeast(1)
        val remainStepCount = userPathStep.optInt("remainStepCount", 0)
        if (userPathStep.optBoolean("dayLimit", false) || remainStepCount < minGoStepCount) {
            Log.sports(
                TAG,
                "行走路线🚶🏻‍♂️路线[$pathName]今日步数不足[remain=$remainStepCount,min=$minGoStepCount,dayLimit=${userPathStep.optBoolean("dayLimit", false)}]"
            )
            return RouteWalkOutcome.NO_STEPS
        }

        val pathStepCount = pathObj.optInt("pathStepCount", 0)
        val forwardStepCount = userPathStep.optInt("forwardStepCount", 0)
        val needStepCount = if (pathStepCount > 0) {
            val remainToFinish = pathStepCount - (forwardStepCount % pathStepCount)
            remainToFinish.takeIf { it > 0 } ?: pathStepCount
        } else {
            minGoStepCount
        }
        val useStepCount = min(remainStepCount, max(needStepCount, minGoStepCount))
        if (useStepCount < minGoStepCount) {
            return RouteWalkOutcome.NO_STEPS
        }
        return walkGo(pathId, useStepCount, pathName)
    }

    private fun chooseNextRoute(config: RouteConfig, currentPathData: JSONObject?, joinedPathId: String): RouteDecision? {
        val currentCandidate = buildRouteCandidateFromPathData(currentPathData)
            ?: joinedPathId.takeIf { it.isNotBlank() }?.let { RouteCandidate(pathId = it) }
        val currentPathId = currentCandidate?.pathId
        val currentStatus = currentPathData?.optJSONObject("userPathStep")?.optString("pathCompleteStatus", "").orEmpty()
        val currentRouteTerminal = isRouteTerminal(currentPathData, currentStatus)

        if (config.routeLoop) {
            currentCandidate?.let {
                return RouteDecision(it, "路线循环")
            }
            return firstConfiguredRoute(config)?.let { RouteDecision(it, "路线循环默认首条") }
        }

        if (config.themeLoop) {
            val currentThemeId = currentCandidate?.themeId?.takeIf { it in config.themeIds }
            val themeId = currentThemeId ?: config.themeIds.firstOrNull()
            if (!themeId.isNullOrBlank()) {
                val themeRoutes = collectRouteCandidatesForThemes(listOf(themeId))
                val candidate = themeRoutes.firstOrNull { !isRouteCompleted(it.status) }
                    ?: themeRoutes.firstOrNull()
                if (candidate != null) {
                    return RouteDecision(candidate, "主题循环[$themeId]")
                }
            }
        }

        if (config.pathIds.isNotEmpty()) {
            val configuredPathRoutes = config.pathIds.mapNotNull { queryRouteCandidateByPathId(it) }
            configuredPathRoutes.firstOrNull {
                !isRouteCompleted(it.status) && !(currentRouteTerminal && it.pathId == currentPathId)
            }?.let {
                return RouteDecision(it, "自定义路线未完成优先")
            }
        }

        val themeRoutes = if (config.pathIds.isEmpty()) {
            collectRouteCandidatesForThemes(config.themeIds)
        } else {
            emptyList()
        }
        if (config.pathIds.isEmpty()) {
            themeRoutes.firstOrNull {
                !isRouteCompleted(it.status) && !(currentRouteTerminal && it.pathId == currentPathId)
            }?.let {
                return RouteDecision(it, "主题路线未完成优先")
            }
        }

        findMissingKnowledgeRoute(
            excludePathId = currentPathId.takeIf { currentRouteTerminal },
            excludePathName = currentCandidate?.name?.takeIf { currentRouteTerminal && it.isNotBlank() }
        )?.let { return it }

        currentPathId?.let { pathId ->
            collectRecommendedRouteCandidates(pathId)
                .firstOrNull {
                    !isRouteCompleted(it.status) &&
                        !(currentRouteTerminal && it.pathId == pathId)
                }
                ?.let { return RouteDecision(it, "服务端推荐路线未完成优先") }
        }

        firstConfiguredRoute(config)?.let { return RouteDecision(it, "默认回到首条路线") }

        return null
    }

    private fun firstConfiguredRoute(config: RouteConfig): RouteCandidate? {
        if (config.pathIds.isNotEmpty()) {
            return queryRouteCandidateByPathId(config.pathIds.first()) ?: RouteCandidate(pathId = config.pathIds.first())
        }
        return collectRouteCandidatesForThemes(config.themeIds).firstOrNull()
    }

    private fun queryRouteCandidateByPathId(pathId: String): RouteCandidate? {
        return buildRouteCandidateFromPathData(queryPath(pathId)) ?: RouteCandidate(pathId = pathId)
    }

    private fun buildRouteCandidateFromPathData(pathData: JSONObject?): RouteCandidate? {
        if (pathData == null) return null
        val pathObj = pathData.optJSONObject("path")
        val userPathStep = pathData.optJSONObject("userPathStep")
        val pathId = userPathStep?.optString("pathId", "")
            ?.takeIf { it.isNotBlank() }
            ?: pathObj?.optString("pathId", "").orEmpty()
        if (pathId.isBlank()) return null
        return RouteCandidate(
            pathId = pathId,
            name = userPathStep?.optString("pathName", "")
                ?.takeIf { it.isNotBlank() }
                ?: pathObj?.optString("name", "").orEmpty(),
            themeId = pathObj?.optString("themeId", "")?.takeIf { it.isNotBlank() },
            cityId = pathObj?.optString("cityId", "")?.takeIf { it.isNotBlank() },
            status = userPathStep?.optString("pathCompleteStatus", "").orEmpty()
        )
    }

    private fun collectRouteCandidatesForThemes(themeIds: List<String>): List<RouteCandidate> {
        val candidates = LinkedHashMap<String, RouteCandidate>()
        for (themeId in themeIds) {
            val theme = queryWorldMap(themeId) ?: continue
            val cityList = theme.optJSONArray("cityList") ?: continue
            for (i in 0 until cityList.length()) {
                val cityId = cityList.optJSONObject(i)?.optString("cityId", "").orEmpty()
                if (cityId.isBlank()) continue
                val city = queryCityPath(cityId) ?: continue
                val cityPathList = city.optJSONArray("cityPathList") ?: continue
                for (j in 0 until cityPathList.length()) {
                    val cityPath = cityPathList.optJSONObject(j) ?: continue
                    val pathId = cityPath.optString("pathId", "")
                    if (pathId.isBlank() || candidates.containsKey(pathId)) continue
                    candidates[pathId] = RouteCandidate(
                        pathId = pathId,
                        name = cityPath.optString("name", cityPath.optString("pathName", pathId)),
                        themeId = cityPath.optString("themeId", themeId).ifBlank { themeId },
                        cityId = cityPath.optString("cityId", cityId).ifBlank { cityId },
                        status = cityPath.optString("pathCompleteStatus", "")
                    )
                }
            }
        }
        return candidates.values.toList()
    }

    private fun collectRecommendedRouteCandidates(pathId: String): List<RouteCandidate> {
        val response = JSONObject(AntSportsRpcCall.queryRecommendPathList(pathId))
        if (!ResChecker.checkRes(TAG, response)) {
            Log.error(
                TAG,
                "行走路线🚶🏻‍♂️查询推荐路线失败[pathId=$pathId][code=${extractSportsRpcErrorCode(response).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(response)}] raw=$response"
            )
            return emptyList()
        }
        val recommendList = response.optJSONObject("data")?.optJSONArray("recommendPathList") ?: return emptyList()
        val candidates = mutableListOf<RouteCandidate>()
        for (i in 0 until recommendList.length()) {
            val item = recommendList.optJSONObject(i) ?: continue
            val recommendPathId = item.optString("pathId", "")
            if (recommendPathId.isBlank()) continue
            candidates.add(
                RouteCandidate(
                    pathId = recommendPathId,
                    name = item.optString("name", recommendPathId),
                    themeId = item.optString("themeId", "").takeIf { it.isNotBlank() },
                    cityId = item.optString("cityId", "").takeIf { it.isNotBlank() },
                    status = item.optString("pathCompleteStatus", "")
                )
            )
        }
        return candidates
    }

    private fun findMissingKnowledgeRoute(
        excludePathId: String? = null,
        excludePathName: String? = null
    ): RouteDecision? {
        val summary = JSONObject(AntSportsRpcCall.queryCityKnowledgeSummary())
        if (!ResChecker.checkRes(TAG, summary)) {
            Log.error(
                TAG,
                "行走路线🚶🏻‍♂️查询城市见闻汇总失败[code=${extractSportsRpcErrorCode(summary).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(summary)}] raw=$summary"
            )
            return null
        }
        val cityList = summary.optJSONObject("data")?.optJSONArray("cityKnowledgeSummaryList") ?: return null
        for (i in 0 until cityList.length()) {
            val city = cityList.optJSONObject(i) ?: continue
            val total = city.optInt("totalKnowledgeCount", 0)
            val received = city.optInt("receiveKnowledgeCount", 0)
            val cityId = city.optString("cityId", "")
            if (cityId.isBlank() || total <= 0 || received >= total) {
                continue
            }
            val cityPathData = queryCityPath(cityId) ?: continue
            val pathList = cityPathData.optJSONArray("cityPathList") ?: continue
            var hasRouteInCity = false
            var loopCandidate: RouteCandidate? = null
            var excludedLoopCandidate: RouteCandidate? = null
            for (j in 0 until pathList.length()) {
                val cityPath = pathList.optJSONObject(j) ?: continue
                val candidate = RouteCandidate(
                    pathId = cityPath.optString("pathId", ""),
                    name = cityPath.optString("name", cityPath.optString("pathName", "")),
                    themeId = cityPath.optString("themeId", "").takeIf { it.isNotBlank() },
                    cityId = cityId,
                    status = cityPath.optString("pathCompleteStatus", "")
                )
                if (candidate.pathId.isBlank()) {
                    continue
                }
                hasRouteInCity = true
                val isExcluded = (!excludePathId.isNullOrBlank() && candidate.pathId == excludePathId) ||
                    (!excludePathName.isNullOrBlank() &&
                        candidate.name.isNotBlank() &&
                        candidate.name == excludePathName)
                if (!isExcluded && !isRouteCompleted(candidate.status)) {
                    return RouteDecision(
                        candidate,
                        "城市见闻未完成[${city.optString("cityName", cityId)}:$received/$total]"
                    )
                }
                if (!isExcluded && loopCandidate == null) {
                    loopCandidate = candidate
                }
                if (isExcluded && excludedLoopCandidate == null) {
                    excludedLoopCandidate = candidate
                }
            }
            if (loopCandidate != null) {
                return RouteDecision(
                    loopCandidate,
                    "城市见闻未完成[${city.optString("cityName", cityId)}:$received/$total]#优先循环路线"
                )
            }
            if (excludedLoopCandidate != null) {
                return RouteDecision(
                    excludedLoopCandidate,
                    "城市见闻未完成[${city.optString("cityName", cityId)}:$received/$total]#仅当前路线可循环"
                )
            }
            if (hasRouteInCity) {
                Log.sports(
                    TAG,
                    "行走路线🚶🏻‍♂️城市见闻未完成但无未完成路线可切换[${city.optString("cityName", cityId)}:$received/$total]"
                )
            }
        }
        return null
    }

    private fun isRouteStepLimit(errorCode: String, errorMsg: String): Boolean {
        val text = "$errorCode $errorMsg"
        return text.contains("步数") ||
            text.contains("步數") ||
            text.contains("上限") ||
            text.contains("不足") ||
            text.contains("dayLimit", ignoreCase = true)
    }

    private fun tryReviveRouteSteps(config: RouteConfig): Boolean {
        if (!config.reviveSteps) {
            Log.sports(TAG, "行走路线复活步数已关闭")
            return false
        }
        if (Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_ROUTE_REVIVE_TRIED)) {
            Log.sports(TAG, "行走路线复活步数今日已尝试且不可继续，跳过")
            return false
        }

        if (config.reviveTask) {
            completeRouteReviveTasks()
            invalidateRouteStateCache(includeRevive = true)
        }

        val reviveData = queryRouteReviveData()
        if (reviveData == null) {
            return false
        }

        val remainTimes = reviveData.optInt("remainReviveTimes", 0)
        if (remainTimes <= 0) {
            Log.sports(TAG, "行走路线复活次数不足，今日不再重复尝试")
            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_ROUTE_REVIVE_TRIED)
            return false
        }

        val candidates = collectReviveCandidates(reviveData)
        if (candidates.isEmpty()) {
            Log.sports(TAG, "行走路线没有可复活历史步数，今日不再重复尝试")
            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_ROUTE_REVIVE_TRIED)
            return false
        }

        var attempted = 0
        for (candidate in candidates) {
            if (attempted >= remainTimes) break
            attempted++
            val resp = JSONObject(AntSportsRpcCall.reviveSteps(candidate.date))
            if (isSportsRpcSuccess(resp)) {
                invalidateRouteStateCache(includeRevive = true)
                val reviveCount = resp.optJSONObject("data")?.optInt("reviveCount", candidate.count) ?: candidate.count
                Log.sports(TAG, "行走路线复活步数成功[date=${candidate.date}, count=$reviveCount]")
                return true
            }

            val errorCode = extractSportsRpcErrorCode(resp)
            val errorMsg = extractSportsRpcErrorMessage(resp)
            Log.error(
                TAG,
                "行走路线复活步数失败[date=${candidate.date}, count=${candidate.count}][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$resp"
            )
        }

        Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_ROUTE_REVIVE_TRIED)
        return false
    }

    private fun completeRouteReviveTasks() {
        runCatching {
            val finishStatus = JSONObject(AntSportsRpcCall.queryReviveTaskFinishStatus())
            if (!isSportsRpcSuccess(finishStatus)) {
                Log.error(
                    TAG,
                    "行走路线复活任务状态查询失败[code=${extractSportsRpcErrorCode(finishStatus).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(finishStatus)}] raw=$finishStatus"
                )
            }
        }.onFailure {
            Log.printStackTrace(TAG, "queryReviveTaskFinishStatus err", it)
        }

        val taskListResp = JSONObject(AntSportsRpcCall.queryReviveTaskList())
        if (!isSportsRpcSuccess(taskListResp)) {
            Log.error(
                TAG,
                "行走路线复活任务列表查询失败[code=${extractSportsRpcErrorCode(taskListResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(taskListResp)}] raw=$taskListResp"
            )
            return
        }

        val tasks = taskListResp.optJSONObject("data")?.optJSONArray("reviveTaskLists") ?: return
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            val taskId = task.optString("taskId", "")
            val taskName = task.optString("taskName", taskId)
            val taskStatus = task.optString("taskStatus", "")
            val taskType = task.optString("taskType", "")
            val taskBizId = task.optJSONObject("bizExtMap")?.optString("bizId", "").orEmpty()
            val isAdTask = task.optBoolean("adTask", false) || taskType == "AD_TASK"
            if (taskId.isBlank() || taskStatus != "WAIT_COMPLETE") {
                continue
            }
            if (task.optBoolean("needSignUp", false)) {
                Log.sports(TAG, "行走路线复活任务[需要报名，跳过：$taskName，taskId=$taskId]")
                continue
            }
            if (!isAdTask && taskType != "BROWSER") {
                Log.sports(TAG, "行走路线复活任务[非浏览任务跳过：$taskName，taskId=$taskId，type=$taskType]")
                continue
            }
            if (isAdTask && taskBizId.isBlank()) {
                Log.error(TAG, "行走路线复活任务[广告任务缺少bizId，跳过：$taskName，taskId=$taskId]")
                continue
            }

            val triggerResp = JSONObject(
                AntSportsRpcCall.triggerReviveTask(
                    taskId,
                    taskBizId.takeIf { it.isNotBlank() }
                )
            )
            if (!isSportsRpcSuccess(triggerResp)) {
                Log.error(
                    TAG,
                    "行走路线复活任务触发失败[$taskName][taskId=$taskId][code=${extractSportsRpcErrorCode(triggerResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(triggerResp)}] raw=$triggerResp"
                )
                continue
            }
            GlobalThreadPools.sleepCompat(500)
            if (isAdTask) {
                val adFinishResp = JSONObject(AntSportsRpcCall.finishAdTask(taskBizId))
                if (isSportsRpcSuccess(adFinishResp)) {
                    invalidateRouteStateCache(includeRevive = true)
                    Log.sports(TAG, "行走路线复活广告任务完成[$taskName][taskId=$taskId][bizId=$taskBizId]")
                } else {
                    Log.error(
                        TAG,
                        "行走路线复活广告任务完成失败[$taskName][taskId=$taskId][bizId=$taskBizId][code=${extractSportsRpcErrorCode(adFinishResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(adFinishResp)}] raw=$adFinishResp"
                    )
                }
                GlobalThreadPools.sleepCompat(500)
                continue
            }
            val completeResp = JSONObject(AntSportsRpcCall.completeReviveTask(taskId))
            if (isSportsRpcSuccess(completeResp)) {
                invalidateRouteStateCache(includeRevive = true)
                Log.sports(TAG, "行走路线复活任务完成[$taskName][taskId=$taskId]")
            } else {
                Log.error(
                    TAG,
                    "行走路线复活任务完成失败[$taskName][taskId=$taskId][code=${extractSportsRpcErrorCode(completeResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(completeResp)}] raw=$completeResp"
                )
            }
            GlobalThreadPools.sleepCompat(500)
        }
    }

    private fun queryRouteReviveData(): JSONObject? {
        val resp = JSONObject(AntSportsRpcCall.queryUserReviveStepT2())
        if (!isSportsRpcSuccess(resp)) {
            Log.error(
                TAG,
                "行走路线复活步数明细查询失败[code=${extractSportsRpcErrorCode(resp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(resp)}] raw=$resp"
            )
            return null
        }
        return resp.optJSONObject("data")
    }

    private fun collectReviveCandidates(reviveData: JSONObject): List<ReviveCandidate> {
        val result = mutableListOf<ReviveCandidate>()
        val stepRevives = reviveData.optJSONArray("stepRevives") ?: return result
        for (i in 0 until stepRevives.length()) {
            val item = stepRevives.optJSONObject(i) ?: continue
            val date = item.optString("date", "")
            val count = item.optInt("count", 0)
            if (date.isNotBlank() && count > 0) {
                result.add(ReviveCandidate(date, count))
            }
        }
        return result.sortedByDescending { it.count }
    }

    /**
     * @brief 查询世界地图
     */
    private fun queryWorldMap(themeId: String?): JSONObject? {
        var theme: JSONObject? = null
        if (themeId.isNullOrEmpty()) return null
        try {
            val jo = JSONObject(AntSportsRpcCall.queryWorldMap(themeId))
            if (ResChecker.checkRes(TAG + "queryWorldMap失败： [ThemeID: $themeId]: ", jo)) {
                theme = jo.getJSONObject("data")
            } else {
                Log.error(TAG, "queryWorldMap失败： [ThemeID: $themeId]: $jo")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryWorldMap err:", t)
        }
        return theme
    }

    /**
     * @brief 查询指定城市的路线详情
     * @param cityId 城市 ID
     */
    private fun queryCityPath(cityId: String): JSONObject? {
        var city: JSONObject? = null
        try {
            val jo = JSONObject(AntSportsRpcCall.queryCityPath(cityId))
            if (ResChecker.checkRes(TAG, jo)) {
                city = jo.getJSONObject("data")
            } else {
                Log.error(TAG, "queryCityPath失败： [CityID: $cityId]$jo")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryCityPath err:", t)
        }
        return city
    }

    /**
     * @brief 查询路线详情（同时触发宝箱领取）
     */
    /*
    private fun queryPath(pathId: String): JSONObject? {
        var path: JSONObject? = null
        try {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd")
            val jo = JSONObject(AntSportsRpcCall.queryPath(sdf.format(date), pathId))
            if (ResChecker.checkRes(TAG, jo)) {
                path = jo.getJSONObject("data")
                val ja = jo.getJSONObject("data").getJSONArray("treasureBoxList")
                for (i in 0 until ja.length()) {
                    val treasureBox = ja.getJSONObject(i)
                    receiveEvent(treasureBox.getString("boxNo"))
                }
            } else {
                Log.error(TAG, "queryPath失败： $jo")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryPath err:", t)
        }
        return path
    }*/


    //这里会返回路线详情
    private fun queryPath(pathId: String): JSONObject? {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val response = AntSportsRpcCall.queryPath(dateStr, pathId)
            val jo = JSONObject(response)

            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error(TAG, "queryPath 请求失败: $response")
                return null
            }

            // 2. 检查数据节点是否存在
            val data = jo.optJSONObject("data")
            if (data == null) {
                Log.error(TAG, "queryPath 响应成功但 data 节点为空: $response")
                return null
            }

            // --- 逻辑处理 ---
            val userPath = data.optJSONObject("userPathStep")
            Log.sports(TAG, "路线: ${userPath?.optString("pathName")}, 进度: ${userPath?.optInt("pathProgress")}%")

            processRouteEvents(data)

            return data
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryPath 过程中发生崩溃", t)
        }
        return null
    }

    private fun processRouteEvents(data: JSONObject?) {
        if (data == null) return

        val boxList = data.optJSONArray("treasureBoxList")
        if (boxList != null && boxList.length() > 0) {
            for (i in 0 until boxList.length()) {
                val box = boxList.optJSONObject(i) ?: continue
                val eventBillNo = box.optString("eventBillNo", "")
                    .ifBlank { box.optString("boxNo", "") }
                if (eventBillNo.isNotBlank()) {
                    receiveEvent(eventBillNo)
                }
            }
        }

        val cityKnowledgeList = data.optJSONArray("cityKnowledgeList")
        if (cityKnowledgeList != null && cityKnowledgeList.length() > 0) {
            for (i in 0 until cityKnowledgeList.length()) {
                val knowledge = cityKnowledgeList.optJSONObject(i) ?: continue
                val eventBillNo = knowledge.optString("eventBillNo", "")
                if (eventBillNo.isNotBlank()) {
                    Log.sports(
                        TAG,
                        "行走路线📍领取城市见闻[${knowledge.optString("name", knowledge.optString("knowledgeId", eventBillNo))}]"
                    )
                    receiveEvent(eventBillNo)
                }
            }
        }
    }

    /**
     * @brief 新版路线开启宝箱并打印奖励
     */
    private fun receiveEvent(eventBillNo: String) {
        try {
            val jo = JSONObject(AntSportsRpcCall.receiveEvent(eventBillNo))
            if (!isSportsRpcSuccess(jo)) {
                val errorCode = extractSportsRpcErrorCode(jo)
                val errorMsg = extractSportsRpcErrorMessage(jo)
                if (isRouteEventDuplicate(errorCode, errorMsg)) {
                    Log.sports(TAG, "行走路线🎁事件已领取，跳过重复领取[eventBillNo=$eventBillNo][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]")
                    return
                }
                Log.error(
                    TAG,
                    "行走路线🎁领取事件失败[eventBillNo=$eventBillNo][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$jo"
                )
                return
            }

            invalidateRouteStateCache()
            val ja = jo.optJSONObject("data")?.optJSONArray("rewards")
            if (ja == null || ja.length() == 0) {
                Log.sports(TAG, "行走路线🎁事件领取成功[eventBillNo=$eventBillNo]")
                return
            }
            for (i in 0 until ja.length()) {
                val reward = ja.getJSONObject(i)
                Log.sports(
                    TAG,
                    "行走路线🎁领取奖励[${reward.optString("rewardName", reward.optString("name", "未知奖励"))}]*${reward.optInt("count", 1)}"
                )
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveEvent err:", t)
        }
    }

    private fun isRouteEventDuplicate(errorCode: String, errorMsg: String): Boolean {
        return errorCode == "RECEIVE_REWARD_REPEATED" ||
            errorMsg.contains("已领取") ||
            errorMsg.contains("已经领取") ||
            errorMsg.contains("重复")
    }

    /**
     * @brief 根据主题 ID 挑选可加入的 pathId
     */
    private fun queryJoinPath(themeId: String?): String? {
        if (walkCustomPath.value == true) {
            walkCustomPathId.value?.takeIf { it.isNotBlank() }?.let { return it }
        }
        var pathId: String? = null
        try {
            val theme = queryWorldMap(themeId)
            if (theme == null) {
                Log.error(TAG, "queryJoinPath-> theme 失败：$theme")
                return null
            }
            val cityList = theme.getJSONArray("cityList")
            for (i in 0 until cityList.length()) {
                val cityId = cityList.getJSONObject(i).getString("cityId")
                val city = queryCityPath(cityId) ?: continue
                val cityPathList = city.getJSONArray("cityPathList")
                for (j in 0 until cityPathList.length()) {
                    val cityPath = cityPathList.getJSONObject(j)
                    pathId = cityPath.getString("pathId")
                    if ("COMPLETED" != cityPath.getString("pathCompleteStatus")) {
                        return pathId
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryJoinPath err:", t)
        }
        return pathId
    }

    /**
     * @brief 加入新版路线
     */
    private fun joinPath(pathId: String?) {
        var realPathId = pathId
        if (realPathId == null) {
            // 默认龙年祈福线
            realPathId = "p0002023122214520001"
        }
        try {
            val jo = JSONObject(AntSportsRpcCall.joinPath(realPathId))
            if (isSportsRpcSuccess(jo)) {
                invalidateRouteStateCache()
                val path = queryPath(realPathId)
                Log.sports(TAG, "行走路线🚶🏻‍♂️路线[${path?.optJSONObject("path")?.optString("name", realPathId)}]已加入")
            } else {
                val errorCode = extractSportsRpcErrorCode(jo)
                val errorMsg = extractSportsRpcErrorMessage(jo)
                if (isSportsRouteBusinessTerminal(errorCode, errorMsg)) {
                    Log.sports(
                        TAG,
                        "行走路线🚶🏻‍♂️加入路线业务终态[pathId=$realPathId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                    )
                    return
                }
                Log.error(
                    TAG,
                    "行走路线🚶🏻‍♂️加入路线失败[pathId=$realPathId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$jo"
                )
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "joinPath err:", t)
        }
    }

    /**
     * @brief 根据配置索引同步更新路线主题 ID
     */
    internal fun getWalkPathThemeIdOnConfig() {
        val index = walkPathTheme.value ?: WalkPathTheme.DA_MEI_ZHONG_GUO
        if (index in 0 until WalkPathTheme.themeIds.size) {
            walkPathThemeId = WalkPathTheme.themeIds[index]
        } else {
            Log.error(TAG, "非法的路线主题索引: $index，已回退至默认主题")
            walkPathThemeId = WalkPathTheme.themeIds[WalkPathTheme.DA_MEI_ZHONG_GUO]
        }
    }

    // ---------------------------------------------------------------------
    // 旧版行走路线（保留兼容）
    // ---------------------------------------------------------------------

    /**
     * @brief 旧版行走路线首页逻辑（开宝箱 + 行走 + 加入路线）
     */
    internal fun queryMyHomePage(loader: ClassLoader) {
        try {
            var s = AntSportsRpcCall.queryMyHomePage()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val pathJoinStatus = jo.getString("pathJoinStatus")
                if ("GOING" == pathJoinStatus) {
                    if (jo.has("pathCompleteStatus")) {
                        if ("COMPLETED" == jo.getString("pathCompleteStatus")) {
                            jo = JSONObject(AntSportsRpcCall.queryBaseList())
                            if (ResChecker.checkRes(TAG, jo)) {
                                val allPathBaseInfoList = jo.getJSONArray("allPathBaseInfoList")
                                val otherAllPathBaseInfoList = jo.getJSONArray("otherAllPathBaseInfoList")
                                    .getJSONObject(0)
                                    .getJSONArray("allPathBaseInfoList")
                                join(loader, allPathBaseInfoList, otherAllPathBaseInfoList, "")
                            } else {
                                Log.sports(TAG, jo.getString("resultDesc"))
                            }
                        }
                    } else {
                        val rankCacheKey = jo.getString("rankCacheKey")
                        val ja = jo.getJSONArray("treasureBoxModelList")
                        for (i in 0 until ja.length()) {
                            parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey)
                        }
                        val joPathRender = jo.getJSONObject("pathRenderModel")
                        val title = joPathRender.getString("title")
                        val minGoStepCount = joPathRender.getInt("minGoStepCount")
                        jo = jo.getJSONObject("dailyStepModel")
                        val consumeQuantity = jo.getInt("consumeQuantity")
                        val produceQuantity = jo.getInt("produceQuantity")
                        val day = jo.getString("day")
                        val canMoveStepCount = produceQuantity - consumeQuantity
                        if (canMoveStepCount >= minGoStepCount) {
                            go(loader, day, rankCacheKey, canMoveStepCount, title)
                        }
                    }
                } else if ("NOT_JOIN" == pathJoinStatus) {
                    val firstJoinPathTitle = jo.getString("firstJoinPathTitle")
                    val allPathBaseInfoList = jo.getJSONArray("allPathBaseInfoList")
                    val otherAllPathBaseInfoList = jo.getJSONArray("otherAllPathBaseInfoList")
                        .getJSONObject(0)
                        .getJSONArray("allPathBaseInfoList")
                    join(loader, allPathBaseInfoList, otherAllPathBaseInfoList, firstJoinPathTitle)
                }
            } else {
                Log.sports(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryMyHomePage err:", t)
        }
    }

    /**
     * @brief 旧版路线加入逻辑（根据可解锁路径列表）
     */
    private fun join(
        loader: ClassLoader,
        allPathBaseInfoList: JSONArray,
        otherAllPathBaseInfoList: JSONArray,
        firstJoinPathTitle: String
    ) {
        try {
            var index = -1
            var title: String? = null
            var pathId: String? = null
            var jo: JSONObject

            for (i in allPathBaseInfoList.length() - 1 downTo 0) {
                jo = allPathBaseInfoList.getJSONObject(i)
                if (jo.getBoolean("unlocked")) {
                    title = jo.getString("title")
                    pathId = jo.getString("pathId")
                    index = i
                    break
                }
            }
            if (index < 0 || index == allPathBaseInfoList.length() - 1) {
                for (j in otherAllPathBaseInfoList.length() - 1 downTo 0) {
                    jo = otherAllPathBaseInfoList.getJSONObject(j)
                    if (jo.getBoolean("unlocked")) {
                        if (j != otherAllPathBaseInfoList.length() - 1 || index != allPathBaseInfoList.length() - 1) {
                            title = jo.getString("title")
                            pathId = jo.getString("pathId")
                            index = j
                        }
                        break
                    }
                }
            }
            if (index >= 0) {
                val s = if (title == firstJoinPathTitle) {
                    AntSportsRpcCall.openAndJoinFirst()
                } else {
                    AntSportsRpcCall.join(pathId ?: "")
                }
                jo = JSONObject(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    Log.sports("加入线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                } else {
                    Log.sports(TAG, jo.getString("resultDesc"))
                }
            } else {
                Log.sports(TAG, "好像没有可走的线路了！")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "join err:", t)
        }
    }

    /**
     * @brief 旧版路线行走逻辑
     */
    private fun go(loader: ClassLoader, day: String, rankCacheKey: String, stepCount: Int, title: String) {
        try {
            val s = AntSportsRpcCall.go(day, rankCacheKey, stepCount)
            val jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                Log.sports("行走线路🚶🏻‍♂️[$title]#前进了${jo.getInt("goStepCount")}步")
                val completed = "COMPLETED" == jo.getString("completeStatus")
                val ja = jo.getJSONArray("allTreasureBoxModelList")
                for (i in 0 until ja.length()) {
                    parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey)
                }
                if (completed) {
                    Log.sports("完成线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                }
            } else {
                Log.sports(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "go err:", t)
        }
    }

    /**
     * @brief 解析旧版宝箱模型并按时间安排子任务开箱
     */
    private fun parseTreasureBoxModel(loader: ClassLoader, jo: JSONObject, rankCacheKey: String) {
        try {
            val canOpenTime = jo.getString("canOpenTime")
            val issueTime = jo.getString("issueTime")
            val boxNo = jo.getString("boxNo")
            val userId = jo.getString("userId")
            if (canOpenTime == issueTime) {
                openTreasureBox(boxNo, userId)
            } else {
                val cot = canOpenTime.toLong()
                val now = rankCacheKey.toLong()
                val delay = cot - now
                if (delay <= 0) {
                    openTreasureBox(boxNo, userId)
                    return
                }
                val checkIntervalMs = BaseModel.checkInterval.value?.toLong() ?: 0L
                if (delay < checkIntervalMs) {
                    val taskId = "BX|$boxNo"
                    if (hasChildTask(taskId)) return
                    Log.sports(TAG, "还有 $delay ms 开运动宝箱")
                    addChildTask(
                        ChildModelTask(
                            taskId,
                            "BX",
                            Runnable {
                                Log.sports(TAG, "蹲点开箱开始")
                                val startTime = System.currentTimeMillis()
                                while (System.currentTimeMillis() - startTime < 5_000) {
                                    if (openTreasureBox(boxNo, userId) > 0) {
                                        break
                                    }
                                    GlobalThreadPools.sleepCompat(200)
                                }
                            },
                            System.currentTimeMillis() + delay
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "parseTreasureBoxModel err:", t)
        }
    }

    /**
     * @brief 旧版宝箱开启
     * @return 获得的奖励数量
     */
    private fun openTreasureBox(boxNo: String, userId: String): Int {
        try {
            val s = AntSportsRpcCall.openTreasureBox(boxNo, userId)
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val ja = jo.getJSONArray("treasureBoxAwards")
                var num = 0
                for (i in 0 until ja.length()) {
                    jo = ja.getJSONObject(i)
                    num += jo.getInt("num")
                    Log.sports("运动宝箱🎁[$num${jo.getString("name")}]")
                }
                return num
            } else if ("TREASUREBOX_NOT_EXIST" == jo.getString("resultCode")) {
                Log.sports(jo.getString("resultDesc"))
                return 1
            } else {
                Log.sports(jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openTreasureBox err:", t)
        }
        return 0
    }

    // ---------------------------------------------------------------------
    // 旧版捐步 & 慈善
    // ---------------------------------------------------------------------

    /**
     * @brief 查询慈善项目列表并执行捐赠
     */
    internal fun queryProjectList() {
        try {
            var jo = JSONObject(AntSportsRpcCall.queryProjectList(0))
            if (ResChecker.checkRes(TAG, jo)) {
                val donateAmount = (donateCharityCoinAmount.value ?: return).coerceAtLeast(1)
                if (donateAmount <= 0) return
                var charityCoinCount = jo.getInt("charityCoinCount")
                if (charityCoinCount < donateAmount) return

                val ja = jo.getJSONObject("projectPage").getJSONArray("data")
                for (i in 0 until ja.length()) {
                    if (charityCoinCount < donateAmount) break
                    val basicModel = ja.getJSONObject(i).getJSONObject("basicModel")
                    if ("DONATE_COMPLETED" == basicModel.getString("footballFieldStatus")) break
                    donate(donateAmount, basicModel.getString("projectId"), basicModel.getString("title"))
                    Status.donateCharityCoin()
                    charityCoinCount -= donateAmount
                    if (donateCharityCoinType.value == DonateCharityCoinType.ONE) break
                }
            } else {
                Log.sports(TAG)
                Log.sports(jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryProjectList err:", t)
        }
    }

    /**
     * @brief 执行一次慈善捐赠
     */
    private fun donate(donateCharityCoin: Int, projectId: String, title: String) {
        try {
            val s = AntSportsRpcCall.donate(donateCharityCoin, projectId)
            val jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                Log.sports("捐赠活动❤️[$title][$donateCharityCoin 能量🎈]")
            } else {
                Log.sports(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "donate err:", t)
        }
    }

    /**
     * @brief 查询行走步数，并根据条件自动捐步
     */
    internal fun queryWalkStep() {
        try {
            var s = AntSportsRpcCall.queryWalkStep()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val produceQuantity = AntSportsRpcCall.extractWalkStepCount(jo)
                val minExchange = minExchangeCount.value ?: 0
                if (produceQuantity <= 0) {
                    Log.sports(TAG, "当前暂无可捐步数")
                    return
                }
                if (produceQuantity < minExchange && latestExchangeTime.isBeforeCutoff()) {
                    return
                }

                AntSportsRpcCall.walkDonateSignInfo(produceQuantity)
                s = AntSportsRpcCall.donateWalkHome(produceQuantity)
                jo = JSONObject(s)
                if (!jo.optBoolean("isSuccess", false)) {
                    if (s.contains("已捐步") || jo.optString("resultDesc").contains("已捐步")) {
                        Status.exchangeToday(UserMap.currentUid ?: return)
                    }
                    return
                }

                val walkDonateHomeModel = jo.optJSONObject("walkDonateHomeModel") ?: return
                val walkUserInfoModel = walkDonateHomeModel.optJSONObject("walkUserInfoModel")
                if (walkUserInfoModel == null || !walkUserInfoModel.has("exchangeFlag")) {
                    Status.exchangeToday(UserMap.currentUid ?: return)
                    return
                }

                val donateToken = walkDonateHomeModel.optString("donateToken")
                val activityId = walkDonateHomeModel.optJSONObject("walkCharityActivityModel")
                    ?.optString("activityId")
                    .orEmpty()
                if (donateToken.isBlank() || activityId.isBlank()) {
                    Log.sports(TAG, "捐步兑换缺少 donateToken 或 activityId，跳过")
                    return
                }

                s = AntSportsRpcCall.exchange(activityId, produceQuantity, donateToken)
                jo = JSONObject(s)
                if (jo.optBoolean("isSuccess", false)) {
                    val donateExchangeResultModel = jo.optJSONObject("donateExchangeResultModel")
                    val userCount = donateExchangeResultModel?.optInt("userCount", produceQuantity) ?: produceQuantity
                    val amount = donateExchangeResultModel?.optJSONObject("userAmount")?.optDouble("amount", 0.0) ?: 0.0
                    Log.sports("捐出活动❤️[$userCount 步]#兑换$amount 元公益金")
                    Status.exchangeToday(UserMap.currentUid ?: return)
                } else if (s.contains("已捐步") || jo.optString("resultDesc").contains("已捐步")) {
                    Status.exchangeToday(UserMap.currentUid ?: return)
                } else {
                    Log.sports(TAG, jo.optString("resultDesc", s))
                }
            } else {
                Log.sports(TAG, jo.optString("resultDesc", s))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryWalkStep err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 文体中心
    // ---------------------------------------------------------------------

    /**
     * @brief 文体中心任务组查询并自动完成 TODO 状态任务
     */
    internal fun userTaskGroupQuery(groupId: String) {
        try {
            val s = AntSportsRpcCall.userTaskGroupQuery(groupId)
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("group")
                val userTaskList = jo.getJSONArray("userTaskList")
                for (i in 0 until userTaskList.length()) {
                    jo = userTaskList.getJSONObject(i)
                    if ("TODO" != jo.getString("status")) continue
                    val taskInfo = jo.getJSONObject("taskInfo")
                    val bizType = taskInfo.getString("bizType")
                    val taskId = taskInfo.getString("taskId")
                    val res = JSONObject(AntSportsRpcCall.userTaskComplete(bizType, taskId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val taskName = taskInfo.optString("taskName", taskId)
                        Log.sports("完成任务🧾[$taskName]")
                    } else {
                        Log.sports(TAG, "文体每日任务 $res")
                    }
                }
            } else {
                Log.sports(TAG, "文体每日任务 $s")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "userTaskGroupQuery err:", t)
        }
    }

    /**
     * @brief 文体中心走路挑战报名
     */
    internal fun participate() {
        try {
            if (Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_WALK_CHALLENGE_UNAVAILABLE_TODAY)) {
                Log.sports(TAG, "走路挑战赛[今日已停用，跳过重复报名]")
                return
            }
            val s = AntSportsRpcCall.queryAccount()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val balance = jo.getDouble("balance")
                if (balance < 100) return

                jo = JSONObject(AntSportsRpcCall.queryRoundList())
                if (ResChecker.checkRes(TAG, jo)) {
                    val dataList = jo.getJSONArray("dataList")
                    for (i in 0 until dataList.length()) {
                        jo = dataList.getJSONObject(i)
                        if ("P" != jo.getString("status")) continue
                        if (jo.has("userRecord")) continue
                        val instanceList = jo.getJSONArray("instanceList")
                        var pointOptions = 0
                        val roundId = jo.getString("id")
                        var instanceId: String? = null
                        var resultId: String? = null

                        for (j in instanceList.length() - 1 downTo 0) {
                            val inst = instanceList.getJSONObject(j)
                            if (inst.getInt("pointOptions") < pointOptions) continue
                            pointOptions = inst.getInt("pointOptions")
                            instanceId = inst.getString("id")
                            resultId = inst.getString("instanceResultId")
                        }
                        val res = JSONObject(
                            AntSportsRpcCall.participate(
                                pointOptions,
                                instanceId ?: continue,
                                resultId ?: continue,
                                roundId
                            )
                        )
                        if (isSportsRpcSuccess(res)) {
                            val data = res.getJSONObject("data")
                            val roundDescription = data.getString("roundDescription")
                            val targetStepCount = data.getInt("targetStepCount")
                            Log.sports("走路挑战🚶🏻‍♂️[$roundDescription]#$targetStepCount")
                        } else {
                            val errorCode = extractSportsRpcErrorCode(res)
                            val errorMsg = extractSportsRpcErrorMessage(res)
                            if (
                                errorCode == "3000" ||
                                errorMsg.contains("系统出错") ||
                                errorMsg.contains("系統出錯")
                            ) {
                                Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_WALK_CHALLENGE_UNAVAILABLE_TODAY)
                                Log.sports(
                                    TAG,
                                    "走路挑战赛[暂不可用][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$res"
                                )
                                return
                            } else {
                                Log.error(
                                    TAG,
                                    "走路挑战赛RPC失败[code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$res"
                                )
                            }
                        }
                    }
                } else {
                    Log.sports(TAG, "queryRoundList $jo")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "participate err:", t)
        }
    }

    /**
     * @brief 文体中心奖励领取
     */
    internal fun userTaskRightsReceive() {
        try {
            val s = AntSportsRpcCall.userTaskGroupQuery("SPORTS_DAILY_GROUP")
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("group")
                val userTaskList = jo.getJSONArray("userTaskList")
                for (i in 0 until userTaskList.length()) {
                    jo = userTaskList.getJSONObject(i)
                    if ("COMPLETED" != jo.getString("status")) continue
                    val userTaskId = jo.getString("userTaskId")
                    val taskInfo = jo.getJSONObject("taskInfo")
                    val taskId = taskInfo.getString("taskId")
                    val res = JSONObject(AntSportsRpcCall.userTaskRightsReceive(taskId, userTaskId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val taskName = taskInfo.optString("taskName", taskId)
                        val rightsRuleList = taskInfo.getJSONArray("rightsRuleList")
                        val award = StringBuilder()
                        for (j in 0 until rightsRuleList.length()) {
                            val r = rightsRuleList.getJSONObject(j)
                            award.append(r.getString("rightsName"))
                                .append("*")
                                .append(r.getInt("baseAwardCount"))
                        }
                        Log.sports("领取奖励🎖️[$taskName]#$award")
                    } else {
                        Log.sports(TAG, "文体中心领取奖励")
                        Log.sports(res.toString())
                    }
                }
            } else {
                Log.sports(TAG, "文体中心领取奖励")
                Log.sports(s)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "userTaskRightsReceive err:", t)
        }
    }

    /**
     * @brief 文体中心路径特性查询 + 行走任务/加入路径
     */
    internal fun pathFeatureQuery() {
        try {
            val s = AntSportsRpcCall.pathFeatureQuery()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val path = jo.getJSONObject("path")
                val pathId = path.getString("pathId")
                val title = path.getString("title")
                val minGoStepCount = path.getInt("minGoStepCount")
                if (jo.has("userPath")) {
                    val userPath = jo.getJSONObject("userPath")
                    val userPathRecordStatus = userPath.getString("userPathRecordStatus")
                    if ("COMPLETED" == userPathRecordStatus) {
                        pathMapHomepage(pathId)
                        pathMapJoin(title, pathId)
                    } else if ("GOING" == userPathRecordStatus) {
                        pathMapHomepage(pathId)
                        val countDate = TimeUtil.getFormatDate()
                        jo = JSONObject(AntSportsRpcCall.stepQuery(countDate, pathId))
                        if (ResChecker.checkRes(TAG, jo)) {
                            val canGoStepCount = jo.getInt("canGoStepCount")
                            if (canGoStepCount >= minGoStepCount) {
                                val userPathRecordId = userPath.getString("userPathRecordId")
                                tiyubizGo(countDate, title, canGoStepCount, pathId, userPathRecordId)
                            }
                        }
                    }
                } else {
                    pathMapJoin(title, pathId)
                }
            } else {
                Log.sports(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "pathFeatureQuery err:", t)
        }
    }

    /**
     * @brief 文体中心地图首页 & 奖励领取
     */
    private fun pathMapHomepage(pathId: String) {
        try {
            val s = AntSportsRpcCall.pathMapHomepage(pathId)
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                if (!jo.has("userPathGoRewardList")) return
                val userPathGoRewardList = jo.getJSONArray("userPathGoRewardList")
                for (i in 0 until userPathGoRewardList.length()) {
                    jo = userPathGoRewardList.getJSONObject(i)
                    if ("UNRECEIVED" != jo.getString("status")) continue
                    val userPathRewardId = jo.getString("userPathRewardId")
                    val res = JSONObject(AntSportsRpcCall.rewardReceive(pathId, userPathRewardId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val detail = res.getJSONObject("userPathRewardDetail")
                        val rightsRuleList = detail.getJSONArray("userPathRewardRightsList")
                        val award = StringBuilder()
                        for (j in 0 until rightsRuleList.length()) {
                            val right = rightsRuleList.getJSONObject(j).getJSONObject("rightsContent")
                            award.append(right.getString("name"))
                                .append("*")
                                .append(right.getInt("count"))
                        }
                        Log.sports("文体宝箱🎁[$award]")
                    } else {
                        Log.sports(TAG, "文体中心开宝箱")
                        Log.sports(res.toString())
                    }
                }
            } else {
                Log.sports(TAG, "文体中心开宝箱")
                Log.sports(s)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "pathMapHomepage err:", t)
        }
    }

    /**
     * @brief 文体中心加入路线
     */
    private fun pathMapJoin(title: String, pathId: String) {
        try {
            val jo = JSONObject(AntSportsRpcCall.pathMapJoin(pathId))
            if (isSportsRpcSuccess(jo)) {
                Log.sports("加入线路🚶🏻‍♂️[$title]")
                pathFeatureQuery()
            } else if (isSportsRouteBusinessTerminal(extractSportsRpcErrorCode(jo), extractSportsRpcErrorMessage(jo))) {
                val errorCode = extractSportsRpcErrorCode(jo)
                val errorMsg = extractSportsRpcErrorMessage(jo)
                Log.sports(
                    TAG,
                    "文体中心路线[业务终态：已参加][$title][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                )
                pathFeatureQuery()
            } else {
                Log.error(TAG, "文体中心路线[加入失败][$title] raw=$jo")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "pathMapJoin err:", t)
        }
    }

    /**
     * @brief 文体中心行走逻辑
     */
    private fun tiyubizGo(
        countDate: String,
        title: String,
        goStepCount: Int,
        pathId: String,
        userPathRecordId: String
    ) {
        try {
            val s = AntSportsRpcCall.tiyubizGo(countDate, goStepCount, pathId, userPathRecordId)
            var jo = JSONObject(s)
            if (isSportsRpcSuccess(jo)) {
                jo = jo.getJSONObject("userPath")
                Log.sports(
                    "行走线路🚶🏻‍♂️[$title]#前进了" +
                        jo.getInt("userPathRecordForwardStepCount") + "步"
                )
                pathMapHomepage(pathId)
                val completed = "COMPLETED" == jo.getString("userPathRecordStatus")
                if (completed) {
                    Log.sports("完成线路🚶🏻‍♂️[$title]")
                    pathFeatureQuery()
                }
            } else if (isSportsRouteBusinessTerminal(extractSportsRpcErrorCode(jo), extractSportsRpcErrorMessage(jo))) {
                val errorCode = extractSportsRpcErrorCode(jo)
                val errorMsg = extractSportsRpcErrorMessage(jo)
                Log.sports(
                    TAG,
                    "文体中心路线[业务终态：已完成][$title][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                )
                pathMapHomepage(pathId)
                pathFeatureQuery()
            } else {
                Log.error(TAG, "文体中心路线[前进失败][$title] raw=$s")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "tiyubizGo err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 抢好友大战
    // ---------------------------------------------------------------------

    /**
     * @brief 抢好友主页查询 + 训练好友收益泡泡收集
     */
    internal fun queryClubHome() {
        try {
            val maxCount = getTrainFriendZeroCoinLimit()
            if (maxCount != null && hasReachedTrainFriendZeroCoinLimit()) {
                val today = TimeUtil.getDateStr2()
                DataStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                Log.sports(TAG, "✅ 训练好友获得0金币已达${maxCount}次上限，今日不再执行")
                return
            }
            val clubHomeData = JSONObject(AntSportsRpcCall.queryClubHome())
            processBubbleList(clubHomeData.optJSONObject("mainRoom"))
            val roomList = clubHomeData.optJSONArray("roomList")
            if (roomList != null) {
                for (i in 0 until roomList.length()) {
                    val room = roomList.optJSONObject(i)
                    processBubbleList(room)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryClubHome err:", t)
        }
    }

    /**
     * @brief 训练好友收益泡泡收集逻辑
     */
    private fun processBubbleList(obj: JSONObject?) {
        if (obj == null || !obj.has("bubbleList")) return
        try {
            val bubbleList = obj.optJSONArray("bubbleList") ?: return
            for (j in 0 until bubbleList.length()) {
                val bubble = bubbleList.optJSONObject(j) ?: continue
                val recordId = resolveTrainFriendBubbleRecordId(bubble)
                if (recordId.isBlank()) {
                    Log.error(TAG, "训练好友收益泡泡缺少可领取ID raw=$bubble")
                    continue
                }
                if (recordId in skippedTrainBubbleRecordIds) {
                    continue
                }

                val responseStr = AntSportsRpcCall.pickBubbleTaskEnergy(recordId, false)
                val responseJson = JSONObject(responseStr)

                if (!isSportsRpcSuccess(responseJson)) {
                    val errorCode = extractSportsRpcErrorCode(responseJson)
                    val errorMsg = extractSportsRpcErrorMessage(responseJson)
                    if (isTrainFriendBubbleAlreadyHandled(errorCode, errorMsg)) {
                        skippedTrainBubbleRecordIds.add(recordId)
                        Log.sports(
                            TAG,
                            "训练好友收益泡泡已处理[recordId=$recordId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                        )
                        continue
                    }
                    if (isTrainFriendBubbleInvalid(errorCode, errorMsg)) {
                        skippedTrainBubbleRecordIds.add(recordId)
                        Log.sports(
                            TAG,
                            "训练好友收益泡泡已失效[recordId=$recordId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                        )
                        continue
                    }
                    if (errorCode == "CAMP_TRIGGER_ERROR") {
                        Log.error(
                            TAG,
                            "收取训练好友失败-业务RPC受限[recordId=$recordId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$responseStr"
                        )
                        continue
                    }
                    if (!isSportsRpcRetryable(responseJson)) {
                        Log.error(
                            TAG,
                            "收取训练好友失败-非重试RPC[recordId=$recordId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$responseStr"
                        )
                        continue
                    }
                    Log.error(
                        TAG,
                        "收取训练好友失败[recordId=$recordId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$responseStr"
                    )
                    continue
                }

                var amount = 0
                val data = responseJson.optJSONObject("data")
                if (data != null) {
                    val changeAmountStr = data.optString("changeAmount", "0")
                    amount = changeAmountStr.toIntOrNull() ?: 0
                }

                Log.sports("训练好友💰️ [获得:$amount 金币]")

                if (amount <= 0) {
                    zeroTrainCoinCount++
                    val maxCount = getTrainFriendZeroCoinLimit()
                    if (maxCount != null && hasReachedTrainFriendZeroCoinLimit()) {
                        val today = TimeUtil.getDateStr2()
                        DataStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                        Log.sports(TAG, "✅ 连续获得0金币已达${maxCount}次，今日停止执行")
                        return
                    } else if (maxCount != null) {
                        Log.sports(TAG, "训练好友0金币计数: $zeroTrainCoinCount/$maxCount")
                    }
                }

                GlobalThreadPools.sleepCompat(1000)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "processBubbleList 异常:", t)
        }
    }

    private fun resolveTrainFriendBubbleRecordId(bubble: JSONObject): String {
        return sequenceOf(
            bubble.optString("medEnergyBallInfoRecordId", ""),
            bubble.optString("recordId", ""),
            bubble.optString("assetId", ""),
            bubble.optString("bubbleId", "")
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    private fun isTrainFriendBubbleAlreadyHandled(errorCode: String, errorMsg: String): Boolean {
        val errorText = "$errorCode $errorMsg"
        return errorCode == "RECEIVE_REWARD_REPEATED" ||
            errorText.contains("已领取") ||
            errorText.contains("已经领取") ||
            errorText.contains("重复领取")
    }

    private fun isTrainFriendBubbleInvalid(errorCode: String, errorMsg: String): Boolean {
        val errorText = "$errorCode $errorMsg"
        return errorCode == "ENERGY_BALL_STATUS_IS_INVALID_ERROR" ||
            errorText.contains("能量球数据状态校验错误") ||
            errorText.contains("已失效") ||
            errorText.contains("已过期")
    }

    /**
     * @brief 训练好友：选取可训练好友并执行一次训练
     */
    internal fun queryTrainItem() {
        if (trainFriend.value != true) {
            return
        }
        val maxCount = getTrainFriendZeroCoinLimit()
        if (maxCount != null && hasReachedTrainFriendZeroCoinLimit()) {
            Log.sports(TAG, "训练好友🥋0金币次数已达上限，跳过继续训练")
            return
        }
        try {
            var trainedAny = false
            val skippedOriginBossIds = mutableSetOf<String>()
            var memberChangedRetryCount = 0
            while (true) {
                if (maxCount != null && hasReachedTrainFriendZeroCoinLimit()) {
                    Log.sports(TAG, "训练好友🥋0金币次数已达上限，停止继续训练")
                    return
                }

                val clubHomeData = queryClubHomeForTraining() ?: return
                processClubRoomBubbleRewards(clubHomeData)
                if (maxCount != null && hasReachedTrainFriendZeroCoinLimit()) {
                    Log.sports(TAG, "训练好友🥋0金币次数已达上限，停止继续训练")
                    return
                }

                val trainTarget = findNextTrainTarget(clubHomeData, skippedOriginBossIds)
                if (trainTarget == null) {
                    if (!trainedAny) {
                        Log.sports(TAG, "训练好友🥋当前没有可训练好友")
                    }
                    return
                }

                val trainItemSelection = queryBestTrainItemSelection() ?: return
                val trainMemberJson = JSONObject(
                    AntSportsRpcCall.trainMember(
                        trainItemSelection.bizId,
                        trainItemSelection.itemType,
                        trainTarget.memberId,
                        trainTarget.originBossId
                    )
                )
                if (!isSportsRpcSuccess(trainMemberJson)) {
                    val errorCode = extractSportsRpcErrorCode(trainMemberJson)
                    val errorMsg = extractSportsRpcErrorMessage(trainMemberJson)
                    if (errorCode == "CLUB_MEMBER_CHANGED") {
                        skippedOriginBossIds.add(trainTarget.originBossId)
                        memberChangedRetryCount++
                        Log.sports(
                            TAG,
                            "训练好友[目标已变化，跳过本轮目标并重试][friend=${trainTarget.userName}][retry=$memberChangedRetryCount/$MAX_TRAIN_MEMBER_CHANGED_RETRIES]"
                        )
                        if (memberChangedRetryCount >= MAX_TRAIN_MEMBER_CHANGED_RETRIES) {
                            Log.sports(TAG, "训练好友[CLUB_MEMBER_CHANGED重试已达上限，结束本轮训练]")
                            return
                        }
                        GlobalThreadPools.sleepCompat(500)
                        continue
                    }
                    Log.error(
                        TAG,
                        "训练好友[trainMember失败][friend=${trainTarget.userName}][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$trainMemberJson"
                    )
                    return
                }

                trainedAny = true
                Log.sports("训练好友🥋[训练:${trainTarget.userName} ${trainItemSelection.itemName}]")
                GlobalThreadPools.sleepCompat(1000)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryTrainItem err:", t)
        }
    }

    /**
     * @brief 抢好友大战：抢购好友逻辑
     */
    internal fun buyMember() {
        try {
            var clubHomeJson = queryClubHomeForBattle() ?: return
            if (!isClubHomeEnabled(clubHomeJson)) return
            clubHomeJson = unlockNextRoomsIfPossible(clubHomeJson)

            var boughtAny = false
            roomLoop@ while (true) {
                if (!isClubHomeEnabled(clubHomeJson)) return

                val coinBalance = clubHomeJson.optJSONObject("assetsInfo")?.optInt("energyBalance", 0) ?: 0
                if (coinBalance <= 0) {
                    Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑当前能量为0，跳过抢好友")
                    return
                }

                val room = findEmptyClubRoom(clubHomeJson)
                if (room == null) {
                    Log.sports(TAG, if (boughtAny) "抢好友大战🧑‍🤝‍🧑空场地已处理完" else "抢好友大战🧑‍🤝‍🧑暂无空场地")
                    return
                }
                val roomId = room.optString("roomId", "")
                if (roomId.isBlank()) {
                    Log.error(TAG, "抢好友大战🧑‍🤝‍🧑空场地缺少roomId raw=$room")
                    return
                }

                val memberPriceJson = queryMemberPriceRankingForBattle(coinBalance) ?: return
                val candidates = collectClubMemberCandidates(memberPriceJson)
                if (candidates.isEmpty()) {
                    Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑暂无可抢好友")
                    return
                }

                var matchedTarget = false
                for (candidate in candidates) {
                    if (!shouldRobClubMember(candidate.originBossId)) {
                        continue
                    }
                    matchedTarget = true
                    if (candidate.price > coinBalance) {
                        continue
                    }
                    if (FriendGuard.shouldSkipFriend(candidate.originBossId, TAG, "抢好友")) {
                        continue
                    }

                    val memberObj = queryClubMemberForBattle(candidate) ?: continue
                    val currentBossId = memberObj.optString("currentBossId", candidate.currentBossId)
                    val memberId = memberObj.optString("memberId", candidate.memberId)
                    val priceInfo = memberObj.optJSONObject("priceInfo")
                    if (currentBossId.isBlank() || memberId.isBlank() || priceInfo == null) {
                        Log.error(TAG, "抢好友大战🧑‍🤝‍🧑成员详情缺少必要字段[originBossId=${candidate.originBossId}] raw=$memberObj")
                        continue
                    }
                    val realPrice = priceInfo.optInt("price", candidate.price)
                    if (realPrice > coinBalance) {
                        continue
                    }

                    val buyMemberResponse = JSONObject(
                        AntSportsRpcCall.buyMember(
                            currentBossId,
                            memberId,
                            candidate.originBossId,
                            priceInfo.toString(),
                            roomId
                        )
                    )
                    GlobalThreadPools.sleepCompat(500)

                    if (isSportsRpcSuccess(buyMemberResponse)) {
                        boughtAny = true
                        val userName = UserMap.getMaskName(candidate.originBossId) ?: candidate.originBossId
                        Log.sports("抢购好友🥋[成功:将 $userName 抢回来，消耗${realPrice}能量]")
                        clubHomeJson = queryClubHomeForBattle() ?: return
                        if (trainFriend.value == true) {
                            queryTrainItem()
                            clubHomeJson = queryClubHomeForBattle() ?: return
                        }
                        clubHomeJson = unlockNextRoomsIfPossible(clubHomeJson)
                        continue@roomLoop
                    }

                    val errorCode = extractSportsRpcErrorCode(buyMemberResponse)
                    val errorMsg = extractSportsRpcErrorMessage(buyMemberResponse)
                    when {
                        errorCode == "CLUB_AMOUNT_NOT_ENOUGH" -> {
                            Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑能量不足，停止抢购[code=$errorCode][msg=$errorMsg]")
                            return
                        }

                        isClubRetryableConflict(errorCode, errorMsg) -> {
                            Log.sports(
                                TAG,
                                "抢好友大战🧑‍🤝‍🧑成员或房间状态变化，刷新主页后重试[code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                            )
                            clubHomeJson = queryClubHomeForBattle() ?: return
                            continue@roomLoop
                        }

                        else -> {
                            Log.error(
                                TAG,
                                "抢好友大战🧑‍🤝‍🧑抢购失败[originBossId=${candidate.originBossId}][roomId=$roomId][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$buyMemberResponse"
                            )
                            return
                        }
                    }
                }

                Log.sports(
                    TAG,
                    if (matchedTarget) "抢好友大战🧑‍🤝‍🧑可抢目标能量不足或不可交易" else "抢好友大战🧑‍🤝‍🧑未匹配到配置中的可抢好友"
                )
                return
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "buyMember err:", t)
        }
    }

    private fun queryClubHomeForBattle(): JSONObject? {
        val clubHomeJson = JSONObject(AntSportsRpcCall.queryClubHome())
        GlobalThreadPools.sleepCompat(500)
        if (!isSportsRpcSuccess(clubHomeJson)) {
            Log.error(
                TAG,
                "抢好友大战🧑‍🤝‍🧑queryClubHome失败[code=${extractSportsRpcErrorCode(clubHomeJson).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(clubHomeJson)}] raw=$clubHomeJson"
            )
            return null
        }
        return clubHomeJson
    }

    private fun isClubHomeEnabled(clubHomeJson: JSONObject): Boolean {
        if (clubHomeJson.optString("clubAuth", "") == "ENABLE") {
            return true
        }
        Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑未授权开启[clubAuth=${clubHomeJson.optString("clubAuth", "UNKNOWN")}]")
        return false
    }

    private fun unlockNextRoomsIfPossible(clubHomeJson: JSONObject): JSONObject {
        if (!::battleAutoUnlockRoom.isInitialized || battleAutoUnlockRoom.value != true) {
            return clubHomeJson
        }

        var latestHome = clubHomeJson
        var unlockCount = 0
        while (unlockCount < 10) {
            val unlockPrice = latestHome.optInt("nextRoomUnlockPrice", 0)
            if (unlockPrice <= 0) {
                return latestHome
            }
            val energyBalance = latestHome.optJSONObject("assetsInfo")?.optInt("energyBalance", 0) ?: 0
            if (energyBalance < unlockPrice) {
                Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑解锁新场地能量不足[balance=$energyBalance, price=$unlockPrice]")
                return latestHome
            }

            val unlockResp = JSONObject(AntSportsRpcCall.unlockNextRoom(energyBalance))
            GlobalThreadPools.sleepCompat(500)
            if (isSportsRpcSuccess(unlockResp)) {
                unlockCount++
                Log.sports(
                    TAG,
                    "抢好友大战🧑‍🤝‍🧑解锁新场地成功[price=$unlockPrice,balanceBefore=$energyBalance,nextPrice=${unlockResp.optInt("nextRoomUnlockPrice", 0)}]"
                )
                latestHome = queryClubHomeForBattle() ?: return latestHome
                continue
            }

            val errorCode = extractSportsRpcErrorCode(unlockResp)
            val errorMsg = extractSportsRpcErrorMessage(unlockResp)
            if (errorCode == "CLUB_AMOUNT_NOT_ENOUGH") {
                Log.sports(TAG, "抢好友大战🧑‍🤝‍🧑解锁新场地能量不足[code=$errorCode][msg=$errorMsg]")
                return latestHome
            }
            if (isClubRetryableConflict(errorCode, errorMsg)) {
                Log.sports(
                    TAG,
                    "抢好友大战🧑‍🤝‍🧑解锁场地遇到状态变化，刷新主页[code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                )
                return queryClubHomeForBattle() ?: latestHome
            }

            Log.error(
                TAG,
                "抢好友大战🧑‍🤝‍🧑解锁新场地失败[price=$unlockPrice,balance=$energyBalance][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$unlockResp"
            )
            return latestHome
        }
        Log.error(TAG, "抢好友大战🧑‍🤝‍🧑解锁新场地达到保护上限，停止解锁")
        return latestHome
    }

    private fun findEmptyClubRoom(clubHomeJson: JSONObject): JSONObject? {
        val roomList = clubHomeJson.optJSONArray("roomList") ?: return null
        for (i in 0 until roomList.length()) {
            val room = roomList.optJSONObject(i) ?: continue
            val memberList = room.optJSONArray("memberList")
            if (memberList == null || memberList.length() == 0) {
                return room
            }
        }
        return null
    }

    private fun queryMemberPriceRankingForBattle(coinBalance: Int): JSONObject? {
        val memberPriceJson = JSONObject(AntSportsRpcCall.queryMemberPriceRanking(coinBalance))
        GlobalThreadPools.sleepCompat(500)
        if (!isSportsRpcSuccess(memberPriceJson)) {
            Log.error(
                TAG,
                "抢好友大战🧑‍🤝‍🧑queryMemberPriceRanking失败[coinBalance=$coinBalance][code=${extractSportsRpcErrorCode(memberPriceJson).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(memberPriceJson)}] raw=$memberPriceJson"
            )
            return null
        }
        return memberPriceJson
    }

    private fun collectClubMemberCandidates(memberPriceJson: JSONObject): List<ClubMemberCandidate> {
        val candidates = mutableListOf<ClubMemberCandidate>()

        val memberDetailList = memberPriceJson.optJSONArray("memberDetailList")
        if (memberDetailList != null) {
            for (i in 0 until memberDetailList.length()) {
                val memberModel = memberDetailList.optJSONObject(i)?.optJSONObject("memberModel") ?: continue
                val memberId = memberModel.optString("memberId", "")
                val originBossId = memberModel.optString("originBossId", "")
                if (memberId.isBlank() || originBossId.isBlank()) continue
                candidates.add(
                    ClubMemberCandidate(
                        currentBossId = memberModel.optString("currentBossId", ""),
                        memberId = memberId,
                        originBossId = originBossId,
                        price = memberModel.optJSONObject("priceInfo")?.optInt("price", Int.MAX_VALUE)
                            ?: memberModel.optInt("price", Int.MAX_VALUE)
                    )
                )
            }
        }

        val rankData = memberPriceJson.optJSONObject("rank")?.optJSONArray("data")
        if (rankData != null) {
            for (i in 0 until rankData.length()) {
                val item = rankData.optJSONObject(i) ?: continue
                val memberId = item.optString("memberId", "")
                val originBossId = item.optString("originBossId", "")
                if (memberId.isBlank() || originBossId.isBlank()) continue
                candidates.add(
                    ClubMemberCandidate(
                        currentBossId = item.optString("currentBossId", ""),
                        memberId = memberId,
                        originBossId = originBossId,
                        price = item.optInt("price", Int.MAX_VALUE)
                    )
                )
            }
        }

        return candidates.distinctBy { "${it.memberId}#${it.originBossId}" }
    }

    private fun shouldRobClubMember(originBossId: String): Boolean {
        var isTarget = originBossIdList.value?.contains(originBossId) == true
        if (battleForFriendType.value == BattleForFriendType.DONT_ROB) {
            isTarget = !isTarget
        }
        return isTarget
    }

    private fun queryClubMemberForBattle(candidate: ClubMemberCandidate): JSONObject? {
        val clubMemberDetailJson = JSONObject(
            AntSportsRpcCall.queryClubMember(candidate.memberId, candidate.originBossId)
        )
        GlobalThreadPools.sleepCompat(500)
        if (!isSportsRpcSuccess(clubMemberDetailJson) || !clubMemberDetailJson.has("member")) {
            Log.error(
                TAG,
                "抢好友大战🧑‍🤝‍🧑queryClubMember失败[memberId=${candidate.memberId}][originBossId=${candidate.originBossId}][code=${extractSportsRpcErrorCode(clubMemberDetailJson).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(clubMemberDetailJson)}] raw=$clubMemberDetailJson"
            )
            return null
        }
        return clubMemberDetailJson.optJSONObject("member")
    }

    private fun isClubRetryableConflict(errorCode: String, errorMsg: String): Boolean {
        val text = "$errorCode $errorMsg"
        return errorCode == "CLUB_MEMBER_TRADE_PROTECT" ||
            errorCode == "CLUB_MEMBER_CHANGED" ||
            text.contains("独处") ||
            text.contains("保护") ||
            text.contains("变化") ||
            text.contains("已被抢")
    }

    // ---------------------------------------------------------------------
    // 健康岛任务处理器（内部类）
    // ---------------------------------------------------------------------

    /**
     * @brief 健康岛任务处理器
     *
     * <p>整体流程：</p>
     * <ol>
     *   <li>签到（querySign + takeSign）</li>
     *   <li>任务大厅循环处理（queryTaskCenter + taskSend / adtask.finish）</li>
     *   <li>健康岛浏览任务（queryTaskInfo + energyReceive）</li>
     *   <li>捡泡泡（queryBubbleTask + pickBubbleTaskEnergy）</li>
     *   <li>走路建造 / 旧版行走（queryBaseinfo + queryMapInfo/Build/WalkGrid 等）</li>
     * </ol>
     */
    @Suppress("GrazieInspection")
    inner class NeverlandTaskHandler {

        private val TAG = "Neverland"

        /** @brief 最大失败次数（优先使用 BaseModel 配置，默认 5 次） */
        private val MAX_ERROR_COUNT: Int = run {
            val v = BaseModel.setMaxErrorCount.value ?: 0
            if (v > 0) v else 5
        }

        /** @brief 任务循环间隔（毫秒） */
        private val TASK_LOOP_DELAY: Long = 1000
        private val pickedNeverlandBubbleRecordIds = mutableSetOf<String>()
        private val handledNeverlandBubbleEncryptValues = mutableSetOf<String>()

        /**
         * @brief 健康岛任务入口
         */
        fun runNeverland() {
            try {
                Log.sports(TAG, "开始执行健康岛任务")
                if (neverlandTask.value == true) {
                    // 1. 签到
                    neverlandDoSign()
                    // 2. 任务大厅循环处理
                    loopHandleTaskCenter()
                    // 3. 浏览任务
                    handleHealthIslandTask()
                    // 4. 捡泡泡
                    neverlandPickAllBubble()
                }

                if (neverlandGrid.value == true) {
                    // 5. 自动走路建造
                    neverlandAutoTask()
                }

                Log.sports(TAG, "健康岛任务结束")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "runNeverland err:", t)
            }
        }

        // ---------------------------------------------------------------
        // 1. 健康岛签到
        // ---------------------------------------------------------------

        /**
         * @brief 健康岛签到流程
         */
        private fun neverlandDoSign() {
            try {
                if (Status.hasFlagToday(StatusFlags.FLAG_NEVERLAND_SIGN_DONE)) return

                Log.sports(TAG, "健康岛 · 检查签到状态")
                val jo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.querySign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "查询签到失败:", jo) ||
                    !ResChecker.checkRes(TAG, jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    val errorCode = jo.optString("errorCode", "")
                    if ("ALREADY_SIGN_IN" == errorCode ||
                        "已签到" == jo.optString("errorMsg", "")
                    ) {
                        Status.setFlagToday(StatusFlags.FLAG_NEVERLAND_SIGN_DONE)
                    }
                    return
                }

                val data = jo.getJSONObject("data")
                val signInfo = data.optJSONObject("continuousSignInfo")
                if (signInfo != null && signInfo.optBoolean("signedToday", false)) {
                    Log.sports(
                        TAG,
                        "今日已签到 ✔ 连续：${signInfo.optInt("continuitySignedDayCount")} 天"
                    )
                    return
                }

                Log.sports(TAG, "健康岛 · 正在签到…")
                val signRes = JSONObject(AntSportsRpcCall.NeverlandRpcCall.takeSign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "签到失败:", signRes) ||
                    !ResChecker.checkRes(TAG, signRes) ||
                    signRes.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "takeSign raw=$signRes")
                    Status.setFlagToday(StatusFlags.FLAG_NEVERLAND_SIGN_DONE)
                    return
                }

                val signData = signRes.getJSONObject("data")
                val reward = signData.optJSONObject("continuousDoSignInVO")
                val rewardAmount = reward?.optInt("rewardAmount", 0) ?: 0
                val rewardType = reward?.optString("rewardType", "") ?: ""
                val signInfoAfter = signData.optJSONObject("continuousSignInfo")
                val newContinuity = signInfoAfter?.optInt("continuitySignedDayCount", -1) ?: -1

                Log.sports(
                    "健康岛签到成功 🎉 +" + rewardAmount + rewardType +
                        " 连续：" + newContinuity + " 天"
                )
                Status.setFlagToday(StatusFlags.FLAG_NEVERLAND_SIGN_DONE)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "neverlandDoSign err:$t", t)
            }
        }

        // ---------------------------------------------------------------
        // 2. 任务大厅循环处理
        // ---------------------------------------------------------------

        /**
         * @brief 循环处理健康岛任务大厅中的 PROMOKERNEL_TASK & LIGHT_TASK
         */
        private fun loopHandleTaskCenter() {
            var errorCount = 0
            Log.sports(TAG, "开始循环处理任务大厅（失败限制：$MAX_ERROR_COUNT 次）")

            while (!Thread.currentThread().isInterrupted) {
                try {
                    if (errorCount >= MAX_ERROR_COUNT) {
                        Log.error(TAG, "任务处理失败次数达到上限，停止循环")
                        Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_TASK_CENTER_DONE)
                        break
                    }

                    val taskCenterResp = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryTaskCenter())
                    if (!ResChecker.checkRes(TAG, taskCenterResp) ||
                        taskCenterResp.optJSONObject("data") == null
                    ) {
                        errorCount++
                        GlobalThreadPools.sleepCompat(TASK_LOOP_DELAY)
                        continue
                    }

                    val taskList = taskCenterResp.getJSONObject("data").optJSONArray("taskCenterTaskVOS")
                    if (taskList == null || taskList.length() == 0) {
                        Log.sports("任务中心为空，无任务可处理")
                        break
                    }

                    val pendingTasks = mutableListOf<JSONObject>()
                    for (i in 0 until taskList.length()) {
                        val task = taskList.optJSONObject(i) ?: continue

                        val title = task.optString("title", task.optString("taskName", "未知任务"))
                        val type = task.optString("taskType", "")
                        val status = task.optString("taskStatus", "")
                        val taskId = task.optString("id", task.optString("taskId", ""))

                        if ("NOT_SIGNUP" == status) {
                            Log.sports(TAG, "任务 [$title] 需要手动报名，已自动拉黑并跳过")
                            if (taskId.isNotEmpty()) {
                                TaskBlacklist.addToBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId, title)
                            }
                            continue
                        }

                        if (TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, taskId) ||
                            TaskBlacklist.isTaskInBlacklist(SPORTS_TASK_BLACKLIST_MODULE, title)
                        ) {
                            continue
                        }

                        if (("PROMOKERNEL_TASK" == type || "LIGHT_TASK" == type) &&
                            "FINISHED" != status
                        ) {
                            pendingTasks.add(task)
                        }
                    }

                    if (pendingTasks.isEmpty()) {
                        Log.sports(TAG, "没有可处理或领取的任务，退出循环")
                        break
                    }

                    Log.sports(TAG, "本次发现 ${pendingTasks.size} 个可处理任务（含待领取）")

                    var currentBatchError = 0
                    for (task in pendingTasks) {
                        val ok = handleSingleTask(task)
                        if (!ok) currentBatchError++
                        GlobalThreadPools.sleepCompat(3000)
                    }

                    errorCount += currentBatchError
                    Log.sports(TAG, "当前批次执行完毕，准备下一次刷新检查")
                    GlobalThreadPools.sleepCompat(TASK_LOOP_DELAY)
                } catch (t: Throwable) {
                    errorCount++
                    Log.printStackTrace(TAG, "循环异常", t)
                }
            }
        }

        /**
         * @brief 处理单个大厅任务
         */
        private fun handleSingleTask(task: JSONObject): Boolean {
            return try {
                val title = task.optString("title", "未知任务")
                val type = task.optString("taskType", "")
                val status = task.optString("taskStatus", "")
                val jumpLink = task.optString("jumpLink", "")

                Log.sports(TAG, "任务：[$title] 状态：$status 类型：$type")

                if ("TO_RECEIVE" == status) {
                    try {
                        task.put("scene", "MED_TASK_HALL")
                        if (!task.has("source")) {
                            task.put("source", "jkdsportcard")
                        }

                        val res = JSONObject(AntSportsRpcCall.NeverlandRpcCall.taskReceive(task))
                        if (res.optBoolean("success", false)) {
                            val data = res.optJSONObject("data")
                            var rewardDetail = ""
                            if (data != null && data.has("userItems")) {
                                val items = data.getJSONArray("userItems")
                                val sb = StringBuilder()
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val name = item.optString("name", "未知奖励")
                                    val amount = item.optInt("modifyCount", 0)
                                    val total = item.optInt("count", 0)
                                    sb.append("[").append(name)
                                        .append(" +").append(amount)
                                        .append(" (余:").append(total).append(")] ")
                                }
                                rewardDetail = sb.toString()
                            }
                            Log.sports(TAG, "完成[$title]✔$rewardDetail")
                            return true
                        } else {
                            val errorMsg = res.optString("errorMsg", "未知错误")
                            val errorCode = res.optString("errorCode", "UNKNOWN")
                            Log.error(TAG, "❌ 奖励领取失败 [$errorCode]: $errorMsg")
                            return false
                        }
                    } catch (e: Exception) {
                        Log.error(TAG, "领取流程异常: ${e.message}")
                        return false
                    }
                }

                if ("SIGNUP_COMPLETE" == status || "INIT" == status) {
                    return when (type) {
                        "PROMOKERNEL_TASK" -> handlePromoKernelTask(task, title)
                        "LIGHT_TASK" -> handleLightTask(task, title, jumpLink)
                        else -> {
                            Log.error(TAG, "未处理的任务类型：$type")
                            false
                        }
                    }
                }

                Log.sports(TAG, "任务状态为 $status，跳过执行")
                true
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "handleSingleTask 异常", e)
                false
            }
        }

        // ---------------------------------------------------------------
        // 3. 健康岛浏览任务
        // ---------------------------------------------------------------

        /**
         * @brief 处理健康岛浏览任务（LIGHT_FEEDS_TASK）
         */
        private fun handleHealthIslandTask() {
            try {
                Log.sports(TAG, "开始检查健康岛浏览任务")
                var hasTask = true
                val completedEncryptValues = mutableSetOf<String>()
                val nonRetryableEncryptValues = mutableSetOf<String>()
                while (hasTask) {
                    val taskInfoResp = JSONObject(
                        AntSportsRpcCall.NeverlandRpcCall.queryTaskInfo(
                            "health-island",
                            "LIGHT_FEEDS_TASK"
                        )
                    )

                    if (!ResChecker.checkRes(TAG + "查询健康岛浏览任务失败:", taskInfoResp) ||
                        taskInfoResp.optJSONObject("data") == null
                    ) {
                        Log.error(TAG, "健康岛浏览任务查询失败 [$taskInfoResp] 请关闭此功能")
                        return
                    }

                    val taskInfos = taskInfoResp.getJSONObject("data").optJSONArray("taskInfos")
                    if (taskInfos == null || taskInfos.length() == 0) {
                        Log.sports(TAG, "健康岛浏览任务列表为空")
                        hasTask = false
                        continue
                    }

                    val pendingTaskInfos = mutableListOf<JSONObject>()
                    val roundEncryptValues = mutableSetOf<String>()
                    var repeatedTaskCount = 0

                    for (i in 0 until taskInfos.length()) {
                        val taskInfo = taskInfos.getJSONObject(i)
                        val taskTitle = taskInfo.optString("title", taskInfo.optString("taskName", "未知任务"))
                        val encryptValue = taskInfo.optString("encryptValue")
                        val viewSec = taskInfo.optInt("viewSec", 15)
                        val energyNum = taskInfo.optInt("energyNum", 0)

                        if (encryptValue.isEmpty()) {
                            Log.error(
                                TAG,
                                "健康岛任务[$taskTitle] encryptValue 为空，跳过 [viewSec=$viewSec][energyNum=$energyNum]"
                            )
                            continue
                        }
                        if (encryptValue in completedEncryptValues ||
                            encryptValue in nonRetryableEncryptValues ||
                            !roundEncryptValues.add(encryptValue)
                        ) {
                            repeatedTaskCount++
                            continue
                        }
                        pendingTaskInfos.add(taskInfo)
                    }

                    if (pendingTaskInfos.isEmpty()) {
                        if (repeatedTaskCount > 0) {
                            Log.sports(
                                TAG,
                                "健康岛浏览任务本轮仅返回已处理任务，停止循环以避免重复完成[count=$repeatedTaskCount]"
                            )
                        }
                        hasTask = false
                        continue
                    }

                    for (taskInfo in pendingTaskInfos) {
                        val taskTitle = taskInfo.optString("title", taskInfo.optString("taskName", "未知任务"))
                        val encryptValue = taskInfo.optString("encryptValue")
                        val energyNum = taskInfo.optInt("energyNum", 0)
                        val viewSec = taskInfo.optInt("viewSec", 15)

                        Log.sports(
                            TAG,
                            "健康岛浏览任务[$taskTitle]：能量+$energyNum，直接提交领取RPC(viewSec=${viewSec}s)"
                        )

                        val receiveResp = JSONObject(
                            AntSportsRpcCall.NeverlandRpcCall.energyReceive(
                                encryptValue,
                                energyNum,
                                "LIGHT_FEEDS_TASK",
                                null
                            )
                        )
                        if (ResChecker.checkRes(TAG + "领取健康岛任务奖励:", receiveResp) &&
                            ResChecker.checkRes(TAG, receiveResp)
                        ) {
                            completedEncryptValues.add(encryptValue)
                            Log.sports("✅ 健康岛浏览任务[$taskTitle]完成，获得能量+$energyNum")
                        } else {
                            if (!isSportsRpcRetryable(receiveResp)) {
                                nonRetryableEncryptValues.add(encryptValue)
                                Log.sports(
                                    TAG,
                                    "健康岛任务[$taskTitle]非重试失败，加入本轮跳过列表[encryptValue=$encryptValue]"
                                )
                            }
                            Log.error(
                                TAG,
                                "健康岛任务领取失败[$taskTitle][viewSec=$viewSec][energyNum=$energyNum][encryptValue=$encryptValue]: $receiveResp"
                            )
                        }

                        GlobalThreadPools.sleepCompat(1000)
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "handleHealthIslandTask err", t)
            }
        }

        // ---------------------------------------------------------------
        // 4. PROMOKERNEL_TASK / LIGHT_TASK 处理
        // ---------------------------------------------------------------

        /**
         * @brief 处理 PROMOKERNEL_TASK（活动类任务）
         */
        private fun handlePromoKernelTask(task: JSONObject, title: String): Boolean {
            return try {
                task.put("scene", "MED_TASK_HALL")
                val res = JSONObject(AntSportsRpcCall.NeverlandRpcCall.taskSend(task))
                if (ResChecker.checkRes(TAG, res)) {
                    Log.sports("✔ 活动任务完成：$title")
                    true
                } else {
                    Log.error(TAG, "taskSend 失败: $task 响应：$res")
                    false
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "handlePromoKernelTask 处理 PROMOKERNEL_TASK 异常（$title）", e)
                false
            }
        }

        /**
         * @brief 处理 LIGHT_TASK（浏览类任务）
         */
        private fun handleLightTask(task: JSONObject, title: String, jumpLink: String): Boolean {
            return try {
                var bizId = task.optString("bizId", "")
                if (bizId.isEmpty()) {
                    val logExtMap = task.optJSONObject("logExtMap")
                    if (logExtMap != null) {
                        bizId = logExtMap.optString("bizId", "")
                    }
                }

                if (bizId.isEmpty()) {
                    Log.error(TAG, "LIGHT_TASK 未找到 bizId：$title jumpLink=$jumpLink")
                    return false
                }

                val res = JSONObject(AntSportsRpcCall.NeverlandRpcCall.finish(bizId))
                if (res.optBoolean("success", false) ||
                    "0" == res.optString("errCode", "")
                ) {
                    var rewardMsg = ""
                    val extendInfo = res.optJSONObject("extendInfo")
                    if (extendInfo != null) {
                        val rewardInfo = extendInfo.optJSONObject("rewardInfo")
                        if (rewardInfo != null) {
                            val amount = rewardInfo.optString("rewardAmount", "0")
                            rewardMsg = " (获得奖励: $amount 能量)"
                        }
                    }
                    Log.sports("✔ 浏览任务完成：$title$rewardMsg")
                    true
                } else {
                    Log.error(TAG, "完成 LIGHT_TASK 失败: $title 返回: $res")
                    false
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "handleLightTask 处理 LIGHT_TASK 异常（$title）", e)
                false
            }
        }

        // ---------------------------------------------------------------
        // 5. 捡泡泡
        // ---------------------------------------------------------------

        /**
         * @brief 健康岛捡泡泡 + 浏览类泡泡任务
         */
        private fun neverlandPickAllBubble() {
            try {
                Log.sports(TAG, "健康岛 · 检查可领取泡泡")

                val jo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryBubbleTask())

                if (!ResChecker.checkRes(TAG + "查询泡泡失败:", jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "queryBubbleTask raw=$jo")
                    return
                }

                val arr = jo.getJSONObject("data").optJSONArray("bubbleTaskVOS")
                if (arr == null || arr.length() == 0) {
                    Log.sports("无泡泡可领取")
                    return
                }

                val ids = mutableListOf<String>()
                val encryptValues = mutableListOf<String>()

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val bubbleTaskStatus = item.optString("bubbleTaskStatus")
                    val encryptValue = item.optString("encryptValue")
                    val energyNum = item.optInt("energyNum", 0)
                    val viewSec = item.optInt("viewSec", 15)

                    if ("INIT" == bubbleTaskStatus && encryptValue.isNotEmpty()) {
                        if (encryptValue in handledNeverlandBubbleEncryptValues) {
                            Log.sports(
                                TAG,
                                "浏览任务已处理，跳过重复提交：${item.optString("title")}"
                            )
                            continue
                        }
                        encryptValues.add(encryptValue)
                        Log.sports(
                            TAG,
                            "找到可浏览任务： ${item.optString("title")}，能量+$energyNum，直接提交领取RPC(viewSec=${viewSec}s)"
                        )
                    } else if (!item.optBoolean("initState") &&
                        item.optString("medEnergyBallInfoRecordId").isNotEmpty()
                    ) {
                        val recordId = item.getString("medEnergyBallInfoRecordId")
                        if (recordId in pickedNeverlandBubbleRecordIds) {
                            Log.sports(TAG, "泡泡奖励已处理，跳过重复领取[recordId=$recordId]")
                            continue
                        }
                        ids.add(recordId)
                    }
                }

                if (ids.isEmpty() && encryptValues.isEmpty()) {
                    Log.sports(TAG, "没有可领取的泡泡任务")
                    return
                }

                if (ids.isNotEmpty()) {
                    Log.sports(TAG, "健康岛 · 正在领取 ${ids.size} 个泡泡…")
                    val pick = JSONObject(AntSportsRpcCall.NeverlandRpcCall.pickBubbleTaskEnergy(ids))

                    if (!ResChecker.checkRes(TAG + "领取泡泡失败:", pick) ||
                        pick.optJSONObject("data") == null
                    ) {
                        Log.error(TAG, "pickBubbleTaskEnergy raw=$pick")
                        return
                    }

                    val data = pick.getJSONObject("data")
                    val changeAmount = data.optString("changeAmount", "0")
                    val balance = data.optString("balance", "0")
                    if (changeAmount == "0") {
                        Log.sports(TAG, "健康岛 · 本次未获得任何能量")
                    } else {
                        Log.sports("捡泡泡成功 🎈 +$changeAmount 余额：$balance")
                    }
                    pickedNeverlandBubbleRecordIds.addAll(ids)
                }

                for (encryptValue in encryptValues) {
                    Log.sports(TAG, "开始浏览任务，任务 encryptValue: $encryptValue")

                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        if (encryptValue == item.optString("encryptValue")) {
                            val energyNum = item.optInt("energyNum", 0)
                            val viewSec = item.optInt("viewSec", 15)
                            val title = item.optString("title")

                            val receiveResp = JSONObject(
                                AntSportsRpcCall.NeverlandRpcCall.energyReceive(
                                    encryptValue,
                                    energyNum,
                                    "LIGHT_FEEDS_TASK",
                                    "adBubble"
                                )
                            )

                            if (ResChecker.checkRes(TAG + "领取泡泡任务奖励:", receiveResp)) {
                                handledNeverlandBubbleEncryptValues.add(encryptValue)
                                Log.sports("✅ 浏览任务[$title]完成，获得能量+$energyNum")
                            } else {
                                if (!isSportsRpcRetryable(receiveResp)) {
                                    handledNeverlandBubbleEncryptValues.add(encryptValue)
                                }
                                Log.error(TAG, "浏览任务领取失败: $receiveResp")
                            }

                            GlobalThreadPools.sleepCompat((1000 + Math.random() * 1000).toLong())
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "neverlandPickAllBubble err:", t)
            }
        }

        // ---------------------------------------------------------------
        // 6. 自动走路建造（步数限制 + 能量限制）
        // ---------------------------------------------------------------

        /**
         * @brief 检查今日步数是否达到上限
         * @return 剩余可走步数（<=0 表示已达上限）
         */
        private fun checkDailyStepLimit(): Int {
            var stepCount = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            val maxStepLimit = (neverlandGridStepCount.value ?: 0).coerceAtLeast(0)
            val remainSteps = maxStepLimit - stepCount

            Log.sports(
                TAG,
                String.format(
                    "今日步数统计: 已走 %d/%d 步, 剩余 %d 步",
                    stepCount,
                    maxStepLimit,
                    max(0, remainSteps)
                )
            )
            return remainSteps
        }

        /**
         * @brief 记录步数增加
         * @param addedSteps 本次增加的步数
         * @return 更新后的总步数
         */
        private fun recordStepIncrease(addedSteps: Int): Int {
            if (addedSteps <= 0) {
                return Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            }
            var currentSteps = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            val newSteps = currentSteps + addedSteps
            Status.setIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT, newSteps)
            val maxLimit = (neverlandGridStepCount.value ?: 0).coerceAtLeast(0)
            Log.sports(
                TAG,
                String.format(
                    "步数增加: +%d 步, 当前总计 %d/%d 步",
                    addedSteps,
                    newSteps,
                    maxLimit
                )
            )
            return newSteps
        }

        /**
         * @brief 健康岛走路建造任务入口
         */
        private fun neverlandAutoTask() {
            try {
                Log.sports(TAG, "健康岛 · 启动走路建造任务")

                val baseInfo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryBaseinfo())
                if (!ResChecker.checkRes(TAG + " 查询基础信息失败:", baseInfo) ||
                    baseInfo.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "queryBaseinfo 失败, 响应数据: $baseInfo")
                    return
                }

                val baseData = baseInfo.getJSONObject("data")
                val isNewGame = baseData.optBoolean("newGame", false)
                var branchId = baseData.optString("branchId", "MASTER")
                var mapId = baseData.optString("mapId", "")
                val mapName = baseData.optString("mapName", "未知地图")
                if (isNewGame && Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_NEVERLAND_ENERGY_LIMIT)) {
                    Log.sports(TAG, "健康岛 · 今日已判定能量不足以单倍建造，跳过自动建造")
                    return
                }

                Log.sports(
                    TAG,
                    String.format(
                        "当前地图: [%s](%s) | 模式: %s",
                        mapName,
                        mapId,
                        if (isNewGame) "新游戏建造" else "旧版行走"
                    )
                )

                var remainSteps = checkDailyStepLimit()
                if (remainSteps <= 0) {
                    Log.sports(TAG, "今日步数已达上限, 任务结束")
                    return
                }

                var leftEnergy = queryUserEnergy()
                if (leftEnergy < 0) {
                    Log.error(TAG, "健康岛 · 查询用户能量失败，停止本轮自动建造")
                    return
                }
                if (isNewGame) {
                    neverlandPickAllBubble()
                    val refreshedEnergy = queryUserEnergy()
                    if (refreshedEnergy >= 0) {
                        leftEnergy = refreshedEnergy
                    } else {
                        Log.error(TAG, "健康岛 · 领取泡泡后刷新能量失败，停止本轮自动建造")
                        return
                    }
                }
                if (!isNewGame && leftEnergy < 5) {
                    Log.sports(TAG, "剩余能量不足(< 5), 无法执行任务")
                    return
                }

                if (isNewGame) {
                    executeAutoBuild(branchId, mapId, remainSteps, leftEnergy, mapName)
                    neverlandPickAllBubble()
                } else {
                    executeAutoWalk(branchId, mapId, remainSteps, leftEnergy, mapName)
                }

                Log.sports(TAG, "健康岛自动走路建造执行完成 ✓")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "neverlandAutoTask 发生异常$t", t)
            }
        }

        /**
         * @brief 查询用户剩余能量
         */
        private fun queryUserEnergy(): Int {
            return try {
                val energyResp = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryUserEnergy())
                if (!ResChecker.checkRes(TAG + " 查询用户能量失败:", energyResp) ||
                    energyResp.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "queryUserEnergy 失败, 响应数据: $energyResp")
                    -1
                } else {
                    val balance = energyResp.getJSONObject("data").optInt("balance", 0)
                    Log.sports(TAG, "当前剩余能量: $balance")
                    balance
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryUserEnergy err", t)
                -1
            }
        }

        /**
         * @brief 执行旧版行走任务（能量泵走路模式）
         */
        private fun executeAutoWalk(
            branchId: String,
            mapId: String,
            remainSteps: Int,
            leftEnergyInit: Int,
            mapName: String
        ) {
            var leftEnergy = leftEnergyInit
            try {
                Log.sports(TAG, "开始执行旧版行走任务")
                val mapInfoResp = JSONObject(
                    AntSportsRpcCall.NeverlandRpcCall.queryMapInfo(mapId, branchId)
                )

                if (!ResChecker.checkRes(TAG + " queryMapInfo 失败:", mapInfoResp) ||
                    mapInfoResp.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "queryMapInfo 失败，终止走路任务")
                    return
                }

                val mapInfo = mapInfoResp.getJSONObject("data")
                if (!mapInfo.optBoolean("canWalk", false)) {
                    Log.sports(TAG, "当前地图不可走(canWalk=false)，跳过走路任务")
                    return
                }

                val mapStarData = mapInfo.optJSONObject("starData")
                var lastCurrStar = mapStarData?.optInt("curr", 0) ?: 0

                for (i in 0 until remainSteps) {
                    if (leftEnergy < 5) {
                        Log.sports(TAG, "[$mapName] 能量不足(< 5), 停止走路任务")
                        break
                    }

                    val walkResp = JSONObject(
                        AntSportsRpcCall.NeverlandRpcCall.walkGrid(branchId, mapId, false)
                    )

                    if (!ResChecker.checkRes(TAG + " walkGrid 失败:", walkResp) ||
                        walkResp.optJSONObject("data") == null
                    ) {
                        val errorCode = walkResp.optString("errorCode", "")
                        Log.error(
                            TAG,
                            String.format(
                                "walkGrid 失败, 错误码: %s, 响应数据: %s",
                                errorCode,
                                walkResp
                            )
                        )
                        break
                    }

                    val walkData = walkResp.getJSONObject("data")
                    leftEnergy = walkData.optInt("leftCount", leftEnergy)

                    recordStepIncrease(1)
                    val stepThisTime = extractStepIncrease(walkData)

                    val starData = walkData.optJSONObject("starData")
                    val currStar = starData?.optInt("curr", lastCurrStar) ?: lastCurrStar
                    val maxStar = starData?.optInt("count", 0) ?: Int.MAX_VALUE
                    val starIncreased = currStar > lastCurrStar
                    lastCurrStar = currStar

                    var redPocketAdd = 0
                    val userItems = walkData.optJSONArray("userItems")
                    if (userItems != null && userItems.length() > 0) {
                        val item = userItems.optJSONObject(0)
                        if (item != null) {
                            redPocketAdd = item.optInt("modifyCount", item.optInt("count", 0))
                        }
                    }

                    val sb = StringBuilder()
                    sb.append("[").append(mapName).append("] 前进 ")
                        .append(stepThisTime).append(" 步，")

                    if (starIncreased) {
                        sb.append("获得 🌟")
                    } else if (redPocketAdd > 0) {
                        sb.append("获得 🧧 +").append(redPocketAdd)
                    } else {
                        sb.append("啥也没有")
                    }

                    Log.sports(sb.toString())

                    tryReceiveStageReward(branchId, mapId, starData)

                    if (currStar >= maxStar) {
                        Log.sports("[$mapName] 当前地图已完成星星，准备切换地图")
                        chooseAvailableMap()
                        break
                    }
                    GlobalThreadPools.sleepCompat(888)
                }
                Log.sports(TAG, "自动走路任务完成 ✓")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "executeAutoWalk err", t)
            }
        }

        /**
         * @brief 若有未领取的关卡奖励则尝试领取
         */
        private fun tryReceiveStageReward(branchId: String, mapId: String, starData: JSONObject?) {
            if (starData == null) return

            val rewardLevel = starData.optInt("rewardLevel", -1)
            if (rewardLevel <= 0) return

            val recordArr = starData.optJSONArray("stageRewardRecord")
            if (recordArr != null) {
                for (i in 0 until recordArr.length()) {
                    if (recordArr.optInt(i, -1) == rewardLevel) return
                }
            }

            Log.sports(String.format("检测到未领取关卡奖励 🎁 map=%s 等级: %d，尝试领取…", mapId, rewardLevel))

            val rewardStr = try {
                AntSportsRpcCall.NeverlandRpcCall.mapStageReward(branchId, rewardLevel, mapId)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "mapStageReward RPC 调用异常", t)
                return
            }.trim()

            if (rewardStr.isEmpty()) {
                Log.error(TAG, "mapStageReward 返回空字符串")
                return
            }
            if (!rewardStr.startsWith("{")) {
                Log.error(TAG, "mapStageReward 返回非 JSON: $rewardStr")
                return
            }

            val rewardResp = try {
                JSONObject(rewardStr)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "mapStageReward JSON 解析失败", t)
                return
            }

            if (!ResChecker.checkRes(TAG, rewardResp)) {
                val errCode = rewardResp.optString("errorCode", "")
                if ("ASSET_ITEM_NOT_EXISTED" == errCode) {
                    Log.sports("关卡奖励已被领取或不存在（可忽略）")
                } else {
                    Log.error(TAG, "领取关卡奖励失败: $rewardResp")
                }
                return
            }

            val data = rewardResp.optJSONObject("data")
            val receiveResult = data?.optJSONObject("receiveResult")
            if (receiveResult == null) {
                Log.sports(TAG, "关卡奖励领取成功 🎉（无奖励详情）")
                return
            }

            val prizes = receiveResult.optJSONArray("prizes")
            val balance = receiveResult.optString("balance", "")

            if (prizes != null && prizes.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until prizes.length()) {
                    val p = prizes.optJSONObject(i) ?: continue
                    sb.append(p.optString("title", "未知奖励"))
                        .append(" x")
                        .append(p.optString("modifyCount", "1"))
                    if (i != prizes.length() - 1) sb.append("，")
                }
                Log.sports(
                    String.format(
                        "Lv.%s 奖励领取成功 🎉 %s | 当前余额: %s",
                        rewardLevel,
                        sb.toString(),
                        balance
                    )
                )
            } else {
                Log.sports("关卡奖励领取成功 🎉（无可展示奖励）")
            }
        }

        /**
         * @brief 查询地图列表，按服务端顺序选择可处理的新游戏岛屿
         */
        private fun chooseAvailableMap(skipMapId: String? = null): JSONObject? {
            return try {
                val mapResp = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryMapList())
                if (!ResChecker.checkRes(TAG + " 查询地图失败:", mapResp)) {
                    Log.error(
                        TAG,
                        "queryMapList 失败[code=${extractSportsRpcErrorCode(mapResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(mapResp)}] raw=$mapResp"
                    )
                    return null
                }

                val data = mapResp.optJSONObject("data")
                val mapList = data?.optJSONArray("mapList")
                if (mapList == null || mapList.length() == 0) {
                    Log.error(TAG, "地图列表为空")
                    return null
                }

                var finishNotRewardMap: JSONObject? = null
                var doingMap: JSONObject? = null
                var firstUnfinishedMap: JSONObject? = null
                for (i in 0 until mapList.length()) {
                    val map = mapList.optJSONObject(i) ?: continue
                    if (skipMapId != null && map.optString("mapId", "") == skipMapId) {
                        continue
                    }
                    val status = map.optString("status")
                    if ("FINISH_NOT_REWARD" == status && finishNotRewardMap == null) {
                        finishNotRewardMap = map
                    }
                    if ("DOING" == status && doingMap == null) {
                        doingMap = map
                    }
                    val currentPercent = map.optInt("currentPercent", 0)
                    if (firstUnfinishedMap == null &&
                        map.optBoolean("newIsLandFlg", true) &&
                        status != "LOCKED" &&
                        status != "FINISH" &&
                        currentPercent < 100
                    ) {
                        firstUnfinishedMap = map
                    }
                }

                val selected = doingMap ?: firstUnfinishedMap ?: finishNotRewardMap
                if (selected == null) {
                    Log.sports(TAG, "健康岛没有可切换的未完成岛屿")
                    return null
                }
                Log.sports(
                    TAG,
                    "健康岛选择岛屿[${selected.optString("mapName", selected.optString("mapId"))}][status=${selected.optString("status", "")}]"
                )
                chooseMap(selected)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "chooseAvailableMap err", t)
                null
            }
        }

        /**
         * @brief 切换当前地图
         */
        private fun chooseMap(map: JSONObject): JSONObject? {
            return try {
                val mapId = map.optString("mapId")
                val branchId = map.optString("branchId", "MASTER").ifBlank { "MASTER" }
                val resp = JSONObject(
                    AntSportsRpcCall.NeverlandRpcCall.chooseMap(branchId, mapId)
                )
                if (ResChecker.checkRes(TAG, resp)) {
                    Log.sports(TAG, "切换地图成功: $mapId")
                    map
                } else {
                    Log.error(
                        TAG,
                        "切换地图失败[mapId=$mapId][branchId=$branchId][code=${extractSportsRpcErrorCode(resp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(resp)}] raw=$resp"
                    )
                    null
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "chooseMap err", t)
                null
            }
        }

        /**
         * @brief 从 walkData 中提取步数增量
         */
        private fun extractStepIncrease(walkData: JSONObject): Int {
            return try {
                val mapAwards = walkData.optJSONArray("mapAwards")
                if (mapAwards != null && mapAwards.length() > 0) {
                    mapAwards.getJSONObject(0).optInt("step", 0)
                } else {
                    0
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
                0
            }
        }

        /**
         * @brief 执行自动建造任务（新游戏模式）
         */
        private fun executeAutoBuild(
            branchIdInit: String,
            mapIdInit: String,
            remainStepsInit: Int,
            leftEnergyInit: Int,
            mapName: String
        ) {
            var branchId = branchIdInit.ifBlank { "MASTER" }
            var mapId = mapIdInit
            var remainSteps = remainStepsInit
            var leftEnergy = leftEnergyInit
            var currentMapName = mapName
            val blockedMultis = mutableSetOf<Int>()
            try {
                Log.sports(String.format("开始执行建造任务, 地图: %s", mapId))

                while (remainSteps > 0) {
                    neverlandPickAllBubble()
                    val refreshedEnergy = queryUserEnergy()
                    if (refreshedEnergy >= 0) {
                        leftEnergy = refreshedEnergy
                    } else if (leftEnergy <= 0) {
                        Log.error(TAG, "健康岛[$currentMapName]刷新能量失败，停止本轮建造")
                        return
                    }

                    val mapInfo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryMapInfoNew(mapId, branchId))
                    if (!ResChecker.checkRes(TAG + " 查询建造地图失败", mapInfo)) {
                        Log.error(
                            TAG,
                            "查询建造地图失败[mapId=$mapId][branchId=$branchId][code=${extractSportsRpcErrorCode(mapInfo).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(mapInfo)}] raw=$mapInfo"
                        )
                        return
                    }
                    val data = mapInfo.optJSONObject("data")
                    if (data == null) {
                        Log.error(TAG, "地图Data 为空，无法解析 raw=$mapInfo")
                        return
                    }

                    branchId = data.optString("branchId", branchId).ifBlank { branchId }
                    mapId = data.optString("mapId", mapId).ifBlank { mapId }
                    currentMapName = data.optString("mapName", currentMapName).ifBlank { currentMapName }
                    val mapEnergyFinal = data.optInt("mapEnergyFinal", 0)
                    val mapEnergyProcess = data.optInt("mapEnergyProcess", 0)
                    val mapStatus = data.optString("mapStatus", "")
                    if (isNeverlandMapFinished(mapStatus, mapEnergyProcess, mapEnergyFinal)) {
                        Log.sports(TAG, "健康岛[$currentMapName]已完成，准备领奖与切换岛屿")
                        neverlandPickAllBubble()
                        if (!::neverlandAutoReward.isInitialized || neverlandAutoReward.value == true) {
                            tryChooseMapReward(branchId, mapId)
                        } else {
                            Log.sports(TAG, "健康岛自动领奖已关闭，跳过[$currentMapName]")
                        }
                        val nextMap = chooseAvailableMap(mapId) ?: return
                        if (!nextMap.optBoolean("newIsLandFlg", true)) {
                            Log.sports(TAG, "已切换至旧版走路地图[${nextMap.optString("mapId")}], 本轮新游戏建造结束")
                            return
                        }
                        branchId = nextMap.optString("branchId", "MASTER").ifBlank { "MASTER" }
                        mapId = nextMap.optString("mapId", "")
                        currentMapName = nextMap.optString("mapName", mapId)
                        blockedMultis.clear()
                        if (mapId.isBlank()) {
                            Log.error(TAG, "健康岛切换后 mapId 为空 raw=$nextMap")
                            return
                        }
                        continue
                    }

                    val mapConfig = data.optJSONObject("mapConfig")
                    val oneBuildEnergy = mapConfig?.optInt("oneBuildEnergy", 5)?.takeIf { it > 0 } ?: 5
                    val buildMultis = extractBuildMultis(mapConfig)
                    val multiNum = buildMultis.firstOrNull { multi ->
                        multi !in blockedMultis && multi <= remainSteps && leftEnergy >= oneBuildEnergy * multi
                    }

                    if (multiNum == null) {
                        if (leftEnergy < oneBuildEnergy) {
                            Log.sports(
                                TAG,
                                "健康岛[$currentMapName]单倍能量不足[balance=$leftEnergy, oneBuildEnergy=$oneBuildEnergy]，今日停止建造"
                            )
                            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_NEVERLAND_ENERGY_LIMIT)
                        } else {
                            Log.sports(TAG, "健康岛[$currentMapName]今日建造次数不足以执行最小倍数[remainSteps=$remainSteps]")
                        }
                        return
                    }

                    val buildResp = JSONObject(
                        AntSportsRpcCall.NeverlandRpcCall.build(mapId, multiNum, branchId)
                    )
                    if (!ResChecker.checkRes(TAG + " build 失败:", buildResp)) {
                        val errorCode = extractSportsRpcErrorCode(buildResp)
                        val errorMsg = extractSportsRpcErrorMessage(buildResp)
                        if (isNeverlandEnergyLimit(errorCode, errorMsg)) {
                            if (multiNum > 1) {
                                blockedMultis.add(multiNum)
                                leftEnergy = queryUserEnergy()
                                if (leftEnergy < 0) {
                                    Log.error(TAG, "健康岛[$currentMapName]刷新能量失败，停止降级重试")
                                    return
                                }
                                Log.sports(TAG, "健康岛[$currentMapName]x$multiNum 能量不足，刷新能量后降级重试[balance=$leftEnergy]")
                                continue
                            }
                            Log.sports(TAG, "健康岛[$currentMapName]单倍建造受限[code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]")
                            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_NEVERLAND_ENERGY_LIMIT)
                            return
                        }
                        if (isNeverlandBuildTimesLimit(errorCode, errorMsg)) {
                            Log.sports(
                                TAG,
                                "健康岛[$currentMapName]建造受限[code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]，停止当前岛屿建造"
                            )
                            return
                        }
                        Log.error(
                            TAG,
                            "build 失败[mapId=$mapId][branchId=$branchId][multiNum=$multiNum][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$buildResp"
                        )
                        return
                    }

                    val buildData = buildResp.optJSONObject("data")
                    if (buildData == null || buildData.length() == 0) {
                        Log.sports(TAG, "build响应数据为空，刷新地图状态后继续判断")
                        GlobalThreadPools.sleepCompat(500)
                        continue
                    }

                    val stepIncrease = calculateBuildSteps(buildData, multiNum)
                    val totalSteps = recordStepIncrease(stepIncrease)
                    remainSteps -= stepIncrease
                    leftEnergy = (leftEnergy - oneBuildEnergy * stepIncrease).coerceAtLeast(0)

                    val awardInfo = extractAwardInfo(buildData)
                    Log.sports(
                        String.format(
                            "建造进度 🏗️ [%s] 倍数: x%d | 能量: %d | 本次: +%d | 今日: %d/%d%s",
                            currentMapName,
                            multiNum,
                            leftEnergy,
                            stepIncrease,
                            totalSteps,
                            neverlandGridStepCount.value,
                            awardInfo
                        )
                    )
                    if (isBuildDataMapFinished(buildData)) {
                        neverlandPickAllBubble()
                    }
                    GlobalThreadPools.sleepCompat(1000)
                }
                handleFinishedMapAfterBuildLimit(branchId, mapId, currentMapName)
                Log.sports("自动建造任务完成 ✓")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "executeAutoBuild err", t)
            }
        }

        private fun handleFinishedMapAfterBuildLimit(branchId: String, mapId: String, mapName: String) {
            try {
                val mapInfo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryMapInfoNew(mapId, branchId))
                if (!ResChecker.checkRes(TAG + " 查询建造地图失败", mapInfo)) {
                    Log.error(
                        TAG,
                        "健康岛结束前查询地图失败[mapId=$mapId][branchId=$branchId][code=${extractSportsRpcErrorCode(mapInfo).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(mapInfo)}] raw=$mapInfo"
                    )
                    return
                }
                val data = mapInfo.optJSONObject("data") ?: return
                val latestMapId = data.optString("mapId", mapId).ifBlank { mapId }
                val latestBranchId = data.optString("branchId", branchId).ifBlank { branchId }
                val latestMapName = data.optString("mapName", mapName).ifBlank { mapName }
                if (!isNeverlandMapFinished(
                        data.optString("mapStatus", ""),
                        data.optInt("mapEnergyProcess", 0),
                        data.optInt("mapEnergyFinal", 0)
                    )
                ) {
                    return
                }
                Log.sports(TAG, "健康岛[$latestMapName]建造已完成，结束前尝试领奖与切换岛屿")
                neverlandPickAllBubble()
                if (!::neverlandAutoReward.isInitialized || neverlandAutoReward.value == true) {
                    tryChooseMapReward(latestBranchId, latestMapId)
                } else {
                    Log.sports(TAG, "健康岛自动领奖已关闭，跳过[$latestMapName]")
                }
                chooseAvailableMap(latestMapId)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "handleFinishedMapAfterBuildLimit err", t)
            }
        }

        private fun isNeverlandMapFinished(mapStatus: String, process: Int, final: Int): Boolean {
            return mapStatus == "FINISH_NOT_REWARD" ||
                mapStatus == "FINISH" ||
                (final > 0 && process >= final)
        }

        private fun isBuildDataMapFinished(buildData: JSONObject): Boolean {
            val end = buildData.optJSONObject("endStageInfo") ?: return false
            val final = end.optInt("buildingEnergyFinal", 0)
            val process = end.optInt("buildingEnergyProcess", 0)
            val rewardCount = buildData.optJSONArray("rewards")?.length() ?: 0
            return final > 0 && process >= final && rewardCount > 0
        }

        private fun extractBuildMultis(mapConfig: JSONObject?): List<Int> {
            val arr = mapConfig?.optJSONArray("buildMulti")
            val result = mutableListOf<Int>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val multi = arr.optInt(i, 0)
                    if (multi > 0) result.add(multi)
                }
            }
            return result.ifEmpty { listOf(1) }.distinct().sortedDescending()
        }

        private fun tryChooseMapReward(branchId: String, mapId: String): Boolean {
            val detailResp = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryMapDetail(mapId))
            if (!ResChecker.checkRes(TAG + " queryMapDetail 失败:", detailResp)) {
                Log.error(
                    TAG,
                    "健康岛查询领奖详情失败[mapId=$mapId][code=${extractSportsRpcErrorCode(detailResp).ifEmpty { "UNKNOWN" }}][msg=${extractSportsRpcErrorMessage(detailResp)}] raw=$detailResp"
                )
                return false
            }
            val baseMapInfo = detailResp.optJSONObject("data")?.optJSONObject("baseMapInfo")
            val rewards = collectNeverlandRewardCandidates(baseMapInfo)
            if (rewards.isEmpty()) {
                Log.sports(TAG, "健康岛[$mapId]没有可选奖励")
                return false
            }
            val orderedRewards = orderNeverlandRewards(rewards)
            for (reward in orderedRewards) {
                val chooseResp = JSONObject(
                    AntSportsRpcCall.NeverlandRpcCall.chooseReward(branchId, mapId, reward.rewardId)
                )
                if (ResChecker.checkRes(TAG, chooseResp)) {
                    Log.sports(TAG, "健康岛领奖成功[mapId=$mapId][reward=${reward.name}][rewardId=${reward.rewardId}]")
                    return true
                }
                val errorCode = extractSportsRpcErrorCode(chooseResp)
                val errorMsg = extractSportsRpcErrorMessage(chooseResp)
                if (isNeverlandRewardNonRetryable(errorCode, errorMsg)) {
                    Log.sports(
                        TAG,
                        "健康岛奖励不可领取，尝试下一个[reward=${reward.name}][rewardId=${reward.rewardId}][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                    )
                    continue
                }
                if (isNeverlandDuplicateReward(errorCode, errorMsg)) {
                    Log.sports(
                        TAG,
                        "健康岛奖励已处理[reward=${reward.name}][rewardId=${reward.rewardId}][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg]"
                    )
                    return true
                }
                Log.error(
                    TAG,
                    "健康岛领奖失败[mapId=$mapId][reward=${reward.name}][rewardId=${reward.rewardId}][code=${errorCode.ifEmpty { "UNKNOWN" }}][msg=$errorMsg] raw=$chooseResp"
                )
                return false
            }
            Log.error(TAG, "健康岛[$mapId]所有奖励均领取失败或不可领取")
            return false
        }

        private fun collectNeverlandRewardCandidates(baseMapInfo: JSONObject?): List<NeverlandRewardCandidate> {
            if (baseMapInfo == null) return emptyList()
            val rewardArray = baseMapInfo.optJSONArray("rewards") ?: baseMapInfo.optJSONArray("reward") ?: return emptyList()
            val candidates = mutableListOf<NeverlandRewardCandidate>()
            for (i in 0 until rewardArray.length()) {
                val reward = rewardArray.optJSONObject(i) ?: continue
                val rewardId = reward.optString("itemId", reward.optString("rewardId", ""))
                if (rewardId.isBlank()) continue
                candidates.add(
                    NeverlandRewardCandidate(
                        rewardId = rewardId,
                        name = reward.optString("name", rewardId),
                        status = reward.opt("status")?.toString().orEmpty(),
                        prizeStatus = reward.optString("prizeStatus", "")
                    )
                )
            }
            return candidates
        }

        private fun orderNeverlandRewards(rewards: List<NeverlandRewardCandidate>): List<NeverlandRewardCandidate> {
            if (!::neverlandPreferMedal.isInitialized || neverlandPreferMedal.value == true) {
                val medal = rewards.firstOrNull { it.name.contains("奖牌") }
                if (medal != null) {
                    return listOf(medal) + rewards.filterNot { it.rewardId == medal.rewardId }
                }
            }
            return rewards
        }

        private fun isNeverlandEnergyLimit(errorCode: String, errorMsg: String): Boolean {
            val text = "$errorCode $errorMsg"
            return text.contains("能量") ||
                text.contains("体力") ||
                text.contains("體力") ||
                text.contains("ENERGY", ignoreCase = true) ||
                text.contains("NOT_ENOUGH", ignoreCase = true)
        }

        private fun isNeverlandBuildTimesLimit(errorCode: String, errorMsg: String): Boolean {
            val text = "$errorCode $errorMsg"
            return text.contains("次数") ||
                text.contains("上限") ||
                text.contains("今日")
        }

        private fun isNeverlandRewardNonRetryable(errorCode: String, errorMsg: String): Boolean {
            val text = "$errorCode $errorMsg"
            return errorCode == "PROMO_PRIZE_NOT_RETRY_ERROR" ||
                text.contains("奖品已抢完") ||
                text.contains("不可重试") ||
                text.contains("今日已兑完")
        }

        private fun isNeverlandDuplicateReward(errorCode: String, errorMsg: String): Boolean {
            val text = "$errorCode $errorMsg"
            return text.contains("已领取") || text.contains("重复领取")
        }

        /**
         * @brief 计算建造实际产生的步数
         */
        private fun calculateBuildSteps(buildData: JSONObject?, defaultMulti: Int): Int {
            return try {
                val buildResults = buildData?.optJSONArray("buildResults")
                if (buildResults != null && buildResults.length() > 0) {
                    buildResults.length()
                } else {
                    defaultMulti
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
                defaultMulti
            }
        }

        /**
         * @brief 从建造数据中提取奖励信息
         */
        private fun extractAwardInfo(buildData: JSONObject?): String {
            return try {
                val awards = buildData?.optJSONArray("awards") ?: buildData?.optJSONArray("rewards")
                if (awards != null && awards.length() > 0) {
                    String.format(" | 获得奖励: %d 项", awards.length())
                } else {
                    ""
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
                ""
            }
        }
    }

    // ---------------------------------------------------------------------
    // 配置用枚举/常量
    // ---------------------------------------------------------------------

    /**
     * @brief 蚂蚁运动路线主题常量与映射表
     */
    interface WalkPathTheme {
        companion object {
            const val DA_MEI_ZHONG_GUO = 0  ///< 大美中国 (默认)
            const val GONG_YI_YI_XIAO_BU = 1  ///< 公益一小步
            const val DENG_DING_ZHI_MA_SHAN = 2  ///< 登顶芝麻山
            const val WEI_C_DA_TIAO_ZHAN = 3  ///< 维C大挑战
            const val LONG_NIAN_QI_FU = 4  ///< 龙年祈福
            const val SHOU_HU_TI_YU_MENG = 5  ///< 守护体育梦

            /** @brief 界面显示的名称列表 */
            val nickNames = arrayOf(
                "大美中国",
                "公益一小步",
                "登顶芝麻山",
                "维C大挑战",
                "龙年祈福",
                "守护体育梦"
            )

            /**
             * @brief 对应目标应用接口的 ThemeID 映射表
             * @note 数组顺序必须与上方常量定义保持严格一致
             */
            val themeIds = arrayOf(
                "M202308082226",  ///< [0] 大美中国
                "M202401042147",  ///< [1] 公益一小步
                "V202405271625",  ///< [2] 登顶芝麻山
                "202404221422",   ///< [3] 维C大挑战
                "WF202312050200", ///< [4] 龙年祈福
                "V202409061650"   ///< [5] 守护体育梦
            )
        }
    }

    /**
     * @brief 慈善捐能量模式
     */
    interface DonateCharityCoinType {
        companion object {
            const val ONE = 0
            // 保留原 ALL 选项的文案，方便以后扩充
            val nickNames = arrayOf("捐赠一个项目", "捐赠所有项目")
        }
    }

    /**
     * @brief 抢好友模式
     */
    interface BattleForFriendType {
        companion object {
            const val ROB = 0
            const val DONT_ROB = 1
            val nickNames = arrayOf("选中抢", "选中不抢")
        }
    }
}


package fansirsqi.xposed.sesame.task.antSports

import android.annotation.SuppressLint
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.entity.AlipayUser
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.util.*
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
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
    }

    /** @brief 临时步数缓存（-1 表示未初始化） */
    private var tmpStepCount: Int = -1

    // 配置字段
    private lateinit var walk: BooleanModelField
    private lateinit var walkPathTheme: ChoiceModelField
    private var walkPathThemeId: String? = null
    private lateinit var walkCustomPath: BooleanModelField
    private lateinit var walkCustomPathId: StringModelField
    private lateinit var openTreasureBox: BooleanModelField
    private lateinit var receiveCoinAssetField: BooleanModelField
    private lateinit var donateCharityCoin: BooleanModelField
    private lateinit var donateCharityCoinType: ChoiceModelField
    private lateinit var donateCharityCoinAmount: IntegerModelField
    private lateinit var minExchangeCount: IntegerModelField
    private lateinit var latestExchangeTime: IntegerModelField
    private lateinit var syncStepCount: IntegerModelField
    private lateinit var tiyubiz: BooleanModelField
    private lateinit var battleForFriends: BooleanModelField
    private lateinit var battleForFriendType: ChoiceModelField
    private lateinit var originBossIdList: SelectModelField
    private lateinit var sportsTasksField: BooleanModelField
    private lateinit var sportsEnergyBubble: BooleanModelField

    // 训练好友相关配置
    private lateinit var trainFriend: BooleanModelField
    private lateinit var zeroCoinLimit: IntegerModelField

    /** @brief 记录训练好友连续获得 0 金币的次数 */
    private var zeroTrainCoinCount: Int = 0

    // 健康岛任务
    private lateinit var neverlandTask: BooleanModelField
    private lateinit var neverlandGrid: BooleanModelField
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
        modelFields.addField(BooleanModelField("walk", "行走路线 | 开启", false).also { walk = it })
        modelFields.addField(
            ChoiceModelField(
                "walkPathTheme",
                "行走路线 | 主题",
                WalkPathTheme.DA_MEI_ZHONG_GUO,
                WalkPathTheme.nickNames
            ).also { walkPathTheme = it }
        )
        modelFields.addField(
            BooleanModelField("walkCustomPath", "行走路线 | 开启自定义路线", false).also { walkCustomPath = it }
        )
        modelFields.addField(
            StringModelField(
                "walkCustomPathId",
                "行走路线 | 自定义路线代码(debug)",
                "p0002023122214520001"
            ).also { walkCustomPathId = it }
        )

        // 旧版路线相关
        modelFields.addField(
            BooleanModelField("openTreasureBox", "开启宝箱", false).also { openTreasureBox = it }
        )

        // 运动任务 & 能量球
        modelFields.addField(
            BooleanModelField("sportsTasks", "开启运动任务", false).also { sportsTasksField = it }
        )
        modelFields.addField(
            BooleanModelField(
                "sportsEnergyBubble",
                "运动球任务(开启后有概率出现滑块验证)",
                false
            ).also { sportsEnergyBubble = it }
        )

        // 首页金币 & 捐步
        modelFields.addField(
            BooleanModelField("receiveCoinAsset", "收能量🎈", false).also { receiveCoinAssetField = it }
        )
        modelFields.addField(
            BooleanModelField("donateCharityCoin", "捐能量🎈 | 开启", false).also { donateCharityCoin = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "donateCharityCoinType",
                "捐能量🎈 | 方式",
                DonateCharityCoinType.ONE,
                DonateCharityCoinType.nickNames
            ).also { donateCharityCoinType = it }
        )
        modelFields.addField(
            IntegerModelField("donateCharityCoinAmount", "捐能量🎈 | 数量(每次)", 100)
                .also { donateCharityCoinAmount = it }
        )

        // 健康岛任务
        modelFields.addField(
            BooleanModelField("neverlandTask", "健康岛 | 任务", false).also { neverlandTask = it }
        )
        modelFields.addField(
            BooleanModelField("neverlandGrid", "健康岛 | 自动走路建造", false).also { neverlandGrid = it }
        )
        modelFields.addField(
            IntegerModelField("neverlandGridStepCount", "健康岛 | 今日走路最大次数", 20)
                .also { neverlandGridStepCount = it }
        )

        // 抢好友相关
        modelFields.addField(
            BooleanModelField("battleForFriends", "抢好友 | 开启", false).also { battleForFriends = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "battleForFriendType",
                "抢好友 | 动作",
                BattleForFriendType.ROB,
                BattleForFriendType.nickNames
            ).also { battleForFriendType = it }
        )
        modelFields.addField(
            SelectModelField(
                "originBossIdList",
                "抢好友 | 好友列表",
                LinkedHashSet(),
                AlipayUser::getList
            ).also { originBossIdList = it }
        )

        // 训练好友相关
        modelFields.addField(
            BooleanModelField("trainFriend", "训练好友 | 开启", false).also { trainFriend = it }
        )
        modelFields.addField(
            IntegerModelField("zeroCoinLimit", "训练好友 | 0金币上限次数当天关闭", 5)
                .also { zeroCoinLimit = it }
        )

        // 文体中心 & 捐步 & 步数同步
        modelFields.addField(BooleanModelField("tiyubiz", "文体中心", false).also { tiyubiz = it })
        modelFields.addField(
            IntegerModelField("minExchangeCount", "最小捐步步数", 0).also { minExchangeCount = it }
        )
        modelFields.addField(
            IntegerModelField("latestExchangeTime", "最晚捐步时间(24小时制)", 22)
                .also { latestExchangeTime = it }
        )
        modelFields.addField(
            IntegerModelField("syncStepCount", "自定义同步步数", 22000).also { syncStepCount = it }
        )

        // 本地字段：能量兑换双击卡
        val coinExchangeDoubleCard = BooleanModelField(
            "coinExchangeDoubleCard",
            "能量🎈兑换限时能量双击卡",
            false
        )
        modelFields.addField(coinExchangeDoubleCard)

        return modelFields
    }

    /**
     * @brief Xposed 启动时 hook 步数读取逻辑，实现自定义步数同步
     */
    override fun boot(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.alibaba.health.pedometer.core.datasource.PedometerAgent",
                classLoader,
                "readDailyStep",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val originStep = param.result as Int
                        val step = tmpStepCount()
                        // 早于 8 点或步数小于自定义步数时进行 hook
                        if (TaskCommon.IS_AFTER_8AM && originStep < step) {
                            param.result = step
                        }
                    }
                }
            )
            Log.record(TAG, "hook readDailyStep successfully")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "hook readDailyStep err:", t)
        }
    }

    /**
     * @brief 任务主入口
     */
    override fun runJava() {
        Log.record(TAG, "执行开始-" + name)

        try {
            val loader = ApplicationHook.classLoader
            if (loader == null) {
                Log.error(TAG, "ClassLoader is null, 跳过运动任务")
                return
            }

            // 健康岛整体任务（任务大厅 + 泡泡 + 走路建造）
            if (neverlandTask.value || neverlandGrid.value) {
                Log.record(TAG, "开始执行健康岛")
                NeverlandTaskHandler().runNeverland()
                Log.record(TAG, "健康岛结束")
            }

            // 步数同步
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE) &&
                TimeUtil.isNowAfterOrCompareTimeStr("0600")) {
                syncStepTask()
            }

            // 运动任务
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE) &&
                sportsTasksField.value) {
                sportsTasks()
            }

            // 运动球任务
            if (sportsEnergyBubble.value) {
                sportsEnergyBubbleTask()
            }

            // 新版行走路线
            if (walk.value) {
                getWalkPathThemeIdOnConfig()
                walk()
            }

            // 旧版路线：只开宝箱
            if (openTreasureBox.value && !walk.value) {
                queryMyHomePage(loader)
            }

            // 捐能量
            if (donateCharityCoin.value && Status.canDonateCharityCoin()) {
                queryProjectList(loader)
            }

            // 捐步
            val currentUid = UserMap.currentUid
            if (minExchangeCount.value > 0 &&
                currentUid != null &&
                Status.canExchangeToday(currentUid)) {
                queryWalkStep(loader)
            }

            // 文体中心
            if (tiyubiz.value) {
                userTaskGroupQuery("SPORTS_DAILY_SIGN_GROUP")
                userTaskGroupQuery("SPORTS_DAILY_GROUP")
                userTaskRightsReceive()
                pathFeatureQuery()
                participate()
            }

            // 抢好友大战
            if (battleForFriends.value) {
                queryClubHome()
                queryTrainItem()
                buyMember()
            }

            // 首页金币
            if (receiveCoinAssetField.value) {
                receiveCoinAsset()
            }

        } catch (t: Throwable) {
            Log.record(TAG, "runJava error:")
            Log.printStackTrace(TAG, t)
        } finally {
            Log.record(TAG, "执行结束-" + name)
        }
    }

    /**
     * 步数同步任务
     */
    private fun syncStepTask() {
        addChildTask(
            ChildModelTask(
                "syncStep",
                Runnable {
                    val step = tmpStepCount()
                    try {
                        val loader = ApplicationHook.classLoader
                        if (loader == null) {
                            Log.error(TAG, "ClassLoader is null, 跳过同步步数")
                            return@Runnable
                        }

                        val rpcManager = XposedHelpers.callStaticMethod(
                            loader.loadClass("com.alibaba.health.pedometer.intergation.rpc.RpcManager"),
                            "a"
                        )

                        val success = XposedHelpers.callMethod(
                            rpcManager,
                            "a",
                            step,
                            java.lang.Boolean.FALSE,
                            "system"
                        ) as Boolean

                        if (success) {
                            Log.other("同步步数🏃🏻‍♂️[$step 步]")
                            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
                        } else {
                            Log.error(TAG, "同步运动步数失败:$step")
                        }
                    } catch (t: Throwable) {
                        Log.printStackTrace(TAG, t)
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
        tmpStepCount = syncStepCount.value
        if (tmpStepCount > 0) {
            tmpStepCount = RandomUtil.nextInt(tmpStepCount, tmpStepCount + 2000)
            if (tmpStepCount > 100_000) {
                tmpStepCount = 100_000
            }
        }
        return tmpStepCount
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
            val jo = JSONObject(AntSportsRpcCall.queryCoinTaskPanel())

            if (ResChecker.checkRes(TAG, jo)) {
                val data = jo.getJSONObject("data")
                val taskList = data.getJSONArray("taskList")

                var totalTasks = 0
                var completedTasks = 0
                var availableTasks = 0

                for (i in 0 until taskList.length()) {
                    val taskDetail = taskList.getJSONObject(i)
                    val taskId = taskDetail.getString("taskId")
                    val taskName = taskDetail.getString("taskName")
                    val taskStatus = taskDetail.getString("taskStatus")
                    val taskType = taskDetail.optString("taskType", "")

                    // 排除自动结算任务
                    if (taskType == "SETTLEMENT") continue

                    // 黑名单过滤
                    if (TaskBlacklist.isTaskInBlacklist(taskId) || TaskBlacklist.isTaskInBlacklist(taskName)) {
                        continue
                    }

                    totalTasks++

                    when (taskStatus) {
                        "HAS_RECEIVED" -> {
                            completedTasks++
                        }
                        "WAIT_RECEIVE" -> {
                            if (receiveTaskReward(taskDetail, taskName)) {
                                completedTasks++
                            }
                        }
                        "WAIT_COMPLETE" -> {
                            availableTasks++
                            if (completeTask(taskDetail, taskName)) {
                                completedTasks++
                            }
                        }
                        else -> {
                            Log.error(TAG, "做任务得能量🎈[未知状态：$taskName，状态：$taskStatus]")
                        }
                    }
                }

                Log.record(TAG, "运动任务完成情况：$completedTasks/$totalTasks，可执行任务：$availableTasks")

                // 所有任务完成后标记
                if (totalTasks > 0 && completedTasks >= totalTasks && availableTasks == 0) {
                    val today = TimeUtil.getDateStr2()
                    DataStore.put(SPORTS_TASKS_COMPLETED_DATE, today)
                    Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE)
                    Log.record(TAG, "✅ 所有运动任务已完成，今日不再执行")
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
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

            if (ResChecker.checkRes(TAG, result)) {
                Log.other("做任务得能量🎈[$taskName] +$prizeAmount 能量")
                true
            } else {
                val errorMsg = resultData.optString("errorMsg", "未知错误")
                val errorCode = resultData.optString("errorCode", "")
                Log.error(TAG, "做任务得能量🎈[领取失败：$taskName，错误：$errorCode - $errorMsg]")
                if (!resultData.optBoolean("retryable", true) || errorCode == "CAMP_TRIGGER_ERROR") {
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.error(TAG, "做任务得能量🎈[领取异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /**
     * @brief 执行任务（可能包含多次完成）
     */
    private fun completeTask(taskDetail: JSONObject, taskName: String): Boolean {
        return try {
            val taskId = taskDetail.getString("taskId")
            val prizeAmount = taskDetail.getString("prizeAmount")
            val currentNum = taskDetail.getInt("currentNum")
            val limitConfigNum = taskDetail.getInt("limitConfigNum")
            val remainingNum = limitConfigNum - currentNum
            val needSignUp = taskDetail.optBoolean("needSignUp", false)

            if (remainingNum <= 0) {
                return true
            }

            // 需要先签到
            if (needSignUp) {
                if (!signUpForTask(taskId, taskName)) {
                    return false
                }
                GlobalThreadPools.sleepCompat(2000)
            }

            for (i in 0 until remainingNum) {
                val result = JSONObject(AntSportsRpcCall.completeExerciseTasks(taskId))
                if (ResChecker.checkRes(TAG, result)) {
                    Log.record(
                        TAG,
                        "做任务得能量🎈[完成任务：$taskName，得$prizeAmount💰]#(${i + 1}/$remainingNum)"
                    )

                    if (i == remainingNum - 1) {
                        GlobalThreadPools.sleepCompat(2000)
                        receiveCoinAsset()
                    }
                } else {
                    val errorMsg = result.optString("errorMsg", "未知错误")
                    Log.error(
                        TAG,
                        "做任务得能量🎈[任务失败：$taskName，错误：$errorMsg]#(${i + 1}/$remainingNum)"
                    )
                    val errorCode = result.optString("errorCode", "")
                    if (errorCode.isNotEmpty()) {
                        TaskBlacklist.autoAddToBlacklist(taskId, taskName, errorCode)
                    }
                    break
                }

                if (remainingNum > 1 && i < remainingNum - 1) {
                    GlobalThreadPools.sleepCompat(10000)
                }
            }
            true
        } catch (e: Exception) {
            Log.error(TAG, "做任务得能量🎈[执行异常：$taskName，错误：${e.message}]")
            false
        }
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
                Log.other("做任务得能量🎈[签到成功：$taskName，订单：$taskOrderId]")
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
     * - 使用 {@link AntSportsRpcCall#queryEnergyBubbleModule} 获取 recBubbleList
     * - 对有 channel 的记录执行任务
     * - 成功后统一调用 pickBubbleTaskEnergy 领取奖励
     */
    private fun sportsEnergyBubbleTask() {
        try {
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

            for (i in 0 until recBubbleList.length()) {
                val bubble = recBubbleList.optJSONObject(i) ?: continue

                val id = bubble.optString("id")
                val taskId = bubble.optString("channel", "")
                if (taskId.isEmpty()) continue
                if (TaskBlacklist.isTaskInBlacklist(id)) continue

                val sourceName = bubble.optString("simpleSourceName", "")
                val coinAmount = bubble.optInt("coinAmount", 0)
                Log.record(TAG, "运动首页任务[开始完成：$sourceName，taskId=$taskId，coin=$coinAmount]")

                val completeRes = JSONObject(AntSportsRpcCall.completeExerciseTasks(taskId))
                if (ResChecker.checkRes(TAG, completeRes)) {
                    hasCompletedTask = true
                    val dataObj = completeRes.optJSONObject("data")
                    val assetCoinAmount = dataObj?.optInt("assetCoinAmount", 0) ?: 0
                    Log.other("运动球任务✅[$sourceName]#奖励$assetCoinAmount💰")
                } else {
                    val errorCode = completeRes.optString("errorCode", "")
                    val errorMsg = completeRes.optString("errorMsg", "")
                    Log.error(TAG, "运动球任务❌[$sourceName]#$completeRes 任务：$bubble")

                    if (id.isNotEmpty()) {
                        TaskBlacklist.addToBlacklist(id, sourceName)
                    }
                }

                val sleepMs = RandomUtil.nextInt(10000, 30000)
                GlobalThreadPools.sleepCompat(sleepMs.toLong())
            }

            if (hasCompletedTask) {
                val result = AntSportsRpcCall.pickBubbleTaskEnergy()
                val resultJson = JSONObject(result)
                if (ResChecker.checkRes(TAG, resultJson)) {
                    val dataObj = resultJson.optJSONObject("data")
                    val balance = dataObj?.optString("balance", "0") ?: "0"
                    Log.other("拾取能量球成功  当前余额: $balance💰")
                } else {
                    Log.error(TAG, "领取能量球任务失败: ${resultJson.optString("errorMsg", "未知错误")}")
                }
            } else {
                Log.record(TAG, "未完成任何任务，跳过领取能量球")
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

                                Log.other(
                                    "做任务得能量🎈[签到${expireDays}天|" +
                                        coinAmount + "能量，" + toast + "💰]"
                                )
                            } else {
                                Log.record(TAG, "签到接口调用失败：$signJo")
                            }
                            break
                        }
                    }
                } else {
                    Log.record(TAG, "运动签到今日已签到")
                }
            } else {
                Log.record(TAG, "查询签到状态失败：$queryJo")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "sportsCheck_in err", e)
        }
    }

    /**
     * @brief 首页金币收集逻辑
     */
    private fun receiveCoinAsset() {
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
                        Log.other("收集金币💰[$coinAmount 个]")
                    } else {
                        Log.record(TAG, "首页收集金币 $res")
                    }
                }
            } else {
                Log.record(TAG, s)
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
    private fun walk() {
        try {
            val user = JSONObject(AntSportsRpcCall.queryUser())
            if (!ResChecker.checkRes(TAG, user)) {
                Log.error(TAG, "查询用户失败: $user")
                return
            }

            val data = user.optJSONObject("data")
            val joinedPathId = data?.optString("joinedPathId") ?: ""
            if(joinedPathId.isEmpty()) {

                Log.error(TAG, "未找到有效线路: $user")
            }
            val path = queryPath(joinedPathId)

            if (path == null) {
                Log.error(TAG, "无法获取路线详情(PathId: $joinedPathId)")
                return
            }
            val userPathStep = path.getJSONObject("userPathStep")

            //如果是 JOIN 则还没走完
            if ("COMPLETED" == userPathStep.getString("pathCompleteStatus")) {
                Log.record(TAG, "行走路线🚶🏻‍♂️路线[${userPathStep.getString("pathName")}]已完成")
                // 获取新路线 ID
                val newPathId = queryJoinPath(walkPathThemeId)    //walkPathThemeId 在进入walk()之前已经获取了
                if (!newPathId.isNullOrEmpty()) {
                    Log.record(TAG, "发现新路线，准备加入: $newPathId")
                    joinPath(newPathId)
                } else {
                    Log.error(TAG, "未发现可加入的新路线，可能当前地图已全部走完")
                }
                return
            }

            val pathObj = path.getJSONObject("path")
            val minGoStepCount = pathObj.getInt("minGoStepCount")
            val pathStepCount = pathObj.getInt("pathStepCount")
            val forwardStepCount = userPathStep.getInt("forwardStepCount")
            val remainStepCount = userPathStep.getInt("remainStepCount")
            val needStepCount = pathStepCount - forwardStepCount

            if (remainStepCount >= minGoStepCount) {
                val useStepCount = min(remainStepCount, needStepCount)
                walkGo(userPathStep.getString("pathId"), useStepCount, userPathStep.getString("pathName"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "walk err:", t)
        }
    }

    /**
     * @brief 新版路线行走一步
     */
    private fun walkGo(pathId: String, useStepCount: Int, pathName: String) {
        try {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd")
            val jo = JSONObject(AntSportsRpcCall.walkGo(sdf.format(date), pathId, useStepCount))
            if (ResChecker.checkRes(TAG, jo)) {
                Log.other(TAG, "行走路线🚶🏻‍♂️路线[$pathName]#前进了${useStepCount}步")
                queryPath(pathId)
            } else {
                Log.error(TAG, "walkGo失败： [pathId: $pathId]: $jo")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "walkGo err:", t)
        }
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
            Log.record(TAG, "路线: ${userPath?.optString("pathName")}, 进度: ${userPath?.optInt("pathProgress")}%")

            val boxList = data.optJSONArray("treasureBoxList")
            if (boxList != null && boxList.length() > 0) {
                for (i in 0 until boxList.length()) {
                    val boxNo = boxList.optJSONObject(i)?.optString("boxNo")
                    if (!boxNo.isNullOrEmpty()) receiveEvent(boxNo)
                }
            }

            return data
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryPath 过程中发生崩溃", t)
        }
        return null
    }

    /**
     * @brief 新版路线开启宝箱并打印奖励
     */
    private fun receiveEvent(eventBillNo: String) {
        try {
            val jo = JSONObject(AntSportsRpcCall.receiveEvent(eventBillNo))
            if (!ResChecker.checkRes(TAG, jo)) return

            val ja = jo.getJSONObject("data").getJSONArray("rewards")
            for (i in 0 until ja.length()) {
                val reward = ja.getJSONObject(i)
                Log.record(
                    TAG,
                    "行走路线🎁开启宝箱[${reward.getString("rewardName")}]*${reward.getInt("count")}"
                )
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveEvent err:", t)
        }
    }

    /**
     * @brief 根据主题 ID 挑选可加入的 pathId
     */
    private fun queryJoinPath(themeId: String?): String? {
        if (walkCustomPath.value) {
            return walkCustomPathId.value
        }
        var pathId: String? = null
        try {
            val theme = queryWorldMap(walkPathThemeId)
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
            if (ResChecker.checkRes(TAG, jo)) {
                val path = queryPath(realPathId)
                Log.record(TAG, "行走路线🚶🏻‍♂️路线[${path?.getJSONObject("path")?.getString("name")}]已加入")
            } else {
                Log.error(TAG, "行走路线🚶🏻‍♂️路线[$realPathId]有误，无法加入！")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "joinPath err:", t)
        }
    }

    /**
     * @brief 根据配置索引同步更新路线主题 ID
     */
    private fun getWalkPathThemeIdOnConfig() {
        val index = walkPathTheme.value
        if (index >= 0 && index < WalkPathTheme.themeIds.size) {
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
    private fun queryMyHomePage(loader: ClassLoader) {
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
                                Log.record(TAG, jo.getString("resultDesc"))
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
                Log.record(TAG, jo.getString("resultDesc"))
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
                    Log.other("加入线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                } else {
                    Log.record(TAG, jo.getString("resultDesc"))
                }
            } else {
                Log.record(TAG, "好像没有可走的线路了！")
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
                Log.other("行走线路🚶🏻‍♂️[$title]#前进了${jo.getInt("goStepCount")}步")
                val completed = "COMPLETED" == jo.getString("completeStatus")
                val ja = jo.getJSONArray("allTreasureBoxModelList")
                for (i in 0 until ja.length()) {
                    parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey)
                }
                if (completed) {
                    Log.other("完成线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                }
            } else {
                Log.record(TAG, jo.getString("resultDesc"))
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
                if (delay < BaseModel.checkInterval.value) {
                    val taskId = "BX|$boxNo"
                    if (hasChildTask(taskId)) return
                    Log.record(TAG, "还有 $delay ms 开运动宝箱")
                    addChildTask(
                        ChildModelTask(
                            taskId,
                            "BX",
                            Runnable {
                                Log.record(TAG, "蹲点开箱开始")
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
                    Log.other("运动宝箱🎁[$num${jo.getString("name")}]")
                }
                return num
            } else if ("TREASUREBOX_NOT_EXIST" == jo.getString("resultCode")) {
                Log.record(jo.getString("resultDesc"))
                return 1
            } else {
                Log.record(jo.getString("resultDesc"))
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
    private fun queryProjectList(loader: ClassLoader) {
        try {
            var jo = JSONObject(AntSportsRpcCall.queryProjectList(0))
            if (ResChecker.checkRes(TAG, jo)) {
                var charityCoinCount = jo.getInt("charityCoinCount")
                if (charityCoinCount < donateCharityCoinAmount.value) return

                val ja = jo.getJSONObject("projectPage").getJSONArray("data")
                for (i in 0 until ja.length()) {
                    if (charityCoinCount < donateCharityCoinAmount.value) break
                    val basicModel = ja.getJSONObject(i).getJSONObject("basicModel")
                    if ("DONATE_COMPLETED" == basicModel.getString("footballFieldStatus")) break
                    donate(
                        loader,
                        donateCharityCoinAmount.value,
                        basicModel.getString("projectId"),
                        basicModel.getString("title")
                    )
                    Status.donateCharityCoin()
                    charityCoinCount -= donateCharityCoinAmount.value
                    if (donateCharityCoinType.value == DonateCharityCoinType.ONE) break
                }
            } else {
                Log.record(TAG)
                Log.record(jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryProjectList err:", t)
        }
    }

    /**
     * @brief 执行一次慈善捐赠
     */
    private fun donate(loader: ClassLoader, donateCharityCoin: Int, projectId: String, title: String) {
        try {
            val s = AntSportsRpcCall.donate(donateCharityCoin, projectId)
            val jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                Log.other("捐赠活动❤️[$title][$donateCharityCoin 能量🎈]")
            } else {
                Log.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "donate err:", t)
        }
    }

    /**
     * @brief 查询行走步数，并根据条件自动捐步
     */
    private fun queryWalkStep(loader: ClassLoader) {
        try {
            var s = AntSportsRpcCall.queryWalkStep()
            var jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("dailyStepModel")
                val produceQuantity = jo.getInt("produceQuantity")
                val hour = TimeUtil.getFormatTime().split(":").first().toInt()

                if (produceQuantity >= minExchangeCount.value || hour >= latestExchangeTime.value) {
                    AntSportsRpcCall.walkDonateSignInfo(produceQuantity)
                    s = AntSportsRpcCall.donateWalkHome(produceQuantity)
                    jo = JSONObject(s)
                    if (!jo.getBoolean("isSuccess")) return
                    val walkDonateHomeModel = jo.getJSONObject("walkDonateHomeModel")
                    val walkUserInfoModel = walkDonateHomeModel.getJSONObject("walkUserInfoModel")
                    if (!walkUserInfoModel.has("exchangeFlag")) {
                        Status.exchangeToday(UserMap.currentUid ?: return)
                        return
                    }
                    val donateToken = walkDonateHomeModel.getString("donateToken")
                    val walkCharityActivityModel = walkDonateHomeModel.getJSONObject("walkCharityActivityModel")
                    val activityId = walkCharityActivityModel.getString("activityId")
                    s = AntSportsRpcCall.exchange(activityId, produceQuantity, donateToken)
                    jo = JSONObject(s)
                    if (jo.getBoolean("isSuccess")) {
                        val donateExchangeResultModel = jo.getJSONObject("donateExchangeResultModel")
                        val userCount = donateExchangeResultModel.getInt("userCount")
                        val amount = donateExchangeResultModel.getJSONObject("userAmount").getDouble("amount")
                        Log.other("捐出活动❤️[$userCount 步]#兑换$amount 元公益金")
                        Status.exchangeToday(UserMap.currentUid ?: return)
                    } else if (s.contains("已捐步")) {
                        Status.exchangeToday(UserMap.currentUid ?: return)
                    } else {
                        Log.record(TAG, jo.getString("resultDesc"))
                    }
                }
            } else {
                Log.record(TAG, jo.getString("resultDesc"))
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
    private fun userTaskGroupQuery(groupId: String) {
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
                        Log.other("完成任务🧾[$taskName]")
                    } else {
                        Log.record(TAG, "文体每日任务 $res")
                    }
                }
            } else {
                Log.record(TAG, "文体每日任务 $s")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "userTaskGroupQuery err:", t)
        }
    }

    /**
     * @brief 文体中心走路挑战报名
     */
    private fun participate() {
        try {
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
                        if (ResChecker.checkRes(TAG, res)) {
                            val data = res.getJSONObject("data")
                            val roundDescription = data.getString("roundDescription")
                            val targetStepCount = data.getInt("targetStepCount")
                            Log.other("走路挑战🚶🏻‍♂️[$roundDescription]#$targetStepCount")
                        } else {
                            Log.record(TAG, "走路挑战赛 $res")
                        }
                    }
                } else {
                    Log.record(TAG, "queryRoundList $jo")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "participate err:", t)
        }
    }

    /**
     * @brief 文体中心奖励领取
     */
    private fun userTaskRightsReceive() {
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
                        Log.other("领取奖励🎖️[$taskName]#$award")
                    } else {
                        Log.record(TAG, "文体中心领取奖励")
                        Log.record(res.toString())
                    }
                }
            } else {
                Log.record(TAG, "文体中心领取奖励")
                Log.record(s)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "userTaskRightsReceive err:", t)
        }
    }

    /**
     * @brief 文体中心路径特性查询 + 行走任务/加入路径
     */
    private fun pathFeatureQuery() {
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
                Log.record(TAG, jo.getString("resultDesc"))
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
                        Log.other("文体宝箱🎁[$award]")
                    } else {
                        Log.record(TAG, "文体中心开宝箱")
                        Log.record(res.toString())
                    }
                }
            } else {
                Log.record(TAG, "文体中心开宝箱")
                Log.record(s)
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
            if (ResChecker.checkRes(TAG, jo)) {
                Log.other("加入线路🚶🏻‍♂️[$title]")
                pathFeatureQuery()
            } else {
                Log.record(TAG, jo.toString())
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
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("userPath")
                Log.other(
                    "行走线路🚶🏻‍♂️[$title]#前进了" +
                        jo.getInt("userPathRecordForwardStepCount") + "步"
                )
                pathMapHomepage(pathId)
                val completed = "COMPLETED" == jo.getString("userPathRecordStatus")
                if (completed) {
                    Log.other("完成线路🚶🏻‍♂️[$title]")
                    pathFeatureQuery()
                }
            } else {
                Log.record(TAG, s)
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
    private fun queryClubHome() {
        try {
            val maxCount = zeroCoinLimit.value
            if (zeroTrainCoinCount >= maxCount) {
                val today = TimeUtil.getDateStr2()
                DataStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                Log.record(TAG, "✅ 训练好友获得0金币已达${maxCount}次上限，今日不再执行")
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
            val bubbleList = obj.getJSONArray("bubbleList")
            for (j in 0 until bubbleList.length()) {
                val bubble = bubbleList.getJSONObject(j)
                val bubbleId = bubble.optString("bubbleId")

                val responseStr = AntSportsRpcCall.pickBubbleTaskEnergy(bubbleId, false)
                val responseJson = JSONObject(responseStr)

                if (!ResChecker.checkRes(TAG, responseJson)) {
                    Log.error(TAG, "收取训练好友 失败: $responseStr")
                    continue
                }

                var amount = 0
                val data = responseJson.optJSONObject("data")
                if (data != null) {
                    val changeAmountStr = data.optString("changeAmount", "0")
                    amount = changeAmountStr.toIntOrNull() ?: 0
                }

                Log.other("训练好友💰️ [获得:$amount 金币]")

                if (amount <= 0) {
                    zeroTrainCoinCount++
                    val maxCount = zeroCoinLimit.value
                    if (zeroTrainCoinCount >= maxCount) {
                        val today = TimeUtil.getDateStr2()
                        DataStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                        Log.record(TAG, "✅ 连续获得0金币已达${maxCount}次，今日停止执行")
                        return
                    } else {
                        Log.record(TAG, "训练好友0金币计数: $zeroTrainCoinCount/$maxCount")
                    }
                }

                GlobalThreadPools.sleepCompat(1000)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "processBubbleList 异常:", t)
        }
    }

    /**
     * @brief 训练好友：选取可训练好友并执行一次训练
     */
    private fun queryTrainItem() {
        try {
            val clubHomeData = JSONObject(AntSportsRpcCall.queryClubHome())
            val roomList = clubHomeData.optJSONArray("roomList") ?: return

            for (i in 0 until roomList.length()) {
                val room = roomList.optJSONObject(i) ?: continue
                val memberList = room.optJSONArray("memberList") ?: continue

                for (j in 0 until memberList.length()) {
                    val member = memberList.optJSONObject(j) ?: continue
                    val trainInfo = member.optJSONObject("trainInfo")
                    if (trainInfo == null || trainInfo.optBoolean("training", false)) continue

                    val memberId = member.optString("memberId")
                    val originBossId = member.optString("originBossId")
                    val userName = UserMap.getMaskName(originBossId) ?: originBossId

                    val responseData = AntSportsRpcCall.queryTrainItem()
                    val responseJson = JSONObject(responseData)
                    if (!ResChecker.checkRes(TAG, responseJson)) {
                        Log.record(
                            TAG,
                            "queryTrainItem rpc failed: ${responseJson.optString("resultDesc")}"
                        )
                        return
                    }

                    var bizId = responseJson.optString("bizId", "")
                    if (bizId.isEmpty() && responseJson.has("taskDetail")) {
                        bizId = responseJson.getJSONObject("taskDetail").optString("taskId", "")
                    }

                    val trainItemList = responseJson.optJSONArray("trainItemList")
                    if (bizId.isEmpty() || trainItemList == null || trainItemList.length() == 0) {
                        Log.record(TAG, "queryTrainItem response missing bizId or trainItemList")
                        return
                    }

                    var bestItem: JSONObject? = null
                    var bestProduction = -1
                    for (k in 0 until trainItemList.length()) {
                        val item = trainItemList.optJSONObject(k) ?: continue
                        val production = item.optInt("production", 0)
                        if (production > bestProduction) {
                            bestProduction = production
                            bestItem = item
                        }
                    }

                    if (bestItem == null) return

                    val itemType = bestItem.optString("itemType")
                    val trainItemName = bestItem.optString("name")

                    val trainMemberResponse = AntSportsRpcCall.trainMember(
                        bizId,
                        itemType,
                        memberId,
                        originBossId
                    )
                    val trainMemberJson = JSONObject(trainMemberResponse)
                    if (!ResChecker.checkRes(TAG, trainMemberJson)) {
                        Log.record(
                            TAG,
                            "trainMember request failed: ${trainMemberJson.optString("resultDesc")}"
                        )
                        return
                    }

                    Log.other("训练好友🥋[训练:$userName $trainItemName]")
                    GlobalThreadPools.sleepCompat(1000)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryTrainItem err:", t)
        }
    }

    /**
     * @brief 抢好友大战：抢购好友逻辑
     */
    private fun buyMember() {
        try {
            val clubHomeResponse = AntSportsRpcCall.queryClubHome()
            GlobalThreadPools.sleepCompat(500)
            val clubHomeJson = JSONObject(clubHomeResponse)

            if ("ENABLE" != clubHomeJson.optString("clubAuth")) {
                Log.record(TAG, "抢好友大战🧑‍🤝‍🧑未授权开启")
                return
            }

            val assetsInfo = clubHomeJson.optJSONObject("assetsInfo") ?: return
            val coinBalance = assetsInfo.optInt("energyBalance", 0)
            if (coinBalance <= 0) {
                Log.record(TAG, "抢好友大战🧑‍🤝‍🧑当前能量为0，跳过抢好友")
                return
            }

            val roomList = clubHomeJson.optJSONArray("roomList") ?: return

            for (i in 0 until roomList.length()) {
                val room = roomList.optJSONObject(i) ?: continue
                val memberList = room.optJSONArray("memberList")

                if (memberList != null && memberList.length() > 0) continue

                val roomId = room.optString("roomId")
                if (roomId.isEmpty()) continue

                val memberPriceResult = AntSportsRpcCall.queryMemberPriceRanking(coinBalance)
                GlobalThreadPools.sleepCompat(500)
                val memberPriceJson = JSONObject(memberPriceResult)
                if (!memberPriceJson.optBoolean("success", true)) {
                    Log.error(TAG, "queryMemberPriceRanking err: ${memberPriceJson.optString("resultDesc")}")
                    continue
                }

                val memberDetailList = memberPriceJson.optJSONArray("memberDetailList") ?: run {
                    Log.record(TAG, "抢好友大战🧑‍🤝‍🧑暂无可抢好友")
                    continue
                }

                for (j in 0 until memberDetailList.length()) {
                    val detail = memberDetailList.optJSONObject(j) ?: continue
                    val memberModel = detail.optJSONObject("memberModel") ?: continue

                    val originBossId = memberModel.optString("originBossId")
                    val memberIdFromRank = memberModel.optString("memberId")
                    if (originBossId.isEmpty() || memberIdFromRank.isEmpty()) continue

                    var isTarget = originBossIdList.value.contains(originBossId)
                    if (battleForFriendType.value == BattleForFriendType.DONT_ROB) {
                        isTarget = !isTarget
                    }
                    if (!isTarget) continue

                    val priceInfoObj = memberModel.optJSONObject("priceInfo") ?: continue
                    val price = priceInfoObj.optInt("price", Int.MAX_VALUE)
                    if (price > coinBalance) continue

                    val clubMemberResult = AntSportsRpcCall.queryClubMember(memberIdFromRank, originBossId)
                    GlobalThreadPools.sleepCompat(500)
                    val clubMemberDetailJson = JSONObject(clubMemberResult)
                    if (!clubMemberDetailJson.optBoolean("success", true) ||
                        !clubMemberDetailJson.has("member")
                    ) continue

                    val memberObj = clubMemberDetailJson.getJSONObject("member")
                    val currentBossId = memberObj.optString("currentBossId")
                    val memberId = memberObj.optString("memberId")
                    val priceInfoFull = memberObj.optJSONObject("priceInfo") ?: continue

                    if (currentBossId.isEmpty() || memberId.isEmpty()) continue

                    val priceInfoStr = priceInfoFull.toString()

                    val buyMemberResult = AntSportsRpcCall.buyMember(
                        currentBossId,
                        memberId,
                        originBossId,
                        priceInfoStr,
                        roomId
                    )
                    GlobalThreadPools.sleepCompat(500)
                    val buyMemberResponse = JSONObject(buyMemberResult)

                    if (ResChecker.checkRes(TAG, buyMemberResponse)) {
                        val userName = UserMap.getMaskName(originBossId) ?: originBossId
                        Log.other("抢购好友🥋[成功:将 $userName 抢回来]")
                        if (trainFriend.value) {
                            queryTrainItem()
                        }
                        return
                    } else if ("CLUB_AMOUNT_NOT_ENOUGH" == buyMemberResponse.optString("resultCode")) {
                        Log.record(TAG, "[能量🎈不足，无法完成抢购好友！]")
                        return
                    } else if ("CLUB_MEMBER_TRADE_PROTECT" == buyMemberResponse.optString("resultCode")) {
                        Log.record(TAG, "[暂时无法抢购好友，给Ta一段独处的时间吧！]")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "buyMember err:", t)
        }
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
        private val MAX_ERROR_COUNT: Int =
            if (BaseModel.setMaxErrorCount.value > 0) BaseModel.setMaxErrorCount.value else 5

        /** @brief 任务循环间隔（毫秒） */
        private val TASK_LOOP_DELAY: Long = 1000

        /**
         * @brief 健康岛任务入口
         */
        fun runNeverland() {
            try {
                Log.record(TAG, "开始执行健康岛任务")
                if (neverlandTask.value) {
                    // 1. 签到
                    neverlandDoSign()
                    // 2. 任务大厅循环处理
                    loopHandleTaskCenter()
                    // 3. 浏览任务
                    handleHealthIslandTask()
                    // 4. 捡泡泡
                    neverlandPickAllBubble()
                }

                if (neverlandGrid.value) {
                    // 5. 自动走路建造
                    neverlandAutoTask()
                }

                Log.record(TAG, "健康岛任务结束")
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
                if (Status.hasFlagToday("AntSports::neverlandDoSign::已签到")) return

                Log.record(TAG, "健康岛 · 检查签到状态")
                val jo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.querySign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "查询签到失败:", jo) ||
                    !ResChecker.checkRes(TAG, jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    val errorCode = jo.optString("errorCode", "")
                    if ("ALREADY_SIGN_IN" == errorCode ||
                        "已签到" == jo.optString("errorMsg", "")
                    ) {
                        Status.setFlagToday("AntSports::neverlandDoSign::已签到")
                    }
                    return
                }

                val data = jo.getJSONObject("data")
                val signInfo = data.optJSONObject("continuousSignInfo")
                if (signInfo != null && signInfo.optBoolean("signedToday", false)) {
                    Log.record(
                        TAG,
                        "今日已签到 ✔ 连续：${signInfo.optInt("continuitySignedDayCount")} 天"
                    )
                    return
                }

                Log.record(TAG, "健康岛 · 正在签到…")
                val signRes = JSONObject(AntSportsRpcCall.NeverlandRpcCall.takeSign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "签到失败:", signRes) ||
                    !ResChecker.checkRes(TAG, signRes) ||
                    signRes.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "takeSign raw=$signRes")
                    Status.setFlagToday("AntSports::neverlandDoSign::已签到")
                    return
                }

                val signData = signRes.getJSONObject("data")
                val reward = signData.optJSONObject("continuousDoSignInVO")
                val rewardAmount = reward?.optInt("rewardAmount", 0) ?: 0
                val rewardType = reward?.optString("rewardType", "") ?: ""
                val signInfoAfter = signData.optJSONObject("continuousSignInfo")
                val newContinuity = signInfoAfter?.optInt("continuitySignedDayCount", -1) ?: -1

                Log.other(
                    "健康岛签到成功 🎉 +" + rewardAmount + rewardType +
                        " 连续：" + newContinuity + " 天"
                )
                Status.setFlagToday("AntSports::neverlandDoSign::已签到")
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
            Log.record(TAG, "开始循环处理任务大厅（失败限制：$MAX_ERROR_COUNT 次）")

            while (true) {
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
                        Log.other("任务中心为空，无任务可处理")
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
                            Log.record(TAG, "任务 [$title] 需要手动报名，已自动拉黑并跳过")
                            if (taskId.isNotEmpty()) {
                                TaskBlacklist.addToBlacklist(taskId, title)
                            }
                            continue
                        }

                        if (TaskBlacklist.isTaskInBlacklist(taskId)) continue

                        if (("PROMOKERNEL_TASK" == type || "LIGHT_TASK" == type) &&
                            "FINISHED" != status
                        ) {
                            pendingTasks.add(task)
                        }
                    }

                    if (pendingTasks.isEmpty()) {
                        Log.record(TAG, "没有可处理或领取的任务，退出循环")
                        break
                    }

                    Log.record(TAG, "本次发现 ${pendingTasks.size} 个可处理任务（含待领取）")

                    var currentBatchError = 0
                    for (task in pendingTasks) {
                        val ok = handleSingleTask(task)
                        if (!ok) currentBatchError++
                        GlobalThreadPools.sleepCompat(3000)
                    }

                    errorCount += currentBatchError
                    Log.record(TAG, "当前批次执行完毕，准备下一次刷新检查")
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

                Log.record(TAG, "任务：[$title] 状态：$status 类型：$type")

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
                            Log.record(TAG, "完成[$title]✔$rewardDetail")
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

                Log.record(TAG, "任务状态为 $status，跳过执行")
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
                Log.record(TAG, "开始检查健康岛浏览任务")
                var hasTask = true
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
                        Log.record(TAG, "健康岛浏览任务列表为空")
                        hasTask = false
                        continue
                    }

                    for (i in 0 until taskInfos.length()) {
                        val taskInfo = taskInfos.getJSONObject(i)
                        val encryptValue = taskInfo.optString("encryptValue")
                        val energyNum = taskInfo.optInt("energyNum", 0)
                        val viewSec = taskInfo.optInt("viewSec", 15)

                        if (encryptValue.isEmpty()) {
                            Log.error(TAG, "健康岛任务 encryptValue 为空，跳过")
                            continue
                        }

                        Log.record(TAG, "健康岛浏览任务：能量+$energyNum，需等待${viewSec}秒")
                        GlobalThreadPools.sleepCompat((viewSec / 3).toLong())

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
                            Log.other("✅ 健康岛浏览任务完成，获得能量+$energyNum")
                        } else {
                            Log.error(TAG, "健康岛任务领取失败: $receiveResp")
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
                    Log.other("✔ 活动任务完成：$title")
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
                    Log.other("✔ 浏览任务完成：$title$rewardMsg")
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
                Log.record(TAG, "健康岛 · 检查可领取泡泡")

                val jo = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryBubbleTask())

                if (!ResChecker.checkRes(TAG + "查询泡泡失败:", jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "queryBubbleTask raw=$jo")
                    return
                }

                val arr = jo.getJSONObject("data").optJSONArray("bubbleTaskVOS")
                if (arr == null || arr.length() == 0) {
                    Log.other("无泡泡可领取")
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
                        encryptValues.add(encryptValue)
                        Log.record(
                            TAG,
                            "找到可浏览任务： ${item.optString("title")}，能量+$energyNum，需等待${viewSec}秒"
                        )
                    } else if (!item.optBoolean("initState") &&
                        item.optString("medEnergyBallInfoRecordId").isNotEmpty()
                    ) {
                        ids.add(item.getString("medEnergyBallInfoRecordId"))
                    }
                }

                if (ids.isEmpty() && encryptValues.isEmpty()) {
                    Log.record(TAG, "没有可领取的泡泡任务")
                    return
                }

                if (ids.isNotEmpty()) {
                    Log.record(TAG, "健康岛 · 正在领取 ${ids.size} 个泡泡…")
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
                        Log.record(TAG, "健康岛 · 本次未获得任何能量")
                    } else {
                        Log.other("捡泡泡成功 🎈 +$changeAmount 余额：$balance")
                    }
                }

                for (encryptValue in encryptValues) {
                    Log.record(TAG, "开始浏览任务，任务 encryptValue: $encryptValue")

                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        if (encryptValue == item.optString("encryptValue")) {
                            val energyNum = item.optInt("energyNum", 0)
                            val viewSec = item.optInt("viewSec", 15)
                            val title = item.optString("title")

                            GlobalThreadPools.sleepCompat(viewSec * 1000L)

                            val receiveResp = JSONObject(
                                AntSportsRpcCall.NeverlandRpcCall.energyReceive(
                                    encryptValue,
                                    energyNum,
                                    "LIGHT_FEEDS_TASK",
                                    "adBubble"
                                )
                            )

                            if (ResChecker.checkRes(TAG + "领取泡泡任务奖励:", receiveResp)) {
                                Log.other("✅ 浏览任务[$title]完成，获得能量+$energyNum")
                            } else {
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
            val maxStepLimit = neverlandGridStepCount.value
            val remainSteps = maxStepLimit - stepCount

            Log.record(
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
            val maxLimit = neverlandGridStepCount.value
            Log.record(
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
                Log.record(TAG, "健康岛 · 启动走路建造任务")

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

                Log.record(
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
                    Log.record(TAG, "今日步数已达上限, 任务结束")
                    return
                }

                var leftEnergy = queryUserEnergy()
                if (leftEnergy < 5) {
                    Log.record(TAG, "剩余能量不足(< 5), 无法执行任务")
                    return
                }

                if (isNewGame) {
                    executeAutoBuild(branchId, mapId, remainSteps, leftEnergy, mapName)
                } else {
                    executeAutoWalk(branchId, mapId, remainSteps, leftEnergy, mapName)
                }

                Log.record(TAG, "健康岛自动走路建造执行完成 ✓")
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
                    0
                } else {
                    val balance = energyResp.getJSONObject("data").optInt("balance", 0)
                    Log.record(TAG, "当前剩余能量: $balance")
                    balance
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryUserEnergy err", t)
                0
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
                Log.record(TAG, "开始执行旧版行走任务")
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
                    Log.record(TAG, "当前地图不可走(canWalk=false)，跳过走路任务")
                    return
                }

                val mapStarData = mapInfo.optJSONObject("starData")
                var lastCurrStar = mapStarData?.optInt("curr", 0) ?: 0

                for (i in 0 until remainSteps) {
                    if (leftEnergy < 5) {
                        Log.record(TAG, "[$mapName] 能量不足(< 5), 停止走路任务")
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

                    Log.other(sb.toString())

                    tryReceiveStageReward(branchId, mapId, starData)

                    if (currStar >= maxStar) {
                        Log.other("[$mapName] 当前地图已完成星星，准备切换地图")
                        chooseAvailableMap()
                        break
                    }
                    Thread.sleep(888)
                }
                Log.record(TAG, "自动走路任务完成 ✓")
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

            Log.other(String.format("检测到未领取关卡奖励 🎁 map=%s 等级: %d，尝试领取…", mapId, rewardLevel))

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
                    Log.other("关卡奖励已被领取或不存在（可忽略）")
                } else {
                    Log.error(TAG, "领取关卡奖励失败: $rewardResp")
                }
                return
            }

            val data = rewardResp.optJSONObject("data")
            val receiveResult = data?.optJSONObject("receiveResult")
            if (receiveResult == null) {
                Log.record(TAG, "关卡奖励领取成功 🎉（无奖励详情）")
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
                Log.other(
                    String.format(
                        "Lv.%s 奖励领取成功 🎉 %s | 当前余额: %s",
                        rewardLevel,
                        sb.toString(),
                        balance
                    )
                )
            } else {
                Log.other("关卡奖励领取成功 🎉（无可展示奖励）")
            }
        }

        /**
         * @brief 查询地图列表，优先返回 DOING 地图，否则随机选择 LOCKED 地图并切换
         */
        private fun chooseAvailableMap(): JSONObject? {
            return try {
                val mapResp = JSONObject(AntSportsRpcCall.NeverlandRpcCall.queryMapList())
                if (!ResChecker.checkRes(TAG + " 查询地图失败:", mapResp)) {
                    Log.error(TAG, "queryMapList 失败: $mapResp")
                    return null
                }

                val data = mapResp.optJSONObject("data")
                val mapList = data?.optJSONArray("mapList")
                if (mapList == null || mapList.length() == 0) {
                    Log.error(TAG, "地图列表为空")
                    return null
                }

                var doingMap: JSONObject? = null
                val lockedMaps = mutableListOf<JSONObject>()
                for (i in 0 until mapList.length()) {
                    val map = mapList.getJSONObject(i)
                    val status = map.optString("status")
                    if ("DOING" == status) {
                        doingMap = map
                        break
                    } else if ("LOCKED" == status) {
                        lockedMaps.add(map)
                    }
                }

                if (doingMap != null) {
                    Log.other(
                        "当前 DOING 地图: " + doingMap.optString("mapName") +
                            doingMap.optString("mapId") + " → 执行一次强制切换确保状态一致"
                    )
                    return chooseMap(doingMap)
                }

                if (lockedMaps.isEmpty()) {
                    Log.error(TAG, "没有 DOING 且没有可选的 LOCKED 地图")
                    return null
                }

                val chosenLocked = lockedMaps[Random().nextInt(lockedMaps.size)]
                Log.other("随机选择 LOCKED 地图: " + chosenLocked.optString("mapId"))
                chooseMap(chosenLocked)
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
                val branchId = map.optString("branchId")
                val resp = JSONObject(
                    AntSportsRpcCall.NeverlandRpcCall.chooseMap(branchId, mapId)
                )
                if (ResChecker.checkRes(TAG, resp)) {
                    Log.record(TAG, "切换地图成功: $mapId")
                    map
                } else {
                    Log.error(TAG, "切换地图失败: $resp")
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
            var branchId = branchIdInit
            var mapId = mapIdInit
            var remainSteps = remainStepsInit
            var leftEnergy = leftEnergyInit
            try {
                Log.other(String.format("开始执行建造任务, 地图: %s", mapId))

                val resp = AntSportsRpcCall.NeverlandRpcCall.queryMapInfoNew(mapId)
                val mapInfo = JSONObject(resp)

                if (!ResChecker.checkRes(TAG + " 查询建造地图失败", mapInfo)) {
                    Log.error(TAG, "查询建造地图失败 $mapInfo")
                    return
                }
                val data = mapInfo.optJSONObject("data")
                if (data == null) {
                    Log.error(TAG, "地图Data 为空，无法解析")
                    return
                }

                val mapEnergyFinal = data.optInt("mapEnergyFinal")
                val mapEnergyProcess = data.optInt("mapEnergyProcess")
                val buildings = data.optJSONArray("buildingConfigInfos")
                var lastBuildingIndex = -1
                if (buildings != null && buildings.length() > 0) {
                    lastBuildingIndex = buildings.getJSONObject(buildings.length() - 1)
                        .optInt("buildingIndex", -1)
                    Log.record(TAG, "最后一个建筑 Index: $lastBuildingIndex")
                }

                if (mapEnergyProcess == mapEnergyFinal) {
                    Log.record(TAG, "当前地图已建造完成，准备切换地图...")
                    val choiceMapInfo = chooseAvailableMap()
                    if (choiceMapInfo == null) {
                        Log.error(TAG, "切换地图失败，可能无可用地图，任务终止。")
                        return
                    }
                    if (choiceMapInfo.optBoolean("newIsLandFlg", true)) {
                        branchId = choiceMapInfo.optString("branchId")
                        mapId = choiceMapInfo.optString("mapId")
                        Log.record(TAG, "成功切换到可建造的新地图: $mapId，继续执行建造。")
                    } else {
                        Log.record(TAG, "已切换至走路地图: $mapId，将在下次运行时执行，任务终止。")
                        return
                    }
                }

                while (remainSteps > 0 && leftEnergy >= 5) {
                    val maxMulti = min(10, remainSteps)
                    val energyBasedMulti = leftEnergy / 5
                    val multiNum = min(maxMulti, energyBasedMulti)

                    val buildResp = JSONObject(
                        AntSportsRpcCall.NeverlandRpcCall.build(branchId, mapId, multiNum)
                    )
                    if (!ResChecker.checkRes(TAG + " build 失败:", buildResp)) {
                        Log.error(
                            TAG,
                            String.format(
                                "build 失败, multiNum=%d, 响应: %s",
                                multiNum,
                                buildResp
                            )
                        )
                        break
                    }

                    val buildData = buildResp.optJSONObject("data")
                    if (buildData == null || buildData.length() == 0) {
                        Log.record(TAG, "⚠️ build响应数据为空，当前地图已达限制，任务重新进入地图完成处理流程。")
                        chooseAvailableMap()
                        return
                    }

                    val before = buildData.optJSONObject("beforeStageInfo")
                    val end = buildData.optJSONObject("endStageInfo")
                    var actualUsedEnergy = 0

                    if (before != null && end != null) {
                        val bIdxBefore = before.optInt("buildingIndex")
                        val bIdxEnd = end.optInt("buildingIndex")
                        actualUsedEnergy = if (bIdxEnd > bIdxBefore) {
                            (before.optInt("buildingEnergyFinal") -
                                before.optInt("buildingEnergyProcess")) +
                                end.optInt("buildingEnergyProcess")
                        } else {
                            end.optInt("buildingEnergyProcess") -
                                before.optInt("buildingEnergyProcess")
                        }
                    } else {
                        actualUsedEnergy = multiNum * 5
                    }

                    leftEnergy -= actualUsedEnergy
                    val stepIncrease = calculateBuildSteps(buildData, multiNum)
                    val totalSteps = recordStepIncrease(stepIncrease)
                    remainSteps -= stepIncrease

                    val awardInfo = extractAwardInfo(buildData)

                    Log.other(
                        String.format(
                            "建造进度 🏗️ 倍数: x%d | 能量: %d | 本次: +%d | 今日: %d/%d%s",
                            multiNum,
                            leftEnergy,
                            stepIncrease,
                            totalSteps,
                            neverlandGridStepCount.value,
                            awardInfo
                        )
                    )
                    GlobalThreadPools.sleepCompat(1000)
                }
                Log.other("自动建造任务完成 ✓")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "executeAutoBuild err", t)
            }
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
                val awards = buildData?.optJSONArray("awards")
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

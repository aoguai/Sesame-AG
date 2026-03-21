package io.github.aoguai.sesameag.task.antOrchard

import android.util.Base64
import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.entity.AlipayUser
import io.github.aoguai.sesameag.hook.internal.SecurityBodyHelper
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.util.CoroutineUtils
import io.github.aoguai.sesameag.util.GameTask
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.RandomUtil
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TaskBlacklist
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class AntOrchard : ModelTask() {
    companion object {
        private val TAG = AntOrchard::class.java.simpleName
        private const val STATUS_YEB_WATER_COUNT = "ANTORCHARD_SPREAD_MANURE_COUNT_YEB"
        private const val STATUS_MONEY_TREE_COLLECTED = "ANTORCHARD_MONEY_TREE_COLLECTED"
        private const val STATUS_YEB_EXP_GOLD_TASK_PREFIX = "ANTORCHARD_YEB_EXP_GOLD_TASK"
        private const val STATUS_YEB_EXP_GOLD_SIGN_DONE = "ANTORCHARD_YEB_EXP_GOLD_SIGN_DONE"
        private const val STATUS_YEB_EXP_GOLD_EXCHANGE_DONE = "ANTORCHARD_YEB_EXP_GOLD_EXCHANGE_DONE"
        private const val ORCHARD_SOURCE = "ch_appcenter__chsub_9patch"
        private const val YEB_SOURCE = "yaoqianshu_qiehuan"
        private const val XLIGHT_PAGE_FROM = "ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html"
    }

    private var userId: String? = UserMap.currentUid
    private var treeLevel: String? = null
    private var currentPlantScene: String = "main"
    private var executeIntervalInt: Int = 0
    private var skipManurePotCollectThisRound: Boolean = false

    private lateinit var executeInterval: IntegerModelField
    private lateinit var receiveSevenDayGift: BooleanModelField
    private lateinit var receiveOrchardTaskAward: BooleanModelField
    // {{ 修改：分离果树和摇钱树的施肥次数配置 }}
    private lateinit var orchardSpreadManureCountMain: IntegerModelField
    private lateinit var orchardSpreadManureCountYeb: IntegerModelField

    private lateinit var assistFriendList: SelectModelField
    //模式选择
    private lateinit var plantModeField: ChoiceModelField


    override fun getName(): String = "农场"

    override fun getGroup(): ModelGroup = ModelGroup.ORCHARD

    override fun getIcon(): String = "AntOrchard.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()


        modelFields.addField(
            ChoiceModelField(
                "plantMode",
                "种植模式",
                PlantModeType.MAIN,
                PlantModeType.nickNames,
                "选择优先推进果树、摇钱树或先摇钱树后果树的混合策略。"
            ).also { plantModeField = it }
        )

        modelFields.addField(
            IntegerModelField("executeInterval", "执行间隔(毫秒)", 500, 500, null).withDesc(
                "单次农场操作之间的等待时间，过小可能增加风控。"
            ).also { executeInterval = it }
        )
        modelFields.addField(
            BooleanModelField("receiveSevenDayGift", "收取七日礼包", true).withDesc(
                "自动领取芭芭农场七日礼包奖励。"
            ).also { receiveSevenDayGift = it }
        )
        modelFields.addField(
            BooleanModelField("receiveOrchardTaskAward", "收取农场任务奖励", false).withDesc(
                "自动领取芭芭农场任务奖励，包括肥料等常规收益。"
            ).also { receiveOrchardTaskAward = it }
        )
        // {{ 修改：添加果树和摇钱树的独立设置项 }}
        modelFields.addField(
            IntegerModelField("orchardSpreadManureCount", "果树每日施肥次数", 0).withDesc(
                "每日给果树施肥的次数；施肥可推进成熟并产出庄园食材。-1 表示施肥到当日上限。"
            ).also { orchardSpreadManureCountMain = it }
        )
        modelFields.addField(
            IntegerModelField("orchardSpreadManureCountYeb", "摇钱树每日施肥次数", 0).withDesc(
                "每日给摇钱树施肥的次数；0 表示不处理摇钱树，-1 表示施肥到当日上限。"
            ).also { orchardSpreadManureCountYeb = it }
        )

        modelFields.addField(
            SelectModelField("assistFriendList", "助力好友列表", LinkedHashSet(), AlipayUser::getList).withDesc(
                "仅对选中的好友执行助力流程。"
            ).also { assistFriendList = it }
        )

        return modelFields
    }

    override suspend fun runSuspend() {
        try {
            Log.record(TAG, "执行开始-${getName()}")
            skipManurePotCollectThisRound = false
            executeIntervalInt = maxOf(executeInterval.value ?: 0, 500)

            val indexResponse = AntOrchardRpcCall.orchardIndex()
            val indexJson = JSONObject(indexResponse)

            if (indexJson.optString("resultCode") != "100") {
                Log.record(TAG, indexJson.optString("resultDesc", "orchardIndex 调用失败"))
                return
            }

            if (!indexJson.optBoolean("userOpenOrchard", false)) {
                enableField.value = false
                Log.farm("请先开启芭芭农场！")
                return
            }

            val taobaoDataStr = indexJson.optString("taobaoData")
            if (taobaoDataStr.isNotEmpty()) {
                val taobaoData = JSONObject(taobaoDataStr)
                treeLevel = taobaoData.optJSONObject("gameInfo")
                    ?.optJSONObject("plantInfo")
                    ?.optJSONObject("seedStage")
                    ?.optInt("stageLevel")
                    ?.toString()
            }
            currentPlantScene = indexJson.optString("currentPlantScene", currentPlantScene)

            if (userId == null) {
                userId = UserMap.currentUid
            }

            // 施肥活动奖励（丰收奖励）：抓包可见 spreadManureActivity.status=FINISHED 但 manureTaskAwardReceive=false
            tryReceiveSpreadManureActivityAward(indexJson)

            // 七日礼包
            if (receiveSevenDayGift.value == true) {
                if (indexJson.has("lotteryPlusInfo")) {
                    drawLotteryPlus(indexJson.getJSONObject("lotteryPlusInfo"))
                } else {
                    checkLotteryPlus()
                }
            }

            // 每日肥料 (Entry入口)
            extraInfoGet("entry")

            // 砸金蛋
            val goldenEggInfo = indexJson.optJSONObject("goldenEggInfo")
            if (goldenEggInfo != null) {
                val unsmashed = goldenEggInfo.optInt("unsmashedGoldenEggs")
                val limit = goldenEggInfo.optInt("goldenEggLimit")
                val smashed = goldenEggInfo.optInt("smashedGoldenEggs")

                if (unsmashed > 0) {
                    // 现成的蛋先砸了
                    smashedGoldenEgg(unsmashed)
                } else {
                    val remain = limit - smashed
                    if (remain > 0) {
                        GameTask.Orchard_ncscc.report(remain)
                    }
                }
            }

            // 农场任务
            if (receiveOrchardTaskAward.value == true) {
                doOrchardDailyTask(userId!!)
                triggerTbTask()
                handleYebExpGoldTasks()
            }

            // 摇钱树余额奖励 (每天7点后)
            receiveMoneyTreeReward()

            // 回访奖励
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTORCHARD_WIDGET_DAILY_AWARD)) {
                receiveOrchardVisitAward()
            }

            limitedTimeChallenge()

            // 施肥逻辑
            // {{ 修改：调用新的施肥分发逻辑 }}
            if ((orchardSpreadManureCountMain.value ?: 0) != 0 || (orchardSpreadManureCountYeb.value ?: 0) != 0) {
                CoroutineUtils.sleepCompat(200)
                orchardSpreadManure()
                // 施肥后刷新并尝试领取丰收奖励（例如：施肥满5次可领）
                tryReceiveSpreadManureActivityAwardByQueryIndex()
            }

            // 许愿 (仅根据果树计数判断，或者可以改为独立配置，此处保持原逻辑使用果树计数)
            val wateredMain = Status.getIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SPREAD_MANURE_COUNT) ?: 0
            if (wateredMain in 3..<10) {
                querySubplotsActivity(3)
            } else if (wateredMain >= 10) {
                querySubplotsActivity(10)
            }

            // 助力
            orchardAssistFriend()

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "start.run err:", t)
        } finally {
            Log.record(TAG, "执行结束-${getName()}")
        }
    }

    private fun orchardSpreadManure() {
        try {
            val modeSet = plantModeField.value
            // {{ 修改：分别获取两个配置的上限值 }}
            val targetLimitMain = orchardSpreadManureCountMain.value ?: 0
            val targetLimitYeb = orchardSpreadManureCountYeb.value ?: 0

            // 1. 如果是 摇钱树模式(YEB) 或者 混合模式(HYBRID)
            if (modeSet == PlantModeType.YEB || modeSet == PlantModeType.HYBRID) {
                if (targetLimitYeb != 0) {
                    waterTree("yeb", targetLimitYeb)
                }
            }

            // 2. 如果是 果树模式(MAIN) 或者 混合模式(HYBRID)
            if (modeSet == PlantModeType.MAIN || modeSet == PlantModeType.HYBRID) {
                if (targetLimitMain != 0) {
                    waterTree("main", targetLimitMain)
                }
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "orchardSpreadManure err:", t)
        }
    }

    private fun waterTree(targetScene: String, targetLimit: Int) {
        val isMain = targetScene == "main"
        val waterToLimit = targetLimit == -1
        val sceneName = if (isMain) "种果树" else "种摇钱树"
        // 独立计数：果树使用原Flag，摇钱树使用新Key
        val statusKey = if (isMain) StatusFlags.FLAG_ANTORCHARD_SPREAD_MANURE_COUNT else STATUS_YEB_WATER_COUNT

        var totalWatered = Status.getIntFlagToday(statusKey) ?: 0

        if (!waterToLimit && totalWatered >= targetLimit) {
            Log.record(TAG, "$sceneName: 今日已完成施肥目标 $totalWatered/$targetLimit")
            return
        }

        Log.record(
            TAG,
            if (waterToLimit) {
                "开始 $sceneName 任务，当前进度: $totalWatered，目标: 施肥到当日上限"
            } else {
                "开始 $sceneName 任务，当前进度: $totalWatered"
            }
        )

        // 切换场景
        try {
            AntOrchardRpcCall.switchPlantScene(targetScene, getSceneSource(targetScene))
            currentPlantScene = targetScene
            CoroutineUtils.sleepCompat(500)
        } catch (ignore: Throwable) {}

        val sourceList = listOf(
            "DNHZ_NC_zhimajingnangSF",
            "widget_shoufei",
            "ch_appcenter__chsub_9patch"
        )

        do {
            try {
                // 检查肥料余额
                val orchardIndexData = JSONObject(AntOrchardRpcCall.orchardIndex())
                if (orchardIndexData.optString("resultCode") != "100") break

                val taobaoDataStr = orchardIndexData.optString("taobaoData")
                if (taobaoDataStr.isEmpty()) break
                val spreadStageLeftTimesBefore = orchardIndexData.optJSONObject("spreadManureActivity")
                    ?.optJSONObject("spreadManureStage")
                    ?.optInt("leftSpreadManureTimes", Int.MAX_VALUE)
                    ?: Int.MAX_VALUE

                // {{ 修改：适配不同场景的肥料数据结构 }}
                val taobaoData = JSONObject(taobaoDataStr)
                val accountInfo = if (isMain) {
                    taobaoData.optJSONObject("gameInfo")?.optJSONObject("accountInfo")
                } else {
                    // 摇钱树模式下 taobaoData 结构不同，通常肥料信息在 common 字段或者复用 gameInfo，需根据实际情况防御性获取
                    // 根据日志，摇钱树模式下 orchardIndex 返回的 taobaoData 依然包含 gameInfo->accountInfo (24日 13:13:18.50 日志)
                    taobaoData.optJSONObject("gameInfo")?.optJSONObject("accountInfo")
                }

                if (accountInfo != null) {
                    val happyPoint = accountInfo.optInt("happyPoint", 0)
                    val wateringCost = 600 // 默认消耗

                    if (happyPoint < wateringCost) {
                        Log.record(TAG, "$sceneName 肥料不足: 当前 $happyPoint < 消耗 $wateringCost")
                        return
                    }
                }

                // 核心逻辑：施肥到199次时，强制开启5连，突破200次限制到204次
                // {{ 修改：移除 isMain 限制，让摇钱树也支持 199->204 逻辑 }}
                var useBatchSpread = false
                var actualWaterTimes = 1

                if (totalWatered == 199) {
                    useBatchSpread = true
                    actualWaterTimes = 5 // 预期增加5次
                    Log.record(TAG, "$sceneName 触发199次临界点，开启5连施肥模式以突破限制")
                }

                val wua = SecurityBodyHelper.getSecurityBodyData(4).toString()
                val randomSource = sourceList.random()

                // 执行施肥请求
                val spreadResponse = AntOrchardRpcCall.orchardSpreadManure(wua, randomSource, useBatchSpread, targetScene)
                val spreadJson = JSONObject(spreadResponse)
                val resultCode = spreadJson.optString("resultCode")

                // 摇钱树特有逻辑：达到上限停止
                // {{ 修改：增加 P13 状态码判定 (摇钱树施肥已达当日上限) }}
                if ((resultCode == "P14" || resultCode == "P13") && !isMain) {
                    Log.record(TAG, "$sceneName 已达持仓金额上限/次数上限，停止施肥")
                    return
                }

                if (resultCode != "100") {
                    Log.error(TAG, "$sceneName 施肥失败: ${spreadJson.optString("resultDesc")}")
                    return
                }

                // 更新计数
                val spreadTaobaoDataStr = spreadJson.optString("taobaoData")
                if (spreadTaobaoDataStr.isNotEmpty()) {
                    val spreadTaobaoData = JSONObject(spreadTaobaoDataStr)

                    // 尝试从服务端获取今日次数，如果不准确(或服务端没返回)则手动累加
                    var dailyCount = 0

                    // {{ 修改：针对不同场景解析统计数据 }}
                    if (isMain && spreadTaobaoData.has("statistics")) {
                        dailyCount = spreadTaobaoData.getJSONObject("statistics").optInt("dailyAppWateringCount")
                    } else if (!isMain) {
                        // 摇钱树尝试解析 dailyRevenueInfo 或手动累加
                        // 由于日志中摇钱树返回数据结构差异大，这里保持手动累加作为兜底，若有明确字段可补充
                    }

                    if (dailyCount > 0) {
                        totalWatered = dailyCount
                    } else {
                        totalWatered += actualWaterTimes
                    }

                    Status.setIntFlagToday(statusKey, totalWatered)

                    // {{ 修改：提取进度文本，统一日志格式 }}
                    var stageText = ""
                    if (isMain) {
                        stageText = spreadTaobaoData.optJSONObject("currentStage")?.optString("stageText") ?: ""
                    } else {
                        // 尝试从 yebScenePlantInfo 提取进度
                        val yebInfo = spreadTaobaoData.optJSONObject("yebScenePlantInfo")?.optJSONObject("plantProgressInfo")
                        if (yebInfo != null) {
                            val levelProgress = yebInfo.optString("levelProgress", "")
                            if (levelProgress.isNotEmpty()) {
                                stageText = "当前进度:$levelProgress%"
                            }
                        }
                    }

                    Log.farm("施肥💩[$sceneName] $stageText|累计:$totalWatered")
                } else {
                    // 兜底逻辑
                    totalWatered += actualWaterTimes
                    Status.setIntFlagToday(statusKey, totalWatered)
                    Log.farm("施肥💩[$sceneName] 成功|累计:$totalWatered")
                }

                CoroutineUtils.sleepCompat(500)
                // 检查施肥后礼盒
                checkFertilizerBox(targetScene)
                if (spreadStageLeftTimesBefore in 1..actualWaterTimes) {
                    tryReceiveSpreadManureActivityAwardByQueryIndex()
                }

            } finally {
                CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
            }
        } while (waterToLimit || totalWatered < targetLimit)

        Log.record(
            TAG,
            if (waterToLimit) {
                "$sceneName 施肥结束，已按当日上限模式停止，最终累计: $totalWatered"
            } else {
                "$sceneName 施肥结束，最终累计: $totalWatered"
            }
        )
    }

    // ... 其余方法保持不变 ...
    private fun receiveMoneyTreeReward() {
        try {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            // 每天7点后尝试领取
            if (hour >= 7 && !Status.hasFlagToday(STATUS_MONEY_TREE_COLLECTED)) {
                Log.record(TAG, "检测到7点已过，尝试领取摇钱树余额奖励...")
                val res = AntOrchardRpcCall.moneyTreeTrigger()
                val json = JSONObject(res)
                if (json.optBoolean("success")) {
                    val result = json.optJSONObject("result")
                    val awardInfo = result?.optJSONObject("awardInfo")
                    val amount = awardInfo?.optString("totalAmount", "0") ?: "0"

                    if (amount != "0") {
                        Log.farm("摇钱树💰[获得余额]#$amount 元")
                    } else {
                        Log.record(TAG, "摇钱树暂无奖励可领")
                    }
                    Status.setFlagToday(STATUS_MONEY_TREE_COLLECTED)
                } else {
                    Log.record(TAG, "摇钱树奖励领取失败: ${json.toString()}")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveMoneyTreeReward err:", t)
        }
    }

    private fun handleYebExpGoldTasks() {
        try {
            val enterSceneResponse = JSONObject(AntOrchardRpcCall.refinedOperation())
            if (enterSceneResponse.optString("resultCode").isNotEmpty() &&
                enterSceneResponse.optString("resultCode") != "100"
            ) {
                Log.record(
                    TAG,
                    "余额宝体验金任务场景进入异常: ${
                        enterSceneResponse.optString(
                            "resultDesc",
                            enterSceneResponse.toString()
                        )
                    }"
                )
            }

            var queryResponse = JSONObject(AntOrchardRpcCall.queryYebExpGoldMain())
            if (!isYebExpGoldSuccess(queryResponse)) {
                Log.record(
                    TAG,
                    "余额宝体验金任务查询失败: ${
                        queryResponse.optString(
                            "resultDesc",
                            queryResponse.toString()
                        )
                    }"
                )
                return
            }

            val manualTaskTitles = LinkedHashSet<String>()
            var handledTask = trySignInYebExpGold(queryResponse, manualTaskTitles)
            if (handledTask) {
                JSONObject(AntOrchardRpcCall.queryYebExpGoldMain()).also { refreshed ->
                    if (isYebExpGoldSuccess(refreshed)) {
                        queryResponse = refreshed
                    }
                }
            }

            val taskMap = queryYebExpGoldTaskMap(queryResponse)
            collectYebExpGoldManualTasks(taskMap, manualTaskTitles)
            handledTask = claimPendingYebExpGoldRewards(queryResponse, taskMap) || handledTask

            for ((taskId, task) in taskMap.entries.toList()) {
                if (taskId.isBlank()) {
                    continue
                }

                val title = getYebExpGoldTaskTitle(task, taskId)
                if (isYebExpGoldTaskBlacklisted(title, taskId)) {
                    Log.record(TAG, "跳过黑名单任务[$title]")
                    continue
                }
                val successFlag = "$STATUS_YEB_EXP_GOLD_TASK_PREFIX::$taskId"
                if (Status.hasFlagToday(successFlag)) {
                    continue
                }
                when (task.optString("simplifiedStatus").lowercase()) {
                    "not_done" -> {
                        if (tryCompleteYebExpGoldTask(taskId, task, taskMap)) {
                            handledTask = true
                        } else {
                            manualTaskTitles.add(title)
                        }
                        CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
                    }

                    "not_sign" -> {
                        if (shouldAutoReceiveYebExpGoldTask(task) &&
                            tryCompleteYebExpGoldTask(taskId, task, taskMap)
                        ) {
                            handledTask = true
                            CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
                        } else {
                            manualTaskTitles.add(title)
                        }
                    }
                }
            }

            queryResponse = JSONObject(AntOrchardRpcCall.queryYebExpGoldMain())
            if (isYebExpGoldSuccess(queryResponse)) {
                handledTask = claimPendingYebExpGoldRewards(queryResponse, taskMap) || handledTask
                handledTask = handleYebExpGoldExchange(queryResponse) || handledTask
            } else {
                Log.record(
                    TAG,
                    "余额宝体验金任务刷新失败: ${
                        queryResponse.optString(
                            "resultDesc",
                            queryResponse.toString()
                        )
                    }"
                )
            }

            if (!handledTask && manualTaskTitles.isEmpty()) {
                Log.record(TAG, "余额宝体验金任务: 未发现可自动处理项目")
            }
            if (manualTaskTitles.isNotEmpty()) {
                Log.record(TAG, "余额宝体验金任务待手动完成: ${manualTaskTitles.joinToString("、")}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleYebExpGoldTasks err:", t)
        }
    }

    private fun collectYebExpGoldManualTasks(
        taskMap: Map<String, JSONObject>,
        manualTaskTitles: MutableSet<String>
    ) {
        for ((taskId, task) in taskMap) {
            if (Status.hasFlagToday("$STATUS_YEB_EXP_GOLD_TASK_PREFIX::$taskId")) {
                continue
            }
            val title = getYebExpGoldTaskTitle(task, taskId)
            if (isYebExpGoldTaskBlacklisted(title, taskId)) {
                continue
            }
            if (task.optString("simplifiedStatus").lowercase() == "not_sign" &&
                !shouldAutoReceiveYebExpGoldTask(task)
            ) {
                manualTaskTitles.add(title)
            }
        }
    }

    private fun trySignInYebExpGold(
        queryResponse: JSONObject,
        manualTaskTitles: MutableSet<String>
    ): Boolean {
        if (Status.hasFlagToday(STATUS_YEB_EXP_GOLD_SIGN_DONE)) {
            return false
        }

        val todaySign = getYebExpGoldTodaySignItem(queryResponse) ?: return false
        val signStatus = todaySign.optJSONObject("signInfo")
            ?.optString("signStatus")
            .orEmpty()
            .uppercase()
        if (signStatus != "TO_SIGNED" && signStatus != "UNSIGNED") {
            Status.setFlagToday(STATUS_YEB_EXP_GOLD_SIGN_DONE)
            return false
        }

        val amount = todaySign.optJSONObject("prizeInfo")
            ?.opt("prizeAmount")
            ?.toString()
            .orEmpty()
        val title = if (amount.isBlank()) "余额宝体验金签到" else "余额宝体验金签到(${amount}元)"
        if (TaskBlacklist.isTaskInBlacklist(title)) {
            Log.record(TAG, "跳过黑名单任务[$title]")
            Status.setFlagToday(STATUS_YEB_EXP_GOLD_SIGN_DONE)
            return false
        }

        val signResponse = JSONObject(AntOrchardRpcCall.signInYebExpGold())
        if (!isYebExpGoldSuccess(signResponse)) {
            Log.record(TAG, "余额宝体验金签到失败: ${getYebExpGoldErrorDesc(signResponse)}")
            manualTaskTitles.add(title)
            return false
        }

        logYebExpGoldSignInRewards(amount, signResponse)
        Status.setFlagToday(STATUS_YEB_EXP_GOLD_SIGN_DONE)
        return true
    }

    private fun logYebExpGoldSignInRewards(
        fallbackAmount: String,
        response: JSONObject
    ) {
        val prizeOrderList = response.optJSONObject("resultData")
            ?.optJSONObject("resultData")
            ?.optJSONArray("prizeOrderDTOList")
        if (prizeOrderList != null && prizeOrderList.length() > 0) {
            for (index in 0 until prizeOrderList.length()) {
                val order = prizeOrderList.optJSONObject(index) ?: continue
                val memo = order.optJSONObject("customMemo")
                val amount = memo?.optString("PRIZE_AMOUNT").orEmpty()
                val unit = memo?.optString("PRIZE_UNIT").orEmpty()
                val prizeName = order.optString("prizeName")
                val rewardText = when {
                    amount.isNotBlank() -> amount + unit.ifBlank { "元" }
                    prizeName.isNotBlank() -> prizeName
                    fallbackAmount.isNotBlank() -> fallbackAmount + "元"
                    else -> "成功"
                }
                Log.farm("余额宝体验金💰[签到成功]#$rewardText")
            }
            return
        }

        val rewardText = if (fallbackAmount.isNotBlank()) "${fallbackAmount}元" else "成功"
        Log.farm("余额宝体验金💰[签到成功]#$rewardText")
    }

    private fun getYebExpGoldErrorDesc(response: JSONObject): String {
        return response.optString("resultDesc")
            .ifBlank { response.optString("resultView") }
            .ifBlank { response.optString("errorMessage") }
            .ifBlank { response.optString("memo") }
            .ifBlank { response.toString() }
    }

    private fun getYebExpGoldTodaySignItem(queryResponse: JSONObject): JSONObject? {
        val signList = queryResponse.optJSONObject("resultData")
            ?.optJSONObject("signInData")
            ?.optJSONArray("list")
            ?: return null
        for (index in 0 until signList.length()) {
            val signItem = signList.optJSONObject(index) ?: continue
            val signInfo = signItem.optJSONObject("signInfo")
            val signDateDesc = signInfo?.optString("signDateDesc").orEmpty()
            val displayDate = signItem.optString("displayDate")
            if (signDateDesc == "TODAY" || displayDate.contains("今天")) {
                return signItem
            }
        }
        return null
    }

    private fun shouldAutoReceiveYebExpGoldTask(task: JSONObject): Boolean {
        val buttonText = task.optString("buttonText")
        return buttonText.contains("领取") || buttonText.contains("领奖") || buttonText.contains("领")
    }

    private fun tryCompleteYebExpGoldTask(
        taskId: String,
        task: JSONObject,
        taskMap: MutableMap<String, JSONObject>
    ): Boolean {
        val title = getYebExpGoldTaskTitle(task, taskId)
        if (taskId.isBlank()) {
            return false
        }

        val prepareResponse = JSONObject(AntOrchardRpcCall.queryYebExpGoldMain(true, taskId))
        if (!isYebExpGoldSuccess(prepareResponse)) {
            Log.record(
                TAG,
                "余额宝体验金任务预处理失败[$title]: ${
                    prepareResponse.optString(
                        "resultDesc",
                        prepareResponse.toString()
                    )
                }"
            )
            return false
        }

        collectYebExpGoldTasks(prepareResponse, taskMap)
        val claimedByCompleteList = claimPendingYebExpGoldRewards(
            prepareResponse,
            taskMap
        )
        if (claimedByCompleteList) {
            return true
        }

        val completeResponse = JSONObject(AntOrchardRpcCall.completeYebExpGoldTask(taskId))
        if (!isYebExpGoldSuccess(completeResponse)) {
            queryYebExpGoldTaskById(taskId)?.let { verifiedTask ->
                taskMap[taskId] = verifiedTask
                if (isYebExpGoldTaskReceived(verifiedTask)) {
                    Status.setFlagToday("$STATUS_YEB_EXP_GOLD_TASK_PREFIX::$taskId")
                    return true
                }
            }
            Log.record(
                TAG,
                "余额宝体验金任务领取失败[$title]: ${
                    completeResponse.optString(
                        "resultDesc",
                        completeResponse.toString()
                    )
                }"
            )
            return false
        }

        logYebExpGoldRewards(title, completeResponse)
        Status.setFlagToday("$STATUS_YEB_EXP_GOLD_TASK_PREFIX::$taskId")
        queryYebExpGoldTaskById(taskId)?.let { verifiedTask ->
            taskMap[taskId] = verifiedTask
        }
        return true
    }

    private fun claimPendingYebExpGoldRewards(
        queryResponse: JSONObject,
        taskMap: Map<String, JSONObject>
    ): Boolean {
        val completeList = getYebExpGoldCompleteList(queryResponse)
        var claimed = false
        for (index in 0 until completeList.length()) {
            val rewardItem = completeList.optJSONObject(index) ?: continue
            val taskId = rewardItem.optString("taskId")
            if (taskId.isBlank()) {
                continue
            }
            val successFlag = "$STATUS_YEB_EXP_GOLD_TASK_PREFIX::$taskId"
            if (Status.hasFlagToday(successFlag)) {
                continue
            }

            val title = getYebExpGoldCompletedTitle(rewardItem, taskMap[taskId], taskId)
            if (isYebExpGoldTaskBlacklisted(title, taskId)) {
                Log.record(TAG, "跳过黑名单任务[$title]")
                continue
            }
            val completeResponse = JSONObject(AntOrchardRpcCall.completeYebExpGoldTask(taskId))
            if (isYebExpGoldSuccess(completeResponse)) {
                logYebExpGoldRewards(title, completeResponse)
                Status.setFlagToday(successFlag)
                claimed = true
            } else {
                val verifiedTask = queryYebExpGoldTaskById(taskId)
                if (verifiedTask != null && isYebExpGoldTaskReceived(verifiedTask)) {
                    Status.setFlagToday(successFlag)
                    claimed = true
                    continue
                }
                Log.record(
                    TAG,
                    "余额宝体验金任务领取失败[$title]: ${
                        completeResponse.optString(
                            "resultDesc",
                            completeResponse.toString()
                        )
                    }"
                )
            }
            CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
        }
        return claimed
    }

    private fun getYebExpGoldCompleteList(queryResponse: JSONObject): JSONArray {
        return queryResponse.optJSONObject("resultData")
            ?.optJSONObject("taskData")
            ?.optJSONArray("completeList")
            ?: JSONArray()
    }

    private fun getYebExpGoldCompletedTitle(
        rewardItem: JSONObject,
        task: JSONObject?,
        defaultTitle: String
    ): String {
        return rewardItem.optJSONObject("ext")
            ?.optJSONObject("TASK_MORPHO_DETAIL")
            ?.optString("title")
            .orEmpty()
            .ifBlank {
                rewardItem.optJSONObject("ext")
                    ?.optJSONObject("TASK_MORPHO_DETAIL")
                    ?.optString("taskMainTitle")
                    .orEmpty()
            }
            .ifBlank { task?.let { getYebExpGoldTaskTitle(it, defaultTitle) }.orEmpty() }
            .ifBlank { defaultTitle }
    }

    private fun handleYebExpGoldExchange(queryResponse: JSONObject): Boolean {
        if (Status.hasFlagToday(STATUS_YEB_EXP_GOLD_EXCHANGE_DONE)) {
            return false
        }

        val resultData = queryResponse.optJSONObject("resultData") ?: return false
        val balanceText = resultData.optString("balance")
        val balance = balanceText.toDoubleOrNull() ?: return false
        val threshold = when (val thresholdValue = resultData.opt("subThreshold")) {
            is Number -> thresholdValue.toDouble()
            is String -> thresholdValue.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val thresholdText = resultData.opt("subThreshold")?.toString().orEmpty()

        if (balance <= 0.0) {
            Status.setFlagToday(STATUS_YEB_EXP_GOLD_EXCHANGE_DONE)
            return false
        }

        if (threshold > 0.0 && balance < threshold) {
            Log.record(
                TAG,
                "余额宝体验金未达兑换门槛: 当前$balanceText，最低需${thresholdText.ifBlank { threshold.toString() }}"
            )
            Status.setFlagToday(STATUS_YEB_EXP_GOLD_EXCHANGE_DONE)
            return false
        }

        val trialAssetResponse = JSONObject(AntOrchardRpcCall.queryYebTrialAsset())
        if (!isYebExpGoldSuccess(trialAssetResponse)) {
            Log.record(
                TAG,
                "余额宝体验金资产查询失败: ${
                    trialAssetResponse.optString(
                        "resultDesc",
                        trialAssetResponse.toString()
                    )
                }"
            )
            return false
        }

        val trialInfo = getYebTrialInfo(trialAssetResponse)
        if (trialInfo == null) {
            Log.record(TAG, "余额宝体验金兑换缺少试用资产信息")
            return false
        }

        val campId = trialInfo.optString("promoCampId")
        val prizeId = trialInfo.optString("promoPrizeId")
        if (campId.isBlank() || prizeId.isBlank()) {
            Log.record(TAG, "余额宝体验金兑换缺少活动参数")
            return false
        }

        val exchangeResponse = JSONObject(
            AntOrchardRpcCall.exchangeYebExpGold(
                campId = campId,
                prizeId = prizeId,
                exchangeAmount = balanceText
            )
        )
        if (!isYebExpGoldSuccess(exchangeResponse)) {
            Log.record(
                TAG,
                "余额宝体验金兑换失败: ${
                    exchangeResponse.optString(
                        "resultDesc",
                        exchangeResponse.toString()
                    )
                }"
            )
            return false
        }

        val couponId = exchangeResponse.optJSONObject("result")
            ?.optString("equityNo")
            .orEmpty()
        if (couponId.isBlank()) {
            Log.record(TAG, "余额宝体验金兑换成功但缺少激活凭证")
            return false
        }

        val activeResponse = JSONObject(AntOrchardRpcCall.activeYebTrial(couponId))
        if (!isYebExpGoldSuccess(activeResponse)) {
            Log.record(
                TAG,
                "余额宝体验金激活失败: ${
                    activeResponse.optString(
                        "resultDesc",
                        activeResponse.toString()
                    )
                }"
            )
            return false
        }

        val amountText = activeResponse.optJSONObject("amount")
            ?.opt("amount")
            ?.toString()
            .orEmpty()
            .ifBlank { balanceText }
        val confirmDate = activeResponse.optString("confirmDate")
        val profitDate = activeResponse.optString("profitDate")
        val extraInfo = buildString {
            if (confirmDate.isNotBlank()) {
                append("[确认:$confirmDate]")
            }
            if (profitDate.isNotBlank()) {
                append("[收益:$profitDate]")
            }
        }
        Log.farm("余额宝体验金💰[兑换激活]#${amountText}元$extraInfo")
        Status.setFlagToday(STATUS_YEB_EXP_GOLD_EXCHANGE_DONE)
        return true
    }

    private fun getYebTrialInfo(trialAssetResponse: JSONObject): JSONObject? {
        val trialInfoList = trialAssetResponse.optJSONArray("trialInfoList") ?: return null
        for (index in 0 until trialInfoList.length()) {
            val trialInfo = trialInfoList.optJSONObject(index) ?: continue
            if (trialInfo.optString("promoCampId").isNotBlank() &&
                trialInfo.optString("promoPrizeId").isNotBlank()
            ) {
                return trialInfo
            }
        }
        return null
    }

    private fun queryYebExpGoldTaskMap(
        fallbackQueryResponse: JSONObject
    ): LinkedHashMap<String, JSONObject> {
        val taskMap = LinkedHashMap<String, JSONObject>()
        val taskListResponse = JSONObject(AntOrchardRpcCall.queryYebExpGoldTaskList())
        if (isYebExpGoldSuccess(taskListResponse)) {
            val taskDetailList = taskListResponse.optJSONObject("result")
                ?.optJSONArray("taskDetailList")
            if (taskDetailList != null) {
                for (index in 0 until taskDetailList.length()) {
                    val task = taskDetailList.optJSONObject(index) ?: continue
                    val taskId = task.optString("taskId")
                    if (taskId.isBlank() || !task.has("simplifiedStatus")) {
                        continue
                    }
                    taskMap[taskId] = task
                }
            }
        } else {
            Log.record(
                TAG,
                "余额宝体验金任务列表查询失败: ${getYebExpGoldErrorDesc(taskListResponse)}"
            )
        }

        collectYebExpGoldTasks(fallbackQueryResponse, taskMap)
        return taskMap
    }

    private fun queryYebExpGoldTaskById(taskId: String): JSONObject? {
        if (taskId.isBlank()) {
            return null
        }

        return try {
            val queryResponse = JSONObject(AntOrchardRpcCall.queryYebExpGoldTaskById(taskId))
            if (!isYebExpGoldSuccess(queryResponse)) {
                return null
            }

            val taskDetailList = queryResponse.optJSONObject("result")
                ?.optJSONArray("taskDetailList")
                ?: return null
            for (index in 0 until taskDetailList.length()) {
                val task = taskDetailList.optJSONObject(index) ?: continue
                if (taskId == task.optString("taskId")) {
                    return task
                }
            }
            null
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "queryYebExpGoldTaskById err:", t)
            null
        }
    }

    private fun collectYebExpGoldTasks(
        node: Any?,
        taskMap: MutableMap<String, JSONObject>
    ) {
        when (node) {
            is JSONObject -> {
                val taskId = node.optString("taskId")
                if (taskId.isNotBlank() && node.has("simplifiedStatus")) {
                    taskMap.putIfAbsent(taskId, node)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectYebExpGoldTasks(node.opt(key), taskMap)
                }
            }

            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectYebExpGoldTasks(node.opt(index), taskMap)
                }
            }
        }
    }

    private fun isYebExpGoldSuccess(jo: JSONObject): Boolean {
        return jo.optBoolean("success") || jo.optString("resultCode") == "100"
    }

    private fun isYebExpGoldTaskReceived(task: JSONObject): Boolean {
        val simplifiedStatus = task.optString("simplifiedStatus").lowercase()
        val taskProcessStatus = task.optString("taskProcessStatus").uppercase()
        return simplifiedStatus == "complete" || taskProcessStatus == "RECEIVE_SUCCESS"
    }

    private fun isYebExpGoldTaskBlacklisted(
        title: String,
        taskId: String
    ): Boolean {
        return TaskBlacklist.isTaskInBlacklist(title) ||
            (taskId.isNotBlank() && TaskBlacklist.isTaskInBlacklist(taskId))
    }

    private fun getYebExpGoldTaskTitle(
        task: JSONObject,
        defaultTitle: String
    ): String {
        return task.optString("title")
            .ifBlank { task.optString("taskMainTitle") }
            .ifBlank { task.optJSONObject("taskExtProps")?.optString("title").orEmpty() }
            .ifBlank { defaultTitle }
    }

    private fun logYebExpGoldRewards(
        title: String,
        response: JSONObject
    ) {
        val promoSdkResultList = response.optJSONArray("resultObj")
        if (promoSdkResultList != null && promoSdkResultList.length() > 0) {
            val rewardNames = ArrayList<String>()
            for (resultIndex in 0 until promoSdkResultList.length()) {
                val resultItem = promoSdkResultList.optJSONObject(resultIndex) ?: continue
                val prizeSendDetails = resultItem.optJSONArray("prizeSendDetails") ?: continue
                for (detailIndex in 0 until prizeSendDetails.length()) {
                    val detail = prizeSendDetails.optJSONObject(detailIndex) ?: continue
                    val prizeName = detail.optJSONObject("prizeBaseInfo")
                        ?.optString("prizeName")
                        .orEmpty()
                        .ifBlank {
                            detail.optJSONObject("extInfo")
                                ?.optString("promoPrizeName")
                                .orEmpty()
                        }
                        .ifBlank {
                            detail.optJSONObject("extInfo")
                                ?.optString("title")
                                .orEmpty()
                        }
                    if (prizeName.isNotBlank()) {
                        rewardNames.add(prizeName)
                    }
                }
            }
            if (rewardNames.isNotEmpty()) {
                rewardNames.forEach { prizeName ->
                    Log.farm("余额宝体验金💰[$title]#$prizeName")
                }
                return
            }
        }

        val prizeSendOrderList = response.optJSONObject("result")
            ?.optJSONArray("prizeSendOrderList")
        if (prizeSendOrderList != null && prizeSendOrderList.length() > 0) {
            for (index in 0 until prizeSendOrderList.length()) {
                val prizeOrder = prizeSendOrderList.optJSONObject(index) ?: continue
                val prizeName = prizeOrder.optString("prizeName")
                if (prizeName.isNotBlank()) {
                    Log.farm("余额宝体验金💰[$title]#$prizeName")
                } else {
                    Log.farm("余额宝体验金💰[$title]")
                }
            }
            return
        }
        Log.farm("余额宝体验金💰[$title]")
    }

    // 辅助方法：施肥后检测肥料礼盒
    private fun checkFertilizerBox(currentPlantScene: String) {
        extraInfoGet(from = "water", scene = currentPlantScene)
    }

    /**
     * 获取额外信息（包含每日肥料、施肥礼盒）
     * @param from "entry" 或 "water"
     */
    private fun extraInfoGet(from: String = "entry", scene: String = currentPlantScene) {
        try {
            val source = if (from == "entry") ORCHARD_SOURCE else getSceneSource(scene)
            val response = AntOrchardRpcCall.extraInfoGet(from, source)
            val jo = JSONObject(response)

            if (jo.getString("resultCode") == "100") {
                val data = jo.optJSONObject("data") ?: return
                val extraData = data.optJSONObject("extraData") ?: return
                val fertilizerPacket = extraData.optJSONObject("fertilizerPacket") ?: return

                // 状态为 waitTake 时领取
                if (fertilizerPacket.optString("status") == "todayFertilizerWaitTake") {
                    val num = fertilizerPacket.optInt("todayFertilizerNum")
                    val setResponse = JSONObject(AntOrchardRpcCall.extraInfoSet(source))
                    if (setResponse.getString("resultCode") == "100") {
                        val typeName = if (from == "water") "礼盒" else "每日"
                        Log.farm("领取${typeName}肥料💩[${num}g]")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "extraInfoGet err:", t)
        }
    }

    private fun checkLotteryPlus() {
        try {
            if (treeLevel == null) return
            val response = AntOrchardRpcCall.querySubplotsActivity(treeLevel!!)
            val json = JSONObject(response)
            if (!ResChecker.checkRes(TAG, json)) return

            val subplots = json.optJSONArray("subplotsActivityList") ?: return
            for (i in 0 until subplots.length()) {
                val activity = subplots.getJSONObject(i)
                if (activity.optString("activityType") == "LOTTERY_PLUS") {
                    val extendStr = activity.optString("extend")
                    if (extendStr.isNotEmpty()) {
                        val lotteryPlusInfo = JSONObject(extendStr)
                        drawLotteryPlus(lotteryPlusInfo)
                    }
                    break
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "checkLotteryPlus err", t)
        }
    }

    private fun drawLotteryPlus(lotteryPlusInfo: JSONObject) {
        try {
            if (!lotteryPlusInfo.has("userSevenDaysGiftsItem")) return

            val itemId = lotteryPlusInfo.getString("itemId")
            val jo = lotteryPlusInfo.getJSONObject("userSevenDaysGiftsItem")
            val ja = jo.getJSONArray("userEverydayGiftItems")

            for (i in 0 until ja.length()) {
                val jo2 = ja.getJSONObject(i)
                if (jo2.getString("itemId") == itemId) {
                    if (!jo2.getBoolean("received")) {
                        Log.record(TAG, "七日礼包: 发现未领取奖励 (itemId=$itemId)")
                        val jo3 = JSONObject(AntOrchardRpcCall.drawLottery())
                        if (jo3.getString("resultCode") == "100") {
                            val userEverydayGiftItems = jo3.getJSONObject("lotteryPlusInfo")
                                .getJSONObject("userSevenDaysGiftsItem")
                                .getJSONArray("userEverydayGiftItems")

                            for (j in 0 until userEverydayGiftItems.length()) {
                                val jo4 = userEverydayGiftItems.getJSONObject(j)
                                if (jo4.getString("itemId") == itemId) {
                                    val awardCount = jo4.optInt("awardCount", 1)
                                    Log.farm("七日礼包🎁[获得肥料]#${awardCount}g")
                                    break
                                }
                            }
                        } else {
                            Log.record(TAG, jo3.toString())
                        }
                    } else {
                        Log.record(TAG, "七日礼包: 今日已领取")
                    }
                    break
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "drawLotteryPlus err:", t)
        }
    }

    private fun doOrchardDailyTask(userId: String) {
        try {
            val response = AntOrchardRpcCall.orchardListTask()
            val responseJson = JSONObject(response)

            if (responseJson.optString("resultCode") != "100") {
                Log.error("doOrchardDailyTask响应异常", response)
                return
            }

            val inTeam = responseJson.optBoolean("inTeam", false)
            Log.record(TAG, if (inTeam) "当前为农场 team 模式（合种/帮帮种已开启）" else "当前为普通单人农场模式")

            if (responseJson.has("signTaskInfo")) {
                val signTaskInfo = responseJson.getJSONObject("signTaskInfo")
                orchardSign(signTaskInfo)
            }

            logLinkedTaskHints(responseJson)

            val taskList = responseJson.getJSONArray("taskList")
            for (i in 0 until taskList.length()) {
                val task = taskList.getJSONObject(i)
                if (task.optString("taskStatus") != "TODO") continue

                val actionType = task.optString("actionType")
                val sceneCode = task.optString("sceneCode")
                val taskId = task.optString("taskId")
                val groupId = task.optString("groupId")

                val title = if (task.has("taskDisplayConfig")) {
                    task.getJSONObject("taskDisplayConfig").optString("title", "未知任务")
                } else {
                    "未知任务"
                }

                val groupIdInBlacklist = TaskBlacklist.isTaskInBlacklist(groupId)
                val titleInBlacklist = TaskBlacklist.isTaskInBlacklist(title)
                if (groupIdInBlacklist || titleInBlacklist) {
                    Log.record(TAG, "跳过黑名单任务[$title] groupId=$groupId")
                    continue
                }

                when (actionType) {
                    "XLIGHT" -> {
                        executeXLightTask(task, title)
                    }
                    "VISIT" -> {
                        if (isXLightTask(task)) {
                            executeXLightTask(task, title)
                        } else {
                            Log.record(TAG, "农场任务⏭️[$title] action=VISIT 暂无稳定RPC，跳过")
                        }
                    }
                    "TRIGGER", "ADD_HOME", "PUSH_SUBSCRIBE" -> {
                        val finishResponse = JSONObject(AntOrchardRpcCall.finishTask(userId, sceneCode, taskId, ORCHARD_SOURCE))
                        if (ResChecker.checkRes(TAG, finishResponse)) {
                            Log.farm("农场任务🧾[$title]")
                        } else {
                            Log.error(TAG, "农场任务🧾[$title]${finishResponse.optString("desc")}")
                        }
                    }
                    "ANTFARM_COLLECT_MANURE" -> {
                        // actionType=ANTFARM_COLLECT_MANURE(taskStatus=TODO) 时，需要调用 com.alipay.antfarm.collectManurePot(sceneCode=ORCHARD)
                        collectOrchardManurePotIfNeeded(responseJson)
                    }
                    "ANTFOREST_DEFOLIATION" -> {
                        Log.record(TAG, "农场联动任务⏭️[森林落叶] 依赖森林模块完成后再领奖")
                    }
                    "MULTI_STAGE", "P2P_NEW", "SYSTEM_SWITCH", "JUMP", "GAME_CENTER" -> {
                        Log.record(TAG, "农场任务⏭️[$title] action=$actionType 暂未自动化，已兼容跳过")
                    }
                    else -> {
                        Log.record(TAG, "农场任务⏭️[$title] action=$actionType 暂未支持，已跳过")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doOrchardDailyTask err:", t)
        }
    }

    private fun collectOrchardManurePotIfNeeded(listTaskJson: JSONObject) {
        try {
            if (skipManurePotCollectThisRound) {
                Log.record(TAG, "庄园鸡屎💩任务：本轮已触发“肥料太少”保护，跳过重复收取")
                return
            }
            val manureFactory = listTaskJson.optJSONObject("manureFactory") ?: run {
                Log.record(TAG, "庄园鸡屎💩任务：缺少 manureFactory 字段，跳过")
                return
            }
            if (!manureFactory.optBoolean("canCollect", false)) {
                Log.record(TAG, "庄园鸡屎💩任务：当前不可收取（canCollect=false）")
                return
            }

            val manure = manureFactory.optJSONObject("manure")
            val potList = manure?.optJSONArray("manurePotList")
            val candidateNos = LinkedHashSet<String>()
            val fallbackNos = LinkedHashSet<String>()
            val allPotNos = LinkedHashSet<String>()
            var totalPotNum = 0.0
            if (potList != null) {
                for (i in 0 until potList.length()) {
                    val pot = potList.optJSONObject(i) ?: continue
                    val potNo = pot.optString("manurePotNO").trim()
                    val potNum = pot.optDouble("manurePotNum", 0.0)
                    totalPotNum += potNum.coerceAtLeast(0.0)
                    if (potNo.isEmpty()) continue
                    allPotNos.add(potNo)
                    if (potNum > 0.0) {
                        fallbackNos.add(potNo)
                    }
                    if (potNum > 1.0) {
                        candidateNos.add(potNo)
                    }
                }
            }
            val collectTargets: List<String> = when {
                allPotNos.size >= 3 && candidateNos.size >= 3 -> candidateNos.toList()
                allPotNos.size < 3 && totalPotNum > 3.0 && fallbackNos.isNotEmpty() -> fallbackNos.toList()
                else -> emptyList()
            }
            if (collectTargets.isEmpty()) {
                if (allPotNos.size >= 3) {
                    Log.record(
                        TAG,
                        "庄园鸡屎💩任务：已识别${allPotNos.size}个池子，但未满足至少3个池子都>1g，跳过收取"
                    )
                } else {
                    Log.record(
                        TAG,
                        "庄园鸡屎💩任务：未识别到完整三池结构，当前总量${"%.1f".format(totalPotNum)}g未达到>3g兜底门槛，跳过收取"
                    )
                }
                return
            }

            val source = getSceneSource()
            val collectResp = JSONObject(AntOrchardRpcCall.collectManurePot(collectTargets.joinToString(","), source))
            if (ResChecker.checkRes(TAG, collectResp)) {
                val collected = collectResp.optInt("collectManurePotNum", 0)
                if (collected > 0) {
                    Log.farm("庄园鸡屎💩[收取肥料]#${collected}g")
                } else {
                    Log.record(TAG, "庄园鸡屎💩任务：已触发收取，但本次为0g")
                }
            } else {
                val resultCode = collectResp.optString("resultCode").ifBlank { collectResp.optString("code") }
                val desc = collectResp.optString("memo")
                    .ifBlank { collectResp.optString("desc", collectResp.optString("resultDesc")) }
                if (resultCode == "G03" || desc.contains("肥料太少")) {
                    skipManurePotCollectThisRound = true
                    Log.record(TAG, "庄园鸡屎💩任务：肥料太少啦，等一会再收吧；本轮不再重试")
                    return
                }
                Log.record(TAG, "庄园鸡屎💩任务收取失败: ${collectResp.toString()}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "collectOrchardManurePotIfNeeded err:", t)
        }
    }

    private fun executeXLightTask(task: JSONObject, title: String): Boolean {
        try {
            val taskDisplayConfig = task.optJSONObject("taskDisplayConfig") ?: return false
            val targetUrl = taskDisplayConfig.optString("targetUrl")
            if (targetUrl.isEmpty()) {
                Log.record(TAG, "农场广告任务⏭️[$title] 缺少 targetUrl")
                return false
            }

            val pageUrl = UrlUtil.getFullNestedUrl(targetUrl, "url")
                ?: UrlUtil.getParamValue(targetUrl, "url")
                ?: targetUrl.takeIf { it.startsWith("http") }
            if (pageUrl.isNullOrEmpty()) {
                Log.record(TAG, "农场广告任务⏭️[$title] 无法解析 pageUrl")
                return false
            }

            val spaceCode = UrlUtil.extractParamFromUrl(pageUrl, "spaceCodeFeeds")
                ?: UrlUtil.getParamValue(targetUrl, "spaceCodeFeeds")
            val referToken = UrlUtil.extractParamFromUrl(pageUrl, "tokenFeeds")
                ?: UrlUtil.getParamValue(targetUrl, "tokenFeeds")
            if (spaceCode.isNullOrEmpty()) {
                Log.record(TAG, "农场广告任务⏭️[$title] 无法解析 spaceCodeFeeds")
                return false
            }

            val response = XLightRpcCall.xlightPlugin(
                pageUrl = pageUrl,
                pageFrom = XLIGHT_PAGE_FROM,
                session = buildXLightSession(),
                spaceCode = spaceCode,
                referToken = referToken
            )
            val responseJson = JSONObject(response)
            val playingResult = responseJson.optJSONObject("resData")?.optJSONObject("playingResult")
                ?: responseJson.optJSONObject("playingResult")
            if (playingResult == null) {
                Log.record(TAG, "农场广告任务⏭️[$title] 未返回 playingResult")
                return false
            }

            val playingBizId = playingResult.optString("playingBizId")
            val eventRewardInfoList = playingResult.optJSONObject("eventRewardDetail")
                ?.optJSONArray("eventRewardInfoList")
            if (playingBizId.isEmpty() || eventRewardInfoList == null || eventRewardInfoList.length() == 0) {
                Log.record(TAG, "农场广告任务⏭️[$title] 未返回可完成事件")
                return false
            }

            var finishedCount = 0
            for (i in 0 until eventRewardInfoList.length()) {
                val playEventInfo = eventRewardInfoList.optJSONObject(i) ?: continue
                if (playEventInfo.optString("playingEventType") != "BROWSE") {
                    continue
                }
                val finishResult = JSONObject(
                    XLightRpcCall.finishTask(
                        playBizId = playingBizId,
                        playEventInfo = playEventInfo
                    )
                )
                if (!ResChecker.checkRes(TAG, finishResult)) {
                    Log.record(TAG, "农场广告任务⏭️[$title] 浏览事件领取失败:${finishResult.optString("desc", finishResult.optString("errMsg"))}")
                    return finishedCount > 0
                }
                finishedCount++
                CoroutineUtils.sleepCompat(executeIntervalInt.toLong())
            }

            if (finishedCount > 0) {
                Log.farm("农场广告任务📺[$title] 完成${finishedCount}次浏览奖励")
                return true
            }

            Log.record(TAG, "农场广告任务⏭️[$title] 未找到 BROWSE 事件")
            return false
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "executeXLightTask err:", t)
            return false
        }
    }

    private fun isXLightTask(task: JSONObject): Boolean {
        val taskDisplayConfig = task.optJSONObject("taskDisplayConfig") ?: return false
        val targetUrl = taskDisplayConfig.optString("targetUrl")
        return targetUrl.contains("spaceCodeFeeds=") && targetUrl.contains("tokenFeeds=")
    }

    private fun logLinkedTaskHints(responseJson: JSONObject) {
        val convertToManureTask = responseJson.optJSONObject("convertToManureTask")
        if (convertToManureTask != null && convertToManureTask.optBoolean("showTask", false)) {
            val taskStatus = convertToManureTask.optString("taskStatus")
            if (taskStatus == "TODO") {
                Log.record(TAG, "农场联动任务⏭️[${convertToManureTask.optString("title")}] 需由森林模块完成")
            }
        }
    }

    private fun getSceneSource(scene: String = currentPlantScene): String {
        return if (scene == "yeb") YEB_SOURCE else ORCHARD_SOURCE
    }

    private fun tryReceiveSpreadManureActivityAward(indexJson: JSONObject) {
        try {
            // manureTaskAwardReceive=false + spreadManureStage.status=FINISHED 时，可通过 antiep.receiveTaskAward 领奖
            val alreadyReceived = indexJson.optBoolean("manureTaskAwardReceive", true)
            val stage = indexJson.optJSONObject("spreadManureActivity")
                ?.optJSONObject("spreadManureStage")
                ?: return
            val status = stage.optString("status")
            if (alreadyReceived || status != "FINISHED") {
                return
            }
            val sceneCode = stage.optString("sceneCode")
            val taskType = stage.optString("taskType")
            if (sceneCode.isBlank() || taskType.isBlank()) {
                Log.record(TAG, "丰收奖励🎁字段缺失: sceneCode=$sceneCode taskType=$taskType")
                return
            }
            val awardCount = stage.optInt("awardCount", 0)
            val source = getSceneSource(indexJson.optString("currentPlantScene", currentPlantScene))
            val awardResp = JSONObject(AntOrchardRpcCall.receiveTaskAward(sceneCode, taskType, source))
            if (ResChecker.checkRes(TAG, awardResp)) {
                Log.farm("丰收奖励🎁[领取成功]#${awardCount}g肥料")
            } else {
                Log.record(TAG, "丰收奖励🎁领取失败: ${awardResp.toString()}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "tryReceiveSpreadManureActivityAward err:", t)
        }
    }

    private fun tryReceiveSpreadManureActivityAwardByQueryIndex() {
        try {
            val refreshed = JSONObject(AntOrchardRpcCall.orchardIndex())
            if (refreshed.optString("resultCode") != "100") {
                return
            }
            tryReceiveSpreadManureActivityAward(refreshed)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "tryReceiveSpreadManureActivityAwardByQueryIndex err:", t)
        }
    }

    private fun buildXLightSession(): String {
        return "u_${RandomUtil.getRandomString(5)}_${RandomUtil.getRandomString(5)}"
    }

    private fun orchardSign(signTaskInfo: JSONObject) {
        try {
            val currentSignItem = signTaskInfo.getJSONObject("currentSignItem")
            if (!currentSignItem.getBoolean("signed")) {
                val joSign = JSONObject(AntOrchardRpcCall.orchardSign())
                if (joSign.getString("resultCode") == "100") {
                    val awardCount = joSign.getJSONObject("signTaskInfo")
                        .getJSONObject("currentSignItem")
                        .getInt("awardCount")
                    Log.farm("农场签到📅[获得肥料]#${awardCount}g")
                } else {
                    Log.record(TAG, joSign.toString())
                }
            } else {
                Log.record(TAG, "农场今日已签到")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "orchardSign err:", t)
        }
    }

    private fun smashedGoldenEgg(count: Int) {
        try {
            val response = AntOrchardRpcCall.smashedGoldenEgg(count)
            val jo = JSONObject(response)

            if (ResChecker.checkRes(TAG, jo)) {
                val batchSmashedList = jo.getJSONArray("batchSmashedList")
                for (i in 0 until batchSmashedList.length()) {
                    val smashedItem = batchSmashedList.getJSONObject(i)
                    val manureCount = smashedItem.optInt("manureCount", 0)
                    val jackpot = smashedItem.optBoolean("jackpot", false)
                    Log.farm("砸出肥料 🎖️: $manureCount g" + if (jackpot) "（触发大奖）" else "")
                }
            } else {
                Log.record(TAG, jo.optString("resultDesc", "未知错误"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "smashedGoldenEgg err:", t)
        }
    }

    private fun triggerTbTask() {
        try {
            val response = AntOrchardRpcCall.orchardListTask()
            val jo = JSONObject(response)

            if (jo.getString("resultCode") == "100") {
                val jaTaskList = jo.getJSONArray("taskList")
                for (i in 0 until jaTaskList.length()) {
                    val jo2 = jaTaskList.getJSONObject(i)
                    if (jo2.getString("taskStatus") != "FINISHED") continue

                    val title = jo2.getJSONObject("taskDisplayConfig").getString("title")
                    val awardCount = jo2.optInt("awardCount", 0)
                    val taskId = jo2.getString("taskId")
                    val taskPlantType = jo2.getString("taskPlantType")

                    val jo3 = claimTaskReward(taskId, taskPlantType)
                    if (jo3 != null && jo3.getString("resultCode") == "100") {
                        Log.farm("领取奖励🎖️[$title]#${awardCount}g肥料")
                    } else {
                        Log.record(TAG, "领取奖励失败[$title] ${jo3?.toString() ?: "无可用响应"}")
                    }
                }
            } else {
                Log.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "triggerTbTask err:", t)
        }
    }

    private fun claimTaskReward(taskId: String, taskPlantType: String): JSONObject? {
        val sourceCandidates = linkedSetOf(getSceneSource(), ORCHARD_SOURCE, YEB_SOURCE)
        var lastResponse: JSONObject? = null
        for (source in sourceCandidates) {
            val response = JSONObject(AntOrchardRpcCall.triggerTbTask(taskId, taskPlantType, source))
            lastResponse = response
            if (response.optString("resultCode") == "100") {
                return response
            }
        }
        return lastResponse
    }

    private fun receiveOrchardVisitAward() {
        try {
            val awardSources = listOf(
                Pair("tmall", "upgrade_tmall_exchange_task"),
                Pair("antfarm", "ANTFARM_ORCHARD_PLUS"),
                Pair("widget", "widget_shoufei")
            )
            var hasAwardReceived = false

            for ((diversionSource, source) in awardSources) {
                val response = AntOrchardRpcCall.receiveOrchardVisitAward(diversionSource, source)
                val jo = JSONObject(response)

                if (!ResChecker.checkRes(TAG, response)) {
                    continue
                }

                val awardList = jo.optJSONArray("orchardVisitAwardList")
                if (awardList == null || awardList.length() == 0) {
                    continue
                }

                for (i in 0 until awardList.length()) {
                    val awardObj = awardList.optJSONObject(i) ?: continue
                    val awardCount = awardObj.optInt("awardCount", 0)
                    val awardDesc = awardObj.optString("awardDesc", "")
                    Log.farm("回访奖励[$awardDesc] $awardCount g肥料")
                    hasAwardReceived = true
                }
            }

            if (hasAwardReceived) {
                Status.setFlagToday(StatusFlags.FLAG_ANTORCHARD_WIDGET_DAILY_AWARD)
                Log.record(TAG, "回访奖励领取完成")
            } else {
                Log.record(TAG, "回访奖励已全部领取或无可领取奖励")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveOrchardVisitAward err:", t)
        }
    }

    private fun limitedTimeChallenge() {
        try {
            val wua = SecurityBodyHelper.getSecurityBodyData(4).toString()
            val response = AntOrchardRpcCall.orchardSyncIndex(wua)
            val root = JSONObject(response)
            if (!ResChecker.checkRes(TAG, root)) return

            val challenge = root.optJSONObject("limitedTimeChallenge") ?: return
            val currentRound = challenge.optInt("currentRound", 0)
            if (currentRound <= 0) return

            val taskArray = challenge.optJSONArray("limitedTimeChallengeTasks") ?: return
            val targetIdx = currentRound - 1
            if (targetIdx !in 0 until taskArray.length()) return

            val roundTask = taskArray.optJSONObject(targetIdx) ?: return
            val ongoing = roundTask.optBoolean("ongoing", false)
            val MtaskStatus = roundTask.optString("taskStatus")
            val MtaskId = roundTask.optString("taskId")
            val MawardCount = roundTask.optInt("awardCount", 0)

            if (MtaskStatus == "FINISHED" && ongoing) {
                val awardResp = AntOrchardRpcCall.receiveTaskAward("ORCHARD_LIMITED_TIME_CHALLENGE", MtaskId)
                val joo = JSONObject(awardResp)
                if (ResChecker.checkRes(TAG, joo)) {
                    Log.farm("第 $currentRound 轮 限时任务🎁[肥料 * $MawardCount]")
                }
                return
            }

            if (roundTask.optString("taskStatus") != "TODO") return
            val childTasks = roundTask.optJSONArray("childTaskList") ?: return

            for (i in 0 until childTasks.length()) {
                val child = childTasks.optJSONObject(i) ?: continue
                val childTaskId = child.optString("taskId", "未知ID")
                val actionType = child.optString("actionType")
                val groupId = child.optString("groupId")
                val taskStatus = child.optString("taskStatus")
                val sceneCode = child.optString("sceneCode")
                val taskRequire = child.optInt("taskRequire", 0)
                val taskProgress = child.optInt("taskProgress", 0)

                if (taskStatus != "TODO") continue
                if (groupId == "GROUP_1_STEP_3_GAME_WZZT_30s") continue
                if (groupId == "GROUP_1_STEP_2_GAME_WZZT_30s") continue

                when (actionType) {
                    "SPREAD_MANURE" -> {
                        val need = taskRequire - taskProgress
                        if (need > 0) {
                            repeat(need) {
                                val w = SecurityBodyHelper.getSecurityBodyData(4).toString()
                                val r = AntOrchardRpcCall.orchardSpreadManure(w, "ch_appcenter__chsub_9patch")
                                if (JSONObject(r).optString("resultCode") != "100") return
                            }
                        }
                    }
                    "GAME_CENTER" -> {
                        val r = AntOrchardRpcCall.noticeGame("2021004165643274")
                        if (ResChecker.checkRes(TAG, JSONObject(r))) {
                            Log.record(TAG, "游戏任务触发成功")
                        }
                    }
                    "VISIT" -> {
                        val displayCfg = child.optJSONObject("taskDisplayConfig") ?: continue
                        val targetUrl = displayCfg.optString("targetUrl", "")
                        if (targetUrl.isEmpty()) continue

                        val finalUrl = UrlUtil.getFullNestedUrl(targetUrl, "url") ?: ""
                        val spaceCodeFeeds = if (finalUrl.isNotEmpty()) UrlUtil.extractParamFromUrl(finalUrl, "spaceCodeFeeds") else null
                        val finalSpaceCode = spaceCodeFeeds ?: UrlUtil.getParamValue(targetUrl, "spaceCodeFeeds") ?: ""
                        if (finalSpaceCode.isEmpty()) continue

                        val pageFrom = "ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html"
                        val session = "u_41ba1_2f33e"
                        val r = XLightRpcCall.xlightPlugin(finalUrl, pageFrom, session, finalSpaceCode)
                        val jr = JSONObject(r)

                        val playingResult = jr.optJSONObject("resData")?.optJSONObject("playingResult") ?: jr.optJSONObject("playingResult")
                        if (playingResult == null) continue

                        val playingBizId = playingResult.optString("playingBizId", "")
                        val eventRewardDetail = playingResult.optJSONObject("eventRewardDetail")
                        val infoListArray = eventRewardDetail?.optJSONArray("eventRewardInfoList")
                        if (infoListArray == null || infoListArray.length() == 0) continue

                        val playEventInfo = infoListArray.getJSONObject(0)
                        val finishResult = XLightRpcCall.finishTask(playingBizId, playEventInfo, sceneCode, groupId)
                        if (ResChecker.checkRes(TAG, JSONObject(finishResult))) {
                            Log.record(TAG, "浏览广告任务完成")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "limitedTimeChallenge err:", t)
        }
    }

    private fun querySubplotsActivity(taskRequire: Int) {
        try {
            val level = treeLevel
            if (level.isNullOrEmpty() || level == "0") return

            val response = AntOrchardRpcCall.querySubplotsActivity(level)
            val jo = JSONObject(response)

            if (jo.getString("resultCode") == "100") {
                val subplotsActivityList = jo.getJSONArray("subplotsActivityList")
                for (i in 0 until subplotsActivityList.length()) {
                    val jo2 = subplotsActivityList.getJSONObject(i)
                    if (jo2.getString("activityType") != "WISH") continue

                    val activityId = jo2.getString("activityId")
                    when (jo2.getString("status")) {
                        "NOT_STARTED" -> {
                            val extend = jo2.getString("extend")
                            val jo3 = JSONObject(extend)
                            val wishActivityOptionList = jo3.getJSONArray("wishActivityOptionList")
                            var optionKey: String? = null

                            for (j in 0 until wishActivityOptionList.length()) {
                                val jo4 = wishActivityOptionList.getJSONObject(j)
                                if (taskRequire == jo4.getInt("taskRequire")) {
                                    optionKey = jo4.getString("optionKey")
                                    break
                                }
                            }

                            if (optionKey != null) {
                                val jo5 = JSONObject(AntOrchardRpcCall.triggerSubplotsActivity(activityId, "WISH", optionKey))
                                if (jo5.getString("resultCode") == "100") {
                                    Log.farm("农场许愿✨[每日施肥$taskRequire 次]")
                                } else {
                                    Log.record(TAG, jo5.getString("resultDesc"))
                                }
                            }
                        }
                        "FINISHED" -> {
                            val jo3 = JSONObject(AntOrchardRpcCall.receiveOrchardRights(activityId, "WISH"))
                            if (jo3.getString("resultCode") == "100") {
                                Log.farm("许愿奖励✨[肥料${jo3.getInt("amount")}g]")
                                querySubplotsActivity(taskRequire)
                                return
                            } else {
                                Log.record(TAG, jo3.getString("resultDesc"))
                            }
                        }
                    }
                }
            } else {
                Log.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "querySubplotsActivity err:", t)
        }
    }

    private fun orchardAssistFriend() {
        try {
            if (!Status.canAntOrchardAssistFriendToday()) {
                Log.record(TAG, "今日已助力，跳过农场助力")
                return
            }

            val friendSet = assistFriendList.value ?: emptySet()
            if (friendSet.isEmpty()) {
                Log.record(TAG, "未设置农场助力好友列表，跳过农场助力")
                return
            }
            for (uid in friendSet) {
                if (Status.hasFlagToday("orchard::assistRelationInvalid::$uid")) {
                    Log.record(TAG, "农场助力⏭️[${UserMap.getMaskName(uid)}]今日关系已判定无效，跳过")
                    continue
                }
                val shareId = Base64.encodeToString(
                    ("$uid-${RandomUtil.getRandomInt(5)}ANTFARM_ORCHARD_SHARE_P2P").toByteArray(),
                    Base64.NO_WRAP
                )
                val str = AntOrchardRpcCall.achieveBeShareP2P(shareId)
                val jsonObject = JSONObject(str)
                CoroutineUtils.sleepCompat(800)
                val name = UserMap.getMaskName(uid)

                if (!ResChecker.checkRes(TAG, str)) {
                    val code = jsonObject.optString("code")
                    if (code == "600000027") {
                        Log.record(TAG, "农场助力💪今日助力他人次数上限")
                        Status.antOrchardAssistFriendToday()
                        return
                    }
                    if (code == "600000031") {
                        Log.record(TAG, "农场助力💪邀请过于频繁，停止今日助力以避免风控")
                        Status.antOrchardAssistFriendToday()
                        return
                    }
                    if (code == "600000010") {
                        Status.setFlagToday("orchard::assistRelationInvalid::$uid")
                        Log.record(TAG, "农场助力⏭️[$name]人传人邀请关系不存在，已记录为今日跳过")
                        continue
                    }
                    Log.error(TAG, "农场助力😔失败[$name]${jsonObject.optString("desc")}")
                    continue
                }
                Log.farm("农场助力💪[助力:$name]")
            }
            Status.antOrchardAssistFriendToday()
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "orchardAssistFriend err:", t)
        }
    }
    object PlantModeType {
        const val MAIN = 0
        const val YEB = 1
        const val HYBRID = 2

        @JvmField
        val nickNames = arrayOf(
            "种果树(Main)",
            "种摇钱树(Yeb)",
            "混合模式(先摇钱树后果树)"
        )
    }
}


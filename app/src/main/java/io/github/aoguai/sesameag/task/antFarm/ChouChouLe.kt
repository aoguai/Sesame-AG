package io.github.aoguai.sesameag.task.antFarm


import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * 小鸡抽抽乐功能类
 */
class ChouChouLe {

    companion object {
        private val TAG = ChouChouLe::class.java.simpleName
    }

    /**
     * 任务状态枚举
     */
    @Suppress("unused")
    enum class TaskStatus {
        TODO, FINISHED, RECEIVED, DONATION
    }

    /**
     * 任务信息结构体
     */
    private data class TaskInfo(
        var taskStatus: String = "",
        var title: String = "",
        var taskId: String = "",
        var innerAction: String = "",
        var rightsTimes: Int = 0,
        var rightsTimesLimit: Int = 0,
        var awardType: String = "",
        var awardCount: Int = 0,
        var targetUrl: String = ""
    ) {
        /**
         * 获取剩余次数
         */
        fun getRemainingTimes(): Int {
            return max(0, rightsTimesLimit - rightsTimes)
        }

        fun isLimitedTask(): Boolean {
            return title.contains("【限时】")
        }
    }

    /**
     * 抽抽乐主入口
     * 返回值判断是否真的完成任务，是否全部执行完毕且无剩余（任务已做、奖励已领、抽奖已完）
     */
    fun chouchoule(): Boolean {
        var allFinished = true
        try {
            val response = AntFarmRpcCall.queryLoveCabin(UserMap.currentUid)
            val jo = JSONObject(response)
            if (!ResChecker.checkRes(TAG, jo)) {
                return false
            }

            val drawMachineInfo = jo.optJSONObject("drawMachineInfo")
            val hasIpDraw = drawMachineInfo?.has("ipDrawMachineActivityId") == true ||
                jo.has("ipDrawMachineActivityId") ||
                jo.has("ipDrawMachine")
            val hasDailyDraw = drawMachineInfo?.has("dailyDrawMachineActivityId") == true ||
                jo.has("dailyDrawMachineActivityId") ||
                jo.has("dailyDrawMachine")
            if (!hasIpDraw && !hasDailyDraw) {
                Log.error(TAG, "抽抽乐🎁[获取抽抽乐活动信息失败]")
                return false
            }

            // 执行IP抽抽乐
            if (hasIpDraw) {
                allFinished = true and doChouchoule("ipDraw")
            }

            // 执行普通抽抽乐
            if (hasDailyDraw) {
                allFinished = allFinished and doChouchoule("dailyDraw")
            }

            return allFinished
        } catch (t: Throwable) {
            Log.printStackTrace("chouchoule err:", t)
            return false
        }
    }

    /**
     * 执行抽抽乐
     *
     * @param drawType "dailyDraw" 或 "ipDraw"
     * 返回是否该类型已全部完成
     */
    private fun doChouchoule(drawType: String): Boolean {
        var doubleCheck: Boolean
        try {
            runCatching {
                AntFarmRpcCall.refinedOperation("DRAW_MACHINE", "antfarm_villa", "RPC")
            }
            do {
                doubleCheck = false
                val jo = JSONObject(AntFarmRpcCall.chouchouleListFarmTask(drawType))
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error(TAG, if (drawType == "ipDraw") "IP抽抽乐任务列表获取失败" else "抽抽乐任务列表获取失败")
                    return false
                }

                val farmTaskList = jo.getJSONArray("farmTaskList")
                val tasks = parseTasks(farmTaskList)

                for (task in tasks) {
                    if (TaskStatus.FINISHED.name == task.taskStatus) {
                        if (receiveTaskAward(drawType, task)) {
                            GlobalThreadPools.sleepCompat(300L)
                            doubleCheck = true
                        }
                    } else if (TaskStatus.TODO.name == task.taskStatus) {
                        if (shouldSkipLimitedTaskToday(task)) {
                            continue
                        }
                        // 只要有剩余次数，且（不是捐赠任务 OR 开启了捐赠任务开关），就执行
                        if (task.getRemainingTimes() > 0 &&
                            (task.innerAction != "DONATION" || AntFarm.instance?.doChouChouLeDonationTask?.value == true)) {
                            if (doChouTask(drawType, task)) {
                                doubleCheck = true
                            }
                        }
                    }
                }
            } while (doubleCheck)
        } catch (t: Throwable) {
            Log.printStackTrace("doChouchoule err:", t)
            return false
        }

        // 执行抽奖
        val drawSuccess = if ("ipDraw" == drawType) {
            handleIpDraw()
        } else {
            handleDailyDraw()
        }

        if (!drawSuccess) return false

        // 最后校验是否真的全部完成
        return verifyFinished(drawType)
    }

    /*
     校验是否还有未完成的任务或抽奖
     */
    private fun verifyFinished(drawType: String): Boolean {
        return try {
            // 校验任务
            val jo = JSONObject(AntFarmRpcCall.chouchouleListFarmTask(drawType))
            if (!ResChecker.checkRes(TAG, jo)) return false

            val farmTaskList = jo.getJSONArray("farmTaskList")
            val tasks = parseTasks(farmTaskList)
            for (task in tasks) {
                if (TaskStatus.FINISHED.name == task.taskStatus) {
                    return false
                } else if (TaskStatus.TODO.name == task.taskStatus) {
                    if (shouldSkipLimitedTaskToday(task)) {
                        continue
                    }
                    // 还有剩余次数且满足执行条件
                    if (task.getRemainingTimes() > 0 &&
                        (task.innerAction != "DONATION" || AntFarm.instance?.doChouChouLeDonationTask?.value == true)) {
                        return false
                    }
                }
            }

            // 校验抽奖次数
            val drawJo = if ("ipDraw" == drawType) {
                JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New("ipDrawMachine", "dailyDrawMachine"))
            } else {
                JSONObject(AntFarmRpcCall.queryDrawMachineActivity_New("dailyDrawMachine", "ipDrawMachine"))
            }
            if (!ResChecker.checkRes(TAG, drawJo)) return false
            val drawTimes = extractDrawTimes(drawJo)
            if (drawTimes > 0) return false

            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun limitedTaskFlag(taskId: String): String {
        return "antFarm::chouchouleLimitedEnded::$taskId"
    }

    private fun shouldSkipLimitedTaskToday(task: TaskInfo): Boolean {
        return task.isLimitedTask() && Status.hasFlagToday(limitedTaskFlag(task.taskId))
    }

    private fun markLimitedTaskEndedToday(task: TaskInfo, reason: String) {
        if (!task.isLimitedTask()) {
            return
        }
        Status.setFlagToday(limitedTaskFlag(task.taskId))
        val detail = reason.ifBlank { "服务端返回活动已结束" }
        Log.record(TAG, "限时抽抽乐任务[${task.title}]已结束，今日不再尝试：$detail")
    }

    private fun getResponseMessage(jo: JSONObject): String {
        val resData = jo.optJSONObject("resData")
        return listOf(
            jo.optString("resultDesc"),
            jo.optString("desc"),
            jo.optString("memo"),
            resData?.optString("resultDesc").orEmpty(),
            resData?.optString("desc").orEmpty(),
            resData?.optString("memo").orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun extractDrawTimes(jo: JSONObject): Int {
        val userInfo = jo.optJSONObject("userInfo")
        val drawMachineActivity = jo.optJSONObject("drawMachineActivity")
        return listOf(
            jo.optInt("drawTimes", -1),
            jo.optInt("leftDrawTimes", -1),
            jo.optInt("quotaCanUse", -1),
            jo.optInt("canUseTimes", -1),
            jo.optInt("drawRightsTimes", -1),
            userInfo?.optInt("leftDrawTimes", -1) ?: -1,
            userInfo?.optInt("drawTimes", -1) ?: -1,
            drawMachineActivity?.optInt("quotaCanUse", -1) ?: -1,
            drawMachineActivity?.optInt("canUseTimes", -1) ?: -1,
            drawMachineActivity?.optInt("drawRightsTimes", -1) ?: -1
        ).firstOrNull { it >= 0 } ?: 0
    }

    private fun isLimitedTaskEndedResponse(jo: JSONObject): Boolean {
        val resultCode = jo.optString("resultCode")
        if (resultCode == "DRAW_MACHINE07") {
            return false
        }
        val message = getResponseMessage(jo)
        if (message.isBlank()) {
            return false
        }
        return listOf("活动已结束", "活动结束", "已下线", "已失效", "不存在", "未开始", "已结束")
            .any { message.contains(it) }
    }

    private fun isTaskQuotaReachedResponse(jo: JSONObject): Boolean {
        val resultCode = jo.optString("resultCode").ifBlank { jo.optString("code") }
        if (resultCode == "309") {
            return true
        }
        val message = getResponseMessage(jo)
        return message.contains("任务数达到当日上限") ||
            message.contains("权益获取次数超过上限") ||
            message.contains("当日上限")
    }

    /**
     * 解析任务列表
     */
    @Throws(Exception::class)
    private fun parseTasks(array: JSONArray): List<TaskInfo> {
        val list = ArrayList<TaskInfo>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val info = TaskInfo(
                taskStatus = item.getString("taskStatus"),
                title = item.getString("title"),
                taskId = item.optString("bizKey").ifBlank { item.optString("taskId") },
                innerAction = item.optString("innerAction"),
                rightsTimes = listOf(
                    item.optInt("rightsTimes", -1),
                    item.optInt("receivedTimes", -1)
                ).firstOrNull { it >= 0 } ?: 0,
                rightsTimesLimit = listOf(
                    item.optInt("rightsTimesLimit", -1),
                    item.optInt("drawRightsTimes", -1),
                    item.optInt("canReceiveAwardCount", -1)
                ).firstOrNull { it >= 0 } ?: 0,
                awardType = item.optString("awardType").ifBlank { item.optString("taskAwardType") },
                awardCount = listOf(
                    item.optInt("awardCount", -1),
                    item.optInt("canReceiveAwardCount", -1)
                ).firstOrNull { it >= 0 } ?: 0,
                targetUrl = item.optString("targetUrl").ifBlank { item.optString("finishedUrl") }
            )
            list.add(info)
        }
        return list
    }

    private fun isAdBrowseTask(task: TaskInfo): Boolean {
        if (task.innerAction == "DONATION") {
            return false
        }
        val merged = listOf(task.taskId, task.title, task.targetUrl)
            .joinToString("|")
            .lowercase()
        return merged.contains("shangyehua") ||
            merged.contains("30s") ||
            merged.contains("15s") ||
            merged.contains("browse") ||
            merged.contains("杂货铺") ||
            merged.contains("逛一逛")
    }

    private fun resolveAdTaskAttemptCount(task: TaskInfo): Int {
        val remainingTimes = task.getRemainingTimes()
        if (remainingTimes > 0) {
            return remainingTimes
        }
        if (task.rightsTimesLimit <= 0) {
            Log.record(TAG, "广告任务[${task.title}]剩余次数字段异常，按默认3次兜底")
            return 3
        }
        return 0
    }

    /**
     * 执行任务
     */
    private fun doChouTask(drawType: String, task: TaskInfo): Boolean {
        try {
            if (shouldSkipLimitedTaskToday(task)) {
                return false
            }
            if (task.taskId.isBlank()) {
                Log.record(TAG, "抽抽乐任务[${task.title}]缺少 taskId，跳过")
                return false
            }
            val taskName = if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐"

            // 特殊任务：浏览广告
            if (isAdBrowseTask(task)) {
                return handleAdTask(drawType, task)
            }

            // 普通任务
            if (task.title == "消耗饲料换机会") {
                if (AntFarm.foodStock < 90) {
                    Log.record(TAG, "饲料余量(${AntFarm.foodStock}g)少于90g，跳过任务: ${task.title}")
                    return false // 返回 false 避免 doubleCheck，且不执行后续 RPC
                }
            }
            val s = AntFarmRpcCall.chouchouleDoFarmTask(drawType, task.taskId)
            val jo = JSONObject(s)
            val resultCode = jo.optString("resultCode")
            if ("DRAW_MACHINE07" == resultCode) {
                Log.record(TAG, "${taskName}任务[${task.title}]失败: 饲料不足，停止后续尝试")
                return false
            }
            if (isTaskQuotaReachedResponse(jo)) {
                Log.record(TAG, "${taskName}任务[${task.title}]今日次数已达上限，停止继续尝试")
                return false
            }
            if (ResChecker.checkRes(TAG, jo)) {
                Log.farm("$taskName🧾️[任务: ${task.title}]")
                if (task.title == "消耗饲料换机会") {
                    GlobalThreadPools.sleepCompat(300L)
                } else {
                    GlobalThreadPools.sleepCompat(1000L)
                }
                return true
            } else {
                if (isLimitedTaskEndedResponse(jo)) {
                    markLimitedTaskEndedToday(task, getResponseMessage(jo))
                    return true
                }
            }
            return false
        } catch (t: Throwable) {
            Log.printStackTrace("执行抽抽乐任务 err:", t)
            return false
        }
    }

    private fun finishAdTaskDirectly(
        drawType: String,
        task: TaskInfo,
        taskSceneCode: String,
        maxRetry: Int? = null
    ): Int {
        val taskName = if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐"
        val attemptCount = resolveAdTaskAttemptCount(task)
        if (attemptCount <= 0) {
            return 0
        }
        val maxTimes = maxRetry?.let { attemptCount.coerceAtMost(it) } ?: attemptCount
        var successCount = 0
        for (index in 0 until maxTimes) {
            val outBizNo = buildString {
                append(task.taskId)
                append("_")
                append(System.currentTimeMillis())
                append("_")
                append(index)
                append("_")
                append(Integer.toHexString((Math.random() * 0xFFFFFF).toInt()))
            }
            val response = AntFarmRpcCall.finishTask(task.taskId, taskSceneCode, outBizNo)
            val jo = JSONObject(response)
            if (isTaskQuotaReachedResponse(jo)) {
                Log.record(TAG, "广告任务[${task.title}]今日权益已达上限，停止继续尝试")
                return -1
            }
            if (ResChecker.checkRes(TAG, jo)) {
                successCount++
                Log.farm("$taskName🧾️[任务: ${task.title}]#第${task.rightsTimes + successCount}次")
                continue
            }
            if (isLimitedTaskEndedResponse(jo)) {
                markLimitedTaskEndedToday(task, getResponseMessage(jo))
                return successCount
            }
            val message = getResponseMessage(jo)
            if (message.contains("任务已完成") || message.contains("已完成")) {
                return max(1, successCount)
            }
            if (successCount == 0) {
                Log.record(TAG, "广告任务直连完成失败[${task.title}]: ${message.ifBlank { jo.toString() }}")
            }
            break
        }
        return successCount
    }

    /**
     * 处理广告任务
     */
    private fun handleAdTask(drawType: String, task: TaskInfo): Boolean {
        try {
            if (shouldSkipLimitedTaskToday(task)) {
                return false
            }
            val taskSceneCode = if (drawType == "ipDraw") "ANTFARM_IP_DRAW_TASK" else "ANTFARM_DAILY_DRAW_TASK"
            val directSuccessCount = finishAdTaskDirectly(drawType, task, taskSceneCode)
            if (directSuccessCount != 0) {
                return directSuccessCount > 0
            }

            val referToken = AntFarm.loadAntFarmReferToken()
            if (!referToken.isNullOrEmpty()) {
                val response = AntFarmRpcCall.xlightPlugin(referToken, "HDWFCJGXNZW_CUSTOM_20250826173111")
                val jo = JSONObject(response)
                if (isTaskQuotaReachedResponse(jo)) {
                    Log.record(TAG, "浏览广告任务[${task.title}]今日权益已达上限，跳过插件流程")
                    return false
                }

                if (jo.optString("retCode") == "0") {
                    val resData = jo.optJSONObject("resData")
                    if (resData != null) {
                        val adList = resData.optJSONArray("adList")

                        if (adList != null && adList.length() > 0) {
                            // 检查是否有猜一猜任务
                            val playingResult = resData.optJSONObject("playingResult")
                            if (playingResult != null &&
                                "XLIGHT_GUESS_PRICE_FEEDS" == playingResult.optString("playingStyleType")
                            ) {
                                return handleGuessTask(drawType, task, adList, playingResult)
                            }
                        }
                    } else {
                        Log.record(TAG, "浏览广告任务[广告插件未返回resData，回退普通完成方式]")
                    }
                }
                Log.record(TAG, "浏览广告任务[没有可用广告或不支持，使用普通完成方式]")
            } else {
                Log.record(TAG, "浏览广告任务[没有可用Token，请手动看一起广告]")
            }

            return finishAdTaskDirectly(drawType, task, taskSceneCode, 1) > 0
        } catch (t: Throwable) {
            Log.printStackTrace("处理广告任务 err:", t)
            return false
        }
    }

    /**
     * 处理猜一猜任务
     */
    private fun handleGuessTask(
        drawType: String, task: TaskInfo,
        adList: JSONArray, playingResult: JSONObject
    ): Boolean {
        try {
            // 找到正确价格
            var correctPrice = -1
            var targetAdId = ""

            for (i in 0 until adList.length()) {
                val ad = adList.getJSONObject(i)
                val schemaJson = ad.optString("schemaJson", "")
                if (schemaJson.isNotEmpty()) {
                    val schema = JSONObject(schemaJson)
                    val price = schema.optInt("price", -1)
                    if (price > 0) {
                        if (correctPrice == -1 || abs(price - 11888) < abs(correctPrice - 11888)) {
                            correctPrice = price
                            targetAdId = ad.optString("adId", "")
                        }
                    }
                }
            }

            if (correctPrice > 0 && targetAdId.isNotEmpty()) {
                // 提交猜价格结果
                val playBizId = playingResult.optString("playingBizId", "")
                val eventRewardDetail = playingResult.optJSONObject("eventRewardDetail")
                if (eventRewardDetail != null) {
                    val eventRewardInfoList = eventRewardDetail.optJSONArray("eventRewardInfoList")
                    if (eventRewardInfoList != null && eventRewardInfoList.length() > 0) {
                        val playEventInfo = eventRewardInfoList.getJSONObject(0)

                        val taskSceneCode =
                            if (drawType == "ipDraw") "ANTFARM_IP_DRAW_TASK" else "ANTFARM_DAILY_DRAW_TASK"

                        val response = AntFarmRpcCall.finishAdTask(
                            playBizId, playEventInfo, task.taskId, taskSceneCode
                        )
                        val jo = JSONObject(response)

                        if (jo.optJSONObject("resData") != null &&
                            jo.getJSONObject("resData").optBoolean("success", false)
                        ) {
                            Log.farm(
                                (if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐") +
                                        "🧾️[猜价格任务完成: ${task.title}, 猜中价格: $correctPrice]"
                            )
                            GlobalThreadPools.sleepCompat(300L)
                            return true
                        }
                    }
                }
            }

            Log.record(TAG, "猜价格任务[未找到合适价格，使用普通完成方式]")
            return false
        } catch (t: Throwable) {
            Log.printStackTrace("处理猜价格任务 err:", t)
            return false
        }
    }

    /**
     * 领取任务奖励
     */
    private fun receiveTaskAward(drawType: String, task: TaskInfo): Boolean {
        try {
            if (task.taskId.isBlank()) {
                Log.record(TAG, "抽抽乐奖励[${task.title}]缺少 taskId，跳过领取")
                return false
            }
            val s = AntFarmRpcCall.chouchouleReceiveFarmTaskAward(
                drawType,
                task.taskId,
                task.awardType
            )
            val jo = JSONObject(s)
            if (ResChecker.checkRes(TAG, jo)) {
                return true
            }
        } catch (t: Throwable) {
            Log.printStackTrace("receiveFarmTaskAward err:", t)
        }
        return false
    }

    /**
     * 执行IP抽抽乐抽奖
     */
    private fun handleIpDraw(): Boolean {
        try {
            val jo = JSONObject(
                AntFarmRpcCall.queryDrawMachineActivity_New(
                    "ipDrawMachine", "dailyDrawMachine"
                )
            )
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.record(TAG, "IP抽抽乐新版活动查询失败，切换旧版接口重试")
                return handleIpDrawLegacy()
            }

            val activity = jo.optJSONObject("drawMachineActivity") ?: return handleIpDrawLegacy()
            val activityId = activity.optString("activityId")
            val endTime = activity.optLong("endTime", 0)
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                Log.record(TAG, "该[${activity.optString("activityId")}]抽奖活动已结束")
                return true
            }

            var remainingTimes = extractDrawTimes(jo)
            if (remainingTimes <= 0) {
                Log.record(TAG, "IP抽抽乐当前无可用次数，跳过旧版兜底接口")
                return true
            }
            var allSuccess = true
            Log.record(TAG, "IP抽抽乐剩余次数: $remainingTimes")

            while (remainingTimes > 0) {
                val batchCount = remainingTimes.coerceAtMost(10)
                Log.record(TAG, "执行 IP 抽抽乐 $batchCount 连抽...")

                val response = AntFarmRpcCall.drawMachineIP(batchCount)
                val batchSuccess = drawPrize("IP抽抽乐", response)
                if (!batchSuccess) {
                    Log.record(TAG, "IP抽抽乐连抽失败，切换旧版单抽流程")
                    return handleIpDrawLegacy()
                }
                allSuccess = allSuccess and batchSuccess

                remainingTimes -= batchCount
                if (remainingTimes > 0) {
                    GlobalThreadPools.sleepCompat(1500L)
                }
            }
            if (activityId.isNotEmpty() && AntFarm.instance?.autoExchange?.value == true) {
                batchExchangeRewards(activityId)
            }
            return allSuccess
        } catch (t: Throwable) {
            Log.printStackTrace("handleIpDraw err:", t)
            return false
        }
    }

    /**
     * 执行普通抽抽乐抽奖
     */
    private fun handleDailyDraw(): Boolean {
        try {
            val jo = JSONObject(
                AntFarmRpcCall.queryDrawMachineActivity_New(
                    "dailyDrawMachine", "ipDrawMachine"
                )
            )
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.record(TAG, "日常抽抽乐新版活动查询失败，切换旧版接口重试")
                return handleDailyDrawLegacy()
            }

            val activity = jo.optJSONObject("drawMachineActivity") ?: return handleDailyDrawLegacy()
            val endTime = activity.optLong("endTime", 0)
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                Log.record(TAG, "该[${activity.optString("activityId")}]抽奖活动已结束")
                return true
            }

            var remainingTimes = extractDrawTimes(jo)
            if (remainingTimes <= 0) {
                Log.record(TAG, "日常抽抽乐当前无可用次数，跳过旧版兜底接口")
                return true
            }
            var allSuccess = true

            Log.record(TAG, "日常抽抽乐剩余次数: $remainingTimes")

            while (remainingTimes > 0) {
                val batchCount = remainingTimes.coerceAtMost(10)
                Log.record(TAG, "执行日常抽抽乐 $batchCount 连抽...")

                val response = AntFarmRpcCall.drawMachineDaily(batchCount)
                val batchSuccess = drawPrize("日常抽抽乐", response)
                if (!batchSuccess) {
                    Log.record(TAG, "日常抽抽乐连抽失败，切换旧版单抽流程")
                    return handleDailyDrawLegacy()
                }
                allSuccess = allSuccess and batchSuccess

                remainingTimes -= batchCount
                if (remainingTimes > 0) {
                    GlobalThreadPools.sleepCompat(1500L)
                }
            }
            return allSuccess
        } catch (t: Throwable) {
            Log.printStackTrace("handleDailyDraw err:", t)
            return false
        }
    }

    private fun handleIpDrawLegacy(): Boolean {
        return try {
            val jo = JSONObject(AntFarmRpcCall.queryDrawMachineActivity())
            if (!ResChecker.checkRes(TAG, jo)) {
                false
            } else {
                val activity = jo.optJSONObject("drawMachineActivity") ?: return true
                val endTime = activity.optLong("endTime", 0)
                if (endTime > 0 && System.currentTimeMillis() > endTime) {
                    Log.record(TAG, "该[${activity.optString("activityId")}]抽奖活动已结束")
                    return true
                }

                var remainingTimes = jo.optInt("drawTimes", 0)
                var allSuccess = true
                while (remainingTimes > 0) {
                    val drawSuccess = drawPrize("IP抽抽乐", AntFarmRpcCall.drawMachine())
                    allSuccess = allSuccess and drawSuccess
                    if (!drawSuccess) {
                        break
                    }
                    remainingTimes--
                    if (remainingTimes > 0) {
                        GlobalThreadPools.sleepCompat(1500L)
                    }
                }
                allSuccess
            }
        } catch (t: Throwable) {
            Log.printStackTrace("handleIpDrawLegacy err:", t)
            false
        }
    }

    private fun handleDailyDrawLegacy(): Boolean {
        return try {
            val jo = JSONObject(AntFarmRpcCall.enterDrawMachine())
            if (!ResChecker.checkRes(TAG, jo)) {
                false
            } else {
                val userInfo = jo.optJSONObject("userInfo") ?: return true
                val drawActivityInfo = jo.optJSONObject("drawActivityInfo") ?: return true
                val endTime = drawActivityInfo.optLong("endTime", 0)
                if (endTime > 0 && System.currentTimeMillis() > endTime) {
                    Log.record(TAG, "该[${drawActivityInfo.optString("activityId")}]抽奖活动已结束")
                    return true
                }

                var remainingTimes = userInfo.optInt("leftDrawTimes", 0)
                val activityId = drawActivityInfo.optString("activityId")
                var allSuccess = true
                while (remainingTimes > 0) {
                    val response = if (activityId.isBlank() || activityId == "null") {
                        AntFarmRpcCall.DrawPrize()
                    } else {
                        AntFarmRpcCall.DrawPrize(activityId)
                    }
                    val drawSuccess = drawPrize("日常抽抽乐", response)
                    allSuccess = allSuccess and drawSuccess
                    if (!drawSuccess) {
                        break
                    }
                    remainingTimes--
                    if (remainingTimes > 0) {
                        GlobalThreadPools.sleepCompat(1500L)
                    }
                }
                allSuccess
            }
        } catch (t: Throwable) {
            Log.printStackTrace("handleDailyDrawLegacy err:", t)
            false
        }
    }

    /**
     * 领取抽抽乐奖品
     *
     * @param prefix   抽奖类型前缀
     * @param response 服务器返回的结果
     * 返回是否领取成功
     */
    private fun drawPrize(prefix: String, response: String): Boolean {
        try {
            val jo = JSONObject(response)
            if (ResChecker.checkRes(TAG, jo)) {
                val prizeList = jo.optJSONArray("drawMachinePrizeList")
                if (prizeList != null && prizeList.length() > 0) {
                    for (i in 0 until prizeList.length()) {
                        val prize = prizeList.getJSONObject(i)
                        val title = prize.optString("title", prize.optString("prizeName", "未知奖品"))
                        Log.farm("$prefix🎁[领取: $title]")
                    }
                } else {
                    val prize = jo.optJSONObject("drawMachinePrize")
                    if (prize != null) {
                        val title = prize.optString("title", prize.optString("prizeName", "未知奖品"))
                        Log.farm("$prefix🎁[领取: $title]")
                    } else {
                        Log.farm("$prefix🎁[抽奖成功，但未解析到具体奖品名称]")
                    }
                }
                return true
            }
        } catch (t: Throwable) {
            Log.printStackTrace("drawPrize err:", t)
        }
        return false
    }

    /**
     * 批量兑换奖励（严格优先级策略：攒钱买最好的）
     */
    fun batchExchangeRewards(activityId: String) {
        try {
            val response = AntFarmRpcCall.getItemList(activityId, 10, 0)
            val respJson = JSONObject(response)

            if (respJson.optBoolean("success", false) || respJson.optString("code") == "100000000") {
                var totalCent = 0
                val mallAccount = respJson.optJSONObject("mallAccountInfoVO")
                if (mallAccount != null) {
                    val holdingCount = mallAccount.optJSONObject("holdingCount")
                    if (holdingCount != null) {
                        totalCent = holdingCount.optInt("cent", 0)
                    }
                }
                Log.record("自动兑换", "当前持有总碎片: " + (totalCent / 100))
                val itemVOList = respJson.optJSONArray("itemInfoVOList") ?: return

                val allSkus = ArrayList<JSONObject>()
                for (i in 0 until itemVOList.length()) {
                    val item = itemVOList.optJSONObject(i) ?: continue
                    val itemReachedLimit = isReachedLimit(item)
                    val minPriceObj = item.optJSONObject("minPrice")
                    val cent = minPriceObj?.optInt("cent", 0) ?: 0

                    val skuList = item.optJSONArray("skuModelList") ?: continue
                    for (j in 0 until skuList.length()) {
                        val sku = skuList.optJSONObject(j) ?: continue
                        sku.put("_spuId", item.optString("spuId"))
                        sku.put("_spuName", item.optString("spuName"))
                        sku.put("_isReachLimit", itemReachedLimit || isReachedLimit(sku))
                        sku.put("_cent", cent)
                        allSkus.add(sku)
                    }
                }
                allSkus.sortWith { a, b -> b.optInt("_cent", 0).compareTo(a.optInt("_cent", 0)) }

                for (sku in allSkus) {
                    if (sku.optBoolean("_isReachLimit")) continue
                    val cent = sku.optInt("_cent", 0)
                    val skuName = sku.optString("skuName")

                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.record("自动兑换", "最高价值项 [$skuName] 碎片不足(持有 ${totalCent/100}, 需 ${cent/100})，等攒够再换，终止本次兑换")
                        return
                    }
                    break
                }

                // 执行顺序兑换，按价格从高到低
                for (sku in allSkus) {
                    if (sku.optBoolean("_isReachLimit")) continue

                    val skuName = sku.optString("skuName")
                    val cent = sku.optInt("_cent", 0)
                    val extendInfo = sku.optString("skuExtendInfo")
                    val limitCount = if (extendInfo.contains("20次")) 20 else if (extendInfo.contains("5次")) 5 else 1

                    // 【核心逻辑】：如果当前项买不起，直接 return 停止，不再尝试后续更便宜的项目
                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.record("自动兑换", "剩余碎片不足以兑换优先级项 [$skuName] (需 ${cent/100})，停止后续兑换任务")
                        return
                    }

                    var sessionExchangedCount = 0
                    while (sessionExchangedCount < limitCount) {
                        // 预检查当前余额
                        if (cent > 0 && totalCent < cent) break

                        val result = AntFarmRpcCall.exchangeBenefit(
                            sku.optString("_spuId"), sku.optString("skuId"),
                            activityId, "ANTFARM_IP_DRAW_MALL", "antfarm_villa"
                        )

                        val resObj = JSONObject(result)
                        val resultCode = resObj.optString("resultCode")

                        if ("SUCCESS" == resultCode) {
                            sessionExchangedCount++
                            totalCent -= cent // 减去花费
                            Log.record(
                                "自动兑换",
                                "成功兑换: $skuName (本次第 $sessionExchangedCount 次，剩余碎片: ${totalCent/100})"
                            )
                            GlobalThreadPools.sleepCompat(800L)
                        } else if ("NO_ENOUGH_POINT" == resultCode) {
                            Log.record("自动兑换", "兑换过程中积分不足，停止后续所有任务")
                            return
                        } else if (resultCode.contains("LIMIT") || resultCode.contains("MAX")) {
                            Log.record("自动兑换", "[$skuName] 已达兑换上限: " + resObj.optString("resultDesc"))
                            break
                        } else {
                            Log.record("自动兑换", "跳过 [$skuName]: " + resObj.optString("resultDesc"))
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG,"自动兑换异常", e)
        }
    }

    private fun isReachedLimit(jo: JSONObject?): Boolean {
        if (jo == null) return false
        if ("REACH_LIMIT" == jo.optString("itemStatus")) return true
        val list = jo.optJSONArray("itemStatusList")
        if (list != null) {
            for (i in 0 until list.length()) {
                val status = list.optString(i)
                if ("REACH_LIMIT" == status || status.contains("LIMIT")) return true
            }
        }
        return false
    }

    private fun isNoEnoughPoint(jo: JSONObject?): Boolean {
        if (jo == null) return false
        if ("NO_ENOUGH_POINT" == jo.optString("itemStatus")) return true
        val list = jo.optJSONArray("itemStatusList")
        if (list != null) {
            for (i in 0 until list.length()) {
                if ("NO_ENOUGH_POINT" == list.optString(i)) return true
            }
        }
        return false
    }
}


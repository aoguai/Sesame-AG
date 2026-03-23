package io.github.aoguai.sesameag.task.antFarm

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.task.TaskStatus
import io.github.aoguai.sesameag.util.GameTask
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

object FarmGame {
    private const val TAG = "FarmGame"
    enum class GameType {
        flyGame, hitGame, starGame, jumpGame;
        fun gameName(): String = when(this) {
            flyGame -> "飞行赛"
            hitGame -> "欢乐揍小鸡"
            starGame -> "星星球"
            jumpGame -> "登山赛"
        }
    }

    /**
     * 外部入口：处理游戏改分逻辑
     */
    suspend fun run(antFarm: AntFarm) {
        val isAccelEnabled = antFarm.useAccelerateTool!!.value
        val isAccelLimitReached = Status.hasFlagToday(StatusFlags.FLAG_FARM_ACCELERATE_LIMIT) || !Status.canUseAccelerateTool()
        val isInsideTimeRange = antFarm.farmGameTime!!.value?.any { TimeUtil.checkNowInTimeRange(it) }
        val ignoreAcceLimitMode = !isAccelEnabled!! || antFarm.ignoreAcceLimit!!.value == true

        when {
            ignoreAcceLimitMode -> {
                if (isInsideTimeRange == true) {
                    antFarm.receiveFarmAwards()
                    playAllFarmGames()
                }
            }
            isAccelLimitReached || antFarm.accelerateToolCount <= 0 -> {
                antFarm.syncAnimalStatus(antFarm.ownerFarmId)
                val foodStockThreshold = AntFarm.foodStockLimit - antFarm.gameRewardMax!!.value!!
                val reserveMin = 180
                val ceilingStock = AntFarm.foodStockLimit - reserveMin

                if (AntFarm.foodStock < foodStockThreshold) {
                    antFarm.receiveFarmAwards()
                }

                var isSatisfied: Boolean
                if (reserveMin <= antFarm.gameRewardMax!!.value!!) {
                    isSatisfied = AntFarm.foodStock in foodStockThreshold..ceilingStock
                } else{
                    isSatisfied = AntFarm.foodStock >= foodStockThreshold
                }
                val isTaskEnabled = antFarm.doFarmTask?.value == true
                val isTaskFinished = Status.hasFlagToday(StatusFlags.FLAG_FARM_TASK_FINISHED)

                when {
                    isSatisfied -> playAllFarmGames()

                    AntFarm.foodStock > ceilingStock -> {
                        Log.record(TAG, "当前饲料${AntFarm.foodStock}g（空间不足180g），等待小鸡进食后再执行游戏改分")
                    }

                    !isTaskEnabled -> {
                        Log.record("未开启饲料任务，虽然尝试领取了奖励，但饲料缺口仍超过${antFarm.gameRewardMax!!.value}g，直接执行游戏")
                        playAllFarmGames()
                    }

                    isTaskFinished -> {
                        Log.record(
                            "已开启饲料任务且今日已完成，但领取奖励后缺口仍超过${antFarm.gameRewardMax!!.value}g，暂不执行游戏改分。" +
                                    "请确认饲料奖励完成情况，可以关闭设置里的“做饲料任务”选项直接进行游戏改分"
                        )
                    }

                    else -> {
                        Log.record("已开启饲料任务但尚未完成，现有饲料缺口超过${antFarm.gameRewardMax!!.value}g，等待任务完成后再执行")
                    }
                }
            }
            // 加速卡还没用完，等待加速卡用完
            antFarm.accelerateToolCount > 0 -> {
                Log.record("加速卡有${antFarm.accelerateToolCount}张，已使用${Status.INSTANCE.useAccelerateToolCount}张，" +
                        "尚未达到今日使用上限，等待加速完成后再改分")
            }
        }
    }
    suspend fun playAllFarmGames() {
        recordFarmGame(GameType.flyGame)
        recordFarmGame(GameType.hitGame)
        recordFarmGame(GameType.starGame)
        recordFarmGame(GameType.jumpGame)
        Status.setFlagToday(StatusFlags.FLAG_FARM_GAME_FINISHED)
        Log.farm("今日庄园游戏改分已完成")
    }

    private suspend fun recordFarmGame(gameType: GameType) {
        try {
            while (true) {
                val initRes = AntFarmRpcCall.initFarmGame(gameType.name)
                val joInit = JSONObject(initRes)
                if (!ResChecker.checkRes(TAG, joInit)) break

                val gameAward = joInit.optJSONObject("gameAward")
                if (gameAward?.optBoolean("level3Get") == true) {
                    Log.record(TAG, "[${gameType.gameName()}]#今日奖励已领满")
                    break
                }

                val remainingCount = joInit.optInt("remainingGameCount", 1)
                if (remainingCount > 0) {
                    val recordResult = AntFarmRpcCall.recordFarmGame(gameType.name)
                    val joRecord = JSONObject(recordResult)
                    if (ResChecker.checkRes(TAG, joRecord)) {
                        val awardStr = parseGameAward(joRecord)
                        Log.farm("庄园游戏🎮[${gameType.gameName()}]#$awardStr")

                        if (joRecord.optInt("remainingGameCount", 0) > 0) {
                            delay(3000)
                            continue
                        }
                    } else {
                        Log.record(TAG, "庄园游戏提交失败: $joRecord")
                    }
                }

                if (handleGameTasks(gameType)) {
                    delay(3000)
                    continue
                }
                break
            }
        } catch (e: CancellationException) {
            // 协程取消异常必须重新抛出，不能吞掉
            Log.record(TAG, "recordFarmGame 协程被取消")
            throw e
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "recordFarmGame err:",t)
        }
    }

    private fun parseGameAward(jo: JSONObject): String {
        val award = StringBuilder()
        jo.optJSONArray("awardInfos")?.let { ja ->
            for (i in 0 until ja.length()) {
                val info = ja.getJSONObject(i)
                if (award.isNotEmpty()) award.append(",")
                award.append(info.optString("awardName")).append("*").append(info.optInt("awardCount"))
            }
        }
        val foodCount = jo.optString("receiveFoodCount", "")
        if (foodCount.isNotEmpty()) {
            if (award.isNotEmpty()) award.append(";")
            award.append("饲料*").append(foodCount)
        }
        return award.toString()
    }

    private fun handleGameTasks(gameType: GameType): Boolean {
        // 仅飞行赛和揍小鸡有独立任务列表
        val listResponse = when (gameType) {
            GameType.flyGame -> AntFarmRpcCall.FlyGameListFarmTask()
            GameType.hitGame -> AntFarmRpcCall.HitGameListFarmTask()
            else -> return false
        }
        if (listResponse.isEmpty()) return false
        val farmTaskList = JSONObject(listResponse).optJSONArray("farmTaskList") ?: return false

        for (i in 0 until farmTaskList.length()) {
            val task = farmTaskList.getJSONObject(i)
            val status = task.optString("taskStatus")
            if (TaskStatus.RECEIVED.name == status) continue
            if (TaskStatus.FINISHED.name == status) {
                AntFarmRpcCall.receiveFarmTaskAward(task.optString("taskId"))
                return true
            }
            if (TaskStatus.TODO.name == status) {
                val bizKey = task.optString("bizKey")
                val outBizNo = "${bizKey}_${System.currentTimeMillis()}_${Integer.toHexString((Math.random() * 0xFFFFFF).toInt())}"
                AntFarmRpcCall.finishTask(bizKey, "ANTFARM_GAME_TIMES_TASK", outBizNo)
                return true
            }
        }
        return false
    }

    internal suspend fun drawGameCenterAward() {
        var totalParadiseCoins = 0 // 🚀 统计总共获得的乐园币
        try {
            runCatching {
                val warmup = JSONObject(AntFarmRpcCall.refinedOperation("ENTERSELFWITHOUTPOP"))
                if (!warmup.optBoolean("success", false) && warmup.optString("resultCode") != "100") {
                    Log.record(TAG, "庄园游戏中心预热失败，继续尝试查询游戏列表")
                }
            }
            while (true) {
                val response = AntFarmRpcCall.queryGameList()
                val responseJo = JSONObject(response)
                val jo = responseJo.optJSONObject("resData") ?: responseJo

                if (!jo.optBoolean("success", responseJo.optBoolean("success"))) {
                    Log.record(TAG, "queryGameList 失败: $responseJo")
                    break
                }

                val currentRights = findFirstObjectByKey(jo, "gameCenterDrawRights")
                    ?: findFirstObjectByKey(jo, "gameDrawAwardActivity")
                    ?: findFirstObjectByKey(jo, "gameEntryInfo")
                if (currentRights == null) {
                    Log.record(TAG, "未找到开宝箱权益，退出")
                    break
                }

                // 1. 处理当前可开的宝箱 (对应你说的 canUse)
                var quotaCanUse = currentRights.optInt(
                    "quotaCanUse",
                    currentRights.optInt("canUseTimes", currentRights.optInt("drawRightsTimes", 0))
                )
                if (quotaCanUse > 0) {
                    Log.record(AntFarm.TAG, "当前有 $quotaCanUse 个宝箱待开启...")
                    while (quotaCanUse > 0) {
                        val batchDrawCount = quotaCanUse.coerceAtMost(10)
                        val drawResponse = JSONObject(AntFarmRpcCall.drawGameCenterAward(batchDrawCount))
                        val drawRes = drawResponse.optJSONObject("resData") ?: drawResponse
                        if (drawRes.optBoolean("success", drawResponse.optBoolean("success"))) {
                            // 领取成功后，更新剩余可领取的 quotaCanUse
                            val nextRights = findFirstObjectByKey(drawRes, "gameCenterDrawRights")
                                ?: findFirstObjectByKey(drawRes, "gameDrawAwardActivity")
                                ?: findFirstObjectByKey(drawRes, "gameEntryInfo")
                            quotaCanUse = nextRights?.optInt(
                                "quotaCanUse",
                                nextRights.optInt(
                                    "canUseTimes",
                                    drawRes.optInt("drawRightsTimes", quotaCanUse - batchDrawCount)
                                )
                            ) ?: (quotaCanUse - batchDrawCount)

                            val awardList = findFirstArrayByKey(drawRes, "gameCenterDrawAwardList")
                                ?: findFirstArrayByKey(drawRes, "drawAwardList")
                            val awardStrings = mutableListOf<String>()
                            if (awardList != null) {
                                for (i in 0 until awardList.length()) {
                                    val item = awardList.getJSONObject(i)
                                    val awardName = item.optString("awardName")
                                    val awardCount = item.optInt("awardCount")
                                    awardStrings.add("$awardName*$awardCount")
                                    if (awardName.contains("乐园币")) {
                                        totalParadiseCoins += awardCount
                                    }
                                }
                            }
                            Log.farm("庄园小鸡🎁[获得奖品: ${awardStrings.joinToString(",")}]")
                            delay(3000)
                        } else {
                            val desc = drawRes.optString("desc")
                                .ifBlank { drawRes.optString("resultDesc") }
                                .ifBlank { drawResponse.optString("desc") }
                            Log.record(TAG, "开启宝箱失败: $desc")
                            return
                        }
                    }
                    continue
                }

                // 2. 处理剩余任务 (判断是否需要去刷任务)
                val limit = currentRights.optInt("quotaLimit", currentRights.optInt("limit")) // 总上限，比如 10
                val used = currentRights.optInt("usedQuota", currentRights.optInt("usedTimes"))   // 今日已获得的总数，比如 2

                // 计算逻辑：如果 已获得 < 总上限，且当前没机会了，就去刷
                val remainToTask = limit - used
                if (remainToTask > 0 && quotaCanUse == 0) {
                    // Log.record(TAG, "宝箱进度: $used/$limit，开始自动刷任务补齐...")
                    // 根据游戏类型选择上报任务
                    GameTask.Farm_ddply.report(remainToTask)
                    continue
                } else if (remainToTask <= 0) {
                    Log.record(TAG, "今日 $limit 个金蛋任务已全部满额")
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.printStackTrace(AntFarm.TAG, "drawGameCenterAward 流程异常", t)
        } finally {
            if (totalParadiseCoins > 0) {
                Log.farm("庄园小鸡🎁[本次任务总计获得乐园币: ${totalParadiseCoins}]")
            }
        }
    }


    internal fun findFirstObjectByKey(source: Any?, targetKey: String): JSONObject? {
        return when (source) {
            is JSONObject -> {
                source.optJSONObject(targetKey)?.let { return it }
                val keys = source.keys()
                while (keys.hasNext()) {
                    val child = source.opt(keys.next())
                    findFirstObjectByKey(child, targetKey)?.let { return it }
                }
                null
            }

            is JSONArray -> {
                for (index in 0 until source.length()) {
                    findFirstObjectByKey(source.opt(index), targetKey)?.let { return it }
                }
                null
            }

            else -> null
        }
    }

    internal fun findFirstArrayByKey(source: Any?, targetKey: String): JSONArray? {
        return when (source) {
            is JSONObject -> {
                source.optJSONArray(targetKey)?.let { return it }
                val keys = source.keys()
                while (keys.hasNext()) {
                    val child = source.opt(keys.next())
                    findFirstArrayByKey(child, targetKey)?.let { return it }
                }
                null
            }

            is JSONArray -> {
                for (index in 0 until source.length()) {
                    findFirstArrayByKey(source.opt(index), targetKey)?.let { return it }
                }
                null
            }

            else -> null
        }
    }

}

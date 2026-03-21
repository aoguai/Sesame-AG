package io.github.aoguai.sesameag.task.antForest

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.hook.Toast
import io.github.aoguai.sesameag.util.GameTask
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

/**
 * 能量雨功能 - Kotlin协程版本
 *
 * 这是EnergyRain.java的协程版本重构，提供更好的性能和可维护性
 */
object EnergyRainCoroutine {
    private const val TAG = "EnergyRain"
    private val SILENT_GRANT_FAILURE_CODES = setOf(
        "FRIEND_NOT_FOREST_USER",
        "RAIN_ENERGY_GRANTED_BY_OTHER",
        "RAIN_ENERGY_GRANT_EXCEED"
    )

    /**
     * 上次执行能量雨的时间戳
     */
    @Volatile
    private var lastExecuteTime: Long = 0

    /**
     * 随机延迟，增加随机性避免风控检测
     * @param min 最小延迟（毫秒）
     * @param max 最大延迟（毫秒）
     */
    private suspend fun randomDelay(min: Int, max: Int) {
        val delayTime = Random.nextInt(min, max + 1).toLong()
        delay(delayTime)
    }

    /**
     * 执行能量雨功能
     */
    suspend fun execEnergyRain() {
        try {
            // 执行频率检查：防止短时间内重复执行
            val currentTime = System.currentTimeMillis()
            val timeSinceLastExec = currentTime - lastExecuteTime
            val cooldownSeconds = 3 // 冷却时间：3秒

            if (timeSinceLastExec < cooldownSeconds * 1000) {
                // 粗放点，delay 3秒
                delay(cooldownSeconds * 1000.toLong())
            }

            energyRain()

            // 更新最后执行时间
            lastExecuteTime = System.currentTimeMillis()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.record(TAG, "execEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.printStackTrace(TAG, "执行能量雨出错:", th)
        }
    }

    /**
     * 能量雨主逻辑（协程版本）
     */
    private suspend fun energyRain() {
        try {
            var playedCount = 0
            val maxPlayLimit = 10

            do {
                val joEnergyRainHome = JSONObject(AntForestRpcCall.queryEnergyRainHome())
                randomDelay(250, 400) // 随机延迟 300-400ms
                if (!ResChecker.checkRes(TAG, joEnergyRainHome)) {
                    Log.record(TAG, "查询能量雨状态失败")
                    break
                }
                val canPlayToday = joEnergyRainHome.optBoolean("canPlayToday", false)
                val canPlayGame = joEnergyRainHome.optBoolean("canPlayGame", false)
                val canGrantStatus = joEnergyRainHome.optBoolean("canGrantStatus", false)

                // 1️⃣ 检查是否可以开始能量雨
                if (canPlayToday) {
                    startEnergyRain()
                    playedCount++
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    continue
                }

                // 2️⃣ 检查是否可以赠送能量雨
                if (canGrantStatus) {
                    Log.record(TAG, "有送能量雨的机会")
                    val grantExceedFlag = "EnergyRain::grant_energy_rain_exceed"
                    if (Status.hasFlagToday(grantExceedFlag)) {
                        Log.record(TAG, "今日已达到赠送能量雨上限，跳过赠送环节")
                    } else {
                        val joEnergyRainCanGrantList = JSONObject(AntForestRpcCall.queryEnergyRainCanGrantList())
                        val grantInfos = joEnergyRainCanGrantList.optJSONArray("grantInfos") ?: org.json.JSONArray()
                        val giveEnergyRainSet = AntForest.giveEnergyRainList?.value ?: emptySet()
                        var granted = false
                        var grantExceeded = false

                        for (j in 0 until grantInfos.length()) {
                            val grantInfo = grantInfos.getJSONObject(j)
                            if (grantInfo.optBoolean("canGrantedStatus", false)) {
                                val uid = grantInfo.getString("userId")
                                if (giveEnergyRainSet.contains(uid)) {
                                    val rainJsonObj = JSONObject(AntForestRpcCall.grantEnergyRainChance(uid))
                                    val maskedName = UserMap.getMaskName(uid)
                                    val resultCode = rainJsonObj.optString("resultCode")
                                    val resultDesc = rainJsonObj.optString("resultDesc")
                                    Log.record(TAG, "尝试送能量雨给【$maskedName】")
                                    if (resultCode in SILENT_GRANT_FAILURE_CODES) {
                                        when (resultCode) {
                                            "RAIN_ENERGY_GRANT_EXCEED" -> {
                                                Status.setFlagToday(grantExceedFlag)
                                                Log.record(TAG, "送能量雨已达到今日上限，停止继续尝试")
                                                grantExceeded = true
                                                break
                                            }

                                            "FRIEND_NOT_FOREST_USER" -> {
                                                Log.record(TAG, "跳过赠送【$maskedName】:${resultDesc.ifEmpty { "好友未开通蚂蚁森林" }}")
                                            }

                                            "RAIN_ENERGY_GRANTED_BY_OTHER" -> {
                                                Log.record(TAG, "跳过赠送【$maskedName】:${resultDesc.ifEmpty { "该好友已被其他人赠送" }}")
                                            }
                                        }
                                        continue
                                    }
                                    if (ResChecker.checkRes(TAG, rainJsonObj)) {
                                        Log.forest(
                                            "赠送能量雨机会给🌧️[$maskedName]#${
                                                UserMap.getMaskName(
                                                    UserMap.currentUid
                                                )
                                            }"
                                        )
                                        randomDelay(300, 400) // 随机延迟 300-400ms
                                        granted = true
                                        break
                                    } else {
                                        Log.error(TAG, "送能量雨失败 $rainJsonObj")
                                    }
                                }
                            }
                        }
                        if (grantExceeded) {
                            // 已达到上限：已记录并设置今日标记，无需继续提示
                        } else if (granted) {
                            continue
                        } else {
                            Log.record(TAG, "今日无可送能量雨好友或已达到赠送上限")
                        }
                    }
                }

                // 3️⃣ 检查是否可以能量雨游戏
                // canPlayGame 好像一直是true        注意：能量雨游戏只能执行一次，执行后会设置标记防止重复
                Log.record(TAG, "是否可以能量雨游戏: $canPlayGame")

                if (canPlayGame) {
                    // 防止能量雨游戏重复执行
                    val energyRainGameFlag = "EnergyRain::能量雨游戏任务"
                    if (Status.hasFlagToday(energyRainGameFlag)) {
                        break
                    }
                    val hasTaskToProcess = checkAndDoEndGameTask()//检查能量雨 游戏任务 并接取
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    playedCount++
                    // 只有当有实际任务需要处理时才继续循环
                    if (hasTaskToProcess) {
                        continue
                    } else {
                        // 没有任务需要处理，跳出循环
                        Status.setFlagToday(energyRainGameFlag)
                        break
                    }
                }

            /*
                // 3️⃣ 检查能量雨游戏任务
                val energyRainGameFlag = "EnergyRain::能量雨游戏任务"
                if (!Status.hasFlagToday(energyRainGameFlag)) {
                    Log.record(TAG, "检查能量雨游戏任务")
                    val hasTaskToProcess = checkAndDoEndGameTask()//检查能量雨 游戏任务
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    playedCount++
                    // 只有当有实际任务需要处理时才继续循环
                    if (hasTaskToProcess) {
                        continue
                    } else {
                        // 设置能量雨游戏已执行标志
                        Status.setFlagToday(energyRainGameFlag)
                        break
                    }
                } else {
                    // 今天已经执行过能量雨游戏任务，跳出循环
                    break
                }
            */

                break
            } while (playedCount < maxPlayLimit)
            if (playedCount >= maxPlayLimit) {
                Log.record(TAG, "能量雨执行达到单次任务上限($maxPlayLimit)，停止执行")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.record(TAG, "energyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.record(TAG, "energyRain err:")
            Log.printStackTrace(TAG, th)
        }
    }

    /**
     * 开始能量雨（协程版本）
     */
    private suspend fun startEnergyRain() {
        try {
            Log.record("开始执行能量雨🌧️")
            val joStart = JSONObject(AntForestRpcCall.startEnergyRain())

            if (ResChecker.checkRes(TAG, joStart)) {
                val token = joStart.getString("token")
                val bubbleEnergyList = joStart.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList")
                var sum = 0

                for (i in 0 until bubbleEnergyList.length()) {
                    sum += bubbleEnergyList.getInt(i)
                }

                randomDelay(5000, 5200) // 随机延迟 5-5.2秒，模拟真人玩游戏
                val resultJson = JSONObject(AntForestRpcCall.energyRainSettlement(sum, token))

                if (ResChecker.checkRes(TAG, resultJson)) {
                    val s = "收获能量雨🌧️[${sum}g]"
                    Toast.show(s)
                    Log.forest(s)
                }
                randomDelay(300, 400) // 随机延迟 300-400ms
            } else {
                Log.record(TAG, "startEnergyRain: $joStart")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.record(TAG, "startEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.record(TAG, "startEnergyRain err:")
            Log.printStackTrace(TAG, th)
        }
    }

    /**
     * 检查并领取能量雨后的额外游戏任务
     * @return Boolean 是否还有待处理的任务
     */
    @JvmStatic
    private fun checkAndDoEndGameTask(): Boolean {
        try {
            // 1. 查询当前是否有可接或已接的游戏任务
            val response = AntForestRpcCall.queryEnergyRainEndGameList()
            val jo = JSONObject(response)
            if (!ResChecker.checkRes(TAG, jo)) {
                //Log.error(TAG, "查询能量雨游戏任务失败 $jo")
                return false
            }
            // 2. 先处理“有新任务可以接”的情况
            if (jo.optBoolean("needInitTask", false)) {
                // Log.record(TAG, "检测到新任务，准备接入[森林救援队]...")
                val initRes = JSONObject(AntForestRpcCall.initTask("GAME_DONE_SLJYD"))
                if (!ResChecker.checkRes(TAG, initRes)) {
                    // Log.record(TAG, "[森林救援队] 任务接入失败")
                    // 初始化失败，直接返回false
                    return false
                }

                // 3. 核心逻辑：遍历任务列表，检查是否有处于 TO DO 状态的任务
                val groupTask = jo.optJSONObject("energyRainEndGameGroupTask")
                val taskInfoList = groupTask?.optJSONArray("taskInfoList")
                if (taskInfoList != null && taskInfoList.length() > 0) {
                    for (i in 0 until taskInfoList.length()) {
                        val task = taskInfoList.getJSONObject(i)
                        val baseInfo = task.optJSONObject("taskBaseInfo") ?: continue
                        val taskType = baseInfo.optString("taskType")
                        val taskStatus = baseInfo.optString("taskStatus") // 关键状态
                        // 只有当任务是我们要的救援队，且状态是 to do 或还没开始触发时
                        if (taskType == "GAME_DONE_SLJYD") {
                            if (taskStatus == "TODO" || taskStatus == "NOT_TRIGGER") {
                                // Log.record(TAG, "发现待完成任务[$taskType]，当前状态: $taskStatus，开始执行...")
                                // 执行上报逻辑
                                GameTask.Forest_sljyd.report(1)
                                // 完成任务后，检查是否还有更多任务需要处理
                                return true
                            } else if (taskStatus == "FINISHED" || taskStatus == "DONE") {
                                // Log.record(TAG, "任务[$taskType]已完成，无需重复执行")
                                return false
                            }
                        }
                    }
                } else {
                    // 如果列表为空且 needInitTask 也是 false，说明真没任务了
                    if (!jo.optBoolean("needInitTask", false)) {
                        //Log.error(TAG, "当前无任何能量雨附加任务[$jo]")
                        return false
                    }
                }

            }
            // 4. 如果没有找到任何待处理的任务，返回false
            return false
        } catch (th: Throwable) {
            //Log.printStackTrace(TAG, "执行能量雨后续任务出错:", th)
            return false
        }
    }

    /**
     * 兼容Java调用的包装方法
     */
    @JvmStatic
    fun execEnergyRainCompat() {
        kotlinx.coroutines.runBlocking {
            execEnergyRain()
        }
    }
}


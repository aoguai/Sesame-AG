package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.TaskStatus
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 森林寻宝任务处理类 (每天自动执行, 完成后标记)
 */
class ForestChouChouLe {

    companion object {
        private const val TAG = "ForestChouChouLe"
        private const val SOURCE = "task_entry"

        // 场景代码常量
        private const val SCENE_NORMAL = "ANTFOREST_NORMAL_DRAW"
        private const val SCENE_ACTIVITY = "ANTFOREST_ACTIVITY_DRAW"

        // 屏蔽的任务类型关键词
        private val BLOCKED_TYPES = setOf(
            "FOREST_NORMAL_DRAW_SHARE",
            "FOREST_ACTIVITY_DRAW_SHARE",
            "FOREST_ACTIVITY_DRAW_XS" // 玩游戏得新机会
        )

        // 屏蔽的任务名称关键词
        private val BLOCKED_NAMES = setOf("玩游戏得", "开宝箱")

        /**
         * 抽奖场景数据类
         */
        private data class Scene(
            val id: String,
            val code: String,
            val name: String,
            val flag: String
        ) {
            val taskCode get() = "${code}_TASK"
        }

        // 扩展函数：简化 JSON 解析和检查
        private fun String.toJson(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()
        private fun JSONObject.check(): Boolean = ResChecker.checkRes(TAG, this)

        // 动态获取抽奖场景配置
        private fun getScenes(): List<Scene> {
            val defaultScenes = listOf(
                Scene("2025112701", SCENE_NORMAL, "森林寻宝", "forest::chouChouLe::normal::completed"),
                Scene("20251024", SCENE_ACTIVITY, "森林寻宝IP", "forest::chouChouLe::activity::completed")
            )

            return runCatching {
                val scenes = mutableListOf<Scene>()
                // 使用普通场景查询
                val response = AntForestRpcCall.enterDrawActivityopengreen("", SCENE_NORMAL, SOURCE).toJson() ?: return@runCatching defaultScenes

                if (response.optBoolean("success", false)) {
                    val drawSceneGroups = response.optJSONArray("drawSceneGroups") ?: return@runCatching defaultScenes

                    for (i in 0 until drawSceneGroups.length()) {
                        val sceneGroup = drawSceneGroups.optJSONObject(i) ?: continue
                        val drawActivity = sceneGroup.optJSONObject("drawActivity") ?: continue

                        val activityId = drawActivity.optString("activityId")
                        val sceneCode = drawActivity.optString("sceneCode")
                        val name = sceneGroup.optString("name", "未知活动")

                        val flag = when (sceneCode) {
                            SCENE_NORMAL -> "forest::chouChouLe::normal::completed"
                            SCENE_ACTIVITY -> "forest::chouChouLe::activity::completed"
                            else -> "forest::chouChouLe::${sceneCode.lowercase(Locale.getDefault())}::completed"
                        }
                        scenes.add(Scene(activityId, sceneCode, name, flag))
                    }
                }
                if (scenes.isEmpty()) defaultScenes else scenes
            }.getOrElse {
                Log.printStackTrace(TAG, "获取抽奖场景配置失败, 使用默认配置", it)
                defaultScenes
            }
        }
    }

    private val taskTryCount = ConcurrentHashMap<String, AtomicInteger>()

    fun chouChouLe() {
        runCatching {
            val scenes = getScenes()
            if (scenes.all { Status.hasFlagToday(it.flag) }) {
                Log.record("⏭️ 今天所有森林寻宝任务已完成, 跳过执行")
                return
            }

            Log.record("开始处理森林寻宝, 共 ${scenes.size} 个场景")
            scenes.forEach {
                processScene(it)
                sleepCompat(100L)
            }
        }.onFailure { Log.printStackTrace(TAG, "执行异常", it) }
    }

    private fun processScene(s: Scene) = runCatching {
        if (Status.hasFlagToday(s.flag)) {
            Log.record("⏭️ ${s.name} 今天已完成, 跳过")
            return@runCatching
        }

        Log.record("👉 开始处理: ${s.name}")

        // 1. 检查活动有效期
        val enterResp = AntForestRpcCall.enterDrawActivityopengreen(s.id, s.code, SOURCE).toJson()
        if (enterResp == null || !enterResp.check()) return@runCatching

        val drawActivity = enterResp.optJSONObject("drawActivity")
        if (drawActivity != null) {
            val now = System.currentTimeMillis()
            val startTime = drawActivity.optLong("startTime")
            val endTime = drawActivity.optLong("endTime")
            if (now !in startTime..endTime) {
                Log.record("⛔ ${s.name} 活动不在有效期内, 跳过")
                return@runCatching
            }
        }

        // 2. 循环处理任务 (执行 -> 领取)
        processTasksLoop(s)

        // 3. 执行抽奖
        processLottery(s)

        // 4. 最终检查完成状态
        checkCompletion(s)

    }.onFailure { Log.printStackTrace(TAG, "${s.name} 处理异常", it) }

    /**
     * 循环处理任务列表
     */
    private fun processTasksLoop(s: Scene) {
        repeat(3) { loop ->
            Log.record("${s.name} 第 ${loop + 1} 轮任务检查")
            val tasksResp = AntForestRpcCall.listTaskopengreen(s.taskCode, SOURCE).toJson() ?: return@repeat
            if (!tasksResp.check()) return@repeat

            val taskList = tasksResp.optJSONArray("taskInfoList") ?: return@repeat
            var hasChange = false

            for (i in 0 until taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue
                if (processSingleTask(s, task)) {
                    hasChange = true
                }
            }

            if (!hasChange) {
                Log.record("${s.name} 本轮无任务状态变更, 结束任务循环")
                return
            }
            if (loop < 2) sleepCompat(100L)
        }
    }

    /**
     * 执行抽奖逻辑
     */
    private fun processLottery(s: Scene) {
        val enterResp = AntForestRpcCall.enterDrawActivityopengreen(s.id, s.code, SOURCE).toJson() ?: return
        if (!enterResp.check()) return

        val drawAsset = enterResp.optJSONObject("drawAsset") ?: return
        var balance = drawAsset.optInt("blance", 0)
        val total = drawAsset.optInt("totalTimes", 0)

        Log.record("${s.name} 剩余抽奖次数: $balance / $total")

        var retry = 0
        // 最多抽50次，防止死循环
        while (balance > 0 && retry < 50) {
            retry++
            Log.record("${s.name} 第 $retry 次抽奖")

            val drawResp = AntForestRpcCall.drawopengreen(s.id, s.code, SOURCE, UserMap.currentUid).toJson()
            if (drawResp == null || !drawResp.check()) {
                break
            }

            balance = drawResp.optJSONObject("drawAsset")?.optInt("blance", 0) ?: 0
            val prize = drawResp.optJSONObject("prizeVO")
            if (prize != null) {
                val name = prize.optString("prizeName", "未知奖品")
                val num = prize.optInt("prizeNum", 1)
                Log.forest("${s.name} 🎁 [获得: $name * $num] 剩余次数: $balance")
            }

            if (balance > 0) sleepCompat(100L)
        }
    }

    /**
     * 检查是否所有任务都已完成，并设置 Flag
     */
    private fun checkCompletion(s: Scene) {
        val resp = AntForestRpcCall.listTaskopengreen(s.taskCode, SOURCE).toJson() ?: return
        if (!resp.check()) return

        val taskList = resp.optJSONArray("taskInfoList") ?: return
        var total = 0
        var completed = 0
        var allDone = true

        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i) ?: continue
            val baseInfo = task.optJSONObject("taskBaseInfo") ?: continue

            val taskType = baseInfo.optString("taskType")
            val taskStatus = baseInfo.optString("taskStatus")
            val bizInfoStr = baseInfo.optString("bizInfo")
            val taskName = if (bizInfoStr.isNotEmpty()) {
                JSONObject(bizInfoStr).optString("title", taskType)
            } else taskType

            if (isBlockedTask(taskType, taskName)) continue

            total++
            if (taskStatus == TaskStatus.RECEIVED.name) {
                completed++
            } else {
                allDone = false
                Log.record("${s.name} 未完成: $taskName [$taskStatus]")
            }
        }

        Log.record("${s.name} 进度: $completed / $total")
        if (allDone) {
            Status.setFlagToday(s.flag)
            val msg = if (total > 0) "全部完成" else "无有效任务"
            Log.record("✅ ${s.name} $msg ($completed/$total)")
        } else {
            Log.record("⚠️ ${s.name} 未全部完成")
        }
    }

    /**
     * 判断任务是否在屏蔽列表中
     */
    private fun isBlockedTask(taskType: String, taskName: String): Boolean {
        return BLOCKED_TYPES.any { taskType.contains(it) } ||
                BLOCKED_NAMES.any { taskName.contains(it) }
    }

    /**
     * 处理单个任务分发
     * @return 任务状态是否有变更
     */
    private fun processSingleTask(s: Scene, task: JSONObject): Boolean {
        val baseInfo = task.optJSONObject("taskBaseInfo") ?: return false
        val bizInfoStr = baseInfo.optString("bizInfo")
        val bizInfo = if (bizInfoStr.isNotEmpty()) JSONObject(bizInfoStr) else JSONObject()

        val taskName = bizInfo.optString("title", "未知任务")
        val taskCode = baseInfo.optString("sceneCode")
        val taskStatus = baseInfo.optString("taskStatus")
        val taskType = baseInfo.optString("taskType")

        if (isBlockedTask(taskType, taskName)) return false

        Log.record("${s.name} 任务: $taskName [$taskStatus]")

        return when (taskStatus) {
            TaskStatus.TODO.name -> handleTodoTask(s, taskName, taskCode, taskType)
            TaskStatus.FINISHED.name -> handleFinishedTask(s, taskName, taskCode, taskType)
            else -> false
        }
    }

    private fun handleTodoTask(s: Scene, name: String, code: String, type: String): Boolean {
        return if (type == "NORMAL_DRAW_EXCHANGE_VITALITY") {
            // 活力值兑换
            Log.record("${s.name} 兑换活力值: $name")
            val res = AntForestRpcCall.exchangeTimesFromTaskopengreen(s.id, s.code, SOURCE, code, type).toJson()
            if (res != null && res.check()) {
                Log.forest("${s.name} 🧾 $name 兑换成功")
                true
            } else false
        } else if (type.startsWith("FOREST_NORMAL_DRAW") || type.startsWith("FOREST_ACTIVITY_DRAW")) {
            // 普通任务
            Log.record("${s.name} 执行任务(模拟耗时): $name")
            sleepCompat(100L) //

            val result = if (type.contains("XLIGHT")) {
                AntForestRpcCall.finishTask4Chouchoule(type, code)
            } else {
                AntForestRpcCall.finishTaskopengreen(type, code)
            }

            val resJson = result.toJson()
            if (resJson != null && resJson.check()) {
                Log.forest("${s.name} 🧾 $name")
                true
            } else {
                val count = taskTryCount.computeIfAbsent(type) { AtomicInteger(0) }.incrementAndGet()
                Log.error(TAG, "${s.name} 任务失败($count): $name")
                false
            }
        } else {
            false
        }
    }

    private fun handleFinishedTask(s: Scene, name: String, code: String, type: String): Boolean {
        Log.record("${s.name} 领取奖励: $name")
        sleepCompat(100L)
        val res = AntForestRpcCall.receiveTaskAwardopengreen(SOURCE, code, type).toJson()
        return if (res != null && res.check()) {
            Log.forest("${s.name} 🧾 $name 奖励领取成功")
            true
        } else {
            Log.error(TAG, "${s.name} 奖励领取失败: $name")
            false
        }
    }
}
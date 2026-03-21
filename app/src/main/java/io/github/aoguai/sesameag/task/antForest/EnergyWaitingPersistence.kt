package io.github.aoguai.sesameag.task.antForest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import io.github.aoguai.sesameag.util.DataStore
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 蹲点任务持久化数据类
 * 用于序列化和反序列化，存储到 DataStore
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WaitingTaskPersistData(
    val userId: String = "",
    val userName: String = "",
    val bubbleId: Long = 0L,
    val produceTime: Long = 0L,
    val fromTag: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val shieldEndTime: Long = 0L,
    val bombEndTime: Long = 0L,
    val savedTime: Long = System.currentTimeMillis() // 保存时间，用于判断是否过期
) {
    /**
     * 转换为运行时任务对象
     */
    fun toWaitingTask(): EnergyWaitingManager.WaitingTask {
        return EnergyWaitingManager.WaitingTask(
            userId = userId,
            userName = userName,
            bubbleId = bubbleId,
            produceTime = produceTime,
            fromTag = fromTag,
            retryCount = retryCount,
            maxRetries = maxRetries,
            shieldEndTime = shieldEndTime,
            bombEndTime = bombEndTime
        )
    }

    companion object {
        /**
         * 从运行时任务对象创建持久化数据
         */
        fun fromWaitingTask(task: EnergyWaitingManager.WaitingTask): WaitingTaskPersistData {
            return WaitingTaskPersistData(
                userId = task.userId,
                userName = task.userName,
                bubbleId = task.bubbleId,
                produceTime = task.produceTime,
                fromTag = task.fromTag,
                retryCount = task.retryCount,
                maxRetries = task.maxRetries,
                shieldEndTime = task.shieldEndTime,
                bombEndTime = task.bombEndTime
            )
        }
    }
}

/**
 * 蹲点任务持久化管理器
 *
 * 职责：
 * 1. 保存蹲点任务到 DataStore
 * 2. 从 DataStore 恢复蹲点任务
 * 3. 验证恢复的任务是否仍然有效
 * 4. 过滤过期或无效的任务
 */
object EnergyWaitingPersistence {
    private const val TAG = "EnergyWaitingPersistence"

    // 任务最大保存时间（8小时，超过此时间的任务视为过期）
    private const val MAX_TASK_AGE_MS = 8 * 60 * 60 * 1000L

    // 协程作用域
    private val persistenceScope = CoroutineScope(Dispatchers.IO)

    /**
     * 获取当前账号的 DataStore 存储键
     * 每个账号使用独立的键，避免多账号切换时数据混淆
     *
     * @return 包含当前用户 uid 的存储键，如果 uid 为空则使用默认键
     */
    private fun getDataStoreKey(): String {
        val currentUid = UserMap.currentUid
        return if (currentUid.isNullOrEmpty()) {
            "energy_waiting_tasks_default"
        } else {
            "energy_waiting_tasks_$currentUid"
        }
    }

    /**
     * 保存蹲点任务到 DataStore（异步）
     *
     * @param tasks 当前活跃的蹲点任务
     */
    fun saveTasks(tasks: Map<String, EnergyWaitingManager.WaitingTask>) {
        persistenceScope.launch {
            try {
                val persistDataList = tasks.values.map { task ->
                    WaitingTaskPersistData.fromWaitingTask(task)
                }

                val dataStoreKey = getDataStoreKey()
                DataStore.put(dataStoreKey, persistDataList)

                Log.record(TAG, "✅ 保存${persistDataList.size}个蹲点任务到持久化存储 (key: $dataStoreKey)")
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "保存蹲点任务失败:", e)
            }
        }
    }

    /**
     * 从 DataStore 加载蹲点任务
     *
     * @return 恢复的任务列表（已过滤过期任务）
     */
    fun loadTasks(): List<EnergyWaitingManager.WaitingTask> {
        return try {
            val dataStoreKey = getDataStoreKey()
            val typeRef = object : TypeReference<List<WaitingTaskPersistData>>() {}
            val persistDataList = DataStore.getOrCreate(dataStoreKey, typeRef)

            if (persistDataList.isEmpty()) {
                Log.record(TAG, "持久化存储中无蹲点任务 (key: $dataStoreKey)")
                return emptyList()
            }

            val currentTime = System.currentTimeMillis()
            val validTasks = mutableListOf<EnergyWaitingManager.WaitingTask>()
            var expiredCount = 0
            var tooOldCount = 0

            persistDataList.forEach { persistData ->
                // 检查1：任务保存时间是否过久
                val taskAge = currentTime - persistData.savedTime
                if (taskAge > MAX_TASK_AGE_MS) {
                    tooOldCount++
                    Log.record(TAG, "  跳过[${persistData.userName}]：保存时间超过${taskAge / 1000 / 60 / 60}小时")
                    return@forEach
                }

                // 检查2：能量是否已经过期超过1小时
                if (currentTime > persistData.produceTime + 60 * 60 * 1000L) {
                    expiredCount++
                    Log.record(TAG, "  跳过[${persistData.userName}]：能量已过期超过1小时")
                    return@forEach
                }

                // 任务有效，添加到列表
                validTasks.add(persistData.toWaitingTask())
            }

            Log.record(TAG, "📥 从持久化存储恢复${validTasks.size}个有效任务（跳过${expiredCount}个过期，${tooOldCount}个过旧）")

            validTasks
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "加载蹲点任务失败:", e)
            emptyList()
        }
    }

    /**
     * 清空持久化存储中的所有任务
     */
    fun clearTasks() {
        try {
            val dataStoreKey = getDataStoreKey()
            DataStore.put(dataStoreKey, emptyList<WaitingTaskPersistData>())
            Log.record(TAG, "清空持久化存储 (key: $dataStoreKey)")
        } catch (e: Exception) {
            Log.error(TAG, "清空持久化存储失败: ${e.message}")
        }
    }

    /**
     * 验证并重新添加恢复的任务
     *
     * @param tasks 恢复的任务列表
     * @param addTaskCallback 添加任务的回调函数
     * @return 实际重新添加的任务数量
     */
    suspend fun validateAndRestoreTasks(
        tasks: List<EnergyWaitingManager.WaitingTask>,
        addTaskCallback: suspend (EnergyWaitingManager.WaitingTask) -> Boolean
    ): Int {
        if (tasks.isEmpty()) {
            return 0
        }

        Log.record(TAG, "🔄 开始验证${tasks.size}个恢复的蹲点任务...")

        var restoredCount = 0
        var skippedCount = 0

        tasks.forEach { task ->
            try {
                // 重新查询用户主页以获取最新保护罩状态
                val userHomeResponse = AntForestRpcCall.queryFriendHomePage(task.userId, null)

                if (userHomeResponse.isNullOrEmpty()) {
                    Log.record(TAG, "  验证[${task.userName}]：无法获取主页信息，跳过恢复")
                    skippedCount++
                    return@forEach
                }

                val userHomeObj = org.json.JSONObject(userHomeResponse)

                // 自己的账号：无论是否有保护罩都要恢复（到时间后直接收取）
                if (task.isSelf()) {
                    val success = addTaskCallback(task)
                    if (success) {
                        restoredCount++
                        Log.record(
                            TAG,
                            "  ⭐️ 恢复[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：能量${TimeUtil.getCommonDate(task.produceTime)}成熟，到时间直接收取"
                        )
                    } else {
                        skippedCount++
                    }
                    return@forEach
                }

                // 好友账号：如果保护罩覆盖能量成熟期则跳过
                if (ForestUtil.shouldSkipWaitingDueToProtection(userHomeObj, task.produceTime)) {
                    val protectionEndTime = ForestUtil.getProtectionEndTime(userHomeObj)
                    val timeDifference = protectionEndTime - task.produceTime
                    val hours = timeDifference / (1000 * 60 * 60)
                    val minutes = (timeDifference % (1000 * 60 * 60)) / (1000 * 60)

                    Log.record(
                        TAG,
                        "  ❌ 跳过[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：保护罩覆盖能量成熟期(${hours}小时${minutes}分钟)"
                    )
                    skippedCount++
                } else {
                    // 好友任务有效，重新添加
                    val success = addTaskCallback(task)
                    if (success) {
                        restoredCount++
                        Log.record(TAG, "  ✅ 恢复[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：能量${TimeUtil.getCommonDate(task.produceTime)}成熟")
                    } else {
                        skippedCount++
                    }
                }

                // 添加短暂延迟，避免请求过快
                kotlinx.coroutines.delay(200)
            } catch (e: Exception) {
                Log.record(TAG, "  验证任务[${task.userName}]时出错: ${e.message}，跳过")
                skippedCount++
            }
        }

        Log.record(TAG, "✅ 恢复完成：成功${restoredCount}个，跳过${skippedCount}个")

        return restoredCount
    }
}


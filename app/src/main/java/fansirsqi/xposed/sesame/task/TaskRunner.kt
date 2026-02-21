package fansirsqi.xposed.sesame.task

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.customTasks.ManualTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 协程任务执行器 (优化版)
 *
 * 核心改进:
 * 1. **并发执行**: 支持任务并发运行，缩短总耗时。
 * 2. **生命周期**: 绑定到调用者的生命周期，防止泄漏。
 * 3. **逻辑简化**: 移除复杂的宽限期嵌套，使用标准的协程超时机制。
 */
class CoroutineTaskRunner(allModels: List<Model>) {

    companion object {
        private const val TAG = "CoroutineTaskRunner"
        private const val DEFAULT_TASK_TIMEOUT = 10 * 60 * 1000L // 10分钟

        // 最大并发数，防止请求过于频繁触发风控
        // 可以做成配置项，目前硬编码为 3
        private const val MAX_CONCURRENCY = 3

        private val TIMEOUT_WHITELIST = setOf("森林", "庄园", "运动")
    }

    private val taskList: List<ModelTask> = allModels.filterIsInstance<ModelTask>()

    // 统计数据
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val skippedCount = AtomicInteger(0)
    private val taskExecutionTimes = ConcurrentHashMap<String, Long>()

    /**
     * 启动任务执行流程
     * 注意：现在这是一个 suspend 函数，需要在一个协程作用域内调用
     */
    suspend fun run(
        isFirst: Boolean = true,
        rounds: Int = BaseModel.taskExecutionRounds.value
    ) = coroutineScope { // 使用 coroutineScope 创建子作用域
        val startTime = System.currentTimeMillis()

        // 【互斥检查】如果手动任务流正在运行，则跳过本次自动执行
        if (ManualTask.isManualRunning) {
            Log.record(TAG, "⏸ 检测到“手动庄园任务流”正在运行中，跳过本次自动任务调度")
            return@coroutineScope
        }

        if (isFirst) {
            ApplicationHook.updateDay()
            resetCounters()
        }

        try {
            Log.record(TAG, "🚀 开始执行任务流程 (并发数: $MAX_CONCURRENCY)")

            CustomSettings.loadForTaskRunner()
            val status = CustomSettings.getOnceDailyStatus(enableLog = true)

            // 执行多轮任务
            repeat(rounds) { roundIndex ->
                val round = roundIndex + 1
                executeRound(round, rounds, status)
            }

            if (CustomSettings.onlyOnceDaily.value) {
                // 确保时间状态是最新的
                TaskCommon.update()
                if (TaskCommon.IS_MODULE_SLEEP_TIME) {
                    Log.record(TAG, "💤 当前处于模块休眠时间，不设置 OnceDaily::Finished 标记")
                } else {
                    Status.setFlagToday("OnceDaily::Finished")
                }
            }

        } catch (e: CancellationException) {
            Log.record(TAG, "🚫 任务流程被取消")
            throw e
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "任务流程异常", e)
        } finally {
            printExecutionSummary(startTime, System.currentTimeMillis())
            scheduleNext()
        }
    }

    /**
     * 执行一轮任务 (并发模式)
     */
    private suspend fun executeRound(round: Int, totalRounds: Int, status: CustomSettings.OnceDailyStatus) = coroutineScope {
        val roundStartTime = System.currentTimeMillis()

        // 1. 筛选任务
        val tasksToRun = taskList.filter { task ->
            task.isEnable && !CustomSettings.isOnceDailyBlackListed(task.getName(), status)
        }

        val excludedCount = taskList.count { it.isEnable } - tasksToRun.size
        if (excludedCount > 0) skippedCount.addAndGet(excludedCount)

        Log.record(TAG, "🔄 [第 $round/$totalRounds 轮] 开始，共 ${tasksToRun.size} 个任务")

        // 2. 并发执行
        // 使用 Semaphore 限制并发数量
        val semaphore = Semaphore(MAX_CONCURRENCY)

        // 创建所有任务的 Deferred 对象
        val deferreds = tasksToRun.map { task ->
            async {
                // 【互斥检查】再次检查手动任务，防止并发启动
                if (ManualTask.isManualRunning) {
                     Log.record(TAG, "⏸ 任务 ${task.getName()} 因手动模式启动而中止")
                     return@async
                }
                semaphore.withPermit {
                    executeSingleTask(task, round)
                }
            }
        }

        // 3. 等待本轮所有任务完成
        deferreds.awaitAll()

        val roundTime = System.currentTimeMillis() - roundStartTime
        Log.record(TAG, "✅ [第 $round/$totalRounds 轮] 结束，耗时: ${roundTime}ms")
    }

    /**
     * 执行单个任务
     */
    private suspend fun executeSingleTask(task: ModelTask, round: Int) {
        val taskName = task.getName() ?: "未知任务"
        val taskId = "$taskName-R$round"
        val startTime = System.currentTimeMillis()

        val isWhitelist = TIMEOUT_WHITELIST.contains(taskName)

        // 如果是白名单任务（如森林），它们往往是“启动后即视为完成”，或者是长运行任务
        // 我们可以给一个较短的“启动超时时间”，而不是等待整个任务结束
        val timeout = if (isWhitelist) 30_000L else DEFAULT_TASK_TIMEOUT

        try {
            Log.record(TAG, "▶️ 启动: $taskId")
            task.addRunCents()

            withTimeout(timeout) {
                // startTask 是一个 suspend 函数，或者返回一个 Job
                // 假设 task.startTask 现在是 suspend 的，或者我们 wrap 一下
                val job = task.startTask(force = false, rounds = 1)

                // 如果是白名单任务，我们只等待它启动成功（job active），不 join
                if (isWhitelist) {
                    if (job.isActive) {
                        Log.record(TAG, "✨ $taskId 启动成功 (后台运行中)")
                        return@withTimeout
                    }
                }

                // 普通任务等待完成
                job.join()
            }

            // 成功
            val time = System.currentTimeMillis() - startTime
            successCount.incrementAndGet()
            taskExecutionTimes[taskId] = time
            Log.record(TAG, "✅ 完成: $taskId (耗时: ${time}ms)")

        } catch (e: TimeoutCancellationException) {
            val time = System.currentTimeMillis() - startTime

            if (isWhitelist) {
                // 白名单任务超时通常意味着它还在后台跑，视作成功
                successCount.incrementAndGet()
                taskExecutionTimes[taskId] = time
                Log.record(TAG, "✅ $taskId 已运行 ${time}ms (后台继续)")
            } else {
                // 普通任务超时 -> 失败
                failureCount.incrementAndGet()
                Log.error(TAG, "⏰ 超时: $taskId (${time}ms > ${timeout}ms)")
                // 尝试停止任务
                task.stopTask()
            }

        } catch (e: Exception) {
            val time = System.currentTimeMillis() - startTime
            failureCount.incrementAndGet()
            Log.error(TAG, "❌ 失败: $taskId (${e.message})")
        }
    }

    private fun scheduleNext() {
        try {
            ApplicationHook.scheduleNextExecutionInternal(ApplicationHook.lastExecTime)
            Log.record(TAG, "📅 已调度下次执行")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "调度失败", e)
        }
    }

    private fun resetCounters() {
        successCount.set(0)
        failureCount.set(0)
        skippedCount.set(0)
        taskExecutionTimes.clear()
    }

    @SuppressLint("DefaultLocale")
    private fun printExecutionSummary(startTime: Long, endTime: Long) {
        val totalTime = endTime - startTime
        val avgTime = if (taskExecutionTimes.isNotEmpty()) taskExecutionTimes.values.average() else 0.0

        Log.record(TAG, "📈 === 执行统计 (并发模式) ===")
        Log.record(TAG, "⏱️ 总耗时: ${totalTime}ms")
        Log.record(TAG, "✅ 成功: ${successCount.get()} | ❌ 失败: ${failureCount.get()} | ⏭️ 跳过: ${skippedCount.get()}")
        if (taskExecutionTimes.isNotEmpty()) {
            Log.record(TAG, "⚡ 平均耗时: %.0fms".format(avgTime))
        }

        val nextTime = ApplicationHook.nextExecutionTime
        if (nextTime > 0) {
            Log.record(TAG, "📅 下次: ${TimeUtil.getCommonDate(nextTime)}")
        }
        Log.record(TAG, "============================")
    }
}
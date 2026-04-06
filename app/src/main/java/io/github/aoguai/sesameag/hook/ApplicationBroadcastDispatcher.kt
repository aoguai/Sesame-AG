package io.github.aoguai.sesameag.hook

import android.content.Context
import android.content.Intent
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.task.customTasks.CustomTask
import io.github.aoguai.sesameag.task.customTasks.ManualTask
import io.github.aoguai.sesameag.task.customTasks.ManualTaskModel
import io.github.aoguai.sesameag.util.GlobalThreadPools.execute
import io.github.aoguai.sesameag.util.Log.record
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.WorkflowRootGuard

internal object ApplicationBroadcastDispatcher {
    private const val TAG = ApplicationHook.TAG

    fun handleReceive(context: Context?, intent: Intent?) {
        val safeIntent = intent ?: return
        val action = safeIntent.action ?: return

        val finalProcessName = ApplicationHook.finalProcessName
        if (finalProcessName != null && finalProcessName.endsWith(":widgetProvider")) {
            return
        }

        when (action) {
            ApplicationHookConstants.BroadcastActions.RESTART -> handleRestartBroadcast(safeIntent)
            ApplicationHookConstants.BroadcastActions.EXECUTE -> handleExecuteBroadcast(safeIntent)
            ApplicationHookConstants.BroadcastActions.PRE_WAKEUP -> handlePreWakeupBroadcast(context, safeIntent)
            ApplicationHookConstants.BroadcastActions.RE_LOGIN -> ApplicationHook.reOpenApp()
            ApplicationHookConstants.BroadcastActions.RPC_TEST -> handleRpcTest(safeIntent)
            ApplicationHookConstants.BroadcastActions.MANUAL_TASK -> handleManualTaskBroadcast(safeIntent)
        }
    }

    private fun handleRestartBroadcast(intent: Intent) {
        val safeIntent = Intent(intent)
        ApplicationHookConstants.submitEntry("broadcast_restart") {
            val targetUserId = safeIntent.getStringExtra("userId")
            val currentUserId = HookUtil.getUserId(ApplicationHook.classLoader!!)
            if (targetUserId != null && targetUserId != currentUserId) {
                record(TAG, "忽略非当前用户的重启广播: target=$targetUserId, current=$currentUserId")
                return@submitEntry
            }
            val restartReason =
                if (safeIntent.getBooleanExtra("configReload", false)) {
                    "config_reload"
                } else {
                    "broadcast_restart"
                }
            ApplicationHook.restartWorkflow(restartReason)
        }
    }

    private fun handleExecuteBroadcast(intent: Intent) {
        val safeIntent = Intent(intent)
        val isAlarmTriggered = safeIntent.getBooleanExtra("alarm_triggered", false)
        val wakenAtTime = safeIntent.getBooleanExtra("waken_at_time", false)
        val wakenTime = safeIntent.getStringExtra("waken_time")?.trim().takeIf { !it.isNullOrBlank() }
        val normalizedWakenTime = if (wakenAtTime && wakenTime.isNullOrBlank()) "0000" else wakenTime

        val trigger = ApplicationHookConstants.TriggerInfo(
            type = ApplicationHookConstants.TriggerType.BROADCAST_EXECUTE,
            priority = if (isAlarmTriggered || wakenAtTime) {
                ApplicationHookConstants.TriggerPriority.HIGH
            } else {
                ApplicationHookConstants.TriggerPriority.NORMAL
            },
            alarmTriggered = isAlarmTriggered,
            wakenAtTime = wakenAtTime,
            wakenTime = normalizedWakenTime,
            reason = "broadcast_execute",
            dedupeKey = when {
                wakenAtTime && !normalizedWakenTime.isNullOrBlank() -> "wakeup_$normalizedWakenTime"
                isAlarmTriggered -> "alarm_execute"
                else -> "broadcast_execute"
            }
        )

        ApplicationHookConstants.submitEntry("broadcast_execute") {
            if (!ApplicationHook.isReadyForExec()) {
                record(TAG, "execute broadcast received but not ready: ${ApplicationHook.readinessSummary()}")
            }
            ApplicationHookCore.requestExecution(trigger)
        }
    }

    private fun handlePreWakeupBroadcast(context: Context?, intent: Intent) {
        val ctx = context?.applicationContext ?: context ?: return
        val safeIntent = Intent(intent)
        val executionTimeMillis = safeIntent.getLongExtra("execution_time", 0L)
        val now = System.currentTimeMillis()
        val delayMillis = executionTimeMillis - now

        val triggerAtExecTime = ApplicationHookConstants.TriggerInfo(
            type = ApplicationHookConstants.TriggerType.BROADCAST_PREWAKEUP,
            priority = ApplicationHookConstants.TriggerPriority.HIGH,
            alarmTriggered = true,
            reason = if (executionTimeMillis > 0) "prewakeup_to_${TimeUtil.getCommonDate(executionTimeMillis)}" else "prewakeup",
            dedupeKey = if (executionTimeMillis > 0) "prewakeup_$executionTimeMillis" else "prewakeup"
        )

        io.github.aoguai.sesameag.hook.keepalive.SmartSchedulerManager.initialize(ctx)
        if (executionTimeMillis > 0 && delayMillis > 0) {
            record(TAG, "收到 prewakeup，计划在 ${TimeUtil.getCommonDate(executionTimeMillis)} 执行 (delay=${TimeUtil.formatDuration(delayMillis)})")
            io.github.aoguai.sesameag.hook.keepalive.SmartSchedulerManager.schedule(delayMillis, "prewakeup_execute") {
                ApplicationHookCore.requestExecution(triggerAtExecTime)
            }
            return
        }

        record(TAG, "收到 prewakeup，但 execution_time 无效/已过期，立即触发一次执行链路")
        ApplicationHookCore.requestExecution(triggerAtExecTime)
    }

    private fun handleManualTaskBroadcast(intent: Intent) {
        record(TAG, "🚀 收到手动庄园任务指令")
        execute {
            val taskName = intent.getStringExtra("task")
            if (taskName != null) {
                val normalizedTaskName = taskName.replace("+", "_")
                try {
                    val task = CustomTask.valueOf(normalizedTaskName)
                    val extraParams = HashMap<String, Any>()
                    when (task) {
                        CustomTask.FOREST_WHACK_MOLE -> {
                            extraParams["whackMoleMode"] = intent.getIntExtra("whackMoleMode", 1)
                            extraParams["whackMoleGames"] = intent.getIntExtra("whackMoleGames", 5)
                        }

                        CustomTask.FOREST_ENERGY_RAIN -> {
                            extraParams["exchangeEnergyRainCard"] = intent.getBooleanExtra("exchangeEnergyRainCard", false)
                        }

                        CustomTask.FARM_SPECIAL_FOOD -> {
                            extraParams["specialFoodCount"] = intent.getIntExtra("specialFoodCount", 0)
                        }

                        CustomTask.FARM_USE_TOOL -> {
                            extraParams["toolType"] = intent.getStringExtra("toolType") ?: ""
                            extraParams["toolCount"] = intent.getIntExtra("toolCount", 1)
                        }

                        else -> {
                            record(TAG, "❌ 无效的任务指令: $taskName")
                        }
                    }
                    if (!WorkflowRootGuard.hasRoot(forceRefresh = true, reason = "manual_task")) {
                        record(TAG, "⛔ 未检测到可用执行权限，忽略手动任务指令: $taskName")
                        return@execute
                    }
                    if (!ApplicationHook.ensureLegalAcceptedForWorkflow()) {
                        record(TAG, "⛔ 未勾选 LEGAL 说明确认，忽略手动任务指令: $taskName")
                        return@execute
                    }
                    ManualTask.runSingle(task, extraParams)
                } catch (e: Exception) {
                    record(TAG, "❌ 无效的任务指令: $taskName -> ${e.message}")
                }
            } else {
                if (!WorkflowRootGuard.hasRoot(forceRefresh = true, reason = "manual_task_model")) {
                    record(TAG, "⛔ 未检测到可用执行权限，忽略手动模型任务指令")
                    return@execute
                }
                if (!ApplicationHook.ensureLegalAcceptedForWorkflow()) {
                    record(TAG, "⛔ 未勾选 LEGAL 说明确认，忽略手动模型任务指令")
                    return@execute
                }
                for (model in Model.modelArray) {
                    if (model is ManualTaskModel) {
                        model.startTask(true, 1)
                        break
                    }
                }
            }
        }
    }

    private fun handleRpcTest(intent: Intent) {
        execute {
            record(TAG, "RPC测试: $intent")
            try {
                val rpc = io.github.aoguai.sesameag.hook.rpc.debug.DebugRpc()
                rpc.start(
                    intent.getStringExtra("method") ?: "",
                    intent.getStringExtra("data") ?: "",
                    intent.getStringExtra("type") ?: ""
                )
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}

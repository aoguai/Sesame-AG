package io.github.aoguai.sesameag.model

import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField.MultiplyIntegerModelField
import io.github.aoguai.sesameag.model.modelFieldExt.TimePointListModelField
import io.github.aoguai.sesameag.model.modelFieldExt.TimeWindowListModelField
import io.github.aoguai.sesameag.model.modelFieldExt.StringModelField
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.maps.BeachMap
import io.github.aoguai.sesameag.util.maps.IdMapManager

/**
 * 基础配置模块
 * 管理全局调度、日志、调试及应用生命周期相关的核心配置
 */
class BaseModel : Model() {
    override fun getName(): String = "基础"
    override fun getGroup(): ModelGroup = ModelGroup.BASE
    override fun getIcon(): String = "BaseModel.png"
    override fun getEnableFieldName(): String = "启用模块"

    override fun getFields(): ModelFields = buildModelFields {
        boolean("stayAwake", "保持唤醒", true, desc = "开启后，模块在延时等待期间会尽量保持 CPU 唤醒；关闭可省电，但后台定时精度可能下降。") { stayAwake = it }
        boolean("manualTriggerAutoSchedule", "手动触发运行", false, desc = "手动打开目标应用时是否补触发一次任务。") { manualTriggerAutoSchedule = it }
        integer("checkInterval", "执行间隔(分钟)", 50, 1, 720, desc = "自动轮询的基础间隔。") {
            checkInterval = MultiplyIntegerModelField(it.code, it.name, it.value, it.minLimit, it.maxLimit, 60000)
        }
        integer("offlineCooldown", "离线冷却(分钟)", 0, 0, 1440, desc = "异常熔断后的冷却时长；0 为随执行间隔。") {
            offlineCooldown = MultiplyIntegerModelField(it.code, it.name, it.value, it.minLimit, it.maxLimit, 60000)
        }
        integer("taskExecutionRounds", "执行轮数", 1, 1, 99, desc = "每次调度执行的重复轮数。") { taskExecutionRounds = it }

        // 辅助方法：快速构建复杂字段并保存引用
        val addTpl = { f: TimePointListModelField -> modelFields.addField(f); f }
        val addTwl = { f: TimeWindowListModelField -> modelFields.addField(f); f }

        execAtTimeList = addTpl(TimePointListModelField("execAtTimeList", "定时执行", "0010,0030,0700,1200,1700,2000,2359", true))
        wakenAtTimeList = addTpl(TimePointListModelField("wakenAtTimeList", "定时唤醒", "0010,0030,0100,0650,2350", true))
        energyTime = addTwl(TimeWindowListModelField("energyTime", "只收能量时间", "0700-0730", true))
        modelSleepTime = addTwl(TimeWindowListModelField("modelSleepTime", "模块休眠时间", "0200-0201", true))

        choice("timedTaskModel", "定时模式", TimedTaskModel.SYSTEM, TimedTaskModel.nickNames, desc = "子任务延时策略：系统模式省电，程序模式更准。") { timedTaskModel = it }
        boolean("timeoutRestart", "超时重启", true, desc = "流程超时后尝试重新拉起应用。") { timeoutRestart = it }
        integer("waitWhenException", "异常等待(分)", 60, desc = "任务异常后的额外挂起时间。") {
            waitWhenException = MultiplyIntegerModelField(it.code, it.name, it.value, it.minLimit, it.maxLimit, 60000)
        }
        boolean("errNotify", "异常通知", false, desc = "熔断或连续异常时发送通知。") { errNotify = it }
        integer("setMaxErrorCount", "异常阈值", 8, desc = "连续异常达此次数后冷却。") { setMaxErrorCount = it }
        boolean("newRpc", "新版接口", true, desc = "优先使用新版 RPC 桥接。") { newRpc = it }
        boolean("debugMode", "开启抓包", false, desc = "写入 Hook 请求响应到抓包日志。") { debugMode = it }
        boolean("sendHookData", "数据转发", false, desc = "转发 Hook 到的数据到指定 URL。", dependency = "debugMode") { sendHookData = it }
        string("sendHookDataUrl", "转发地址", "http://127.0.0.1:9527/hook", dependency = "sendHookData") { sendHookDataUrl = it }
        boolean("batteryPerm", "申请电池优化豁免", true) { batteryPerm = it }
        boolean("recordLog", "记录 record 日志", true) { recordLog = it }
        boolean("runtimeLog", "记录 runtime 日志", false) { runtimeLog = it }
        boolean("showToast", "气泡提示", true) { showToast = it }
        boolean("enableOnGoing", "常驻运行通知", false) { enableOnGoing = it }
        boolean("languageSimplifiedChinese", "强制中文时区", true) { languageSimplifiedChinese = it }
        string("toastPerfix", "气泡前缀", "") { toastPerfix = it }
    }

    interface TimedTaskModel {
        companion object {
            const val SYSTEM = 0
            const val PROGRAM = 1
            val nickNames = arrayOf("🤖系统计时", "📦程序计时")
        }
    }

    companion object {
        private const val TAG = "BaseModel"

        // --- 核心调度配置 ---
        /** 是否尽量保持 CPU 唤醒以提高定时精度 */
        var stayAwake = BooleanModelField("stayAwake", "保持唤醒", true)
        /** 是否在手动切回应用时触发任务 */
        var manualTriggerAutoSchedule = BooleanModelField("manualTriggerAutoSchedule", "手动触发运行", false)
        /** 自动执行的基础轮询间隔（毫秒，默认 50 分钟） */
        var checkInterval = MultiplyIntegerModelField("checkInterval", "执行间隔", 50, 1, 720, 60000)
        /** 异常后的冷却时间（毫秒） */
        var offlineCooldown = MultiplyIntegerModelField("offlineCooldown", "离线冷却", 0, 0, 1440, 60000)
        /** 每次触发时运行的任务轮数 */
        var taskExecutionRounds = IntegerModelField("taskExecutionRounds", "执行轮数", 1, 1, 99)

        // --- 时间列表配置 ---
        /** 自动执行任务的时间点列表 */
        var execAtTimeList = TimePointListModelField("execAtTimeList", "定时执行", "0010,0700,1200,1700,2000,2359", true)
        /** 唤醒应用的时间点列表 */
        var wakenAtTimeList = TimePointListModelField("wakenAtTimeList", "定时唤醒", "0010,0100,0650,2350", true)
        /** 限制只做核心任务（如森林）的时间段 */
        var energyTime = TimeWindowListModelField("energyTime", "只收能量时间", "0700-0730", true)
        /** 停止执行任务的休眠时段 */
        var modelSleepTime = TimeWindowListModelField("modelSleepTime", "模块休眠时间", "0200-0201", true)

        // --- 容错与调试配置 ---
        /** 定时任务执行模式（系统/程序） */
        var timedTaskModel = ChoiceModelField("timedTaskModel", "定时模式", TimedTaskModel.SYSTEM, TimedTaskModel.nickNames)
        /** 流程超时是否重启应用 */
        var timeoutRestart = BooleanModelField("timeoutRestart", "超时重启", true)
        /** 出现异常后的等待挂起时间（毫秒） */
        var waitWhenException = MultiplyIntegerModelField("waitWhenException", "异常等待", 60, 0, 1440, 60000)
        /** 连续异常进入熔断的次数 */
        var setMaxErrorCount = IntegerModelField("setMaxErrorCount", "异常阈值", 8)
        /** 是否发送异常通知 */
        var errNotify = BooleanModelField("errNotify", "异常通知", false)
        /** 是否优先使用新版 RPC 桥接 */
        var newRpc = BooleanModelField("newRpc", "新版接口", true)
        /** 自定义 RPC 配置定时调度开关 */
        var customRpcScheduleEnable = BooleanModelField("customRpcScheduleEnable", "自定义RPC定时", false)
        /** 是否开启全局抓包调试 */
        var debugMode = BooleanModelField("debugMode", "开启抓包", false)
        /** 是否转发抓包数据 */
        var sendHookData = BooleanModelField("sendHookData", "数据转发", false)
        /** 数据转发的 Webhook 地址 */
        var sendHookDataUrl = StringModelField("sendHookDataUrl", "转发地址", "http://127.0.0.1:9527/hook")

        // --- 辅助与界面配置 ---
        /** 是否自动检查电池优化权限 */
        var batteryPerm = BooleanModelField("batteryPerm", "申请电池优化豁免", true)
        /** 是否记录业务总览日志 */
        var recordLog = BooleanModelField("recordLog", "记录 record 日志", true)
        /** 是否记录底层运行日志 */
        var runtimeLog = BooleanModelField("runtimeLog", "记录 runtime 日志", false)
        /** 全局气泡提示开关 */
        var showToast = BooleanModelField("showToast", "气泡提示", true)
        /** 气泡提示的文本前缀 */
        var toastPerfix = StringModelField("toastPerfix", "气泡前缀", "")
        /** 是否将通知设为 Ongoing 以防止被清除 */
        var enableOnGoing = BooleanModelField("enableOnGoing", "常驻运行通知", false)
        /** 是否强制简体中文环境 */
        var languageSimplifiedChinese = BooleanModelField("languageSimplifiedChinese", "强制中文时区", true)

        fun destroyData() {
            try {
                Log.record(TAG, "🧹清理所有数据")
                IdMapManager.getInstance(BeachMap::class.java).clear()
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }
        }
    }
}
package io.github.aoguai.sesameag.task.other

import io.github.aoguai.sesameag.entity.OtherEntityProvider.listCreditOptions
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectAndCountModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.task.other.credit2101.Credit2101
import io.github.aoguai.sesameag.util.Log

class OtherTask : ModelTask() {
    override fun getName(): String {
        return "其他任务"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }

    override fun getIcon(): String {
        return ""
    }

    /** @brief 信用2101 游戏开关 */
    private var credit2101: BooleanModelField? = null

    /** @brief 信用2101 事件列表 */
    private var creditOptions: SelectAndCountModelField? = null


    /** @brief 信用2101 自动开宝箱 */
    private var autoOpenChest: BooleanModelField? = null

    /** @brief 信用2101 仅完成1次的事件列表 */
    private var creditOnceOptions: SelectModelField? = null


    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "credit2101", "信用2101", false
            ).withDesc("开启后执行信用2101的签到、任务领奖、天赋升级，并按下方配置处理探测事件。").also {
                credit2101 = it
            })

        fields.addField(
            BooleanModelField(
                "AutoOpenChest", "信用2101 | 自动开宝箱", false
            ).withDesc("自动打开信用2101中的印记宝箱。需开启“信用2101”主开关。").also {
                autoOpenChest = it
            })


        fields.addField(
            SelectAndCountModelField(
                "CreditOptions",
                "信用2101 | 事件类型",
                LinkedHashMap<String?, Int?>(),
                listCreditOptions(),
                "设置各事件每日运行次数，0为不执行，-1为不限。"
            ).also {
                creditOptions = it
            })









        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (credit2101?.value == true) {
                val optionField = creditOptions ?: run {
                    Log.record(TAG, "信用2101配置未初始化，跳过执行")
                    return
                }
                Credit2101.doCredit2101(autoOpenChest?.value == true, optionField)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }


    companion object {
        const val TAG = "OtherTask"
        fun run() {
            // TODO: 添加其他任务
        }
    }
}


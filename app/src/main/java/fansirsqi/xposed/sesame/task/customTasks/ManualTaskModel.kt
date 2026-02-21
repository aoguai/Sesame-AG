package fansirsqi.xposed.sesame.task.customTasks

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log

/**
 * 手动任务模型
 * 用于在 UI 点击时触发特定的庄园子任务序列
 */
class ManualTaskModel : ModelTask() {
    private lateinit var forestWhackMole: BooleanModelField
    private lateinit var forestEnergyRain: BooleanModelField
    private lateinit var exchangeEnergyRainCard: BooleanModelField
    private lateinit var farmSendBackAnimal: BooleanModelField
    private lateinit var farmGameLogic: BooleanModelField
    private lateinit var farmChouChouLe: BooleanModelField


    override fun getName(): String = "手动调度任务"

    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(BooleanModelField("forestWhackMole", "森林打地鼠", false).also { forestWhackMole = it })
        fields.addField(BooleanModelField("forestEnergyRain", "能量雨", false).also { forestEnergyRain = it })
        fields.addField(BooleanModelField("exchangeEnergyRainCard", " ↪ 兑换使用能量雨卡", false).also { exchangeEnergyRainCard = it })
        fields.addField(BooleanModelField("farmSendBackAnimal", "遣返小鸡", false).also { farmSendBackAnimal = it })
        fields.addField(BooleanModelField("farmGameLogic", "庄园游戏改分", false).also { farmGameLogic = it })
        fields.addField(BooleanModelField("farmChouChouLe", "庄园抽抽乐", false).also { farmChouChouLe = it })
        return fields
    }

    /**
     * 关键修复：返回 false。
     * 这确保了该任务永远不会被 TaskRunner 的自动执行循环选中。
     * 只有通过首页按钮发送广播，显式调用 startTask 时才会运行。
     */
    override fun check(): Boolean {
        return false
    }

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = "ManualTask.png"

    override suspend fun runSuspend() {
        Log.record("ManualTask", "🔍 正在检查运行环境...")
        
        // 检查是否有其他自动任务正在运行 (不包括自己)
        val otherRunningTasks = modelArray.filterIsInstance<ModelTask>()
            .filter { it != this && it.isRunning }
            .map { it.getName() ?: "未知任务" }

        if (otherRunningTasks.isNotEmpty()) {
            Log.record("ManualTask", "⚠️ 无法启动：自动任务队列正在运行中 (${otherRunningTasks.joinToString(", ")})")
            Log.record("ManualTask", "请先在主界面点击“停止所有任务”后再运行手动任务流程。")
            return
        }

        val selectedTasks = mutableListOf<CustomTask>()
        if (forestWhackMole.value) selectedTasks.add(CustomTask.FOREST_WHACK_MOLE)
        if (forestEnergyRain.value) selectedTasks.add(CustomTask.FOREST_ENERGY_RAIN)
        if (farmSendBackAnimal.value) selectedTasks.add(CustomTask.FARM_SEND_BACK_ANIMAL)
        if (farmGameLogic.value) selectedTasks.add(CustomTask.FARM_GAME_LOGIC)
        if (farmChouChouLe.value) selectedTasks.add(CustomTask.FARM_CHOUCHOULE)

        val extraParams = HashMap<String, Any>()
        extraParams["exchangeEnergyRainCard"] = exchangeEnergyRainCard.value

        // 使用上游推荐的 GlobalThreadPools 执行手动流
        GlobalThreadPools.execute {
            ManualTask.run(selectedTasks, extraParams)
        }
    }
}
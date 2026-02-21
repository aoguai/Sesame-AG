package fansirsqi.xposed.sesame.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.antForest.AntForest
import fansirsqi.xposed.sesame.task.customTasks.CustomTask
import fansirsqi.xposed.sesame.task.customTasks.ManualTaskModel
import fansirsqi.xposed.sesame.ui.screen.components.ManualTaskItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTaskScreen(
    onBackClick: () -> Unit,
    onTaskClick: (CustomTask, Map<String, Any>) -> Unit
) {
    val tasks = CustomTask.entries.toTypedArray()
    // 从模型系统中读取实例（此时 getFields() 返回的字段已被 Config.load 挂载了正确的值）
    val antForestModel = remember { Model.getModel(AntForest::class.java) }
    val manualTaskModel = remember { Model.getModel(ManualTaskModel::class.java) }
    val title = manualTaskModel?.getName() ?: "手动调度任务"

    // 初始化打地鼠参数
    val initialMode = remember(antForestModel) {
        val mode = antForestModel?.whackMoleMode?.value ?: 1
        if (mode == 0) 1 else mode
    }
    val initialGames = remember(antForestModel) {
        (antForestModel?.whackMoleGames?.value ?: 5).toString()
    }
    // 子任务状态
    var whackMoleMode by remember { mutableIntStateOf(initialMode) }
    var whackMoleGames by remember { mutableStateOf(initialGames) }
    var specialFoodCount by remember { mutableStateOf("1") }

    // 道具使用状态
    var selectedTool by remember { mutableStateOf("BIG_EATER_TOOL") }
    var toolCount by remember { mutableStateOf("1") }

    // 能量雨状态
    var exchangeEnergyRainCard by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(tasks) { task ->
                val params = when (task) {
                    CustomTask.FOREST_WHACK_MOLE -> mapOf(
                        "whackMoleMode" to whackMoleMode,
                        "whackMoleGames" to (whackMoleGames.toIntOrNull() ?: 5)
                    )

                    CustomTask.FOREST_ENERGY_RAIN -> mapOf(
                        "exchangeEnergyRainCard" to exchangeEnergyRainCard
                    )

                    CustomTask.FARM_SPECIAL_FOOD -> {
                        val count = specialFoodCount.toIntOrNull() ?: 0
                        mapOf("specialFoodCount" to count)
                    }

                    CustomTask.FARM_USE_TOOL -> mapOf(
                        "toolType" to selectedTool,
                        "toolCount" to (toolCount.toIntOrNull() ?: 1)
                    )

                    else -> emptyMap()
                }

                ManualTaskItem(
                    task = task,
                    onClick = { onTaskClick(task, params) },
                    hasSettings = task == CustomTask.FOREST_WHACK_MOLE || task == CustomTask.FOREST_ENERGY_RAIN || task == CustomTask.FARM_SPECIAL_FOOD || task == CustomTask.FARM_USE_TOOL,
                    whackMoleMode = whackMoleMode,
                    onModeChange = { whackMoleMode = it },
                    whackMoleGames = whackMoleGames,
                    onGamesChange = { whackMoleGames = it },
                    specialFoodCount = specialFoodCount,
                    onSpecialFoodCountChange = { specialFoodCount = it },
                    selectedTool = selectedTool,
                    onToolChange = { selectedTool = it },
                    toolCount = toolCount,
                    onToolCountChange = { toolCount = it },
                    exchangeEnergyRainCard = exchangeEnergyRainCard,
                    onExchangeEnergyRainCardChange = { exchangeEnergyRainCard = it }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

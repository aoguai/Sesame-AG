package fansirsqi.xposed.sesame.ui.screen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.task.customTasks.CustomTask

private val toolDisplayNameMap = mapOf(
    "BIG_EATER_TOOL" to "加饭卡",
    "NEWEGGTOOL" to "新蛋卡",
    "FENCETOOL" to "篱笆卡"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTaskItem(
    task: CustomTask,
    onClick: () -> Unit,
    hasSettings: Boolean = false,
    whackMoleMode: Int = 1,
    onModeChange: (Int) -> Unit = {},
    whackMoleGames: String = "5",
    onGamesChange: (String) -> Unit = {},
    specialFoodCount: String = "0",
    onSpecialFoodCountChange: (String) -> Unit = {},
    selectedTool: String = "BIG_EATER_TOOL",
    onToolChange: (String) -> Unit = {},
    toolCount: String = "1",
    onToolCountChange: (String) -> Unit = {},
    exchangeEnergyRainCard: Boolean = false,
    onExchangeEnergyRainCardChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.displayName,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "点击立即运行",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (hasSettings) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onClick() }
            )
        }

        AnimatedVisibility(visible = hasSettings && expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                if (task == CustomTask.FOREST_WHACK_MOLE) {
                    Text("运行模式选择:", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = whackMoleMode == 1, onClick = { onModeChange(1) })
                        Text("兼容", modifier = Modifier.clickable { onModeChange(1) })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = whackMoleMode == 2, onClick = { onModeChange(2) })
                        Text("激进", modifier = Modifier.clickable { onModeChange(2) })
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = whackMoleGames,
                        onValueChange = onGamesChange,
                        label = { Text("执行局数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (task == CustomTask.FOREST_ENERGY_RAIN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onExchangeEnergyRainCardChange(!exchangeEnergyRainCard) }
                    ) {
                        Checkbox(
                            checked = exchangeEnergyRainCard,
                            onCheckedChange = { onExchangeEnergyRainCardChange(it) }
                        )
                        Text(text = "是否兑换使用能量雨卡", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (task == CustomTask.FARM_SPECIAL_FOOD) {
                    OutlinedTextField(
                        value = specialFoodCount,
                        onValueChange = onSpecialFoodCountChange,
                        label = { Text("使用总次数 (必须大于0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (task == CustomTask.FARM_USE_TOOL) {
                    val tools = toolDisplayNameMap.keys.toList()
                    var toolExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = toolExpanded,
                        onExpandedChange = { toolExpanded = !toolExpanded }
                    ) {
                        OutlinedTextField(
                            value = toolDisplayNameMap[selectedTool] ?: selectedTool,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择道具") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toolExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = toolExpanded,
                            onDismissRequest = { toolExpanded = false }
                        ) {
                            tools.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(toolDisplayNameMap[tool] ?: tool) },
                                    onClick = {
                                        onToolChange(tool)
                                        toolExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedTool == "NEWEGGTOOL") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = toolCount,
                            onValueChange = onToolCountChange,
                            label = { Text("使用数量") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

package io.github.aoguai.sesameag.ui.material3

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aoguai.sesameag.entity.AntFarmIPChouChouLeBenefit
import io.github.aoguai.sesameag.entity.KVMap
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.ui.extension.openUrl
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 字段值真假判断扩展
 */
val FieldState.isTrue: Boolean
    get() = when (val v = value) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.lowercase() == "true" || (v.toIntOrNull()?.let { it != 0 } ?: false)
        else -> false
    }

/**
 * 设置项的基础容器 - 适配 Miuix 风格
 */
@Composable
fun SettingItemContainer(
    name: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    isChild: Boolean = false,
    depth: Int = 0,
    isHighlighted: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val highlightColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
    val animatedBgColor = remember { Animatable(Color.Transparent) }
    
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            animatedBgColor.snapTo(highlightColor)
            delay(1000)
            animatedBgColor.animateTo(Color.Transparent, animationSpec = tween(1500))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = animatedBgColor.value
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isChild) (16 + (depth - 1) * 8).coerceAtLeast(16).dp else 16.dp, 
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChild) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(end = 4.dp),
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = if (isChild) 15.sp else 16.sp,
                    fontWeight = if (isChild && depth > 1) FontWeight.Normal else FontWeight.Medium,
                    color = if (isChild) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f) else MiuixTheme.colorScheme.onSurface
                )
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = 15.sp
                    )
                }
            }
            
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.wrapContentWidth(Alignment.End)) {
                    trailingContent()
                }
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
    field: FieldState,
    isChild: Boolean = false,
    depth: Int = 0,
    isHighlighted: Boolean = false,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    onRowClick: (() -> Unit)? = null,
    onValueChange: (Boolean) -> Unit
) {
    SettingItemContainer(
        name = field.name,
        modifier = if (isExpandable && onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier,
        desc = field.desc,
        isChild = isChild,
        depth = depth,
        isHighlighted = isHighlighted,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExpandable) {
                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation).padding(end = 8.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = field.isTrue,
                    onCheckedChange = onValueChange,
                    modifier = Modifier.scale(0.85f)
                )
            }
        }
    )
}

@Composable
fun SettingFieldDispatcher(
    field: FieldState,
    isChild: Boolean,
    depth: Int = 0,
    isHighlighted: Boolean = false,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    onRowClick: (() -> Unit)? = null,
    onFieldChange: (FieldState, Any) -> Unit
) {
    if (field.type == "CATEGORY") {
        SettingItemContainer(
            name = field.name,
            modifier = if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier,
            desc = field.desc,
            isChild = isChild,
            depth = depth,
            isHighlighted = isHighlighted,
            trailingContent = {
                if (isExpandable) {
                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        )
        return
    }

    when (field.type) {
        "BOOLEAN" -> SwitchSettingItem(field, isChild, depth, isHighlighted, isExpandable, isExpanded, onRowClick) { onFieldChange(field, it) }
        "CHOICE" -> ChoiceSettingItem(field, isChild, depth, isHighlighted, isExpandable, isExpanded, onRowClick) { onFieldChange(field, it) }
        "INTEGER", "STRING", "MULTIPLY_INTEGER" -> TextSettingItem(field, isChild, depth, isHighlighted) { onFieldChange(field, it) }
        "LIST", "TIME_POINT", "TIME_POINT_LIST", "TIME_WINDOW_LIST", "TIME_TRIGGER", "HOUR_OF_DAY" -> ListSettingItem(field, isChild, depth, isHighlighted) { onFieldChange(field, it) }
        "SELECT", "SELECT_AND_COUNT" -> MultiSelectSettingItem(field, isChild, depth, isHighlighted) { onFieldChange(field, it) }
        "SELECT_AND_COUNT_ONE" -> SingleSelectAndCountSettingItem(field, isChild, depth, isHighlighted) { onFieldChange(field, it) }
        "URL_TEXT", "READ_TEXT" -> ReadOnlyTextSettingItem(field, isChild, depth, isHighlighted)
        else -> SettingItemContainer(name = field.name, desc = "暂不支持: ${field.type}", isChild = isChild, depth = depth, isHighlighted = isHighlighted)
    }
}

@Composable
fun TextSettingItem(field: FieldState, isChild: Boolean = false, depth: Int = 0, isHighlighted: Boolean = false, onValueChange: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    if (isEditing) {
        var textValue by remember { mutableStateOf(field.value?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text(field.name) },
            text = {
                TextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "请输入内容",
                    keyboardOptions = KeyboardOptions(keyboardType = if (field.type.contains("INTEGER")) KeyboardType.Number else KeyboardType.Text)
                )
            },
            confirmButton = {
                TextButton(onClick = { onValueChange(textValue); isEditing = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) { Text("取消") }
            }
        )
    }
    SettingItemContainer(name = field.name, modifier = Modifier.clickable { isEditing = true }, desc = field.desc, isChild = isChild, depth = depth, isHighlighted = isHighlighted) {
        Text(text = field.value?.toString() ?: "", fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
fun ChoiceSettingItem(
    field: FieldState,
    isChild: Boolean = false,
    depth: Int = 0,
    isHighlighted: Boolean = false,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    onRowClick: (() -> Unit)? = null,
    onValueChange: (Int) -> Unit
) {
    val currentIndex = field.value?.toString()?.toIntOrNull() ?: 0
    val choices = field.expandData as? Array<*> ?: emptyArray<Any?>()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(field.name) },
            text = {
                Column {
                    choices.forEachIndexed { index, choice ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onValueChange(index); showDialog = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = index == currentIndex, onClick = { onValueChange(index); showDialog = false })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = choice?.toString() ?: "空", fontSize = 17.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    SettingItemContainer(
        name = field.name,
        modifier = Modifier.clickable { if (isExpandable && onRowClick != null) onRowClick() else showDialog = true },
        desc = field.desc,
        isChild = isChild,
        depth = depth,
        isHighlighted = isHighlighted,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showDialog = true }) {
                Text(text = choices.getOrNull(currentIndex)?.toString() ?: "未知", fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary, textAlign = TextAlign.End)
                if (isExpandable) {
                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")
                    Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation).padding(start = 4.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    )
}

@Composable
fun MultiSelectSettingItem(field: FieldState, isChild: Boolean = false, depth: Int = 0, isHighlighted: Boolean = false, onValueChange: (Any) -> Unit) {
    var showMainDialog by remember { mutableStateOf(false) }
    var showCountDialog by remember { mutableStateOf<String?>(null) } 
    val currentData = field.value
    val selectedIds = when (currentData) {
        is Map<*, *> -> currentData.keys.filterIsInstance<String>().toSet()
        is Set<*> -> currentData.filterIsInstance<String>().toSet()
        else -> emptySet()
    }
    val allOptions = (field.expandData as? List<*>)?.filterIsInstance<MapperEntity>() ?: emptyList()
    
    val getOrder = { id: String ->
        if (currentData is Map<*, *>) {
            val keys = currentData.keys.toList()
            val idx = keys.indexOf(id)
            if (idx >= 0) idx + 1 else 0
        } else 0
    }

    if (showMainDialog) {
        AlertDialog(
            onDismissRequest = { showMainDialog = false },
            title = { Text(field.name) },
            text = { Box(modifier = Modifier.heightIn(max = 400.dp)) { LazyColumn { items(allOptions) { item ->
                val id = item.id
                val isSelected = selectedIds.contains(id)
                val order = getOrder(id)
                Row(modifier = Modifier.fillMaxWidth().clickable { 
                    if (field.type == "SELECT_AND_COUNT") {
                        if (isSelected) {
                            @Suppress("UNCHECKED_CAST")
                            val newMap = (currentData as Map<String, Int>).toMutableMap(); newMap.remove(id); onValueChange(newMap)
                        } else { showCountDialog = id }
                    } else {
                        val newSet = selectedIds.toMutableSet(); if (isSelected) newSet.remove(id) else newSet.add(id); onValueChange(newSet)
                    }
                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                        onClick = {
                             if (field.type == "SELECT_AND_COUNT") {
                                if (isSelected) {
                                    @Suppress("UNCHECKED_CAST")
                                    val newMap = (currentData as Map<String, Int>).toMutableMap(); newMap.remove(id); onValueChange(newMap)
                                } else { showCountDialog = id }
                            } else {
                                val newSet = selectedIds.toMutableSet(); if (isSelected) newSet.remove(id) else newSet.add(id); onValueChange(newSet)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val nameText = if (order > 0) "[$order] ${item.name}" else item.name
                        Text(nameText, fontSize = 16.sp)
                        val subText = StringBuilder()
                        if (item is AntFarmIPChouChouLeBenefit) {
                            if (item.cent > 0) subText.append("${item.cent/100}碎片")
                            if (item.limitCount > 0) {
                                if (subText.isNotEmpty()) subText.append(" | ")
                                subText.append("限购${item.limitCount}次")
                            }
                        }
                        if (isSelected && field.type == "SELECT_AND_COUNT") {
                            @Suppress("UNCHECKED_CAST")
                            val count = (currentData as Map<String, Int>)[id] ?: 0
                            if (subText.isNotEmpty()) subText.append(" | ")
                            subText.append("设定次数: $count")
                        }
                        if (subText.isNotEmpty()) { Text(text = subText.toString(), fontSize = 12.sp, color = MiuixTheme.colorScheme.primary) }
                    }
                }
            } } } },
            confirmButton = { TextButton(onClick = { showMainDialog = false }) { Text("确定") } }
        )
    }
    
    showCountDialog?.let { targetId ->
        val item = allOptions.find { it.id == targetId }
        var countValue by remember { mutableStateOf("1") }
        AlertDialog(onDismissRequest = { showCountDialog = null }, title = { Text(item?.name ?: "设置兑换次数") },
            text = { 
                Column {
                    if (item is AntFarmIPChouChouLeBenefit && item.limitCount > 0) {
                        Text("该物品在商店中限购 ${item.limitCount} 次", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                    }
                    TextField(value = countValue, onValueChange = { countValue = it }, label = "请输入次数", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { 
                var finalCount = countValue.toIntOrNull() ?: 1
                if (item is AntFarmIPChouChouLeBenefit && item.limitCount > 0 && finalCount > item.limitCount) finalCount = item.limitCount
                @Suppress("UNCHECKED_CAST")
                onValueChange((currentData as? Map<String, Int>)?.toMutableMap()?.apply { put(targetId, finalCount) } ?: mutableMapOf(targetId to finalCount))
                showCountDialog = null 
            }) { Text("确定") } }
        )
    }
    SettingItemContainer(name = field.name, modifier = Modifier.clickable { showMainDialog = true }, desc = "${selectedIds.size} 已选择", isChild = isChild, depth = depth, isHighlighted = isHighlighted) {
        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
fun SingleSelectAndCountSettingItem(field: FieldState, isChild: Boolean = false, depth: Int = 0, isHighlighted: Boolean = false, onValueChange: (Any) -> Unit) {
    var showMainDialog by remember { mutableStateOf(false) }
    var showCountDialog by remember { mutableStateOf<String?>(null) }
    @Suppress("UNCHECKED_CAST")
    val currentKV = field.value as? KVMap<String?, Int?>
    val allOptions = (field.expandData as? List<*>)?.filterIsInstance<MapperEntity>() ?: emptyList()

    if (showMainDialog) {
        AlertDialog(
            onDismissRequest = { showMainDialog = false },
            title = { Text(field.name) },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        items(allOptions) { item ->
                            val id = item.id
                            val isSelected = currentKV?.key == id
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                if (isSelected) {
                                    onValueChange(KVMap(null, 0))
                                    showMainDialog = false
                                } else {
                                    showCountDialog = id
                                }
                            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = isSelected, onClick = {
                                    if (isSelected) {
                                        onValueChange(KVMap(null, 0))
                                        showMainDialog = false
                                    } else {
                                        showCountDialog = id
                                    }
                                })
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(item.name, fontSize = 16.sp)
                                    if (isSelected) {
                                        Text(text = "设定次数: ${currentKV.value ?: 0}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMainDialog = false }) { Text("确定") } }
        )
    }

    showCountDialog?.let { targetId ->
        val item = allOptions.find { it.id == targetId }
        var countValue by remember { mutableStateOf("1") }
        AlertDialog(onDismissRequest = { showCountDialog = null }, title = { Text(item?.name ?: "设置次数") },
            text = {
                TextField(value = countValue, onValueChange = { countValue = it }, label = "请输入次数", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = {
                val finalCount = countValue.toIntOrNull() ?: 1
                onValueChange(KVMap(targetId, finalCount))
                showCountDialog = null
                showMainDialog = false
            }) { Text("确定") } }
        )
    }

    val selectedName = allOptions.find { it.id == currentKV?.key }?.name ?: "未选择"
    val summary = if (currentKV?.key != null) "$selectedName (${currentKV.value}次)" else "未选择"

    SettingItemContainer(name = field.name, modifier = Modifier.clickable { showMainDialog = true }, desc = summary, isChild = isChild, depth = depth, isHighlighted = isHighlighted) {
        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
fun ReadOnlyTextSettingItem(field: FieldState, isChild: Boolean = false, depth: Int = 0, isHighlighted: Boolean = false) {
    val context = LocalContext.current
    val isUrl = field.type == "URL_TEXT"
    val configValue = field.originalField.configValue ?: ""
    SettingItemContainer(
        name = field.name,
        modifier = if (isUrl) Modifier.clickable { context.openUrl(configValue) } else Modifier,
        desc = field.desc,
        isChild = isChild,
        depth = depth,
        isHighlighted = isHighlighted
    ) {
        if (isUrl) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
        } else {
            Text(
                text = if (configValue.isEmpty()) "无内容" else configValue,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

@Composable
fun ListSettingItem(field: FieldState, isChild: Boolean = false, depth: Int = 0, isHighlighted: Boolean = false, onValueChange: (String) -> Unit) {
    val rawValue = field.value
    val currentValue = when (rawValue) {
        is List<*> -> rawValue.joinToString(",")
        is String -> rawValue.removeSurrounding("\"")
        else -> rawValue?.toString() ?: ""
    }
    var isEditing by remember { mutableStateOf(false) }
    if (isEditing) {
        var textValue by remember { mutableStateOf(currentValue) }
        AlertDialog(onDismissRequest = { isEditing = false }, title = { Text(field.name) },
            text = { TextField(value = textValue, onValueChange = { textValue = it }, label = "请输入内容", modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { onValueChange(textValue); isEditing = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { isEditing = false }) { Text("取消") } }
        )
    }
    SettingItemContainer(name = field.name, modifier = Modifier.clickable { isEditing = true }, desc = field.desc, isChild = isChild, depth = depth, isHighlighted = isHighlighted) {
        Text(text = currentValue, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.primary, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
    }
}

@Composable
fun GroupedSettingContainer(
    modifier: Modifier = Modifier,
    isNested: Boolean = false,
    depth: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    // 动态计算边距：层级越深，左右边距微调，避免内容区被过度压缩
    val horizontalPadding = if (isNested) 8.dp else 12.dp
    val verticalPadding = if (isNested) 4.dp else 8.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            ),
        insideMargin = PaddingValues(0.dp) // 移除内边距让背景色填充整个卡片
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // 核心改进：深度 > 0 时，叠加一层半透明背景。
                    // 使用 onSurface 颜色：在浅色模式下是黑色，产生变灰效果；在深色模式下是白色，产生变亮提色效果。
                    if (depth > 0) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f * depth.coerceAtMost(3))
                    else Color.Transparent
                )
                .then(
                    if (depth > 0) {
                        // 增加显式的边框（1dp），确保在黑暗模式下能清晰分辨卡片边界
                        Modifier.border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                    } else Modifier
                )
                .padding(vertical = 4.dp), // 卡片内部的上下填充
            content = content
        )
    }
}

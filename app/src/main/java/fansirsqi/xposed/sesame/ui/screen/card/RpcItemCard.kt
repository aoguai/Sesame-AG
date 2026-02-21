package fansirsqi.xposed.sesame.ui.screen.card

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.entity.RpcDebugEntity
import fansirsqi.xposed.sesame.util.ToastUtil

@Composable
fun RpcItemCard(
    item: RpcDebugEntity,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    // 控制描述展开的状态
    var descriptionExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { ToastUtil.showToast(item.description) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 1. 标题和运行按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.method,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(
                    onClick = onRun,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.PlayArrow, "运行", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // 2. 描述区域 (如果有描述)
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { descriptionExpanded = !descriptionExpanded },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Notes, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "功能描述",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (descriptionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // 展开/收起动画
                        AnimatedVisibility(visible = descriptionExpanded) {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 收起时显示的摘要 (可选，如果不想显示摘要可去掉 else)
                        if (!descriptionExpanded) {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // 3. 底部操作栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCopy) { Text("复制配置") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
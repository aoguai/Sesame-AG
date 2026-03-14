package fansirsqi.xposed.sesame.ui.screen.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fansirsqi.xposed.sesame.util.CommandUtil.ServiceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesStatusCard(
    status: ServiceStatus, // 使用新定义的状态
    expanded: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp), // 稍微调整间距
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (status) {
                is ServiceStatus.Active -> {
                    if (status.type == "Root") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                }
                is ServiceStatus.Inactive -> MaterialTheme.colorScheme.errorContainer
                is ServiceStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
                else -> {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    is ServiceStatus.Active -> {
                        val isRootReady = status.type == "Root"
                        Icon(
                            if (isRootReady) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            if (isRootReady) "已授权" else "权限不足"
                        )
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = if (isRootReady) "Root 服务正常" else "仅检测到 Shizuku",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isRootReady) "自动工作流已解锁" else "当前版本仅 Root 可启用工作流",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isRootReady) {
                                    "已检测到 Root 权限，当前配置允许生效"
                                } else {
                                    "Shizuku 状态仅供诊断，当前配置不会生效"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    is ServiceStatus.Inactive -> {
                        Icon(Icons.Outlined.Warning, "未授权")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "Shell 服务不可用", style = MaterialTheme.typography.titleMedium)
                            Text(text = "点击查看解决方案", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is ServiceStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "正在检查服务权限...", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "正在检查服务权限...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            // 展开内容：故障排查
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "授权指南", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前版本仅在检测到 Root 权限后才会启动工作流并使配置生效。\n\n" +
                                "说明：\n" +
                                "1. Shizuku 状态仅用于排障与状态展示，不会解锁自动任务。\n" +
                                "2. 请先确认设备已 Root，并在 Root 管理器中授予本应用 Root 权限。\n" +
                                "3. 返回首页后等待状态刷新为 Root。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

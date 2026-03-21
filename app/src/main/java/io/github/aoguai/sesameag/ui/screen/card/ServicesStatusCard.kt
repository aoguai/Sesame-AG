package io.github.aoguai.sesameag.ui.screen.card

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
import io.github.aoguai.sesameag.util.CommandUtil.ServiceStatus

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
                is ServiceStatus.Active -> MaterialTheme.colorScheme.primary
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
                            Icons.Outlined.CheckCircle,
                            "已连接"
                        )
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = if (isRootReady) "Root Shell 已连接" else "Shizuku Shell 已连接",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isRootReady) "命令服务具备 Root 执行器" else "命令服务当前使用 Shizuku 执行器",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isRootReady) {
                                    "已检测到 Root Shell，命令服务可直接执行提权命令"
                                } else {
                                    "此卡仅反映命令执行器；若当前进程已由 LSPosed/LibXposed 注入，工作流仍可生效"
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
                        text = "工作流权限以 Hook 注入或实时 Root 检测结果为准；此卡仅反映命令服务当前选中的 Shell 执行器。\n\n" +
                                "说明：\n" +
                                "1. 当前进程已由 LSPosed/LibXposed 注入时，可直接启动工作流。\n" +
                                "2. 未注入时，会回退到实时 Root 检测。\n" +
                                "3. Shizuku 状态主要用于排障与命令服务展示，不单独决定配置是否生效。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}


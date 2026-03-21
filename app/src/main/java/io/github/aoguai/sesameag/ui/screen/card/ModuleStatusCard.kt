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
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.ui.screen.components.HtmlText
import io.github.aoguai.sesameag.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleStatusCard(
    status: MainViewModel.ModuleStatus,
    expanded: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor =
                when (status) {
                    is MainViewModel.ModuleStatus.Activated -> MaterialTheme.colorScheme.primary
                    is MainViewModel.ModuleStatus.Unsupported -> MaterialTheme.colorScheme.errorContainer
                    is MainViewModel.ModuleStatus.NotActivated -> MaterialTheme.colorScheme.errorContainer
                    is MainViewModel.ModuleStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
                }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    is MainViewModel.ModuleStatus.Activated -> {
                        Icon(Icons.Outlined.CheckCircle, "已激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "Activated", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Version: ${BuildConfig.VERSION_NAME} ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "by ${status.frameworkName} ${status.frameworkVersion} API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.Unsupported -> {
                        Icon(Icons.Outlined.Warning, "不受支持")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "框架 API 不受支持", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "by ${status.frameworkName} ${status.frameworkVersion} API ${status.apiVersion}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "需要支持 libxposed API 101+ 的管理器", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "点击展开帮助", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.NotActivated -> {
                        Icon(Icons.Outlined.Warning, "未激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "模块未激活", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "请在支持 libxposed API 101+ 的管理器中激活", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "点击展开帮助", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is MainViewModel.ModuleStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "正在检查模块状态...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "故障排查指南", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    HtmlText(
                        html = "查看帮助 <a href=\"${General.PROJECT_HOMEPAGE_URL}\">项目仓库主页</a>"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "当前模块仅支持 libxposed API 101+；若管理器或框架仍停留在 API 100，模块不会生效。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(text = "Lspatch/Npatch/FPA/Opatch 请忽略此状态", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}



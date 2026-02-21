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
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ui.screen.components.HtmlText
import fansirsqi.xposed.sesame.ui.viewmodel.MainViewModel

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

                    is MainViewModel.ModuleStatus.NotActivated -> {
                        Icon(Icons.Outlined.Warning, "未激活")
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(text = "模块未激活", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "请尝试在Ls/Xposed中激活", style = MaterialTheme.typography.bodyMedium)
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
                        html = "查看帮助 <a href=\"https://github.com/Fansirsqi/Sesame-TK/wiki/%E6%97%A0Root\">免Root食用指南</a>"
                    )
                    Text(text = "Lspatch/Npatch/FPA/Opatch 请忽略此状态", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}


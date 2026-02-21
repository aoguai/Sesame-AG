package fansirsqi.xposed.sesame.ui.screen.content

import SettingsSwitchItem
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.ui.MainActivity
import fansirsqi.xposed.sesame.ui.ManualTaskActivity
import fansirsqi.xposed.sesame.ui.RpcDebugActivity
import fansirsqi.xposed.sesame.ui.compose.CommonAlertDialog
import fansirsqi.xposed.sesame.ui.extension.joinQQGroup
import fansirsqi.xposed.sesame.ui.screen.components.SettingsItem
import fansirsqi.xposed.sesame.ui.screen.components.UserItemCard


@Composable
fun SettingsContent(
    userList: List<UserEntity>,
    isDynamicColor: Boolean,          // 新增参数
    onToggleDynamicColor: (Boolean) -> Unit, // 新增参数
    onNavigateToSettings: (UserEntity) -> Unit,
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    // 状态定义在最外层
    var showClearConfigDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 使用 Box 或 Column 包裹，或者直接平铺
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. 列表内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),//在列表内容的四周（上、下、左、右）各添加 16dp 的内边距
            verticalArrangement = Arrangement.spacedBy(8.dp)//在每个列表项之间添加 固定间距
        ) {
            item {
                Text(
                    text = "账号配置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (userList.isEmpty()) {
                item {
                    Text(
                        text = "暂无已载入的用户配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(userList) { user ->
                    UserItemCard(user = user, onClick = { onNavigateToSettings(user) })
                }
            }

            // 通用功能部分
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "扩展&外观",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItem(
                    title = "扩展功能",
                    icon = Icons.Rounded.Extension,
                    onClick = { onEvent(MainActivity.MainUiEvent.OpenExtend) }
                )
            }



            if (BuildConfig.DEBUG) {
                item {
                    SettingsItem(
                        title = "RPC 调试工具",
                        icon = Icons.Rounded.BugReport,
                        onClick = {
                            // 直接跳转 Activity
                            context.startActivity(Intent(context, RpcDebugActivity::class.java))
                        }
                    )
                }

                item {
                    SettingsItem(
                        title = "手动调度任务",
                        icon = Icons.Rounded.SatelliteAlt,
                        onClick = {
                            context.startActivity(Intent(context, ManualTaskActivity::class.java))
                        }
                    )
                }

                item {
                    SettingsItem(
                        title = "查看RPC抓包数据",
                        icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                        onClick = { onEvent(MainActivity.MainUiEvent.OpenCaptureLog) }
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsSwitchItem(
                        title = "动态取色",
                        subtitle = "跟随壁纸颜色 (Material You)",
                        icon = Icons.Rounded.Palette,
                        checked = isDynamicColor,
                        onCheckedChange = onToggleDynamicColor
                    )
                }
            }

            item {
                SettingsItem(
                    title = "清除所有配置",
                    subtitle = "重置所有模块数据",
                    icon = Icons.Rounded.DeleteForever,
                    isDanger = true,
                    onClick = { showClearConfigDialog = true } // 点击只改变状态
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "支持",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItem(
                    title = "Github",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = {
                        uriHandler.openUri("https://github.com/Fansirsqi/Sesame-TK")
                    }
                )
            }
            item {
                SettingsItem(
                    title = "Telegram",
                    icon = Icons.AutoMirrored.Rounded.Send,
                    onClick = {
                        uriHandler.openUri("https://t.me/Sesame_TK_Channel")
                    }
                )
            }

            item {
                SettingsItem(
                    title = "QQ群",
                    icon = Icons.Rounded.Groups,
                    onClick = {
                        joinQQGroup(context)
                    }
                )
            }

            // 底部留白，防止被导航栏遮挡（如果有的话）
            item { Spacer(Modifier.height(32.dp)) }
        }

        // 2. 弹窗放在 LazyColumn 外面 (同级)
        if (showClearConfigDialog) {
            CommonAlertDialog(
                showDialog = true,
                onDismissRequest = { showClearConfigDialog = false },
                onConfirm = {
                    onEvent(MainActivity.MainUiEvent.ClearConfig)
                },
                title = "⚠️ 警告",
                text = "🤔❗ 确认清除所有模块配置？\n此操作无法撤销❗❗❗",
                icon = Icons.Outlined.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmText = "确认清除",
                confirmButtonColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

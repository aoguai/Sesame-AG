package fansirsqi.xposed.sesame.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.entity.UserEntity


@Composable
fun UserSelectionDialog(
    userList: List<UserEntity>,
    onDismissRequest: () -> Unit,
    onUserSelected: (UserEntity) -> Unit
) {
    if (userList.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Default.ManageAccounts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "账号设置",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            // 给列表加个最大高度，防止太长铺满屏幕
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Item 之间的间距
                ) {
                    items(userList) { user ->
                        // 使用 Surface 包裹，自带圆角和背景色适配
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, // 比背景稍微亮一点的颜色
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { // Surface 自带 onClick，自带正确的水波纹
                                onUserSelected(user)
                                onDismissRequest()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp) // 内部留白
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧头像/图标
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // 右侧文本信息
                                Column {
                                    Text(
                                        text = user.showName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!user.account.isNullOrEmpty()) {
                                        Text(
                                            text = user.account,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        },
        // 设置 Dialog 的背景色，使其更融合
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

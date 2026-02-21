package fansirsqi.xposed.sesame.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fansirsqi.xposed.sesame.ui.screen.components.RpcDialogHandler
import fansirsqi.xposed.sesame.ui.screen.card.RpcItemCard
import fansirsqi.xposed.sesame.ui.viewmodel.RpcDebugViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcDebugScreen(
    onBack: () -> Unit,
    viewModel: RpcDebugViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 使用 Column 垂直排列主标题和副标题
                    Column {
                        Text(
                            text = "RPC 调试",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "⚠️ 敏感功能，请谨慎操作", // 警告文案
                            style = MaterialTheme.typography.labelMedium, // 使用较小的字号
                            color = MaterialTheme.colorScheme.error // 使用错误色(红色)示警，或者使用 outline 变体
                        )
                    }
                },
                navigationIcon = { /* IconButton(onClick = onBack) ... */ },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("备份数据") },
                            onClick = { viewModel.backupToClipboard(context); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("恢复数据") },
                            onClick = { viewModel.tryRestoreFromClipboard(context); showMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("加载默认") },
                            onClick = { viewModel.loadDefaultItems(); showMenu = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog(context) }) {
                Icon(Icons.Default.Add, "添加")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无数据，请点击右下角添加", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    RpcItemCard(
                        item = item,
                        onRun = { viewModel.runRpcItem(item, context) },
                        onEdit = { viewModel.showEditDialog(item) },
                        onDelete = { viewModel.showDeleteDialog(item) },
                        onCopy = { viewModel.shareItem(item,context) }
                    )
                }
            }
        }

        // 处理所有弹窗
        RpcDialogHandler(dialogState, viewModel)
    }
}



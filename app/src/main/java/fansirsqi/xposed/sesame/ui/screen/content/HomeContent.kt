package fansirsqi.xposed.sesame.ui.screen.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.ui.MainActivity
import fansirsqi.xposed.sesame.ui.screen.card.ModuleStatusCard
import fansirsqi.xposed.sesame.ui.screen.card.OneWordCard
import fansirsqi.xposed.sesame.ui.screen.card.ServicesStatusCard
import fansirsqi.xposed.sesame.ui.viewmodel.MainViewModel
import fansirsqi.xposed.sesame.util.CommandUtil.ServiceStatus
import fansirsqi.xposed.sesame.util.ToastUtil

@Composable
fun HomeContent(
    moduleStatus: MainViewModel.ModuleStatus,
    serviceStatus: ServiceStatus,
    oneWord: String,
    isOneWordLoading: Boolean,
    onOneWordClick: () -> Unit,
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    val context = LocalContext.current
    var isServiceCardExpanded by remember { mutableStateOf(false) }

    var isStatusCardExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "本应用开源免费,严禁倒卖",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        // 1. 模块状态
        item {
            ModuleStatusCard(
                status = moduleStatus,
                expanded = isStatusCardExpanded,
                onClick = {
                    if (moduleStatus is MainViewModel.ModuleStatus.NotActivated) {
                        isStatusCardExpanded = !isStatusCardExpanded//此处不可省略
                    }
                }
            )
        }

        // 2. 服务权限
        item {
            ServicesStatusCard(
                status = serviceStatus,
                expanded = isServiceCardExpanded,
                onClick = {
                    isServiceCardExpanded = !isServiceCardExpanded // 此处不可省略
                }
            )
        }

        // 3. 一言
        item {
            OneWordCard( // 提取出的一言卡片组件
                oneWord = oneWord,
                isLoading = isOneWordLoading,
                onClick = onOneWordClick,
                onLongClick = {
                    onEvent(MainActivity.MainUiEvent.OpenDebugLog)
                    ToastUtil.showToast(context, "准备起飞🛫")
                }
            )
        }


    }
}

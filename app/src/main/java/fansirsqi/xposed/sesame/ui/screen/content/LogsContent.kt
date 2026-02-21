package fansirsqi.xposed.sesame.ui.screen.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fansirsqi.xposed.sesame.ui.MainActivity
import fansirsqi.xposed.sesame.ui.screen.components.MenuButton

@Composable
fun LogsContent(
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center, // 居中显示
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 使用 Grid 布局或者简单的 Row 组合
        val modifier = Modifier.weight(1f)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton(text = "森林日志", icon = Icons.Rounded.Forest, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenForestLog) }
            MenuButton(text = "农场日志", icon = Icons.Rounded.Agriculture, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenFarmLog) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton(text = "其他日志", icon = Icons.Rounded.AlignVerticalTop, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenOtherLog) }
            MenuButton(text = "错误日志", icon = Icons.Rounded.BugReport, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenErrorLog) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton(text = "全部日志", icon = Icons.Rounded.Description, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenAllLog) }
            MenuButton(text = "抓包日志", icon = Icons.Rounded.History, modifier = modifier) { onEvent(MainActivity.MainUiEvent.OpenCaptureLog) }
        }
    }
}
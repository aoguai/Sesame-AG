package fansirsqi.xposed.sesame.ui.screen.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary // 获取主题的主色调
    AndroidView(
        modifier = modifier,
        factory = { context -> TextView(context) },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            textView.movementMethod = LinkMovementMethod.getInstance() // 确保链接可点击
            textView.setLinkTextColor(linkColor.toArgb()) // 设置链接颜色与主题一致
        }
    )
}
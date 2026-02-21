package fansirsqi.xposed.sesame.ui.screen.card


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OneWordCard(
    oneWord: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .padding(vertical = 8.dp)
            // 裁剪为圆角
            .clip(RoundedCornerShape(12.dp))
            // 组合点击事件 (支持长按)
            .combinedClickable(
                enabled = !isLoading,
                onClick = onClick,
                onLongClick = onLongClick
            )
            // 内部 Padding
            .padding(16.dp)
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "OneWordAnimation"
        ) { loading ->
            if (loading) {
                // 加载状态
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本来无一物,何处惹尘..",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 显示一言
                Text(
                    text = oneWord,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
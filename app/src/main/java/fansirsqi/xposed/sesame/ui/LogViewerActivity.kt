package fansirsqi.xposed.sesame.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.ui.screen.LogViewerScreen
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager

/**
 * 承载 Compose 日志查看器的 Activity
 */
class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.data?.path ?: ""
        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(
                dynamicColor = isDynamicColor,
            ) {
                LogViewerScreen(
                    filePath = path,
                    onBackClick = {
                        finish()
                    }
                )
            }
        }
    }
}

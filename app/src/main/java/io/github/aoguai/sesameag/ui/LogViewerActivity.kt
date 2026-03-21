package io.github.aoguai.sesameag.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aoguai.sesameag.ui.screen.LogViewerScreen
import io.github.aoguai.sesameag.ui.theme.AppTheme
import io.github.aoguai.sesameag.ui.theme.ThemeManager

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


package fansirsqi.xposed.sesame.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.ui.screen.RpcDebugScreen
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager

class RpcDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                RpcDebugScreen(onBack = { finish() })
            }

        }
    }
}

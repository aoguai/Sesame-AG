package fansirsqi.xposed.sesame.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.ui.screen.ExtendScreen
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager

class ExtendActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                ExtendScreen(onBackClick = { finish() })
            }

        }
    }
}

package io.github.aoguai.sesameag.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aoguai.sesameag.ui.screen.ExtendScreen
import io.github.aoguai.sesameag.ui.theme.AppTheme
import io.github.aoguai.sesameag.ui.theme.ThemeManager

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


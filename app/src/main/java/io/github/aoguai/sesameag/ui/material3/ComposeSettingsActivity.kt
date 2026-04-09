package io.github.aoguai.sesameag.ui.material3

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.aoguai.sesameag.ui.theme.AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ComposeSettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val userId = intent.getStringExtra("userId")
        viewModel.loadSettings(userId)

        // 🚀 监听来自 ViewModel 的事件
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is SettingsViewModel.SettingsEvent.ShowToast -> {
                        Toast.makeText(this@ComposeSettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                    }
                    is SettingsViewModel.SettingsEvent.Exit -> {
                        finish()
                    }
                    is SettingsViewModel.SettingsEvent.ShowExitConfirmation -> {
                        showExitConfirmationDialog()
                    }
                }
            }
        }

        setContent {
            AppTheme {
                ComposeSettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    /**
     * 🚀 退出确认对话框 - 运行时强制高对比度颜色
     */
    private fun showExitConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("保存更改？")
            .setMessage("检测到配置已修改，是否在退出前保存？")
            .setPositiveButton("保存并退出") { _, _ ->
                viewModel.save(this, isDetail = false)
            }
            .setNegativeButton("放弃更改") { _, _ ->
                viewModel.discardAndExit()
            }
            .setNeutralButton("取消", null)
            .setCancelable(true)
            .create()

        dialog.setOnShowListener {
            // 🚀 强制设置 Positive 按钮为醒目的青蓝色
            val posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            posBtn?.apply {
                setTextColor(Color.parseColor("#00B0FF"))
                typeface = Typeface.DEFAULT_BOLD
            }

            // 🚀 强制设置 Negative 按钮为纯白色 (夜间模式对比度最高)
            val negBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            negBtn?.apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            
            // Neutral 按钮保持默认或设为浅灰
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(Color.LTGRAY)
        }

        dialog.show()
    }
}

package fansirsqi.xposed.sesame.ui.extension

import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.ui.compose.CommonAlertDialog
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager
import kotlin.system.exitProcess


object NativeComposeBridge {


    /**
     * 供 C++ 调用的静态入口
     */
    @JvmStatic
    fun showAlertDialog(context: Context, title: String, message: String, buttonText: String) {
        // JNI 调用可能在后台线程，必须切回主线程操作 UI
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = context as? Activity ?: // 如果 Context 不是 Activity，无法显示 Compose Dialog
                return@post
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                val composeView = ComposeView(context)
                composeView.setContent {
                    val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
                    AppTheme(dynamicColor = isDynamicColor) {
                        var show by remember { mutableStateOf(true) }
                        if (show) {
                            CommonAlertDialog(
                                showDialog = true,
                                onDismissRequest = {
                                    show = false
                                    rootView.post { rootView.removeView(composeView) }
                                },
                                onConfirm = {
                                },
                                title = title,
                                text = message,
                                confirmText = buttonText,
                                showCancelButton = false
                            )
                        }
                    }
                }
                rootView.addView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }





    @JvmStatic
    fun showAlertAfterDelay(
        context: Context,
        title: String,
        message: String,
        buttonText: String,
        delayMillis: Long
    ) {
        // 1. 使用从 C++ 传入的参数显示对话框
        showAlertDialog(context, title, message, buttonText)

        // 2. 使用从 C++ 传入的延迟时间来执行退出操作
        Handler(Looper.getMainLooper()).postDelayed({
            exitProcess(0)
        }, delayMillis)
    }
}
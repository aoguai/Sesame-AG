package fansirsqi.xposed.sesame.ui.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.ui.SettingActivity
import fansirsqi.xposed.sesame.ui.WebSettingsActivity
import fansirsqi.xposed.sesame.ui.model.UiMode
import fansirsqi.xposed.sesame.ui.repository.ConfigRepository
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 扩展函数：打开浏览器
 */

fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

fun Context.performNavigationToSettings(user: UserEntity) {
    Log.record("载入用户配置 ${user.showName}")
    try {
        val currentMode = ConfigRepository.uiMode.value
        val targetActivity = currentMode.targetActivity

        val intent = Intent(this, targetActivity).apply {
            putExtra("userId", user.userId)
            putExtra("userName", user.showName)
        }
        startActivity(intent)
    } catch (e: Exception) {
        ToastUtil.showToast(this, "无法启动设置页面: ${e.message}")
    }
}

val UiMode.targetActivity: Class<*>
    get() = when (this) {
        UiMode.Web -> WebSettingsActivity::class.java
        UiMode.New -> SettingActivity::class.java
    }


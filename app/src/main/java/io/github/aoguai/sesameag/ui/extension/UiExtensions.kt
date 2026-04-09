package io.github.aoguai.sesameag.ui.extension

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import io.github.aoguai.sesameag.entity.UserEntity
import io.github.aoguai.sesameag.ui.material3.ComposeSettingsActivity
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ToastUtil

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
        val intent = Intent(this, ComposeSettingsActivity::class.java).apply {
            putExtra("userId", user.userId)
            putExtra("userName", user.showName)
        }
        startActivity(intent)
    } catch (e: Exception) {
        ToastUtil.showToast(this, "无法启动设置页面: ${e.message}")
    }
}

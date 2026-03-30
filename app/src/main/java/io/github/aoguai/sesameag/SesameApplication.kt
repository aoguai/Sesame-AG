package io.github.aoguai.sesameag

import android.app.Application
import io.github.aoguai.sesameag.ui.theme.ThemeManager
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ToastUtil

/**
 * 芝麻粒应用主类
 *
 * 负责应用初始化
 */
class SesameApplication : Application() {

    companion object {
        private const val TAG = "SesameApplication"
        const val PREFERENCES_KEY = "sesame-ag"
        var hasPermissions: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        ToastUtil.init(this)
        Log.init(this)
        ThemeManager.init(this)
    }
}

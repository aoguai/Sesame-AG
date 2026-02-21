package fansirsqi.xposed.sesame

import android.app.Application
import android.content.Intent
import fansirsqi.xposed.sesame.ui.repository.ConfigRepository
import fansirsqi.xposed.sesame.service.CommandService
import fansirsqi.xposed.sesame.ui.theme.ThemeManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 芝麻粒应用主类
 *
 * 负责应用初始化
 */
class SesameApplication : Application() {

    companion object {
        private const val TAG = "SesameApplication"
        public const val PREFERENCES_KEY = "sesame-tk"
        var hasPermissions: Boolean = false

    }

    override fun onCreate() {
        super.onCreate()
        ToastUtil.init(this) // 初始化全局 Context
        Log.init(this)
        ThemeManager.init(this)
        ConfigRepository.init(this, PREFERENCES_KEY)
        startCommandService()
    }

    /**
     * 启动 CommandService
     */
    private fun startCommandService() {
        try {
            val intent = Intent(this, CommandService::class.java)
            startService(intent)
            Log.record(TAG, "✅ CommandService 已启动")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "❌ CommandService 启动失败:", e)
        }
    }

}
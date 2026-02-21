package fansirsqi.xposed.sesame.ui.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import fansirsqi.xposed.sesame.ui.model.UiMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConfigRepository {

    // 内存缓存 (StateFlow)，UI 观察它
    private val _uiMode = MutableStateFlow(UiMode.Web)
    val uiMode = _uiMode.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    // 键名常量
    private const val KEY_UI_OPTION = "ui_option"

    /**
     * 初始化：在 Application onCreate 中调用
     */
    fun init(context: Context, prefKey: String) {
        if (isInitialized) return

        // 获取 SP 实例
        prefs = context.getSharedPreferences(prefKey, Context.MODE_PRIVATE)

        // 同步一次初始状态到内存
        val savedValue = prefs.getString(KEY_UI_OPTION, UiMode.Web.value)
        _uiMode.value = UiMode.fromValue(savedValue)

        isInitialized = true
    }

    /**
     * 更新配置：同时更新内存和磁盘
     */
    fun setUiMode(mode: UiMode) {
        check(isInitialized) { "ConfigRepository 未初始化！" }
        // 1. 更新内存 (UI 瞬间响应)
        _uiMode.value = mode
        // 2. 持久化 (异步写入 XML)
        prefs.edit { putString(KEY_UI_OPTION, mode.value) }
    }
}
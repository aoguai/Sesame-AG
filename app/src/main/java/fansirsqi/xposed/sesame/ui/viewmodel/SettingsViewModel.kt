package fansirsqi.xposed.sesame.ui.viewmodel

import androidx.lifecycle.ViewModel
import fansirsqi.xposed.sesame.ui.model.UiMode
import fansirsqi.xposed.sesame.ui.repository.ConfigRepository

class SettingsViewModel : ViewModel() {
    // 直接暴露仓库的 StateFlow
    val uiMode = ConfigRepository.uiMode

    fun switchMode(newMode: UiMode) {
        ConfigRepository.setUiMode(newMode)
    }
}
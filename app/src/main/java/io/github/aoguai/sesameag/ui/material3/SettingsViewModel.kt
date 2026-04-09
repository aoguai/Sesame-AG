package io.github.aoguai.sesameag.ui.material3

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aoguai.sesameag.data.Config
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.model.ModelConfig
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.ModelOrder
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectAndCountModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private var allModules: List<ModuleState> = emptyList()
    
    // 🚀 记录当前会话用户是否手动触碰过设置
    private var isManualChanged = false

    fun loadSettings(userId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userId = userId) }
            
            // 1. 系统核心初始化
            Model.initAllModel()
            
            // 🚀 核心修复：加载对应用户好友列表
            UserMap.setCurrentUserId(userId)
            UserMap.load(userId) 
            
            Config.load(userId)
            // 🚀 重置修改标记：刚加载完时，用户尚未手动修改
            isManualChanged = false
            
            val user = UserMap.get(userId)
            val modelConfigMap = Model.getModelConfigMap()

            // 2. 提取权威配置实例
            val modules = ModelOrder.allConfig.mapNotNull { clazz ->
                val config = modelConfigMap[clazz.simpleName] ?: return@mapNotNull null
                if (config.group == ModelGroup.HIDE) return@mapNotNull null
                mapModelConfigToState(config)
            }

            allModules = modules

            _uiState.update { 
                it.copy(
                    modules = allModules, 
                    isLoading = false,
                    userName = user?.showName ?: userId
                ) 
            }
        }
    }

    private fun mapModelConfigToState(config: ModelConfig): ModuleState {
        val fieldList = mutableListOf<FieldState>()
        try {
            val modelFields = config.fields
            modelFields.values.forEach { field ->
                if (field != null) {
                    fieldList.add(mapFieldToState(field))
                }
            }
        } catch (e: Exception) {
            // 🚀 修复：ModelConfig 中 name 是公共属性
            Log.record("SettingsVM", "模块 [${config.name}] 字段映射异常: ${e.message}")
        }

        return ModuleState(
            // 🚀 修复：ModelConfig 中 code, name, group 是公共属性
            code = config.code,
            name = config.name,
            icon = config.icon,
            group = config.group ?: ModelGroup.OTHER,
            fields = fieldList
        )
    }

    private fun mapFieldToState(field: ModelField<*>): FieldState {
        var expandData: Any? = null
        try {
            when (field) {
                is ChoiceModelField -> expandData = field.getExpandKey()
                is SelectModelField -> expandData = field.getExpandValue()
                is SelectAndCountModelField -> expandData = field.getExpandValue()
            }
        } catch (_: Exception) { }

        val isCollection = field is SelectModelField || field is SelectAndCountModelField
        // 🚀 修复：ModelField 中没有 configValue 属性，应调用 getConfigValue() 方法
        val uiValue = if (isCollection) field.value else field.configValue

        return FieldState(
            code = field.code,
            name = field.name,
            desc = field.desc,
            type = field.getType(),
            value = uiValue,
            expandData = expandData,
            dependencyCode = field.dependencyCode,
            originalField = field
        )
    }

    fun selectModule(module: ModuleState?) {
        _uiState.update { it.copy(selectedModule = module, scrollToFieldCode = null) }
    }

    fun navigateToSearchResult(moduleCode: String, fieldCode: String) {
        val module = allModules.find { it.code == moduleCode }
        if (module != null) {
            val field = module.fields.find { it.code == fieldCode }
            val targetFieldCode = if (!field?.dependencyCode.isNullOrEmpty()) {
                field.dependencyCode
            } else {
                fieldCode
            }
            
            _uiState.update { 
                it.copy(
                    selectedModule = module,
                    scrollToFieldCode = targetFieldCode 
                ) 
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
    }

    fun getSearchResults(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()
        allModules.forEach { module ->
            module.fields.forEach { field ->
                if (field.name.contains(query, ignoreCase = true) || 
                    (field.desc?.contains(query, ignoreCase = true) == true)) {
                    results.add(SearchResult(module, field))
                }
            }
        }
        return results
    }

    data class SearchResult(val module: ModuleState, val field: FieldState)

    fun updateField(fieldState: FieldState, newValue: Any) {
        viewModelScope.launch {
            try {
                // 🚀 用户触发了交互，标记为已修改
                isManualChanged = true
                
                val isCollection = fieldState.originalField is SelectModelField || 
                                   fieldState.originalField is SelectAndCountModelField
                
                if (isCollection) {
                    fieldState.originalField.setObjectValue(newValue)
                } else {
                    fieldState.originalField.setConfigValue(newValue.toString())
                }
                
                _uiState.update { currentState ->
                    val newModules = currentState.modules.map { module ->
                        if (module.fields.any { it.originalField == fieldState.originalField }) {
                            module.copy(fields = module.fields.map { field ->
                                if (field.originalField == fieldState.originalField) {
                                    field.copy(value = newValue)
                                } else field
                            })
                        } else module
                    }
                    val newSelectedModule = currentState.selectedModule?.let { selected ->
                        if (selected.fields.any { it.originalField == fieldState.originalField }) {
                            selected.copy(fields = selected.fields.map { field ->
                                if (field.originalField == fieldState.originalField) {
                                    field.copy(value = newValue)
                                } else field
                            })
                        } else selected
                    }
                    currentState.copy(modules = newModules, selectedModule = newSelectedModule)
                }
                
                allModules = allModules.map { module ->
                    if (module.fields.any { it.originalField == fieldState.originalField }) {
                        module.copy(fields = module.fields.map { field ->
                            if (field.originalField == fieldState.originalField) {
                                field.copy(value = newValue)
                            } else field
                        })
                    } else module
                }
            } catch (e: Exception) {
                Log.record("SettingsVM", "更新字段失败: ${e.message}")
            }
        }
    }

    fun save(context: Context, isDetail: Boolean) {
        viewModelScope.launch {
            val userId = _uiState.value.userId
            val hasModify = Config.isModify(userId)
            
            if (hasModify) {
                if (Config.save(userId, false)) {
                    isManualChanged = false // 保存成功，清空将会话标记
                    _events.emit(SettingsEvent.ShowToast("保存成功！"))
                    if (!isDetail && !userId.isNullOrEmpty()) {
                        try {
                            val intent = Intent("com.eg.android.AlipayGphone.sesame.config_changed")
                            intent.putExtra("userId", userId)
                            context.sendBroadcast(intent)
                        } catch (e: Exception) {
                            Log.printStackTrace(e)
                        }
                    }
                } else {
                    _events.emit(SettingsEvent.ShowToast("保存失败！"))
                }
            } else {
                isManualChanged = false // 虽然没写磁盘，但说明已对齐，标记也清空
                if (!isDetail) {
                    _events.emit(SettingsEvent.ShowToast("配置未修改，无需保存！"))
                }
            }
            
            if (!userId.isNullOrEmpty()) {
                UserMap.save(userId)
                try {
                    val cooperateMapClass = Class.forName("io.github.aoguai.sesameag.util.maps.CooperateMap")
                    val getInstance = cooperateMapClass.getMethod("getInstance", Class::class.java)
                    val instance = getInstance.invoke(null, cooperateMapClass)
                    val saveMethod = cooperateMapClass.getMethod("save", String::class.java)
                    saveMethod.invoke(instance, userId)
                } catch (e: Exception) {
                    Log.record("SettingsVM", "CooperateMap 保存失败: ${e.message}")
                }
            }

            if (!isDetail) {
                _events.emit(SettingsEvent.Exit)
            }
        }
    }

    fun handleBack() {
        viewModelScope.launch {
            val userId = _uiState.value.userId
            // 🚀 核心优化：只有当用户手动改过配置，且内容确实不同时，才弹出确认
            if (isManualChanged && Config.isModify(userId)) {
                _events.emit(SettingsEvent.ShowExitConfirmation)
            } else {
                _events.emit(SettingsEvent.Exit)
            }
        }
    }

    fun discardAndExit() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.Exit)
        }
    }

    sealed class SettingsEvent {
        data class ShowToast(val message: String) : SettingsEvent()
        object Exit : SettingsEvent()
        object ShowExitConfirmation : SettingsEvent()
    }
}

package io.github.aoguai.sesameag.ui.material3

import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelGroup

/**
 * 字段状态
 */
data class FieldState(
    val code: String,
    val name: String,
    val desc: String?,
    val type: String,
    val value: Any?,
    val expandData: Any? = null,
    val dependencyCode: String? = null,
    val originalField: ModelField<*>
)

/**
 * 模块状态（对应一级目录，即 ModelOrder 中的条目）
 */
data class ModuleState(
    val code: String,
    val name: String,
    val icon: String,
    val group: ModelGroup,
    val fields: List<FieldState>
)

/**
 * UI 状态
 */
data class SettingsUiState(
    val modules: List<ModuleState> = emptyList(),
    val isLoading: Boolean = true,
    val userId: String? = null,
    val userName: String? = null,
    val selectedModule: ModuleState? = null, // 当前选中的详情模块，为 null 则显示列表页
    val scrollToFieldCode: String? = null // 🚀 新增：详情页需要滚动到的字段 Code
)

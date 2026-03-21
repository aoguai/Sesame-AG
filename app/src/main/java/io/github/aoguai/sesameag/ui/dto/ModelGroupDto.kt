package io.github.aoguai.sesameag.ui.dto

import java.io.Serializable

/**
 * 模型组数据传输对象。
 * 用于封装模型组的相关信息，包括组代码、名称和图标。
 *
 * @property code 模型组代码
 * @property name 模型组名称
 * @property icon 模型组图标
 */
data class ModelGroupDto(
    var code: String = "",
    var name: String = "",
    var icon: String = ""
) : Serializable {
    constructor() : this("", "", "")
}


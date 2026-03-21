package io.github.aoguai.sesameag.ui.dto

import java.io.Serializable

/**
 * 模型数据传输对象。
 * 用于封装模型的代码、名称、组代码以及模型字段展示信息。
 *
 * @property modelCode 模型代码
 * @property modelName 模型名称
 * @property modelIcon 模型图标
 * @property groupCode 组代码
 * @property modelFields 模型字段展示信息列表
 */
data class ModelDto(
    var modelCode: String = "",
    var modelName: String = "",
    var modelIcon: String = "",
    var groupCode: String = "",
    var modelFields: List<ModelFieldShowDto> = emptyList()
) : Serializable {
    constructor() : this("", "", "", "", emptyList())
}


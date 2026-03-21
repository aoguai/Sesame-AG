package io.github.aoguai.sesameag.ui.dto

import io.github.aoguai.sesameag.model.ModelField
import org.json.JSONException
import java.io.Serializable

/**
 * 模型字段信息数据传输对象。
 * 用于封装模型字段的详细信息，包括字段代码、名称、类型、扩展键、扩展值和配置值。
 *
 * @property code 字段代码
 * @property name 字段名称
 * @property type 字段类型
 * @property expandKey 扩展键，用于存储额外的信息
 * @property expandValue 扩展值，用于存储额外的信息
 * @property configValue 配置值，用于存储字段的配置信息
 * @property desc 字段描述
 */
data class ModelFieldInfoDto(
    var code: String = "",
    var name: String = "",
    var type: String = "",
    var expandKey: Any? = null,
    var expandValue: Any? = null,
    var configValue: String = "",
    var desc: String = ""
) : Serializable {

    constructor() : this("", "", "", null, null, "", "")

    companion object {
        /**
         * 将ModelField对象转换为ModelFieldInfoDto对象。
         *
         * @param modelField ModelField对象
         * @return ModelFieldInfoDto对象
         * @throws JSONException 如果转换过程中发生JSON异常
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun toInfoDto(modelField: ModelField<*>): ModelFieldInfoDto {
            return ModelFieldInfoDto(
                code = modelField.code,
                name = modelField.name,
                type = modelField.getType(),
                expandKey = modelField.getExpandKey(),
                expandValue = modelField.getExpandValue(),
                configValue = modelField.getConfigValue() ?: "",
                desc = modelField.desc
            )
        }
    }
}


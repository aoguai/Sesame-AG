package io.github.aoguai.sesameag.model

import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.modelFieldExt.*
import io.github.aoguai.sesameag.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import io.github.aoguai.sesameag.util.TimeTriggerParseOptions

/**
 * ModelFields 的 DSL 构建器
 * 统一管理 ModelField 的创建与归类
 */
class ModelFieldsBuilder(val modelFields: ModelFields = ModelFields()) {

    fun boolean(
        code: String, 
        name: String, 
        defaultValue: Boolean, 
        desc: String? = null,
        dependency: String? = null,
        setter: (BooleanModelField) -> Unit = {}
    ): BooleanModelField {
        return BooleanModelField(code, name, defaultValue, desc).apply {
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun choice(
        code: String, 
        name: String, 
        defaultValue: Int, 
        choices: Array<out String?>, 
        desc: String? = null,
        dependency: String? = null,
        setter: (ChoiceModelField) -> Unit = {}
    ): ChoiceModelField {
        return ChoiceModelField(code, name, defaultValue, choices).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun string(
        code: String, 
        name: String, 
        defaultValue: String, 
        desc: String? = null,
        dependency: String? = null,
        setter: (StringModelField) -> Unit = {}
    ): StringModelField {
        return StringModelField(code, name, defaultValue).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun integer(
        code: String, 
        name: String, 
        defaultValue: Int, 
        min: Int? = null, 
        max: Int? = null, 
        desc: String? = null,
        dependency: String? = null,
        setter: (IntegerModelField) -> Unit = {}
    ): IntegerModelField {
        return IntegerModelField(code, name, defaultValue, min, max).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun listJoin(
        code: String, 
        name: String, 
        defaultValue: MutableList<String>, 
        desc: String? = null,
        dependency: String? = null,
        setter: (ListJoinCommaToStringModelField) -> Unit = {}
    ): ListJoinCommaToStringModelField {
        return ListJoinCommaToStringModelField(code, name, defaultValue).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    /**
     * 分类标题（不带参数的分类总开关）
     */
    fun category(code: String, name: String, desc: String? = null, dependency: String? = null): StringModelField {
        val field = object : StringModelField(code, name, "") {
            override fun getType(): String = "CATEGORY"
        }
        field.desc = desc
        field.dependencyCode = dependency
        modelFields.addField(field)
        return field
    }

    @Suppress("UNCHECKED_CAST")
    fun selectAndCount(
        code: String,
        name: String,
        defaultValue: MutableMap<String, Int>,
        dataProvider: () -> List<MapperEntity>?,
        desc: String? = null,
        dependency: String? = null,
        setter: (SelectAndCountModelField) -> Unit = {}
    ): SelectAndCountModelField {
        val field = SelectAndCountModelField(
            code, 
            name, 
            defaultValue, 
            object : SelectAndCountModelField.SelectListFunc {
                @Suppress("UNCHECKED_CAST")
                override fun getList(): List<MapperEntity>? = dataProvider()
            }, 
            desc
        )
        field.dependencyCode = dependency
        modelFields.addField(field)
        setter(field)
        return field
    }

    /**
     * 核心多选列表方法
     */
    @Suppress("UNCHECKED_CAST")
    fun select(
        code: String, 
        name: String, 
        defaultValue: MutableSet<String>, 
        desc: String? = null,
        dependency: String? = null,
        setter: (SelectModelField) -> Unit = {},
        dataProvider: () -> List<MapperEntity>?
    ): SelectModelField {
        val field = SelectModelField(
            code, 
            name, 
            defaultValue, 
            object : SelectModelField.SelectListFunc {
                @Suppress("UNCHECKED_CAST")
                override fun getList(): List<MapperEntity>? = dataProvider()
            }
        )
        field.desc = desc
        field.dependencyCode = dependency
        modelFields.addField(field)
        setter(field)
        return field
    }

    /**
     * 静态列表多选
     */
    @Suppress("UNCHECKED_CAST")
    fun selectStatic(
        code: String,
        name: String,
        defaultValue: MutableSet<String>,
        data: List<MapperEntity>?,
        desc: String? = null,
        dependency: String? = null,
        setter: (SelectModelField) -> Unit = {}
    ): SelectModelField {
        val field = SelectModelField(code, name, defaultValue, data)
        field.desc = desc
        field.dependencyCode = dependency
        modelFields.addField(field)
        setter(field)
        return field
    }

    fun urlText(
        code: String,
        name: String,
        url: String,
        desc: String? = null,
        dependency: String? = null,
        setter: (TextModelField.UrlTextModelField) -> Unit = {}
    ): TextModelField.UrlTextModelField {
        return TextModelField.UrlTextModelField(code, name, url).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun readOnlyText(
        code: String,
        name: String,
        text: String,
        desc: String? = null,
        dependency: String? = null,
        setter: (TextModelField.ReadOnlyTextModelField) -> Unit = {}
    ): TextModelField.ReadOnlyTextModelField {
        return TextModelField.ReadOnlyTextModelField(code, name, text).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun timePoint(
        code: String,
        name: String,
        defaultValue: String,
        allowDisable: Boolean = false,
        allowSeconds: Boolean = false,
        desc: String? = null,
        dependency: String? = null,
        setter: (TimePointModelField) -> Unit = {}
    ): TimePointModelField {
        return TimePointModelField(code, name, defaultValue, allowDisable, allowSeconds).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun timeTrigger(
        code: String,
        name: String,
        defaultValue: String,
        options: TimeTriggerParseOptions = TimeTriggerParseOptions(),
        desc: String? = null,
        dependency: String? = null,
        setter: (TimeTriggerModelField) -> Unit = {}
    ): TimeTriggerModelField {
        return TimeTriggerModelField(code, name, defaultValue, options).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun hourOfDay(
        code: String,
        name: String,
        defaultValue: String,
        allowDisable: Boolean = false,
        allowDayEnd: Boolean = false,
        desc: String? = null,
        dependency: String? = null,
        setter: (HourOfDayModelField) -> Unit = {}
    ): HourOfDayModelField {
        return HourOfDayModelField(code, name, defaultValue, allowDisable, allowDayEnd).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }

    fun intervalString(
        code: String,
        name: String,
        defaultValue: String,
        minLimit: Int,
        maxLimit: Int,
        desc: String? = null,
        dependency: String? = null,
        setter: (StringModelField.IntervalStringModelField) -> Unit = {}
    ): StringModelField.IntervalStringModelField {
        return StringModelField.IntervalStringModelField(code, name, defaultValue, minLimit, maxLimit).apply {
            this.desc = desc
            this.dependencyCode = dependency
            modelFields.addField(this)
            setter(this)
        }
    }
}

/**
 * 扩展函数，用于启动 DSL
 */
fun buildModelFields(block: ModelFieldsBuilder.() -> Unit): ModelFields {
    return ModelFieldsBuilder().apply(block).modelFields
}

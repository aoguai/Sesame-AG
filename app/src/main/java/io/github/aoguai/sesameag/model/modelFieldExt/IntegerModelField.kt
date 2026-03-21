package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.ui.StringDialog
import io.github.aoguai.sesameag.util.Log

/**
 * Integer 类型字段类，继承自 ModelField<Int>
 * 该类用于表示具有最小值和最大值限制的整数字段。
 */
open class IntegerModelField : ModelField<Int> {
    
    /** 最小值限制 */
    val minLimit: Int?
    
    /** 最大值限制 */
    val maxLimit: Int?

    /**
     * 构造函数：创建一个没有最小值和最大值限制的 Integer 类型字段
     *
     * @param code 字段代码
     * @param name 字段名称
     * @param value 字段初始值
     */
    constructor(code: String, name: String, value: Int) : super(code, name, value) {
        this.minLimit = null
        this.maxLimit = null
        valueType = Int::class.java
    }

    /**
     * 构造函数：创建一个具有最小值和最大值限制的 Integer 类型字段
     *
     * @param code 字段代码
     * @param name 字段名称
     * @param value 字段初始值
     * @param minLimit 最小值限制
     * @param maxLimit 最大值限制
     */
    constructor(code: String, name: String, value: Int, minLimit: Int?, maxLimit: Int?) : super(code, name, value) {
        this.minLimit = minLimit
        this.maxLimit = maxLimit
        valueType = Int::class.java
    }

    protected open fun parseIntValue(objectValue: Any?): Int? {
        return when (objectValue) {
            null -> null
            is Number -> objectValue.toInt()
            is Boolean -> if (objectValue) 1 else 0
            is String -> objectValue.trim().toIntOrNull()
            else -> objectValue.toString().trim().toIntOrNull()
        }
    }

    protected open fun clampValue(rawValue: Int): Int {
        var newValue = rawValue
        minLimit?.let { newValue = maxOf(it, newValue) }
        maxLimit?.let { newValue = minOf(it, newValue) }
        return newValue
    }

    /**
     * 获取字段类型
     *
     * @return 返回字段类型的字符串表示 "INTEGER"
     */
    override fun getType(): String = "INTEGER"

    /**
     * 获取字段的配置值（将当前的值转换为字符串）
     *
     * @return 返回字段的字符串形式的配置值
     */
    override fun getConfigValue(): String? = value?.toString()

    override fun setObjectValue(objectValue: Any?) {
        value = clampValue(parseIntValue(objectValue) ?: defaultValue ?: 0)
    }

    /**
     * 设置字段的配置值（根据配置值设置新的值，并且在有最小/最大值限制的情况下进行限制）
     *
     * @param configValue 字段的配置值
     */
    override fun setConfigValue(configValue: String?) {
        if (configValue.isNullOrBlank()) {
            value = clampValue(defaultValue ?: 0)
            return
        }
        setObjectValue(configValue)
    }

    /**
     * 获取视图（返回一个 Button，点击后弹出编辑框）
     *
     * @param context 上下文
     * @return 按钮视图
     */
    override fun getView(context: Context): View {
        return Button(context).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.selection_color))
            background = ContextCompat.getDrawable(context, R.drawable.dialog_list_button)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = 150
            maxHeight = 180
            setPaddingRelative(40, 0, 40, 0)
            isAllCaps = false
            setOnClickListener { v ->
                StringDialog.showEditDialog(v.context, (v as Button).text, this@IntegerModelField)
            }
        }
    }

    /**
     * MultiplyIntegerModelField 类，继承自 IntegerModelField，处理带乘数的整数类型字段
     * 该类在设置值时会乘以指定的倍数。
     */
    class MultiplyIntegerModelField(
        code: String,
        name: String,
        value: Int,
        minLimit: Int?,
        maxLimit: Int?,
        /** 乘数，用于计算最终值 */
        val multiple: Int
    ) : IntegerModelField(code, name, value * multiple, minLimit, maxLimit) {

        private fun clampExpandedValue(rawValue: Int): Int {
            var newValue = rawValue
            minLimit?.let { newValue = maxOf(it * multiple, newValue) }
            maxLimit?.let { newValue = minOf(it * multiple, newValue) }
            return newValue
        }

        /**
         * 获取字段类型
         *
         * @return 返回字段类型的字符串表示 "MULTIPLY_INTEGER"
         */
        override fun getType(): String = "MULTIPLY_INTEGER"

        /**
         * 设置字段的配置值（乘数影响最终值）
         *
         * @param configValue 字段的配置值
         */
        override fun setConfigValue(configValue: String?) {
            if (configValue.isNullOrBlank()) {
                reset()
                return
            }

            setObjectValue(configValue)
        }

        override fun setObjectValue(objectValue: Any?) {
            val parsedValue = parseIntValue(objectValue) ?: run {
                value = clampExpandedValue(defaultValue ?: 0)
                return
            }

            val expandedValue = when {
                // 兼容 UI / 旧配置中的“未乘倍率”值，例如 50(分钟)。
                parsedValue >= 0 && maxLimit != null && parsedValue <= maxLimit -> parsedValue * multiple
                // 已经是内部存储值（例如 3000000ms）时直接使用。
                else -> parsedValue
            }

            value = clampExpandedValue(expandedValue)
        }

        /**
         * 获取字段的配置值（返回值除以乘数）
         *
         * @return 配置值（字段值除以乘数）
         */
        override fun getConfigValue(): String? = value?.let { (it / multiple).toString() }
    }
}


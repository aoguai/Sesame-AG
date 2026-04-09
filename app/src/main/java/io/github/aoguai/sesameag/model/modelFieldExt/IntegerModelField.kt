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

    constructor(code: String?, name: String?, value: Int) : super(code, name, value) {
        this.minLimit = null
        this.maxLimit = null
    }

    constructor(code: String?, name: String?, value: Int, minLimit: Int?, maxLimit: Int?) : super(code, name, value) {
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

    override fun getType(): String {
        return "INTEGER"
    }

    override val configValue: String
        get() = value.toString()

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

    open class MultiplyIntegerModelField(
        code: String?,
        name: String?,
        value: Int,
        minLimit: Int?,
        maxLimit: Int?,
        /** 乘数，用于计算最终值 */
        val multiple: Int
    ) : IntegerModelField(code, name, value * multiple, minLimit, maxLimit) {

        override fun getType(): String {
            return "MULTIPLY_INTEGER"
        }

        private fun clampExpandedValue(rawValue: Int): Int {
            var newValue = rawValue
            minLimit?.let { newValue = maxOf(it * multiple, newValue) }
            maxLimit?.let { newValue = minOf(it * multiple, newValue) }
            return newValue
        }

        override fun setConfigValue(configValue: String?) {
            if (configValue.isNullOrBlank()) {
                reset()
                return
            }
            super.setConfigValue(configValue)
            try {
                value *= multiple
                return
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }
            reset()
        }

        override val configValue: String
            get() = (value / multiple).toString()
    }
}


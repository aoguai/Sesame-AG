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
import io.github.aoguai.sesameag.ui.ChoiceDialog

/**
 * 选择型字段，用于在多个选项中选择一个
 */
class ChoiceModelField : ModelField<Int> {
    
    private var choiceArray: Array<out String?>? = null

    constructor(code: String, name: String, value: Int) : super(code, name, value) {
        valueType = Int::class.java
    }

    constructor(code: String, name: String, value: Int, choiceArray: Array<out String?>) : super(code, name, value) {
        this.choiceArray = choiceArray
        valueType = Int::class.java
    }

    constructor(code: String, name: String, value: Int, desc: String) : super(code, name, value, desc) {
        valueType = Int::class.java
    }

    constructor(code: String, name: String, value: Int, choiceArray: Array<out String?>, desc: String) 
        : super(code, name, value, desc) {
        this.choiceArray = choiceArray
        valueType = Int::class.java
    }

    override fun getType(): String = "CHOICE"

    override fun getExpandKey(): Array<out String?>? = choiceArray

    private fun parseChoiceValue(objectValue: Any?): Int? {
        return when (objectValue) {
            null -> null
            is Number -> objectValue.toInt()
            is Boolean -> if (objectValue) 1 else 0
            is String -> objectValue.trim().toIntOrNull()
            else -> objectValue.toString().trim().toIntOrNull()
        }
    }

    private fun normalizeChoiceValue(rawValue: Int?): Int {
        val fallback = defaultValue ?: 0
        val parsedValue = rawValue ?: fallback
        val lastIndex = (choiceArray?.size ?: 0) - 1
        return if (lastIndex >= 0) parsedValue.coerceIn(0, lastIndex) else parsedValue
    }

    override fun setObjectValue(objectValue: Any?) {
        value = normalizeChoiceValue(parseChoiceValue(objectValue))
    }
    
    /**
     * 设置配置值
     * 直接解析整数值，避免父类的类型推断错误
     */
    override fun setConfigValue(configValue: String?) {
        value = normalizeChoiceValue(parseChoiceValue(configValue))
    }
    
    /**
     * 获取配置值
     */
    override fun getConfigValue(): String? = value?.toString()

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
                ChoiceDialog.show(v.context, (v as Button).text, this@ChoiceModelField)
            }
        }
    }
}


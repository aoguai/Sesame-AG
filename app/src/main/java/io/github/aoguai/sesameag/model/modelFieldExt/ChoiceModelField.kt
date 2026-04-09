package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.ui.ChoiceDialog

/**
 * 选择型字段，用于在多个选项中选择一个
 */
class ChoiceModelField : ModelField<Int> {
    private var choiceArray: Array<String?>? = null

    constructor(code: String?, name: String?, value: Int?) : super(code, name, value ?: 0)

    constructor(code: String?, name: String?, value: Int?, choiceArray: Array<out String?>?) : super(code, name, value ?: 0) {
        @Suppress("UNCHECKED_CAST")
        this.choiceArray = choiceArray as Array<String?>?
    }

    constructor(code: String?, name: String?, value: Int?, desc: String?) : super(code, name, value ?: 0, desc)

    constructor(code: String?, name: String?, value: Int?, choiceArray: Array<out String?>?, desc: String?) : super(code, name, value ?: 0, desc) {
        @Suppress("UNCHECKED_CAST")
        this.choiceArray = choiceArray as Array<String?>?
    }

    override fun getType(): String = "CHOICE"

    @JsonIgnore
    override fun getExpandKey(): Array<String?>? {
        return choiceArray
    }

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


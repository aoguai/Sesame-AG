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

/**
 * String类型字段类
 * 该类用于表示字符串值字段，点击按钮弹出编辑对话框
 */
open class StringModelField(code: String, name: String, value: String) : ModelField<String>(code, name, null) {

    init {
        // Avoid calling subclass normalization before subclass constructor properties are initialized.
        val initialValue = value.trim()
        defaultValue = initialValue
        this.value = initialValue
    }

    override fun getType(): String = "STRING"

    override fun getConfigValue(): String? = value

    protected open fun normalizeValue(rawValue: String): String {
        return rawValue.trim()
    }

    override fun setObjectValue(objectValue: Any?) {
        if (objectValue == null) {
            reset()
            return
        }
        value = normalizeValue(objectValue.toString())
    }

    override fun setConfigValue(configValue: String?) {
        setObjectValue(configValue)
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
                StringDialog.showEditDialog(v.context, (v as Button).text, this@StringModelField)
            }
        }
    }

    class TimeStringModelField(
        code: String,
        name: String,
        value: String,
        private val allowDisable: Boolean = false,
        private val extraAllowedValues: Set<String> = emptySet()
    ) : StringModelField(code, name, value) {

        @Transient
        private var initialized = false

        init {
            initialized = true
            setObjectValue(value)
            defaultValue = this.value
        }

        override fun normalizeValue(rawValue: String): String {
            if (!initialized) {
                return rawValue.trim()
            }
            val trimmed = rawValue.trim()
            if (allowDisable && trimmed == "-1") {
                return "-1"
            }
            if (trimmed in extraAllowedValues) {
                return trimmed
            }

            val digits = trimmed.filter { it.isDigit() }
            val normalized = when (digits.length) {
                2 -> digits + "00"
                3 -> "0$digits"
                4, 6 -> digits
                else -> defaultValue ?: ""
            }

            if (normalized.isBlank()) {
                return normalized
            }

            val hour = normalized.substring(0, 2).toIntOrNull() ?: return defaultValue ?: ""
            val minute = normalized.substring(2, 4).toIntOrNull() ?: return defaultValue ?: ""
            if (hour !in 0..23 || minute !in 0..59) {
                return defaultValue ?: ""
            }

            if (normalized.length == 6) {
                val second = normalized.substring(4, 6).toIntOrNull() ?: return defaultValue ?: ""
                if (second !in 0..59) {
                    return defaultValue ?: ""
                }
            }

            return normalized
        }
    }

    class IntervalStringModelField(
        code: String,
        name: String,
        value: String,
        private val minLimit: Int,
        private val maxLimit: Int
    ) : StringModelField(code, name, value) {

        init {
            require(minLimit <= maxLimit) { "minLimit must be <= maxLimit" }
            setObjectValue(value)
        }

        override fun normalizeValue(rawValue: String): String {
            val trimmed = rawValue.trim()
            if (trimmed.isBlank()) {
                return defaultValue ?: maxLimit.toString()
            }

            val parts = trimmed.split("-")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            return when (parts.size) {
                1 -> clamp(parts[0].toIntOrNull() ?: maxLimit).toString()
                2 -> {
                    val first = clamp(parts[0].toIntOrNull() ?: minLimit)
                    val second = clamp(parts[1].toIntOrNull() ?: maxLimit)
                    val rangeMin = minOf(first, second)
                    val rangeMax = maxOf(first, second)
                    if (rangeMin == rangeMax) {
                        rangeMin.toString()
                    } else {
                        "$rangeMin-$rangeMax"
                    }
                }
                else -> defaultValue ?: maxLimit.toString()
            }
        }

        private fun clamp(value: Int): Int = value.coerceIn(minLimit, maxLimit)
    }
}


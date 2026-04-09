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
import io.github.aoguai.sesameag.ui.StringDialog
import io.github.aoguai.sesameag.ui.extension.openUrl

open class TextModelField(code: String?, name: String?, value: String?) : ModelField<String?>(code, name, value) {
    override fun getType(): String {
        return "TEXT"
    }

    override val configValue: String
        get() = value ?: ""

    override fun setConfigValue(configValue: String?) {
        value = configValue
    }

    @JsonIgnore
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
                StringDialog.showReadDialog(v.context, (v as Button).text, this@TextModelField)
            }
        }
    }

    open class UrlTextModelField(code: String?, name: String?, value: String?) : ReadOnlyTextModelField(code, name, value) {
        override fun getType(): String {
            return "URL_TEXT"
        }

        @JsonIgnore
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
                    val innerContext = v.context
                    val url = configValue
                    innerContext.openUrl(url)
                }
            }
        }
    }

    open class ReadOnlyTextModelField(code: String?, name: String?, value: String?) : TextModelField(code, name, value) {
        override fun getType(): String {
            return "READ_TEXT"
        }

        @get:JsonIgnore
        override var value: String?
            get() = super.value
            set(_) {
                // Read-only field, no assignment
            }

        override fun setConfigValue(configValue: String?) {
            // Read-only field, no assignment
        }
    }
}


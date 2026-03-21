package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.content.Intent
import android.net.Uri
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

/**
 * 文本字段类
 */
open class TextModelField(code: String, name: String, value: String) : ModelField<String>(code, name, value) {

    override fun getType(): String = "TEXT"

    override fun getConfigValue(): String? = value

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

    /**
     * URL文本字段，点击打开网页
     */
    class UrlTextModelField(code: String, name: String, value: String) : ReadOnlyTextModelField(code, name, value) {

        override fun getType(): String = "URL_TEXT"

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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getConfigValue()))
                    innerContext.startActivity(intent)
                }
            }
        }
    }

    /**
     * 只读文本字段
     */
    open class ReadOnlyTextModelField(code: String, name: String, value: String) : TextModelField(code, name, value) {

        override fun getType(): String = "READ_TEXT"

        override fun setConfigValue(configValue: String?) {
            // 只读，不设置值
        }
    }
}


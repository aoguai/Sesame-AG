package io.github.aoguai.sesameag.model.modelFieldExt

import android.annotation.SuppressLint
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
 * 表示一个存储字符串列表的字段模型，用于管理和展示列表数据。
 * 提供基本的获取类型、配置值以及视图展示的方法。
 */
open class ListModelField(code: String, name: String, value: MutableList<String>) : ModelField<MutableList<String>>(code, name, value) {

    override fun getType(): String = "LIST"

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun getView(context: Context): View {
        val btn = Button(context).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.selection_color))
            background = context.resources.getDrawable(R.drawable.dialog_list_button, context.theme)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = 150
            maxHeight = 180
            setPaddingRelative(40, 0, 40, 0)
            isAllCaps = false
            setOnClickListener { v ->
                StringDialog.showEditDialog(v.context, (v as Button).text, this@ListModelField)
            }
        }
        return btn
    }

    /**
     * 一个子类，用于将字符串列表转换为逗号分隔的字符串，并实现相应的设置和获取功能。
     */
    class ListJoinCommaToStringModelField(code: String, name: String, value: MutableList<String>) : ListModelField(code, name, value) {

        override fun setConfigValue(configValue: String?) {
            if (configValue == null) {
                reset()
                return
            }
            val list = mutableListOf<String>()
            for (str in configValue.split(",")) {
                if (str.isNotEmpty()) {
                    list.add(str)
                }
            }
            value = list
        }

        override fun getConfigValue(): String {
            return value?.joinToString(",") ?: ""
        }
    }
}


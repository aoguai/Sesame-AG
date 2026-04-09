package io.github.aoguai.sesameag.model.modelFieldExt

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.core.type.TypeReference
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField
import java.util.*
import io.github.aoguai.sesameag.ui.StringDialog

/**
 * 列表字段模型。
 * 内部存储为 MutableList<String> 以保证与业务逻辑（如 AntForest.useCardBoot）兼容。
 * 提供兼容性构造函数以接收来自配置端的 MutableList<String?>。
 */
open class ListModelField : ModelField<MutableList<String>> {

    constructor(code: String?, name: String?, value: MutableList<String>) : super(code, name, value)

    /**
     * 兼容性构造函数：支持来自 BaseModel / ModelFieldsBuilder 等处的 MutableList<String?> 调用。
     * 使用 Collection 类型以避免与 MutableList 构造函数产生 JVM 签名冲突。
     * 内部通过 filterNotNull 转换为不可空列表。
     */
    constructor(code: String?, name: String?, value: Collection<String?>) :
        super(code, name, value.filterNotNull().toMutableList())

    override fun getType(): String {
        return "LIST"
    }

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
     * 逗号分隔的字符串列表字段。
     */
    open class ListJoinCommaToStringModelField : ListModelField {
        constructor(code: String?, name: String?, value: MutableList<String>) : super(code, name, value)

        constructor(code: String?, name: String?, value: Collection<String?>) : super(code, name, value)

        override fun setConfigValue(configValue: String?) {
            if (configValue == null) {
                reset()
                return
            }
            val list: MutableList<String> = ArrayList()
            for (str in configValue.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (str.isNotEmpty()) {
                    list.add(str)
                }
            }
            value = list
        }

        override val configValue: String
            get() = java.lang.String.join(",", value)

        override fun toConfigValue(value: MutableList<String>?): Any? {
            return if (value == null) "" else java.lang.String.join(",", value)
        }
    }

    companion object {
        private val typeReference: TypeReference<List<String>> = object : TypeReference<List<String>>() {}
    }
}

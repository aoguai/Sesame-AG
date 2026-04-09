package io.github.aoguai.sesameag.model.modelFieldExt

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField

/**
 * Boolean类型字段类
 * 该类用于表示布尔值字段，使用Switch控件进行展示
 */
class BooleanModelField : ModelField<Boolean> {
    
    constructor(code: String?, name: String?, value: Boolean) : super(code, name, value)

    constructor(code: String?, name: String?, value: Boolean, desc: String?) : super(code, name, value, desc)

    @JsonIgnore
    override fun getType(): String {
        return "BOOLEAN"
    }

    // 此时 super.value 返回的就是 Boolean (非空)，可以直接在 Kotlin 中 if (field.value) 使用

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun getView(context: Context): View {
        return Switch(context).apply {
            text = name // 设置 Switch 的文本为字段名称
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ) // 设置布局参数
            minHeight = 150 // 设置最小高度
            maxHeight = 180 // 设置最大高度
            setPaddingRelative(40, 0, 40, 0) // 设置左右内边距
            isChecked = value ?: false // 根据字段值设置 Switch 的选中状态
            // 设置点击监听器，更新字段值
            setOnClickListener { v ->
                setObjectValue((v as Switch).isChecked)
            }
        }
    }
}


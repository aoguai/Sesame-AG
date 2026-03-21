package io.github.aoguai.sesameag.model.modelFieldExt

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.model.ModelField

/**
 * 空模型字段，用于显示按钮但不存储值
 *
 * @property clickRunner 点击按钮时执行的操作，如果为null则显示"无配置项"提示
 */
class EmptyModelField : ModelField<Any?> {
    
    private val clickRunner: Runnable?

    constructor(code: String, name: String) : super(code, name, null) {
        this.clickRunner = null
    }

    constructor(code: String, name: String, clickRunner: Runnable) : super(code, name, null) {
        this.clickRunner = clickRunner
    }

    override fun getType(): String = "EMPTY"

    override fun setObjectValue(objectValue: Any?) {
        // 空实现，不存储值
    }
    
    override fun setConfigValue(configValue: String?) {
        // 空实现，EmptyModelField不需要存储配置值
    }
    
    override fun getConfigValue(): String? {
        // 返回null，EmptyModelField没有配置值
        return null
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

            setOnClickListener {
                if (clickRunner != null) {
                    AlertDialog.Builder(context)
                        .setTitle("警告")
                        .setMessage("确认执行该操作？")
                        .setPositiveButton(R.string.ok) { dialog, _ ->
                            clickRunner.run()
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                } else {
                    Toast.makeText(context, "无配置项", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}


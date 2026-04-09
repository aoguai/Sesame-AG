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

class EmptyModelField : ModelField<Any> {
    private val clickRunner: Runnable?

    // 核心修复：传入 Any() 作为占位符，避免基类 value 为 null 的校验报错
    constructor(code: String?, name: String?) : super(code, name, Any()) {
        this.clickRunner = null
    }

    constructor(code: String?, name: String?, clickRunner: Runnable?) : super(code, name, Any()) {
        this.clickRunner = clickRunner
    }

    @JsonIgnore
    override fun getType(): String {
        return "EMPTY"
    }

    @get:JsonIgnore
    override var value: Any
        get() = super.value
        set(v) { super.value = v }

    override fun setObjectValue(objectValue: Any?) {
        // EmptyModelField does not store value
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


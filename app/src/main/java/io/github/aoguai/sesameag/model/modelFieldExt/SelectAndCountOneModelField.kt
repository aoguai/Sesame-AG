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
import io.github.aoguai.sesameag.entity.KVMap
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.ui.widget.ListDialog
import java.util.Objects

class SelectAndCountOneModelField : ModelField<KVMap<String?, Int?>>, SelectModelFieldFunc {
    private var selectListFunc: SelectListFunc? = null
    private var expandValue: List<MapperEntity>? = null

    constructor(code: String?, name: String?, value: KVMap<String?, Int?>, expandValue: List<MapperEntity>?) : super(code, name, value) {
        this.expandValue = expandValue
    }

    constructor(code: String?, name: String?, value: KVMap<String?, Int?>, selectListFunc: SelectListFunc?) : super(code, name, value) {
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String {
        return "SELECT_AND_COUNT_ONE"
    }

    @JsonIgnore
    override fun getExpandValue(): List<MapperEntity>? {
        return if (selectListFunc == null) expandValue else selectListFunc!!.getList()
    }

    override fun getView(context: Context): View {
        val btn = Button(context).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.selection_color))
            background = ContextCompat.getDrawable(context, R.drawable.dialog_list_button)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = 150
            setPaddingRelative(40, 0, 40, 0)
            isAllCaps = false
            setOnClickListener { v ->
                ListDialog.show(v.context, (v as Button).text, this@SelectAndCountOneModelField, ListDialog.ListType.RADIO)
            }
        }
        return btn
    }

    override fun clear() {
        value = defaultValue!!
    }

    override fun get(id: String?): Int? {
        val kvMap = value
        return if (Objects.equals(kvMap.key, id)) {
            kvMap.value
        } else 0
    }

    override fun add(id: String?, count: Int?) {
        value = KVMap(id, count ?: 0)
    }

    override fun remove(id: String?) {
        if (Objects.equals(value.key, id)) {
            value = defaultValue!!
        }
    }

    override fun contains(id: String?): Boolean {
        return Objects.equals(value.key, id)
    }

    interface SelectListFunc {
        fun getList(): List<MapperEntity>?
    }
}


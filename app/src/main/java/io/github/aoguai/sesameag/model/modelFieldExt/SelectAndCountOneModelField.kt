package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.entity.KVMap
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.ui.widget.ListDialog

class SelectAndCountOneModelField : ModelField<KVMap<String, Int>>, SelectModelFieldFunc {
    
    private val selectListFunc: SelectListFunc?
    private val expandValueList: List<MapperEntity>?

    constructor(code: String, name: String, value: KVMap<String, Int>, expandValue: List<MapperEntity>) : super(code, name, value) {
        this.expandValueList = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: KVMap<String, Int>, selectListFunc: SelectListFunc) : super(code, name, value) {
        this.selectListFunc = selectListFunc
        this.expandValueList = null
    }

    override fun getType(): String = "SELECT_AND_COUNT_ONE"

    override fun getExpandValue(): List<MapperEntity>? {
        return selectListFunc?.getList() ?: expandValueList
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
        value = defaultValue
    }

    override fun get(id: String?): Int? {
        val kvMap = value
        return if (kvMap != null && kvMap.key == id) {
            kvMap.value
        } else {
            0
        }
    }

    override fun add(id: String?, count: Int?) {
        if (id != null && count != null) {
            value = KVMap(id, count)
        }
    }

    override fun remove(id: String?) {
        val kvMap = value
        if (kvMap != null && kvMap.key == id) {
            value = defaultValue
        }
    }

    override fun contains(id: String?): Boolean {
        val kvMap = value
        return kvMap != null && kvMap.key == id
    }

    fun interface SelectListFunc {
        fun getList(): List<MapperEntity>
    }
}


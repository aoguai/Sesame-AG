package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import org.json.JSONException
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.ui.widget.ListDialog
import io.github.aoguai.sesameag.util.JsonUtil

class SelectModelField : ModelField<MutableSet<String>>, SelectModelFieldFunc {
    private var selectListFunc: SelectListFunc? = null
    private var expandValue: List<MapperEntity>? = null

    constructor(code: String?, name: String?, value: MutableSet<String>, expandValue: List<MapperEntity>?) : super(code, name, value) {
        this.expandValue = expandValue
    }

    constructor(code: String?, name: String?, value: Collection<String?>, expandValue: List<MapperEntity>?) :
        this(code, name, value.filterNotNull().toCollection(LinkedHashSet()), expandValue)

    constructor(code: String?, name: String?, value: MutableSet<String>, selectListFunc: SelectListFunc?) : super(code, name, value) {
        this.selectListFunc = selectListFunc
    }

    constructor(code: String?, name: String?, value: Collection<String?>, selectListFunc: SelectListFunc?) :
        this(code, name, value.filterNotNull().toCollection(LinkedHashSet()), selectListFunc)

    constructor(code: String?, name: String?, value: MutableSet<String>, expandValue: List<MapperEntity>?, desc: String?) : super(code, name, value, desc) {
        this.expandValue = expandValue
    }

    constructor(code: String?, name: String?, value: MutableSet<String>, selectListFunc: SelectListFunc?, desc: String?) : super(code, name, value, desc) {
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String = "SELECT"

    @JsonIgnore
    @Throws(JSONException::class)
    override fun getExpandValue(): List<MapperEntity>? {
        return if (selectListFunc == null) expandValue else selectListFunc!!.getList()
    }

    private fun normalizeId(rawId: Any?): String? {
        return rawId?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun sanitizeSelection(rawSelection: Any?): MutableSet<String> {
        val result = LinkedHashSet<String>()
        when (rawSelection) {
            is Iterable<*> -> rawSelection.forEach { item ->
                normalizeId(item)?.let { result.add(it) }
            }
            is Array<*> -> rawSelection.forEach { item ->
                normalizeId(item)?.let { result.add(it) }
            }
            else -> normalizeId(rawSelection)?.let { result.add(it) }
        }
        return result
    }

    override fun setObjectValue(objectValue: Any?) {
        if (objectValue == null) {
            reset()
            return
        }
        value = sanitizeSelection(objectValue)
    }
    
    /**
     * 设置配置值
     * 直接解析Set类型，避免父类的类型推断错误
     */
    override fun setConfigValue(configValue: String?) {
        if (configValue.isNullOrBlank()) {
            reset()
            return
        }
        val parsedValue = try {
            JsonUtil.parseObject(configValue, object : TypeReference<LinkedHashSet<String>>() {})
        } catch (e: Exception) {
            defaultValue ?: LinkedHashSet()
        }
        setObjectValue(parsedValue)
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
            maxHeight = 180
            setPaddingRelative(40, 0, 40, 0)
            isAllCaps = false
            setOnClickListener { v ->
                try {
                    ListDialog.show(v.context, (v as Button).text, this@SelectModelField)
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            }
        }
        return btn
    }

    override fun clear() {
        value.clear()
    }

    override fun get(id: String?): Int {
        return 0
    }

    override fun add(id: String?, count: Int?) {
        if (id != null) {
            value.add(id)
        }
    }

    override fun remove(id: String?) {
        if (id != null) {
            value.remove(id)
        }
    }

    override fun contains(id: String?): Boolean {
        if (id == null) return false
        return value.contains(id)
    }

    fun interface SelectListFunc {
        @Throws(JSONException::class)
        fun getList(): List<MapperEntity>?
    }
}


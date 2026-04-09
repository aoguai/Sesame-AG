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
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.entity.MapperEntity
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.ui.widget.ListDialog
import io.github.aoguai.sesameag.util.JsonUtil

class SelectAndCountModelField : ModelField<MutableMap<String, Int>>, SelectModelFieldFunc {
    private var selectListFunc: SelectListFunc? = null
    private var expandValue: List<MapperEntity>? = null

    constructor(code: String?, name: String?, value: MutableMap<String, Int>, expandValue: List<MapperEntity>?) : super(code, name, value) {
        this.expandValue = expandValue
    }

//    constructor(code: String?, name: String?, value: Map<String?, Int?>, expandValue: List<MapperEntity>?) :
//            this(code, name, sanitizeMap(value), expandValue)

    constructor(code: String?, name: String?, value: MutableMap<String, Int>, selectListFunc: SelectListFunc?) : super(code, name, value) {
        this.selectListFunc = selectListFunc
    }

//    constructor(code: String?, name: String?, value: Map<String?, Int?>, selectListFunc: SelectListFunc?) :
//            this(code, name, sanitizeMap(value), selectListFunc)

    constructor(code: String?, name: String?, value: MutableMap<String, Int>, expandValue: List<MapperEntity>?, desc: String?) : super(code, name, value, desc) {
        this.expandValue = expandValue
    }

//    constructor(code: String?, name: String?, value: Map<String?, Int?>, expandValue: List<MapperEntity>?, desc: String?) :
//            this(code, name, sanitizeMap(value), expandValue, desc)

    constructor(code: String?, name: String?, value: MutableMap<String, Int>, selectListFunc: SelectListFunc?, desc: String?) : super(code, name, value, desc) {
        this.selectListFunc = selectListFunc
    }

//    constructor(code: String?, name: String?, value: Map<String?, Int?>, selectListFunc: SelectListFunc?, desc: String?) :
//            this(code, name, sanitizeMap(value), selectListFunc, desc)

    override fun getType(): String = "SELECT_AND_COUNT"

    @JsonIgnore
    override fun getExpandValue(): List<MapperEntity>? {
        return if (selectListFunc == null) expandValue else selectListFunc!!.getList()
    }

    override fun setObjectValue(objectValue: Any?) {
        if (objectValue == null) {
            reset()
            return
        }
        value = sanitizeMap(objectValue)
    }
    
    /**
     * 设置配置值
     * 直接解析Map类型，避免父类的类型推断错误
     */
    override fun setConfigValue(configValue: String?) {
        if (configValue.isNullOrBlank()) {
            reset()
            return
        }
        val parsedValue = try {
            JsonUtil.parseObject(configValue, object : TypeReference<LinkedHashMap<String, Int>>() {})
        } catch (e: Exception) {
            defaultValue ?: LinkedHashMap()
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
                ListDialog.show(v.context, (v as Button).text, this@SelectAndCountModelField)
            }
        }
        return btn
    }

    override fun clear() {
        value.clear()
    }

    override fun get(id: String?): Int? {
        return normalizeId(id)?.let { value.get(it) }
    }

    override fun add(id: String?, count: Int?) {
        val normalizedId = normalizeId(id)
        if (normalizedId != null && count != null) {
            value.set(normalizedId, count)
        }
    }

    override fun remove(id: String?) {
        normalizeId(id)?.let { value.remove(it) }
    }

    override fun contains(id: String?): Boolean {
        return normalizeId(id)?.let { value.containsKey(it) } == true
    }

    fun interface SelectListFunc {
        fun getList(): List<MapperEntity>?
    }

    companion object {
        private fun normalizeId(rawId: Any?): String? {
            return rawId?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun normalizeCount(rawCount: Any?): Int {
            return when (rawCount) {
                null -> 0
                is Number -> rawCount.toInt()
                is Boolean -> if (rawCount) 1 else 0
                is String -> rawCount.trim().toIntOrNull() ?: 0
                else -> rawCount.toString().trim().toIntOrNull() ?: 0
            }
        }

        private fun sanitizeMap(rawSelection: Any?): MutableMap<String, Int> {
            val result = LinkedHashMap<String, Int>()
            if (rawSelection is Map<*, *>) {
                rawSelection.forEach { (rawId, rawCount) ->
                    val normalizedId = normalizeId(rawId) ?: return@forEach
                    result[normalizedId] = normalizeCount(rawCount)
                }
            }
            return result
        }
    }
}

package io.github.aoguai.sesameag.model.modelFieldExt

import io.github.aoguai.sesameag.util.TimeTriggerParseOptions
import io.github.aoguai.sesameag.util.TimeTriggerParser
import io.github.aoguai.sesameag.util.TimeTriggerSpec

class TimeTriggerModelField(
    code: String,
    name: String,
    value: String,
    private val parseOptions: TimeTriggerParseOptions = TimeTriggerParseOptions()
) : StringModelField(code, name, value) {

    @Transient
    private var cachedSpec: TimeTriggerSpec? = null

    @Transient
    private var initialized = false

    init {
        initialized = true
        setObjectValue(value)
        defaultValue = this.value
    }

    override fun normalizeValue(rawValue: String): String {
        if (!initialized) {
            return rawValue.trim()
        }
        return TimeTriggerParser.normalize(rawValue, parseOptions, defaultValue ?: "-1")
    }

    override fun setObjectValue(objectValue: Any?) {
        super.setObjectValue(objectValue)
        if (!initialized) {
            cachedSpec = null
            return
        }
        cachedSpec = value?.let { TimeTriggerParser.parse(it, parseOptions) }
    }

    fun getTriggerSpec(): TimeTriggerSpec {
        val currentValue = value ?: "-1"
        val cached = cachedSpec
        if (cached != null && cached.raw == currentValue) {
            return cached
        }
        return TimeTriggerParser.parse(currentValue, parseOptions).also {
            cachedSpec = it
        }
    }
}

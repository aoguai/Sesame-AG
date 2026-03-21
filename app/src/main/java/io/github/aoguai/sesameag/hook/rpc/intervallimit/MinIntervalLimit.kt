package io.github.aoguai.sesameag.hook.rpc.intervallimit

/**
 * 为已有的 [IntervalLimit] 增加“最小间隔”兜底。
 *
 * 典型场景：用户配置的区间过小导致访问过快触发风控，但仍希望保留可配置性。
 */
class MinIntervalLimit(
    private val delegate: IntervalLimit,
    private val minIntervalMs: Int
) : IntervalLimit {

    init {
        require(minIntervalMs > 0) { "minIntervalMs must be > 0" }
    }

    override val interval: Int?
        get() {
            val raw = delegate.interval ?: minIntervalMs
            return if (raw < minIntervalMs) minIntervalMs else raw
        }

    override var time: Long = 0
}



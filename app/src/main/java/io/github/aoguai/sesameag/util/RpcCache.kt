package io.github.aoguai.sesameag.util

import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * RPC 请求缓存（短 TTL + LRU）
 *
 * 用于缓存短时间内重复的查询类 RPC，降低网络开销与重复解析成本。
 *
 * 设计目标：
 * - LRU 淘汰（最近最少使用）
 * - 线程安全
 * - 自动过期清理
 * - 可观测 metrics（Phase 6 会用于状态导出）
 */
object RpcCache {

    private const val TAG = "RpcCache"

    private const val DEFAULT_TTL_MS = 5_000L

    // 限制缓存数量，避免内存膨胀
    private const val MAX_CACHE_SIZE = 1_000

    private data class CacheEntry(
        val value: String,
        val createdAtMs: Long,
        val ttlMs: Long,
        var lastAccessAtMs: Long
    ) {
        fun isExpired(nowMs: Long): Boolean = nowMs - createdAtMs > ttlMs
    }

    private val hitCount = AtomicLong(0)
    private val missCount = AtomicLong(0)
    private val putCount = AtomicLong(0)
    private val evictCount = AtomicLong(0)
    private val expiredRemoveCount = AtomicLong(0)

    private val lock = ReentrantReadWriteLock()

    // key -> entry
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // LRU access order（只在 write lock 下维护）
    private val accessOrder = LinkedHashMap<String, Long>(16, 0.75f, true)

    private fun generateKey(method: String?, data: String?): String? {
        if (method.isNullOrBlank()) return null
        val dataHash = data?.hashCode() ?: 0
        return "${method}_${dataHash}"
    }

    fun get(method: String?, data: String?): String? {
        val key = generateKey(method, data) ?: return null
        val nowMs = System.currentTimeMillis()

        return lock.write {
            val entry = cache[key]
            if (entry == null) {
                missCount.incrementAndGet()
                return@write null
            }

            if (entry.isExpired(nowMs)) {
                cache.remove(key)
                accessOrder.remove(key)
                expiredRemoveCount.incrementAndGet()
                missCount.incrementAndGet()
                return@write null
            }

            entry.lastAccessAtMs = nowMs
            accessOrder[key] = nowMs
            hitCount.incrementAndGet()
            entry.value
        }
    }

    fun put(method: String?, data: String?, value: String, ttlMs: Long = DEFAULT_TTL_MS) {
        if (value.isBlank()) return
        val key = generateKey(method, data) ?: return

        putCount.incrementAndGet()
        val nowMs = System.currentTimeMillis()

        lock.write {
            if (cache.size >= MAX_CACHE_SIZE) {
                cleanExpiredEntriesLocked(nowMs)
                if (cache.size >= MAX_CACHE_SIZE) {
                    val lruKey = accessOrder.entries.firstOrNull()?.key
                    if (!lruKey.isNullOrBlank()) {
                        cache.remove(lruKey)
                        accessOrder.remove(lruKey)
                        evictCount.incrementAndGet()
                        Log.runtime(TAG, "LRU 淘汰: $lruKey")
                    }
                }
            }

            cache[key] = CacheEntry(value = value, createdAtMs = nowMs, ttlMs = ttlMs, lastAccessAtMs = nowMs)
            accessOrder[key] = nowMs
        }
    }

    fun invalidate(method: String?) {
        if (method.isNullOrBlank()) return
        lock.write {
            val keysToRemove = cache.keys.filter { it.startsWith(method) }
            for (key in keysToRemove) {
                cache.remove(key)
                accessOrder.remove(key)
            }
        }
    }

    fun clear() {
        lock.write {
            cache.clear()
            accessOrder.clear()
        }
    }

    private fun cleanExpiredEntriesLocked(nowMs: Long) {
        val expiredKeys = cache.entries
            .asSequence()
            .filter { it.value.isExpired(nowMs) }
            .map { it.key }
            .toList()

        for (key in expiredKeys) {
            cache.remove(key)
            accessOrder.remove(key)
        }

        if (expiredKeys.isNotEmpty()) {
            expiredRemoveCount.addAndGet(expiredKeys.size.toLong())
            Log.runtime(TAG, "清除过期缓存: ${expiredKeys.size} 个")
        }
    }

    fun getMetricsSnapshot(): Map<String, Any?> {
        return linkedMapOf(
            "size" to cache.size,
            "maxSize" to MAX_CACHE_SIZE,
            "hit" to hitCount.get(),
            "miss" to missCount.get(),
            "put" to putCount.get(),
            "evict" to evictCount.get(),
            "expiredRemove" to expiredRemoveCount.get()
        )
    }

    fun getStats(): String {
        val nowMs = System.currentTimeMillis()
        lock.write { cleanExpiredEntriesLocked(nowMs) }
        return lock.read {
            "RpcCache size=${cache.size}/$MAX_CACHE_SIZE hit=${hitCount.get()} miss=${missCount.get()}"
        }
    }
}



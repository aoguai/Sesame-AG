package io.github.aoguai.sesameag.hook

import io.github.aoguai.sesameag.util.Log
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicLong

internal sealed class RpcRequestOutcome {
    data class Success(val body: String) : RpcRequestOutcome()
    data class Failure(val reason: String) : RpcRequestOutcome()
}

internal class RpcLogLimiter(private val intervalMs: Long) {
    private val lastLogAtMs = AtomicLong(0)

    fun shouldLog(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastLogAtMs.get()
        return if (last == 0L || now - last >= intervalMs) {
            lastLogAtMs.set(now)
            true
        } else {
            false
        }
    }
}

internal class RpcInFlightRegistry {
    private val tasks = ConcurrentHashMap<String, FutureTask<RpcRequestOutcome>>()

    fun execute(
        tag: String,
        key: String,
        onCreate: () -> Unit,
        onJoin: () -> Unit,
        supplier: () -> RpcRequestOutcome
    ): RpcRequestOutcome {
        val task = FutureTask(Callable { supplier() })
        val existing = tasks.putIfAbsent(key, task)
        val toWait = existing ?: task
        if (existing == null) {
            onCreate()
            Log.runtime(tag, "in-flight create: key=$key")
            try {
                task.run()
            } finally {
                tasks.remove(key, task)
            }
        } else {
            onJoin()
            Log.runtime(tag, "in-flight join: key=$key")
        }

        return try {
            toWait.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            RpcRequestOutcome.Failure("Interrupted")
        } catch (_: ExecutionException) {
            RpcRequestOutcome.Failure("ExecutionException")
        } catch (_: Throwable) {
            RpcRequestOutcome.Failure("Throwable")
        }
    }

    fun size(): Int = tasks.size
}

internal object RpcRequestPolicy {
    fun shouldUseCache(method: String?): Boolean {
        val normalizedMethod = method?.lowercase() ?: return false
        val allow =
            normalizedMethod.contains("query") ||
                normalizedMethod.contains("list") ||
                normalizedMethod.contains("get")
        if (!allow) return false

        val deny =
            normalizedMethod.contains("send") ||
                normalizedMethod.contains("finish") ||
                normalizedMethod.contains("receive") ||
                normalizedMethod.contains("draw") ||
                normalizedMethod.contains("exchange") ||
                normalizedMethod.contains("apply") ||
                normalizedMethod.contains("submit") ||
                normalizedMethod.contains("sign") ||
                normalizedMethod.contains("use")
        return !deny
    }

    fun buildCacheKeyData(data: String?, relation: String?): String? {
        if (data == null) return null
        if (relation.isNullOrEmpty()) return data
        return data + "\u0001rel=" + relation
    }

    fun generateKey(method: String?, data: String?): String? {
        if (method.isNullOrBlank()) return null
        val dataHash = data?.hashCode() ?: 0
        return "${method}_${dataHash}"
    }
}

internal object RpcFallbackJsonFactory {
    fun build(reason: String, method: String?): String {
        val message = "$reason，请稍后再试"
        return try {
            JSONObject().apply {
                put("success", false)
                put("memo", message)
                put("resultDesc", message)
                put("desc", message)
                put("resultCode", "I07")
                if (!method.isNullOrBlank()) {
                    put("rpcMethod", method)
                }
            }.toString()
        } catch (_: Throwable) {
            """{"success":false,"memo":"$message","resultDesc":"$message","desc":"$message","resultCode":"I07"}"""
        }
    }
}

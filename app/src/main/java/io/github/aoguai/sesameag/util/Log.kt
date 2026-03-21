package io.github.aoguai.sesameag.util

import android.content.Context
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.model.BaseModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 日志工具类，负责初始化和管理各种类型的日志记录器，并提供日志输出方法。
 */
object Log {
    private const val MAX_DUPLICATE_ERRORS = 3 // 最多打印3次相同错误

    // 错误去重机制
    private val errorCountMap = ConcurrentHashMap<String, AtomicInteger>()

    private enum class Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private val loggerMap: Map<LogChannel, Logger>

    init {
        // 🔥 1. 立即初始化 Logcat，确保在任何 Context 到来之前控制台可用
        Logback.initLogcatOnly()

        // 2. 初始化 Logger 实例 (此时它们已经有了 Logcat 能力)
        loggerMap = LogCatalog.channels.associateWith { LoggerFactory.getLogger(it.loggerName) }
    }

    /**
     * 🔥 新增初始化方法
     * 在这里传入 Context，追加文件日志功能
     */
    @JvmStatic
    fun init(context: Context) {
        try {
            Logback.initFileLogging(context)
        } catch (e: Exception) {
            android.util.Log.e("SesameLog", "Log init failed", e)
        }
    }

    // --- 日志方法 ---

    private fun getLogger(channel: LogChannel): Logger {
        return loggerMap.getValue(channel)
    }

    private fun formatTaggedMessage(tag: String, msg: String): String = "[$tag]: $msg"

    private fun shouldWrite(channel: LogChannel): Boolean {
        return when (channel) {
            LogChannel.RECORD -> BaseModel.recordLog.value == true
            LogChannel.RUNTIME -> BaseModel.runtimeLog.value == true || BuildConfig.DEBUG
            else -> true
        }
    }

    private fun logRaw(channel: LogChannel, severity: Severity, msg: String) {
        if (!shouldWrite(channel)) {
            return
        }
        val logger = getLogger(channel)
        when (severity) {
            Severity.DEBUG -> logger.debug("{}", msg)
            Severity.INFO -> logger.info("{}", msg)
            Severity.WARN -> logger.warn("{}", msg)
            Severity.ERROR -> logger.error("{}", msg)
        }
    }

    private fun write(channel: LogChannel, severity: Severity, msg: String) {
        if (channel.mirrorToRecord) {
            logRaw(LogChannel.RECORD, Severity.INFO, msg)
        }
        logRaw(channel, severity, msg)
    }

    @JvmStatic
    fun system(msg: String) {
        write(LogChannel.SYSTEM, Severity.INFO, msg)
    }

    @JvmStatic
    fun system(tag: String, msg: String) {
        system("[$tag]: $msg")
    }

    @JvmStatic
    fun runtime(msg: String) {
        write(LogChannel.RUNTIME, Severity.INFO, msg)
    }

    @JvmStatic
    fun runtime(tag: String, msg: String) {
        runtime("[$tag]: $msg")
    }


    @JvmStatic
    fun record(msg: String) {
        write(LogChannel.RECORD, Severity.INFO, msg)
    }

    @JvmStatic
    fun record(tag: String, msg: String) {
        record("[$tag]: $msg")
    }

    @JvmStatic
    fun forest(msg: String) {
        write(LogChannel.FOREST, Severity.DEBUG, msg)
    }

    @JvmStatic
    fun forest(tag: String, msg: String) {
        forest("[$tag]: $msg")
    }

    @JvmStatic
    fun farm(msg: String) {
        write(LogChannel.FARM, Severity.DEBUG, msg)
    }

    @JvmStatic
    fun farm(tag: String, msg: String) {
        farm(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun other(msg: String) {
        write(LogChannel.OTHER, Severity.DEBUG, msg)
    }

    @JvmStatic
    fun other(tag: String, msg: String) {
        other("[$tag]: $msg")
    }

    @JvmStatic
    fun debug(msg: String) {
        write(LogChannel.DEBUG, Severity.DEBUG, msg)
    }

    @JvmStatic
    fun debug(tag: String, msg: String) {
        debug("[$tag]: $msg")
    }

    @JvmStatic
    fun error(msg: String) {
        write(LogChannel.ERROR, Severity.ERROR, msg)
    }

    @JvmStatic
    fun error(tag: String, msg: String) {
        error("[$tag]: $msg")
    }

    @JvmStatic
    fun capture(msg: String) {
        write(LogChannel.CAPTURE, Severity.INFO, msg)
    }

    @JvmStatic
    fun capture(tag: String, msg: String) {
        capture("[$tag]: $msg")
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        debug(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun i(tag: String, msg: String) {
        record(formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun w(tag: String, msg: String) {
        logRaw(LogChannel.RECORD, Severity.WARN, formatTaggedMessage(tag, msg))
    }

    @JvmStatic
    fun w(tag: String, msg: String, th: Throwable?) {
        val finalMsg = if (th == null) {
            formatTaggedMessage(tag, msg)
        } else {
            "${formatTaggedMessage(tag, msg)}\n${android.util.Log.getStackTraceString(th)}"
        }
        logRaw(LogChannel.RECORD, Severity.WARN, finalMsg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, th: Throwable? = null) {
        val finalMsg = if (th == null) {
            formatTaggedMessage(tag, msg)
        } else {
            "${formatTaggedMessage(tag, msg)}\n${android.util.Log.getStackTraceString(th)}"
        }
        write(LogChannel.ERROR, Severity.ERROR, finalMsg)
    }


    /**
     * 检查是否应该打印此错误（去重机制）
     */
    private fun shouldSkipDuplicateError(th: Throwable?): Boolean {
        if (th == null) return false

        // 提取错误特征
        var errorSignature = th.javaClass.simpleName + ":" +
                (th.message?.take(50) ?: "null")

        // 特殊处理：JSON解析空字符串错误
        if (th.message?.contains("End of input at character 0") == true) {
            errorSignature = "JSONException:EmptyResponse"
        }

        val count = errorCountMap.computeIfAbsent(errorSignature) { AtomicInteger(0) }
        val currentCount = count.incrementAndGet()

        // 如果是第3次，记录一个汇总信息
        if (currentCount == MAX_DUPLICATE_ERRORS) {
            record("⚠️ 错误【$errorSignature】已出现${currentCount}次，后续将不再打印详细堆栈")
            return false
        }

        // 超过最大次数后不再打印
        return currentCount > MAX_DUPLICATE_ERRORS
    }

    private fun buildStackTraceMessage(tag: String? = null, msg: String? = null, th: Throwable): String {
        val header = when {
            !tag.isNullOrBlank() && !msg.isNullOrBlank() -> "[$tag] $msg"
            !tag.isNullOrBlank() -> "[$tag] Throwable error"
            !msg.isNullOrBlank() -> msg
            else -> "Throwable error"
        }
        return "$header\n${android.util.Log.getStackTraceString(th)}"
    }

    private fun shouldTreatContextAsTag(context: String): Boolean {
        if (context.isBlank()) {
            return false
        }
        if (context.any { it.isWhitespace() || it > '\u007F' }) {
            return false
        }
        return context.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == '$' }
    }

    @JvmStatic
    fun printStackTrace(th: Throwable) {
        if (shouldSkipDuplicateError(th)) return
        error(buildStackTraceMessage(th = th))
    }

    @JvmStatic
    fun printStackTrace(context: String, th: Throwable) {
        if (shouldSkipDuplicateError(th)) return
        val message = if (shouldTreatContextAsTag(context)) {
            buildStackTraceMessage(tag = context, th = th)
        } else {
            buildStackTraceMessage(msg = context, th = th)
        }
        error(message)
    }

    @JvmStatic
    fun printStackTrace(tag: String, msg: String, th: Throwable) {
        if (shouldSkipDuplicateError(th)) return
        error(buildStackTraceMessage(tag = tag, msg = msg, th = th))
    }

    @JvmStatic
    fun printStack(tag: String) {
        val stackTrace = "stack: " + android.util.Log.getStackTraceString(Exception("获取当前堆栈$tag:"))
        record(stackTrace)
    }
}


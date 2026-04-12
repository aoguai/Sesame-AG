package io.github.aoguai.sesameag.util

import android.content.Context
import android.util.Log
import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import io.github.aoguai.sesameag.data.General
import org.slf4j.LoggerFactory
import java.io.File

object Logback {
    private var isFileInitialized = false

    /**
     * 阶段1：初始化 Logcat (保证控制台一定有日志)
     * 在 Log 类的 init 块中自动调用
     */
    fun initLogcatOnly() {
        try {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext
            lc.reset() // 清除之前的配置

            // 配置 Logcat Appender
            val encoder = PatternLayoutEncoder().apply {
                context = lc
                pattern = "[%thread] %logger{80} %msg%n" // 保持与 Java 版本一致
                start()
            }

            val logcatAppender = LogcatAppender().apply {
                context = lc
                this.encoder = encoder
                name = "LOGCAT"
                start()
            }

            // 为根 Logger 添加 Logcat 输出
            lc.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).apply {
                level = Level.DEBUG // 默认根级别
                addAppender(logcatAppender)
            }

        } catch (e: Exception) {
            Log.e("SesameLog", "Logback initLogcatOnly failed", e)
        }
    }

    /**
     * 阶段2：初始化文件日志 (有了 Context 之后调用)
     * 这是一个“追加”操作，不会打断 Logcat 日志
     */
    @Synchronized
    fun initFileLogging(context: Context) {
        if (isFileInitialized) return

        val logDir = resolveLogDir(context)
        val isMainProcess = context.packageName == General.PACKAGE_NAME

        try {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext

            // 为每个特定业务的 Logger 添加文件 Appender
            LogCatalog.loggerNames().forEach { logName ->
                addFileAppender(lc, logName, logDir, isMainProcess)
            }

            isFileInitialized = true
            Log.i("SesameLog", "File logging initialized at: $logDir (MainProcess: $isMainProcess)")
        } catch (e: Exception) {
            Log.e("SesameLog", "Logback initFileLogging failed", e)
        }
    }

    /**
     * 核心路径逻辑
     */
    private fun resolveLogDir(context: Context): String {
        var targetDir = Files.LOG_DIR

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        if (!targetDir.exists() || !targetDir.canWrite()) {
            val fallbackDir = context.getExternalFilesDir("logs")
            targetDir = fallbackDir ?: File(context.filesDir, "logs")
        }

        File(targetDir, "bak").mkdirs()

        return targetDir.absolutePath + File.separator
    }

    private fun addFileAppender(lc: LoggerContext, logName: String, logDir: String, isMainProcess: Boolean) {
        // 主进程使用带滚动策略的 Appender，非主进程使用普通追加 Appender
        val fileAppender = if (isMainProcess) RollingFileAppender<ILoggingEvent>() else FileAppender<ILoggingEvent>()

        fileAppender.apply {
            context = lc
            // 如果是 UI 进程，将其命名为内部 Appender，因为它会被 AsyncAppender 包装
            name = if (isMainProcess) "FILE-$logName" else "INTERNAL-FILE-$logName"
            file = "$logDir$logName.log"
            isAppend = true

            if (this is RollingFileAppender) {
                val rfa = this
                // 只有主进程配置滚动策略
                rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
                    context = lc
                    fileNamePattern = "${logDir}bak/$logName-%d{yyyy-MM-dd}.%i.log"
                    setMaxFileSize(FileSize.valueOf("7MB"))
                    setTotalSizeCap(FileSize.valueOf("32MB"))
                    maxHistory = 3
                    isCleanHistoryOnStart = true
                    setParent(rfa)
                    start()
                }
            } else {
                // 非主进程开启 Prudent 模式，确保能感知到主进程随时可能触发的滚动（按天或按大小）
                isPrudent = true
            }

            // 配置编码器
            encoder = PatternLayoutEncoder().apply {
                context = lc
                pattern = "%d{dd日 HH:mm:ss.SS} %msg%n"
                start()
            }

            start()
        }

        val finalAppender = if (!isMainProcess) {
            AsyncAppender().apply {
                context = lc
                name = "FILE-$logName"
                queueSize = 256
                discardingThreshold = 0
                addAppender(fileAppender)
                start()
            }
        } else {
            fileAppender
        }

        // 获取对应的 Logger 并添加 Appender
        lc.getLogger(logName).apply {
            level = Level.ALL
            isAdditive = true
            addAppender(finalAppender)
        }
    }
}

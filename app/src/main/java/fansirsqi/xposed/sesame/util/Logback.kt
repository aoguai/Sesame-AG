package fansirsqi.xposed.sesame.util

import android.content.Context
import android.util.Log
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import org.slf4j.LoggerFactory
import java.io.File

object Logback {
    private var isFileInitialized = false

    // 定义所有 Logger 的名称
    val LOG_NAMES = listOf(
        "runtime", "system", "record", "debug", "forest",
        "farm", "other", "error", "capture", "captcha"
    )

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
                // 默认先不设 Level，让它继承或默认 DEBUG/INFO，避免过滤掉重要信息
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

        // 🔥 修复点：恢复原有的路径判断逻辑
        val logDir = resolveLogDir(context)

        try {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext

            // 为每个特定业务的 Logger 添加文件 Appender
            LOG_NAMES.forEach { logName ->
                addFileAppender(lc, logName, logDir)
            }

            isFileInitialized = true
            Log.i("SesameLog", "File logging initialized at: $logDir")
        } catch (e: Exception) {
            Log.e("SesameLog", "Logback initFileLogging failed", e)
        }
    }

    /**
     * 核心路径逻辑：完全还原 Java 版本的判断
     * 优先 Files.LOG_DIR -> 失败则回退到 Context.external -> Context.files
     */
    private fun resolveLogDir(context: Context): String {
        // 1. 尝试使用 Files 类中定义的路径
        var targetDir = Files.LOG_DIR

        // 尝试创建目录，确保 exists() 判断准确
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // 2. 检查是否有权写入
        if (!targetDir.exists() || !targetDir.canWrite()) {
            // 回退逻辑
            val fallbackDir = context.getExternalFilesDir("logs")
            targetDir = fallbackDir ?: File(context.filesDir, "logs")
        }

        // 3. 确保目录结构完整 (创建 bak 子目录)
        File(targetDir, "bak").mkdirs()

        return targetDir.absolutePath + File.separator
    }

    private fun addFileAppender(lc: LoggerContext, logName: String, logDir: String) {
        // 1. 先创建实例，不要直接链式 apply，以便后面引用它
        val fileAppender = RollingFileAppender<ILoggingEvent>()

        fileAppender.apply {
            context = lc
            name = "FILE-$logName"
            file = "$logDir$logName.log"

            // 2. 配置 Policy (保持与 Java 版本参数一致)
            val policy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
                context = lc
                fileNamePattern = "${logDir}bak/$logName-%d{yyyy-MM-dd}.%i.log"
                setMaxFileSize(FileSize.valueOf("7MB")) // 还原为 50MB
                setTotalSizeCap(FileSize.valueOf("32MB"))
                maxHistory = 3
                isCleanHistoryOnStart = true // 还原 Java 中的 setCleanHistoryOnStart(true)
                // 必须调用 setParent
                setParent(fileAppender)
                start()
            }
            rollingPolicy = policy

            // 3. 配置 Encoder
            encoder = PatternLayoutEncoder().apply {
                context = lc
                pattern = "%d{dd日 HH:mm:ss.SS} %msg%n"
                start()
            }

            // 启动 Appender
            start()
        }

        // 4. 获取对应的 Logger 并添加 Appender
        lc.getLogger(logName).apply {
            // 这里可以不强制 setLevel，沿用默认配置
            isAdditive = true
            addAppender(fileAppender)
        }
    }
}
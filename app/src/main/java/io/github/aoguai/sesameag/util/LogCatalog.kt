package io.github.aoguai.sesameag.util

enum class LogChannel(
    val loggerName: String,
    val mirrorToRecord: Boolean = false,
    val visibleInViewer: Boolean = false
) {
    SYSTEM("system"),
    RUNTIME("runtime"),
    RECORD("record", visibleInViewer = true),
    DEBUG("debug"),
    FOREST("forest", mirrorToRecord = true, visibleInViewer = true),
    FARM("farm", mirrorToRecord = true, visibleInViewer = true),
    OTHER("other", mirrorToRecord = true, visibleInViewer = true),
    ERROR("error", mirrorToRecord = true, visibleInViewer = true),
    CAPTURE("capture", visibleInViewer = true),
    CAPTCHA("captcha");

    val fileName: String
        get() = "$loggerName.log"
}

object LogCatalog {
    val channels: List<LogChannel> = LogChannel.values().toList()

    @JvmStatic
    fun loggerNames(): List<String> = channels.map { it.loggerName }

    @JvmStatic
    fun fileName(loggerName: String): String = "${loggerName}.log"
}


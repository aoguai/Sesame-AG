package fansirsqi.xposed.sesame.ui.model

enum class UiMode(val value: String) {
    Web("web"),
    New("new");

    companion object {
        // 安全解析，默认为 Web
        fun fromValue(value: String?): UiMode = entries.find { it.value == value } ?: Web
    }
}
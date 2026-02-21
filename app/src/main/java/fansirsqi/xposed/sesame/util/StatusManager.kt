package fansirsqi.xposed.sesame.util

import java.io.File

data class ModuleRuntimeStatus(
    val framework: String, // "LSPatch", "LSPosed"
    val timestamp: Long,   // 写入时间，用于判断是否是旧数据
    val packageName: String // 来源包名，例如 com.eg.android.AlipayGphone
)

object StatusManager {
    private const val TAG = "StatusManager"
    private const val STATUS_FILE_NAME = "ModuleStatus.json"

    // 获取状态文件的位置 (与你的 Config 目录同级或在里面)
    private fun getStatusFile(): File {
        return File(Files.CONFIG_DIR.parentFile, STATUS_FILE_NAME)
    }

    /**
     * [Hook端调用] 写入当前状态
     */
    fun updateStatus(framework: String, packageName: String) {
        try {
            val status = ModuleRuntimeStatus(
                framework = framework,
                timestamp = System.currentTimeMillis(),
                packageName = packageName
            )
            val json = JsonHelper.toJson(status) // 转 JSON 字符串
            Files.write2File(json, getStatusFile())
            Log.d(TAG, "Status updated: $framework")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write status", e)
        }
    }

    /**
     * [UI端调用] 读取状态
     */
    fun readStatus(): ModuleRuntimeStatus? {
        try {
            val file = getStatusFile()
            if (!file.exists()) return null
            // if (System.currentTimeMillis() - file.lastModified() > 5 * 60 * 1000) return null
            val json = Files.readFromFile(file)
            // ❌ 错误写法 (Java 风格):
            // return JsonHelper.fromJson(json, ModuleRuntimeStatus::class.java)
            // ✅ 正确写法 (Kotlin reified 泛型):
            // 直接用尖括号 <类型> 指定，无需传 class 参数
            return JsonHelper.fromJson<ModuleRuntimeStatus>(json)
        } catch (e: Exception) {
            return null
        }
    }
}
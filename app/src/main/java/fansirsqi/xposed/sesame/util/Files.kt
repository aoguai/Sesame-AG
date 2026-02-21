@file:JvmName("Files") // 让 Java 调用时类名依然是 Files

package fansirsqi.xposed.sesame.util

import android.annotation.SuppressLint
import android.os.Environment
import fansirsqi.xposed.sesame.data.General
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

object Files {
    private val TAG = Files::class.java.simpleName

    const val CONFIG_DIR_NAME = "sesame-TK"

    @JvmField
    val MAIN_DIR: File = getMainDir()

    @JvmField
    val CONFIG_DIR: File = getConfigDir()

    @JvmField
    val LOG_DIR: File = getLogDir()

    /**
     * 确保指定的目录存在且不是一个文件。
     */
    @JvmStatic
    fun ensureDir(directory: File?) {
        try {
            if (directory == null) {
                android.util.Log.e(TAG, "Directory cannot be null")
                return
            }
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    android.util.Log.e(TAG, "Failed to create directory: ${directory.absolutePath}")
                }
            } else if (directory.isFile) {
                if (!directory.delete() || !directory.mkdirs()) {
                    android.util.Log.e(TAG, "Failed to replace file with directory: ${directory.absolutePath}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ensureDir error", e)
        }
    }

    private fun getMainDir(): File {
        val storageDirStr = Environment.getExternalStorageDirectory().toString() +
                File.separator + "Android" + File.separator + "media" + File.separator + General.PACKAGE_NAME
        val storageDir = File(storageDirStr)
        val mainDir = File(storageDir, CONFIG_DIR_NAME)
        ensureDir(mainDir)
        return mainDir
    }

    private fun getLogDir(): File {
        val logDir = File(MAIN_DIR, "log")
        ensureDir(logDir)
        return logDir
    }

    private fun getConfigDir(): File {
        val configDir = File(MAIN_DIR, "config")
        ensureDir(configDir)
        return configDir
    }

    @JvmStatic
    fun getUserConfigDir(userId: String): File {
        val configDir = File(CONFIG_DIR, userId)
        ensureDir(configDir)
        return configDir
    }

    @JvmStatic
    fun getDefaultConfigV2File(): File {
        return File(CONFIG_DIR, "config_v2.json")
    }

    @JvmStatic
    @Synchronized
    fun setDefaultConfigV2File(json: String): Boolean {
        return write2File(json, File(CONFIG_DIR, "config_v2.json"))
    }

    @JvmStatic
    @Synchronized
    fun getConfigV2File(userId: String): File {
        var confV2File = File(CONFIG_DIR.toString() + File.separator + userId, "config_v2.json")
        // 如果新配置文件不存在，则尝试从旧配置文件迁移
        if (!confV2File.exists()) {
            val oldFile = File(CONFIG_DIR, "config_v2-$userId.json")
            if (oldFile.exists()) {
                val content = readFromFile(oldFile)
                if (write2File(content, confV2File)) {
                    if (!oldFile.delete()) {
                        Log.error(TAG, "Failed to delete old config file: ${oldFile.absolutePath}")
                    }
                } else {
                    confV2File = oldFile
                    Log.error(TAG, "Failed to migrate config file for user: $userId")
                }
            }
        }
        return confV2File
    }

    @JvmStatic
    @Synchronized
    fun setConfigV2File(userId: String, json: String): Boolean {
        return write2File(json, File(CONFIG_DIR.toString() + File.separator + userId, "config_v2.json"))
    }

    @JvmStatic
    fun getCustomSetFile(userId: String): File? {
        return getTargetFileofUser(userId, "customset.json")
    }

    @JvmStatic
    @Synchronized
    fun getTargetFileofUser(userId: String?, fullTargetFileName: String): File? {
        if (userId.isNullOrEmpty()) {
            Log.error(TAG, "Invalid userId for target file: $fullTargetFileName")
            return null
        }
        val userDir = File(CONFIG_DIR, userId)
        ensureDir(userDir)
        val targetFile = File(userDir, fullTargetFileName)
        if (!targetFile.exists()) {
            try {
                targetFile.createNewFile()
            } catch (e: IOException) {
                Log.printStackTrace(TAG, "Failed to create file: ${targetFile.name}", e)
            }
        } else {
            val canWrite = targetFile.canWrite()
//            Log.record(TAG, "$fullTargetFileName permissions: r=$canRead; w=$canWrite")
            if (!canWrite) {
                if (targetFile.setWritable(true)) {
                    Log.record(TAG, "${targetFile.name} write permission set successfully")
                } else {
                    Log.record(TAG, "${targetFile.name} write permission set failed")
                }
            }
        }
        return targetFile
    }

    @JvmStatic
    @Synchronized
    fun getTargetFileofDir(dir: File, fullTargetFileName: String): File {
        ensureDir(dir)
        val targetFile = File(dir, fullTargetFileName)

        if (!targetFile.exists()) {
            try {
                targetFile.createNewFile()
            } catch (e: IOException) {
                Log.printStackTrace(TAG, "Failed to create file: ${targetFile.absolutePath}",e)
            }
        } else {
            val canWrite = targetFile.canWrite()
//            Log.record(TAG, "File permissions for ${targetFile.absolutePath}: r=$canRead; w=$canWrite")
            if (!canWrite) {
                if (targetFile.setWritable(true)) {
                    Log.record(TAG, "Write permission set successfully for file: ${targetFile.absolutePath}")
                } else {
                    Log.record(TAG, "Write permission set failed for file: ${targetFile.absolutePath}")
                }
            }
        }
        return targetFile
    }

    @JvmStatic
    fun getSelfIdFile(userId: String?): File? {
        return getTargetFileofUser(userId, "self.json")
    }

    @JvmStatic
    fun getFriendIdMapFile(userId: String?): File? {
        return getTargetFileofUser(userId, "friend.json")
    }

    @JvmStatic
    fun runtimeInfoFile(userId: String?): File? {
        return getTargetFileofUser(userId, "runtime.json")
    }

    @JvmStatic
    fun getStatusFile(userId: String?): File? {
        return getTargetFileofUser(userId, "status.json")
    }

    @JvmStatic
    fun getFriendWatchFile(userId: String): File? {
        return getTargetFileofUser(userId, "friendWatch.json")
    }

    @JvmStatic
    fun exportFile(file: File, hasTime: Boolean): File? {
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            CONFIG_DIR_NAME
        )
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Log.error(TAG, "Failed to create export directory: ${exportDir.absolutePath}")
            return null
        }

        val fileName = file.name
        val dotIndex = fileName.lastIndexOf('.')
        val fileNameWithoutExtension = if (dotIndex != -1) fileName.take(dotIndex) else fileName
        val fileExtension = if (dotIndex != -1) fileName.substring(dotIndex) else ""

        val newFileName = if (hasTime) {
            @SuppressLint("SimpleDateFormat")
            val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")
            val dateTimeString = simpleDateFormat.format(Date())
            "${fileNameWithoutExtension}_$dateTimeString$fileExtension"
        } else {
            "$fileNameWithoutExtension$fileExtension"
        }

        val exportFile = File(exportDir, newFileName)
        if (exportFile.exists() && exportFile.isDirectory) {
            if (!exportFile.delete()) {
                Log.error(TAG, "Failed to delete existing directory: ${exportFile.absolutePath}")
                return null
            }
        }
        if (!copy(file, exportFile)) {
            Log.error(TAG, "Failed to copy file: ${file.absolutePath} to ${exportFile.absolutePath}")
            return null
        }
        return exportFile
    }

    @JvmStatic
    fun getCityCodeFile(): File {
        val cityCodeFile = File(MAIN_DIR, "cityCode.json")
        if (cityCodeFile.exists() && cityCodeFile.isDirectory) {
            if (!cityCodeFile.delete()) {
                Log.error(TAG, "Failed to delete directory: ${cityCodeFile.absolutePath}")
            }
        }
        return cityCodeFile
    }

    private fun ensureLogFile(logFileName: String): File {
        val logFile = File(LOG_DIR, logFileName)
        if (logFile.exists() && logFile.isDirectory) {
            if (logFile.delete()) {
                Log.record(TAG, "日志${logFile.name}目录存在，删除成功！")
            } else {
                Log.error(TAG, "日志${logFile.name}目录存在，删除失败！")
            }
        }
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    Log.record(TAG, "日志${logFile.name}文件不存在，创建成功！")
                } else {
                    Log.error(TAG, "日志${logFile.name}文件不存在，创建失败！")
                }
            } catch (_: IOException) {
            }
        }
        return logFile
    }

    @JvmStatic
    fun getLogFile(logName: String): String {
        return "$logName.log"
    }

    @JvmStatic
    fun getRecordLogFile(): File = ensureLogFile(getLogFile("record"))

    @JvmStatic
    fun getDebugLogFile(): File = ensureLogFile(getLogFile("debug"))

    @JvmStatic
    fun getCaptureLogFile(): File = ensureLogFile(getLogFile("capture"))

    @JvmStatic
    fun getForestLogFile(): File = ensureLogFile(getLogFile("forest"))

    @JvmStatic
    fun getFarmLogFile(): File = ensureLogFile(getLogFile("farm"))

    @JvmStatic
    fun getOtherLogFile(): File = ensureLogFile(getLogFile("other"))

    @JvmStatic
    fun getErrorLogFile(): File = ensureLogFile(getLogFile("error"))

    @JvmStatic
    fun close(c: Closeable?) {
        try {
            c?.close()
        } catch (e: IOException) {
            Log.printStackTrace(TAG, e)
        }
    }

    @JvmStatic
    fun readFromFile(f: File): String {
        if (!f.exists()) return ""
        if (!f.canRead()) {
            ToastUtil.showToast("${f.name}没有读取权限！")
            return ""
        }
        // Kotlin 扩展方法：一行代码读取所有文本
        return try {
            f.readText()
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
            ""
        }
    }

    @JvmStatic
    fun beforWrite(f: File): Boolean {
        if (f.exists()) {
            if (!f.canWrite()) {
                ToastUtil.showToast("${f.absoluteFile}没有写入权限！")
                return true
            }
            if (f.isDirectory) {
                if (!f.delete()) {
                    ToastUtil.showToast("${f.absoluteFile}无法删除目录！")
                    return true
                }
            }
        } else {
            val parent = f.parentFile
            if (parent != null && !parent.mkdirs() && !parent.exists()) {
                ToastUtil.showToast("${f.absoluteFile}无法创建目录！")
                return true
            }
        }
        return false
    }

    @JvmStatic
    @Synchronized
    fun write2File(s: String, f: File): Boolean {
        if (beforWrite(f)) return false
        var fw: FileWriter? = null
        try {
            fw = FileWriter(f, false)
            fw.write(s)
            fw.flush()
            return true
        } catch (e: IOException) {
            Log.printStackTrace(TAG, e)
            return false
        } finally {
            if (fw != null) {
                try {
                    fw.close()
                } catch (e: IOException) {
                    Log.printStackTrace(TAG, "文件关闭异常（数据已写入）", e)
                }
            }
        }
    }

    @JvmStatic
    fun copy(source: File, dest: File): Boolean {
        // Kotlin 扩展方法，内部使用了 FileChannel 或 Files.copy
        return try {
            createFile(dest)?.let { target ->
                source.copyTo(target, overwrite = true)
                true
            } ?: false
        } catch (e: Exception) {
            Log.printStackTrace(e)
            false
        }
    }

    @JvmStatic
    fun streamTo(source: InputStream, dest: OutputStream): Boolean {
        return try {
            source.use { input ->
                dest.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: IOException) {
            Log.printStackTrace(e)
            false
        }
    }

    @JvmStatic
    fun createFile(file: File): File? {
        if (file.exists() && file.isDirectory) {
            if (!file.delete()) return null
        }
        if (!file.exists()) {
            try {
                file.parentFile?.mkdirs()
                if (!file.createNewFile()) return null
            } catch (e: Exception) {
                Log.printStackTrace(e)
                return null
            }
        }
        return file
    }

    @JvmStatic
    fun clearFile(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            // Kotlin 扩展方法
            file.writeText("")
            true
        } catch (e: Exception) {
            Log.printStackTrace(e)
            false
        }
    }

    @JvmStatic
    fun delFile(file: File): Boolean {
        if (!file.exists()) {
            ToastUtil.showToast("${file.absoluteFile}不存在！别勾把删了")
            Log.record(TAG, "delFile: ${file.absoluteFile}不存在！,无须删除")
            return false
        }

        // Kotlin 提供了 deleteRecursively() 扩展，但它不带重试机制
        // 为了保留你的重试逻辑，我们依然手动实现
        if (file.isFile) {
            return deleteFileWithRetry(file)
        }

        val files = file.listFiles() ?: return deleteFileWithRetry(file)

        var allSuccess = true
        for (innerFile in files) {
            if (!delFile(innerFile)) {
                allSuccess = false
            }
        }
        return allSuccess && deleteFileWithRetry(file)
    }

    private fun deleteFileWithRetry(file: File): Boolean {
        var retryCount = 3
        while (retryCount > 0) {
            if (file.delete()) {
                return true
            }
            retryCount--
            Log.record(TAG, "删除失败，重试中: ${file.absolutePath}")
            CoroutineUtils.sleepCompat(500)
        }
        Log.error(TAG, "删除失败: ${file.absolutePath}")
        return false
    }
}
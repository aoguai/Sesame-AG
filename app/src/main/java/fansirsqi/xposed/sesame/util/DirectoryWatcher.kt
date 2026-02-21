package fansirsqi.xposed.sesame.util

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

object DirectoryWatcher {

    fun observeDirectoryChanges(directory: File): Flow<Unit> = callbackFlow {
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM

        // 适配不同 Android 版本
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 File 构造函数
            object : FileObserver(directory, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) trySend(Unit)
                }
            }
        } else {
            // Android 9 及以下使用 String 构造函数
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) trySend(Unit)
                }
            }
        }

        observer.startWatching()

        awaitClose {
            observer.stopWatching()
        }
    }
}
package io.github.aoguai.sesameag.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.aoguai.sesameag.data.Config
import io.github.aoguai.sesameag.util.maps.CooperateMap
import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.UserMap
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for handling import and export operations.
 */
object PortUtil {

    @JvmStatic
    fun handleExport(context: Context, uri: Uri?, userId: String?) {
        if (uri == null) {
            ToastUtil.makeText("未选择目标位置", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val configV2File = if (userId.isNullOrEmpty()) {
                Files.getDefaultConfigV2File()
            } else {
                Files.getConfigV2File(userId!!)
            }

            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                ToastUtil.makeText("导出失败：无法打开输出流", Toast.LENGTH_SHORT).show()
                return
            }

            outputStream.use { output ->
                val inputStream = FileInputStream(configV2File)
                inputStream.use { input ->
                    if (Files.streamTo(input, output)) {
                        ToastUtil.makeText("导出成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        ToastUtil.makeText("导出失败！", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: IOException) {
            Log.printStackTrace(e)
            ToastUtil.makeText("导出失败：发生异常", Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun handleImport(context: Context, uri: Uri?, userId: String?) {
        if (uri == null) {
            ToastUtil.makeText("导入失败：未选择文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                ToastUtil.makeText("导入失败：无法打开输入流", Toast.LENGTH_SHORT).show()
                return
            }

            val configV2File = if (userId.isNullOrEmpty()) {
                Files.getDefaultConfigV2File()
            } else {
                Files.getConfigV2File(userId!!)
            }

            inputStream.use { input ->
                val outputStream = FileOutputStream(configV2File)
                outputStream.use { output ->
                    if (Files.streamTo(input, output)) {
                        ToastUtil.makeText("导入成功！", Toast.LENGTH_SHORT).show()
                        if (!userId.isNullOrEmpty()) {
                            try {
                                val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                                intent.putExtra("userId", userId)
                                intent.putExtra("configReload", true)
                                context.sendBroadcast(intent)
                            } catch (th: Throwable) {
                                Log.printStackTrace(th)
                            }
                        }

                        val activity = context as? Activity
                        if (activity != null) {
                            val intent = activity.intent
                            activity.finish()
                            activity.startActivity(intent)
                        }
                    } else {
                        ToastUtil.makeText("导入失败！", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: IOException) {
            Log.printStackTrace(e)
            ToastUtil.makeText("导入失败：发生异常", Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun save(context: Context, userId: String?) {
        try {
            if (Config.isModify(userId) && Config.save(userId, false)) {
                ToastUtil.showToastWithDelay("保存成功！", 100)
                if (!userId.isNullOrEmpty()) {
                    val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                    intent.putExtra("userId", userId)
                    intent.putExtra("configReload", true)
                    context.sendBroadcast(intent)
                }
            }
            if (!userId.isNullOrEmpty()) {
                UserMap.save(userId)
                IdMapManager.getInstance(CooperateMap::class.java).save(userId)
            }
        } catch (th: Throwable) {
            Log.printStackTrace(th)
        }
    }
}


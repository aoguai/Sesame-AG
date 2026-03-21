package io.github.aoguai.sesameag.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.aoguai.sesameag.model.BaseModel.Companion.showToast
import io.github.aoguai.sesameag.model.BaseModel.Companion.toastPerfix

object ToastUtil {
    private const val TAG = "ToastUtil"
    private var appContext: Context? = null

    fun init(context: Context?) {
        if (context != null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() {
            checkNotNull(appContext) { "ToastUtil is not initialized. Call ToastUtil.init(context) in Application." }
            return appContext!!
        }

    fun showToast(message: String?) {
        showToast(context, message)
    }

    fun showToast(context: Context?, message: String?) {
        // 1. 修复逻辑错误：处理前缀拼接
        var finalMessage = message
        val shouldShow = showToast.value == true
        val prefix = toastPerfix.value

      //  Log.record(TAG, "prefix::$prefix")

        // 修复：必须同时满足 "不为空" 且 "不等于字符串null"
        if (!prefix.isNullOrBlank() && prefix != "null") {
            finalMessage = "$prefix:$message"
        }

        Log.record(TAG, "showToast::$shouldShow::$finalMessage")

        if (shouldShow) {
            Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun makeText(context: Context?, message: String?, duration: Int): Toast {
        var finalMessage = message
        val prefix = toastPerfix.value

       // Log.record(TAG, "prefix::$prefix")

        // 修复逻辑
        if (!prefix.isNullOrBlank() && prefix != "null") {
            finalMessage = "$prefix:$message"
        }

        return Toast.makeText(context, finalMessage, duration)
    }

    fun makeText(message: String?, duration: Int): Toast {
        return makeText(context, message, duration)
    }

    fun showToastWithDelay(context: Context?, message: String?, delayMillis: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            makeText(context, message, Toast.LENGTH_SHORT).show()
        }, delayMillis.toLong())
    }

    fun showToastWithDelay(message: String?, delayMillis: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            makeText(message, Toast.LENGTH_SHORT).show()
        }, delayMillis.toLong())
    }
}


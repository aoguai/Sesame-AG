package fansirsqi.xposed.sesame.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import fansirsqi.xposed.sesame.model.BaseModel.Companion.showToast
import fansirsqi.xposed.sesame.model.BaseModel.Companion.toastOffsetY
import fansirsqi.xposed.sesame.model.BaseModel.Companion.toastPerfix

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
        val shouldShow = showToast.value
        val prefix = toastPerfix.value

      //  Log.record(TAG, "prefix::$prefix")

        // 修复：必须同时满足 "不为空" 且 "不等于字符串null"
        if (!prefix.isNullOrBlank() && prefix != "null") {
            finalMessage = "$prefix:$message"
        }

        Log.record(TAG, "showToast::$shouldShow::$finalMessage")

        if (shouldShow) {
            val toast = Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT)
            // 2. 修复系统错误：Android 11 (API 30) 及以上禁止对文本 Toast 设置 Gravity
            setToastGravity(toast)
            toast.show()
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

        val toast = Toast.makeText(context, finalMessage, duration)
        // 修复系统错误
        setToastGravity(toast)
        return toast
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

    // 抽离设置 Gravity 的逻辑
    fun setToastGravity(toast: Toast) {
        // Android 11 (API 30/R) 之后，makeText 创建的标准文本 Toast 禁止自定义位置
        // 尝试设置会被系统忽略并打印 Error 日志
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            try {
                toast.setGravity(toast.gravity, toast.xOffset, toastOffsetY.value)
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
            }
        }
    }
}
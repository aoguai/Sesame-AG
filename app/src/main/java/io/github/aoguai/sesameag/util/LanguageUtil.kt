package io.github.aoguai.sesameag.util

import android.content.Context
import android.content.res.Configuration
import io.github.aoguai.sesameag.model.BaseModel
import java.util.Locale

/**
 * 语言工具类，用于设置应用程序的语言环境。
 */
object LanguageUtil {

    /**
     * 设置应用程序的语言环境为简体中文。
     * 如果配置指定使用简体中文，则忽略系统语言设置，强制应用简体中文。
     *
     * @param context 应用程序上下文，用于访问资源和配置。
     */
    @JvmStatic
    fun setLocale(context: Context) {
        if (BaseModel.languageSimplifiedChinese.value == true) {
            val locale = Locale.Builder().setLanguage("zh").setRegion("CN").build()
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        }
    }
}


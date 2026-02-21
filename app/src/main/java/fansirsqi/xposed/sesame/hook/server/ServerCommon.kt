package fansirsqi.xposed.sesame.hook.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object ServerCommon {
    // 🚀 性能优化：全局单例，线程安全，避免每次请求都创建
    val jsonMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    const val MIME_JSON = "application/json"
    const val MIME_PLAINTEXT = "text/plain" // 补上这个
}
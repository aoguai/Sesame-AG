package fansirsqi.xposed.sesame.entity

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * RPC 调试项数据模型
 */
/**
 * RPC 调试项数据模型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RpcDebugEntity(
    // ✅ 使用 @field:JsonAlias 消除警告，同时支持多个别名

    @field:JsonAlias("Name")
    var name: String = "",

    // 🔥 关键：同时支持 "method" (默认), "methodName", "Method"
    @field:JsonAlias("methodName", "Method")
    var method: String = "",

    @field:JsonAlias("RequestData")
    var requestData: Any? = null,

    var id: String = "",

    @field:JsonAlias("Description", "desc", "Desc")
    var description: String = ""
) {
    companion object {
        private val objectMapper = ObjectMapper()
    }

    init {
        if (id.isEmpty()) {
            id = System.currentTimeMillis().toString()
        }
    }

    @JsonIgnore
    fun getDisplayName(): String {
        return name.ifEmpty { method }
    }

    @JsonIgnore
    fun getRequestDataString(): String {
        return when (requestData) {
            is String -> requestData as String
            is List<*> -> objectMapper.writeValueAsString(requestData)
            is Map<*, *> -> objectMapper.writeValueAsString(listOf(requestData))
            else -> "[{}]"
        }
    }
}
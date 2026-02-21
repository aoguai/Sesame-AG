package fansirsqi.xposed.sesame.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object JsonHelper {
    val mapper = jacksonObjectMapper()


    inline fun <reified T> fromJson(json: String): T {
        return mapper.readValue(json)
    }

    fun toJson(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}

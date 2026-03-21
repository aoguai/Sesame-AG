package io.github.aoguai.sesameag.task.AnswerAI

import io.github.aoguai.sesameag.util.JsonUtil.getValueByPath
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.github.aoguai.sesameag.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * @author Byseven
 * @date 2025/1/30
 * @apiNote
 */

/**
 * DeepSeek帮助类，用于与DeepSeek接口交互以获取AI回答
 * 支持单条文本问题及带有候选答案列表的问题请求
 */
class DeepSeek(apiKey: String?) : AnswerAIInterface {
    
    private var modelNameInternal: String = "deepseek-reasoner"
    private val apiKey: String = if (!apiKey.isNullOrEmpty()) apiKey else ""
    
    override fun getModelName(): String = modelNameInternal
    override fun setModelName(modelName: String) {
        this.modelNameInternal = modelName
    }

    private fun removeControlCharacters(text: String): String {
        return text.replace(Regex("\\p{Cntrl}&&[^\n\t]"), "")
    }

    @Throws(JSONException::class)
    private fun buildRequestJson(text: String): JSONObject {
        val cleanText = removeControlCharacters(text)
        val requestJson = JSONObject()
        requestJson.put("model", modelNameInternal)

        val messages = JSONArray()
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", SYSTEM_MESSAGE)
        messages.put(systemMessage)

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", cleanText)
        messages.put(userMessage)

        requestJson.put("messages", messages)
        requestJson.put("stream", false)
        return requestJson
    }

    @Throws(IOException::class)
    private fun sendRequest(requestJson: JSONObject): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(TIME_OUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(TIME_OUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(TIME_OUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()
        
        val mediaType = CONTENT_TYPE.toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(BASE_URL)
            .method("POST", body)
            .addHeader("Content-Type", CONTENT_TYPE)
            .addHeader("Authorization", AUTH_HEADER_PREFIX + apiKey)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body ?: return ""
            val json = responseBody.string()
            if (!response.isSuccessful) {
                Log.other("DeepSeek请求失败")
                Log.runtime(TAG, "DeepSeek接口异常：$json")
                return ""
            }
            return json
        }
    }

    override fun getAnswerStr(text: String, model: String): String {
        setModelName(model)
        return getAnswerStr(text)
    }

    override fun getAnswerStr(text: String): String {
        var result = ""
        try {
            val requestJson = buildRequestJson(text)
            val jsonResponse = sendRequest(requestJson)
            if (jsonResponse.isNotEmpty()) {
                val jsonObject = JSONObject(jsonResponse)
                result = getValueByPath(jsonObject, JSON_PATH)
            }
        } catch (e: IOException) {
            Log.printStackTrace(TAG, e)
        } catch (e: JSONException) {
            Log.printStackTrace(TAG, e)
        }
        return result
    }

    override fun getAnswer(title: String, answerList: List<String>): Int {
        val answerStr = StringBuilder()
        for (answer in answerList) {
            answerStr.append("[").append(answer).append("]")
        }
        val answerResult = getAnswerStr("$title\n$answerStr")
        if (answerResult.isNotEmpty()) {
            for (i in answerList.indices) {
                if (answerResult.contains(answerList[i])) {
                    return i
                }
            }
        }
        return -1
    }

    companion object {
        private val TAG = DeepSeek::class.java.simpleName
        private const val BASE_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val CONTENT_TYPE = "application/json"
        private const val JSON_PATH = "choices.[0].message.content"
        private const val SYSTEM_MESSAGE = "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。"
        private const val AUTH_HEADER_PREFIX = "Bearer "
        private const val TIME_OUT_SECONDS = 180
    }
}


package io.github.aoguai.sesameag.task.AnswerAI

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class CustomService(apiKey: String?, baseUrl: String?) : AnswerAIInterface {
    
    private val apiKey: String = apiKey.orEmpty().trim()
    private val baseUrl: String = baseUrl.orEmpty().trim().removeSuffix("/").ifEmpty { "https://api.openai.com/v1" }
    private var modelNameInternal: String = "gpt-3.5-turbo"
    
    override fun getModelName(): String = modelNameInternal
    override fun setModelName(modelName: String) {
        this.modelNameInternal = modelName.trim().ifEmpty { "gpt-3.5-turbo" }
    }

    @Throws(JSONException::class)
    private fun buildRequestJson(text: String): JSONObject {
        val requestJson = JSONObject()
        requestJson.put("model", modelNameInternal)

        val messages = JSONArray()
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", SYSTEM_MESSAGE)
        messages.put(systemMessage)

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", text)
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

        val url = "${baseUrl.removeSuffix("/")}/chat/completions"
        val mediaType = CONTENT_TYPE.toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .method("POST", body)
            .addHeader("Content-Type", CONTENT_TYPE)
            .addHeader("Authorization", AUTH_HEADER_PREFIX + apiKey)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body ?: return ""
            val json = responseBody.string()
            if (!response.isSuccessful) {
                Log.other("CustomService请求失败")
                Log.runtime(TAG, "CustomService接口异常：$json")
                return ""
            }
            return json
        }
    }

    override fun getAnswerStr(text: String): String {
        var result = ""
        try {
            val requestJson = buildRequestJson(text)
            val jsonResponse = sendRequest(requestJson)
            if (jsonResponse.isNotEmpty()) {
                val jsonObject = JSONObject(jsonResponse)
                result = JsonUtil.getValueByPath(jsonObject, JSON_PATH)
            }
        } catch (e: IOException) {
            Log.printStackTrace(TAG, e)
        } catch (e: JSONException) {
            Log.printStackTrace(TAG, e)
        }
        return result
    }

    override fun getAnswerStr(text: String, model: String): String {
        setModelName(model)
        return getAnswerStr(text)
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
        private val TAG = CustomService::class.java.simpleName
        private const val CONTENT_TYPE = "application/json"
        private const val JSON_PATH = "choices.[0].message.content"
        private const val SYSTEM_MESSAGE = "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。"
        private const val AUTH_HEADER_PREFIX = "Bearer "
        private const val TIME_OUT_SECONDS = 180
    }
}


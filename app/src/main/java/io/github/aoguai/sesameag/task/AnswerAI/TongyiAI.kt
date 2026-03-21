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

/**
 * @author Byseven
 * @date 2025/1/30
 * @apiNote
 */
class TongyiAI(token: String?) : AnswerAIInterface {
    
    private val token: String = if (!token.isNullOrEmpty()) token else ""
    private var modelNameInternal: String = "qwen-turbo"
    
    override fun getModelName(): String = modelNameInternal
    override fun setModelName(modelName: String) {
        this.modelNameInternal = modelName
    }

    override fun getAnswerStr(text: String): String {
        var result = ""
        var response: Response? = null
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val contentObject = JSONObject()
            contentObject.put("role", "user")
            contentObject.put("content", text)
            
            val messageArray = JSONArray()
            messageArray.put(contentObject)
            
            val bodyObject = JSONObject()
            bodyObject.put("model", modelNameInternal)
            bodyObject.put("messages", messageArray)
            
            val body = bodyObject.toString().toRequestBody(CONTENT_TYPE.toMediaType())
            val request = Request.Builder()
                .url(URL)
                .method("POST", body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", CONTENT_TYPE)
                .build()
            
            response = client.newCall(request).execute()
            val responseBody = response.body ?: return result
            val json = responseBody.string()
            if (!response.isSuccessful) {
                Log.other("Tongyi请求失败")
                Log.record(TAG, "Tongyi接口异常：$json")
                return result
            }
            val jsonObject = JSONObject(json)
            result = JsonUtil.getValueByPath(jsonObject, JSON_PATH)
        } catch (e: JSONException) {
            Log.printStackTrace(TAG, e)
        } catch (e: IOException) {
            Log.printStackTrace(TAG, e)
        } finally {
            response?.close()
        }
        return result
    }

    override fun getAnswerStr(text: String, model: String): String {
        setModelName(model)
        return getAnswerStr(text)
    }

    override fun getAnswer(title: String, answerList: List<String>): Int {
        val size = answerList.size
        val answerStr = StringBuilder()
        for (i in 0 until size) {
            answerStr.append(i + 1).append(".[").append(answerList[i]).append("]\n")
        }
        val answerResult = getAnswerStr("问题：$title\n\n答案列表：\n\n$answerStr\n\n请只返回答案列表中的序号")
        if (answerResult.isNotEmpty()) {
            try {
                val index = answerResult.toInt() - 1
                if (index in 0 until size) {
                    return index
                }
            } catch (e: Exception) {
                Log.record(TAG, "AI🧠回答，返回数据：$answerResult")
            }
            for (i in 0 until size) {
                if (answerResult.contains(answerList[i])) {
                    return i
                }
            }
        }
        return -1
    }

    companion object {
        private val TAG = TongyiAI::class.java.simpleName
        private const val URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        private const val CONTENT_TYPE = "application/json"
        private const val JSON_PATH = "choices.[0].message.content"
    }
}


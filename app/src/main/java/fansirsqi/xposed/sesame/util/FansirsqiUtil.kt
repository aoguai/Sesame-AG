package fansirsqi.xposed.sesame.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object FansirsqiUtil {
    // 定义一言API的URL

    private const val HIROHITO_API_URL1 = "https://international.v1.hitokoto.cn/"
    private const val HIROHITO_API_URL2 = "https://v1.hitokoto.cn/"

    /**
     * 从指定 URL 获取一言
     */
    private fun fetchHitokotoFromUrl(url: String): Pair<String, String>? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            val jsonObject = JSONObject(response)
            val hitokoto = jsonObject.optString("hitokoto", "")
            val from = jsonObject.optString("from", "")
            
            if (hitokoto.isNotEmpty()) Pair(hitokoto, from) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取一言（挂起函数），推荐在协程中使用
     * @return 成功返回句子，失败返回默认句子
     */
    suspend fun getOneWord(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // 先尝试国内版 API，失败则尝试国际版 API
            val result = fetchHitokotoFromUrl(HIROHITO_API_URL2) ?: fetchHitokotoFromUrl(HIROHITO_API_URL1)
            
            result?.let { (hitokoto, from) ->
                "$hitokoto\n\n                    -----Re: $from"
            } ?: " 去年相送，余杭门外，飞雪似杨花。\n今年春尽，杨花似雪，犹不见还家。\n\n                    -----Re: 少年游·润州作代人寄远 苏轼"
        } catch (e: Exception) {
            Log.printStackTrace(e)
            " 去年相送，余杭门外，飞雪似杨花。\n今年春尽，杨花似雪，犹不见还家。\n\n                    -----Re: 少年游·润州作代人寄远 苏轼"
        }
    }

    /**
     * 生成随机字符串
     * @param length 字符串长度
     */
    fun getRandomString(length: Int): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }


    //获取目标文件夹下的文件夹列表
    fun getFolderList(folderPath: String): List<String> {
        val file = File(folderPath)
        return if (file.exists() && file.isDirectory) {
            file.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        } else {
            emptyList()
        }
    }
}

package io.github.aoguai.sesameag.util

import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.hook.internal.AlipayMiniMarkHelper
import io.github.aoguai.sesameag.hook.internal.AuthCodeHelper
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

enum class GameTask(
    val title: String,
    val appId: String,
    val gid: String,
    val action: String,
    val channel: String,
    val version: String,
    val requestsPerEgg: Int //完成1个🥚要多少次 为了防止网络崩溃 多加1次
) {
    Orchard_ncscc("农场乐园:农场上车车", "2060170000356601", "zfb_ncscc", "ncscc_game_kaiche_every_10", "nongchangleyuan", "1.0.2", 2),
    Farm_ddply("庄园乐园:对对碰乐园", "2021004149679303", "zfb_ddply", "ddply_game_xiaochu_every_5", "zhuangyuan", "1.0.14", 4),

    Forest_slxcc("森林乐园:森林小车车","2060170000363691","zfb_slxcc","slxcc_game_kaiche_every_10","lianyun_senlin_leyuan","1.0.1",3),
    Forest_sljyd("森林乐园:森林救援队(能量雨)", "2021005113684028", "zfb_sljydx", "sljyd_game_xiaochu_every_10", "lianyun_senlin_leyuan", "1.0.1", 3);

    private var cachedToken: String? = null

    private fun logTask(msg: String) {
        when (this) {
            Orchard_ncscc -> Log.orchard(title, msg)
            Farm_ddply -> Log.farm(title, msg)
            Forest_slxcc, Forest_sljyd -> Log.forest(title, msg)
        }
    }

    /**
     * 第一步：登录获取 Token 并缓存
     */
    private fun login(): String? {
        return try {
            val authCode = AuthCodeHelper.getAuthCode(appId)
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            val reqId = "${System.currentTimeMillis()}_${(1..350).random()}"

            val body = JSONObject().apply {
                put("v", version); put("code", authCode); put("pf", "zfb")
                put("reqId", reqId); put("gid", gid); put("version", version)
            }.toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/game/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 💡 改进：登录失败也要读错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "EMPTY"

            val resJson = JSONObject(responseText)
            if (resJson.optInt("code") == 1) {
                val token = resJson.optJSONObject("data")?.optString("token")
                //Log.record(title, "✅ 登录成功，Token 已获取")
                token
            } else {
               // Log.record(title, "❌ 登录接口报错 (Code $respCode): $responseText")
                null
            }
        } catch (_: Exception) {
            //Log.record(title, "🚨 登录过程抛出异常: ${e.message}")
            null
        }
    }

    /**
     * 外部调用：执行上报任务 (改为协程版本)
     * @return Boolean 是否达到完成任务所需的最小成功次数
     */
    suspend fun report(eggCount: Int): Boolean {
        val requiredSuccesses = eggCount * requestsPerEgg
        val totalNeeded = (eggCount * requestsPerEgg) + 1 //正常不需要加1，多1次确保网络请求不会错误

        cachedToken = login()
        if (cachedToken.isNullOrEmpty()) {
            logTask("⚠️ 无法获取有效的 Token，放弃上报任务")
            return false
        }

        var successCount = 0
        //Log.record(title, "🚀 开始执行任务：目标 $eggCount 个蛋，需请求 $totalNeeded 次")
        for (i in 1..totalNeeded) {
            // 执行单次上报
            if (executeSingleReport(i, totalNeeded)) {
                successCount++
            } else {
                // 如果第一次就失败，或者中途由于 Token 失效等原因失败
                break
            }
            if (i < totalNeeded) delay((1000..3000).random().toLong())
        }
        //Log.record(title, "🏁 任务流程运行结束")
        return successCount >= requiredSuccesses
    }

    private fun executeSingleReport(current: Int, total: Int): Boolean {
        return try {
            val mark = AlipayMiniMarkHelper.getAlipayMiniMark(appId, version)
            val body = JSONObject().apply {
                put("v", version); put("version", version)
                put("reqId", "${System.currentTimeMillis()}_${(10..99).random()}")
                put("gid", gid); put("action_code", action); put("action_finish_channel", channel)
            }.toString()

            val conn = (URL("https://gamesapi2.aslk2018.com/v2/zfb/taskReport").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("authorization", cachedToken)
                setRequestProperty("alipayMiniMark", mark)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", getDynamicUA())
                setRequestProperty("x-release-type", "ONLINE")
                setRequestProperty("referer", "https://$appId.hybrid.alipay-eco.com/$appId/$version/index.html")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            // 💡 重点改进：读取响应码并捕获错误流
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "NULL_RESPONSE"

            val resJson = JSONObject(responseText)
            if (resJson.optInt("code") == 1) {
                if (current % requestsPerEgg == 0) {
                    logTask("📈 进度: $current/${total - 1} (已达成 ${current / requestsPerEgg} 个蛋)")
                }
                true
            } else {
                // 💡 修正：这里会直接打印出服务器返回的完整错误 JSON，比如 {"code":0,"msg":"token invalid"...}
                //Log.error(title, "⚠️ 第 $current 次上报业务失败 (HTTP $respCode): $responseText")
                false
            }
        } catch (_: Exception) {
           // Log.e(title, "🚨 第 $current 次请求发生网络崩溃:",e)
            false
        }
    }

    private fun getDynamicUA(): String {
        val systemUa = System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android 11)"
        val alipayVer = ApplicationHook.alipayVersion
        return "$systemUa NebulaSDK/1.8.100112 Nebula AliApp(AP/$alipayVer) AlipayClient/$alipayVer"
    }
}

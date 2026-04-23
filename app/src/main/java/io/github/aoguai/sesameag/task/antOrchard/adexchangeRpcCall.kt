package io.github.aoguai.sesameag.task.antOrchard

import io.github.aoguai.sesameag.hook.RequestManager
import io.github.aoguai.sesameag.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object XLightRpcCall {
    private const val TAG = "XLightRpcCall"

    // 固定 SDK 信息
    private const val AD_COMPONENT_TYPE = "FEEDS"
    private const val AD_COMPONENT_VERSION = "4.30.21"
    private const val ENABLE_FUSION = true
    private const val NETWORK_TYPE = "WWAN"
    private const val PAGE_NO = 2
    private const val UNION_APP_ID = "2060090000304921"
    private const val XLIGHT_RUNTIME_SDK_VERSION = "4.30.21"
    private const val XLIGHT_SDK_TYPE = "h5"
    private const val XLIGHT_SDK_VERSION = "4.30.21"

    /**
     * 调用 xlightPlugin
     * @param referToken referToken 字符串
     * @param pageUrl 当前页面 url
     * @param pageFrom 页面来源
     */
    fun xlightPlugin(
        pageUrl: String,
        pageFrom: String,
        session: String,
        spaceCode: String,
        referToken: String? = null,
        searchInfo: JSONObject? = null,
        playingPageInfo: String? = null,
        positionExtMap: JSONObject? = null,
        pageNo: Int = PAGE_NO,
        networkType: String = NETWORK_TYPE
    ): String {
        return try {

            // positionRequest
            val positionRequest = JSONObject().apply {
                put("extMap", positionExtMap ?: JSONObject())
                put("referInfo", JSONObject().apply {
                    if (!referToken.isNullOrBlank()) {
                        put("referToken", referToken)
                    }
                })
                put("searchInfo", searchInfo ?: JSONObject())
                put("spaceCode", spaceCode)
            }

            // sdkPageInfo
            val sdkPageInfo = JSONObject().apply {
                put("adComponentType", AD_COMPONENT_TYPE)
                put("adComponentVersion", AD_COMPONENT_VERSION)
                put("enableFusion", ENABLE_FUSION)
                put("networkType", if (networkType.isBlank()) NETWORK_TYPE else networkType)
                put("pageFrom", pageFrom)
                put("pageNo", if (pageNo > 0) pageNo else PAGE_NO)
                put("pageUrl", pageUrl)
                if (!playingPageInfo.isNullOrBlank()) {
                    put("playingPageInfo", playingPageInfo)
                }
                put("session", session)
                put("unionAppId", UNION_APP_ID)
                put("usePlayLink", "true")
                put("xlightRuntimeSDKversion", XLIGHT_RUNTIME_SDK_VERSION)
                put("xlightSDKType", XLIGHT_SDK_TYPE)
                put("xlightSDKVersion", XLIGHT_SDK_VERSION)
            }

            // 数组包装
            val args = JSONArray().apply {
                put(JSONObject().apply {
                    put("positionRequest", positionRequest)
                    put("sdkPageInfo", sdkPageInfo)
                })
            }

            // RPC 调用
            RequestManager.requestString(
                "com.alipay.adexchange.ad.facade.xlightPlugin",
                args.toString()
            )

        } catch (e: Exception) {
            Log.printStackTrace(TAG, "xlightPlugin failed", e)
            ""
        }
    }

    /**
     * 完成广告任务（新版，支持 extendInfo）
     * @param playBizId 广告任务业务 ID
     * @param playEventInfo 完整的 playEventInfo JSON
     * @param iepTaskSceneCode extendInfo.iepTaskSceneCode
     * @param iepTaskType extendInfo.iepTaskType
     */
    fun finishTask(
        playBizId: String,
        playEventInfo: JSONObject,
        iepTaskSceneCode: String? = null,
        iepTaskType: String? = null
    ): String {
        return try {

            // extendInfo
            val extendInfo = JSONObject().apply {
                if (!iepTaskSceneCode.isNullOrBlank()) {
                    put("iepTaskSceneCode", iepTaskSceneCode)
                }
                if (!iepTaskType.isNullOrBlank()) {
                    put("iepTaskType", iepTaskType)
                }
            }

            // 单条任务对象
            val args = JSONObject().apply {
                put("extendInfo", extendInfo)
                put("playBizId", playBizId)
                put("playEventInfo", playEventInfo)
                put("source", "adx")   // 固定
            }

            // 最外层数组
            val argsArray = JSONArray().apply {
                put(args)
            }

            RequestManager.requestString(
                "com.alipay.adtask.biz.mobilegw.service.interaction.finish",
                argsArray.toString()
            )

        } catch (e: Exception) {
            Log.printStackTrace(TAG, "finishTask failed", e)
            ""
        }
    }

}

object UrlUtil {
    private const val TAG = "UrlUtil"
    private val QUERY_PARAM_BOUNDARY = listOf(
        "canPullDown=",
        "showOptionMenu=",
        "iepTaskType=",
        "iepTaskSceneCode=",
        "canDoTask=",
        "awardCount=",
        "doneTimes=",
        "taskDoneTimes=",
        "xlightFrom="
    )

    /**
     * 从原始URL中提取指定参数的完整值(支持多层嵌套)
     * @param url 原始URL
     * @param key 要提取的参数名
     * @return 完整的参数值(已解码)
     */
    fun getParamValue(url: String, key: String): String? {
        if (url.isEmpty()) return null

        try {
            val carrier = findQueryCarrier(url, key) ?: return null

            // 找到查询字符串部分
            val queryStart = carrier.indexOf("?")
            if (queryStart == -1) return null

            val query = carrier.substring(queryStart + 1)

            // 使用正则表达式精确匹配参数,避免截断
            val pattern = Regex("(?:^|&)" + Regex.escape(key) + "=([^&]*)")
            val match = pattern.find(query)

            return match?.groupValues?.get(1)?.let { decode(it) }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "getParamValue failed", e)
            return null
        }
    }

    /**
     * 提取URL中指定参数后面的完整嵌套URL
     * @param url 原始URL
     * @param key 触发参数名(比如"url")
     * @return 完整的嵌套URL(已解码)
     */
    fun getFullNestedUrl(url: String, key: String): String? {
        if (url.isEmpty()) return null

        try {
            val carrier = findTextContaining(url, "$key=") ?: return null

            // 查找key=的位置
            val searchKey = "$key="
            val keyIndex = carrier.indexOf(searchKey)
            if (keyIndex == -1) return null

            // 从key=之后开始提取
            val startIndex = keyIndex + searchKey.length
            val remaining = carrier.substring(startIndex)

            // 找到参数值的结束位置
            var endIndex = remaining.length

            // 如果是URL类型,需要找到完整URL的边界
            if (remaining.startsWith("http://") || remaining.startsWith("https://") || remaining.startsWith("alipays://")) {
                // 查找下一个顶层&符号(在URL外部的&)
                // 策略: 检测到&后,判断其后是否跟着已知的顶层参数名
                for (i in remaining.indices) {
                    if (remaining[i] == '&') {
                        // 检查&后面是否是顶层参数
                        val afterAmp = remaining.substring(i + 1)
                        if (QUERY_PARAM_BOUNDARY.any { afterAmp.startsWith(it) }) {
                            endIndex = i
                            break
                        }
                    }
                }
            } else {
                // 非URL类型,找第一个&
                val ampIndex = remaining.indexOf("&")
                if (ampIndex != -1) {
                    endIndex = ampIndex
                }
            }

            val value = remaining.substring(0, endIndex)

            var result = value
            repeat(5) {
                if (result.startsWith("http://") || result.startsWith("https://") || result.startsWith("alipays://")) {
                    return result
                }
                val temp = decode(result)
                if (temp == result) {
                    return result
                }
                result = temp
            }

            return result
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "getFullNestedUrl failed", e)
            return null
        }
    }

    /**
     * 从完整URL中提取指定参数
     * @param fullUrl 完整的URL
     * @param key 参数名
     * @return 参数值
     */
    fun extractParamFromUrl(fullUrl: String, key: String): String? {
        if (fullUrl.isEmpty()) return null

        try {
            val carrier = findQueryCarrier(fullUrl, key) ?: return null
            val queryStart = carrier.indexOf("?")
            if (queryStart == -1) return null

            val query = carrier.substring(queryStart + 1)

            // 使用正则精确匹配
            val pattern = Regex("(?:^|&)" + Regex.escape(key) + "=([^&]*)")
            val match = pattern.find(query)

            return match?.groupValues?.get(1)?.let { decode(it) }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "extractParamFromUrl failed", e)
            return null
        }
    }

    /**
     * 安全解码URL
     */
    fun decode(url: String): String {
        return try {
            URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
    }

    /**
     * 批量解码直到稳定
     */
    fun decodeUntilStable(url: String): String {
        var current = url
        var last: String
        var count = 0
        do {
            last = current
            current = decode(current)
            count++
        } while (current != last && count < 10)
        return current
    }

    private fun findQueryCarrier(url: String, key: String): String? {
        var current = url
        repeat(5) {
            if (hasQueryParam(current, key)) {
                return current
            }
            val temp = decode(current)
            if (temp == current) {
                return null
            }
            current = temp
        }
        return if (hasQueryParam(current, key)) current else null
    }

    private fun hasQueryParam(url: String, key: String): Boolean {
        val queryStart = url.indexOf("?")
        if (queryStart == -1) return false
        val query = url.substring(queryStart + 1)
        return Regex("(?:^|&)" + Regex.escape(key) + "=").containsMatchIn(query)
    }

    private fun findTextContaining(text: String, target: String): String? {
        var current = text
        repeat(5) {
            if (current.contains(target)) {
                return current
            }
            val temp = decode(current)
            if (temp == current) {
                return null
            }
            current = temp
        }
        return if (current.contains(target)) current else null
    }
}


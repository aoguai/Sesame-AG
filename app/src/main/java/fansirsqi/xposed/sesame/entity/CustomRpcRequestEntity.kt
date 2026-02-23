package fansirsqi.xposed.sesame.entity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.io.File
import java.security.MessageDigest

/**
 * 自定义 RPC 配置项（用于“配置文件 + 定时执行”功能）
 *
 * 支持在账号配置目录放置文件：
 * - `config/<uid>/rpcRequest.json`
 * - 兼容历史拼写：`config/<uid>/rpcResquest.json`
 *
 * 文件内容支持两种格式：
 *
 * 1) 数组格式（推荐）
 * ```json
 * [
 *   {
 *     "name": "查询森林使用道具(示例)",
 *     "methodName": "alipay.antforest.forest.h5.queryMiscInfo",
 *     "requestData": [{"queryBizType":"usingProp","source":"SELF_HOME","version":"20240201"}]
 *   }
 * ]
 * ```
 *
 * 2) Map 格式（兼容 Sesame-GR 的 rpcRequestMap 思路）
 * ```json
 * {
 *   "{\"methodName\":\"xxx\",\"requestData\":[{}]}": "示例名称"
 * }
 * ```
 */
class CustomRpcRequestEntity : MapperEntity() {

    var methodName: String = ""

    /**
     * RPC requestData 的“字符串形态”，通常应为 JSON 数组字符串，例如：`[{"a":1}]`
     */
    var requestData: String = ""

    /**
     * 是否启用“定时执行”
     */
    var scheduleEnabled: Boolean = false

    /**
     * 每日执行次数（仅在 scheduleEnabled=true 时生效）
     */
    var dailyCount: Int = 0

    companion object {
        private const val TAG = "CustomRpcRequestEntity"

        private val mapper: ObjectMapper = JsonUtil.copyMapper()

        @JvmStatic
        fun getList(): List<CustomRpcRequestEntity> {
            val uid = UserMap.currentUid
            if (uid.isNullOrBlank()) return emptyList()
            return loadListForUid(uid)
        }

        /**
         * 供 UI/工具类按指定账号读取（例如：RPC 调试界面“同步/导入”）。
         */
        @JvmStatic
        fun getListForUid(uid: String): List<CustomRpcRequestEntity> {
            if (uid.isBlank()) return emptyList()
            return loadListForUid(uid)
        }

        @JvmStatic
        fun getMap(): Map<String, CustomRpcRequestEntity> {
            val list = getList()
            if (list.isEmpty()) return emptyMap()
            return list.associateBy { it.id }
        }

        private fun loadListForUid(uid: String): List<CustomRpcRequestEntity> {
            val text = readConfigText(uid)
            if (text.isBlank()) return emptyList()

            val root: JsonNode = try {
                mapper.readTree(text)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "解析 rpcRequest.json 失败", t)
                return emptyList()
            }

            val items = when {
                root.isArray -> root.toList()
                root.isObject -> listOf(root)
                else -> emptyList()
            }

            val result = ArrayList<CustomRpcRequestEntity>()
            for (node in items) {
                try {
                    if (node.isObject) {
                        // Map 兼容：{ "<json>": "name", ... }
                        // 只有当它“像 map”时才走这条分支：字段值都是文本
                        val fields = node.properties().asSequence().toList()
                        val looksLikeMap = fields.isNotEmpty() && fields.all { it.value.isTextual }
                        if (looksLikeMap) {
                            for ((key, valueNode) in fields) {
                                val displayName = valueNode.asText("").trim()
                                val parsed = parseKeyAsRequest(key, displayName)
                                if (parsed != null) result.add(parsed)
                            }
                        } else {
                            val parsed = parseNodeAsRequest(node)
                            if (parsed != null) result.add(parsed)
                        }
                    }
                } catch (t: Throwable) {
                    Log.printStackTrace(TAG, "解析 RPC 条目失败", t)
                }
            }

            return result.sortedWith { a, b -> a.compareTo(b) }
        }

        private fun readConfigText(uid: String): String {
            // 统一数据源：优先读取主目录（RPC 调试工具也会写入这个文件）
            val rootFile = File(Files.MAIN_DIR, "rpcRequest.json")
            if (rootFile.exists()) {
                val rootText = Files.readFromFile(rootFile).trim()
                if (rootText.isNotBlank()) return rootText
            }

            val userDir = Files.getUserConfigDir(uid)

            val newFile = File(userDir, "rpcRequest.json")
            if (!newFile.exists()) {
                // 仅创建新文件（避免用户找不到路径），不写入默认内容
                Files.createFile(newFile)
            }
            val newText = Files.readFromFile(newFile).trim()
            if (newText.isNotBlank()) return newText

            // 兼容历史拼写（不强制创建）
            val oldFile = File(userDir, "rpcResquest.json")
            if (oldFile.exists()) {
                val oldText = Files.readFromFile(oldFile).trim()
                if (oldText.isNotBlank()) return oldText
            }

            return ""
        }

        private fun parseKeyAsRequest(keyJson: String, displayName: String): CustomRpcRequestEntity? {
            // keyJson 形如 {"methodName":"...","requestData":[...]}
            val node = try {
                mapper.readTree(keyJson)
            } catch (_: Throwable) {
                return null
            }
            if (!node.isObject) return null

            val methodName = pickText(node, "methodName", "method", "Method").trim()
            if (methodName.isBlank()) return null

            val requestDataNode = pickNode(node, "requestData", "RequestData")
            val requestDataStr = toRequestDataString(requestDataNode)

            val id = stableId(methodName, requestDataStr)
            return CustomRpcRequestEntity().apply {
                this.id = id
                this.name = (displayName.ifBlank { methodName }).trim()
                this.methodName = methodName
                this.requestData = requestDataStr
            }
        }

        private fun parseNodeAsRequest(node: JsonNode): CustomRpcRequestEntity? {
            val methodName = pickText(node, "methodName", "method", "Method").trim()
            if (methodName.isBlank()) return null

            val displayName = pickText(node, "name", "Name").trim()

            val requestDataNode = pickNode(node, "requestData", "RequestData")
            val requestDataStr = toRequestDataString(requestDataNode)

            val explicitId = pickText(node, "id", "Id").trim()
            val id = if (explicitId.isNotBlank()) explicitId else stableId(methodName, requestDataStr)

            val scheduleEnabled = pickBoolean(node, "scheduleEnabled", "scheduleEnable", "enableSchedule", "EnableSchedule")
            val dailyCount = pickInt(node, "dailyCount", "dayCount", "DailyCount")
            val normalizedDailyCount = if (scheduleEnabled) dailyCount.coerceAtLeast(0) else 0

            return CustomRpcRequestEntity().apply {
                this.id = id
                this.name = (displayName.ifBlank { methodName }).trim()
                this.methodName = methodName
                this.requestData = requestDataStr
                this.scheduleEnabled = scheduleEnabled
                this.dailyCount = normalizedDailyCount
            }
        }

        private fun pickText(node: JsonNode, vararg keys: String): String {
            for (key in keys) {
                val v = node.get(key) ?: continue
                if (v.isTextual || v.isNumber || v.isBoolean) {
                    return v.asText("")
                }
            }
            return ""
        }

        private fun pickNode(node: JsonNode, vararg keys: String): JsonNode? {
            for (key in keys) {
                if (node.has(key)) return node.get(key)
            }
            return null
        }

        private fun pickBoolean(node: JsonNode, vararg keys: String): Boolean {
            for (key in keys) {
                val v = node.get(key) ?: continue
                if (v.isBoolean) return v.asBoolean(false)
                if (v.isNumber) return v.asInt(0) != 0
                if (v.isTextual) {
                    val s = v.asText("").trim().lowercase()
                    if (s == "true" || s == "1" || s == "yes" || s == "y") return true
                    if (s == "false" || s == "0" || s == "no" || s == "n") return false
                }
            }
            return false
        }

        private fun pickInt(node: JsonNode, vararg keys: String): Int {
            for (key in keys) {
                val v = node.get(key) ?: continue
                if (v.isNumber) return v.asInt(0)
                if (v.isTextual) return v.asText("").trim().toIntOrNull() ?: 0
                if (v.isBoolean) return if (v.asBoolean(false)) 1 else 0
            }
            return 0
        }

        private fun toRequestDataString(requestDataNode: JsonNode?): String {
            return try {
                when {
                    requestDataNode == null || requestDataNode.isNull -> "[{}]"
                    requestDataNode.isTextual -> {
                        val s = requestDataNode.asText("")
                        val t = s.trim()
                        when {
                            t.isBlank() -> "[{}]"
                            t.startsWith("{") && t.endsWith("}") -> "[$t]"
                            else -> s
                        }
                    }
                    requestDataNode.isObject -> {
                        val obj = mapper.writeValueAsString(requestDataNode)
                        if (obj.isBlank()) "[{}]" else "[$obj]"
                    }
                    else -> mapper.writeValueAsString(requestDataNode)
                }.ifBlank { "[{}]" }
            } catch (_: Throwable) {
                "[{}]"
            }
        }

        private fun stableId(methodName: String, requestData: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = (methodName.trim() + "\n" + requestData.trim()).toByteArray(Charsets.UTF_8)
            val digest = md.digest(bytes)
            return digest.joinToString("") { b -> "%02x".format(b) }
        }
    }
}

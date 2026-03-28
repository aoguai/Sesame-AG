package io.github.aoguai.sesameag.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.aoguai.sesameag.SesameApplication.Companion.PREFERENCES_KEY
import io.github.aoguai.sesameag.entity.RpcDebugEntity
import io.github.aoguai.sesameag.hook.ApplicationHookConstants
import io.github.aoguai.sesameag.ui.LogViewerActivity
import io.github.aoguai.sesameag.util.SesameAgUtil
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

// 弹窗状态
sealed class RpcDialogState {
    data object None : RpcDialogState()
    data class Edit(
        val item: RpcDebugEntity?,
        val initialJson: String,
        val initialDesc: String,
        val initialName: String,
        val initialScheduleEnabled: Boolean,
        val initialDailyCount: Int
    ) : RpcDialogState()

    data class DeleteConfirm(val item: RpcDebugEntity) : RpcDialogState()
    data class RestoreConfirm(val items: List<RpcDebugEntity>) : RpcDialogState()


}

class RpcDebugViewModel(application: Application) : AndroidViewModel(application) {


    data class RpcDebugItemRaw(val name: String, val method: String, val requestData: Any?, val description: String)


    private val prefs = application.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)
    private val legacyPrefsKey = "rpc_debug_items"
    private val configFile: File = File(Files.MAIN_DIR, "rpcRequest.json")

    private val objectMapper = JsonMapper.builder()
        .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .build()

    // UI State
    private val _items = MutableStateFlow<List<RpcDebugEntity>>(emptyList())
    val items = _items.asStateFlow()

    private val _dialogState = MutableStateFlow<RpcDialogState>(RpcDialogState.None)
    val dialogState = _dialogState.asStateFlow()

    init {
        val hasConfig = loadItems()
        if (!hasConfig && _items.value.isEmpty()) {
            loadDefaultItems()
        }
    }

    // --- 加载与保存 ---

    /**
     * 读取 RPC 配置（同一份 JSON）：`Android/media/.../sesame-AG/rpcRequest.json`
     *
     * 兼容旧版：如果文件不存在，则尝试从旧 SharedPreferences(`rpc_debug_items`) 迁移一次。
     *
     * @return 是否存在可识别的配置来源（文件存在 / legacy prefs 存在）
     */
    private fun loadItems(): Boolean {
        try {
            if (configFile.exists()) {
                val text = Files.readFromFile(configFile).trim()
                if (text.isBlank()) {
                    _items.value = emptyList()
                    return true
                }

                val list = parseRpcListCompat(text)
                _items.value = list.map { normalizeItem(it) }
                return true
            }

            val legacyText = prefs.getString(legacyPrefsKey, null)?.trim().orEmpty()
            if (legacyText.isNotBlank()) {
                val legacyList = objectMapper.readValue(legacyText, object : TypeReference<List<RpcDebugEntity>>() {})
                _items.value = legacyList.map { normalizeItem(it) }
                saveItems() // 迁移到文件，后续不再需要同步
                return true
            }

            // 兼容旧逻辑：如果 root 文件不存在，尝试从任意账号目录的 rpcRequest.json 迁移到 root
            if (tryMigrateFromAnyUserConfigFile()) {
                return true
            }
        } catch (e: Exception) {
            Log.e("RpcDebug", "Load failed", e)
        }
        return false
    }

    private fun saveItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(_items.value)
                Files.write2File(jsonString, configFile)
            } catch (e: Exception) {
                Log.e("RpcDebug", "Save failed", e)
            }
        }
    }

    private fun parseRpcListCompat(text: String): List<RpcDebugEntity> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(trimmed, object : TypeReference<List<RpcDebugEntity>>() {})
        }

        // 兼容 map 格式：{ "<jsonKey>": "显示名", ... }
        if (trimmed.startsWith("{")) {
            val map = objectMapper.readValue(trimmed, object : TypeReference<Map<String, Any?>>() {})
            val result = ArrayList<RpcDebugEntity>()
            for ((key, value) in map) {
                val displayName = value?.toString()?.trim().orEmpty()
                val keyObj = try {
                    objectMapper.readValue(key, object : TypeReference<Map<String, Any?>>() {})
                } catch (_: Exception) {
                    null
                } ?: continue

                val method = (keyObj["methodName"] ?: keyObj["method"] ?: keyObj["Method"])?.toString()?.trim().orEmpty()
                if (method.isBlank()) continue

                val requestData = keyObj["requestData"] ?: keyObj["RequestData"]
                val id = stableId(method, requestData)

                result.add(
                    RpcDebugEntity(
                        id = id,
                        name = displayName.ifBlank { method },
                        method = method,
                        requestData = requestData,
                        description = ""
                    )
                )
            }
            return result
        }

        return emptyList()
    }

    private fun normalizeItem(item: RpcDebugEntity): RpcDebugEntity {
        if (item.id.isBlank()) {
            item.id = stableId(item.method, item.requestData)
        }
        if (item.name.isBlank()) {
            item.name = item.method
        }
        if (item.dailyCount < 0) item.dailyCount = 0
        if (!item.scheduleEnabled) item.dailyCount = 0
        return item
    }

    private fun stableId(method: String, requestData: Any?): String {
        val requestDataStr = try {
            when (requestData) {
                null -> ""
                is String -> requestData
                is Map<*, *> -> objectMapper.writeValueAsString(listOf(requestData))
                is List<*> -> objectMapper.writeValueAsString(requestData)
                else -> objectMapper.writeValueAsString(requestData)
            }
        } catch (_: Exception) {
            requestData?.toString() ?: ""
        }

        val md = MessageDigest.getInstance("SHA-256")
        val bytes = (method.trim() + "\n" + requestDataStr.trim()).toByteArray(Charsets.UTF_8)
        val digest = md.digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private fun tryMigrateFromAnyUserConfigFile(): Boolean {
        if (configFile.exists()) return false

        val uids = SesameAgUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
        if (uids.isEmpty()) return false

        for (uid in uids) {
            val userDir = File(Files.CONFIG_DIR, uid)
            val candidates = arrayOf(
                File(userDir, "rpcRequest.json"),
                File(userDir, "rpcResquest.json")
            )
            val text = candidates.firstNotNullOfOrNull { f ->
                if (!f.exists()) return@firstNotNullOfOrNull null
                val body = Files.readFromFile(f).trim()
                if (body.isBlank()) null else body
            } ?: continue

            return try {
                val list = parseRpcListCompat(text)
                if (list.isEmpty()) false
                else {
                    _items.value = list.map { normalizeItem(it) }
                    saveItems()
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        return false
    }

    // --- 业务操作 ---

    /**
     * 显示添加 RPC 调试项弹窗
     */
    fun showAddDialog(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        // 1. 修复 RpcDebugItemRaw 报错：传入默认空值
        val (name, method, requestData, description) = try {
            parseJsonFields(clipText)
        } catch (e: Exception) {
            // ✅ 修复点：传入空参数
            RpcDebugItemRaw("", "", null, "")
        }
        // 2. 准备 JSON 字符串 (用于填充输入框)
        val initialJson = if (method.isNotEmpty()) {
            try {
                val map = mapOf("methodName" to method, "requestData" to requestData)
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        // 注意：Name 目前无法通过这种方式预填充（因为 item 为 null），如果需要预填充 Name，
        // 这里建议由用户手动输入 Name，或者只预填充 JSON 和 描述。
        _dialogState.value = RpcDialogState.Edit(null, initialJson, description, name, false, 0)
    }

    /**
     * 显示编辑 RPC 调试项弹窗
     */
    fun showEditDialog(item: RpcDebugEntity) {
        val json = try {
            // 编辑时不把 description 放进 JSON 编辑框，而是单独显示
            val map = mapOf("methodName" to item.method, "requestData" to item.requestData)
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)
        } catch (_: Exception) {
            "{}"
        }

        _dialogState.value = RpcDialogState.Edit(item, json, item.description, item.name, item.scheduleEnabled, item.dailyCount)
    }

    fun showDeleteDialog(item: RpcDebugEntity) {
        _dialogState.value = RpcDialogState.DeleteConfirm(item)
    }

    fun dismissDialog() {
        _dialogState.value = RpcDialogState.None
    }

    // 保存逻辑
    fun saveItem(
        name: String,
        description: String,
        jsonText: String,
        scheduleEnabled: Boolean,
        dailyCount: Int,
        editingItem: RpcDebugEntity?
    ) {
        try {
            // 解析 JSON 编辑框的内容 (这里面只包含 method 和 data)
            val (_, method, requestData, _) = parseJsonFields(jsonText)

            // name 和 description 从独立输入框取
            val finalName = name.ifEmpty { method } // 如果没填名字，用 method 代替
            val finalDailyCount = if (scheduleEnabled) dailyCount.coerceAtLeast(0) else 0

            if (method.isEmpty()) {
                ToastUtil.makeText("methodName 不能为空", 0).show()
                return
            }

            val currentList = _items.value.toMutableList()
            val newId = stableId(method, requestData)

            if (editingItem != null) {
                // 编辑模式
                val index = currentList.indexOfFirst { it.id == editingItem.id }
                if (index != -1) {
                    currentList[index] = normalizeItem(
                        editingItem.copy(
                            id = newId,
                            name = finalName,
                            description = description,
                            method = method,
                            requestData = requestData,
                            scheduleEnabled = scheduleEnabled,
                            dailyCount = finalDailyCount
                        )
                    )
                    _items.value = currentList.toList()
                }
            } else {
                // 新增模式
                val newItem = RpcDebugEntity(
                    id = newId,
                    name = finalName,
                    description = description,
                    method = method,
                    requestData = requestData,
                    scheduleEnabled = scheduleEnabled,
                    dailyCount = finalDailyCount
                )
                currentList.add(normalizeItem(newItem))
                _items.value = currentList
            }
            saveItems()
            dismissDialog()
            ToastUtil.makeText("保存成功", 0).show()
        } catch (e: Exception) {
            ToastUtil.makeText("JSON 格式错误: ${e.message}", 1).show()
        }
    }

    fun deleteItem(item: RpcDebugEntity) {
        val list = _items.value.toMutableList()
        list.remove(item)
        _items.value = list
        saveItems()
        dismissDialog()
    }

    fun runRpcItem(item: RpcDebugEntity, activityContext: Context) {
        viewModelScope.launch {
            try {
                val logFile = Files.getDebugLogFile()
                Files.clearFile(logFile)
                val intent = Intent(ApplicationHookConstants.BroadcastActions.RPC_TEST).apply {
                    putExtra("method", item.method)
                    putExtra("data", item.getRequestDataString())
                    putExtra("type", "Rpc")
                }
                activityContext.sendBroadcast(intent)
                ToastUtil.makeText("已发送: ${item.getDisplayName()}", Toast.LENGTH_SHORT).show()
                // 轮询等待日志写入（Logback 是异步写入的，需要等待）
                var waitCount = 0
                val maxWait = 30 // 最多等待 3 秒（30 * 100ms）
                while (waitCount < maxWait) {
                    delay(100)
                    if (logFile.exists() && logFile.length() > 0) {
                        // 日志文件有内容了，再等待一小段时间确保写入完成
                        delay(200)
                        break
                    }
                    waitCount++
                }
                // 跳转日志
                try {
                    val logIntent = Intent(activityContext, LogViewerActivity::class.java).apply {
                        data = logFile.toUri()
                    }
                    activityContext.startActivity(logIntent)
                } catch (_: Exception) {
                    ToastUtil.makeText("无法打开日志", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ToastUtil.makeText("执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- 工具功能 ---

    fun backupToClipboard(context: Context) {
        if (_items.value.isEmpty()) return
        try {
            val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(_items.value)
            copyToClipboard("RPC Backup", json, context)
            ToastUtil.makeText("已备份到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            ToastUtil.makeText("备份失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun tryRestoreFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (!text.trim().startsWith("[")) {
            ToastUtil.makeText(context, "剪贴板不是数组格式", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val list = objectMapper.readValue(text, object : TypeReference<List<RpcDebugEntity>>() {})
            _dialogState.value = RpcDialogState.RestoreConfirm(list)
        } catch (_: Exception) {
            ToastUtil.makeText(context, "解析失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmRestore(newItems: List<RpcDebugEntity>) {
        _items.value = newItems.map { normalizeItem(it) }
        saveItems()
        dismissDialog()
        ToastUtil.makeText("恢复成功", Toast.LENGTH_SHORT).show()
    }

    fun loadDefaultItems() {
        val defaultList = listOf(
            RpcDebugEntity(
                name = "雇佣黄金鸡",
                method = "com.alipay.antfarm.hireAnimal",
                requestData = listOf(mapOf("hireActionType" to "HIRE_IN_SELF_FARM", "sceneCode" to "ANTFARM")), // 简化示例
                description = "这是一个雇佣黄金鸡的操作,可以让你雇佣一个黄金的鸡"
            )
        )
        // 简单合并逻辑：略
        _items.value = defaultList
        saveItems()
    }

    fun shareItem(item: RpcDebugEntity, context: Context) {
        try {
            val map = mapOf(
                "Name" to item.name,
                "Description" to item.description, // 分享出去
                "methodName" to item.method,
                "requestData" to item.requestData
            )
            val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)
            copyToClipboard("RPC Item", json, context)
            ToastUtil.makeText("已复制完整配置", 0).show()
        } catch (_: Exception) {
        }
    }

    private fun copyToClipboard(label: String, text: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun parseJsonFields(json: String): RpcDebugItemRaw {
        Log.d("RpcDebug", "尝试解析 JSON: $json")
        val map = try {
            // 2. 使用 TypeReference 明确泛型，避免类型擦除问题
            objectMapper.readValue(json, object : TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            Log.e("RpcDebug", "JSON 解析失败", e)
            emptyMap()
        }

        return RpcDebugItemRaw(
            // 3. 增加 trim() 去除可能存在的首尾空格
            name = (map["name"] ?: map["Name"])?.toString()?.trim() ?: "",
            method = (map["method"] ?: map["methodName"] ?: map["Method"])?.toString()?.trim() ?: "",
            requestData = map["requestData"] ?: map["RequestData"],
            description = (map["description"] ?: map["Description"] ?: map["desc"] ?: map["Desc"])?.toString()?.trim() ?: ""
        )
    }

    fun formatJsonFromRaw(data: Any): String {
        return try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * 尝试格式化 JSON 字符串
     * @return 格式化后的 JSON，如果解析失败返回 null
     */
    fun tryFormatJson(jsonStr: String): String? {
        if (jsonStr.isBlank()) return null
        return try {
            // 1. 先解析成通用对象 (Map 或 List)
            val obj = objectMapper.readValue(jsonStr, Any::class.java)
            // 2. 再用 PrettyPrinter 输出
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
        } catch (e: Exception) {
            // 解析失败（格式错误），返回 null 让 UI 提示
            null
        }
    }
}



package io.github.aoguai.sesameag.ui.repository

import android.app.Application
import android.content.Context
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.aoguai.sesameag.SesameApplication.Companion.PREFERENCES_KEY
import io.github.aoguai.sesameag.entity.RpcDebugEntity
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.SesameAgUtil
import java.io.File
import java.security.MessageDigest

internal data class RpcDebugLoadResult(
    val items: List<RpcDebugEntity>,
    val hasConfigSource: Boolean
)

internal class RpcDebugConfigStore(application: Application) {

    private val prefs = application.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)
    private val configFile = File(Files.MAIN_DIR, ROOT_CONFIG_FILE_NAME)
    private val objectMapper = JsonMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .build()

    fun loadItems(): RpcDebugLoadResult {
        return try {
            loadFromRootConfig()?.let { return it }
            loadFromLegacyPrefs()?.let { items ->
                saveItems(items)
                RpcDebugLoadResult(items, true)
            } ?: migrateFromUserConfigFiles()?.let { items ->
                saveItems(items)
                RpcDebugLoadResult(items, true)
            } ?: RpcDebugLoadResult(emptyList(), false)
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            RpcDebugLoadResult(emptyList(), false)
        }
    }

    fun saveItems(items: List<RpcDebugEntity>) {
        try {
            val normalizedItems = normalizeItems(items)
            val jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalizedItems)
            Files.write2File(jsonString, configFile)
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
        }
    }

    fun normalizeItems(items: List<RpcDebugEntity>): List<RpcDebugEntity> {
        return items.map(::normalizeItem)
    }

    fun normalizeItem(item: RpcDebugEntity): RpcDebugEntity {
        val normalizedId = item.id.ifBlank { stableId(item.method, item.requestData) }
        val normalizedName = item.name.ifBlank { item.method }
        val normalizedDailyCount = if (item.scheduleEnabled) item.dailyCount.coerceAtLeast(0) else 0
        return item.copy(
            id = normalizedId,
            name = normalizedName,
            dailyCount = normalizedDailyCount
        )
    }

    fun stableId(method: String, requestData: Any?): String {
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

    private fun loadFromRootConfig(): RpcDebugLoadResult? {
        if (!configFile.exists()) {
            return null
        }

        val text = Files.readFromFile(configFile).trim()
        if (text.isBlank()) {
            return RpcDebugLoadResult(emptyList(), true)
        }

        return RpcDebugLoadResult(normalizeItems(parseRpcListCompat(text)), true)
    }

    private fun loadFromLegacyPrefs(): List<RpcDebugEntity>? {
        val legacyText = prefs.getString(LEGACY_PREFS_KEY, null)?.trim().orEmpty()
        if (legacyText.isBlank()) {
            return null
        }
        val legacyList = objectMapper.readValue(legacyText, object : TypeReference<List<RpcDebugEntity>>() {})
        return normalizeItems(legacyList)
    }

    private fun migrateFromUserConfigFiles(): List<RpcDebugEntity>? {
        if (configFile.exists()) {
            return null
        }

        val uids = SesameAgUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
        if (uids.isEmpty()) {
            return null
        }

        for (uid in uids) {
            val userDir = File(Files.CONFIG_DIR, uid)
            val text = readUserConfigText(userDir) ?: continue
            try {
                val parsedItems = parseRpcListCompat(text)
                if (parsedItems.isNotEmpty()) {
                    return normalizeItems(parsedItems)
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "Failed to migrate rpc config from ${userDir.absolutePath}", e)
            }
        }

        return null
    }

    private fun readUserConfigText(userDir: File): String? {
        val candidates = arrayOf(
            File(userDir, ROOT_CONFIG_FILE_NAME),
            File(userDir, LEGACY_FILE_NAME)
        )
        return candidates.firstNotNullOfOrNull { file ->
            if (!file.exists()) {
                return@firstNotNullOfOrNull null
            }
            Files.readFromFile(file).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun parseRpcListCompat(text: String): List<RpcDebugEntity> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(trimmed, object : TypeReference<List<RpcDebugEntity>>() {})
        }

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
                if (method.isBlank()) {
                    continue
                }

                val requestData = keyObj["requestData"] ?: keyObj["RequestData"]
                result.add(
                    RpcDebugEntity(
                        id = stableId(method, requestData),
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

    private companion object {
        private const val TAG = "RpcDebugConfigStore"
        private const val LEGACY_PREFS_KEY = "rpc_debug_items"
        private const val ROOT_CONFIG_FILE_NAME = "rpcRequest.json"
        private const val LEGACY_FILE_NAME = "rpcResquest.json"
    }
}

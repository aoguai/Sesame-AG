package io.github.aoguai.sesameag.util

import com.fasterxml.jackson.core.type.TypeReference
import kotlin.collections.any

/**
 * 通用任务黑名单管理器
 * 使用DataStore持久化存储黑名单数据
 */
object TaskBlacklist {
    private const val TAG = "TaskBlacklist"
    private const val BLACKLIST_KEY = "task_blacklist"

    private fun getAllBlacklists(): Map<String, Set<String>> {
        return try {
            DataStore.getOrCreate(BLACKLIST_KEY, object : TypeReference<Map<String, Set<String>>>() {})
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "获取黑名单列表失败", e)
            emptyMap()
        }
    }

    /**
     * 保存完整的黑名单映射
     */
    private fun saveAllBlacklists(blacklists: Map<String, Set<String>>) {
        try {
            DataStore.put(BLACKLIST_KEY, blacklists)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "保存黑名单失败", e)
        }
    }

    /**
     * 获取所有有黑名单的模块名称
     */
    fun getBlacklistModuleNames(): List<String> {
        return getAllBlacklists().keys.toList()
    }

    /**
     * 获取指定模块的黑名单
     */
    fun getBlacklist(moduleName: String?): Set<String> {
        if (moduleName.isNullOrBlank()) return emptySet()
        return getAllBlacklists()[moduleName] ?: emptySet()
    }

    /**
     * 获取黑名单列表
     * @return 黑名单任务集合
     */
    fun getBlacklist(): Set<String> {
        return try {
            // 优先从所有模块中合并
            val allStored = getAllBlacklists().values.flatten().toSet()
            if (allStored.isNotEmpty()) {
                (allStored + defaultBlacklist).toSet()
            } else {
                // 兼容旧格式
                val storedBlacklist = DataStore.getOrCreate(BLACKLIST_KEY, object : TypeReference<Set<String>>() {})
                (storedBlacklist + defaultBlacklist).toSet()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "获取黑名单失败，使用默认黑名单", e)
            defaultBlacklist
        }
    }
    
    
    
    /**
     * 保存黑名单列表
     * @param blacklist 要保存的黑名单集合
     */
    private fun saveBlacklist(blacklist: Set<String>) {
        try {
            DataStore.put(BLACKLIST_KEY, blacklist)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "保存黑名单失败", e)
        }
    }

    /**
     * 检查任务是否在黑名单中
     * 采用包含匹配逻辑，确保 ID 或 标题 任意一项命中即可
     */
    fun isTaskInBlacklist(moduleName: String?, taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false
        if (moduleName.isNullOrBlank()) return isTaskInBlacklist(taskInfo)

        // 1. 检查内置黑名单
        DEFAULT_BLACKLIST[moduleName]?.let { defaultSet ->
            if (defaultSet.any { isMatch(taskInfo, it) }) return true
        }

        // 2. 检查持久化存储的黑名单
        val moduleBlacklist = getBlacklist(moduleName)
        return moduleBlacklist.any { isMatch(taskInfo, it) }
    }

    /**
     * 兼容旧版调用，检查任务是否在任意模块的黑名单中
     */
    fun isTaskInBlacklist(taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false

        // 1. 检查所有内置黑名单
        if (DEFAULT_BLACKLIST.values.any { set -> set.any { isMatch(taskInfo, it) } }) return true

        // 2. 检查所有持久化存储的黑名单
        return getAllBlacklists().values.any { set -> set.any { isMatch(taskInfo, it) } }
    }

    /**
     * 统一的匹配逻辑
     * @param input 传入的待检查字符串 (通常是 taskId)
     * @param blacklistItem 黑名单中的项 (通常是 taskId + taskTitle)
     */
    private fun isMatch(input: String, blacklistItem: String): Boolean {
        if (blacklistItem.isBlank()) return false

        // 1. 完全匹配
        if (input == blacklistItem) return true

        // 2. 分隔符匹配（如果黑名单项包含 | 分隔符，说明是新格式）
        if (blacklistItem.contains('|')) {
            val parts = blacklistItem.split('|')
            return parts.any { part ->
                if (part.isBlank()) return@any false
                if (input == part) return@any true
                // 如果 input 包含中文（标题），则允许包含匹配
                val inputHasChinese = input.any { it in '\u4e00'..'\u9fa5' }
                if (inputHasChinese) {
                    return@any input.contains(part) || part.contains(input)
                }
                false
            }
        }

        // 3. 兼容旧格式（IDTitle）
        val itemHasChinese = blacklistItem.any { it in '\u4e00'..'\u9fa5' }
        val inputHasChinese = input.any { it in '\u4e00'..'\u9fa5' }

        return if (itemHasChinese) {
            if (!inputHasChinese) {
                // 如果 input 是 ID（无中文），尝试更精确的匹配：ID 必须是黑名单项的前缀，且后面紧跟中文
                if (blacklistItem.startsWith(input)) {
                    val nextIdx = input.length
                    if (nextIdx < blacklistItem.length && blacklistItem[nextIdx] in '\u4e00'..'\u9fa5') {
                        return true
                    }
                }
                false
            } else {
                // 如果 input 也是标题（含中文），则维持包含匹配
                blacklistItem.contains(input) || input.contains(blacklistItem)
            }
        } else {
            // 黑名单项无中文（可能是纯 ID），则要求 input 包含黑名单项（常规关键词匹配）
            input.contains(blacklistItem)
        }
    }

    /**
     * 添加任务到指定模块的黑名单
     */
    fun addToBlacklist(moduleName: String?, taskId: String, taskTitle: String = "") {
        if (moduleName.isNullOrBlank() || taskId.isBlank()) return

        // 使用分隔符 | 拼接 ID 和 标题，便于后续精确匹配
        val blacklistItem = if (taskTitle.isNotBlank() && taskId != taskTitle) "$taskId|$taskTitle" else taskId
        val allBlacklists = getAllBlacklists().toMutableMap()
        val moduleSet = allBlacklists[moduleName]?.toMutableSet() ?: mutableSetOf()

        if (moduleSet.add(blacklistItem)) {
            allBlacklists[moduleName] = moduleSet
            saveAllBlacklists(allBlacklists)
        }
    }

    /**
     * 从指定模块的黑名单中移除任务
     */
    fun removeFromBlacklist(moduleName: String?, taskId: String, taskTitle: String = "") {
        if (moduleName.isNullOrBlank() || taskId.isBlank()) return

        val blacklistItem = if (taskTitle.isNotBlank() && taskId != taskTitle) "$taskId|$taskTitle" else taskId
        val allBlacklists = getAllBlacklists().toMutableMap()
        val moduleSet = allBlacklists[moduleName]?.toMutableSet() ?: return

        if (moduleSet.remove(blacklistItem)) {
            allBlacklists[moduleName] = moduleSet
            saveAllBlacklists(allBlacklists)
            Log.record(TAG, "模块[$moduleName]的任务[$blacklistItem]已从黑名单移除")
        }
    }

    /**
     * 清空指定模块的黑名单
     */
    fun clearBlacklist(moduleName: String?) {
        if (moduleName.isNullOrBlank()) return
        val allBlacklists = getAllBlacklists().toMutableMap()
        if (allBlacklists.remove(moduleName) != null) {
            saveAllBlacklists(allBlacklists)
            Log.record(TAG, "模块[$moduleName]的黑名单已清空")
        }
    }

    /**
     * 清空所有模块的黑名单
     */
    fun clearAllBlacklists() {
        saveAllBlacklists(emptyMap())
        Log.record(TAG, "所有任务黑名单已清空")
    }

    /**
     * 兼容旧版调用，自动添加任务到黑名单
     */
    fun autoAddToBlacklist(taskId: String, taskTitle: String, errorCode: String) {
        autoAddToBlacklist("未分类", taskId, taskTitle, errorCode)
    }

    /**
     * 自动添加任务到模块黑名单
     */
    fun autoAddToBlacklist(moduleName: String?, taskId: String, taskTitle: String = "", errorCode: String) {
        if (taskId.isBlank()) return
        val finalModuleName = if (moduleName.isNullOrBlank()) "未分类" else moduleName

        var shouldAutoAdd = false
        var reason = ""

        when {
            errorCode == "400000040" -> { shouldAutoAdd = true; reason = "不支持rpc调用" }
            errorCode == "CAMP_TRIGGER_ERROR" -> { shouldAutoAdd = true; reason = "活动触发错误" }
            errorCode == "OP_REPEAT_CHECK" -> { shouldAutoAdd = true; reason = "操作太频繁" }
            errorCode == "ILLEGAL_ARGUMENT" -> { shouldAutoAdd = true; reason = "参数错误" }
            errorCode == "104" || errorCode == "PROMISE_HAS_PROCESSING_TEMPLATE" -> {
                shouldAutoAdd = true; reason = "存在进行中的记录"
            }
            errorCode == "TASK_ID_INVALID" -> { shouldAutoAdd = true; reason = "任务ID非法" }
            errorCode == "PROMISE_TEMPLATE_NOT_EXIST" || errorCode == "生活记录模板不存在" -> {
                shouldAutoAdd = true; reason = "模板不存在"
            }
            errorCode.contains("系统繁忙") || errorCode.contains("稍后再试") -> {
                shouldAutoAdd = true; reason = "系统繁忙/稍后再试"
            }
            errorCode == "FAKE_SUCCESS" -> { shouldAutoAdd = true; reason = "检测到伪成功" }
            errorCode == "10000005" -> { shouldAutoAdd = true; reason = "海豚服务异常" }
        }

        if (shouldAutoAdd) {
            addToBlacklist(finalModuleName, taskId, taskTitle)
            val taskInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.record(TAG, "模块[$finalModuleName]任务[$taskInfo]因$reason 自动加入黑名单")
        }
    }
}

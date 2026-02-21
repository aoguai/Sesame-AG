package fansirsqi.xposed.sesame.util

import com.fasterxml.jackson.core.type.TypeReference

/**
 * 通用任务黑名单管理器
 * 使用DataStore持久化存储黑名单数据
 */
object TaskBlacklist {
    private const val TAG = "TaskBlacklist"
    private const val BLACKLIST_KEY = "task_blacklist"

    /**
     * 获取黑名单列表
     * @return 黑名单任务集合
     */
    fun getBlacklist(): Set<String> {
        return try {
            val storedBlacklist = DataStore.getOrCreate(BLACKLIST_KEY, object : TypeReference<Set<String>>() {})
            // 合并存储的黑名单和默认黑名单
            (storedBlacklist + defaultBlacklist).toSet()
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
     * 检查任务是否在黑名单中（精确匹配逻辑）
     * @param taskInfo 任务信息（可以是任务ID、任务标题或组合信息）
     * @return true表示在黑名单中，应该跳过
     */
    fun isTaskInBlacklist(taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false
        
        val blacklist = getBlacklist()
        return blacklist.any { item ->
            if (item.isBlank()) return@any false

            // 完全匹配（最精确）
            if (taskInfo == item) return@any true

            // 区分处理中文关键词和纯英文的匹配模式。
            val itemHasChinese = item.any { it in '\u4e00'..'\u9fa5' }

            if (itemHasChinese) {
                // 包含中文的项维持双向模糊匹配逻辑
                taskInfo.contains(item) || item.contains(taskInfo)
            } else {
                /* 纯英文/数字/符号项使用单向模糊匹配逻辑；防止黑名单中"TAOBAO"这类比较简短、通用的字段匹配到任务
                    "TAOBAO_tab2gzy" ，导致不是在黑名单中的任务被跳过
                 */
                item.contains(taskInfo)
            }
        }
    }
    
    /**
     * 添加任务到黑名单
     * @param taskId 要添加的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    fun addToBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return
        // 如果提供了任务标题，则将ID和标题组合后添加，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId
        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.add(blacklistItem)) {
            saveBlacklist(currentBlacklist)
        }
    }
    
    /**
     * 从黑名单中移除任务
     * @param taskId 要移除的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    fun removeFromBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return
        
        // 如果提供了任务标题，则将ID和标题组合后移除，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId
        
        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.remove(blacklistItem)) {
            saveBlacklist(currentBlacklist)
            val displayInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.record(TAG, "任务[$displayInfo]已从黑名单移除")
        }
    }
    
    /**
     * 清空黑名单
     */
    fun clearBlacklist() {
        try {
            saveBlacklist(emptySet())
            Log.record(TAG, "黑名单已清空")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "清空黑名单失败", e)
        }
    }
    
    /**
     * 根据错误码自动添加任务到黑名单
     * 当任务执行失败时，如果错误码属于预定义的无法恢复的错误类型，
     * 系统会自动将该任务加入黑名单，避免重复执行失败的任务
     * 
     * @param taskId 任务ID，用于标识具体任务
     * @param taskTitle 任务标题（可选），用于显示和模糊匹配
     * @param errorCode 错误码，用于判断是否需要自动加入黑名单
     */
    fun autoAddToBlacklist(taskId: String, taskTitle: String = "", errorCode: String) {
        // 参数校验：如果任务ID为空，直接返回
        if (taskId.isBlank()) return
        // 第一步：判断当前错误码是否需要自动加入黑名单
        // 只有特定的、无法通过重试解决的错误才会自动加入黑名单
        val shouldAutoAdd = when (errorCode) {
            // 农场任务特有的错误：后端不支持RPC调用
            "400000040" -> true
            "CAMP_TRIGGER_ERROR", // 以下错误码都会导致任务自动加入黑名单：
            "104",
            "OP_REPEAT_CHECK",               // 操作频率过高，被系统限制
            "ILLEGAL_ARGUMENT",              // 参数不合法或格式错误
            "PROMISE_HAS_PROCESSING_TEMPLATE" -> true // 存在进行中的生活记录
            "TASK_ID_INVALID" -> true        // 海豚任务ID非法
            else -> false                    // 其他错误码不自动加入黑名单
        }
        
        // 第二步：如果确定需要自动加入黑名单
        if (shouldAutoAdd) {
            // 调用添加方法，将任务ID和标题组合后加入黑名单（支持模糊匹配）
            addToBlacklist(taskId, taskTitle)
            // 第三步：根据错误码生成用户友好的错误说明
            val reason = when (errorCode) {
                "400000040" -> "不支持rpc调用"
                "CAMP_TRIGGER_ERROR" -> "海豚活动触发错误"
                "OP_REPEAT_CHECK" -> "操作太频繁"
                "ILLEGAL_ARGUMENT" -> "参数错误"
                "104", "PROMISE_HAS_PROCESSING_TEMPLATE" -> "存在进行中的生活记录"
                "TASK_ID_INVALID" -> true        // 海豚任务ID非法
                else -> "未知错误"  // 理论上不会执行到此处
            }
            
            // 第四步：生成日志信息并记录
            // 优先显示完整信息（ID-标题），如果标题为空则只显示ID
            val taskInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.record(TAG, "任务[$taskInfo]因$reason 自动加入黑名单")
        }
    }
}
package io.github.aoguai.sesameag.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.entity.UserEntity
import io.github.aoguai.sesameag.model.ModelConfig
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.model.modelFieldExt.SelectAndCountModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.task.TaskCommon
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.FriendGuard
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.maps.UserMap
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 配置类，负责加载、保存、管理应用的配置数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Config private constructor() {

    /** 是否初始化标志 */
    @JsonIgnore
    @Volatile
    var isInit: Boolean = false
        private set

    /** 存储模型字段的映射 */
    private val modelFieldsMap: MutableMap<String, ModelFields> = ConcurrentHashMap()

    /** 用户最近一次确认 LEGAL 说明时对应的应用版本。 */
    var legalAcceptedAppVersion: String? = null
    
    /**
     * 获取模型字段映射（用于序列化）
     */
    fun getModelFieldsMap(): MutableMap<String, ModelFields> = modelFieldsMap
    
    /**
     * 设置模型字段映射（用于反序列化）
     * 这个方法会在Jackson反序列化时被调用
     */
    fun setModelFieldsMap(newModels: Map<String, ModelFields>?) {
        modelFieldsMap.clear()
        val modelConfigMap = Model.getModelConfigMap()
        val models = newModels ?: emptyMap()

        // 遍历所有模型配置，从ModelConfig.fields复制字段
        for ((modelCode, modelConfig) in modelConfigMap.entries) {
            val newModelFields = ModelFields()
            val configModelFields = modelConfig.fields
            val modelFields = models[modelCode]

            if (modelFields != null) {
                // 如果JSON中有这个模型的配置，用JSON的值覆盖
                for (configModelField in configModelFields.values) {
                    val modelField = modelFields[configModelField.code]
                    try {
                        if (modelField != null) {
                            val value = modelField.value
                            if (value != null) {
                                configModelField.setObjectValue(value)
                            }
                        }
                    } catch (e: Exception) {
                        Log.printStackTrace(e)
                    }
                    newModelFields.addField(configModelField)
                }
            } else {
                // 如果JSON中没有这个模型，直接复制ModelConfig的字段
                for (configModelField in configModelFields.values) {
                    newModelFields.addField(configModelField)
                }
            }
            modelFieldsMap[modelCode] = newModelFields
        }
    }

    /**
     * 检查是否存在指定的模型字段
     *
     * @param modelCode 模型代码
     * @return 是否存在该模型字段
     */
    fun hasModelFields(modelCode: String): Boolean {
        return modelFieldsMap.containsKey(modelCode)
    }

    /**
     * 检查指定模型字段是否存在
     *
     * @param modelCode 模型代码
     * @param fieldCode 字段代码
     * @return 是否存在该字段
     */
    fun hasModelField(modelCode: String, fieldCode: String): Boolean {
        val modelFields = modelFieldsMap[modelCode] ?: return false
        return modelFields.containsKey(fieldCode)
    }

    @JsonIgnore
    fun hasAcceptedLegalForCurrentVersion(): Boolean {
        return legalAcceptedAppVersion?.trim().orEmpty() == BuildConfig.VERSION_NAME
    }

    fun updateLegalAcceptedForCurrentVersion(accepted: Boolean) {
        legalAcceptedAppVersion = if (accepted) BuildConfig.VERSION_NAME else ""
    }

    companion object {
        private const val TAG = "Config"
        private val FRIEND_SELECTION_FIELD_CODES = setOf(
            "feedFriendAnimalList",
            "getFeedlList",
            "visitFriendList",
            "hireAnimalList",
            "dontSendFriendList",
            "notifyFriendList",
            "notInviteList",
            "dontCollectList",
            "giveEnergyRainList",
            "waterFriendList",
            "whoYouWantToGiveTo",
            "helpFriendCollectList",
            "cleanOceanList",
            "stallOpenList",
            "stallTicketList",
            "stallThrowManureList",
            "stallInviteShopList",
            "stallWhiteList",
            "stallBlackList",
            "stallInviteRegisterList",
            "assistFriendList",
            "originBossIdList",
            "collectToFriendList",
            "sendFriendCard"
        )
        private const val LEGAL_ACCEPTED_APP_VERSION_FIELD = "legalAcceptedAppVersion"

        /** 单例实例 */
        @JvmField
        val INSTANCE = Config()

        /**
         * 判断配置文件是否已修改
         *
         * @param userId 用户 ID
         * @return 是否已修改
         */
        @JvmStatic
        fun isModify(userId: String?): Boolean {
            val configV2File = if (userId.isNullOrEmpty()) {
                Files.getDefaultConfigV2File()
            } else {
                Files.getConfigV2File(userId)
            }

            if (!configV2File.exists()) {
                return true
            }

            val json = Files.readFromFile(configV2File) ?: return true
            val formatted = JsonUtil.formatJson(INSTANCE) ?: return true
            return formatted != json
        }

        /**
         * 保存配置文件
         *
         * @param userId 用户 ID
         * @param force  是否强制保存
         * @return 保存是否成功
         */
        @JvmStatic
        @Synchronized
        fun save(userId: String?, force: Boolean): Boolean {
            if (!force && !isModify(userId)) {
                return true
            }

            val json = try {
                JsonUtil.formatJson(INSTANCE)
                    ?: throw IllegalStateException("配置格式化失败，返回的 JSON 为空")
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
                Log.runtime(TAG, "保存用户配置失败，格式化 JSON 时出错")
                return false
            }

            return try {
                val actualUserId = userId?.takeIf { it.isNotEmpty() } ?: "默认"
                val success = if (userId.isNullOrEmpty()) {
                    Files.setDefaultConfigV2File(json)
                } else {
                    Files.setConfigV2File(userId, json)
                }

                if (!success) {
                    throw IOException("配置文件保存失败")
                }

                val userName = if (actualUserId == "默认") {
                    "默认用户"
                } else {
                    UserMap.get(actualUserId)?.showName ?: "默认"
                }

                Log.runtime(TAG, "保存 [$userName] 配置")
                true
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
                Log.runtime(TAG, "保存用户配置失败")
                false
            }
        }

        /**
         * 从所有选择型配置中移除失效好友。
         *
         * @param invalidUserIds 需要移除的用户ID集合
         * @param selfUserId     当前用户ID，默认不移除自己
         * @param autoSave       是否自动保存配置
         * @return 移除的配置项数量
         */
        @JvmStatic
        @Synchronized
        fun removeInvalidFriendSelections(
            invalidUserIds: Set<String>,
            selfUserId: String? = UserMap.currentUid,
            autoSave: Boolean = true
        ): Int {
            if (!INSTANCE.isInit || invalidUserIds.isEmpty()) {
                return 0
            }

            var removedCount = 0

            for ((modelCode, modelFields) in INSTANCE.modelFieldsMap) {
                for ((fieldCode, modelField) in modelFields) {
                    if (!FRIEND_SELECTION_FIELD_CODES.contains(fieldCode)) {
                        continue
                    }
                    when (modelField) {
                        is SelectModelField -> {
                            val selectedIds = modelField.value ?: continue
                            val iterator = selectedIds.iterator()
                            while (iterator.hasNext()) {
                                val selectedId = iterator.next() ?: continue
                                if (selectedId == selfUserId || !invalidUserIds.contains(selectedId)) {
                                    continue
                                }
                                iterator.remove()
                                removedCount++
                                Log.record(TAG, "移除失效好友配置[$modelCode.$fieldCode][$selectedId]")
                            }
                        }

                        is SelectAndCountModelField -> {
                            val selectedMap = modelField.value ?: continue
                            val invalidKeys = selectedMap.keys
                                .filterNotNull()
                                .filter { it != selfUserId && invalidUserIds.contains(it) }
                            if (invalidKeys.isEmpty()) {
                                continue
                            }
                            invalidKeys.forEach { invalidKey ->
                                if (selectedMap.remove(invalidKey) != null) {
                                    removedCount++
                                    Log.record(TAG, "移除失效好友配置[$modelCode.$fieldCode][$invalidKey]")
                                }
                            }
                        }
                    }
                }
            }

            if (removedCount > 0) {
                Log.record(TAG, "共清理失效好友配置 $removedCount 项")
                if (autoSave) {
                    save(selfUserId, true)
                }
            }
            return removedCount
        }

        /**
         * 从已知好友配置项中移除当前账号自己，避免误选自己进入好友流程。
         */
        @JvmStatic
        @Synchronized
        fun removeSelfFriendSelections(
            selfUserId: String? = UserMap.currentUid,
            autoSave: Boolean = true
        ): Int {
            if (!INSTANCE.isInit || selfUserId.isNullOrBlank()) {
                return 0
            }

            var removedCount = 0

            for ((modelCode, modelFields) in INSTANCE.modelFieldsMap) {
                for ((fieldCode, modelField) in modelFields) {
                    if (!FRIEND_SELECTION_FIELD_CODES.contains(fieldCode)) {
                        continue
                    }
                    when (modelField) {
                        is SelectModelField -> {
                            val selectedIds = modelField.value ?: continue
                            if (selectedIds.remove(selfUserId)) {
                                removedCount++
                                Log.record(TAG, "移除好友配置中的自己[$modelCode.$fieldCode][$selfUserId]")
                            }
                        }

                        is SelectAndCountModelField -> {
                            val selectedMap = modelField.value ?: continue
                            if (selectedMap.remove(selfUserId) != null) {
                                removedCount++
                                Log.record(TAG, "移除好友配置中的自己[$modelCode.$fieldCode][$selfUserId]")
                            }
                        }
                    }
                }
            }

            if (removedCount > 0) {
                Log.record(TAG, "共清理好友配置中的自己 $removedCount 项")
                if (autoSave) {
                    save(selfUserId, true)
                }
            }
            return removedCount
        }

        /**
         * 检查配置是否已加载
         *
         * @return 是否已加载
         */
        @JvmStatic
        fun isLoaded(): Boolean = INSTANCE.isInit

        @JvmStatic
        fun isLegalAcceptedForCurrentVersion(): Boolean = INSTANCE.hasAcceptedLegalForCurrentVersion()

        @JvmStatic
        fun setLegalAcceptedForCurrentVersion(accepted: Boolean) {
            INSTANCE.updateLegalAcceptedForCurrentVersion(accepted)
        }

        @JvmStatic
        fun readLegalAcceptedForCurrentVersion(userId: String?): Boolean {
            val readableFile = resolveReadableConfigFile(userId) ?: return false
            val acceptedVersion = readLegalAcceptedAppVersion(Files.readFromFile(readableFile))
            return acceptedVersion?.trim().orEmpty() == BuildConfig.VERSION_NAME
        }

        @JvmStatic
        @Synchronized
        fun saveLegalAcceptedForCurrentVersion(userId: String?, accepted: Boolean): Boolean {
            val targetFile = if (userId.isNullOrEmpty()) {
                Files.getDefaultConfigV2File()
            } else {
                Files.getConfigV2File(userId)
            }

            val baseJson = readBaseConfigJsonForLegalWrite(userId, targetFile) ?: return false
            val mapper = JsonUtil.copyMapper()
            val rootNode = (mapper.readTree(baseJson) as? ObjectNode) ?: mapper.createObjectNode()
            rootNode.put(LEGAL_ACCEPTED_APP_VERSION_FIELD, if (accepted) BuildConfig.VERSION_NAME else "")
            val updatedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode)

            return if (userId.isNullOrEmpty()) {
                Files.setDefaultConfigV2File(updatedJson)
            } else {
                Files.setConfigV2File(userId, updatedJson)
            }
        }

        /**
         * 加载配置文件
         *
         * @param userId 用户 ID
         * @return 配置实例
         */
        @JvmStatic
        @Synchronized
        fun load(userId: String?): Config {
            Log.record(TAG, "开始加载配置")
            var configV2File: File? = null

            try {
                val userName: String
                if (userId.isNullOrEmpty()) {
                    configV2File = Files.getDefaultConfigV2File()
                    userName = "默认"
                    if (!configV2File.exists()) {
                        Log.record(TAG, "默认配置文件不存在，初始化新配置")
                        unload()
                        toSaveStr()?.let { Files.write2File(it, configV2File) }
                    }
                } else {
                    configV2File = Files.getConfigV2File(userId)
                    val userEntity = UserMap.get(userId)
                    userName = userEntity?.showName ?: userId
                }

                Log.record(TAG, "加载配置: $userName")
                val configV2FileExists = configV2File.exists()
                val defaultConfigV2FileExists = Files.getDefaultConfigV2File().exists()

                when {
                    configV2FileExists -> {
                        val json = Files.readFromFile(configV2File)
                        Log.runtime(TAG, "读取配置文件成功: ${configV2File.path}")
                        var preparedJson = json
                        try {
                            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(preparedJson, Config::class.java)
                        } catch (e: UnrecognizedPropertyException) {
                            Log.error(TAG, "配置文件中存在无法识别的字段: '${e.propertyName}'，将尝试移除并重新加载。")
                            try {
                                // 移除无法识别的字段并重新解析
                                val mapper = JsonUtil.copyMapper()
                                val rootNode = mapper.readTree(preparedJson)
                                (rootNode as ObjectNode).remove(e.propertyName)
                                preparedJson = mapper.writeValueAsString(rootNode)
                                mapper.readerForUpdating(INSTANCE).readValue(preparedJson, Config::class.java)
                                Log.error(TAG, "成功移除问题字段并加载配置。")
                                // 保存修复后的配置
                                toSaveStr()?.let { Files.write2File(it, configV2File) }
                                Log.error(TAG, "已保存修复后的配置文件。")
                            } catch (innerEx: Exception) {
                                Log.printStackTrace(TAG, "移除问题字段后，加载配置仍然失败。", innerEx)
                                throw innerEx // 抛出内部异常，触发重置逻辑
                            }
                        }
                        syncExtraFieldsFromJson(preparedJson)
                        Log.record(TAG, "格式化配置成功:$configV2File")
                        val formatted = toSaveStr()
                        if (formatted != null && formatted != json) {
                            Log.record(TAG, "格式化配置: $userName")
                            Files.write2File(formatted, configV2File)
                        }
                    }
                    defaultConfigV2FileExists -> {
                        val json = Files.readFromFile(Files.getDefaultConfigV2File())
                        val preparedJson = json
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(preparedJson, Config::class.java)
                        syncExtraFieldsFromJson(preparedJson)
                        Log.record(TAG, "复制新配置: $userName")
                        Files.write2File(toSaveStr() ?: preparedJson, configV2File)
                    }
                    else -> {
                        unload()
                        Log.record(TAG, "初始新配置: $userName")
                        toSaveStr()?.let { Files.write2File(it, configV2File) }
                    }
                }
            } catch (t: Throwable) {
                Log.error(TAG, "重置配置失败$t")
                Log.error(TAG, "重置配置")
                try {
                    unload()
                    configV2File?.let { file ->
                        toSaveStr()?.let { json -> Files.write2File(json, file) }
                    }
                } catch (e: Exception) {
                    Log.printStackTrace(TAG, "重置配置失败", e)
                }
            }

            INSTANCE.isInit = true
            val actualUserId = userId?.takeIf { it.isNotEmpty() } ?: UserMap.currentUid
            val invalidUserIds = UserMap.getUserMap().values
                .mapNotNull { user ->
                    val friendUserId = user.userId
                    if (
                        friendUserId.isNullOrBlank() ||
                        friendUserId == actualUserId ||
                        user.friendStatus == FriendGuard.MUTUAL_FRIEND_STATUS
                    ) {
                        null
                    } else {
                        friendUserId
                    }
                }
                .toSet()
            val removedInvalidCount = removeInvalidFriendSelections(
                invalidUserIds,
                actualUserId,
                autoSave = false
            )
            val removedSelfCount = removeSelfFriendSelections(actualUserId, autoSave = false)
            if (removedInvalidCount + removedSelfCount > 0) {
                save(userId, true)
            }
            TaskCommon.update()
            return INSTANCE
        }

        /**
         * 卸载当前配置
         */
        @JvmStatic
        @Synchronized
        fun unload() {
            for (modelFields in INSTANCE.modelFieldsMap.values) {
                for (modelField in modelFields.values) {
                    modelField?.reset()
                }
            }
            INSTANCE.legalAcceptedAppVersion = null
            INSTANCE.isInit = false
        }

        /**
         * 转换为保存字符串
         *
         * @return JSON字符串
         */
        @JvmStatic
        fun toSaveStr(): String? = JsonUtil.formatJson(INSTANCE)

        private fun syncExtraFieldsFromJson(json: String?) {
            val acceptedVersion = when {
                json.isNullOrBlank() -> null
                else -> {
                    val rootNode = JsonUtil.toNode(json)
                    if (rootNode?.has(LEGAL_ACCEPTED_APP_VERSION_FIELD) == true) {
                        rootNode.path(LEGAL_ACCEPTED_APP_VERSION_FIELD).asText("")
                    } else {
                        null
                    }
                }
            }
            INSTANCE.legalAcceptedAppVersion = acceptedVersion
        }

        private fun resolveReadableConfigFile(userId: String?): File? {
            val targetFile = if (userId.isNullOrEmpty()) {
                Files.getDefaultConfigV2File()
            } else {
                Files.getConfigV2File(userId)
            }
            if (targetFile.exists()) {
                return targetFile
            }
            if (!userId.isNullOrEmpty()) {
                val defaultFile = Files.getDefaultConfigV2File()
                if (defaultFile.exists()) {
                    return defaultFile
                }
            }
            return null
        }

        private fun readLegalAcceptedAppVersion(json: String?): String? {
            return when {
                json.isNullOrBlank() -> null
                else -> {
                    val rootNode = JsonUtil.toNode(json)
                    if (rootNode?.has(LEGAL_ACCEPTED_APP_VERSION_FIELD) == true) {
                        rootNode.path(LEGAL_ACCEPTED_APP_VERSION_FIELD).asText("")
                    } else {
                        null
                    }
                }
            }
        }

        private fun readBaseConfigJsonForLegalWrite(userId: String?, targetFile: File): String? {
            if (targetFile.exists()) {
                val targetJson = Files.readFromFile(targetFile)
                if (targetJson.isNotBlank()) {
                    return targetJson
                }
            }
            if (!userId.isNullOrEmpty()) {
                val defaultFile = Files.getDefaultConfigV2File()
                if (defaultFile.exists()) {
                    val defaultJson = Files.readFromFile(defaultFile)
                    if (defaultJson.isNotBlank()) {
                        return defaultJson
                    }
                }
            }
            return createConfigSkeletonForLegalWrite(userId)
        }

        private fun createConfigSkeletonForLegalWrite(userId: String?): String? {
            val previousJson = toSaveStr()
            val previousInit = INSTANCE.isInit

            return try {
                load(userId)
                toSaveStr()
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "初始化法律确认配置骨架失败", t)
                previousJson
            } finally {
                try {
                    if (!previousJson.isNullOrBlank()) {
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(previousJson, Config::class.java)
                        syncExtraFieldsFromJson(previousJson)
                    } else {
                        unload()
                    }
                } catch (restoreEx: Exception) {
                    Log.printStackTrace(TAG, "恢复 Config 单例状态失败", restoreEx)
                } finally {
                    INSTANCE.isInit = previousInit
                }
            }
        }

    }
}


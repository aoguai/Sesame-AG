package fansirsqi.xposed.sesame.task.antOcean

import com.fasterxml.jackson.core.type.TypeReference
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.AlipayBeach
import fansirsqi.xposed.sesame.entity.AlipayUser
import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.model.BaseModel
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.withDesc
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.TaskCommon
import fansirsqi.xposed.sesame.task.TaskStatus
import fansirsqi.xposed.sesame.task.antForest.AntForestRpcCall
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.BeachMap
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TaskBlacklist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Constanline
 * @since 2023/08/01
 */
class AntOcean : ModelTask() {

    /**
     * 申请动作枚举
     */
    enum class ApplyAction(val code: Int, val desc: String) {
        AVAILABLE(0, "可用"),
        NO_STOCK(1, "无库存"),
        ENERGY_LACK(2, "能量不足");

        companion object {
            /**
             * 根据字符串获取对应枚举
             */
            fun fromString(value: String): ApplyAction? {
                for (action in values()) {
                    if (action.name.equals(value, ignoreCase = true)) {
                        return action
                    }
                }
                Log.error("ApplyAction", "Unknown applyAction: $value")
                return null
            }
        }
    }

    /**
     * 保护类型接口常量
     */
    object ProtectType {
        const val DONT_PROTECT = 0
        const val PROTECT_ALL = 1
        const val PROTECT_BEACH = 2
        val nickNames = arrayOf("不保护", "保护全部", "仅保护沙滩")
    }

    /**
     * 清理类型接口常量
     */
    object CleanOceanType {
        const val CLEAN = 0
        const val DONT_CLEAN = 1
        val nickNames = arrayOf("选中清理", "选中不清理")
    }

    companion object {
        private const val TAG = "AntOcean"
        private const val HELP_CLEAN_LIMIT_FLAG = "Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT"
        
        /**
         * 保护类型字段（静态）
         */
        private var userprotectType: ChoiceModelField? = null
    }

    /**
     * 海洋任务
     */
    private var dailyOceanTask: BooleanModelField? = null

    /**
     * 清理 | 开启
     */
    private var cleanOcean: BooleanModelField? = null

    /**
     * 清理 | 动作
     */
    private var cleanOceanType: ChoiceModelField? = null

    /**
     * 清理 | 好友列表
     */
    private var cleanOceanList: SelectModelField? = null

    /**
     * 神奇海洋 | 制作万能拼图
     */
    private var exchangeProp: BooleanModelField? = null

    /**
     * 神奇海洋 | 使用万能拼图
     */
    private var usePropByType: BooleanModelField? = null

    /**
     * 保护 | 开启
     */
    private var protectOcean: BooleanModelField? = null

    /**
     * 保护 | 海洋列表
     */
    private var protectOceanList: SelectAndCountModelField? = null

    private var PDL_task: BooleanModelField? = null

    private val oceanTaskTryCount = ConcurrentHashMap<String, AtomicInteger>()
    private var currentOceanUserId: String? = null
    private var lastKnownRubbishNumber: Int = -1
    private var selfOceanCleanRetried = false

    override fun getName(): String {
        return "海洋"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.FOREST
    }

    override fun getIcon(): String {
        return "AntOcean.png"
    }

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField("dailyOceanTask", "海洋任务", false).withDesc(
                "完成并领取神奇海洋每日任务奖励，为清理和合成提供碎片。"
            ).also { dailyOceanTask = it }
        )
        modelFields.addField(
            BooleanModelField("cleanOcean", "清理 | 开启", false).withDesc(
                "执行清理自己和好友海域垃圾的主流程。"
            ).also { cleanOcean = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "cleanOceanType",
                "清理 | 动作",
                CleanOceanType.DONT_CLEAN,
                CleanOceanType.nickNames
            ).withDesc("决定列表中的好友是清理还是跳过。").also { cleanOceanType = it }
        )
        modelFields.addField(
            SelectModelField(
                "cleanOceanList",
                "清理 | 好友列表",
                LinkedHashSet(),
                AlipayUser::getListAsMapperEntity
            ).withDesc("配置要参与清理规则的好友列表。").also { cleanOceanList = it }
        )
        modelFields.addField(
            BooleanModelField("exchangeProp", "神奇海洋 | 制作万能拼图", false).withDesc(
                "把重复碎片制作成万能拼图。"
            ).also { exchangeProp = it }
        )
        modelFields.addField(
            BooleanModelField("usePropByType", "神奇海洋 | 使用万能拼图", false).withDesc(
                "在可合成目标鱼类时自动消耗万能拼图。"
            ).also { usePropByType = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "userprotectType",
                "保护 | 类型",
                ProtectType.DONT_PROTECT,
                ProtectType.nickNames
            ).withDesc("控制哪些海域或沙滩不参与自动推进。").also { userprotectType = it }
        )
        modelFields.addField(
            SelectAndCountModelField(
                "protectOceanList",
                "保护 | 海洋列表",
                LinkedHashMap(),
                AlipayBeach::getListAsMapperEntity
            ).withDesc("配置需要保护的海域列表及对应数量配置。").also { protectOceanList = it }
        )
        modelFields.addField(
            BooleanModelField("PDL_task", "潘多拉任务", false).withDesc(
                "执行潘多拉活动系列的独立任务与奖励领取。"
            ).also { PDL_task = it }
        )
        return modelFields
    }

    override fun check(): Boolean {
        return when {
            TaskCommon.IS_ENERGY_TIME -> {
                Log.record(
                    TAG,
                    "⏸ 当前为只收能量时间【" + BaseModel.energyTime.value + "】，停止执行" + getName() + "任务！"
                )
                false
            }
            TaskCommon.IS_MODULE_SLEEP_TIME -> {
                Log.record(
                    TAG,
                    "💤 模块休眠时间【" + BaseModel.modelSleepTime.value + "】停止执行" + getName() + "任务！"
                )
                false
            }
            else -> true
        }
    }

    override suspend fun runSuspend() {
        try {
            Log.record(TAG, "执行开始-" + getName())
            oceanTaskTryCount.clear()
            currentOceanUserId = null
            lastKnownRubbishNumber = -1
            selfOceanCleanRetried = false

            if (!queryOceanStatus()) {
                return
            }
            queryHomePage()

            if (dailyOceanTask?.value == true) {
                receiveTaskAward() // 日常任务
            }

            if (userprotectType?.value != ProtectType.DONT_PROTECT) {
                protectOcean() // 保护
            }

            // 制作万能碎片
            if (exchangeProp?.value == true) {
                exchangeProp()
            }

            // 使用万能拼图
            if (usePropByType?.value == true) {
                usePropByType()
            }

            if (PDL_task?.value == true) {
                doOceanPDLTask() // 潘多拉任务领取
            }
        } catch (e: CancellationException) {
            Log.runtime(TAG, "AntOcean 协程被取消")
            throw e
        } catch (t: Throwable) {
            Log.runtime(TAG, "start.run err:")
            Log.printStackTrace(TAG, t)
        } finally {
            Log.record(TAG, "执行结束-" + getName())
        }
    }

    /**
     * 初始化沙滩任务。
     * 通过调用 AntOceanRpc 接口查询养成列表，
     * 并将符合条件的任务加入 BeachMap。
     */
    fun initBeach() {
        try {
            val response = AntOceanRpcCall.queryCultivationList()
            val jsonResponse = JsonUtil.parseJSONObjectOrNull(response) ?: run {
                IdMapManager.getInstance(BeachMap::class.java).load()
                return
            }
            if (ResChecker.checkRes(TAG, jsonResponse)) {
                val cultivationList = jsonResponse.optJSONArray("cultivationItemVOList")
                if (cultivationList != null) {
                    for (i in 0 until cultivationList.length()) {
                        val item = cultivationList.getJSONObject(i)
                        val templateSubType = item.getString("templateSubType")
                        // 检查 applyAction 是否为 AVAILABLE
                        val actionStr = item.getString("applyAction")
                        val action = ApplyAction.fromString(actionStr)
                        if (action == ApplyAction.AVAILABLE) {
                            val templateCode = item.getString("templateCode") // 业务id
                            val cultivationName = item.getString("cultivationName")
                            val energy = item.getInt("energy")
                            when (userprotectType?.value) {
                                ProtectType.PROTECT_ALL -> {
                                    IdMapManager.getInstance(BeachMap::class.java)
                                        .add(templateCode, "$cultivationName(${energy}g)")
                                }
                                ProtectType.PROTECT_BEACH -> {
                                    if (templateSubType != "BEACH") {
                                        IdMapManager.getInstance(BeachMap::class.java)
                                            .add(templateCode, "$cultivationName(${energy}g)")
                                    }
                                }
                                else -> {
                                    // DONT_PROTECT 或其他，不做处理
                                }
                            }
                        }
                    }
                    Log.runtime(TAG, "初始化沙滩数据成功。")
                }
                // 将所有筛选结果保存到 BeachMap
                IdMapManager.getInstance(BeachMap::class.java).save()
            } else {
                Log.runtime(jsonResponse.optString("resultDesc", "未知错误"))
            }
        } catch (e: JSONException) {
            Log.printStackTrace(TAG, "JSON 解析错误：", e)
            IdMapManager.getInstance(BeachMap::class.java).load() // 若出现异常则加载保存的 BeachMap 备份
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "初始化沙滩任务时出错", e)
            IdMapManager.getInstance(BeachMap::class.java).load() // 加载保存的 BeachMap 备份
        }
    }

    private suspend fun queryOceanStatus(): Boolean {
        return try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.queryOceanStatus()) ?: return false
            if (ResChecker.checkRes(TAG, jo)) {
                if (!jo.getBoolean("opened")) {
                    enableField.setObjectValue(false)
                    Log.record("请先开启神奇海洋，并完成引导教程")
                    false
                } else {
                    initBeach()
                    true
                }
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryOceanStatus err:")
            Log.printStackTrace(TAG, t)
            false
        }
    }

    private suspend fun queryHomePageData(showTaskPanel: Boolean = false): JSONObject? {
        val joHomePage = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.queryHomePage(showTaskPanel)) ?: return null
        if (!ResChecker.checkRes(TAG, joHomePage)) {
            Log.runtime(TAG, extractOceanResultDesc(joHomePage))
            return null
        }
        return joHomePage
    }

    private fun rememberSelfOceanState(userInfoVO: JSONObject?) {
        if (userInfoVO == null) {
            return
        }
        val userId = userInfoVO.optString("userId")
        if (userId.isNotBlank()) {
            currentOceanUserId = userId
        }
        if (userInfoVO.has("rubbishNumber")) {
            lastKnownRubbishNumber = userInfoVO.optInt("rubbishNumber", 0).coerceAtLeast(0)
        }
    }

    private fun extractOceanResultDesc(jo: JSONObject): String {
        return jo.optString("resultDesc").ifBlank {
            jo.optString("memo").ifBlank {
                jo.optString("desc").ifBlank {
                    jo.optString("errorMsg").ifBlank {
                        jo.toString()
                    }
                }
            }
        }
    }

    private fun isHelpCleanLimit(jo: JSONObject): Boolean {
        val resultCode = jo.optString("resultCode").ifBlank { jo.optString("code") }
        val desc = extractOceanResultDesc(jo)
        return resultCode == "HELP_CLEAN_LIMIT" ||
            resultCode == "HELP_CLEAN_ALL_FRIEND_LIMIT" ||
            desc.contains("清理好友海域的次数已达上限") ||
            desc.contains("清理次数已达20次上限") ||
            desc.contains("已达20次上限")
    }

    private fun markHelpCleanLimit(jo: JSONObject) {
        if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
            return
        }
        Status.setFlagToday(HELP_CLEAN_LIMIT_FLAG)
        Log.record(TAG, "神奇海洋🌊帮助清理次数已达上限：${extractOceanResultDesc(jo)}，已记录为当日限制，本轮剩余好友清理全部跳过")
    }

    private fun isSelfCleanTask(taskType: String, taskTitle: String): Boolean {
        return taskType == "CLEAN_RUBBISH_2_EVERY_DAY" || taskTitle.contains("清理自己垃圾")
    }

    private fun isOceanActionTask(taskType: String, taskTitle: String): Boolean {
        return isSelfCleanTask(taskType, taskTitle) ||
            taskType.startsWith("CLEAN_") ||
            taskTitle.contains("清理好友") ||
            taskTitle.contains("清理海域")
    }

    private fun resolveOceanTaskFinishSource(taskType: String, taskTitle: String, bizInfo: JSONObject): String {
        val jumpUrl = bizInfo.optString("taskJumpUrl")
        val fallbackUrl = bizInfo.optString("fallbackUrl")
        val taskDesc = bizInfo.optString("taskDesc")
        val indicators = listOf(taskType, taskTitle, taskDesc, jumpUrl, fallbackUrl)
        return if (
            taskType.startsWith("BUSINESS_LIGHTS") ||
            taskType.contains("XLIGHT", ignoreCase = true) ||
            indicators.any { indicator ->
                indicator.contains("iepTaskType=", ignoreCase = true) ||
                    indicator.contains("renderConfigKey=", ignoreCase = true) ||
                    indicator.contains("adPosId=", ignoreCase = true)
            }
        ) {
            "ADBASICLIB"
        } else {
            "ANTFOCEAN"
        }
    }

    private fun resolveOceanTaskWaitSeconds(taskTitle: String, bizInfo: JSONObject): Int {
        val timeCount = bizInfo.optString("timeCount").trim()
        val parsedTimeCount = timeCount.toIntOrNull()
        if (parsedTimeCount != null && parsedTimeCount in 1..30) {
            return parsedTimeCount
        }

        val taskDesc = bizInfo.optString("taskDesc")
        val candidates = listOf(taskTitle, taskDesc)
        val secondsPattern = Regex("""(\d{1,2})\s*(?:s|秒)""", RegexOption.IGNORE_CASE)
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val match = secondsPattern.find(candidate) ?: continue
            val seconds = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            if (seconds in 1..30) {
                return seconds
            }
        }
        return 0
    }

    private fun isActiveExtraCollect(extraCollectVO: JSONObject?): Boolean {
        if (extraCollectVO == null) {
            return false
        }
        val status = extraCollectVO.optString("status")
        if (status.isBlank()) {
            return extraCollectVO.has("expireAt") || extraCollectVO.has("expireAtLeftDays")
        }
        return status != "FINISHED" &&
            status != "UNOPEN" &&
            status != "NOT_OPEN" &&
            status != "WAIT_OPEN" &&
            status != "TO_OPEN"
    }

    private suspend fun retrySelfOceanCleanIfNeeded(taskTitle: String): Boolean {
        if (selfOceanCleanRetried) {
            return false
        }
        val userId = currentOceanUserId
        if (userId.isNullOrBlank()) {
            return false
        }
        selfOceanCleanRetried = true
        val joHomePage = queryHomePageData(showTaskPanel = true) ?: return false
        val userInfoVO = joHomePage.optJSONObject("userInfoVO")
        rememberSelfOceanState(userInfoVO)
        val refreshedUserId = userInfoVO?.optString("userId").orEmpty().ifBlank { userId }
        val refreshedRubbishNumber = userInfoVO?.optInt("rubbishNumber", 0)?.coerceAtLeast(0) ?: 0
        if (refreshedRubbishNumber <= 0) {
            return false
        }
        Log.record(TAG, "神奇海洋🌊[$taskTitle]触发自清理补偿刷新：$refreshedRubbishNumber 次")
        cleanOcean(refreshedUserId, refreshedRubbishNumber)
        return true
    }

    private suspend fun queryHomePage() {
        try {
            val joHomePage = queryHomePageData(showTaskPanel = true) ?: return
            if (joHomePage.has("bubbleVOList")) {
                collectEnergy(joHomePage.getJSONArray("bubbleVOList"))
            }
            val userInfoVO = joHomePage.optJSONObject("userInfoVO")
            rememberSelfOceanState(userInfoVO)
            val rubbishNumber = lastKnownRubbishNumber.coerceAtLeast(0)
            val userId = currentOceanUserId.orEmpty()
            cleanOcean(userId, rubbishNumber)
            val ipVO = userInfoVO?.optJSONObject("ipVO")
            if (ipVO != null) {
                val surprisePieceNum = ipVO.optInt("surprisePieceNum", 0)
                if (surprisePieceNum > 0) {
                    ipOpenSurprise()
                }
            }
            queryMiscInfo()
            queryNotice()
            queryRefinedMaterial()
            queryReplicaHome()
            queryUserRanking() // 清理
            querySeaAreaDetailList()
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryHomePage err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun queryMiscInfo() {
        try {
            val s = AntOceanRpcCall.queryMiscInfo()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val miscHandlerVOMap = jo.optJSONObject("miscHandlerVOMap") ?: return
                val homeTipsRefresh = miscHandlerVOMap.optJSONObject("HOME_TIPS_REFRESH")
                val fishCanBeCombined = homeTipsRefresh?.optBoolean("fishCanBeCombined") == true
                val canBeRepaired = homeTipsRefresh?.optBoolean("canBeRepaired") == true
                val currentChapterFixed = homeTipsRefresh?.optBoolean("currentChapterFixed") == true
                if (fishCanBeCombined || canBeRepaired || currentChapterFixed) {
                    querySeaAreaDetailList()
                }
                val emergency = miscHandlerVOMap.optJSONObject("EMERGENCY")
                if (emergency?.optBoolean("showEmergency") == true || emergency?.optBoolean("showIntroduceBubble") == true) {
                    Log.record(TAG, "神奇海洋🌊[检测到海洋活动入口]")
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryMiscInfo err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun collectEnergy(bubbleVOList: JSONArray) {
        try {
            for (i in 0 until bubbleVOList.length()) {
                val bubble = bubbleVOList.getJSONObject(i)
                if ("ocean" != bubble.getString("channel")) {
                    continue
                }
                if ("AVAILABLE" == bubble.getString("collectStatus")) {
                    val bubbleId = bubble.getLong("id")
                    val userId = bubble.getString("userId")
                    val s = AntForestRpcCall.collectEnergy("", userId, bubbleId)
                    val jo = JsonUtil.parseJSONObjectOrNull(s) ?: continue
                    if (ResChecker.checkRes(TAG, jo)) {
                        val retBubbles = jo.optJSONArray("bubbles")
                        if (retBubbles != null) {
                            for (j in 0 until retBubbles.length()) {
                                val retBubble = retBubbles.optJSONObject(j)
                                if (retBubble != null) {
                                    val collectedEnergy = retBubble.getInt("collectedEnergy")
                                    Log.forest("神奇海洋🌊收取[${UserMap.getMaskName(userId)}]#${collectedEnergy}g")
                                    Toast.show("海洋能量🌊收取[${UserMap.getMaskName(userId)}]#${collectedEnergy}g")
                                }
                            }
                        }
                    } else {
                        Log.runtime(TAG, jo.getString("resultDesc"))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryHomePage err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun cleanOcean(userId: String, rubbishNumber: Int) {
        if (userId.isBlank() || rubbishNumber <= 0) {
            return
        }
        try {
            var cleanedTimes = 0
            for (i in 0 until rubbishNumber) {
                val s = AntOceanRpcCall.cleanOcean(userId)
                val jo = JsonUtil.parseJSONObjectOrNull(s) ?: continue
                if (ResChecker.checkRes(TAG, jo)) {
                    val cleanRewardVOS = jo.getJSONArray("cleanRewardVOS")
                    checkReward(cleanRewardVOS)
                    Log.forest("神奇海洋🌊[清理:${UserMap.getMaskName(userId)}海域]")
                    cleanedTimes += 1
                } else {
                    Log.runtime(TAG, extractOceanResultDesc(jo))
                    break
                }
            }
            if (cleanedTimes > 0 && lastKnownRubbishNumber >= 0) {
                lastKnownRubbishNumber = (lastKnownRubbishNumber - cleanedTimes).coerceAtLeast(0)
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "cleanOcean err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun ipOpenSurprise() {
        try {
            val s = AntOceanRpcCall.ipOpenSurprise()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val rewardVOS = jo.getJSONArray("surpriseRewardVOS")
                checkReward(rewardVOS)
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "ipOpenSurprise err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun combineFish(fishId: String) {
        try {
            val s = AntOceanRpcCall.combineFish(fishId)
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val fishDetailVO = jo.getJSONObject("fishDetailVO")
                val name = fishDetailVO.getString("name")
                Log.forest("神奇海洋🌊[$name]合成成功")
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "combineFish err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun checkReward(rewards: JSONArray) {
        try {
            for (i in 0 until rewards.length()) {
                val reward = rewards.getJSONObject(i)
                val name = reward.getString("name")
                val attachReward = reward.getJSONArray("attachRewardBOList")
                if (attachReward.length() > 0) {
                    Log.forest("神奇海洋🌊[获得:" + name + "碎片]")
                    var canCombine = true
                    for (j in 0 until attachReward.length()) {
                        val detail = attachReward.getJSONObject(j)
                        if (detail.optInt("count", 0) == 0) {
                            canCombine = false
                            break
                        }
                    }
                    if (canCombine && shouldCombineFish(reward)) {
                        val fishId = reward.opt("id")?.toString().orEmpty()
                        if (fishId.isBlank()) {
                            continue
                        }
                        combineFish(fishId)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "checkReward err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun collectReplicaAsset(canCollectAssetNum: Int) {
        try {
            for (i in 0 until canCollectAssetNum) {
                val s = AntOceanRpcCall.collectReplicaAsset()
                val jo = JsonUtil.parseJSONObjectOrNull(s) ?: continue
                if (ResChecker.checkRes(TAG, jo)) {
                    Log.forest("神奇海洋🌊[学习海洋科普知识]#潘多拉能量+1")
                } else {
                    Log.runtime(TAG, jo.getString("resultDesc"))
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "collectReplicaAsset err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun unLockReplicaPhase(replicaCode: String, replicaPhaseCode: String) {
        try {
            val s = AntOceanRpcCall.unLockReplicaPhase(replicaCode, replicaPhaseCode)
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val name = jo.getJSONObject("currentPhaseInfo").getJSONObject("extInfo").getString("name")
                Log.forest("神奇海洋🌊迎回[$name]")
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "unLockReplicaPhase err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun queryReplicaHome() {
        try {
            val s = AntOceanRpcCall.queryReplicaHome()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                if (jo.has("userReplicaAssetVO")) {
                    val userReplicaAssetVO = jo.getJSONObject("userReplicaAssetVO")
                    val canCollectAssetNum = userReplicaAssetVO.getInt("canCollectAssetNum")
                    collectReplicaAsset(canCollectAssetNum)
                }
                if (jo.has("userCurrentPhaseVO")) {
                    val userCurrentPhaseVO = jo.getJSONObject("userCurrentPhaseVO")
                    val phaseCode = userCurrentPhaseVO.getString("phaseCode")
                    val code = jo.getJSONObject("userReplicaInfoVO").getString("code")
                    if ("COMPLETED" == userCurrentPhaseVO.getString("phaseStatus")) {
                        unLockReplicaPhase(code, phaseCode)
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryReplicaHome err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun queryOceanPropList() {
        try {
            val s = AntOceanRpcCall.queryOceanPropList()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                repairSeaArea()
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryOceanPropList err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun repairSeaArea() {
        try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.repairSeaArea()) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val seaAreaName = jo.optJSONObject("currentSeaAreaVO")?.optString("name")
                    ?: jo.optJSONObject("seaAreaVO")?.optString("name")
                    ?: jo.optJSONObject("seaAreaInfoVO")?.optString("name")
                    ?: jo.optString("seaAreaName")
                if (seaAreaName.isNotBlank()) {
                    Log.forest("神奇海洋🌊[开启修复:$seaAreaName]")
                } else {
                    Log.forest("神奇海洋🌊[开启新海域修复]")
                }
            } else {
                Log.runtime(TAG, jo.optString("resultDesc").ifBlank { jo.toString() })
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "repairSeaArea err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun createSeaAreaExtraCollect() {
        try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.createSeaAreaExtraCollect()) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val extraCollectVO = jo.optJSONObject("seaAreaExtraCollectVO")
                val extraCollectName = extraCollectVO?.optJSONObject("seaAreaVO")?.optString("name")
                    ?: extraCollectVO?.optString("name")
                    ?: "限时挑战"
                Log.forest("神奇海洋🌊[开启限时挑战:$extraCollectName]")
            } else {
                Log.runtime(TAG, jo.optString("resultDesc").ifBlank { jo.toString() })
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "createSeaAreaExtraCollect err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun switchOceanChapter(hasOpenedUnfinishedExtraCollect: Boolean = false) {
        if (hasOpenedUnfinishedExtraCollect) {
            return
        }
        val s = AntOceanRpcCall.queryOceanChapterList()
        try {
            var jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val currentChapterCode = jo.getString("currentChapterCode")
                val chapterVOs = jo.getJSONArray("userChapterDetailVOList")
                var isFinish = false
                var hasReachedCurrentChapter = false
                var dstChapterCode = ""
                var dstChapterName = ""
                for (i in 0 until chapterVOs.length()) {
                    val chapterVO = chapterVOs.getJSONObject(i)
                    val repairedSeaAreaNum = chapterVO.getInt("repairedSeaAreaNum")
                    val seaAreaNum = chapterVO.getInt("seaAreaNum")
                    if (chapterVO.getString("chapterCode") == currentChapterCode) {
                        isFinish = repairedSeaAreaNum >= seaAreaNum
                        hasReachedCurrentChapter = true
                    } else {
                        if (repairedSeaAreaNum >= seaAreaNum || !chapterVO.getBoolean("chapterOpen")) {
                            continue
                        }
                        if (!hasReachedCurrentChapter) {
                            continue
                        }
                        dstChapterName = chapterVO.getString("chapterName")
                        dstChapterCode = chapterVO.getString("chapterCode")
                        break
                    }
                }
                if (isFinish && dstChapterCode.isNotEmpty()) {
                    val switchS = AntOceanRpcCall.switchOceanChapter(dstChapterCode)
                    jo = JsonUtil.parseJSONObjectOrNull(switchS) ?: return
                    if (ResChecker.checkRes(TAG, jo)) {
                        Log.forest("神奇海洋🌊切换到[$dstChapterName]系列")
                    } else {
                        Log.runtime(TAG, jo.getString("resultDesc"))
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryUserRanking err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun querySeaAreaDetailList() {
        try {
            val s = AntOceanRpcCall.querySeaAreaDetailList()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                var hasOpenedUnfinishedExtraCollect = false
                if (jo.optBoolean("awardSeaAreaCanCreateExtraCollect", false)) {
                    createSeaAreaExtraCollect()
                    hasOpenedUnfinishedExtraCollect = true
                }
                val seaAreaNum = jo.getInt("seaAreaNum")
                val fixSeaAreaNum = jo.getInt("fixSeaAreaNum")
                val currentSeaAreaIndex = jo.getInt("currentSeaAreaIndex")
                if (!hasOpenedUnfinishedExtraCollect && currentSeaAreaIndex < fixSeaAreaNum && seaAreaNum > fixSeaAreaNum) {
                    queryOceanPropList()
                }
                val seaAreaVOs = jo.optJSONArray("seaAreaVOs") ?: return
                for (i in 0 until seaAreaVOs.length()) {
                    val seaAreaVO = seaAreaVOs.optJSONObject(i) ?: continue
                    combineCompletedFish(seaAreaVO.optJSONArray("fishVO") ?: seaAreaVO.optJSONArray("fishVOList"))
                    val seaAreaExtraCollectVO = seaAreaVO.optJSONObject("seaAreaExtraCollectVO")
                    if (seaAreaExtraCollectVO != null) {
                        if (isActiveExtraCollect(seaAreaExtraCollectVO)) {
                            hasOpenedUnfinishedExtraCollect = true
                        }
                        combineCompletedFish(
                            seaAreaExtraCollectVO.optJSONArray("fishVO")
                                ?: seaAreaExtraCollectVO.optJSONArray("fishVOList")
                        )
                    }
                }
                var hasWaitForUnlockSeaArea = false
                for (i in 0 until seaAreaVOs.length()) {
                    if (seaAreaVOs.optJSONObject(i)?.optString("status") == "WAIT_FOR_UNLOCK") {
                        hasWaitForUnlockSeaArea = true
                        break
                    }
                }
                if (!hasOpenedUnfinishedExtraCollect && hasWaitForUnlockSeaArea) {
                    repairSeaArea()
                }
                switchOceanChapter(hasOpenedUnfinishedExtraCollect)
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "querySeaAreaDetailList err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun combineCompletedFish(fishVOs: JSONArray?) {
        if (fishVOs == null) {
            return
        }
        for (j in 0 until fishVOs.length()) {
            val fishVO = fishVOs.optJSONObject(j) ?: continue
            if (!shouldCombineFish(fishVO)) {
                continue
            }
            val fishId = fishVO.opt("id")?.toString().orEmpty()
            if (fishId.isNotBlank()) {
                combineFish(fishId)
            }
        }
    }

    private fun shouldCombineFish(fish: JSONObject): Boolean {
        if (fish.optBoolean("unlock", false)) {
            return false
        }
        if (fish.optBoolean("isCombine", false)) {
            return true
        }

        val status = fish.optString("status")
        if (
            status.equals("COMPLETED", ignoreCase = true) ||
            status.equals("CAN_COMBINE", ignoreCase = true) ||
            status.equals("COMBINABLE", ignoreCase = true)
        ) {
            return true
        }

        val pieces = fish.optJSONArray("pieces")
        if (pieces != null && pieces.length() > 0) {
            var completedPieceCount = 0
            for (i in 0 until pieces.length()) {
                if (pieces.optJSONObject(i)?.optInt("num", 0) ?: 0 > 0) {
                    completedPieceCount++
                }
            }
            if (completedPieceCount >= pieces.length()) {
                return true
            }
            val obtainedPieces = fish.optInt("obtainedPieces", -1)
            if (obtainedPieces >= pieces.length()) {
                return true
            }
        }

        val attachRewardBOList = fish.optJSONArray("attachRewardBOList")
        if (attachRewardBOList != null && attachRewardBOList.length() > 0) {
            for (i in 0 until attachRewardBOList.length()) {
                if (attachRewardBOList.optJSONObject(i)?.optInt("count", 0) ?: 0 <= 0) {
                    return false
                }
            }
            return true
        }
        return false
    }

    @Suppress("ReturnCount")
    private suspend fun cleanFriendOcean(fillFlag: JSONObject) {
        if (!fillFlag.optBoolean("canClean")) {
            return
        }
        if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
            return
        }
        try {
            val userId = fillFlag.getString("userId")
            var isOceanClean = cleanOceanList?.value?.contains(userId) == true
            if (cleanOceanType?.value == CleanOceanType.DONT_CLEAN) {
                isOceanClean = !isOceanClean
            }
            if (!isOceanClean) {
                return
            }
            var s = AntOceanRpcCall.queryFriendPage(userId)
            var jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
                    return
                }
                s = AntOceanRpcCall.cleanFriendOcean(userId)
                jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
                if (ResChecker.checkRes(TAG, jo)) {
                    val maskName = UserMap.getMaskName(userId) ?: userId
                    Log.forest("神奇海洋🌊[帮助:${maskName}清理海域]")
                    val cleanRewardVOS = jo.getJSONArray("cleanRewardVOS")
                    checkReward(cleanRewardVOS)
                } else {
                    if (isHelpCleanLimit(jo)) {
                        markHelpCleanLimit(jo)
                    } else {
                        Log.runtime(TAG, extractOceanResultDesc(jo))
                    }
                }
            } else {
                if (isHelpCleanLimit(jo)) {
                    markHelpCleanLimit(jo)
                } else {
                    Log.runtime(TAG, extractOceanResultDesc(jo))
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "cleanFriendOcean err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun fillUserFlagAndClean(userIds: List<String>) {
        if (userIds.isEmpty()) return
        if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) return

        val ja = JSONArray()
        for (id in userIds) {
            if (id.isNotBlank()) ja.put(id)
        }
        if (ja.length() == 0) return

        val s = AntOceanRpcCall.fillUserFlag(ja)
        val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
        if (!ResChecker.checkRes(TAG, jo)) {
            if (isHelpCleanLimit(jo)) {
                markHelpCleanLimit(jo)
            } else {
                Log.runtime(TAG, extractOceanResultDesc(jo))
            }
            return
        }

        val fillFlagVOList = jo.optJSONArray("fillFlagVOList") ?: return
        for (i in 0 until fillFlagVOList.length()) {
            if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) return
            cleanFriendOcean(fillFlagVOList.getJSONObject(i))
        }
    }

    private suspend fun queryUserRanking() {
        try {
            if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
                return
            }
            val s = AntOceanRpcCall.queryUserRanking()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (!ResChecker.checkRes(TAG, jo)) {
                if (isHelpCleanLimit(jo)) {
                    markHelpCleanLimit(jo)
                } else {
                    Log.runtime(TAG, extractOceanResultDesc(jo))
                }
                return
            }

            // 未开启清理功能时，无需额外请求 fillUserFlag
            if (cleanOcean?.value != true) {
                return
            }

            // 1) 先处理首屏 fillFlagVOList（通常为 20 个）
            val firstFillFlags = jo.optJSONArray("fillFlagVOList")
            if (firstFillFlags != null) {
                for (i in 0 until firstFillFlags.length()) {
                    cleanFriendOcean(firstFillFlags.getJSONObject(i))
                    if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) return
                }
            }

            // 2) 扩展处理：若首屏无可清理，继续从 allRankingList 取后续用户并通过 fillUserFlag 补全 canClean
            val allRankingList = jo.optJSONArray("allRankingList") ?: return
            val pageSize = jo.optInt("pageSize", 20).takeIf { it in 1..50 } ?: 20

            var pos = pageSize
            val idList = ArrayList<String>(pageSize)
            val currentUid = UserMap.currentUid

            while (pos < allRankingList.length() && !Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
                val friend = allRankingList.optJSONObject(pos)
                val userId = friend?.optString("userId").orEmpty()
                if (userId.isNotBlank() && userId != currentUid) {
                    idList.add(userId)
                }

                pos++
                if (idList.size >= pageSize) {
                    fillUserFlagAndClean(idList)
                    idList.clear()
                }
            }

            if (idList.isNotEmpty() && !Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG)) {
                fillUserFlagAndClean(idList)
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryUserRanking err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun receiveTaskAward() {
        try {
            val presetBad = LinkedHashSet(listOf("DEMO", "DEMO1"))
            val badTaskSetSchemaVersion = 2
            val badTaskSetSchemaKey = "badOceanTaskSetSchemaVersion"
            val typeRef = object : TypeReference<MutableSet<String>>() {}
            var badTaskSet = DataStore.getOrCreate("badOceanTaskSet", typeRef)
            val storedSchemaVersion = DataStore.getOrCreate<Int>(badTaskSetSchemaKey)
            if (storedSchemaVersion != badTaskSetSchemaVersion) {
                badTaskSet.clear()
                badTaskSet.addAll(presetBad)
                DataStore.put("badOceanTaskSet", badTaskSet)
                DataStore.put(badTaskSetSchemaKey, badTaskSetSchemaVersion)
            }
            if (badTaskSet.isEmpty()) {
                badTaskSet.addAll(presetBad)
                DataStore.put("badOceanTaskSet", badTaskSet)
            }
                        while (currentCoroutineContext().isActive) {
                var done = false
                val s = AntOceanRpcCall.queryTaskList()
                val jo = JsonUtil.parseJSONObjectOrNull(s) ?: break
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.record(TAG, "查询任务列表失败：" + jo.getString("resultDesc"))
                    break
                }
                val jaTaskList = jo.optJSONArray("antOceanTaskVOList") ?: break
                for (i in 0 until jaTaskList.length()) {
                    val task = jaTaskList.getJSONObject(i)
                    val bizInfo = when (val rawBizInfo = task.opt("bizInfo")) {
                        is JSONObject -> rawBizInfo
                        is String -> JsonUtil.parseJSONObjectOrNull(rawBizInfo) ?: JSONObject()
                        else -> JSONObject()
                    }
                    val taskTitle = bizInfo.optString("taskTitle", task.optString("taskType"))
                    val awardCount = bizInfo.optString("awardCount", task.optString("awardCount", "0"))
                    val sceneCode = task.getString("sceneCode")
                    val taskType = task.getString("taskType")
                    val taskStatus = task.getString("taskStatus")
                    // 在处理任何任务前，先检查黑名单
                    if (badTaskSet.contains(taskTitle) ||
                        badTaskSet.contains(taskType) ||
                        TaskBlacklist.isTaskInBlacklist(taskTitle) ||
                        TaskBlacklist.isTaskInBlacklist(taskType)
                    ) {
                        Log.record(TAG, "海洋任务🌊[$taskTitle]已在黑名单中，跳过处理")
                        continue
                    }
                    if (Status.hasFlagToday(HELP_CLEAN_LIMIT_FLAG) &&
                        (taskTitle.contains("帮好友清理") || taskType.contains("HELP_CLEAN"))
                    ) {
                        Log.record(TAG, "海洋任务🌊[$taskTitle]帮助清理次数已达上限，跳过处理")
                        continue
                    }

                    if (!isRewardReadyStatus(taskStatus) && isSelfCleanTask(taskType, taskTitle)) {
                        if (retrySelfOceanCleanIfNeeded(taskTitle)) {
                            done = true
                        }
                        continue
                    }

                    if (!isRewardReadyStatus(taskStatus) && isOceanActionTask(taskType, taskTitle)) {
                        continue
                    }

                    if (isRewardReadyStatus(taskStatus)) {
                        val awardResponse = AntOceanRpcCall.receiveTaskAward(sceneCode, taskType)
                        val joAward = JsonUtil.parseJSONObjectOrNull(awardResponse) ?: continue
                        if (ResChecker.checkRes(TAG, joAward)) {
                            Log.forest("海洋奖励🌊[" + taskTitle + "]# " + awardCount + "拼图")
                            done = true
                        } else {
                            Log.error(TAG, "海洋奖励🌊领取失败：$joAward")
                        }
                    } else if (TaskStatus.TODO.name == taskStatus) {
                        if (taskTitle.contains("答题")) {
                            if (answerQuestion()) {
                                done = true
                            }
                        } else {
                            val waitSeconds = resolveOceanTaskWaitSeconds(taskTitle, bizInfo)
                            if (waitSeconds > 0) {
                                Log.record(TAG, "海洋任务🌊[$taskTitle]需等待${waitSeconds}s，跳过处理")
                                continue
                            }

                            val bizKey = "${sceneCode}_$taskType"
                            val count = oceanTaskTryCount.computeIfAbsent(bizKey) { AtomicInteger(0) }
                                .incrementAndGet()

                            val finishSource = resolveOceanTaskFinishSource(taskType, taskTitle, bizInfo)
                            val fallbackSource = if (finishSource == "ADBASICLIB") "ANTFOCEAN" else "ADBASICLIB"
                            val sourcesToTry = listOf(finishSource, fallbackSource).distinct()
                            var joFinishTask: JSONObject? = null
                            var finishOk = false
                            var notSupportedCount = 0

                            for (source in sourcesToTry) {
                                val finishResponse = AntOceanRpcCall.finishTask(sceneCode, taskType, source)
                                val parsed = JsonUtil.parseJSONObjectOrNull(finishResponse) ?: continue
                                joFinishTask = parsed

                                // 检查特定错误码：不支持RPC完成的任务，仅在两种 source 都不支持时再加入黑名单
                                val errorCode = parsed.optString("code", "")
                                val desc = parsed.optString("desc", "")
                                if (errorCode == "400000040" || desc.contains("不支持RPC完成")) {
                                    notSupportedCount++
                                    continue
                                }

                                if (ResChecker.checkRes(TAG, parsed)) {
                                    finishOk = true
                                    break
                                }
                            }

                            if (!finishOk && notSupportedCount >= sourcesToTry.size) {
                                Log.error(TAG, "海洋任务🌊[$taskTitle]不支持RPC完成，已加入黑名单")
                                badTaskSet.add(taskTitle)
                                badTaskSet.add(taskType)
                                DataStore.put("badOceanTaskSet", badTaskSet)
                                continue
                            }

                            val finishResult = joFinishTask ?: continue
                            if (finishOk) {
                                oceanTaskTryCount.remove(bizKey)
                                Log.forest("海洋任务🌊完成[$taskTitle]")
                                done = true
                            } else {
                                Log.error(TAG, "海洋任务🌊完成失败：$finishResult")
                                if (count > 1) {
                                    badTaskSet.add(taskTitle)
                                    badTaskSet.add(taskType)
                                    DataStore.put("badOceanTaskSet", badTaskSet)
                                }
                            }
                        }

                        delay(500)
                    }
                }
                if (!done) break
            }
        } catch (e: JSONException) {
            Log.runtime(TAG, "JSON解析错误: " + (e.message ?: ""))
            Log.printStackTrace(TAG, e)
        } catch (t: Throwable) {
            Log.runtime(TAG, "receiveTaskAward err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun isRewardReadyStatus(taskStatus: String): Boolean {
        return taskStatus == TaskStatus.FINISHED.name ||
            taskStatus == "COMPLETE" ||
            taskStatus == "WAIT_RECEIVE"
    }

    private fun parseJSONObject(value: Any?): JSONObject? {
        return when (value) {
            is JSONObject -> value
            is String -> JsonUtil.parseJSONObjectOrNull(value)
            else -> null
        }
    }

    private suspend fun queryNotice() {
        try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.queryNotice()) ?: return
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            val noticeInfoList = jo.optJSONArray("noticeInfoList") ?: return
            var needQueryPopup = false
            for (index in 0 until noticeInfoList.length()) {
                val noticeInfo = noticeInfoList.optJSONObject(index) ?: continue
                val noticeType = noticeInfo.optString("noticeType")
                val haveNotice = noticeInfo.optBoolean("haveNotice")
                val extendInfo = parseJSONObject(noticeInfo.opt("extendInfo"))
                when (noticeType) {
                    "INDEX_TASK_NOTICE" -> {
                        val todoTaskNum = extendInfo?.optInt("todoTaskNum", 0) ?: 0
                        val taskCanReceiveRewardNum = extendInfo?.optInt("taskCanReceiveRewardNum", 0) ?: 0
                        if (haveNotice || todoTaskNum > 0 || taskCanReceiveRewardNum > 0) {
                            Log.record(TAG, "海洋任务🌊[待完成:$todoTaskNum,待领取:$taskCanReceiveRewardNum]")
                        }
                    }

                    "CULTIVATION_LIST_ENTRANCE" -> {
                        if (haveNotice) {
                            needQueryPopup = true
                            Log.record(TAG, "神奇海洋🌊[检测到海洋活动入口]")
                        }
                    }

                    "INDEX_GAME_ENTRY_NOTICE" -> {
                        if (haveNotice) {
                            val todoTaskNum = extendInfo?.optInt("todoTaskNum", 0) ?: 0
                            Log.record(TAG, "海洋任务🌊[游戏入口待处理:$todoTaskNum]")
                            needQueryPopup = true
                        }
                    }

                    "INTERACT_RECEIVE_PIECE" -> {
                        if (haveNotice) {
                            Log.record(TAG, "海洋拼图🌊[存在可领取互动拼图]")
                        }
                    }
                }
            }
            if (needQueryPopup) {
                queryPopupWin()
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryNotice err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun queryPopupWin() {
        try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.popupWin()) ?: return
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            val refinedOpsVOList = jo.optJSONArray("refinedOpsVOList") ?: return
            for (index in 0 until refinedOpsVOList.length()) {
                val item = refinedOpsVOList.optJSONObject(index) ?: continue
                val renderValue = item.optJSONObject("renderValue")
                val jumpUrl = renderValue?.optString("jumpUrl").orEmpty()
                if (jumpUrl.contains("cultivationDetail") || jumpUrl.contains("projectCode")) {
                    Log.forest("神奇海洋🌊[检测到海洋活动入口]")
                    return
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryPopupWin err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun queryRefinedMaterial() {
        try {
            val jo = JsonUtil.parseJSONObjectOrNull(AntOceanRpcCall.queryRefinedMaterial()) ?: return
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            val userDynamicsVOList = jo.optJSONArray("userDynamicsVOList") ?: return
            if (userDynamicsVOList.length() > 0) {
                Log.record(TAG, "海洋动态🌊[检测到${userDynamicsVOList.length()}条精炼物料动态]")
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryRefinedMaterial err:")
            Log.printStackTrace(TAG, t)
        }
    }

    // 海洋答题任务
    private suspend fun answerQuestion(): Boolean {
        try {
            val questionResponse = AntOceanRpcCall.getQuestion()
            val questionJson = JsonUtil.parseJSONObjectOrNull(questionResponse) ?: return false
            if (questionJson.getBoolean("answered")) {
                Log.runtime(TAG, "问题已经被回答过，跳过答题流程")
                return false
            }
            if (questionJson.getInt("resultCode") == 200) {
                val questionId = questionJson.getString("questionId")
                val options = questionJson.getJSONArray("options")
                val answer = options.getString(0)
                val submitResponse = AntOceanRpcCall.submitAnswer(answer, questionId)
                val submitJson = JsonUtil.parseJSONObjectOrNull(submitResponse) ?: return false
                if (submitJson.getInt("resultCode") == 200) {
                    Log.forest(TAG, "🌊海洋答题成功")
                    return true
                } else {
                    Log.error(TAG, "海洋答题失败：$submitJson")
                }
            } else {
                Log.error(TAG, "海洋获取问题失败：$questionJson")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "海洋答题错误", t)
        }
        return false
    }

    /**
     * 潘多拉海洋任务领取
     */
    private suspend fun doOceanPDLTask() {
        try {
            Log.runtime(TAG, "执行潘多拉海域任务")
            val homeResponse = AntOceanRpcCall.PDLqueryReplicaHome()
            val homeJson = JsonUtil.parseJSONObjectOrNull(homeResponse) ?: return
            if (ResChecker.checkRes(TAG, homeJson)) {
                val taskListResponse = AntOceanRpcCall.PDLqueryTaskList()
                val taskListJson = JsonUtil.parseJSONObjectOrNull(taskListResponse) ?: return
                val antOceanTaskVOList = taskListJson.optJSONArray("antOceanTaskVOList") ?: return
                for (i in 0 until antOceanTaskVOList.length()) {
                    val task = antOceanTaskVOList.optJSONObject(i) ?: continue
                    val taskStatus = task.optString("taskStatus")
                    if (isRewardReadyStatus(taskStatus)) {
                        val bizInfo = parseJSONObject(task.opt("bizInfo")) ?: JSONObject()
                        val taskTitle = bizInfo.optString("taskTitle", task.optString("taskType"))
                        val awardCount = bizInfo.optString("awardCount", task.optString("awardCount", "0"))
                        val taskType = task.getString("taskType")
                        val receiveTaskResponse = AntOceanRpcCall.PDLreceiveTaskAward(taskType)
                        val receiveTaskJson = JsonUtil.parseJSONObjectOrNull(receiveTaskResponse) ?: continue
                        if (ResChecker.checkRes(TAG, receiveTaskJson) || receiveTaskJson.optInt("code") == 100000000) {
                            Log.forest("海洋奖励🌊[领取:$taskTitle]获得潘多拉能量x$awardCount")
                        } else {
                            val message = receiveTaskJson.optString("message")
                                .ifBlank { receiveTaskJson.optString("resultDesc") }
                                .ifBlank { "领取任务奖励失败，未返回错误信息" }
                            Log.record(TAG, "领取任务奖励失败: $message")
                        }
                    }
                }
            } else {
                Log.record(TAG, "PDLqueryReplicaHome调用失败: ${homeJson.optString("message")}")
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "doOceanPDLTask err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun protectOcean() {
        try {
            val s = AntOceanRpcCall.queryCultivationList()
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return
            if (ResChecker.checkRes(TAG, jo)) {
                val ja = jo.getJSONArray("cultivationItemVOList")
                for (i in 0 until ja.length()) {
                    val item = ja.getJSONObject(i)
                    val templateSubType = item.getString("templateSubType")
                    val applyAction = item.getString("applyAction")
                    val cultivationName = item.getString("cultivationName")
                    val templateCode = item.getString("templateCode")
                    val projectConfig = item.getJSONObject("projectConfigVO")
                    val projectCode = projectConfig.getString("code")
                    val map = protectOceanList?.value ?: continue
                    for (entry in map.entries) {
                        if (entry.key == templateCode) {
                            val count = entry.value
                            if (count != null && count > 0) {
                                oceanExchangeTree(templateCode, projectCode, cultivationName, count)
                            }
                            break
                        }
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "protectBeach err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private suspend fun oceanExchangeTree(
        cultivationCode: String,
        projectCode: String,
        itemName: String,
        count: Int
    ) {
        try {
            var appliedTimes = queryCultivationDetail(cultivationCode, projectCode, count)
            if (appliedTimes < 0) return

            for (applyCount in 1..count) {
                val s = AntOceanRpcCall.oceanExchangeTree(cultivationCode, projectCode)
                val jo = JsonUtil.parseJSONObjectOrNull(s) ?: break
                if (ResChecker.checkRes(TAG, jo)) {
                    val awardInfos = jo.getJSONArray("rewardItemVOs")
                    val award = StringBuilder()
                    for (i in 0 until awardInfos.length()) {
                        val awardItem = awardInfos.getJSONObject(i)
                        award.append(awardItem.getString("name")).append("*").append(awardItem.getInt("num"))
                    }
                    val str = "保护海洋生态🏖️[$itemName]#第${appliedTimes}次-获得奖励$award"
                    Log.forest(str)
                    delay(300)
                } else {
                    Log.error("保护海洋生态🏖️[$itemName]#发生未知错误，停止申请")
                    break
                }
                appliedTimes = queryCultivationDetail(cultivationCode, projectCode, count)
                if (appliedTimes < 0) {
                    break
                } else {
                    delay(300)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "海洋保护错误:", t)
        }
    }

    private suspend fun queryCultivationDetail(
        cultivationCode: String,
        projectCode: String,
        count: Int
    ): Int {
        var appliedTimes = -1
        try {
            val s = AntOceanRpcCall.queryCultivationDetail(cultivationCode, projectCode)
            val jo = JsonUtil.parseJSONObjectOrNull(s) ?: return appliedTimes
            if (ResChecker.checkRes(TAG, jo)) {
                val userInfo = jo.getJSONObject("userInfoVO")
                val currentEnergy = userInfo.getInt("currentEnergy")
                val cultivationDetailVO = jo.getJSONObject("cultivationDetailVO")
                val applyAction = cultivationDetailVO.getString("applyAction")
                val certNum = cultivationDetailVO.getInt("certNum")
                if ("AVAILABLE" == applyAction) {
                    if (currentEnergy >= cultivationDetailVO.getInt("energy")) {
                        if (certNum < count) {
                            appliedTimes = certNum + 1
                        }
                    } else {
                        Log.forest("保护海洋🏖️[${cultivationDetailVO.getString("cultivationName")}]#能量不足停止申请")
                    }
                } else {
                    Log.forest("保护海洋🏖️[${cultivationDetailVO.getString("cultivationName")}]#似乎没有了")
                }
            } else {
                Log.record(jo.getString("resultDesc"))
                Log.runtime(s)
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "queryCultivationDetail err:")
            Log.printStackTrace(TAG, t)
        }
        return appliedTimes
    }

    // 制作万能碎片
    private suspend fun exchangeProp() {
        try {
            var shouldContinue = true
            while (shouldContinue) {
                // 获取道具兑换列表的JSON数据
                val propListJson = AntOceanRpcCall.exchangePropList()
                val propListObj = JsonUtil.parseJSONObjectOrNull(propListJson) ?: return
                // 检查是否成功获取道具列表
                if (ResChecker.checkRes(TAG, propListObj)) {
                    // 获取道具重复数量
                    val duplicatePieceNum = propListObj.getInt("duplicatePieceNum")
                    // 如果道具重复数量小于10，直接返回并停止循环
                    if (duplicatePieceNum < 10) {
                        return
                    }
                    // 如果道具重复数量大于等于10，则执行道具兑换操作
                    val exchangeResultJson = AntOceanRpcCall.exchangeProp()
                    val exchangeResultObj = JsonUtil.parseJSONObjectOrNull(exchangeResultJson) ?: return
                    // 获取兑换后的碎片数量和兑换数量
                    val exchangedPieceNum = exchangeResultObj.getString("duplicatePieceNum")
                    val exchangeNum = exchangeResultObj.getString("exchangeNum")
                    // 检查道具兑换操作是否成功
                    if (ResChecker.checkRes(TAG, exchangeResultObj)) {
                        // 输出日志信息
                        Log.forest("神奇海洋🏖️[万能拼图]制作${exchangeNum}张,剩余${exchangedPieceNum}张碎片")
                        // 制作完成后休眠1秒钟
                        delay(1000)
                    }
                } else {
                    // 如果未成功获取道具列表，停止循环
                    shouldContinue = false
                }
            }
        } catch (t: Throwable) {
            // 捕获并记录异常
            Log.runtime(TAG, "exchangeProp error:")
            Log.printStackTrace(TAG, t)
        }
    }

    // 使用万能拼图
    private suspend fun usePropByType() {
        try {
            // 获取道具使用类型列表的JSON数据
            val propListJson = AntOceanRpcCall.usePropByTypeList()
            val propListObj = JsonUtil.parseJSONObjectOrNull(propListJson) ?: return
            if (ResChecker.checkRes(TAG, propListObj)) {
                val propInfos = ArrayList<JSONObject>()
                val oceanPropVOList = propListObj.optJSONArray("oceanPropVOList")
                if (oceanPropVOList != null) {
                    for (i in 0 until oceanPropVOList.length()) {
                        val propInfo = oceanPropVOList.optJSONObject(i) ?: continue
                        if (propInfo.optString("type") == "UNIVERSAL_PIECE") {
                            propInfos.add(propInfo)
                        }
                    }
                } else {
                    val oceanPropVOByTypeList = propListObj.optJSONArray("oceanPropVOByTypeList") ?: return
                    for (i in 0 until oceanPropVOByTypeList.length()) {
                        val propInfo = oceanPropVOByTypeList.optJSONObject(i) ?: continue
                        if (propInfo.optString("type") == "UNIVERSAL_PIECE") {
                            propInfos.add(propInfo)
                        }
                    }
                }
                propInfos.sortByDescending { it.optString("code") == "LIMIT_TIME_UNIVERSAL_PIECE" }
                // 遍历每个道具类型信息
                for (propInfo in propInfos) {
                    val propCode = propInfo.optString("code").ifBlank { "UNIVERSAL_PIECE" }
                    val propName = propInfo.optString("name").ifBlank {
                        if (propCode == "LIMIT_TIME_UNIVERSAL_PIECE") "限时万能拼图" else "万能拼图"
                    }
                    var holdsNum = propInfo.getInt("holdsNum")
                    if (holdsNum <= 0) {
                        continue
                    }
                    val prioritizeExtraCollect = propCode == "LIMIT_TIME_UNIVERSAL_PIECE"
                    val scanExtraCollectOnlyList = if (prioritizeExtraCollect) listOf(true, false) else listOf(false)
                    th@ for (scanExtraCollectOnly in scanExtraCollectOnlyList) {
                        if (holdsNum <= 0) {
                            break
                        }
                        var pageNum = 0
                        while (holdsNum > 0) {
                            // 查询鱼列表的JSON数据
                            pageNum++
                            val fishListJson = AntOceanRpcCall.queryFishList(pageNum)
                            val fishListObj = JsonUtil.parseJSONObjectOrNull(fishListJson) ?: break
                            // 检查是否成功获取到鱼列表并且 hasMore 为 true
                            if (!ResChecker.checkRes(TAG, fishListObj)) {
                                // 如果没有成功获取到鱼列表或者 hasMore 为 false，则停止后续操作
                                break
                            }
                            // 获取鱼列表中的fishVOS数组
                            val fishVOS = fishListObj.optJSONArray("fishVOS") ?: fishListObj.optJSONArray("fishVOList")
                            if (fishVOS == null) {
                                break
                            }
                            // 遍历fishVOS数组，寻找pieces中num值为0的鱼的order和id
                            for (j in 0 until fishVOS.length()) {
                                val fish = fishVOS.getJSONObject(j)
                                val fishTag = fish.optString("tag")
                                if (scanExtraCollectOnly && fishTag != "EXTRA_COLLECT") {
                                    continue
                                }
                                val pieces = fish.optJSONArray("pieces")
                                if (pieces == null) {
                                    continue
                                }
                                val order = fish.getInt("order")
                                val name = fish.getString("name")
                                val idSet = LinkedHashSet<Int>()
                                for (k in 0 until pieces.length()) {
                                    val piece = pieces.getJSONObject(k)
                                    if (piece.optInt("num") <= 0) {
                                        idSet.add(Integer.parseInt(piece.getString("id")))
                                        if (idSet.size >= holdsNum) {
                                            break
                                        }
                                    }
                                }
                                if (idSet.isNotEmpty()) {
                                    val useCount = idSet.size
                                    val usePropResult = AntOceanRpcCall.usePropByType(propCode, order, idSet) ?: continue
                                    val usePropResultObj = JsonUtil.parseJSONObjectOrNull(usePropResult) ?: continue
                                    if (ResChecker.checkRes(TAG, usePropResultObj)) {
                                        holdsNum -= useCount
                                        Log.forest("神奇海洋🏖️[$propName]使用${useCount}张，获得[$name]剩余${holdsNum}张")
                                        delay(1000)
                                        if (holdsNum <= 0) {
                                            break@th
                                        }
                                    } else {
                                        Log.runtime(TAG, usePropResultObj.optString("resultDesc").ifBlank {
                                            usePropResultObj.optString("memo").ifBlank { usePropResultObj.toString() }
                                        })
                                        break@th
                                    }
                                }
                            }
                            if (!fishListObj.optBoolean("hasMore")) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "usePropByType error:")
            Log.printStackTrace(TAG, t)
        }
    }
}

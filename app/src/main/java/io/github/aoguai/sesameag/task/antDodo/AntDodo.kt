package io.github.aoguai.sesameag.task.antDodo

import com.fasterxml.jackson.core.type.TypeReference
import org.json.JSONException
import org.json.JSONObject
import io.github.aoguai.sesameag.entity.AlipayUser
import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.task.TaskCommon
import io.github.aoguai.sesameag.task.TaskStatus
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.DataStore
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.maps.UserMap
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TimeUtil

class AntDodo : ModelTask() {

    private var collectToFriend: BooleanModelField? = null
    private var collectToFriendType: ChoiceModelField? = null
    private var collectToFriendList: SelectModelField? = null
    private var sendFriendCard: SelectModelField? = null
    private var useProp: BooleanModelField? = null
    private var usePropCollectTimes7Days: BooleanModelField? = null
    private var usePropCollectHistoryAnimal7Days: BooleanModelField? = null
    private var usePropCollectToFriendTimes7Days: BooleanModelField? = null
    private var autoGenerateBook: BooleanModelField? = null

    override fun getName(): String = "神奇物种"

    override fun getGroup(): ModelGroup = ModelGroup.FOREST

    override fun getIcon(): String = "AntDodo.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField("collectToFriend", "帮抽卡 | 开启", false).withDesc(
                "开启后按下面的名单规则帮好友抽神奇物种卡片。"
            ).also { collectToFriend = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "collectToFriendType",
                "帮抽卡 | 动作",
                CollectToFriendType.COLLECT,
                CollectToFriendType.nickNames
            ).withDesc("选择名单解释方式：仅帮选中好友抽卡，或跳过选中好友。需开启“帮抽卡 | 开启”。").also {
                collectToFriendType = it
            }
        )
        modelFields.addField(
            SelectModelField(
                "collectToFriendList",
                "帮抽卡 | 好友列表",
                LinkedHashSet<String?>(),
                AlipayUser::getListAsMapperEntity
            ).withDesc("设置帮抽卡规则作用的好友名单。").also { collectToFriendList = it }
        )
        modelFields.addField(
            SelectModelField(
                "sendFriendCard",
                "送卡片好友列表(当前图鉴所有卡片)",
                LinkedHashSet<String?>(),
                AlipayUser::getListAsMapperEntity
            ).withDesc("列表不为空时，会把当前图鉴可赠送的卡片和新抽到的三星卡送给列表中的首个有效好友。").also {
                sendFriendCard = it
            }
        )
        modelFields.addField(
            BooleanModelField("useProp", "使用道具 | 所有", false).withDesc(
                "自动使用当前可消费的神奇物种道具，对所有支持的道具类型生效。"
            ).also { useProp = it }
        )
        modelFields.addField(
            BooleanModelField("usePropCollectTimes7Days", "使用道具 | 抽卡道具", false).withDesc(
                "单独开启后仅使用抽卡类道具；开启“使用道具 | 所有”时也会一起生效。"
            ).also { usePropCollectTimes7Days = it }
        )
        modelFields.addField(
            BooleanModelField("usePropCollectHistoryAnimal7Days", "使用道具 | 抽历史卡道具", false).withDesc(
                "单独开启后仅使用历史卡抽卡道具；开启“使用道具 | 所有”时也会一起生效。"
            ).also { usePropCollectHistoryAnimal7Days = it }
        )
        modelFields.addField(
            BooleanModelField("usePropCollectToFriendTimes7Days", "使用道具 | 抽好友卡道具", false).withDesc(
                "单独开启后仅使用好友卡抽卡道具；开启“使用道具 | 所有”时也会一起生效。"
            ).also { usePropCollectToFriendTimes7Days = it }
        )
        modelFields.addField(
            BooleanModelField("autoGenerateBook", "自动合成图鉴", false).withDesc(
                "图鉴显示“已集齐”时自动合成对应勋章。"
            ).also { autoGenerateBook = it }
        )
        return modelFields
    }

    override fun check(): Boolean {
        return when {
            TaskCommon.IS_ENERGY_TIME -> {
                Log.record(TAG, "⏸ 当前为只收能量时间【${BaseModel.energyTime.value}】，停止执行${getName()}任务！")
                false
            }
            TaskCommon.IS_MODULE_SLEEP_TIME -> {
                Log.record(TAG, "💤 模块休眠时间【${BaseModel.modelSleepTime.value}】停止执行${getName()}任务！")
                false
            }
            else -> true
        }
    }

    override fun runJava() {
        try {
            Log.record(TAG, "执行开始-${getName()}")
            receiveTaskAward()
            propList()
            collect()
            if (collectToFriend?.value == true) {
                collectToFriend()
            }
            if (autoGenerateBook?.value == true) {
                autoGenerateBook()
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "start.run err:")
            Log.printStackTrace(TAG, t)
        } finally {
            Log.record(TAG, "执行结束-${getName()}")
        }
    }

    private fun lastDay(endDate: String): Boolean {
        val timeStep = System.currentTimeMillis()
        val endTimeStep = TimeUtil.timeToStamp(endDate)
        return timeStep < endTimeStep && (endTimeStep - timeStep) < 86400000L
    }

    private fun in8Days(endDate: String): Boolean {
        val timeStep = System.currentTimeMillis()
        val endTimeStep = TimeUtil.timeToStamp(endDate)
        return timeStep < endTimeStep && (endTimeStep - timeStep) < 691200000L
    }

    private fun collect() {
        try {
            val response = AntDodoRpcCall.queryAnimalStatus()
            if (response.isNullOrEmpty()) {
                Log.runtime(TAG, "queryAnimalStatus返回空")
                return
            }
            val jo = JSONObject(response)
            if (ResChecker.checkRes(TAG, jo)) {
                val data = jo.getJSONObject("data")
                if (data.getBoolean("collect")) {
                    Log.record(TAG, "神奇物种卡片今日收集完成！")
                } else {
                    collectAnimalCard()
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "AntDodo Collect err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun collectAnimalCard() {
        try {
            val homeResponse = AntDodoRpcCall.homePage()
            if (homeResponse.isNullOrEmpty()) {
                Log.runtime(TAG, "homePage返回空")
                return
            }
            var jo = JSONObject(homeResponse)
            if (ResChecker.checkRes(TAG, jo)) {
                var data = jo.getJSONObject("data")
                val animalBook = data.getJSONObject("animalBook")
                val bookId = animalBook.getString("bookId")
                val endDate = "${animalBook.getString("endDate")} 23:59:59"
                receiveTaskAward()
                if (!in8Days(endDate) || lastDay(endDate))
                    propList()
                val ja = data.getJSONArray("limit")
                var index = -1
                for (i in 0 until ja.length()) {
                    jo = ja.getJSONObject(i)
                    if ("DAILY_COLLECT" == jo.getString("actionCode")) {
                        index = i
                        break
                    }
                }
                val set = sendFriendCard?.value ?: emptySet()
                if (index >= 0) {
                    val leftFreeQuota = jo.getInt("leftFreeQuota")
                    for (j in 0 until leftFreeQuota) {
                        val collectResponse = AntDodoRpcCall.collect()
                        if (collectResponse.isNullOrEmpty()) {
                            Log.runtime(TAG, "collect返回空")
                            continue
                        }
                        jo = JSONObject(collectResponse)
                        if (ResChecker.checkRes(TAG, jo)) {
                            data = jo.getJSONObject("data")
                            val animal = data.getJSONObject("animal")
                            val ecosystem = animal.getString("ecosystem")
                            val name = animal.getString("name")
                            Log.forest("神奇物种🦕[$ecosystem]#$name")
                            if (set.isNotEmpty()) {
                                for (userId in set) {
                                    if (userId != null && UserMap.currentUid != userId) {
                                        val fantasticStarQuantity = animal.optInt("fantasticStarQuantity", 0)
                                        if (fantasticStarQuantity == 3) {
                                            sendCard(animal, userId)
                                        }
                                        break
                                    }
                                }
                            }
                        } else {
                            Log.runtime(TAG, jo.getString("resultDesc"))
                        }
                    }
                }
                if (set.isNotEmpty()) {
                    for (userId in set) {
                        if (userId != null && UserMap.currentUid != userId) {
                            sendAntDodoCard(bookId, userId)
                            break
                        }
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "AntDodo CollectAnimalCard err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun receiveTaskAward() {
        try {
            val presetBad = LinkedHashSet(listOf("HELP_FRIEND_COLLECT"))
            val typeRef = object : TypeReference<MutableSet<String>>() {}
            val badTaskSet = DataStore.getOrCreate("badDodoTaskList", typeRef)
            if (badTaskSet.isEmpty()) {
                badTaskSet.addAll(presetBad)
                DataStore.put("badDodoTaskList", badTaskSet)
            }
            while (!Thread.currentThread().isInterrupted) {
                var doubleCheck = false
                val response = AntDodoRpcCall.taskList()
                if (response.isNullOrEmpty()) {
                    Log.runtime(TAG, "taskList返回空")
                    break
                }
                val jsonResponse = JSONObject(response)
                if (!ResChecker.checkRes(TAG, jsonResponse)) {
                    Log.record(TAG, "查询任务列表失败：${jsonResponse.getString("resultDesc")}")
                    Log.runtime(response)
                    break
                }
                val taskGroupInfoList = jsonResponse.getJSONObject("data").optJSONArray("taskGroupInfoList") ?: return
                for (i in 0 until taskGroupInfoList.length()) {
                    val antDodoTask = taskGroupInfoList.getJSONObject(i)
                    val taskInfoList = antDodoTask.getJSONArray("taskInfoList")
                    for (j in 0 until taskInfoList.length()) {
                        val taskInfo = taskInfoList.getJSONObject(j)
                        val taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo")
                        val bizInfo = JSONObject(taskBaseInfo.getString("bizInfo"))
                        val taskType = taskBaseInfo.getString("taskType")
                        val taskTitle = bizInfo.optString("taskTitle", taskType)
                        val awardCount = bizInfo.optString("awardCount", "1")
                        val sceneCode = taskBaseInfo.getString("sceneCode")
                        val taskStatus = taskBaseInfo.getString("taskStatus")
                        when {
                            TaskStatus.FINISHED.name == taskStatus -> {
                                val awardResponse = AntDodoRpcCall.receiveTaskAward(sceneCode, taskType)
                                if (awardResponse.isNullOrEmpty()) {
                                    Log.runtime(TAG, "receiveTaskAward返回空")
                                    continue
                                }
                                val joAward = JSONObject(awardResponse)
                                if (joAward.optBoolean("success")) {
                                    doubleCheck = true
                                    Log.forest("任务奖励🎖️[$taskTitle]#${awardCount}个")
                                } else {
                                    Log.record(TAG, "领取失败，$response")
                                }
                                Log.runtime(joAward.toString())
                            }
                            TaskStatus.TODO.name == taskStatus -> {
                                if (!badTaskSet.contains(taskType)) {
                                    val finishResponse = AntDodoRpcCall.finishTask(sceneCode, taskType)
                                    if (finishResponse.isNullOrEmpty()) {
                                        Log.runtime(TAG, "finishTask返回空")
                                        continue
                                    }
                                    val joFinishTask = JSONObject(finishResponse)
                                    if (joFinishTask.optBoolean("success")) {
                                        Log.forest("物种任务🧾️[$taskTitle]")
                                        doubleCheck = true
                                    } else {
                                        Log.record(TAG, "完成任务失败，$taskTitle")
                                        badTaskSet.add(taskType)
                                        DataStore.put("badDodoTaskList", badTaskSet)
                                    }
                                }
                            }
                        }
                        GlobalThreadPools.sleepCompat(500)
                    }
                }
                if (!doubleCheck) break
            }
        } catch (e: JSONException) {
            Log.error(TAG, "JSON解析错误: ${e.message}")
            Log.printStackTrace(TAG, e)
        } catch (t: Throwable) {
            Log.runtime(TAG, "AntDodo ReceiveTaskAward 错误:")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun propList() {
        try {
            th@ while (!Thread.currentThread().isInterrupted) {
                val response = AntDodoRpcCall.propList()
                if (response.isNullOrEmpty()) {
                    Log.runtime(TAG, "propList返回空")
                    return
                }
                val jo = JSONObject(response)
                if (ResChecker.checkRes(TAG, jo)) {
                    val propList = jo.getJSONObject("data").optJSONArray("propList") ?: return
                    for (i in 0 until propList.length()) {
                        val prop = propList.getJSONObject(i)
                        val propType = prop.getString("propType")
                        val usePropType = isUsePropType(propType)
                        if (!usePropType) {
                            continue
                        }
                        val propIdList = prop.getJSONArray("propIdList")
                        val propId = propIdList.getString(0)
                        val propName = prop.getJSONObject("propConfig").getString("propName")
                        val holdsNum = prop.optInt("holdsNum", 0)
                        val consumeResponse = AntDodoRpcCall.consumeProp(propId, propType)
                        if (consumeResponse.isNullOrEmpty()) {
                            Log.runtime(TAG, "consumeProp返回空")
                            continue
                        }
                        val joConsume = JSONObject(consumeResponse)
                        if (!ResChecker.checkRes(TAG, joConsume)) {
                            Log.record(joConsume.getString("resultDesc"))
                            Log.runtime(joConsume.toString())
                            continue
                        }
                        if ("COLLECT_TIMES_7_DAYS" == propType) {
                            val useResult = joConsume.getJSONObject("data").getJSONObject("useResult")
                            val animal = useResult.getJSONObject("animal")
                            val ecosystem = animal.getString("ecosystem")
                            val name = animal.getString("name")
                            Log.forest("使用道具🎭[$propName]#$ecosystem-$name")
                            val map = sendFriendCard?.value ?: emptySet()
                            for (userId in map) {
                                if (userId != null && UserMap.currentUid != userId) {
                                    val fantasticStarQuantity = animal.optInt("fantasticStarQuantity", 0)
                                    if (fantasticStarQuantity == 3) {
                                        sendCard(animal, userId)
                                    }
                                    break
                                }
                            }
                        } else {
                            Log.forest("使用道具🎭[$propName]")
                        }
                        GlobalThreadPools.sleepCompat(300)
                        if (holdsNum > 1) {
                            continue@th
                        }
                    }
                }
                break
            }
        } catch (th: Throwable) {
            Log.runtime(TAG, "AntDodo PropList err:")
            Log.printStackTrace(TAG, th)
        }
    }

    private fun isUsePropType(propType: String): Boolean {
        var usePropType = useProp?.value ?: false
        usePropType = when (propType) {
            "COLLECT_TIMES_7_DAYS" -> usePropType || (usePropCollectTimes7Days?.value ?: false)
            "COLLECT_HISTORY_ANIMAL_7_DAYS" -> usePropType || (usePropCollectHistoryAnimal7Days?.value ?: false)
            "COLLECT_TO_FRIEND_TIMES_7_DAYS" -> usePropType || (usePropCollectToFriendTimes7Days?.value ?: false)
            else -> usePropType
        }
        return usePropType
    }

    private fun sendAntDodoCard(bookId: String, targetUser: String) {
        try {
            val response = AntDodoRpcCall.queryBookInfo(bookId)
            if (response.isNullOrEmpty()) {
                Log.runtime(TAG, "queryBookInfo返回空")
                return
            }
            val jo = JSONObject(response)
            if (ResChecker.checkRes(TAG, jo)) {
                val animalForUserList = jo.getJSONObject("data").optJSONArray("animalForUserList")
                for (i in 0 until (animalForUserList?.length() ?: 0)) {
                    val animalForUser = animalForUserList!!.getJSONObject(i)
                    val count = animalForUser.getJSONObject("collectDetail").optInt("count")
                    if (count <= 0)
                        continue
                    val animal = animalForUser.getJSONObject("animal")
                    for (j in 0 until count) {
                        sendCard(animal, targetUser)
                        GlobalThreadPools.sleepCompat(500L)
                    }
                }
            }
        } catch (th: Throwable) {
            Log.runtime(TAG, "AntDodo SendAntDodoCard err:")
            Log.printStackTrace(TAG, th)
        }
    }

    private fun sendCard(animal: JSONObject, targetUser: String) {
        try {
            val animalId = animal.getString("animalId")
            val ecosystem = animal.getString("ecosystem")
            val name = animal.getString("name")
            val socialResponse = AntDodoRpcCall.social(animalId, targetUser)
            if (socialResponse.isNullOrEmpty()) {
                Log.runtime(TAG, "social返回空")
                return
            }
            val jo = JSONObject(socialResponse)
            if (ResChecker.checkRes(TAG, jo)) {
                Log.forest("赠送卡片🦕[${UserMap.getMaskName(targetUser)}]#$ecosystem-$name")
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (th: Throwable) {
            Log.runtime(TAG, "AntDodo SendCard err:")
            Log.printStackTrace(TAG, th)
        }
    }

    private fun collectToFriend() {
        try {
            val queryResponse = AntDodoRpcCall.queryFriend()
            if (queryResponse.isNullOrEmpty()) {
                Log.runtime(TAG, "queryFriend返回空")
                return
            }
            var jo = JSONObject(queryResponse)
            if (ResChecker.checkRes(TAG, jo)) {
                var count = 0
                val limitList = jo.getJSONObject("data").getJSONObject("extend").getJSONArray("limit")
                for (i in 0 until limitList.length()) {
                    val limit = limitList.getJSONObject(i)
                    if (limit.getString("actionCode") == "COLLECT_TO_FRIEND") {
                        if (limit.getLong("startTime") > System.currentTimeMillis()) {
                            return
                        }
                        count = limit.getInt("leftLimit")
                        break
                    }
                }
                val friendList = jo.getJSONObject("data").getJSONArray("friends")
                for (i in 0 until friendList.length()) {
                    if (count <= 0) break
                    val friend = friendList.getJSONObject(i)
                    if (friend.getBoolean("dailyCollect")) {
                        continue
                    }
                    val useId = friend.getString("userId")
                    var isCollectToFriend = collectToFriendList?.value?.contains(useId) ?: false
                    if (collectToFriendType?.value == CollectToFriendType.DONT_COLLECT) {
                        isCollectToFriend = !isCollectToFriend
                    }
                    if (!isCollectToFriend) {
                        continue
                    }
                    val collectFriendResponse = AntDodoRpcCall.collect(useId)
                    if (collectFriendResponse.isNullOrEmpty()) {
                        Log.runtime(TAG, "collect(friend)返回空")
                        continue
                    }
                    jo = JSONObject(collectFriendResponse)
                    if (ResChecker.checkRes(TAG, jo)) {
                        val ecosystem = jo.getJSONObject("data").getJSONObject("animal").getString("ecosystem")
                        val name = jo.getJSONObject("data").getJSONObject("animal").getString("name")
                        val userName = UserMap.getMaskName(useId)
                        Log.forest("神奇物种🦕帮好友[$userName]抽卡[$ecosystem]#$name")
                        count--
                    } else {
                        Log.runtime(TAG, jo.getString("resultDesc"))
                    }
                }
            } else {
                Log.runtime(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            Log.runtime(TAG, "AntDodo CollectHelpFriend err:")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun autoGenerateBook() {
        try {
            var hasMore: Boolean
            var pageStart = 0
            do {
                if (Thread.currentThread().isInterrupted) {
                    break
                }
                val bookListResponse = AntDodoRpcCall.queryBookList(9, pageStart)
                if (bookListResponse.isNullOrEmpty()) {
                    Log.runtime(TAG, "queryBookList返回空")
                    break
                }
                var jo = JSONObject(bookListResponse)
                if (!ResChecker.checkRes(TAG, jo)) {
                    break
                }
                jo = jo.getJSONObject("data")
                hasMore = jo.getBoolean("hasMore")
                pageStart += 9
                val bookForUserList = jo.getJSONArray("bookForUserList")
                for (i in 0 until bookForUserList.length()) {
                    jo = bookForUserList.getJSONObject(i)
                    if ("已集齐" != jo.optString("medalGenerationStatus")) {
                        continue
                    }
                    val animalBookResult = jo.getJSONObject("animalBookResult")
                    val bookId = animalBookResult.getString("bookId")
                    val ecosystem = animalBookResult.getString("ecosystem")
                    val medalResponse = AntDodoRpcCall.generateBookMedal(bookId)
                    if (medalResponse.isNullOrEmpty()) {
                        Log.runtime(TAG, "generateBookMedal返回空")
                        continue
                    }
                    jo = JSONObject(medalResponse)
                    if (!ResChecker.checkRes(TAG, jo)) {
                        break
                    }
                    Log.forest("神奇物种🦕合成勋章[$ecosystem]")
                }
            } while (hasMore && !Thread.currentThread().isInterrupted)
        } catch (t: Throwable) {
            Log.runtime(TAG, "generateBookMedal err:")
            Log.printStackTrace(TAG, t)
        }
    }

    interface CollectToFriendType {
        companion object {
            const val COLLECT = 0
            const val DONT_COLLECT = 1
            val nickNames = arrayOf("选中帮抽卡", "选中不帮抽卡")
        }
    }

    companion object {
        private val TAG = AntDodo::class.java.simpleName
    }
}


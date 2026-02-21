package fansirsqi.xposed.sesame.data

import com.fasterxml.jackson.databind.JsonMappingException
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.antForest.AntForest
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.StringUtil
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.util.Calendar
import java.util.Date

class Status {

    // =========================== forest
    var waterFriendLogList: MutableMap<String, Int> = HashMap()
    var cooperateWaterList: MutableSet<String> = HashSet() // 合作浇水
    var reserveLogList: MutableMap<String, Int> = HashMap()
    var ancientTreeCityCodeList: MutableSet<String> = HashSet() // 古树
    var protectBubbleList: MutableSet<String> = HashSet()
    var doubleTimes: Int = 0
    var exchangeEnergyShield: Boolean = false // 活力值兑换能量保护罩
    var exchangeCollectHistoryAnimal7Days: Boolean = false
    var exchangeCollectToFriendTimes7Days: Boolean = false
    var youthPrivilege: Boolean = true
    var studentTask: Boolean = true
    var vitalityStoreList: MutableMap<String, Int> = HashMap() // 注意命名规范首字母小写

    // =========================== farm
    var answerQuestion: Boolean = false
    var feedFriendLogList: MutableMap<String, Int> = HashMap()
    var visitFriendLogList: MutableMap<String, Int> = HashMap()

    // 可以存各种今日计数（步数、次数等）
    // 2025/12/4 GSMT 用来存储int类型数据，无需再重复定义
    var intFlagMap: MutableMap<String, Int> = HashMap()

    var dailyAnswerList: MutableSet<String> = HashSet()
    var donationEggList: MutableSet<String> = HashSet()
    var useAccelerateToolCount: Int = 0

    /** 小鸡换装 */
    var canOrnament: Boolean = true
    var animalSleep: Boolean = false

    // ============================= stall
    var stallHelpedCountLogList: MutableMap<String, Int> = HashMap()
    var spreadManureList: MutableSet<String> = HashSet()
    var stallP2PHelpedList: MutableSet<String> = HashSet()
    var canStallDonate: Boolean = true

    // ========================== sport
    var syncStepList: MutableSet<String> = HashSet()
    var exchangeList: MutableSet<String> = HashSet()

    /** 捐运动币 */
    var donateCharityCoin: Boolean = false

    // ======================= other
    var memberSignInList: MutableSet<String> = HashSet()
    val flagList: MutableSet<String> = HashSet()

    /** 口碑签到 */
    var kbSignIn: Long = 0

    /** 保存时间 */
    var saveTime: Long = 0L

    /** 新村助力好友，已上限的用户 */
    var antStallAssistFriend: MutableSet<String> = HashSet()

    /** 新村-罚单已贴完的用户 */
    var canPasteTicketTime: MutableSet<String> = HashSet()

    /** 绿色经营，收取好友金币已完成用户 */
    var greenFinancePointFriend: MutableSet<String> = HashSet()

    /** 绿色经营，评级领奖已完成用户 */
    var greenFinancePrizesMap: MutableMap<String, Int> = HashMap()

    /** 农场助力 */
    var antOrchardAssistFriend: MutableSet<String> = HashSet()

    /** 会员权益 */
    var memberPointExchangeBenefitLogList: MutableSet<String> = HashSet()

    companion object {
        private val TAG = Status::class.java.simpleName

        @JvmStatic
        val INSTANCE: Status = Status()

        @JvmStatic
        val currentDayTimestamp: Long
            get() {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis
            }

        @JvmStatic
        fun getVitalityCount(skuId: String): Int {
            return INSTANCE.vitalityStoreList[skuId] ?: 0
        }

        @JvmStatic
        fun canVitalityExchangeToday(skuId: String, count: Int): Boolean {
            return !hasFlagToday("forest::VitalityExchangeLimit::$skuId") && getVitalityCount(skuId) < count
        }

        @JvmStatic
        fun vitalityExchangeToday(skuId: String) {
            val count = getVitalityCount(skuId) + 1
            INSTANCE.vitalityStoreList[skuId] = count
            save()
        }

        @JvmStatic
        fun canAnimalSleep(): Boolean {
            return !INSTANCE.animalSleep
        }

        @JvmStatic
        fun animalSleep() {
            if (!INSTANCE.animalSleep) {
                INSTANCE.animalSleep = true
                save()
            }
        }

        @JvmStatic
        fun canWaterFriendToday(id: String, newCount: Int): Boolean {
            val key = "${UserMap.currentUid}-$id"
            val count = INSTANCE.waterFriendLogList[key] ?: return true
            return count < newCount
        }

        @JvmStatic
        fun waterFriendToday(id: String, count: Int) {
            val key = "${UserMap.currentUid}-$id"
            INSTANCE.waterFriendLogList[key] = count
            save()
        }

        @JvmStatic
        fun getReserveTimes(id: String): Int {
            return INSTANCE.reserveLogList[id] ?: 0
        }

        @JvmStatic
        fun canReserveToday(id: String, count: Int): Boolean {
            return getReserveTimes(id) < count
        }

        @JvmStatic
        fun reserveToday(id: String, newCount: Int) {
            val count = INSTANCE.reserveLogList[id] ?: 0
            INSTANCE.reserveLogList[id] = count + newCount
            save()
        }

        @JvmStatic
        fun canCooperateWaterToday(uid: String?, coopId: String): Boolean {
            return !INSTANCE.cooperateWaterList.contains("${uid}_$coopId")
        }

        @JvmStatic
        fun cooperateWaterToday(uid: String?, coopId: String?) {
            val v = "${uid}_$coopId"
            if (INSTANCE.cooperateWaterList.add(v)) {
                save()
            }
        }

        @JvmStatic
        fun canAncientTreeToday(cityCode: String): Boolean {
            return !INSTANCE.ancientTreeCityCodeList.contains(cityCode)
        }

        @JvmStatic
        fun ancientTreeToday(cityCode: String) {
            if (INSTANCE.ancientTreeCityCodeList.add(cityCode)) {
                save()
            }
        }

        @JvmStatic
        fun canAnswerQuestionToday(): Boolean {
            return !INSTANCE.answerQuestion
        }

        @JvmStatic
        fun answerQuestionToday() {
            if (!INSTANCE.answerQuestion) {
                INSTANCE.answerQuestion = true
                save()
            }
        }

        @JvmStatic
        fun canFeedFriendToday(id: String, newCount: Int): Boolean {
            val count = INSTANCE.feedFriendLogList[id] ?: return true
            return count < newCount
        }

        @JvmStatic
        fun feedFriendToday(id: String) {
            val count = INSTANCE.feedFriendLogList[id] ?: 0
            INSTANCE.feedFriendLogList[id] = count + 1
            save()
        }

        @JvmStatic
        fun canVisitFriendToday(id: String, newCount: Int): Boolean {
            val key = "${UserMap.currentUid}-$id"
            val count = INSTANCE.visitFriendLogList[key] ?: return true
            return count < newCount
        }

        @JvmStatic
        fun visitFriendToday(id: String, newCount: Int) {
            val key = "${UserMap.currentUid}-$id"
            INSTANCE.visitFriendLogList[key] = newCount
            save()
        }

        @JvmStatic
        fun canMemberSignInToday(uid: String?): Boolean {
            return !INSTANCE.memberSignInList.contains(uid)
        }

        @JvmStatic
        fun memberSignInToday(uid: String?) {
            if (uid != null) {
                if (INSTANCE.memberSignInList.add(uid)) {
                    save()
                }
            }
        }

        @JvmStatic
        fun canUseAccelerateTool(): Boolean {
            return INSTANCE.useAccelerateToolCount < 8
        }

        @JvmStatic
        fun useAccelerateTool() {
            INSTANCE.useAccelerateToolCount += 1
            save()
        }

        @JvmStatic
        fun canDonationEgg(uid: String?): Boolean {
            return !INSTANCE.donationEggList.contains(uid)
        }

        @JvmStatic
        fun donationEgg(uid: String?) {
            if (!uid.isNullOrEmpty() && INSTANCE.donationEggList.add(uid)) {
                save()
            }
        }

        @JvmStatic
        fun canSpreadManureToday(uid: String): Boolean {
            return !INSTANCE.spreadManureList.contains(uid)
        }

        @JvmStatic
        fun spreadManureToday(uid: String) {
            if (INSTANCE.spreadManureList.add(uid)) {
                save()
            }
        }

        @JvmStatic
        fun canAntStallAssistFriendToday(): Boolean {
            return !INSTANCE.antStallAssistFriend.contains(UserMap.currentUid)
        }

        @JvmStatic
        fun antStallAssistFriendToday() {
            if (INSTANCE.antStallAssistFriend.add(UserMap.currentUid!!)) {
                save()
            }
        }

        @JvmStatic
        fun canAntOrchardAssistFriendToday(): Boolean {
            return !INSTANCE.antOrchardAssistFriend.contains(UserMap.currentUid)
        }

        @JvmStatic
        fun antOrchardAssistFriendToday() {
            if (INSTANCE.antOrchardAssistFriend.add(UserMap.currentUid!!)) {
                save()
            }
        }

        @JvmStatic
        fun canProtectBubbleToday(uid: String?): Boolean {
            return !INSTANCE.protectBubbleList.contains(uid)
        }

        @JvmStatic
        fun protectBubbleToday(uid: String?) {
            if (uid != null) {
                if (INSTANCE.protectBubbleList.add(uid)) {
                    save()
                }
            } else {
                Log.error("protectBubbleToday uid is null")
            }
        }

        @JvmStatic
        fun canPasteTicketTime(): Boolean {
            return !INSTANCE.canPasteTicketTime.contains(UserMap.currentUid)
        }

        @JvmStatic
        fun pasteTicketTime() {
            if (INSTANCE.canPasteTicketTime.add(UserMap.currentUid!!)) {
                save()
            }
        }

        @JvmStatic
        fun canDoubleToday(): Boolean {
            val task = Model.getModel(AntForest::class.java) ?: return false
            return INSTANCE.doubleTimes < (task.doubleCountLimit?.value ?: 0)
        }

        @JvmStatic
        fun doubleToday() {
            INSTANCE.doubleTimes += 1
            save()
        }

        @JvmStatic
        fun canKbSignInToday(): Boolean {
            return INSTANCE.kbSignIn < currentDayTimestamp
        }

        @JvmStatic
        fun KbSignInToday() {
            val todayZero = currentDayTimestamp
            if (INSTANCE.kbSignIn != todayZero) {
                INSTANCE.kbSignIn = todayZero
                save()
            }
        }

        @JvmStatic
        fun setDadaDailySet(dailyAnswerList: MutableSet<String>) {
            INSTANCE.dailyAnswerList = dailyAnswerList
            save()
        }

        @JvmStatic
        fun canDonateCharityCoin(): Boolean {
            return !INSTANCE.donateCharityCoin
        }

        @JvmStatic
        fun donateCharityCoin() {
            if (!INSTANCE.donateCharityCoin) {
                INSTANCE.donateCharityCoin = true
                save()
            }
        }

        @JvmStatic
        fun canExchangeToday(uid: String): Boolean {
            return !INSTANCE.exchangeList.contains(uid)
        }

        @JvmStatic
        fun exchangeToday(uid: String) {
            if (INSTANCE.exchangeList.add(uid)) {
                save()
            }
        }

        @JvmStatic
        fun canGreenFinancePointFriend(): Boolean {
            return INSTANCE.greenFinancePointFriend.contains(UserMap.currentUid)
        }

        @JvmStatic
        fun greenFinancePointFriend() {
            if (canGreenFinancePointFriend()) return
            INSTANCE.greenFinancePointFriend.add(UserMap.currentUid!!)
            save()
        }

        @JvmStatic
        fun canGreenFinancePrizesMap(): Boolean {
            val week = TimeUtil.getWeekNumber(Date())
            val currentUid = UserMap.currentUid
            if (INSTANCE.greenFinancePrizesMap.containsKey(currentUid)) {
                val storedWeek = INSTANCE.greenFinancePrizesMap[currentUid]
                return storedWeek == null || storedWeek != week
            }
            return true
        }

        @JvmStatic
        fun greenFinancePrizesMap() {
            if (!canGreenFinancePrizesMap()) return
            INSTANCE.greenFinancePrizesMap[UserMap.currentUid!!] = TimeUtil.getWeekNumber(Date())
            save()
        }

        @Synchronized
        @JvmStatic
        fun load(currentUid: String?): Status {
            if (StringUtil.isEmpty(currentUid)) {
                Log.record(TAG, "用户为空，状态加载失败")
                throw RuntimeException("用户为空，状态加载失败")
            }
            try {
                val statusFile = Files.getStatusFile(currentUid)
                if (statusFile!!.exists()) {
                    Log.record(TAG, "加载 status.json")
                    val json = Files.readFromFile(statusFile)
                    if (!json.trim().isEmpty()) {
                        // 使用 Jackson 更新现有对象
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue<Status>(json)
                        // 格式化检查
                        val formatted = JsonUtil.formatJson(INSTANCE)
                        if (formatted != null && formatted != json) {
                            Log.record(TAG, "重新格式化 status.json")
                            Files.write2File(formatted, statusFile)
                        }
                    } else {
                        Log.record(TAG, "配置文件为空，初始化默认配置")
                        initializeDefaultConfig(statusFile)
                    }
                } else {
                    Log.record(TAG, "配置文件不存在，初始化默认配置")
                    initializeDefaultConfig(statusFile)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
                Log.record(TAG, "状态文件格式有误，已重置")
                resetAndSaveConfig()
            }

            // 这里逻辑有点奇怪，如果 saveTime 是 0，则设为当前时间。
            // 原始 Java 代码中 Long 默认为 null，但这里属性初始化为 0L。
            if (INSTANCE.saveTime == 0L) {
                INSTANCE.saveTime = System.currentTimeMillis()
            }
            return INSTANCE
        }

        private fun initializeDefaultConfig(statusFile: java.io.File) {
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, Status())
                Log.record(TAG, "初始化 status.json")
                Files.write2File(JsonUtil.formatJson(INSTANCE), statusFile)
            } catch (e: JsonMappingException) {
                Log.printStackTrace(TAG, e)
                throw RuntimeException("初始化配置失败", e)
            }
        }

        private fun resetAndSaveConfig() {
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, Status())
                Files.write2File(JsonUtil.formatJson(INSTANCE), Files.getStatusFile(UserMap.currentUid)!!)
            } catch (e: JsonMappingException) {
                Log.printStackTrace(TAG, e)
                throw RuntimeException("重置配置失败", e)
            }
        }

        @Synchronized
        @JvmStatic
        fun unload() {
            try {
                // 创建新状态实例并确保清空所有每日标记
                val newStatus = Status()
                // 确保清空flagList
                INSTANCE.flagList.clear()
                JsonUtil.copyMapper().updateValue(INSTANCE, newStatus)
            } catch (e: JsonMappingException) {
                Log.printStackTrace(TAG, e)
            }
        }

        @Synchronized
        @JvmStatic
        fun save(nowCalendar: Calendar = Calendar.getInstance()) {
            val currentUid = UserMap.currentUid
            if (StringUtil.isEmpty(currentUid)) {
                Log.record(TAG, "用户为空，状态保存失败")
                throw RuntimeException("用户为空，状态保存失败")
            }
            if (updateDay(nowCalendar)) {
                Log.record(TAG, "重置 statistics.json")
            } else {
                Log.record(TAG, "保存 status.json")
            }
            val lastSaveTime = INSTANCE.saveTime
            try {
                INSTANCE.saveTime = System.currentTimeMillis()
                Files.write2File(JsonUtil.formatJson(INSTANCE), Files.getStatusFile(currentUid)!!)
            } catch (e: Exception) {
                INSTANCE.saveTime = lastSaveTime
                throw e
            }
        }

        @JvmStatic
        fun updateDay(nowCalendar: Calendar): Boolean {
            if (TimeUtil.isLessThanSecondOfDays(INSTANCE.saveTime, nowCalendar.timeInMillis)) {
                unload()
                return true
            }
            return false
        }

        @JvmStatic
        fun canOrnamentToday(): Boolean {
            return INSTANCE.canOrnament
        }

        @JvmStatic
        fun setOrnamentToday() {
            if (INSTANCE.canOrnament) {
                INSTANCE.canOrnament = false
                save()
            }
        }

        @JvmStatic
        fun canStallDonateToday(): Boolean {
            return INSTANCE.canStallDonate
        }

        @JvmStatic
        fun setStallDonateToday() {
            if (INSTANCE.canStallDonate) {
                INSTANCE.canStallDonate = false
                save()
            }
        }

        /**
         * ## 设置今日已运行状态
         * @param flag tagName::done
         */
        @JvmStatic
        fun hasFlagToday(flag: String): Boolean {
            return INSTANCE.flagList.contains(flag)
        }

        @JvmStatic
        fun setFlagToday(flag: String) {
            if (INSTANCE.flagList.add(flag)) {
                save()
            }
        }

        // 2025/12/4 用来获取 自定义flag的int
        @JvmStatic
        fun getIntFlagToday(key: String): Int? {
            return INSTANCE.intFlagMap[key]
        }

        @JvmStatic
        fun setIntFlagToday(key: String, value: Int) {
            INSTANCE.intFlagMap[key] = value
            save()
        }

        @JvmStatic
        fun canMemberPointExchangeBenefitToday(benefitId: String): Boolean {
            return !INSTANCE.memberPointExchangeBenefitLogList.contains(benefitId)
        }

        @JvmStatic
        fun memberPointExchangeBenefitToday(benefitId: String) {
            if (canMemberPointExchangeBenefitToday(benefitId)) {
                INSTANCE.memberPointExchangeBenefitLogList.add(benefitId)
                save()
            }
        }

        /**
         * 乐园商城-是否可以兑换该商品
         *
         * @param spuId 商品spuId
         * @return true 可以兑换 false 兑换达到上限
         */
        @JvmStatic
        fun canParadiseCoinExchangeBenefitToday(spuId: String): Boolean {
            return !hasFlagToday("farm::paradiseCoinExchangeLimit::$spuId")
        }
    }
}

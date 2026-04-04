package io.github.aoguai.sesameag.task.antStall

import android.util.Base64
import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.entity.AlipayUser
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.ChoiceModelField
import io.github.aoguai.sesameag.model.modelFieldExt.IntegerModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.util.FriendGuard
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.RandomUtil
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TimeCounter
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedList
import java.util.Queue

/**
 * @file AntStall.kt
 * @brief 蚂蚁新村任务模块
 * @author Constanline
 * @since 2023/08/22
 */
class AntStall : ModelTask() {

    /**
     * @brief 摊位数据类
     * @property userId 用户ID
     * @property hot 热度值
     */
    private data class Seat(val userId: String, val hot: Int)

    // 配置字段
    private lateinit var stallAutoOpen: BooleanModelField
    private lateinit var stallOpenType: ChoiceModelField
    private lateinit var stallOpenList: SelectModelField
    private lateinit var stallAutoClose: BooleanModelField
    private lateinit var stallAutoTicket: BooleanModelField
    private lateinit var stallTicketType: ChoiceModelField
    private lateinit var stallTicketList: SelectModelField
    private lateinit var stallAutoTask: BooleanModelField
    private lateinit var stallReceiveAward: BooleanModelField
    private lateinit var stallWhiteList: SelectModelField
    private lateinit var stallBlackList: SelectModelField
    private lateinit var stallAllowOpenReject: BooleanModelField
    private lateinit var stallAllowOpenTime: IntegerModelField
    private lateinit var stallSelfOpenTime: IntegerModelField
    private lateinit var stallDonate: BooleanModelField
    private lateinit var stallInviteRegister: BooleanModelField
    private lateinit var stallThrowManure: BooleanModelField
    private lateinit var stallThrowManureType: ChoiceModelField
    private lateinit var stallThrowManureList: SelectModelField
    private lateinit var stallInviteShop: BooleanModelField
    private lateinit var stallInviteShopType: ChoiceModelField
    private lateinit var stallInviteShopList: SelectModelField
    private lateinit var roadmap: BooleanModelField
    private lateinit var stallInviteRegisterList: SelectModelField
    private lateinit var stallAssistFriend: BooleanModelField
    private lateinit var assistFriendList: SelectModelField

    override fun getName(): String = "新村"

    override fun getGroup(): ModelGroup = ModelGroup.STALL

    override fun getIcon(): String = "AntStall.png"

    override fun getFields(): ModelFields {
        return ModelFields().apply {
            addField(BooleanModelField("stallAutoOpen", "摆摊 | 开启", false).withDesc(
                "自动把空闲小摊派到好友村庄摆摊；关闭后不会执行摆摊流程。"
            ).also { stallAutoOpen = it })
            addField(
                ChoiceModelField(
                    "stallOpenType",
                    "摆摊 | 动作",
                    StallOpenType.OPEN,
                    StallOpenType.nickNames
                ).withDesc("决定“摆摊 | 好友列表”是作为允许名单还是排除名单。需开启“摆摊 | 开启”。")
                    .also { stallOpenType = it })
            addField(
                SelectModelField(
                    "stallOpenList",
                    "摆摊 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("配置摆摊规则作用的好友村庄名单。需开启“摆摊 | 开启”。")
                    .also { stallOpenList = it })
            addField(
                BooleanModelField(
                    "stallAutoClose",
                    "收摊 | 开启",
                    false
                ).withDesc("按设定摆摊时长自动收回自己的小摊，并可衔接再次摆摊。")
                    .also { stallAutoClose = it })
            addField(
                IntegerModelField(
                    "stallSelfOpenTime",
                    "收摊 | 摆摊时长(分钟)",
                    120,
                    0,
                    null
                ).withDesc("单个摊位摆出多久后自动收摊；需开启“收摊 | 开启”。")
                    .also { stallSelfOpenTime = it })
            addField(
                BooleanModelField(
                    "stallAutoTicket",
                    "贴罚单 | 开启",
                    false
                ).withDesc("自动巡逻并给符合条件的好友小摊贴罚单。")
                    .also { stallAutoTicket = it })
            addField(
                ChoiceModelField(
                    "stallTicketType",
                    "贴罚单 | 动作",
                    StallTicketType.DONT_TICKET,
                    StallTicketType.nickNames
                ).withDesc("决定“贴罚单 | 好友列表”是贴还是跳过。需开启“贴罚单 | 开启”。")
                    .also { stallTicketType = it })
            addField(
                SelectModelField(
                    "stallTicketList",
                    "贴罚单 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("配置贴罚单规则作用的好友名单。需开启“贴罚单 | 开启”。")
                    .also { stallTicketList = it })
            addField(
                BooleanModelField(
                    "stallThrowManure",
                    "丢肥料 | 开启",
                    false
                ).withDesc("按名单规则自动给动态中的好友丢肥料。")
                    .also { stallThrowManure = it })
            addField(
                ChoiceModelField(
                    "stallThrowManureType",
                    "丢肥料 | 动作",
                    StallThrowManureType.DONT_THROW,
                    StallThrowManureType.nickNames
                ).withDesc("决定“丢肥料 | 好友列表”是丢还是跳过。需开启“丢肥料 | 开启”。")
                    .also { stallThrowManureType = it })
            addField(
                SelectModelField(
                    "stallThrowManureList",
                    "丢肥料 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("配置丢肥料规则作用的好友名单。需开启“丢肥料 | 开启”。")
                    .also { stallThrowManureList = it })
            addField(
                BooleanModelField(
                    "stallInviteShop",
                    "邀请摆摊 | 开启",
                    false
                ).withDesc("自家有空位或请走别人后，自动邀请好友来你家摆摊。")
                    .also { stallInviteShop = it })
            addField(
                ChoiceModelField(
                    "stallInviteShopType",
                    "邀请摆摊 | 动作",
                    StallInviteShopType.INVITE,
                    StallInviteShopType.nickNames
                ).withDesc("决定“邀请摆摊 | 好友列表”是邀请还是排除。需开启“邀请摆摊 | 开启”。")
                    .also { stallInviteShopType = it })
            addField(
                SelectModelField(
                    "stallInviteShopList",
                    "邀请摆摊 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("配置允许自动邀请来摆摊的好友名单。需开启“邀请摆摊 | 开启”。")
                    .also { stallInviteShopList = it })
            addField(
                BooleanModelField(
                    "stallAllowOpenReject",
                    "请走小摊 | 开启",
                    false
                ).withDesc("按时长、黑白名单规则自动请走占位小摊。")
                    .also { stallAllowOpenReject = it })
            addField(
                IntegerModelField(
                    "stallAllowOpenTime",
                    "请走小摊 | 允许摆摊时长(分钟)",
                    121,
                    0,
                    null
                ).withDesc("好友在你家摆摊超过该时长后会被自动请走；白名单除外。需开启“请走小摊 | 开启”。")
                    .also { stallAllowOpenTime = it })
            addField(
                SelectModelField(
                    "stallWhiteList",
                    "请走小摊 | 白名单(超时也不赶)",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("这些好友即使超时也不会被请走。需开启“请走小摊 | 开启”。")
                    .also { stallWhiteList = it })
            addField(
                SelectModelField(
                    "stallBlackList",
                    "请走小摊 | 黑名单(不超时也赶)",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("这些好友即使未超时也会被立即请走。需开启“请走小摊 | 开启”。")
                    .also { stallBlackList = it })
            addField(BooleanModelField("stallAutoTask", "自动任务", false).withDesc(
                "执行新村签到、可自动完成的每日任务及部分特殊任务。"
            ).also { stallAutoTask = it })
            addField(
                BooleanModelField(
                    "stallReceiveAward",
                    "自动领奖",
                    false
                ).withDesc("自动领取已完成新村任务的奖励。建议配合“自动任务”一起开启。")
                    .also { stallReceiveAward = it })
            addField(
                BooleanModelField("stallDonate", "自动捐赠", false).withDesc(
                    "金币满足条件时自动向新村公益项目捐赠。"
                ).also { stallDonate = it }
            )
            addField(
                BooleanModelField("roadmap", "自动进入下一村", false).withDesc(
                    "发现可解锁的新村时自动进入打卡，每个村每天只处理一次。"
                ).also { roadmap = it }
            )
            addField(
                BooleanModelField(
                    "stallInviteRegister",
                    "邀请 | 邀请好友开通新村",
                    false
                ).withDesc("在自动任务中尝试邀请指定好友开通蚂蚁新村。需开启“自动任务”。")
                    .also { stallInviteRegister = it })
            addField(
                SelectModelField(
                    "stallInviteRegisterList",
                    "邀请 | 好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("仅邀请列表中的好友开通新村。需开启“邀请 | 邀请好友开通新村”。")
                    .also { stallInviteRegisterList = it })
            addField(
                BooleanModelField("stallAssistFriend", "新村助力", false).withDesc(
                    "按“助力好友列表”自动执行新村助力；关闭后不会发起助力。"
                ).also { stallAssistFriend = it }
            )
            addField(
                SelectModelField(
                    "assistFriendList",
                    "助力好友列表",
                    LinkedHashSet(),
                    AlipayUser::getFriendList
                ).withDesc("配置允许自动新村助力的好友列表。需开启“新村助力”。")
                    .also { assistFriendList = it })
        }
    }

    override fun runJava() {
        try {
            val tc = TimeCounter(TAG)
            Log.record(TAG, "执行开始-${getName()}")

            val homeResponse = AntStallRpcCall.home()
            val homeJson = JSONObject(homeResponse)

            if (!ResChecker.checkRes(TAG, homeJson)) {
                Log.record(TAG, "home err: $homeResponse")
                return
            }

            // 检查是否已注册
            if (!homeJson.getBoolean("hasRegister") || homeJson.getBoolean("hasQuit")) {
                Log.farm("蚂蚁新村⛪请先开启蚂蚁新村")
                return
            }

            // 收取应收金币
            val astReceivableCoinVO = homeJson.getJSONObject("astReceivableCoinVO")
            if (astReceivableCoinVO.optBoolean("hasCoin")) {
                settleReceivable()
                tc.countDebug("收金币")
            }

            // 丢肥料
            if (stallThrowManure.value == true) {
                throwManure()
                tc.countDebug("丢肥料")
            }

            val seatsMap = homeJson.getJSONObject("seatsMap")

            // 收取金币
            settle(seatsMap)
            tc.countDebug("收取金币")

            // 收肥料
            collectManure()
            tc.countDebug("收肥料")

            // 请走操作
            sendBack(seatsMap)
            tc.countDebug("请走")

            // 收摊
            if (stallAutoClose.value == true) {
                closeShop()
                tc.countDebug("收摊")
            }

            // 摆摊
            if (stallAutoOpen.value == true) {
                openShop()
                tc.countDebug("摆摊")
            }

            // 自动任务
            if (stallAutoTask.value == true) {
                taskList()
                tc.countDebug("自动任务第一次")
                GlobalThreadPools.sleepCompat(500)
                taskList()
                tc.countDebug("自动任务第二次")
            }

            // 新村助力
            if (stallAssistFriend.value == true) {
                assistFriend()
                tc.countDebug("新村助力")
            }

            // 自动捐赠
            if (stallDonate.value == true && Status.canStallDonateToday()) {
                donate()
                tc.countDebug("自动捐赠")
            }

            // 进入下一村
            if (roadmap.value == true) {
                roadmap()
                tc.countDebug("自动进入下一村")
            }

            // 贴罚单
            if (stallAutoTicket.value == true) {
                pasteTicket()
                tc.countDebug("贴罚单")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "home err:", t)
        } finally {
            Log.record(TAG, "执行结束-${getName()}")
        }
    }

    /**
     * @brief 请走小摊
     */
    private fun sendBack(
        billNo: String,
        seatId: String,
        shopId: String,
        shopUserId: String,
        sentUserId: MutableSet<String>
    ) {
        try {
            val preResponse = AntStallRpcCall.shopSendBackPre(billNo, seatId, shopId, shopUserId)
            val preJson = JSONObject(preResponse)

            if (!ResChecker.checkRes(TAG, preJson)) {
                Log.error(TAG, "sendBackPre err: $preResponse")
                return
            }

            val income = preJson.getJSONObject("astPreviewShopSettleVO").getJSONObject("income")
            val amount = income.getDouble("amount").toInt()

            val sendBackResponse = AntStallRpcCall.shopSendBack(seatId)
            val sendBackJson = JSONObject(sendBackResponse)

            if (ResChecker.checkRes(TAG, sendBackJson)) {
                val amountText = if (amount > 0) "获得金币$amount" else ""
                Log.farm("蚂蚁新村⛪请走[${UserMap.getMaskName(shopUserId)}]的小摊$amountText")
            } else {
                Log.error(TAG, "sendBack err: $sendBackResponse")
            }

            if (stallInviteShop.value == true) {
                inviteOpen(seatId, sentUserId)
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "sendBack err:", t)
        }
    }

    /**
     * @brief 邀请开店
     */
    private fun inviteOpen(seatId: String, sentUserId: MutableSet<String>) {
        try {
            val response = AntStallRpcCall.rankInviteOpen()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "inviteOpen err: $response")
                return
            }

            val friendRankList = json.getJSONArray("friendRankList")
            for (i in 0 until friendRankList.length()) {
                val friend = friendRankList.getJSONObject(i)
                val friendUserId = friend.getString("userId")

                var isInviteShop = stallInviteShopList.value?.contains(friendUserId) == true
                if (stallInviteShopType.value == StallInviteShopType.DONT_INVITE) {
                    isInviteShop = !isInviteShop
                }

                if (!isInviteShop || sentUserId.contains(friendUserId)) {
                    continue
                }
                if (FriendGuard.shouldSkipFriend(friendUserId, TAG, "邀请摆摊")) {
                    continue
                }

                if (friend.getBoolean("canInviteOpenShop")) {
                    val inviteResponse = AntStallRpcCall.oneKeyInviteOpenShop(friendUserId, seatId)
                    if (inviteResponse.isEmpty()) {
                        Log.record(TAG, "邀请[${UserMap.getMaskName(friendUserId)}]开店返回空,跳过")
                        continue
                    }

                    val inviteJson = JSONObject(inviteResponse)
                    if (ResChecker.checkRes(TAG, inviteJson)) {
                        Log.farm("蚂蚁新村⛪邀请[${UserMap.getMaskName(friendUserId)}]开店成功")
                        sentUserId.add(friendUserId)
                        return
                    } else {
                        Log.record(
                            TAG,
                            "邀请[${UserMap.getMaskName(friendUserId)}]开店失败: ${
                                inviteJson.optString("errorMessage")
                            }"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "inviteOpen err:", t)
        }
    }

    /**
     * @brief 处理摊位请走逻辑
     */
    private fun sendBack(seatsMap: JSONObject) {
        try {
            val sentUserId = mutableSetOf<String>()

            // 记录已占用的用户
            for (i in 1..2) {
                val seat = seatsMap.getJSONObject("GUEST_0$i")
                if (seat.getString("status") == "BUSY") {
                    val rentLastUser = seat.optString("rentLastUser")
                    if (rentLastUser.isNotEmpty()) {
                        sentUserId.add(rentLastUser)
                    }
                }
            }

            // 处理每个摊位
            for (i in 1..2) {
                val seat = seatsMap.getJSONObject("GUEST_0$i")
                val seatId = seat.getString("seatId")

                // 摊位空闲时尝试邀请
                if (seat.getString("status") == "FREE") {
                    if (stallInviteShop.value == true) {
                        Log.record(TAG, "摊位[$i]空闲,尝试邀请好友...")
                        inviteOpen(seatId, sentUserId)
                    }
                    continue
                }

                if (stallAllowOpenReject.value != true) {
                    continue
                }

                val rentLastUser = seat.optString("rentLastUser")
                if (rentLastUser.isEmpty()) {
                    continue
                }

                // 白名单跳过
                if (stallWhiteList.value?.contains(rentLastUser) == true) {
                    Log.record(
                        TAG,
                        "好友[${UserMap.getMaskName(rentLastUser)}]在白名单中,跳过请走。"
                    )
                    continue
                }

                val rentLastBill = seat.getString("rentLastBill")
                val rentLastShop = seat.getString("rentLastShop")

                // 黑名单直接赶走
                if (stallBlackList.value?.contains(rentLastUser) == true) {
                    Log.record(
                        TAG,
                        "好友[${UserMap.getMaskName(rentLastUser)}]在黑名单中,立即请走。"
                    )
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser, sentUserId)
                    continue
                }

                // 超时判断
                val bizStartTime = seat.getLong("bizStartTime")
                val allowMinutes = stallAllowOpenTime.value ?: 0
                val endTime = bizStartTime + allowMinutes * 60 * 1000L

                if (System.currentTimeMillis() > endTime) {
                    Log.record(TAG, "好友[${UserMap.getMaskName(rentLastUser)}]摆摊超时,立即请走。")
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser, sentUserId)
                } else {
                    val taskId = "SB|$seatId"
                    if (!hasChildTask(taskId)) {
                        addChildTask(ChildModelTask(taskId, "SB", {
                            if (stallAllowOpenReject.value == true) {
                                sendBack(
                                    rentLastBill,
                                    seatId,
                                    rentLastShop,
                                    rentLastUser,
                                    sentUserId
                                )
                            }
                        }, endTime))
                        Log.record(TAG, "添加蹲点请走⛪在[${TimeUtil.getCommonDate(endTime)}]执行")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "sendBack err:", t)
        }
    }

    /**
     * @brief 结算金币
     */
    private fun settle(seatsMap: JSONObject) {
        try {
            val seat = seatsMap.getJSONObject("MASTER")
            if (!seat.has("coinsMap")) return

            val coinsMap = seat.getJSONObject("coinsMap")
            val master = coinsMap.getJSONObject("MASTER")
            val assetId = master.getString("assetId")
            val settleCoin = master.getJSONObject("money").getDouble("amount").toInt()
            val fullShow = master.getBoolean("fullShow")

            if (fullShow || settleCoin > 100) {
                val response = AntStallRpcCall.settle(assetId, settleCoin)
                val json = JSONObject(response)
                if (ResChecker.checkRes(TAG, json)) {
                    Log.farm("蚂蚁新村⛪[收取金币]#$settleCoin")
                } else {
                    Log.error(TAG, "settle err: $response")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "settle err:", t)
        }
    }

    /**
     * @brief 收摊
     */
    private fun closeShop() {
        try {
            val response = AntStallRpcCall.shopList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "closeShop err: $response")
                return
            }

            val astUserShopList = json.getJSONArray("astUserShopList")
            if (astUserShopList.length() == 0) {
                Log.record(TAG, "没有正在摆摊的小摊可收。")
                return
            }

            Log.record(TAG, "检查 ${astUserShopList.length()} 个小摊的收摊时间...")

            for (i in 0 until astUserShopList.length()) {
                val shop = astUserShopList.getJSONObject(i)
                if (shop.getString("status") != "OPEN") continue

                val rentLastEnv = shop.getJSONObject("rentLastEnv")
                val gmtLastRent = rentLastEnv.getLong("gmtLastRent")
                val selfOpenMinutes = stallSelfOpenTime.value ?: 0
                val shopTime = gmtLastRent + selfOpenMinutes * 60 * 1000L
                val shopId = shop.getString("shopId")
                val rentLastBill = shop.getString("rentLastBill")
                val rentLastUser = shop.getString("rentLastUser")

                if (System.currentTimeMillis() > shopTime) {
                    Log.record(TAG, "小摊[$shopId]摆摊时间已到,执行收摊。")
                    shopClose(shopId, rentLastBill, rentLastUser)
                } else {
                    val taskId = "SH|$shopId"
                    if (!hasChildTask(taskId)) {
                        addChildTask(ChildModelTask(taskId, "SH", {
                            if (stallAutoClose.value == true) {
                                shopClose(shopId, rentLastBill, rentLastUser)
                            }
                            GlobalThreadPools.sleepCompat(300L)
                            if (stallAutoOpen.value == true) {
                                openShop()
                            }
                        }, shopTime))
                        Log.record(TAG, "添加蹲点收摊⛪在[${TimeUtil.getCommonDate(shopTime)}]执行")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "closeShop err:", t)
        }
    }

    /**
     * @brief 摆摊
     */
    private fun openShop() {
        try {
            val response = AntStallRpcCall.shopList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "openShop err: $response")
                return
            }

            val astUserShopList = json.getJSONArray("astUserShopList")
            val shopIds: Queue<String> = LinkedList()

            for (i in 0 until astUserShopList.length()) {
                val shop = astUserShopList.getJSONObject(i)
                if (shop.getString("status") == "FREE") {
                    shopIds.add(shop.getString("shopId"))
                }
            }

            if (shopIds.isEmpty()) {
                Log.record(TAG, "没有空闲的小摊可用于摆摊。")
                return
            }

            Log.record(TAG, "找到 ${shopIds.size} 个空闲小摊,开始寻找好友村庄...")
            rankCoinDonate(shopIds)

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openShop err:", t)
        }
    }

    /**
     * @brief 获取好友排行榜
     */
    private fun rankCoinDonate(shopIds: Queue<String>) {
        try {
            val response = AntStallRpcCall.rankCoinDonate()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "rankCoinDonate err: $response")
                return
            }

            val friendRankList = json.getJSONArray("friendRankList")
            val seats = mutableListOf<Seat>()

            for (i in 0 until friendRankList.length()) {
                val friendRank = friendRankList.getJSONObject(i)
                if (!friendRank.getBoolean("canOpenShop")) continue

                val userId = friendRank.getString("userId")
                var isStallOpen = stallOpenList.value?.contains(userId) == true
                if (stallOpenType.value == StallOpenType.CLOSE) {
                    isStallOpen = !isStallOpen
                }

                if (isStallOpen) {
                    val hot = friendRank.getInt("hot")
                    seats.add(Seat(userId, hot))
                }
            }

            friendHomeOpen(seats, shopIds)

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "rankCoinDonate err:", t)
        }
    }

    /**
     * @brief 在好友村庄开店
     */
    private fun openShop(seatId: String, userId: String, shopId: String) {
        try {
            val response = AntStallRpcCall.shopOpen(seatId, userId, shopId)
            val json = JSONObject(response)

            if (json.optString("resultCode") == "SUCCESS") {
                Log.farm("蚂蚁新村⛪在[${UserMap.getMaskName(userId)}]家摆摊")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "openShop err:", t)
        }
    }

    /**
     * @brief 访问好友主页并开店
     */
    private fun friendHomeOpen(seats: List<Seat>, shopIds: Queue<String>) {
        val sortedSeats = seats.sortedByDescending { it.hot }
        val currentUid = UserMap.currentUid

        for (seat in sortedSeats) {
            if (shopIds.isEmpty()) {
                return
            }
            val userId = seat.userId

            try {
                val json = queryFriendHomeIfAvailable(userId, "新村摆摊") ?: continue

                val seatsMap = json.getJSONObject("seatsMap")

                // 检查是否已占用摊位
                val guest1 = seatsMap.getJSONObject("GUEST_01")
                val rentUser1 = guest1.optString("rentLastUser")
                val guest2 = seatsMap.getJSONObject("GUEST_02")
                val rentUser2 = guest2.optString("rentLastUser")

                if (currentUid == rentUser1 || currentUid == rentUser2) {
                    Log.record(TAG, "已在[${UserMap.getMaskName(userId)}]家摆摊,跳过")
                    continue
                }

                val targetSeatId = when {
                    guest1.getBoolean("canOpenShop") -> guest1.getString("seatId")
                    guest2.getBoolean("canOpenShop") -> guest2.getString("seatId")
                    else -> null
                }
                if (targetSeatId == null) {
                    continue
                }
                val shopId = shopIds.poll() ?: return
                openShop(targetSeatId, userId, shopId)

            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            }
        }
    }

    private fun queryFriendHomeIfAvailable(userId: String, sceneName: String): JSONObject? {
        if (FriendGuard.shouldSkipFriend(userId, TAG, sceneName)) {
            return null
        }
        val response = AntStallRpcCall.friendHome(userId)
        val json = JSONObject(response)
        val reason = json.optString("resultDesc")
            .ifEmpty { json.optString("errorMessage") }
            .ifEmpty { json.optString("memo") }
        if (
            json.optString("resultCode") == "NEED_UPGRADE_VILLAGE" ||
            reason.contains("未开通") ||
            reason.contains("未注册")
        ) {
            Log.record(TAG, "$sceneName 跳过[${UserMap.getMaskName(userId) ?: userId}]：${reason.ifEmpty { "对方未开通蚂蚁新村或当前不可访问" }}")
            return null
        }
        if (!ResChecker.checkRes(TAG, json)) {
            Log.record(TAG, "$sceneName 跳过[${UserMap.getMaskName(userId) ?: userId}]：${reason.ifEmpty { "对方未开通蚂蚁新村或当前不可访问" }}")
            return null
        }
        return json
    }

    /**
     * @brief 关闭商店
     */
    private fun shopClose(shopId: String, billNo: String, userId: String) {
        try {
            val preResponse = AntStallRpcCall.preShopClose(shopId, billNo)
            val preJson = JSONObject(preResponse)

            if (!ResChecker.checkRes(TAG, preJson)) {
                Log.error(TAG, "shopClose err: $preResponse")
                return
            }

            val income = preJson.getJSONObject("astPreviewShopSettleVO").getJSONObject("income")
            val closeResponse = AntStallRpcCall.shopClose(shopId)
            val closeJson = JSONObject(closeResponse)

            if (ResChecker.checkRes(TAG, closeJson)) {
                Log.farm(
                    "蚂蚁新村⛪收取在[${UserMap.getMaskName(userId)}]的摊位获得${
                        income.getString(
                            "amount"
                        )
                    }"
                )
            } else {
                Log.error(TAG, "shopClose err: $closeResponse")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "shopClose err:", t)
        }
    }

    /**
     * @brief 处理任务列表
     */
    private fun taskList() {
        try {
            val response = AntStallRpcCall.taskList()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "taskList err: $response")
                return
            }

            // 签到
            val signListModel = json.getJSONObject("signListModel")
            if (!signListModel.getBoolean("currentKeySigned")) {
                Log.record(TAG, "开始执行每日签到...")
                signToday()
            }

            val taskModels = json.getJSONArray("taskModels")
            Log.record(TAG, "开始检查 ${taskModels.length()} 个新村任务...")

            for (i in 0 until taskModels.length()) {
                try {
                    val task = taskModels.getJSONObject(i)
                    val taskStatus = task.getString("taskStatus")
                    val taskType = task.getString("taskType")

                    // 已完成的任务领取奖励
                    if (taskStatus == "FINISHED") {
                        Log.record(TAG, "任务[$taskType]已完成,尝试领取奖励...")
                        receiveTaskAward(taskType)
                        continue
                    }

                    if (taskStatus != "TODO") continue

                    val bizInfo = JSONObject(task.getString("bizInfo"))
                    val title = bizInfo.optString("title", taskType)
                    val actionType = bizInfo.getString("actionType")

                    // 自动完成任务
                    if (actionType == "VISIT_AUTO_FINISH" || taskType in TASK_TYPE_LIST) {
                        if (finishTask(taskType)) {
                            Log.farm("蚂蚁新村💣任务[$title]完成")
                            GlobalThreadPools.sleepCompat(200L)
                        }
                        continue
                    }

                    // 特殊任务处理
                    when (taskType) {
                        "ANTSTALL_NORMAL_DAILY_QA" -> {
                            if (ReadingDada.answerQuestion(bizInfo)) {
                                receiveTaskAward(taskType)
                            }
                        }

                        "ANTSTALL_NORMAL_INVITE_REGISTER" -> {
                            if (inviteRegister()) {
                                GlobalThreadPools.sleepCompat(200L)
                            }
                        }

                        "ANTSTALL_XLIGHT_VARIABLE_AWARD" -> {
                            handleXlightTask()
                        }
                    }

                    GlobalThreadPools.sleepCompat(200L)

                } catch (t: Throwable) {
                    Log.printStackTrace(TAG, "taskList for err:", t)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "taskList err:", t)
        }
    }

    /**
     * @brief 处理X-light任务
     */
    private fun handleXlightTask() {
        try {
            val response = AntStallRpcCall.xlightPlugin()
            val json = JSONObject(response)

            if (!json.has("playingResult")) {
                Log.error(TAG, "taskList.xlightPlugin err: ${json.optString("resultDesc")}")
                return
            }

            val playingResult = json.getJSONObject("playingResult")
            val pid = playingResult.getString("playingBizId")
            val eventList = JsonUtil.getValueByPathObject(
                playingResult,
                "eventRewardDetail.eventRewardInfoList"
            ) as? JSONArray ?: return

            if (eventList.length() == 0) return

            for (j in 0 until eventList.length()) {
                try {
                    val eventInfo = eventList.getJSONObject(j)
                    val finishResponse = AntStallRpcCall.finish(pid, eventInfo)
                    Log.record("延时5S 木兰市集")
                    GlobalThreadPools.sleepCompat(5000)

                    val finishJson = JSONObject(finishResponse)
                    if (!finishJson.optBoolean("success")) {
                        Log.error(TAG, "taskList.finish err: ${finishJson.optString("resultDesc")}")
                    }
                } catch (t: Throwable) {
                    Log.printStackTrace(TAG, "taskList for err:", t)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleXlightTask err:", t)
        }
    }

    /**
     * @brief 今日签到
     */
    private fun signToday() {
        try {
            val response = AntStallRpcCall.signToday()
            val json = JSONObject(response)

            if (ResChecker.checkRes(TAG, json)) {
                Log.farm("蚂蚁新村⛪[签到成功]")
            } else {
                Log.error(TAG, "signToday err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "signToday err:", t)
        }
    }

    /**
     * @brief 领取任务奖励
     */
    private fun receiveTaskAward(taskType: String) {
        if (stallReceiveAward.value != true) return

        try {
            val response = AntStallRpcCall.receiveTaskAward(taskType)
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                Log.farm("蚂蚁新村⛪[领取奖励]")
            } else {
                Log.error(TAG, "receiveTaskAward err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "receiveTaskAward err:", t)
        }
    }

    /**
     * @brief 完成任务
     */
    private fun finishTask(taskType: String): Boolean {
        try {
            val response = AntStallRpcCall.finishTask(
                "${taskType}_${System.currentTimeMillis()}",
                taskType
            )
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                return true
            } else {
                val errorCode = json.optString("code", json.optString("resultCode", ""))
                val desc = json.optString("desc", json.optString("memo", ""))
                if (errorCode == "400000040" || desc.contains("不支持rpc调用")) {
                    Log.record(TAG, "任务[$taskType]不支持RPC完成，跳过finishTask，等待服务端状态回写")
                    return false
                }
                Log.error(TAG, "finishTask err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "finishTask err:", t)
        }
        return false
    }

    /**
     * @brief 邀请好友注册
     */
    private fun inviteRegister(): Boolean {
        if (stallInviteRegister.value != true) return false

        try {
            val response = AntStallRpcCall.rankInviteRegister()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "rankInviteRegister err: $response")
                return false
            }

            val friendRankList = json.optJSONArray("friendRankList") ?: return false
            if (friendRankList.length() == 0) return false

            for (i in 0 until friendRankList.length()) {
                val friend = friendRankList.getJSONObject(i)

                if (!friend.optBoolean("canInviteRegister", false) ||
                    friend.getString("userStatus") != "UNREGISTER"
                ) {
                    continue
                }

                val userId = friend.getString("userId")
                if (FriendGuard.shouldSkipFriend(userId, TAG, "邀请开通新村")) {
                    continue
                }
                if (stallInviteRegisterList.value?.contains(userId) != true) {
                    continue
                }

                val inviteResponse = AntStallRpcCall.friendInviteRegister(userId)
                val inviteJson = JSONObject(inviteResponse)

                if (ResChecker.checkRes(TAG, inviteJson)) {
                    Log.farm("蚂蚁新村⛪邀请好友[${UserMap.getMaskName(userId)}]#开通新村")
                    return true
                } else {
                    Log.error(TAG, "friendInviteRegister err: $inviteJson")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "InviteRegister err:", t)
        }
        return false
    }

    /**
     * @brief 分享助力
     */
    private fun shareP2P(): String? {
        try {
            val response = AntStallRpcCall.shareP2P()
            val json = JSONObject(response)

            if (json.optBoolean("success")) {
                val shareId = json.getString("shareId")
                Log.record(TAG, "蚂蚁新村⛪[分享助力]")
                return shareId
            } else {
                Log.error(TAG, "shareP2P err: $response")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "shareP2P err:", t)
        }
        return null
    }

    /**
     * @brief 助力好友
     */
    private fun assistFriend() {
        try {
            if (stallAssistFriend.value != true) return

            if (!Status.canAntStallAssistFriendToday()) {
                Log.record(TAG, "今日新村助力次数已用完。")
                return
            }

            val friendSet = assistFriendList.value ?: emptySet()
            if (friendSet.isEmpty()) {
                Log.record(TAG, "未设置新村助力好友列表。")
                return
            }

            Log.record(TAG, "开始为 ${friendSet.size} 位好友进行新村助力...")

            for (uid in friendSet) {
                val safeUid = uid ?: continue
                if (FriendGuard.shouldSkipFriend(safeUid, TAG, "新村助力")) {
                    continue
                }
                val shareId = Base64.encodeToString(
                    "$safeUid-${RandomUtil.getRandomInt(5)}ANUTSALTML_2PA_SHARE".toByteArray(),
                    Base64.NO_WRAP
                )

                val response = AntStallRpcCall.achieveBeShareP2P(shareId)
                val json = JSONObject(response)
                val name = UserMap.getMaskName(safeUid)

                if (!json.optBoolean("success")) {
                    when (json.getString("code")) {
                        "600000028" -> {
                            Log.record(TAG, "新村助力🮐被助力次数上限[$name]")
                            continue
                        }

                        "600000027" -> {
                            Log.record(TAG, "新村助力💪今日助力他人次数上限")
                            Status.antStallAssistFriendToday()
                            return
                        }

                        else -> {
                            Log.error(TAG, "新村助力😔失败[$name]${json.optString("desc")}")
                            continue
                        }
                    }
                }

                Log.farm("新村助力🎉成功[$name]")
                GlobalThreadPools.sleepCompat(5000)
            }

            Status.antStallAssistFriendToday()

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "assistFriend err:", t)
        }
    }

    /**
     * @brief 捐赠项目
     */
    private fun donate() {
        try {
            val response = AntStallRpcCall.projectList()
            val json = JSONObject(response)

            if (json.optString("resultCode", "") != "SUCCESS") return

            // 检查余额
            val userInfo = json.optJSONObject("astUserInfoVO")
            if (userInfo != null) {
                val currentCoinAmount = userInfo.optJSONObject("currentCoin")
                    ?.optDouble("amount", 0.0) ?: 0.0

                if (currentCoinAmount < 15000) {
                    return
                }
            }

            // 查找在线项目
            val projects = json.optJSONArray("astProjectVOS") ?: return

            for (i in 0 until projects.length()) {
                val project = projects.optJSONObject(i) ?: continue

                if (project.optString("status", "") == "ONLINE") {
                    val projectId = project.optString("projectId", "")

                    // 获取项目详情
                    val detailResponse = AntStallRpcCall.projectDetail(projectId)
                    val detailJson = JSONObject(detailResponse)

                    if (detailJson.optString("resultCode", "") == "SUCCESS") {
                        // 执行捐赠
                        val donateResponse = AntStallRpcCall.projectDonate(projectId)
                        val donateJson = JSONObject(donateResponse)

                        val astProjectVO = donateJson.optJSONObject("astProjectVO")
                        if (astProjectVO != null) {
                            val title = astProjectVO.optString("title", "未知项目")

                            if (donateJson.optString("resultCode", "") == "SUCCESS") {
                                Log.farm("蚂蚁新村⛪[捐赠:$title]")
                                Status.setStallDonateToday()
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "donate err:", t)
        }
    }

    /**
     * @brief 进入下一村
     */
    private fun roadmap() {
        try {
            val response = AntStallRpcCall.roadmap()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) return

            val roadList = json.getJSONArray("roadList")
            var hasNewVillage = false

            for (i in 0 until roadList.length()) {
                val road = roadList.getJSONObject(i)

                if (road.getString("status") != "NEW") continue

                hasNewVillage = true
                val villageName = road.getString("villageName")
                val flagKey = "stall::roadmap::$villageName"

                if (Status.hasFlagToday(flagKey)) {
                    Log.record(TAG, "今日已进入[$villageName],跳过重复打卡。")
                    continue
                }

                Log.farm("蚂蚁新村⛪[进入:$villageName]成功")
                Status.setFlagToday(flagKey)
                break
            }

            if (!hasNewVillage) {
                Log.record(TAG, "所有村庄都已解锁,无需进入下一村。")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "roadmap err:", t)
        }
    }

    /**
     * @brief 收集肥料
     */
    private fun collectManure() {
        try {
            val response = AntStallRpcCall.queryManureInfo()
            val json = JSONObject(response)

            if (!json.optBoolean("success")) {
                Log.error(TAG, "collectManure err: $response")
                return
            }

            val astManureInfoVO = json.getJSONObject("astManureInfoVO")
            if (astManureInfoVO.optBoolean("hasManure")) {
                val manure = astManureInfoVO.getInt("manure")
                val collectResponse = AntStallRpcCall.collectManure()
                val collectJson = JSONObject(collectResponse)

                if (ResChecker.checkRes(TAG, collectJson)) {
                    Log.farm("蚂蚁新村⛪获得肥料${manure}g")
                }
            } else {
                Log.record(TAG, "没有可收取的肥料。")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "collectManure err:", t)
        }
    }

    /**
     * @brief 丢肥料批量处理
     */
    private fun throwManure(dynamicList: JSONArray) {
        // 前置检查:如果今日已达上限,直接跳过
        if (Status.hasFlagToday(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT)) {
            return
        }

        try {
            val response = AntStallRpcCall.throwManure(dynamicList)
            val json = JSONObject(response)

            // 先于ResChecker判断特定业务错误码
            val resultCode = json.optString("resultCode")
            if (resultCode == "B_OVER_LIMIT_COUNT_OF_THROW_TO_FRIEND") {
                Log.record(TAG, "检测到今日丢肥料次数已达上限,停止后续尝试")
                Status.setFlagToday(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT)
                return
            }

            // 正常的响应检查
            if (ResChecker.checkRes(TAG, json)) {
                Log.farm("蚂蚁新村⛪打肥料成功")
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "throwManure err:", t)
        } finally {
            GlobalThreadPools.sleepCompat(1000)
        }
    }

    /**
     * @brief 丢肥料主流程
     */
    private fun throwManure() {
        try {
            val response = AntStallRpcCall.dynamicLoss()
            val json = JSONObject(response)

            if (!ResChecker.checkRes(TAG, json)) {
                Log.error(TAG, "throwManure err: $response")
                return
            }

            val astLossDynamicVOS = json.getJSONArray("astLossDynamicVOS")
            var dynamicList = JSONArray()

            for (i in 0 until astLossDynamicVOS.length()) {
                val lossDynamic = astLossDynamicVOS.getJSONObject(i)

                if (lossDynamic.has("specialEmojiVO")) continue

                val objectId = lossDynamic.getString("objectId")
                var isThrowManure = stallThrowManureList.value?.contains(objectId) == true

                if (stallThrowManureType.value == StallThrowManureType.DONT_THROW) {
                    isThrowManure = !isThrowManure
                }

                if (!isThrowManure) continue

                val dynamic = JSONObject().apply {
                    put("bizId", lossDynamic.getString("bizId"))
                    put("bizType", lossDynamic.getString("bizType"))
                }
                dynamicList.put(dynamic)

                if (dynamicList.length() == 5) {
                    throwManure(dynamicList)
                    dynamicList = JSONArray()
                }
            }

            if (dynamicList.length() > 0) {
                throwManure(dynamicList)
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "throwManure err:", t)
        }
    }

    /**
     * @brief 结算应收金币
     */
    private fun settleReceivable() {
        try {
            val response = AntStallRpcCall.settleReceivable()
            val json = JSONObject(response)

            if (ResChecker.checkRes(TAG, json)) {
                Log.farm("蚂蚁新村⛪收取应收金币")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "settleReceivable err:", t)
        }
    }

    /**
     * @brief 贴罚单
     */
    private fun pasteTicket() {
        try {
            if (!Status.canPasteTicketTime()) {
                Log.record(TAG, "未到贴罚单时间或今日已贴完。")
                return
            }

            Log.record(TAG, "开始巡逻,寻找可贴罚单的好友...")

            while (!Thread.currentThread().isInterrupted) {
                try {
                    val response = AntStallRpcCall.nextTicketFriend()
                    val json = JSONObject(response)

                    if (!json.optBoolean("success")) {
                        Log.error(
                            TAG,
                            "pasteTicket.nextTicketFriend err: ${json.optString("resultDesc")}"
                        )
                        return
                    }

                    if (json.getInt("canPasteTicketCount") == 0) {
                        Log.record(TAG, "蚂蚁新村👍[今日罚单已贴完]")
                        Status.pasteTicketTime()
                        return
                    }

                    val friendId = json.optString("friendUserId")
                    if (friendId.isEmpty()) {
                        Log.record(TAG, "没有更多可贴罚单的好友了。")
                        return
                    }

                    var isStallTicket = stallTicketList.value?.contains(friendId) == true
                    if (stallTicketType.value == StallTicketType.DONT_TICKET) {
                        isStallTicket = !isStallTicket
                    }

                    if (!isStallTicket) continue

                    // 访问好友主页
                    val homeJson = queryFriendHomeIfAvailable(friendId, "新村贴罚单") ?: continue

                    val seatsMap = homeJson.getJSONObject("seatsMap")
                    val keys = seatsMap.keys()

                    while (keys.hasNext()) {
                        if (Thread.currentThread().isInterrupted) return
                        try {
                            val key = keys.next()
                            val propertyValue = seatsMap.get(key)

                            if (propertyValue !is JSONObject || propertyValue.length() == 0) {
                                continue
                            }

                            if (propertyValue.getBoolean("canOpenShop") ||
                                propertyValue.getString("status") != "BUSY" ||
                                !propertyValue.getBoolean("overTicketProtection")
                            ) {
                                continue
                            }

                            val rentLastUser = propertyValue.getString("rentLastUser")
                            val ticketResponse = AntStallRpcCall.ticket(
                                propertyValue.getString("rentLastBill"),
                                propertyValue.getString("seatId"),
                                propertyValue.getString("rentLastShop"),
                                rentLastUser,
                                propertyValue.getString("userId")
                            )

                            val ticketJson = JSONObject(ticketResponse)
                            if (!ticketJson.optBoolean("success")) {
                                Log.error(
                                    TAG,
                                    "pasteTicket.ticket err: ${ticketJson.optString("resultDesc")}"
                                )
                                return
                            }

                            Log.farm("蚂蚁新村🚫在[${UserMap.getMaskName(friendId)}]贴罚单")

                        } finally {
                            GlobalThreadPools.sleepCompat(1000)
                        }
                    }

                } finally {
                    GlobalThreadPools.sleepCompat(1500)
                }
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "pasteTicket err:", t)
        }
    }

    /**
     * @brief 摆摊操作类型
     */
    interface StallOpenType {
        companion object {
            const val OPEN = 0
            const val CLOSE = 1
            val nickNames = arrayOf("选中摆摊", "选中不摆摊")
        }
    }

    /**
     * @brief 贴罚单操作类型
     */
    interface StallTicketType {
        companion object {
            const val TICKET = 0
            const val DONT_TICKET = 1
            val nickNames = arrayOf("选中贴罚单", "选中不贴罚单")
        }
    }

    /**
     * @brief 丢肥料操作类型
     */
    interface StallThrowManureType {
        companion object {
            const val THROW = 0
            const val DONT_THROW = 1
            val nickNames = arrayOf("选中丢肥料", "选中不丢肥料")
        }
    }

    /**
     * @brief 邀请摆摊操作类型
     */
    interface StallInviteShopType {
        companion object {
            const val INVITE = 0
            const val DONT_INVITE = 1
            val nickNames = arrayOf("选中邀请", "选中不邀请")
        }
    }

    companion object {
        private const val TAG = "AntStall"

        /**
         * @brief 任务类型列表
         */
        private val TASK_TYPE_LIST = listOf(
            "ANTSTALL_NORMAL_OPEN_NOTICE",  // 开启摊新村收益提醒
            "tianjiashouye",                 // 添加首页
            "ANTSTALL_ELEME_VISIT",          // 去饿了么果园逛一逛
            "ANTSTALL_TASK_diantao202311",   // 去点淘赚元宝提现
            "ANTSTALL_TASK_nongchangleyuan"  // 农场乐园
        )
    }
}


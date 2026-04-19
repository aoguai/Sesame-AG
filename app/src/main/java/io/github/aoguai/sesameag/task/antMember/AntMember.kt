package io.github.aoguai.sesameag.task.antMember

import android.annotation.SuppressLint
import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.Status.Companion.canMemberPointExchangeBenefitToday
import io.github.aoguai.sesameag.data.Status.Companion.canMemberSignInToday
import io.github.aoguai.sesameag.data.Status.Companion.hasFlagToday
import io.github.aoguai.sesameag.data.Status.Companion.memberPointExchangeBenefitToday
import io.github.aoguai.sesameag.data.Status.Companion.memberSignInToday
import io.github.aoguai.sesameag.data.Status.Companion.setFlagToday
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.entity.MemberBenefit
import io.github.aoguai.sesameag.entity.SesameGift
import io.github.aoguai.sesameag.hook.ApplicationHookConstants
import io.github.aoguai.sesameag.hook.internal.LocationHelper.requestLocationSuspend
import io.github.aoguai.sesameag.hook.internal.SecurityBodyHelper.getSecurityBodyData
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.withDesc
import io.github.aoguai.sesameag.model.modelFieldExt.BooleanModelField
import io.github.aoguai.sesameag.model.modelFieldExt.SelectModelField
import io.github.aoguai.sesameag.util.TaskBlacklist.autoAddToBlacklist
import io.github.aoguai.sesameag.task.ModelTask
import io.github.aoguai.sesameag.task.antOrchard.AntOrchardRpcCall.orchardSpreadManure
import io.github.aoguai.sesameag.task.antOrchard.UrlUtil
import io.github.aoguai.sesameag.util.ActionDelayUtil
import io.github.aoguai.sesameag.util.CoroutineUtils
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.Log.record
import io.github.aoguai.sesameag.util.ResChecker
import io.github.aoguai.sesameag.util.TaskBlacklist
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.MemberBenefitsMap
import io.github.aoguai.sesameag.util.maps.SesameGiftMap
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern
import kotlin.math.max

class AntMember : ModelTask() {
    override fun getName(): String {
        return "会员"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.MEMBER
    }

    override fun getIcon(): String {
        return "AntMember.png"
    }

    internal var memberSign: BooleanModelField? = null
    internal var memberTask: BooleanModelField? = null
    internal var memberPointExchangeBenefit: BooleanModelField? = null
    private var memberPointExchangeBenefitList: SelectModelField? = null
    internal var collectSesame: BooleanModelField? = null
    internal var collectSesameWithOneClick: BooleanModelField? = null
    internal var sesameTask: BooleanModelField? = null
    private var collectInsuredGold: BooleanModelField? = null
    private var enableGameCenter: BooleanModelField? = null
    internal var merchantSign: BooleanModelField? = null
    internal var merchantKmdk: BooleanModelField? = null
    internal var merchantMoreTask: BooleanModelField? = null
    internal var beanSignIn: BooleanModelField? = null
    internal var beanExchangeBubbleBoost: BooleanModelField? = null

    // 芝麻炼金
    internal var sesameAlchemy: BooleanModelField? = null

    // 芝麻树
    internal var enableZhimaTree: BooleanModelField? = null

    /*//年度回顾
    private var annualReview: BooleanModelField? = null*/

    // 黄金票配置 - 签到
    internal var enableGoldTicket: BooleanModelField? = null

    // 黄金票配置 - 提取/兑换
    internal var enableGoldTicketConsume: BooleanModelField? = null

    /** 账单 贴纸 功能开关 */
    private var collectStickers: BooleanModelField? = null

    // 【新增】芝麻粒兑换
    internal var sesameGrainExchange: BooleanModelField? = null
    private var sesameGrainExchangeList: SelectModelField? = null

    private val sesameTaskRefreshRoundLimit = 8
    private val memberTaskBlacklistModule = "支付宝会员"
    private val sesameAlchemyTaskBlacklistModule = "芝麻炼金"
    private val goldTicketTaskBlacklistModule = "黄金票"

    private data class SesameFeedbackItem(
        val title: String,
        val creditFeedbackId: String,
        val potentialSize: String
    )

    private data class CurrentMemberTask(
        val taskConfigId: String,
        val taskProcessId: String,
        val title: String,
        val awardPoint: String,
        val targetBusiness: String,
        val simpleTaskConfig: JSONObject,
        val adBizId: String
    )

    private data class MemberTaskProcessAward(
        val taskProcessId: String,
        val awardRelatedOutBizNo: String,
        val title: String,
        val awardPoint: Int,
        val stageIndex: Int
    )

    private enum class CurrentMemberTaskVerifyState {
        CONFIRMED,
        UNCONFIRMED
    }

    private data class SesameTaskBatchResult(
        val completedCount: Int = 0,
        val skippedCount: Int = 0,
        val interrupted: Boolean = false
    )

    internal data class SesameTaskRunSummary(
        val finishedAllRounds: Boolean = false,
        val completedCount: Int = 0,
        val skippedCount: Int = 0,
        val interrupted: Boolean = false
    )

    private data class ZhimaTreeTaskRef(
        val title: String,
        val prizeName: String,
        val status: String,
        val taskId: String?,
        val taskIdCandidates: List<String>,
        val needManuallyReceiveAward: Boolean
    ) {
        fun describeCandidates(): String {
            if (taskIdCandidates.isEmpty()) {
                return "<empty>"
            }
            return taskIdCandidates.joinToString(" | ") { it.ifBlank { "<blank>" } }
        }
    }

    private data class ZhimaTreeActionResult(
        val success: Boolean,
        val response: JSONObject?,
        val rawResponse: String?
    )

    private data class ZhimaTreeTaskRefreshResult(
        val tasks: List<ZhimaTreeTaskRef>,
        val queriedSourceCount: Int
    ) {
        val hasConfirmedSnapshot: Boolean
            get() = queriedSourceCount > 0
    }

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(BooleanModelField("memberSign", "会员签到", false).withDesc(
            "执行会员中心每日签到并领取会员积分。"
        ).also {
            memberSign = it
        })
        modelFields.addField(BooleanModelField("memberTask", "会员任务", false).withDesc(
            "执行会员中心每日任务，完成后统一领取会员积分。"
        ).also {
            memberTask = it
        })



        modelFields.addField(
            BooleanModelField(
                "memberPointExchangeBenefit", "会员积分 | 兑换权益", false
            ).withDesc("按下方兑换列表自动尝试兑换会员权益或道具。").also { memberPointExchangeBenefit = it })
        modelFields.addField(
            SelectModelField(
                "memberPointExchangeBenefitList",
                "会员积分 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                MemberBenefit.getList()
            }.withDesc("勾选允许自动兑换的会员权益，需同时开启上方兑换开关才会处理。").also { memberPointExchangeBenefitList = it })


        modelFields.addField(
            BooleanModelField(
                "sesameGrainExchange", "芝麻信用 | 芝麻粒兑换道具", false
            ).withDesc("使用芝麻粒兑换已勾选的道具，适合长期清理库存。").also { sesameGrainExchange = it })

        // 使用 SesameGiftMap 来存储和回显商品名称
        modelFields.addField(
            SelectModelField(
                "sesameGrainExchangeList",
                "芝麻信用 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                SesameGift.getList()
            }.withDesc("勾选允许自动兑换的芝麻粒商品，需同时开启上方兑换开关才会逐项尝试。").also { sesameGrainExchangeList = it })

        modelFields.addField(
            BooleanModelField(
                "sesameTask", "芝麻信用|芝麻粒信用任务", false
            ).withDesc("执行芝麻信用的涨分进度与芝麻粒相关每日任务。").also { sesameTask = it })
        modelFields.addField(BooleanModelField("collectSesame", "芝麻信用|芝麻粒领取", false).withDesc(
            "统一领取芝麻粒、阶段奖励和其他可收取的芝麻相关奖励。"
        ).also {
            collectSesame = it
        })
        modelFields.addField(
            BooleanModelField(
                "collectSesameWithOneClick", "芝麻信用|芝麻粒领取使用一键收取", false
            ).withDesc("需同时开启芝麻粒领取，优先走一键收取接口领取芝麻粒，速度更快但依赖页面状态。").also { collectSesameWithOneClick = it })
        // 芝麻炼金
        modelFields.addField(
            BooleanModelField(
                "sesameAlchemy", "芝麻炼金", false
            ).withDesc("执行芝麻粒炼金的签到、任务和时段奖励领取。").also { sesameAlchemy = it })
        // 芝麻树
        modelFields.addField(BooleanModelField("enableZhimaTree", "芝麻信用|芝麻树", false).withDesc(
            "执行芝麻树相关签到、任务和奖励领取。"
        ).also {
            enableZhimaTree = it
        })


        modelFields.addField(
            BooleanModelField(
                "collectInsuredGold", "蚂蚁保|保障金领取", false
            ).withDesc("领取蚂蚁保页面可收取的签到保障金和活动保障金。").also { collectInsuredGold = it })

        // 黄金票配置
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicket", "黄金票签到", false
            ).withDesc("执行黄金票首页签到与日常收取，持续累积黄金票。").also { enableGoldTicket = it })
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicketConsume", "黄金票提取(兑换黄金)", false
            ).withDesc("黄金票达到提取条件后自动兑换或提取黄金。").also { enableGoldTicketConsume = it })
        modelFields.addField(BooleanModelField("enableGameCenter", "游戏中心签到", false).withDesc(
            "执行游戏中心签到、平台任务，并领取可收取的玩乐豆奖励。"
        ).also {
            enableGameCenter = it
        })
        modelFields.addField(
            BooleanModelField(
                "merchantSign", "商家服务|签到", false
            ).withDesc("执行商家服务每日签到，包含可领取时会顺带处理招财金签到积分。").also { merchantSign = it })
        modelFields.addField(
            BooleanModelField(
                "merchantKmdk", "商家服务|开门打卡", false
            ).withDesc("执行商家服务开门打卡的报名与上午签到，需在可用时段内运行。").also { merchantKmdk = it })
        modelFields.addField(
            BooleanModelField(
                "merchantMoreTask", "商家服务|积分任务", false
            ).withDesc("执行商家服务积分任务，并顺带领取任务产出的积分球奖励。").also {
                merchantMoreTask = it
            })
        modelFields.addField(
            BooleanModelField(
                "beanSignIn", "安心豆签到", false
            ).withDesc("执行安心豆每日签到，领取当天可得的安心豆奖励。").also { beanSignIn = it })
        modelFields.addField(
            BooleanModelField(
                "beanExchangeBubbleBoost", "安心豆兑换时光加速器", false
            ).withDesc("在安心豆余额足够时自动兑换时光加速器。").also { beanExchangeBubbleBoost = it })
       /* modelFields.addField(
            BooleanModelField(
                "annualReview", "年度回顾", false
            ).also { annualReview = it })*/


        modelFields.addField(
            BooleanModelField("CollectStickers", "领取贴纸", false).withDesc(
                "扫描并领取当前账单周期内可领取的贴纸奖励。"
            ).also { collectStickers = it }
        )



        return modelFields
    }

    override fun runJava() {
        runBlocking {
            try {
                Log.member(TAG, "执行开始-${getName()}")
                requestLocationSuspend()

                val deferredTasks = mutableListOf<Deferred<Unit>>()
                val memberPointPlan = prepareMemberPointWorkflows(this, deferredTasks)
                val sesamePlan = prepareSesameWorkflows(this, deferredTasks)

                if (collectInsuredGold?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { collectInsuredGold() })
                }

                scheduleGoldTicketWorkflows(this, deferredTasks)

                if (enableGameCenter?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { enableGameCenter() })
                }

               /* if (annualReview!!.value) {   //年度回顾已下线
                    deferredTasks.add(async(Dispatchers.IO) { doAnnualReview() })
                }*/

                scheduleBeanWorkflows(this, deferredTasks)
                scheduleMerchantWorkflows(this, deferredTasks)

                if (collectStickers?.value == true) {
                    queryAndCollectStickers()
                }

                deferredTasks.awaitAll()
                finishMemberPointWorkflows(memberPointPlan)
                finishSesameWorkflows(sesamePlan)

            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            } finally {
                Log.member(TAG, "执行结束-${getName()}")
            }
        }
    }

    internal suspend fun runMerchantWorkflow() {
        val needKmdkSignIn =
            merchantKmdk?.value == true &&
                !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE) &&
                TimeUtil.isNowAfterTimeStr("0600") &&
                TimeUtil.isNowBeforeTimeStr("1200")
        val needKmdkSignUp =
            merchantKmdk?.value == true &&
                !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNUP_DONE)
        val needMerchantSign =
            merchantSign?.value == true &&
                !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_SIGN_DONE)
        val needMerchantMoreTask =
            merchantMoreTask?.value == true

        if (!(needKmdkSignIn || needKmdkSignUp || needMerchantSign || needMerchantMoreTask)) {
            Log.member(TAG, "⏭️ 今天已处理过商家服务相关任务，跳过执行")
            return
        }

        if (!canRunMerchantService()) {
            return
        }

        if (needMerchantSign) {
            if (doMerchantSign()) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_SIGN_DONE)
                collectMerchantPointBalls()
            }
        }
        if (needMerchantMoreTask) {
            doMerchantMoreTask()
        }
        if (merchantKmdk?.value == true && (needKmdkSignIn || needKmdkSignUp)) {
            if (needKmdkSignIn) {
                if (kmdkSignIn()) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE)
                }
            } else if (TimeUtil.isNowAfterTimeStr("1200")) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE)
            }
            if (needKmdkSignUp) {
                if (kmdkSignUp()) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNUP_DONE)
                }
            }
        }
    }

    internal fun handleGrowthGuideTasks() {
        try {
            if (ApplicationHookConstants.isOffline()) {
                Log.sesame("$TAG.handleGrowthGuideTasks", "信誉任务领取因离线模式跳过，保留后续重试机会")
                return
            }
            Log.sesame("$TAG.", "开始执行信誉任务领取")
            var resp: String?
            try {
                resp = AntMemberRpcCall.Zmxy.queryGrowthGuideToDoList()
            } catch (e: Throwable) {
                Log.printStackTrace("$TAG.handleGrowthGuideTasks.queryGrowthGuideToDoList", e)
                return
            }

            if (resp.isNullOrEmpty()) {
                Log.sesame("$TAG.handleGrowthGuideTasks", "信誉任务列表返回空")
                return
            }

            val root: JSONObject?
            try {
                root = JSONObject(resp)
            } catch (e: Throwable) {
                Log.printStackTrace("$TAG.handleGrowthGuideTasks.parseRootJson", e)
                return
            }

            if (!ResChecker.checkRes(TAG, root)) {
                Log.sesame(
                    "$TAG.handleGrowthGuideTasks", "信誉任务列表获取失败: " + root.optString("resultView", resp)
                )
                return
            }
            // 成长引导列表（不会用，只做计数）
            val growthGuideList = root.optJSONArray("growthGuideList")
            growthGuideList?.length() ?: 0

            // 待处理任务列表
            val toDoList = root.optJSONArray("toDoList")
            val toDoCount = toDoList?.length() ?: 0
            if (toDoList == null || toDoCount == 0) {
                return
            }

            for (i in 0..<toDoList.length()) {
                var task: JSONObject? = null
                try {
                    task = toDoList.optJSONObject(i)
                } catch (_: Throwable) {
                }

                if (task == null) continue

                val behaviorId = task.optString("behaviorId", "")
                val title = task.optString("title", "")
                val status = task.optString("status", "")
                val subTitle = task.optString("subTitle", "")

                // ===== 2.1 公益类任务 =====
                if ("wait_receive" == status) {
                    val openResp: String?
                    try {
                        openResp = AntMemberRpcCall.Zmxy.openBehaviorCollect(behaviorId)
                    } catch (e: Throwable) {
                        Log.printStackTrace("$TAG.handleGrowthGuideTasks.openBehaviorCollect", e)
                        continue
                    }

                    try {
                        val openJo = JSONObject(openResp)
                        if (ResChecker.checkRes(TAG, openJo)) {
                            Log.sesame("信誉任务[领取成功] $title")
                        } else {
                            Log.sesame(
                                "$TAG.handleGrowthGuideTasks", ("信誉任务[领取失败] behaviorId=" + behaviorId + " title=" + title + " resp=" + openResp)
                            )
                        }
                    } catch (e: Throwable) {
                        Log.printStackTrace(
                            "$TAG.handleGrowthGuideTasks.parseOpenBehaviorCollect", e
                        )
                    }
                    continue
                }

                // ===== 2.2 每日问答 =====
                if ("meiriwenda" == behaviorId && "wait_doing" == status) { //如果等待去做才执行，一般不会进入下面的今日已参与判断

                    if (subTitle.contains("今日已参与")) {
                        Log.sesame("信誉任务[每日问答] $subTitle（跳过答题）")
                        continue
                    }

                    try {
                        // ① 查询题目
                        val quizResp = AntMemberRpcCall.Zmxy.queryDailyQuiz(behaviorId)
                        val quizJo: JSONObject?
                        try {
                            quizJo = JSONObject(quizResp)
                        } catch (e: Throwable) {
                            Log.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parseDailyQuiz 每日问答[解析失败]$quizResp", e
                            )
                            continue
                        }

                        if (!ResChecker.checkRes(TAG, quizJo)) {
                            continue
                        }

                        val data = quizJo.optJSONObject("data")
                        if (data == null) {
                            Log.error("$TAG.handleGrowthGuideTasks", "每日问答[返回缺少data]")
                            continue
                        }

                        val qVo = data.optJSONObject("questionVo")
                        if (qVo == null) {
                            Log.error("$TAG.handleGrowthGuideTasks", "每日问答[缺少questionVo]")
                            continue
                        }

                        val rightAnswer = qVo.optJSONObject("rightAnswer")
                        if (rightAnswer == null) {
                            Log.error("$TAG.handleGrowthGuideTasks", "每日问答[缺少rightAnswer]")
                            continue
                        }

                        val bizDate = data.optLong("bizDate", 0L)
                        val questionId = qVo.optString("questionId", "")
                        val questionContent = qVo.optString("questionContent", "")
                        val answerId = rightAnswer.optString("answerId", "")
                        val answerContent = rightAnswer.optString("answerContent", "")

                        if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                            Log.error("$TAG.handleGrowthGuideTasks", "每日问答[关键字段缺失]")
                            continue
                        }

                        // ② 提交答案
                        val pushResp = AntMemberRpcCall.Zmxy.pushDailyTask(
                            behaviorId, bizDate, answerId, questionId, "RIGHT"
                        )

                        val pushJo: JSONObject?
                        try {
                            pushJo = JSONObject(pushResp)
                        } catch (e: Throwable) {
                            Log.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 每日问答[提交解析失败]$quizResp", e
                            )
                            continue
                        }

                        if (ResChecker.checkRes(TAG, pushJo)) {
                            Log.sesame(
                                TAG,
                                ("信誉任务[每日答题成功] " + questionContent + " | 答案=" + answerContent + "(" + answerId + ")" + (if (subTitle.isEmpty()) "" else " | $subTitle"))
                            )
                        } else {
                            Log.error(
                                "$TAG.handleGrowthGuideTasks", "每日问答[提交失败] resp=$pushResp"
                            )
                        }
                    } catch (e: Throwable) {
                        Log.printStackTrace("$TAG.handleGrowthGuideTasks.meiriwenda", e)
                    }
                }

                // ===== 2.3 视频问答 =====
                if ("shipingwenda" == behaviorId && "wait_doing" == status) {
                    val bizDate = System.currentTimeMillis()
                    val questionId = "question3"
                    val answerId = "A"
                    val answerType = "RIGHT"

                    val pushResp = AntMemberRpcCall.Zmxy.pushDailyTask(
                        behaviorId, bizDate, answerId, questionId, answerType
                    )

                    val jo: JSONObject?
                    try {
                        jo = JSONObject(pushResp)
                    } catch (e: Throwable) {
                        Log.printStackTrace(
                            "$TAG.handleGrowthGuideTasks.parsePushDailyTask 视频问答[提交解析失败]$pushResp", e
                        )
                        continue  // 改为continue，避免return影响循环
                    }

                    if (ResChecker.checkRes(TAG, jo)) {
                        Log.sesame("信誉任务[视频问答提交成功] → ")
                    } else {
                        Log.error("$TAG.handleGrowthGuideTasks", "视频问答[提交失败] → $pushResp")
                    }
                }

                // ===== 2.4 芭芭农场施肥 =====
                if ("babanongchang_7d" == behaviorId && "wait_doing" == status) {
                    try {
                        // 假设getWua()方法存在，返回wua（为空即可）
                        val wua = getSecurityBodyData(4) // 传入空字符串
                        val source = "DNHZ_NC_zhimajingnangSF" // 从buttonUrl提取的source
                        Log.debug(TAG, "信誉任务[芭芭农场施肥] set Wua $wua")

                        val spreadManureDataStr = orchardSpreadManure(
                            Objects.requireNonNull(wua).toString(), source, false
                        )
                        val spreadManureData: JSONObject?
                        try {
                            spreadManureData = JSONObject(spreadManureDataStr)
                        } catch (e: Throwable) {
                            Log.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 芭芭农场[提交解析失败]$spreadManureDataStr", e
                            )
                            continue
                        }

                        if ("100" != spreadManureData.optString("resultCode")) {
                            Log.sesame(
                                TAG, "农场 orchardSpreadManure 错误：" + spreadManureData.optString("resultDesc")
                            )
                            continue
                        }

                        val taobaoDataStr = spreadManureData.optString("taobaoData", "")
                        if (taobaoDataStr.isEmpty()) {
                            Log.error("$TAG.handleGrowthGuideTasks", "芭芭农场[缺少taobaoData]")
                            continue
                        }

                        val spreadTaobaoData: JSONObject?
                        try {
                            spreadTaobaoData = JSONObject(taobaoDataStr)
                        } catch (e: Throwable) {
                            Log.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 芭芭农场[taobaoData解析失败]$taobaoDataStr", e
                            )
                            continue
                        }

                        val currentStage = spreadTaobaoData.optJSONObject("currentStage")
                        if (currentStage == null) {
                            Log.error("$TAG.handleGrowthGuideTasks", "芭芭农场[缺少currentStage]")
                            continue
                        }

                        val stageText = currentStage.optString("stageText", "")
                        val statistics = spreadTaobaoData.optJSONObject("statistics")
                        val dailyAppWateringCount = statistics?.optInt("dailyAppWateringCount", 0) ?: 0

                        Log.farm("今日农场已施肥💩 $dailyAppWateringCount 次 [$stageText]")

                        Log.sesame(
                            TAG, "信誉任务[芭芭农场施肥成功] $title | 已施肥 $dailyAppWateringCount 次"
                        )
                    } catch (e: Throwable) {
                        Log.printStackTrace("$TAG.handleGrowthGuideTasks.babanongchang", e)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.printStackTrace("$TAG.handleGrowthGuideTasks.Fatal", e)
        }
    }

    /*
     * 年度回顾已下线：相关 RPC/组件字段不再维护。
     * 为保证编译通过，暂时整体注释掉这一段实现（含 RPC/组件常量未补齐部分）。
     *
     * 如需恢复：请先补齐 AntMemberRpcCall.annualReview* 与组件常量后再启用。
     *
    /**
     * 年度回顾任务：通过 programInvoke 查询并自动完成任务
     *
     *
     * 1) alipay.imasp.program.programInvoke + ..._task_reward_query 查询 playTaskOrderInfoList
     * 2) 对于 taskStatus = "init" 的任务，使用 ..._task_reward_apply(code) 领取，得到 recordNo
     * 3) 使用 ..._task_reward_process(code, recordNo) 上报完成，服务端自动发放成长值奖励
     */
    private suspend fun doAnnualReview(): Unit = CoroutineUtils.run {
        try {
            Log.member("$TAG.doAnnualReview", "年度回顾🎞[开始执行]")

            val resp = AntMemberRpcCall.annualReviewQueryTasks()
            if (resp == null || resp.isEmpty()) {
                Log.member("$TAG.doAnnualReview", "年度回顾[查询返回空]")
                return
            }

            val root: JSONObject?
            try {
                root = JSONObject(resp)
            } catch (e: Throwable) {
                Log.printStackTrace("$TAG.doAnnualReview.parseRoot", e)
                return
            }

            if (!root.optBoolean("isSuccess", false)) {
                Log.member("$TAG.doAnnualReview", "年度回顾[查询失败]#$resp")
                return
            }

            val components = root.optJSONObject("components")
            if (components == null || components.length() == 0) {
                Log.member("$TAG.doAnnualReview", "年度回顾[components 为空]")
                return
            }

            var queryComp = components.optJSONObject(AntMemberRpcCall.ANNUAL_REVIEW_QUERY_COMPONENT)
            if (queryComp == null) {
                // 兜底：取第一个组件
                try {
                    val it = components.keys()
                    if (it.hasNext()) {
                        queryComp = components.optJSONObject(it.next())
                    }
                } catch (_: Throwable) {
                }
            }
            if (queryComp == null) {
                Log.member("$TAG.doAnnualReview", "年度回顾[未找到查询组件]")
                return
            }
            if (!queryComp.optBoolean("isSuccess", true)) {
                Log.member("$TAG.doAnnualReview", "年度回顾[查询组件返回失败]")
                return
            }

            val content = queryComp.optJSONObject("content")
            if (content == null) {
                Log.member("$TAG.doAnnualReview", "年度回顾[content 为空]")
                return
            }

            val taskList = content.optJSONArray("playTaskOrderInfoList")
            if (taskList == null || taskList.length() == 0) {
                Log.member("$TAG.doAnnualReview", "年度回顾[当前无可处理任务]")
                return
            }

            var candidate = 0
            var applied = 0
            var processed = 0
            var failed = 0

            for (i in 0..<taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue

                val taskStatus = task.optString("taskStatus", "")
                if ("init" != taskStatus) {
                    // 已完成/已领奖等状态直接跳过
                    continue
                }
                candidate++

                var code = task.optString("code", "")
                if (code.isEmpty()) {
                    val extInfo = task.optJSONObject("extInfo")
                    if (extInfo != null) {
                        code = extInfo.optString("taskId", "")
                    }
                }
                if (code.isEmpty()) {
                    failed++
                    continue
                }

                var taskName = code
                val displayInfo = task.optJSONObject("displayInfo")
                if (displayInfo != null) {
                    val name = displayInfo.optString(
                        "taskName", displayInfo.optString("activityName", code)
                    )
                    if (!name.isEmpty()) {
                        taskName = name
                    }
                }

                // ========== Step 1: 领取任务 (apply) ==========
                val applyResp = AntMemberRpcCall.annualReviewApplyTask(code)
                if (applyResp == null || applyResp.isEmpty()) {
                    Log.member("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#响应为空")
                    failed++
                    continue
                }

                val applyRoot: JSONObject?
                try {
                    applyRoot = JSONObject(applyResp)
                } catch (e: Throwable) {
                    Log.printStackTrace("$TAG.doAnnualReview.parseApply", e)
                    failed++
                    continue
                }
                if (!applyRoot.optBoolean("isSuccess", false)) {
                    Log.member("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#$applyResp")
                    failed++
                    continue
                }
                val applyComps = applyRoot.optJSONObject("components")
                if (applyComps == null) {
                    failed++
                    continue
                }
                var applyComp = applyComps.optJSONObject(AntMemberRpcCall.ANNUAL_REVIEW_APPLY_COMPONENT)
                if (applyComp == null) {
                    try {
                        val it2 = applyComps.keys()
                        if (it2.hasNext()) {
                            applyComp = applyComps.optJSONObject(it2.next())
                        }
                    } catch (_: Throwable) {
                    }
                }
                if (applyComp == null || !applyComp.optBoolean("isSuccess", true)) {
                    failed++
                    continue
                }
                val applyContent = applyComp.optJSONObject("content")
                if (applyContent == null) {
                    failed++
                    continue
                }
                val claimedTask = applyContent.optJSONObject("claimedTask")
                if (claimedTask == null) {
                    failed++
                    continue
                }
                val recordNo = claimedTask.optString("recordNo", "")
                if (recordNo.isEmpty()) {
                    failed++
                    continue
                }
                applied++

                ActionDelayUtil.humanActionDelay(500L)

                // ========== Step 2: 提交任务完成 (process) ==========
                val processResp = AntMemberRpcCall.annualReviewProcessTask(code, recordNo)
                if (processResp == null || processResp.isEmpty()) {
                    Log.member("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#响应为空")
                    failed++
                    continue
                }

                val processRoot: JSONObject?
                try {
                    processRoot = JSONObject(processResp)
                } catch (e: Throwable) {
                    Log.printStackTrace("$TAG.doAnnualReview.parseProcess", e)
                    failed++
                    continue
                }
                if (!processRoot.optBoolean("isSuccess", false)) {
                    Log.member("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#$processResp")
                    failed++
                    continue
                }
                val processComps = processRoot.optJSONObject("components")
                if (processComps == null) {
                    failed++
                    continue
                }
                var processComp = processComps.optJSONObject(AntMemberRpcCall.ANNUAL_REVIEW_PROCESS_COMPONENT)
                if (processComp == null) {
                    try {
                        val it3 = processComps.keys()
                        if (it3.hasNext()) {
                            processComp = processComps.optJSONObject(it3.next())
                        }
                    } catch (_: Throwable) {
                    }
                }
                if (processComp == null || !processComp.optBoolean("isSuccess", true)) {
                    failed++
                    continue
                }
                val processContent = processComp.optJSONObject("content")
                if (processContent == null) {
                    failed++
                    continue
                }
                val processedTask = processContent.optJSONObject("processedTask")
                if (processedTask == null) {
                    failed++
                    continue
                }
                val newStatus = processedTask.optString("taskStatus", "")
                var rewardStatus = processedTask.optString("rewardStatus", "")

                // ========== Step 3: 如仍未发奖，则调用 get_reward 领取奖励 ==========
                if (!"success".equals(rewardStatus, ignoreCase = true)) {
                    try {
                        val rewardResp = AntMemberRpcCall.annualReviewGetReward(code, recordNo)
                        if (rewardResp != null && !rewardResp.isEmpty()) {
                            val rewardRoot = JSONObject(rewardResp)
                            if (rewardRoot.optBoolean("isSuccess", false)) {
                                val rewardComps = rewardRoot.optJSONObject("components")
                                if (rewardComps != null) {
                                    var rewardComp = rewardComps.optJSONObject(AntMemberRpcCall.ANNUAL_REVIEW_GET_REWARD_COMPONENT)
                                    if (rewardComp == null) {
                                        try {
                                            val it4 = rewardComps.keys()
                                            if (it4.hasNext()) {
                                                rewardComp = rewardComps.optJSONObject(it4.next())
                                            }
                                        } catch (_: Throwable) {
                                        }
                                    }
                                    if (rewardComp != null && rewardComp.optBoolean(
                                            "isSuccess", true
                                        )
                                    ) {
                                        val rewardContent = rewardComp.optJSONObject("content")
                                        if (rewardContent != null) {
                                            var rewardTask = rewardContent.optJSONObject("processedTask")
                                            if (rewardTask == null) {
                                                rewardTask = rewardContent.optJSONObject("claimedTask")
                                            }
                                            if (rewardTask != null) {
                                                val rs = rewardTask.optString("rewardStatus", "")
                                                if (!rs.isEmpty()) {
                                                    rewardStatus = rs
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.printStackTrace("$TAG.doAnnualReview.getReward", e)
                    }
                }

                processed++
                Log.member("年度回顾🎞[任务完成]$taskName#状态=$newStatus 奖励状态=$rewardStatus")
            }

            Log.member(
                "$TAG.doAnnualReview", "年度回顾🎞[执行结束] 待处理=$candidate 已领取=$applied 已提交=$processed 失败=$failed"
            )
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.doAnnualReview", t)
        }
    }

    */

    /**
     * 会员积分0元兑，权益道具兑换
     */
    internal fun memberPointExchangeBenefit() {
        if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE)) {
            return
        }
        val whiteList: Set<String> = memberPointExchangeBenefitList?.value
            ?.filterNotNull()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        if (whiteList.isNotEmpty() && whiteList.all { !canMemberPointExchangeBenefitToday(it) }) {
            Log.member(TAG, "会员积分🎐兑换列表今日已全部处理，跳过执行")
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE)
            return
        }
        try {
            val userId = UserMap.currentUid
            Log.member(TAG, "会员积分商品加载..")
            val remainingWhiteList: MutableSet<String>? = if (whiteList.isNotEmpty()) whiteList.toMutableSet() else null
            // 1. 分类配置直接放在函数内部
            val categoryMap = mapOf(
                "公益道具" to listOf("94000SR2025022012011004"),
                "出行旅游" to listOf("94000SR2025010611441006", "94000SR2025010611458001"),
                "餐饮" to listOf("94000SR2025110315351006"),
                "皮肤藏品" to listOf("94000SR2025110315357001", "94000SR2025111015444005"),
                "理财还款" to listOf("94000SR2025011411575008", "94000SR2025091814834002"),
                "红包神券" to listOf("94000SR2025092414916001"),
                "充值缴费" to listOf("94000SR2025011611640002", "94000SR2025091814821018")
            )
            // 3. 遍历分类
            categoryMap.forEach { (catName, ids) ->
                var currentPage = 1
                var hasNextPage = true
                while (hasNextPage) {//此处请求过载，容易风控，循环频繁请求会炸
                    GlobalThreadPools.sleepCompat(1000L)
                    val responseStr = AntMemberRpcCall.queryDeliveryZoneDetail(ids, currentPage, 48)
                    if (responseStr.isNullOrEmpty()) {
                        Log.error(TAG, "分类[$catName] 接口返回空字符串")
                        break
                    }
                    val jo = JSONObject(responseStr)
                    if (!ResChecker.checkRes(TAG, jo)) {
                        Log.error(TAG, "分类[$catName] 校验失败: $responseStr")
                        break
                    }
                    val benefits = jo.optJSONArray("briefConfigInfos")
                    if (benefits == null || benefits.length() == 0) {
                        Log.error(TAG, "分类[$catName] 第 $currentPage 页没有权益数据")
                        break
                    }
                    for (i in 0 until benefits.length()) {
                        val rawItem = benefits.getJSONObject(i)
                        // 兼容 benefitInfo 嵌套结构
                        val benefit = if (rawItem.has("benefitInfo")) rawItem.getJSONObject("benefitInfo") else rawItem
                        val name = benefit.optString("name", "未知")
                        val benefitId = benefit.optString("benefitId")
                        val itemId = benefit.optString("itemId")
                        val pointNeeded = benefit.optJSONObject("pricePresentation")?.optString("point") ?: "0"
                        if (benefitId.isEmpty()) {
                            Log.member(TAG, "商品[$name] 没有 benefitId，跳过")
                            continue
                        }
                        // 记录 benefitId 映射关系
                        IdMapManager.getInstance(MemberBenefitsMap::class.java).add(benefitId, name)
                        // 校验是否在白名单
                        val inWhiteList = whiteList.contains(benefitId)
                        if (!inWhiteList) {
                            // 如果不在白名单，保持安静，不刷 record 日志，或者你可以按需开启
                            continue
                        }
                        remainingWhiteList?.remove(benefitId)
                        // 校验频率限制
                        if (!canMemberPointExchangeBenefitToday(benefitId)) {
                            Log.member(TAG, "跳过[$name]: 今日已兑换过")
                            continue
                        }
                        // 5. 执行兑换
                        Log.member(TAG, "准备兑换[$name], ID: $benefitId, 需积分: $pointNeeded")
                        if (exchangeBenefit(benefitId, itemId, userId)) {
                            Log.member("会员积分🎐兑换[$name]#花费[$pointNeeded 积分]")
                        } else {
                            Log.member(TAG, "兑换失败: $name (ItemId: $itemId)")
                        }
                    }
                    val nextPageNum = jo.optInt("nextPageNum", 0)
                    if (nextPageNum > 0 && nextPageNum > currentPage) {
                        currentPage = nextPageNum
                    } else {
                        hasNextPage = false
                    }

                    if (remainingWhiteList != null && remainingWhiteList.isEmpty()) {
                        IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
                        Log.member(TAG, "会员积分🎐兑换列表已全部扫描到，提前结束")
                        setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE)
                        return
                    }
                }
                IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
                Log.member(TAG, "分类[$catName]处理完毕，已执行中间保存")
            }
            // 7. 保存映射表
            IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
            Log.member(TAG, "会员积分🎐全部分类任务处理完毕")
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE)

        } catch (t: Throwable) {
            Log.member(TAG, "memberPointExchangeBenefit 运行异常: ${t.message}")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun exchangeBenefit(benefitId: String, itemid: String, userid: String?): Boolean {
        try {
            val resString = AntMemberRpcCall.exchangeBenefit(benefitId, itemid, userid)
            val jo = JSONObject(resString)
            val resultCode = jo.optString("resultCode")

            if (resultCode == "BEYOND_BUYING_TIMES") {
                Log.member(TAG, "会员权益兑换已达上限，标记任务今日完成")
                memberPointExchangeBenefitToday(benefitId)
                return true
            }

            if (ResChecker.checkRes(TAG + "会员权益兑换失败:", jo)) {
                memberPointExchangeBenefitToday(benefitId)
                return true
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "exchangeBenefit 错误:", t)
        }
        return false
    }

    /**
     * 会员签到
     */
    /**
     * 会员签到
     */
    internal suspend fun doMemberSign(): Unit = CoroutineUtils.run {
        var signDoneToday = hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_SIGN_DONE)
        try {
            val uid = UserMap.currentUid
            if (!signDoneToday) {
                if (!canMemberSignInToday(uid)) {
                    signDoneToday = true
                } else {
                    val s = AntMemberRpcCall.queryMemberSigninCalendar()
                    val jo = JSONObject(s)
                    if (ResChecker.checkRes(TAG + "会员签到失败:", jo)) {
                        val currentSigned = jo.optBoolean("currentSigninStatus") || jo.optBoolean("autoSignInSuccess")
                        if (currentSigned) {
                            val signPoint = jo.optString("signinPoint", "0")
                            val signDays = jo.optString("signinSumDay", "-")
                            val signStatus = if (jo.optBoolean("autoSignInSuccess")) "签到成功" else "已签到"
                            Log.member("会员签到📅[${signPoint}积分]#$signStatus${signDays}天")
                            memberSignInToday(uid)
                            signDoneToday = true
                        } else {
                            Log.member(TAG, "会员签到📅[今日未自动签到]#$s")
                        }
                    } else {
                        val resultDesc = jo.optString("resultDesc", "")
                        if (resultDesc.contains("已签到") || resultDesc.contains("成功")) {
                            memberSignInToday(uid)
                            signDoneToday = true
                        }
                        Log.member(TAG, "会员签到📅[$resultDesc]")
                        Log.member(s)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doMemberSign err:", t)
        } finally {
            if (signDoneToday) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_SIGN_DONE)
            }
        }
    }

    internal suspend fun doAllMemberAvailableTaskCompat(): Unit = CoroutineUtils.run {
        try {
            val currentTaskResponse = AntMemberRpcCall.queryMemberTaskList()
            val currentTaskObject = JSONObject(currentTaskResponse)
            val stopReason = resolveMemberTaskQueryStopReason(currentTaskObject)
            if (stopReason != null) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY)
                Log.member(
                    TAG,
                    "会员任务[amic]#${buildMemberTaskQueryStopMessage(stopReason, currentTaskObject)}"
                )
                return@run
            }
            if (!ResChecker.checkRes(TAG, currentTaskObject)) {
                Log.error(
                    "$TAG.doAllMemberAvailableTaskCompat",
                    "会员任务新链路响应失败: " + currentTaskObject.optString("resultDesc", currentTaskResponse)
                )
                Log.member(TAG, "会员任务[amic]#新链路响应异常，回退legacy")
                doAllMemberAvailableTask()
                return@run
            }

            val currentTasks = buildCurrentMemberTasks(currentTaskObject)
            if (currentTasks.isEmpty()) {
                Log.member(TAG, "会员任务[amic]#未发现可执行任务，回退legacy")
                doAllMemberAvailableTask()
                return@run
            }

            var processedCount = 0
            for (task in currentTasks) {
                if (processCurrentMemberTask(task)) {
                    processedCount++
                }
                if (ApplicationHookConstants.isOffline()) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY)
                    Log.member(TAG, "会员任务[amic]#检测到离线模式，今日停止继续刷新")
                    return@run
                }
            }

            if (processedCount == 0) {
                Log.member(TAG, "会员任务[amic]#当前列表无可安全执行任务，本轮结束，后续轮次继续查询")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doAllMemberAvailableTaskCompat err:", t)
        }
    }

    private fun buildMemberTaskProcessAwards(jsonObject: JSONObject): List<MemberTaskProcessAward> {
        val taskProcessList = jsonObject.optJSONArray("availableTaskProcessList") ?: return emptyList()
        val awardList = mutableListOf<MemberTaskProcessAward>()
        val dedupKeys = LinkedHashSet<String>()
        for (i in 0 until taskProcessList.length()) {
            val taskProcess = taskProcessList.optJSONObject(i) ?: continue
            val taskProcessId = taskProcess.optString("taskProcessId")
            if (taskProcessId.isEmpty()) {
                continue
            }
            val taskConfig = taskProcess.optJSONObject("taskConfig")
            val taskTitle = taskConfig?.optString("title").orEmpty().ifEmpty {
                taskProcess.optString("title", "会员任务")
            }
            val stageProcessList = taskProcess.optJSONArray("stageProcessList") ?: continue
            for (stageIndexInList in 0 until stageProcessList.length()) {
                val stageProcess = stageProcessList.optJSONObject(stageIndexInList) ?: continue
                val stageStatus = stageProcess.optString("stageStatus")
                val awardRelatedOutBizNo = stageProcess.optString("awardRelatedOutBizNo")
                if (!stageStatus.equals("COMPLETE", true) || awardRelatedOutBizNo.isEmpty()) {
                    continue
                }
                val stageIndex = stageProcess.optInt("stageIndex", stageIndexInList + 1)
                val dedupKey = "$taskProcessId#$awardRelatedOutBizNo"
                if (!dedupKeys.add(dedupKey)) {
                    continue
                }
                awardList.add(
                    MemberTaskProcessAward(
                        taskProcessId = taskProcessId,
                        awardRelatedOutBizNo = awardRelatedOutBizNo,
                        title = taskTitle,
                        awardPoint = stageProcess.optInt("awardPoint", 0),
                        stageIndex = stageIndex
                    )
                )
            }
        }
        return awardList
    }

    internal suspend fun collectMemberTaskProcessAwards(): Int = CoroutineUtils.run {
        try {
            val response = AntMemberRpcCall.queryMemberTaskProcessList()
            val taskListObject = JSONObject(response)
            if (!ResChecker.checkRes(TAG + "查询会员阶段奖励失败:", taskListObject)) {
                Log.member(
                    TAG,
                    "会员任务[阶段奖励]#查询失败:" + taskListObject.optString("resultDesc", response)
                )
                return@run 0
            }

            val awardList = buildMemberTaskProcessAwards(taskListObject)
            var claimedCount = 0
            for (award in awardList) {
                val awardResponse = AntMemberRpcCall.awardMemberTaskProcess(
                    award.awardRelatedOutBizNo,
                    award.taskProcessId
                )
                val awardObject = JSONObject(awardResponse)
                if (!ResChecker.checkRes(TAG + "领取会员阶段奖励失败:", awardObject)) {
                    Log.member(
                        TAG,
                        "会员任务[${award.title}]#阶段奖励领取失败:" + awardObject.optString("resultDesc", awardResponse)
                    )
                    continue
                }
                val stageSuffix = if (award.stageIndex > 0) "-阶段${award.stageIndex}" else ""
                if (award.awardPoint > 0) {
                    Log.member("会员任务[${award.title}$stageSuffix]#获得积分${award.awardPoint}")
                } else {
                    Log.member("会员任务[${award.title}$stageSuffix]#领取阶段奖励")
                }
                claimedCount++
                delay(300)
            }
            return@run claimedCount
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "collectMemberTaskProcessAwards err:", t)
            return@run 0
        }
    }

    /**
     * 会员任务-逛一逛
     * 单次执行 1
     */
    private suspend fun doAllMemberAvailableTask(): Unit = CoroutineUtils.run {
        try {
            val legacyTaskResponse = AntMemberRpcCall.queryLegacyAllStatusTaskList()
            val legacyTaskObject = JSONObject(legacyTaskResponse)
            val stopReason = resolveMemberTaskQueryStopReason(legacyTaskObject)
            if (stopReason != null) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY)
                Log.member(
                    TAG,
                    "会员任务🎖️[legacy]#${buildMemberTaskQueryStopMessage(stopReason, legacyTaskObject)}"
                )
                return@run
            }
            if (!ResChecker.checkRes(TAG, legacyTaskObject)) {
                Log.error(
                    "$TAG.doAllMemberAvailableTask",
                    "会员任务响应失败: " + legacyTaskObject.optString("resultDesc")
                )
                return@run
            }
            val taskList = legacyTaskObject.optJSONArray("availableTaskList") ?: JSONArray()
            if (taskList.length() <= 0) {
                Log.member(TAG, "会员任务🎖️[legacy]#任务列表数量为0，本轮结束，后续轮次继续查询")
                return@run
            }

            var processedCount = 0
            var safeCandidateCount = 0
            for (i in 0 until taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue
                val taskConfigInfo = task.optJSONObject("taskConfigInfo")
                val taskId = taskConfigInfo?.optLong("id", 0L)?.takeIf { it > 0 }?.toString().orEmpty()
                if (taskConfigInfo != null && shouldKeepMemberTaskConfigId(taskId)) {
                    safeCandidateCount++
                }
                if (processLegacyMemberTask(task)) {
                    processedCount++
                }
                if (ApplicationHookConstants.isOffline()) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY)
                    Log.member(TAG, "会员任务🎖️[legacy]#检测到离线模式，今日停止继续刷新")
                    return@run
                }
            }

            if (processedCount == 0) {
                Log.member(
                    TAG,
                    if (safeCandidateCount > 0) {
                        "会员任务🎖️[legacy]#当前列表无可安全执行任务，本轮结束，后续轮次继续查询"
                    } else {
                        "会员任务🎖️[legacy]#列表仅含不兼容任务，本轮结束，后续轮次继续查询"
                    }
                )
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doAllMemberAvailableTask err:", t)
        }
    }

    private fun resolveMemberTaskQueryStopReason(jsonObject: JSONObject): String? {
        if (ApplicationHookConstants.isOffline()) {
            return "OFFLINE_MODE"
        }
        val code = sequenceOf(
            jsonObject.opt("resultCode")?.toString(),
            jsonObject.opt("errorCode")?.toString(),
            jsonObject.opt("error")?.toString(),
            jsonObject.opt("errorTip")?.toString()
        ).filterNotNull()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val desc = sequenceOf(
            jsonObject.opt("resultDesc")?.toString(),
            jsonObject.opt("memo")?.toString(),
            jsonObject.opt("desc")?.toString(),
            jsonObject.opt("errorMsg")?.toString(),
            jsonObject.opt("errorMessage")?.toString()
        ).filter { !it.isNullOrBlank() }
            .joinToString(" | ")
        val authLikeKeywords = listOf(
            "需要验证",
            "伺服器繁忙",
            "服务器繁忙",
            "請稍後再試",
            "请稍后再试",
            "稍后重试",
            "稍候再试",
            "操作太频繁",
            "过于频繁",
            "系统繁忙",
            "活动太火爆",
            "訪問異常",
            "访问异常"
        )
        if (
            code == "1009" ||
            authLikeKeywords.any { keyword -> desc.contains(keyword, ignoreCase = true) }
        ) {
            return "AUTH_LIKE"
        }
        if (code == "I07" || desc.contains("离线模式")) {
            return "OFFLINE_MODE"
        }
        return null
    }

    private fun buildMemberTaskQueryStopMessage(stopReason: String, jsonObject: JSONObject): String {
        val detail = sequenceOf(
            jsonObject.optString("resultDesc"),
            jsonObject.optString("memo"),
            jsonObject.optString("desc"),
            jsonObject.optString("errorMessage"),
            jsonObject.optString("errorMsg")
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        return when (stopReason) {
            "AUTH_LIKE" -> "检测到验证/服务器繁忙($detail)，停止今日继续刷新"
            "OFFLINE_MODE" -> "检测到离线模式($detail)，停止今日继续刷新"
            else -> "检测到异常($detail)，停止今日继续刷新"
        }
    }

    private fun buildCurrentMemberTasks(jsonObject: JSONObject): List<CurrentMemberTask> {
        val resultData = jsonObject.optJSONObject("resultData") ?: return emptyList()
        val taskProcessVOList = resultData.optJSONArray("taskProcessVOList") ?: return emptyList()
        val taskList = mutableListOf<CurrentMemberTask>()
        val dedupKeys = LinkedHashSet<String>()
        for (i in 0 until taskProcessVOList.length()) {
            val taskProcessObject = taskProcessVOList.optJSONObject(i) ?: continue
            if (isMemberTaskProcessFinished(taskProcessObject)) {
                continue
            }
            val simpleTaskConfig = taskProcessObject.optJSONObject("simpleTaskConfig") ?: continue
            val taskConfigId = resolveCurrentMemberTaskConfigId(taskProcessObject) ?: continue
            if (!shouldKeepMemberTaskConfigId(taskConfigId)) {
                continue
            }
            val unsupportedAdTaskReason = resolveUnsupportedMemberAdTaskReason(taskProcessObject, simpleTaskConfig)
            if (unsupportedAdTaskReason != null) {
                logSkippedMemberAdTask(
                    simpleTaskConfig.optString("title").ifEmpty {
                        simpleTaskConfig.optString("name").ifEmpty { "任务$taskConfigId" }
                    },
                    unsupportedAdTaskReason
                )
                continue
            }
            val adBizId = resolveMemberAdTaskBizId(taskProcessObject, simpleTaskConfig)
            val targetBusiness = resolveSupportedMemberTaskTargetBusiness(
                taskProcessObject.optJSONArray("targetBusiness") ?: simpleTaskConfig.optJSONArray("targetBusiness")
            )
            if (targetBusiness.isEmpty() && adBizId.isEmpty()) {
                continue
            }
            val taskProcessId = taskProcessObject.optString("processId").ifEmpty {
                taskProcessObject.optString("taskProcessId")
            }
            val dedupKey = when {
                taskProcessId.isNotEmpty() -> taskProcessId
                adBizId.isNotEmpty() -> "$taskConfigId#$adBizId"
                else -> taskConfigId
            }
            if (!dedupKeys.add(dedupKey)) {
                continue
            }
            taskList.add(
                CurrentMemberTask(
                    taskConfigId = taskConfigId,
                    taskProcessId = taskProcessId,
                    title = simpleTaskConfig.optString("title").ifEmpty {
                        simpleTaskConfig.optString("name").ifEmpty { "任务$taskConfigId" }
                    },
                    awardPoint = extractMemberTaskAwardPoint(simpleTaskConfig),
                    targetBusiness = targetBusiness,
                    simpleTaskConfig = simpleTaskConfig,
                    adBizId = adBizId
                )
            )
        }
        return taskList
    }

    @Throws(JSONException::class)
    private suspend fun processCurrentMemberTask(task: CurrentMemberTask): Boolean = CoroutineUtils.run {
        if (isMemberTaskInBlacklist(task.taskConfigId, task.title)) {
            Log.member(TAG, "会员任务[${task.title}]#黑名单任务，停止执行")
            return@run false
        }
        if (task.adBizId.isNotEmpty()) {
            return@run finishMemberAdTask(task.taskConfigId, task.title, task.awardPoint, task.adBizId)
        }
        val targetBusinessArray = task.targetBusiness.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (targetBusinessArray.size < 3) {
            return@run false
        }
        val bizType = targetBusinessArray[0]
        val bizSubType = targetBusinessArray[1]
        val bizParam = targetBusinessArray[2]
        val executeResponse = AntMemberRpcCall.executeMemberTask(bizParam, bizSubType, bizType)
        val executeObject = JSONObject(executeResponse)
        if (isSkippableMemberTaskRejection(executeObject)) {
            Log.member(TAG, "会员任务[${task.title}]#不满足营销规则，跳过执行")
            return@run false
        }
        if (!ResChecker.checkRes(TAG + "执行会员任务失败:", executeObject)) {
            Log.error(TAG, "执行任务失败:" + executeObject.optString("resultDesc", executeResponse))
            return@run false
        }
        when (checkCurrentMemberTaskFinished(task)) {
            CurrentMemberTaskVerifyState.CONFIRMED -> {
                if (task.awardPoint.isNotEmpty()) {
                    Log.member("会员任务[${task.title}]#获得积分${task.awardPoint}")
                } else {
                    Log.member("会员任务[${task.title}]#任务完成")
                }
                true
            }

            CurrentMemberTaskVerifyState.UNCONFIRMED -> {
                Log.member(
                    TAG,
                    if (task.taskProcessId.isNotEmpty()) {
                        "会员任务[${task.title}]#执行成功，跳过高风险全量刷新确认，状态待后续页面确认"
                    } else {
                        "会员任务[${task.title}]#执行成功，但当前缺少processId，跳过高风险二次确认"
                    }
                )
                true
            }
        }
    }

    private suspend fun checkCurrentMemberTaskFinished(task: CurrentMemberTask): CurrentMemberTaskVerifyState {
        return try {
            if (task.taskProcessId.isEmpty()) {
                return CurrentMemberTaskVerifyState.UNCONFIRMED
            }

            val detailResponse = AntMemberRpcCall.querySingleTaskProcessDetail(task.taskProcessId)
            val detailObject = JSONObject(detailResponse)
            if (!ResChecker.checkRes(TAG + "查询会员任务详情失败:", detailObject)) {
                Log.error(
                    "$TAG.checkCurrentMemberTaskFinished",
                    "会员任务详情响应失败: " + detailObject.optString("resultDesc", detailResponse)
                )
                return CurrentMemberTaskVerifyState.UNCONFIRMED
            }

            val taskProcessObject = detailObject.optJSONObject("resultData")?.optJSONObject("taskProcessVO")
                ?: detailObject.optJSONObject("taskProcessVO")
            if (isMemberTaskProcessFinished(taskProcessObject)) {
                CurrentMemberTaskVerifyState.CONFIRMED
            } else {
                CurrentMemberTaskVerifyState.UNCONFIRMED
            }
        } catch (_: JSONException) {
            CurrentMemberTaskVerifyState.UNCONFIRMED
        }
    }

    private fun resolveCurrentMemberTaskConfigId(taskObject: JSONObject): String? {
        val directValue = taskObject.optString("taskConfigId")
        if (directValue.isNotEmpty()) {
            return directValue
        }
        val simpleTaskConfig = taskObject.optJSONObject("simpleTaskConfig")
            ?: taskObject.optJSONObject("taskConfigInfo")
            ?: taskObject.optJSONObject("taskConfig")
        if (simpleTaskConfig != null) {
            val configId = simpleTaskConfig.optString("configId")
            if (configId.isNotEmpty()) {
                return configId
            }
            val taskConfigId = simpleTaskConfig.optString("taskConfigId")
            if (taskConfigId.isNotEmpty()) {
                return taskConfigId
            }
            val id = simpleTaskConfig.optLong("id", 0L)
            if (id > 0) {
                return id.toString()
            }
        }
        val id = taskObject.optLong("id", 0L)
        return if (id > 0) id.toString() else null
    }

    private fun isMemberTaskProcessFinished(taskProcessObject: JSONObject?): Boolean {
        if (taskProcessObject == null) {
            return false
        }
        val status = taskProcessObject.optString("status")
        if (
            status.equals("AWARDED", true) ||
            status.equals("SUCCESS", true) ||
            status.equals("COMPLETE", true) ||
            status.equals("DONE", true) ||
            status.equals("FINISHED", true)
        ) {
            return true
        }
        val subStatus = taskProcessObject.optString("subStatus")
        if (
            subStatus.equals("AWARDED", true) ||
            subStatus.equals("SUCCESS", true) ||
            subStatus.equals("COMPLETE", true) ||
            subStatus.equals("DONE", true) ||
            subStatus.equals("FINISHED", true)
        ) {
            return true
        }
        val currentCount = taskProcessObject.optLong("currentCount", -1L)
        val targetCount = taskProcessObject.optLong("targetCount", -1L)
        if (targetCount > 0 && currentCount >= targetCount) {
            return true
        }
        val extInfo = taskProcessObject.optJSONObject("extInfo")
        if (extInfo != null) {
            if (extInfo.optString("awardCurrentPoint").isNotEmpty() || extInfo.optString("awardSuccessTime").isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun extractMemberTaskAwardPoint(simpleTaskConfig: JSONObject): String {
        val stageVOList = simpleTaskConfig.optJSONArray("stageVOList")
        if (stageVOList != null && stageVOList.length() > 0) {
            val stageObject = stageVOList.optJSONObject(0)
            val awardParam = stageObject?.optJSONObject("awardParam")
            val awardPoint = awardParam?.optString("awardParamPoint").orEmpty()
            if (awardPoint.isNotEmpty()) {
                return awardPoint
            }
        }
        return simpleTaskConfig.optJSONObject("awardParam")?.optString("awardParamPoint").orEmpty()
    }

    private fun isSkippableMemberTaskRejection(response: JSONObject): Boolean {
        val resultCode = response.optString("resultCode").ifEmpty {
            response.optString("errorCode")
        }
        val resultDesc = response.optString("resultDesc").ifEmpty {
            response.optString("errorMsg")
        }
        return resultCode == "NOT_PROMO_RULE_QUALIFIED" ||
            resultDesc.contains("不满足任务的营销规则条件")
    }

    /**
     * 芝麻信用任务
     */
    internal suspend fun doAllAvailableSesameTask(): SesameTaskRunSummary = CoroutineUtils.run {
        var overallCompletedTasks = 0
        var overallSkippedTasks = 0
        try {
            var round = 0
            var finishedAllRounds = false
            var interrupted = false
            val transientSkippedTasks = linkedSetOf<String>()
            while (round < sesameTaskRefreshRoundLimit) {
                round++
                val s = AntMemberRpcCall.queryAvailableSesameTask()
                var jo = JSONObject(s)
                if (jo.has("resData")) {
                    jo = jo.getJSONObject("resData")
                }
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error(
                        "$TAG.doAllAvailableSesameTask.queryAvailableSesameTask",
                        "芝麻信用💳[查询任务响应失败]#$s"
                    )
                    val interrupted = true
                    return@run SesameTaskRunSummary(
                        completedCount = overallCompletedTasks,
                        skippedCount = overallSkippedTasks,
                        interrupted = interrupted
                    )
                }

                val taskObj = jo.optJSONObject("data")
                if (taskObj == null) {
                    Log.sesame(TAG, "芝麻信用💳[第${round}轮]#任务数据为空，停止刷新")
                    finishedAllRounds = true
                    break
                }

                var roundTotalTasks = 0
                var roundCompletedTasks = 0
                var roundSkippedTasks = 0

                if (taskObj.has("dailyTaskListVO")) {
                    val dailyTaskListVO = taskObj.getJSONObject("dailyTaskListVO")

                    if (dailyTaskListVO.has("waitCompleteTaskVOS")) {
                        val waitCompleteTaskVOS = dailyTaskListVO.getJSONArray("waitCompleteTaskVOS")
                        roundTotalTasks += waitCompleteTaskVOS.length()
                        Log.sesame(
                            TAG,
                            "芝麻信用💳[第${round}轮待完成任务]#开始处理(" + waitCompleteTaskVOS.length() + "个)"
                        )
                        val results = joinAndFinishSesameTaskWithResult(waitCompleteTaskVOS, transientSkippedTasks)
                        roundCompletedTasks += results.completedCount
                        roundSkippedTasks += results.skippedCount
                        if (results.interrupted) {
                            interrupted = true
                            overallCompletedTasks += roundCompletedTasks
                            overallSkippedTasks += roundSkippedTasks
                            break
                        }
                    }

                    if (dailyTaskListVO.has("waitJoinTaskVOS")) {
                        val waitJoinTaskVOS = dailyTaskListVO.getJSONArray("waitJoinTaskVOS")
                        roundTotalTasks += waitJoinTaskVOS.length()
                        Log.sesame(
                            TAG,
                            "芝麻信用💳[第${round}轮待加入任务]#开始处理(" + waitJoinTaskVOS.length() + "个)"
                        )
                        val results = joinAndFinishSesameTaskWithResult(waitJoinTaskVOS, transientSkippedTasks)
                        roundCompletedTasks += results.completedCount
                        roundSkippedTasks += results.skippedCount
                        if (results.interrupted) {
                            interrupted = true
                            overallCompletedTasks += roundCompletedTasks
                            overallSkippedTasks += roundSkippedTasks
                            break
                        }
                    }
                }

                if (taskObj.has("toCompleteVOS")) {
                    val toCompleteVOS = taskObj.getJSONArray("toCompleteVOS")
                    roundTotalTasks += toCompleteVOS.length()
                    Log.sesame(
                        TAG,
                        "芝麻信用💳[第${round}轮toCompleteVOS任务]#开始处理(" + toCompleteVOS.length() + "个)"
                    )
                    val results = joinAndFinishSesameTaskWithResult(toCompleteVOS, transientSkippedTasks)
                    roundCompletedTasks += results.completedCount
                    roundSkippedTasks += results.skippedCount
                    if (results.interrupted) {
                        interrupted = true
                        overallCompletedTasks += roundCompletedTasks
                        overallSkippedTasks += roundSkippedTasks
                        break
                    }
                }

                overallCompletedTasks += roundCompletedTasks
                overallSkippedTasks += roundSkippedTasks
                Log.sesame(
                    TAG,
                    "芝麻信用💳[第${round}轮处理完成]#总任务:${roundTotalTasks}个, 完成:${roundCompletedTasks}个, 跳过:${roundSkippedTasks}个"
                )

                if (roundTotalTasks == 0) {
                    finishedAllRounds = true
                    Log.sesame(TAG, "芝麻信用💳[当前轮无可做任务，今日停止刷新]")
                    break
                }

                if (roundCompletedTasks <= 0) {
                    finishedAllRounds = true
                    Log.sesame(TAG, "芝麻信用💳[当前轮无新增完成任务，今日停止刷新]")
                    break
                }

                if (round < sesameTaskRefreshRoundLimit) {
                    delay(800)
                }
            }

            Log.sesame(
                TAG,
                "芝麻信用💳[任务总计]#轮次:$round, 完成:${overallCompletedTasks}个, 跳过:${overallSkippedTasks}个"
            )

            if (interrupted || ApplicationHookConstants.isOffline()) {
                return@run SesameTaskRunSummary(
                    completedCount = overallCompletedTasks,
                    skippedCount = overallSkippedTasks,
                    interrupted = true
                )
            }

            if (finishedAllRounds) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK)
                Log.sesame(
                    TAG,
                    if (overallCompletedTasks > 0) {
                        "芝麻信用💳[当前可执行任务已处理完成，今日跳过]"
                    } else {
                        "芝麻信用💳[无新增可执行任务，今日跳过]"
                    }
                )
            } else {
                Log.sesame(
                    TAG,
                    "芝麻信用💳[达到最大刷新轮次]#$sesameTaskRefreshRoundLimit，保留后续重试机会"
                )
            }
            return@run SesameTaskRunSummary(
                finishedAllRounds = finishedAllRounds,
                completedCount = overallCompletedTasks,
                skippedCount = overallSkippedTasks
            )
        } catch (t: Throwable) {
            Log.printStackTrace(TAG + "doAllAvailableSesameTask err", t)
            return@run SesameTaskRunSummary(
                completedCount = overallCompletedTasks,
                skippedCount = overallSkippedTasks,
                interrupted = true
            )
        }
    }

    /**
     * 芝麻粒信用福利签到  与芝麻粒炼金的签到方法都一样 alchemyQueryCheckIn 只不过scenecode不一样
     * 基于 HomeV8RpcManager.queryServiceCard 返回的 serviceCardVOList
     * 通过 itemAttrs.checkInModuleVO.currentDateCheckInTaskVO 判断今日是否可签到
     */
    internal fun doSesameZmlCheckIn() {
        var flagState = Status.TodayFlagState.RETRY_LATER
        try {
            if (ApplicationHookConstants.isOffline()) {
                return
            }
            val checkInRes = AntMemberRpcCall.zmlCheckInQueryTaskLists()
            val checkInJo = JSONObject(checkInRes)
            if (!ResChecker.checkRes(TAG, checkInJo)) {
                return
            }
            val data = checkInJo.optJSONObject("data") ?: return
            val currentDay = data.optJSONObject("currentDateCheckInTaskVO") ?: return

            val status = currentDay.optString("status")
            val checkInDate = currentDay.optString("checkInDate")

            if ("CAN_COMPLETE" != status || checkInDate.isEmpty()) {
                flagState = Status.TodayFlagState.NO_MORE_ACTION_TODAY
                return
            }
            if ("CAN_COMPLETE" == status && checkInDate.isNotEmpty()) {
                // 信誉主页签到
                val completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml")
                val completeJo = JSONObject(completeRes)
                val checkInSuccess = ResChecker.checkRes(TAG, completeJo)
                if (checkInSuccess) {
                    val prize = completeJo.optJSONObject("data")
                    val num = if (prize == null) {
                        0
                    } else {
                        val prizeObj = prize.optJSONObject("prize")
                        prize.optInt("zmlNum", prizeObj?.optInt("num", 0) ?: 0)
                    }
                    Log.sesame("芝麻信用💳[芝麻粒福利签到成功]#获得" + num + "粒")
                } else {
                    Log.error("$TAG.doSesameZmlCheckIn", "芝麻粒福利签到失败:$completeRes")
                }
                if (checkInSuccess) {
                    flagState = Status.TodayFlagState.DONE
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.doSesameZmlCheckIn", t)
        } finally {
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_ZML_CHECKIN_DONE, flagState)
        }
    }

    internal fun doSesameAlchemyNextDayAward() = CoroutineUtils.run {
        try {
            val entryRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryEntryList()
            val entryJo = JSONObject(entryRes)
            if (!ResChecker.checkRes(TAG, entryJo)) {
                Log.error("芝麻炼金⚗️[次日奖励入口查询失败]：$entryRes")
                return@run
            }

            val entryList = entryJo.optJSONObject("data")?.optJSONArray("entryList")
            var nextDayAward: JSONObject? = null
            if (entryList != null) {
                for (i in 0 until entryList.length()) {
                    val entry = entryList.optJSONObject(i) ?: continue
                    if ("ALCHEMY_STAGE_REWARD" == entry.optString("entryCode")) {
                        nextDayAward = entry.optJSONObject("nextDayAwardDTO")
                        break
                    }
                }
            }
            if (nextDayAward == null) {
                Log.sesame(TAG, "芝麻炼金⚗️[次日奖励入口缺失] 视为今日无可领奖励")
                setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
                return@run
            }

            val awardAvailable = nextDayAward.optBoolean("awardAvailable", false)
            val awardId = nextDayAward.optString("awardId")
            val pointValue = nextDayAward.optInt("pointValue", 0)
            if (!awardAvailable) {
                Log.sesame(
                    TAG,
                    "芝麻炼金⚗️[次日奖励暂无可领] 预计奖励=${pointValue}粒${if (awardId.isNotEmpty()) " awardId=$awardId" else ""}"
                )
                setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
                return@run
            }

            val awardRes = AntMemberRpcCall.Zmxy.Alchemy.claimAward(awardId)
            val jo = JSONObject(awardRes)

            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error("芝麻炼金⚗️[次日奖励领取失败]：$awardRes")
                return@run
            }

            val data = jo.optJSONObject("data")
            var gotNum = 0

            if (data != null) {
                val arr = data.optJSONArray("alchemyAwardSendResultVOS")
                if (arr != null && arr.length() > 0) {
                    val item = arr.optJSONObject(0)
                    if (item != null) {
                        gotNum = item.optInt("pointNum", item.optInt("pointValue", 0))
                    }
                }
                if (gotNum <= 0) {
                    gotNum = data.optInt("pointNum", data.optInt("pointValue", 0))
                }
            }

            if (gotNum > 0) {
                Log.sesame("芝麻炼金⚗️[次日奖励领取成功]#获得" + gotNum + "粒")
            } else {
                Log.sesame("芝麻炼金⚗️[次日奖励无奖励] 已领取或无可领奖励")
            }

            setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
        } catch (t: Throwable) {
            Log.printStackTrace("doSesameAlchemyNextDayAward", t)
        }
    }

    private fun extractSesameFeedbackArray(root: JSONObject): JSONArray? {
        return root.optJSONArray("creditFeedbackVOS")
            ?: root.optJSONObject("data")?.optJSONArray("creditFeedbackVOS")
            ?: root.optJSONObject("resData")?.optJSONArray("creditFeedbackVOS")
    }

    private fun buildUnclaimedSesameFeedbackItems(root: JSONObject): List<SesameFeedbackItem> {
        val feedbackArray = extractSesameFeedbackArray(root) ?: return emptyList()
        val result = mutableListOf<SesameFeedbackItem>()
        for (i in 0 until feedbackArray.length()) {
            val item = feedbackArray.optJSONObject(i) ?: continue
            if ("UNCLAIMED" != item.optString("status")) {
                continue
            }
            result.add(
                SesameFeedbackItem(
                    title = item.optString("title", "未知奖励"),
                    creditFeedbackId = item.optString("creditFeedbackId"),
                    potentialSize = item.optString("potentialSize", "0")
                )
            )
        }
        return result
    }

    private suspend fun queryUnclaimedSesameFeedbackItems(logPrefix: String): List<SesameFeedbackItem>? {
        val resp = AntMemberRpcCall.queryCreditFeedback()
        val jo = JSONObject(resp)
        if (!ResChecker.checkRes(TAG, jo)) {
            Log.error(
                "$TAG.queryUnclaimedSesameFeedbackItems",
                "$logPrefix[查询未领取芝麻粒响应失败]#$jo"
            )
            return null
        }
        return buildUnclaimedSesameFeedbackItems(jo)
    }

    private suspend fun collectSesameFeedbackItems(
        items: List<SesameFeedbackItem>,
        preferOneClick: Boolean,
        logPrefix: String
    ): Int {
        if (items.isEmpty()) {
            return 0
        }
        var collectedCount = 0
        var needFallbackCollect = true

        if (preferOneClick) {
            ActionDelayUtil.humanActionDelay(2000L)
            val collectAllResp = AntMemberRpcCall.collectAllCreditFeedback()
            val collectAllJo = JSONObject(collectAllResp)
            if (ResChecker.checkRes(TAG, collectAllJo)) {
                needFallbackCollect = false
                items.forEach { item ->
                    Log.sesame("$logPrefix[" + item.title + "]#" + item.potentialSize + "粒(一键收取)")
                    collectedCount++
                }
            } else {
                val msg = collectAllJo.optString("resultView").ifEmpty {
                    collectAllJo.optString("errorMessage", collectAllResp)
                }
                Log.sesame(TAG, "$logPrefix[一键收取失败，回退逐个收取]#$msg")
            }
        }

        if (!needFallbackCollect) {
            return collectedCount
        }

        for (item in items) {
            if (item.creditFeedbackId.isEmpty()) {
                continue
            }
            val collectResp = AntMemberRpcCall.collectCreditFeedback(item.creditFeedbackId)
            val collectJo = JSONObject(collectResp)
            if (!ResChecker.checkRes(TAG, collectJo)) {
                Log.error(
                    "$TAG.collectSesameFeedbackItems",
                    "$logPrefix[收取芝麻粒响应失败]#$collectJo"
                )
                continue
            }
            Log.sesame("$logPrefix[" + item.title + "]#" + item.potentialSize + "粒")
            collectedCount++
        }
        return collectedCount
    }

    /**
     * 芝麻粒收取
     * @param withOneClick 启用一键收取
     */
    internal suspend fun collectSesame(withOneClick: Boolean): Unit = CoroutineUtils.run {
        var flagState = Status.TodayFlagState.RETRY_LATER
        if (ApplicationHookConstants.isOffline()) {
            return@run
        }
        try {
            val items = queryUnclaimedSesameFeedbackItems("芝麻信用💳") ?: return@run
            if (items.isEmpty()) {
                flagState = Status.TodayFlagState.NO_MORE_ACTION_TODAY
                Log.sesame(TAG, "芝麻信用💳[当前无待收取芝麻粒]")
                return@run
            }
            collectSesameFeedbackItems(items, withOneClick, "芝麻信用💳")
            if (ApplicationHookConstants.isOffline()) {
                return@run
            }
            val remainingItems = queryUnclaimedSesameFeedbackItems("芝麻信用💳[复核]") ?: return@run
            if (remainingItems.isEmpty()) {
                flagState = Status.TodayFlagState.DONE
            } else {
                Log.sesame(TAG, "芝麻信用💳[仍有${remainingItems.size}项未收取] 保留后续重试机会")
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.collectSesame", t)
        } finally {
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_COLLECT_SESAME_DONE, flagState)
        }
    }

    /**
     * 保障金领取
     */
    private suspend fun collectInsuredGold(): Unit = CoroutineUtils.run {
        try {
            var s = AntMemberRpcCall.queryAvailableCollectInsuredGold()
            var jo = JSONObject(s)
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error("$TAG.collectInsuredGold.queryInsuredHome", "保障金🏥[响应失败]#$s")
                return@run
            }
            jo = jo.getJSONObject("data")
            val signInBall = jo.getJSONObject("signInDTO")
            val otherBallList = jo.getJSONArray("eventToWaitDTOList")
            if (1 == signInBall.getInt("sendFlowStatus") && 1 == signInBall.getInt("sendType")) {
                s = AntMemberRpcCall.collectInsuredGold(signInBall)
                jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.collectInsuredGold.collectInsuredGold", "保障金🏥[响应失败]#$s")
                    return@run
                }
                val gainGold = jo.getJSONObject("data").getString("gainSumInsuredYuan")
                Log.member("保障金🏥[领取保证金]#+" + gainGold + "元")
            }
            for (i in 0..<otherBallList.length()) {
                val anotherBall = otherBallList.getJSONObject(i)
                s = AntMemberRpcCall.collectInsuredGold(anotherBall)
                jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.collectInsuredGold.collectInsuredGold", "保障金🏥[响应失败]#$s")
                    return@run
                }
                val gainGold = jo.getJSONObject("data").getJSONObject("gainSumInsuredDTO").getString("gainSumInsuredYuan")
                Log.member("保障金🏥[领取保证金]+" + gainGold + "元")
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.collectInsuredGold", t)
        }
    }

    /**
     * 执行会员任务 类型1
     * @param task 单个任务对象
     */
    @Throws(JSONException::class)
    private suspend fun processLegacyMemberTask(task: JSONObject): Boolean = CoroutineUtils.run {
        val taskConfigInfo = task.getJSONObject("taskConfigInfo")
        val name = taskConfigInfo.getString("name")
        val id = taskConfigInfo.getLong("id")
        val awardParamPoint = extractMemberTaskAwardPoint(taskConfigInfo)
        if (isMemberTaskInBlacklist(id.toString(), name)) {
            Log.member(TAG, "会员任务🎖️[$name]#黑名单任务，停止执行")
            return@run false
        }
        val unsupportedAdTaskReason = resolveUnsupportedMemberAdTaskReason(task, taskConfigInfo)
        if (unsupportedAdTaskReason != null) {
            logSkippedMemberAdTask(name, unsupportedAdTaskReason, "会员任务🎖️")
            return@run false
        }
        val adBizId = resolveMemberAdTaskBizId(task, taskConfigInfo)
        val targetBusiness = resolveSupportedMemberTaskTargetBusiness(
            taskConfigInfo.optJSONArray("targetBusiness")
        )
        if (targetBusiness.isEmpty() && adBizId.isEmpty()) {
            return@run false
        }
        if (adBizId.isNotEmpty()) {
            return@run finishMemberAdTask(id.toString(), name, awardParamPoint, adBizId)
        }
        val targetBusinessArray = targetBusiness.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val bizType = targetBusinessArray[0]
        val bizSubType = targetBusinessArray[1]
        val bizParam = targetBusinessArray[2]
        val str = AntMemberRpcCall.executeTask(bizParam, bizSubType, bizType, id)
        val jo = JSONObject(str)
        if (!ResChecker.checkRes(TAG + "执行会员任务失败:", jo)) {
            Log.error(TAG, "执行任务失败:" + jo.optString("resultDesc"))
            return@run false
        }
        if (checkMemberTaskFinished(id)) {
            Log.member("会员任务🎖️[$name]#获得积分$awardParamPoint")
            return@run true
        }
        false
    }

    private suspend fun checkMemberTaskFinished(taskId: Long): Boolean {
        return try {
            val str = AntMemberRpcCall.queryLegacyAllStatusTaskList()
            val jsonObject = JSONObject(str)
            if (!ResChecker.checkRes(TAG + "查询会员任务状态失败:", jsonObject)) {
                Log.error(
                    "$TAG.checkMemberTaskFinished", "会员任务响应失败: " + jsonObject.getString("resultDesc")
                )
            }
            if (!jsonObject.has("availableTaskList")) {
                return true
            }
            val taskList = jsonObject.getJSONArray("availableTaskList")
            for (i in 0..<taskList.length()) {
                val taskConfigInfo = taskList.getJSONObject(i).getJSONObject("taskConfigInfo")
                val id = taskConfigInfo.getLong("id")
                if (taskId == id) {
                    return false
                }
            }
            true
        } catch (_: JSONException) {
            false
        }
    }

    private fun resolveSupportedMemberTaskTargetBusiness(targetBusinessArray: JSONArray?): String {
        if (targetBusinessArray == null || targetBusinessArray.length() <= 0) {
            return ""
        }
        for (i in 0 until targetBusinessArray.length()) {
            val targetBusiness = targetBusinessArray.optString(i)
            if (isSupportedMemberTaskTargetBusiness(targetBusiness)) {
                return targetBusiness
            }
        }
        return ""
    }

    private fun isSupportedMemberTaskTargetBusiness(targetBusiness: String): Boolean {
        if (targetBusiness.isBlank()) {
            return false
        }
        val targetParts = targetBusiness.split("#")
        if (targetParts.size < 3) {
            return false
        }
        val bizType = targetParts[0]
        val bizSubType = targetParts[1]
        val bizParam = targetParts[2]
        return bizType.equals("BROWSE", true) && bizSubType.isNotBlank() && bizParam.isNotBlank()
    }

    private fun shouldKeepMemberTaskConfigId(taskConfigId: String): Boolean {
        if (taskConfigId.length >= 12 && taskConfigId.startsWith("6")) {
            return true
        }
        return taskConfigId.length == 8 && taskConfigId.startsWith("3200")
    }

    private fun isMemberTaskInBlacklist(taskConfigId: String, taskTitle: String): Boolean {
        return TaskBlacklist.isTaskInBlacklist(memberTaskBlacklistModule, taskTitle)
            || TaskBlacklist.isTaskInBlacklist(memberTaskBlacklistModule, taskConfigId)
    }

    private fun resolveMemberAdTaskBizId(
        taskObject: JSONObject?,
        taskConfigInfo: JSONObject? = null
    ): String {
        if (isUnsupportedMemberAdTaskType(taskObject, taskConfigInfo)) {
            return ""
        }

        val urlCandidates = listOfNotNull(
            taskObject?.optString("actionUrl"),
            taskObject?.optString("targetUrl"),
            taskObject?.optString("jumpUrl"),
            taskObject?.optString("pageUrl"),
            taskObject?.optString("clickThroughUrl"),
            taskObject?.optString("halfClickThroughUrl"),
            taskConfigInfo?.optString("actionUrl"),
            taskConfigInfo?.optString("targetUrl"),
            taskConfigInfo?.optString("jumpUrl"),
            taskConfigInfo?.optString("pageUrl"),
            taskConfigInfo?.optString("clickThroughUrl"),
            taskConfigInfo?.optString("halfClickThroughUrl"),
            taskConfigInfo?.optString("schemaJson")
        ).filter { it.isNotBlank() }
        val hasMemberAdUrlMarker = urlCandidates.any { looksLikeMemberAdTaskUrl(it) }

        if (hasExplicitMemberAdTaskMarker(taskObject, taskConfigInfo, hasMemberAdUrlMarker)) {
            val directBizId = sequenceOf(
                taskObject?.optString("adBizId"),
                taskObject?.optJSONObject("logExtMap")?.optString("bizId"),
                taskObject?.optJSONObject("extInfo")?.optString("adBizId"),
                taskObject?.optJSONObject("extInfo")?.optString("bizId"),
                taskConfigInfo?.optString("adBizId"),
                taskConfigInfo?.optJSONObject("logExtMap")?.optString("bizId"),
                taskConfigInfo?.optJSONObject("extInfo")?.optString("adBizId"),
                taskConfigInfo?.optJSONObject("extInfo")?.optString("bizId")
            ).filterNotNull().firstOrNull { it.isNotBlank() }
            if (!directBizId.isNullOrBlank()) {
                return directBizId
            }
        }

        for (urlCandidate in urlCandidates) {
            if (!hasMemberAdUrlMarker || !looksLikeMemberAdTaskUrl(urlCandidate)) {
                continue
            }
            extractMemberAdBizIdFromText(urlCandidate)?.let { return it }
            val nestedUrl = UrlUtil.getFullNestedUrl(urlCandidate, "url")
            if (!nestedUrl.isNullOrBlank()) {
                extractMemberAdBizIdFromText(nestedUrl)?.let { return it }
            }
        }
        return ""
    }

    private fun resolveUnsupportedMemberAdTaskReason(
        taskObject: JSONObject?,
        taskConfigInfo: JSONObject?
    ): String? {
        val taskTypeCandidates = sequenceOf(
            taskObject?.optString("taskType"),
            taskConfigInfo?.optString("taskType")
        ).filterNotNull()
        if (taskTypeCandidates.any { it.equals("MULTIPLE_TIMER_TASK", true) }) {
            return "MULTIPLE_TIMER_TASK"
        }
        if (taskObject?.has("videoTaskInfo") == true || taskConfigInfo?.has("videoTaskInfo") == true) {
            return "VIDEO_TASK"
        }
        if (taskObject?.optBoolean("adVideoTask") == true || taskConfigInfo?.optBoolean("adVideoTask") == true) {
            return "AD_VIDEO_TASK"
        }
        if (hasMemberAdVideoSchema(taskObject, taskConfigInfo)) {
            return "VIDEO_TASK"
        }
        return null
    }

    private fun isUnsupportedMemberAdTaskType(
        taskObject: JSONObject?,
        taskConfigInfo: JSONObject?
    ): Boolean {
        return resolveUnsupportedMemberAdTaskReason(taskObject, taskConfigInfo) != null
    }

    private fun logSkippedMemberAdTask(
        taskTitle: String,
        skipReason: String,
        logPrefix: String = "会员任务"
    ) {
        val detail = when (skipReason) {
            "MULTIPLE_TIMER_TASK" -> "多阶段倒计时任务"
            "VIDEO_TASK", "AD_VIDEO_TASK" -> "视频广告任务"
            else -> skipReason
        }
        Log.member(TAG, "$logPrefix[$taskTitle]#识别到$detail，阶段6按计划跳过")
    }

    private fun hasExplicitMemberAdTaskMarker(
        taskObject: JSONObject?,
        taskConfigInfo: JSONObject?,
        hasMemberAdUrlMarker: Boolean
    ): Boolean {
        if (hasMemberAdUrlMarker) {
            return true
        }
        val configIds = linkedSetOf<String>().apply {
            taskObject?.let(::resolveCurrentMemberTaskConfigId)?.takeIf { it.isNotBlank() }?.let(::add)
            taskObject?.optString("configId")?.takeIf { it.isNotBlank() }?.let(::add)
            taskConfigInfo?.optString("configId")?.takeIf { it.isNotBlank() }?.let(::add)
            taskConfigInfo?.optLong("id", 0L)?.takeIf { it > 0 }?.toString()?.let(::add)
        }
        if (configIds.any { it.length == 8 && it.startsWith("3200") }) {
            return true
        }
        return taskObject?.optBoolean("adTaskFlag") == true ||
            taskConfigInfo?.optBoolean("adTaskFlag") == true ||
            taskObject?.optBoolean("adTask") == true ||
            taskConfigInfo?.optBoolean("adTask") == true
    }

    private fun hasMemberAdVideoSchema(
        taskObject: JSONObject?,
        taskConfigInfo: JSONObject?
    ): Boolean {
        return sequenceOf(
            taskObject?.optString("schemaJson"),
            taskConfigInfo?.optString("schemaJson")
        ).filterNotNull()
            .any { schemaJson ->
                if (schemaJson.isBlank()) {
                    false
                } else {
                    runCatching {
                        JSONObject(schemaJson).optString("videoUrl").isNotBlank()
                    }.getOrDefault(false)
                }
            }
    }

    private fun extractMemberAdBizIdFromText(text: String): String? {
        if (text.isBlank()) {
            return null
        }
        UrlUtil.getParamValue(text, "bizId")?.takeIf { it.isNotBlank() }?.let { return it }
        UrlUtil.getParamValue(text, "opParam")
            ?.takeIf { it.isNotBlank() }
            ?.let { opParam ->
                runCatching { JSONObject(opParam).optString("bizId") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
        val jsonMatcher = Pattern.compile("\"bizId\"\\s*:\\s*\"([^\"]+)\"").matcher(text)
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1)
        }
        val queryMatcher = Pattern.compile("bizId=([^&#\"]+)").matcher(text)
        if (queryMatcher.find()) {
            return queryMatcher.group(1)
        }
        return null
    }

    private fun looksLikeMemberAdTaskUrl(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        val normalized = text.lowercase()
        return normalized.contains("com.alipay.adtask.biz.mobilegw.service.task.finish") ||
            normalized.contains("spacecode#ant_member_xlight_task") ||
            normalized.contains("spacecode=ant_member_xlight_task") ||
            (normalized.contains("renderconfigkey=") && normalized.contains("ant_member_xlight_task"))
    }

    private suspend fun finishMemberAdTask(
        taskConfigId: String,
        taskTitle: String,
        fallbackAwardPoint: String,
        bizId: String
    ): Boolean = CoroutineUtils.run {
        val response = AntMemberRpcCall.taskFinish(bizId)
        val responseObject = JSONObject(response)
        val success = responseObject.optBoolean("success") ||
            responseObject.optString("errCode") == "0" ||
            responseObject.optString("resultCode").equals("SUCCESS", true)
        if (!success) {
            val message = sequenceOf(
                responseObject.optString("errMsg"),
                responseObject.optString("resultDesc"),
                responseObject.optString("errorMessage"),
                response
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            Log.member(TAG, "会员任务[$taskTitle]#广告任务上报失败:$message")
            return@run false
        }
        val verifyState = checkMemberAdTaskFinished(taskConfigId, bizId)
        val rewardPoint = responseObject.optJSONObject("extendInfo")
            ?.optJSONObject("rewardInfo")
            ?.optString("rewardAmount")
            .orEmpty()
            .ifEmpty { fallbackAwardPoint }
        if (verifyState == CurrentMemberTaskVerifyState.CONFIRMED) {
            if (rewardPoint.isNotBlank()) {
                Log.member("会员任务[$taskTitle]#获得积分$rewardPoint")
            } else {
                Log.member("会员任务[$taskTitle]#广告任务完成")
            }
        } else {
            Log.member(TAG, "会员任务[$taskTitle]#广告任务上报成功，状态待后续页面确认")
        }
        return@run true
    }

    private suspend fun checkMemberAdTaskFinished(
        taskConfigId: String,
        bizId: String
    ): CurrentMemberTaskVerifyState {
        if (taskConfigId.isBlank() || bizId.isBlank()) {
            return CurrentMemberTaskVerifyState.UNCONFIRMED
        }
        return try {
            val detailResponse = AntMemberRpcCall.querySingleAdTaskProcessDetail(taskConfigId, bizId)
            val detailObject = JSONObject(detailResponse)
            if (!ResChecker.checkRes(TAG, detailObject)) {
                return CurrentMemberTaskVerifyState.UNCONFIRMED
            }
            val taskProcessObject = detailObject.optJSONObject("resultData")?.optJSONObject("taskProcessVO")
            if (isMemberTaskProcessFinished(taskProcessObject)) {
                CurrentMemberTaskVerifyState.CONFIRMED
            } else {
                CurrentMemberTaskVerifyState.UNCONFIRMED
            }
        } catch (_: JSONException) {
            CurrentMemberTaskVerifyState.UNCONFIRMED
        }
    }

    /**
     * 黄金票任务入口（首页签到/收取/任务扫描 + 提取）
     * @param doSignIn 是否执行签到
     * @param doConsume 是否执行提取
     */
    internal fun doGoldTicketTask(doSignIn: Boolean, doConsume: Boolean) {
        val needSignIn = doSignIn && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE)
        val needHomeCheck = doSignIn && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_HOME_DONE)
        val needWelfareCheck = doSignIn && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_WELFARE_DONE)
        val needConsume = doConsume && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_CONSUME_DONE)

        if (!needSignIn && !needHomeCheck && !needWelfareCheck && !needConsume) {
            Log.member("黄金票🎫[今日已处理] 跳过执行")
            return
        }

        try {
            Log.member("开始执行黄金票...")

            var homeUpsertData: JSONObject? = null
            if (needSignIn || needHomeCheck) {
                homeUpsertData = queryGoldTicketHomeUpsert()
            }

            if (needSignIn) {
                if (homeUpsertData == null) {
                    Log.error("黄金票🎫[首页查询失败] 无法判断签到状态")
                } else if (doGoldTicketSignIn(homeUpsertData)) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE)
                    homeUpsertData = queryGoldTicketHomeUpsert() ?: homeUpsertData
                }
            }

            if (needHomeCheck) {
                if (homeUpsertData == null) {
                    Log.error("黄金票🎫[首页查询失败] 跳过收取与任务扫描")
                } else {
                    doGoldTicketCollect(homeUpsertData)
                    handleGoldTicketTasks(homeUpsertData)
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_HOME_DONE)
                }
            }

            if (needWelfareCheck) {
                val welfareHandleResult = handleGoldTicketWelfareTasks()
                if (!welfareHandleResult.querySuccess) {
                    Log.error("黄金票🎫[福利中心任务查询失败]")
                } else if (welfareHandleResult.canMarkDone) {
                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_WELFARE_DONE)
                }
            }

            if (needConsume) {
                doGoldTicketConsume()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 黄金票首页数据
     */
    private fun queryGoldTicketHomeUpsert(taskId: String = ""): JSONObject? {
        return try {
            val homeRes = AntMemberRpcCall.queryGoldTicketHome(taskId) ?: return null
            val homeJson = JSONObject(homeRes)
            if (!ResChecker.checkRes(TAG, homeJson)) {
                return null
            }
            homeJson.optJSONObject("result")?.optJSONObject("upsertData")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            null
        }
    }

    private fun isGoldTicketCanSign(homeUpsertData: JSONObject?): Boolean {
        return homeUpsertData?.optJSONObject("assetInfo")?.optBoolean("canSign", false) == true
    }

    private fun doGoldTicketIndexCollect(source: String): Int {
        val needleResponse = AntMemberRpcCall.goldTicketIndexCollect()
        if (!needleResponse.isNullOrBlank()) {
            return logGoldTicketCollectResponse(needleResponse, source)
        }
        return logGoldTicketCollectResponse(
            AntMemberRpcCall.goldBillCollect(),
            "$source-旧版兼容"
        )
    }

    /**
     * 黄金票签到逻辑
     *
     * 真实首页日志来自 `com.alipay.wealthgoldtwa.needle.v2.index`，
     * 抓包显示收取接口已切到 `com.alipay.wealthgoldtwa.needle.index.collect`，
     * 因此先用首页 `canSign` 判定，再尝试新版首页收取；
     * 若仍未落库，再回退到已有的 welfareCenter 触发链路。
     */
    private fun doGoldTicketSignIn(homeUpsertData: JSONObject): Boolean {
        return try {
            if (!isGoldTicketCanSign(homeUpsertData)) {
                Log.member("黄金票🎫[今日已签到]")
                return true
            }

            Log.member("黄金票🎫[准备签到]")

            var signSuccess = false
            val collectCount = doGoldTicketIndexCollect("签到尝试")
            var refreshedHome = queryGoldTicketHomeUpsert()
            if (refreshedHome != null && !isGoldTicketCanSign(refreshedHome)) {
                Log.member(
                    if (collectCount > 0) "黄金票🎫[签到成功]#通过首页收取完成签到"
                    else "黄金票🎫[签到成功]"
                )
                signSuccess = true
            }

            if (!signSuccess) {
                val signRes = AntMemberRpcCall.welfareCenterTrigger("SIGN")
                if (signRes.isNotBlank()) {
                    val signJson = JSONObject(signRes)
                    if (ResChecker.checkRes(TAG, signJson)) {
                        val signResult = signJson.optJSONObject("result")
                        val amount = signResult?.optJSONObject("prize")?.optString("amount").orEmpty()
                        refreshedHome = queryGoldTicketHomeUpsert()
                        signSuccess = refreshedHome != null && !isGoldTicketCanSign(refreshedHome)
                        if (signSuccess || amount.isNotBlank()) {
                            Log.member(
                                if (amount.isNotBlank()) "黄金票🎫[签到成功]#获得: $amount"
                                else "黄金票🎫[签到成功]"
                            )
                            signSuccess = true
                        }
                    }
                }
            }

            if (!signSuccess) {
                Log.error("黄金票🎫[签到失败] 未找到可用签到返回")
            }
            signSuccess
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            false
        }
    }

    /**
     * 黄金票首页场景收取
     */
    private fun doGoldTicketCollect(homeUpsertData: JSONObject) {
        try {
            val toBeCollectInfo = homeUpsertData.optJSONObject("assetInfo")?.optJSONObject("toBeCollectInfo")
            val totalProfitValue = toBeCollectInfo?.optInt("totalProfitValue", 0) ?: 0
            if (totalProfitValue <= 0) {
                return
            }

            val collectCount = doGoldTicketIndexCollect("场景收取")
            if (collectCount == 0) {
                Log.member("黄金票🎫[场景收取] 暂无可领取奖励")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    private fun logGoldTicketCollectResponse(response: String?, source: String): Int {
        if (response.isNullOrBlank()) {
            return 0
        }
        return try {
            val collectJson = JSONObject(response)
            if (!ResChecker.checkRes(TAG, collectJson)) {
                val message = collectJson.optString("resultDesc", collectJson.optString("memo"))
                if (message.isNotBlank()) {
                    Log.member("黄金票🎫[$source] $message")
                }
                return 0
            }

            val result = collectJson.optJSONObject("result") ?: return 0
            val collectedList = result.optJSONArray("collectedList") ?: return 0
            var count = 0
            for (i in 0 until collectedList.length()) {
                val item = collectedList.optString(i)
                if (item.isBlank()) {
                    continue
                }
                count++
                Log.member("黄金票🎫[$source]#$item")
            }

            if (count > 0) {
                val totalAmount = result.optJSONObject("collectedCamp")?.optString("amount").orEmpty()
                if (totalAmount.isNotBlank()) {
                    Log.member("黄金票🎫[$source]#本次共得${totalAmount}份")
                }
            }
            count
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            0
        }
    }

    private fun shouldAutoReceiveGoldTicketTask(task: JSONObject): Boolean {
        val taskStatus = task.optString("taskProcessStatus")
        val taskType = task.optString("taskType").uppercase(Locale.ROOT)
        return when (taskStatus) {
            "NONE_SIGNUP" -> {
                isGoldTicketSesameBrowseTask(task) && (
                    taskType == "BROWSE" || taskType == "COUNT_DOWN"
                    )
            }
            "SIGNUP_EXPIRED" -> {
                isGoldTicketSesameBrowseTask(task) && (
                    taskType == "BROWSE" || taskType == "COUNT_DOWN"
                    )
            }

            else -> false
        }
    }

    private data class GoldTicketWelfareHandleResult(
        val querySuccess: Boolean,
        val canMarkDone: Boolean
    )

    private fun isGoldTicketSesameBrowseTask(task: JSONObject): Boolean {
        val taskId = task.optString("taskId")
        if (taskId == "AP16338809") {
            return true
        }
        val title = task.optString("title")
        if (title.contains("芝麻攒粒") || title.contains("芝麻粒")) {
            return true
        }
        return task.optString("link").contains("zmlshouye", ignoreCase = true)
    }

    private fun isGoldTicketEggSignTask(task: JSONObject): Boolean {
        val taskId = task.optString("taskId")
        if (taskId == "AP11249033") {
            return true
        }
        return task.optString("title").contains("蛋定生财")
    }

    private fun isGoldTicketKnownWelfareAutoTask(task: JSONObject): Boolean {
        return when (task.optString("taskId")) {
            "AP11249033", // 逛蛋定生财去签到
            "AP10247402", // 逛逛稳健理财领红包
            "AP13250426"  // 逛定期市场领红包
            -> true

            else -> false
        }
    }

    private fun queryGoldTicketWelfareResult(): JSONObject? {
        return try {
            val welfareResponse = AntMemberRpcCall.queryWelfareHome() ?: return null
            val welfareJson = JSONObject(welfareResponse)
            if (!ResChecker.checkRes(TAG, welfareJson)) {
                return null
            }
            welfareJson.optJSONObject("result")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            null
        }
    }

    private fun queryGoldTicketWelfareTodoTasks(): JSONArray? {
        val welfareResult = queryGoldTicketWelfareResult() ?: return null
        return welfareResult.optJSONObject("goldbillTasks")
            ?.optJSONArray("todo")
            ?: JSONArray()
    }

    private fun countGoldTicketPendingWelfareAutoTasks(todoTasks: JSONArray?): Int {
        if (todoTasks == null || todoTasks.length() == 0) {
            return 0
        }
        var pendingCount = 0
        for (i in 0 until todoTasks.length()) {
            val task = todoTasks.optJSONObject(i) ?: continue
            if (isGoldTicketKnownWelfareAutoTask(task)) {
                pendingCount++
            }
        }
        return pendingCount
    }

    /**
     * 黄金票任务扫描
     *
     * 首页里已确认的芝麻粒浏览任务会以
     * `SIGNUP_EXPIRED -> goldbill.v4.task.trigger -> needle.taskQueryPush`
     * 闭环完成，其余首页任务仍保守记录为手动任务。
     */
    private fun handleGoldTicketTasks(homeUpsertData: JSONObject) {
        try {
            val todoTasks = homeUpsertData.optJSONObject("task")
                ?.optJSONObject("tasks")
                ?.optJSONArray("todo") ?: return

            if (todoTasks.length() == 0) {
                return
            }

            var autoReceivedCount = 0
            var manualCount = 0
            for (i in 0 until todoTasks.length()) {
                val task = todoTasks.optJSONObject(i) ?: continue
                val status = task.optString("taskProcessStatus")
                when (status) {
                    "TO_RECEIVE" -> {
                        if (tryReceiveGoldTicketTask(task)) {
                            autoReceivedCount++
                        }
                    }

                    "NONE_SIGNUP", "SIGNUP_EXPIRED" -> {
                        if (shouldAutoReceiveGoldTicketTask(task)) {
                            if (tryReceiveGoldTicketTask(task)) {
                                autoReceivedCount++
                            } else {
                                manualCount++
                            }
                            continue
                        }
                        val link = task.optString("link")
                        val canAccess = task.optBoolean("canAccess", false)
                        if (link.isNotBlank() || canAccess) {
                            manualCount++
                        }
                    }

                    "SIGNUP_COMPLETE" -> {
                        if (isGoldTicketEggSignTask(task)) {
                            continue
                        }
                        val link = task.optString("link")
                        val canAccess = task.optBoolean("canAccess", false)
                        if (link.isNotBlank() || canAccess) {
                            manualCount++
                        }
                    }
                }
            }

            if (autoReceivedCount > 0) {
                Log.member("黄金票🎫[任务自动领取] ${autoReceivedCount}项")
            }
            if (manualCount > 0) {
                Log.member("黄金票🎫[任务待手动处理] ${manualCount}项")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 福利中心已确认的浏览类任务会走：
     * `goldbill.v4.task.trigger -> needle.taskQueryPush -> welfareCenter.index`
     * 这里仅放开抓包已确认的 taskId，避免把未知福利任务误判成可自动完成。
     */
    private fun handleGoldTicketWelfareTasks(): GoldTicketWelfareHandleResult {
        try {
            val todoTasks = queryGoldTicketWelfareTodoTasks()
                ?: return GoldTicketWelfareHandleResult(querySuccess = false, canMarkDone = false)

            val trackedAutoTaskCount = countGoldTicketPendingWelfareAutoTasks(todoTasks)
            if (trackedAutoTaskCount == 0) {
                return GoldTicketWelfareHandleResult(querySuccess = true, canMarkDone = true)
            }

            var autoReceivedCount = 0
            var manualCount = 0
            for (i in 0 until todoTasks.length()) {
                val task = todoTasks.optJSONObject(i) ?: continue
                if (!isGoldTicketKnownWelfareAutoTask(task)) {
                    continue
                }

                when (task.optString("taskProcessStatus")) {
                    "TO_RECEIVE", "NONE_SIGNUP", "SIGNUP_EXPIRED", "SIGNUP_COMPLETE" -> {
                        if (tryReceiveGoldTicketTask(task, "福利中心")) {
                            autoReceivedCount++
                        } else {
                            manualCount++
                        }
                    }

                    "RECEIVE_SUCCESS" -> Unit
                    else -> manualCount++
                }
            }

            if (manualCount > 0) {
                Log.member("黄金票🎫[福利中心任务待手动处理] ${manualCount}项")
            }

            val refreshedTodoTasks = queryGoldTicketWelfareTodoTasks()
            if (refreshedTodoTasks == null) {
                Log.member("黄金票🎫[福利中心任务复查失败] 暂不写入今日完成")
                return GoldTicketWelfareHandleResult(
                    querySuccess = true,
                    canMarkDone = false
                )
            }

            val pendingRetryCount = countGoldTicketPendingWelfareAutoTasks(refreshedTodoTasks)
            val confirmedAutoReceivedCount = (trackedAutoTaskCount - pendingRetryCount).coerceAtLeast(0)
            if (confirmedAutoReceivedCount > 0) {
                Log.member("黄金票🎫[福利中心任务自动领取] ${confirmedAutoReceivedCount}项")
            }
            if (pendingRetryCount > 0) {
                Log.member("黄金票🎫[福利中心任务保留下次重试] ${pendingRetryCount}项")
            }
            return GoldTicketWelfareHandleResult(
                querySuccess = true,
                canMarkDone = pendingRetryCount == 0
            )
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            return GoldTicketWelfareHandleResult(querySuccess = false, canMarkDone = false)
        }
    }

    private fun tryReceiveGoldTicketTask(task: JSONObject, source: String = "首页"): Boolean {
        val taskId = task.optString("taskId")
        if (taskId.isBlank()) {
            return false
        }
        val title = task.optString("title", taskId)
        val status = task.optString("taskProcessStatus")
        val isBlacklisted =
            TaskBlacklist.isTaskInBlacklist(goldTicketTaskBlacklistModule, title) ||
                TaskBlacklist.isTaskInBlacklist(goldTicketTaskBlacklistModule, taskId)
        if (isBlacklisted && status != "TO_RECEIVE") {
            Log.member("黄金票🎫[黑名单跳过]#$source#$title#$taskId#$status")
            return false
        }
        if (isBlacklisted) {
            Log.member("黄金票🎫[黑名单放行领奖]#$source#$title#$taskId#$status")
        }
        return try {
            if (status != "SIGNUP_COMPLETE") {
                val triggerRes = AntMemberRpcCall.goldBillTaskTrigger(taskId) ?: return false
                val triggerJson = JSONObject(triggerRes)
                if (!ResChecker.checkRes(TAG, triggerJson)) {
                    val triggerCode = triggerJson.optString("resultCode", triggerJson.optString("errorCode", ""))
                    val triggerDesc = triggerJson.optString("resultDesc", triggerJson.optString("memo"))
                    if (triggerCode.isNotBlank()) {
                        TaskBlacklist.autoAddToBlacklist(goldTicketTaskBlacklistModule, taskId, title, triggerCode)
                    }
                    if (triggerDesc.isNotBlank()) {
                        Log.error("黄金票🎫[${source}任务领取失败] $title#$taskId#$status#$triggerDesc")
                    }
                    return false
                }
            }

            val pushRes = AntMemberRpcCall.taskQueryPush(taskId)
            if (pushRes.isNullOrBlank()) {
                Log.member("黄金票🎫[${source}任务推送无返回] $title#$taskId#$status")
                return false
            }
            val pushJson = JSONObject(pushRes)
            if (!ResChecker.checkRes(TAG, pushJson)) {
                val pushCode = pushJson.optString("resultCode", pushJson.optString("errorCode", ""))
                val pushDesc = pushJson.optString("resultDesc", pushJson.optString("memo"))
                if (pushCode.isNotBlank()) {
                    TaskBlacklist.autoAddToBlacklist(goldTicketTaskBlacklistModule, taskId, title, pushCode)
                }
                if (pushDesc.isNotBlank()) {
                    Log.member("黄金票🎫[${source}任务推送提示] $title#$taskId#$status#$pushDesc")
                }
                return false
            }
            val pushDone = pushJson.optJSONObject("result")
                ?.optJSONObject("pushResult")
                ?.optBoolean("done", true)
            if (pushDone == false) {
                Log.member("黄金票🎫[${source}任务推送未完成] $title#$taskId#$status")
                return false
            }

            val amount = task.optString("amount")
            if (amount.isNotBlank()) {
                Log.member("黄金票🎫[${source}任务领取成功]#$title#+${amount}份")
            } else {
                Log.member("黄金票🎫[${source}任务领取成功]#$title")
            }
            true
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            false
        }
    }

    /**
     * 黄金票提取逻辑（`queryConsumeHome` + `submitConsume`）
     */
    private fun doGoldTicketConsume() {
        var consumeDone = false
        try {
            Log.member("黄金票🎫[准备检查余额及提取]")

            // 1. 调用新接口 queryConsumeHome 获取最新的资产信息
            val queryRes = AntMemberRpcCall.queryConsumeHome() ?: return
            val queryJson = JSONObject(queryRes)
            if (!ResChecker.checkRes(TAG, queryJson)) return

            val result = queryJson.optJSONObject("result") ?: return

            // 2. 获取余额
            val assetInfo = result.optJSONObject("assetInfo") ?: return

            val availableAmount = assetInfo.optInt("availableAmount", 0)
            val minExchangeAmount = assetInfo.optInt("minExchangeAmount", 100)
            val exchangeAmountUnit = assetInfo.optInt("exchangeAmountUnit", minExchangeAmount).coerceAtLeast(1)

            // 3. 按接口返回的门槛与步长计算提取数量
            val extractAmount = (availableAmount / exchangeAmountUnit) * exchangeAmountUnit

            if (extractAmount < minExchangeAmount) {
                Log.member("黄金票🎫[余额不足] 当前: $availableAmount，最低需$minExchangeAmount")
                consumeDone = true
                return
            }

            // 4. 获取必要参数 productId 和 bonusAmount
            var productId = ""
            val product = result.optJSONObject("product")
            if (product != null) {
                productId = product.optString("productId")
            } else if (result.has("productList") && result.optJSONArray("productList") != null && (result.optJSONArray("productList")?.length()
                    ?: 0) > 0
            ) {
                productId = result.optJSONArray("productList")?.optJSONObject(0)?.optString("productId") ?: ""
            } else if (assetInfo.optJSONArray("mainExchangePrizeList")?.length() ?: 0 > 0) {
                productId = assetInfo.optJSONArray("mainExchangePrizeList")?.optJSONObject(0)?.optString("bizNo") ?: ""
            } else if (assetInfo.optJSONArray("footerExchangePrizeList")?.length() ?: 0 > 0) {
                productId = assetInfo.optJSONArray("footerExchangePrizeList")?.optJSONObject(0)?.optString("bizNo") ?: ""
            } else {
                val backupPrize = assetInfo.optJSONObject("backupPrize")
                if (backupPrize != null && "GOLD".equals(backupPrize.optString("prizeType"), true)) {
                    productId = backupPrize.optString("bizNo")
                }
            }

            if (productId.isEmpty()) {
                Log.error("黄金票🎫[提取异常] 未找到有效的基金ID")
                return
            }

            var bonusAmount = 0
            val bonusInfo = result.optJSONObject("bonusInfo")
            if (bonusInfo != null) {
                bonusAmount = bonusInfo.optInt("bonusAmount", 0)
            }

            // 5. 提交提取
            val exchangeMoney = result.optJSONObject("calcInfo")?.optString("exchangeMoney")
                ?.takeIf { it.isNotBlank() } ?: String.format(Locale.US, "%.2f", extractAmount / 1000.0)
            Log.member("黄金票🎫[开始提取] 计划: $extractAmount 份 => $exchangeMoney 元 (持有: $availableAmount)")
            val submitRes = AntMemberRpcCall.submitConsume(extractAmount, productId, bonusAmount)

            if (submitRes.isNullOrBlank()) {
                Log.error("黄金票🎫[提取失败] 接口无返回")
                return
            }

            val submitJson = JSONObject(submitRes)
            if (!ResChecker.checkRes(TAG, submitJson)) {
                val submitDesc = submitJson.optString("resultDesc", submitJson.optString("memo"))
                if (submitDesc.isNotBlank()) {
                    Log.error("黄金票🎫[提取失败] $submitDesc")
                }
                return
            }

            val submitResult = submitJson.optJSONObject("result")
            val writeOffNo = submitResult?.optString("writeOffNo").orEmpty()
            val successTitle = submitResult?.optString("successTitle").orEmpty()
            if (writeOffNo.isNotBlank() || successTitle.contains("成功")) {
                Log.member("黄金票🎫[提取成功]#$exchangeMoney 元#$extractAmount 份")
                consumeDone = true
            } else {
                Log.error("黄金票🎫[提取失败] 未返回核销码")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        } finally {
            if (consumeDone) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_CONSUME_DONE)
            }
        }
    }

    private suspend fun enableGameCenter() {
        try {
            // 1. 查询签到状态并尝试签到
            try {
                val resp = AntMemberRpcCall.querySignInBall()
                val root = JSONObject(resp)
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    Log.error("$TAG.enableGameCenter.signIn", "游戏中心🎮[签到查询失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")

                    // 情况1：data 为 null 或 空对象 → 默认已经签到过
                    if (data == null || data.length() == 0) {
                        Log.error("$TAG.enableGameCenter.signIn", "游戏中心🎮[今日已签到](data为空)")
                        return
                    }
                    val signModule = data.optJSONObject("signInBallModule")
                    val signed = signModule != null && signModule.optBoolean("signInStatus", false)
                    if (signed) {
                        Log.member("$TAG.enableGameCenter.signIn", "游戏中心🎮[今日已签到]")
                    } else {
                        val signResp = AntMemberRpcCall.continueSignIn()
                        val signJo = JSONObject(signResp)
                        if (!ResChecker.checkRes(TAG, signJo)) {
                            val msg = signJo.optString(
                                "errorMsg", signJo.optString("resultView", signResp)
                            )
                            Log.error("$TAG.enableGameCenter.signIn", "游戏中心🎮[签到失败]#$msg")
                        } else {
                            val signData = signJo.optJSONObject("data")
                            var title = ""
                            var desc = ""
                            var type = ""
                            if (signData != null) {
                                val toast = signData.optJSONObject("autoSignInToastModule")
                                if (toast != null) {
                                    title = toast.optString("title", "")
                                    desc = toast.optString("desc", "")
                                    type = toast.optString("type", "")
                                }
                            }
                            val toastSuccess = "SUCCESS".equals(type, ignoreCase = true) && !title.contains("失败") && !desc.contains("失败")
                            if (toastSuccess) {
                                val sb = StringBuilder()
                                sb.append("游戏中心🎮[每日签到成功]")
                                if (!title.isEmpty()) {
                                    sb.append("#").append(title)
                                }
                                if (!desc.isEmpty()) {
                                    sb.append("#").append(desc)
                                }
                                Log.member(sb.toString())
                            } else {
                                val sb = StringBuilder()
                                if (!title.isEmpty()) {
                                    sb.append(title)
                                }
                                if (!desc.isEmpty()) {
                                    if (sb.isNotEmpty()) sb.append(" ")
                                    sb.append(desc)
                                }
                                Log.error(
                                    "$TAG.enableGameCenter.signIn", "游戏中心🎮[签到失败]#" + (if (sb.isNotEmpty()) sb.toString() else signResp)
                                )
                            }
                        }
                    }
                }
            } catch (th: Throwable) {
                Log.printStackTrace(TAG, "enableGameCenter.signIn err:", th)
            }

            // 2. 查询任务列表,完成平台任务
            try {
                val resp = AntMemberRpcCall.queryGameCenterTaskList()
                val root = JSONObject(resp)
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    Log.error("$TAG.enableGameCenter.tasks", "游戏中心🎮[任务列表查询失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")
                    if (data != null) {
                        val platformTaskModule = data.optJSONObject("platformTaskModule")
                        if (platformTaskModule != null) {
                            val platformTaskList = platformTaskModule.optJSONArray("platformTaskList")
                            if (platformTaskList != null && platformTaskList.length() > 0) {
                                var total = 0
                                var finished = 0
                                var failed = 0
                                var lastFailedTaskId = ""
                                var lastFailedCount = 0

                                for (i in 0..<platformTaskList.length()) {
                                    val task = platformTaskList.optJSONObject(i) ?: continue

                                    val taskId = task.optString("taskId")
                                    val status = task.optString("taskStatus")

                                    if (taskId.isEmpty()) continue
                                    if ("NOT_DONE" != status && "SIGNUP_COMPLETE" != status) {
                                        continue
                                    }

                                    // 如果是上次失败的任务,计数加1
                                    if (taskId == lastFailedTaskId) {
                                        lastFailedCount++
                                        if (lastFailedCount >= 2) {
                                            Log.member(
                                                "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[" + task.optString("title") + "]连续失败2次,跳过"
                                            )
                                            continue
                                        }
                                    } else {
                                        // 新任务,重置计数
                                        lastFailedTaskId = taskId
                                        lastFailedCount = 0
                                    }

                                    total++
                                    val title = task.optString("title")
                                    val subTitle = task.optString("subTitle")
                                    val needSignUp = task.optBoolean("needSignUp", false)
                                    val pointAmount = task.optInt("pointAmount", 0)

                                    try {
                                        // needSignUp 为 true 且是首次状态 NOT_DONE:先报名
                                        if (needSignUp && "NOT_DONE" == status) {
                                            val signUpResp = AntMemberRpcCall.doTaskSignup(taskId)
                                            val signUpJo = JSONObject(signUpResp)
                                            if (!ResChecker.checkRes(TAG, signUpJo)) {
                                                val msg = signUpJo.optString(
                                                    "errorMsg", signUpJo.optString("resultView", signUpResp)
                                                )
                                                Log.error(
                                                    "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]报名失败#$msg"
                                                )
                                                failed++
                                                continue
                                            }
                                        }

                                        // 完成任务
                                        val doResp = AntMemberRpcCall.doTaskSend(taskId)
                                        val doJo = JSONObject(doResp)

                                        if (ResChecker.checkRes(TAG, doJo)) {
                                            // 检查返回的任务状态
                                            val doData = doJo.optJSONObject("data")
                                            val resultStatus = if (doData != null) doData.optString(
                                                "taskStatus", ""
                                            ) else ""

                                            if ("SIGNUP_COMPLETE" == resultStatus || "NOT_DONE" == resultStatus) {
                                                // 状态未变更,记为失败
                                                Log.error(
                                                    "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]状态未变更,可能无法完成"
                                                )
                                                failed++
                                            } else {
                                                // 真正完成,重置失败计数
                                                Log.member(
                                                    "游戏中心🎮任务[" + (subTitle.ifEmpty { title }) + "]#完成,奖励" + pointAmount + "玩乐豆" + (if (needSignUp) "(签到任务)" else "")
                                                )
                                                finished++
                                                lastFailedTaskId = ""
                                                lastFailedCount = 0
                                            }
                                        } else {
                                            val msg = doJo.optString(
                                                "errorMsg", doJo.optString("resultView", doResp)
                                            )
                                            Log.error(
                                                "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]完成失败#$msg"
                                            )
                                            failed++
                                        }
                                    } catch (e: Throwable) {
                                        Log.printStackTrace("$TAG.enableGameCenter.tasks.doTask", e)
                                        failed++
                                    }
                                }

                                if (total > 0) {
                                    Log.member(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务处理完成]#待做:$total 完成:$finished 失败:$failed"
                                    )
                                } else {
                                    Log.member(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[无待处理的平台任务]"
                                    )
                                }
                            } else {
                                Log.member("$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务列表为空]")
                            }
                        }
                    }
                }
            } catch (th: Throwable) {
                Log.printStackTrace(TAG, "enableGameCenter.tasks err:", th)
            }

            // 3. 查询待收乐豆并使用一键收取接口
            try {
                val resp = AntMemberRpcCall.queryPointBallList()
                val root = JSONObject(resp)
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    Log.error("$TAG.enableGameCenter.point", "游戏中心🎮[查询待收乐豆失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")
                    val pointBallList = data?.optJSONArray("pointBallList")
                    if (pointBallList == null || pointBallList.length() == 0) {
                        Log.member("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
                    } else {
                        val batchResp = AntMemberRpcCall.batchReceivePointBall()
                        val batchJo = JSONObject(batchResp)
                        if (ResChecker.checkRes(TAG, batchJo)) {
                            val batchData = batchJo.optJSONObject("data")
                            val receiveAmount = batchData?.optInt("receiveAmount", 0) ?: 0
                            val totalAmount = batchData?.optInt("totalAmount", receiveAmount) ?: receiveAmount
                            if (receiveAmount > 0) {
                                Log.member("游戏中心🎮[一键领取乐豆成功]#本次领取" + receiveAmount + " | 当前累计" + totalAmount + "玩乐豆")
                            } else {
                                Log.member("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
                            }
                        } else {
                            val msg = batchJo.optString(
                                "errorMsg", batchJo.optString("resultView", batchResp)
                            )
                            Log.error(
                                "$TAG.enableGameCenter.point", "游戏中心🎮[一键领取乐豆失败]#$msg"
                            )
                        }
                    }
                }
            } catch (th: Throwable) {
                Log.printStackTrace(TAG, "enableGameCenter.point err:", th)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    internal fun beanSignIn() {
        try {
            try {
                val signInProcessStr = AntMemberRpcCall.querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN")

                var jo = JSONObject(signInProcessStr)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.member(jo.toString())
                    return
                }

                if (jo.getJSONObject("result").getBoolean("canPush")) {
                    val signInTriggerStr = AntMemberRpcCall.signInTrigger("AP16242232", "INS_BLUE_BEAN_SIGN")

                    jo = JSONObject(signInTriggerStr)
                    if (ResChecker.checkRes(TAG, jo)) {
                        val prizeName = jo.getJSONObject("result").getJSONArray("prizeSendOrderDTOList").getJSONObject(0).getString("prizeName")
                        Log.member(TAG, "安心豆🫘[$prizeName]")
                    } else {
                        Log.member(jo.toString())
                    }
                }
            } catch (e: NullPointerException) {
                Log.printStackTrace(TAG, "安心豆🫘[RPC桥接失败]#可能是RpcBridge未初始化", e)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "beanSignIn err:", t)
        }
    }

    internal fun beanExchangeBubbleBoost() {
        try {
            // 检查RPC调用是否可用
            try {
                val accountInfo = AntMemberRpcCall.queryUserAccountInfo("INS_BLUE_BEAN")

                var jo = JSONObject(accountInfo)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.member(jo.toString())
                    return
                }

                val userCurrentPoint = jo.getJSONObject("result").getInt("userCurrentPoint")

                // 检查beanExchangeDetail调用
                val exchangeDetailStr = AntMemberRpcCall.beanExchangeDetail("IT20230214000700069722")

                jo = JSONObject(exchangeDetailStr)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.member(jo.toString())
                    return
                }

                jo = jo.getJSONObject("result").getJSONObject("rspContext").getJSONObject("params").getJSONObject("exchangeDetail")
                val itemId = jo.getString("itemId")
                val itemName = jo.getString("itemName")
                jo = jo.getJSONObject("itemExchangeConsultDTO")
                val realConsumePointAmount = jo.getInt("realConsumePointAmount")

                if (!jo.getBoolean("canExchange") || realConsumePointAmount > userCurrentPoint) {
                    return
                }

                val exchangeResult = AntMemberRpcCall.beanExchange(itemId, realConsumePointAmount)

                jo = JSONObject(exchangeResult)
                if (ResChecker.checkRes(TAG, jo)) {
                    Log.member(TAG, "安心豆🫘[兑换:$itemName]")
                } else {
                    Log.member(jo.toString())
                }
            } catch (e: NullPointerException) {
                Log.printStackTrace(TAG, "安心豆🫘[RPC桥接失败]#可能是RpcBridge未初始化", e)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "beanExchangeBubbleBoost err:", t)
        }
    }

    /**
     * 芝麻炼金
     */
    internal suspend fun doSesameAlchemy(): Unit = CoroutineUtils.run {
        try {
            Log.sesame(TAG, "开始执行芝麻炼金⚗️")

            // ================= Step 1: 自动炼金 (消耗芝麻粒升级 / 消耗免费炼金次数) =================
            runSesameAlchemyCycles()

            // ================= Step 2: 自动签到 & 时段奖励 =================
            val checkInRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryCheckIn("alchemy")
            val checkInJo = JSONObject(checkInRes)
            if (ResChecker.checkRes(TAG, checkInJo)) {
                val data = checkInJo.optJSONObject("data")
                if (data != null) {
                    val currentDay = data.optJSONObject("currentDateCheckInTaskVO")
                    if (currentDay != null) {
                        val status = currentDay.optString("status")
                        val checkInDate = currentDay.optString("checkInDate")
                        if ("CAN_COMPLETE" == status && !checkInDate.isEmpty()) {
                            // 炼金签到
                            val completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "alchemy")
                            try {
                                val completeJo = JSONObject(completeRes)
                                if (ResChecker.checkRes(TAG, completeJo)) {
                                    val prize = completeJo.optJSONObject("data")
                                    val num = if (prize == null) {
                                        0
                                    } else {
                                        val prizeObj = prize.optJSONObject("prize")
                                        prize.optInt("zmlNum", prizeObj?.optInt("num", 0) ?: 0)
                                    }
                                    Log.sesame("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒")
                                } else {
                                    Log.error("$TAG.doSesameAlchemy", "炼金签到失败:$completeRes")
                                }
                            } catch (e: Throwable) {
                                Log.printStackTrace(
                                    "$TAG.doSesameAlchemy.alchemyCheckInComplete", e
                                )
                            }
                        } // status 为 COMPLETED 时不再重复签到
                    }
                }
            }

            // 1. 查询时段任务
            val queryRespStr = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryTimeLimitedTask()
            Log.sesame(TAG, "芝麻炼金⚗️[检查时段奖励]")

            val queryResp = JSONObject(queryRespStr)
            if (!ResChecker.checkRes(TAG + "查询时段任务失败:", queryResp) || !ResChecker.checkRes(
                    TAG, queryResp
                ) || queryResp.optJSONObject("data") == null
            ) {
                Log.error(
                    TAG, "芝麻炼金⚗️[检查时段奖励错误] alchemyQueryTimeLimitedTask raw=$queryResp"
                )
                return
            }

            val timeLimitedTaskVO = queryResp.getJSONObject("data").optJSONObject("timeLimitedTaskVO")
            if (timeLimitedTaskVO == null) {
                Log.sesame(TAG, "芝麻炼金⚗️[当前没有时段奖励任务]")
                return
            }

            // 2. 获取任务信息
            val taskName = timeLimitedTaskVO.optString("longTitle", "未知任务")
            val templateId = timeLimitedTaskVO.getString("templateId") // 动态获取
            val state = timeLimitedTaskVO.optInt("state", 0) // 1: 可领取, 2: 未到时间
            val tomorrow = timeLimitedTaskVO.optBoolean("tomorrow", false)
            val rewardAmount = timeLimitedTaskVO.optInt("rewardAmount", 0)

            Log.sesame(
                TAG, "芝麻炼金⚗️[任务检查] 任务=$taskName 状态=$state 奖励=$rewardAmount 明天=$tomorrow"
            )

            // 3. 如果是明天任务，跳过
            if (tomorrow) {
                Log.sesame(TAG, "芝麻炼金⚗️[任务跳过] 任务=$taskName 是明天的奖励")
                return
            }

            // 4. 如果状态是可领取，则领取奖励
            if (state == 1) { // 可领取
                Log.sesame(TAG, "芝麻炼金⚗️[开始领取任务奖励] 任务=$taskName")

                val collectRespStr = AntMemberRpcCall.Zmxy.Alchemy.alchemyCompleteTimeLimitedTask(templateId)
                val collectResp = JSONObject(collectRespStr)

                if (!ResChecker.checkRes(
                        TAG, collectResp
                    ) || collectResp.optJSONObject("data") == null
                ) {
                    Log.error(TAG, "领取任务奖励失败 raw=$collectResp")
                } else {
                    val data = collectResp.getJSONObject("data")
                    val zmlNum = data.optInt("zmlNum", 0)
                    val toast = data.optString("toast", "")
                    Log.sesame(TAG, "芝麻炼金⚗️[领取成功] 获得芝麻粒=$zmlNum 提示=$toast")
                }
            } else { // 其他状态
                Log.sesame(TAG, "芝麻炼金⚗️[当前不可领取] 任务=$taskName")
            }


            // ================= Step 3: 自动做任务 =================
            Log.sesame(TAG, "芝麻炼金⚗️[开始扫描任务列表]")
            val listRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryListV3()
            val listJo = JSONObject(listRes)

            if (ResChecker.checkRes(TAG, listJo)) {
                val data = listJo.optJSONObject("data")
                if (data != null) {
                    // 用于记录所有已处理的黑名单任务，避免在不同任务组间重复记录
                    val allProcessedBlacklistTasks = mutableSetOf<String>()

                    val toComplete = data.optJSONArray("toCompleteVOS")
                    if (toComplete != null) {
                        processAlchemyTasks(toComplete, allProcessedBlacklistTasks)
                    }
                    val dailyTaskVO = data.optJSONObject("dailyTaskListVO")
                    if (dailyTaskVO != null) {
                        processAlchemyTasks(
                            dailyTaskVO.optJSONArray("waitJoinTaskVOS"), allProcessedBlacklistTasks
                        )
                        processAlchemyTasks(
                            dailyTaskVO.optJSONArray("waitCompleteTaskVOS"), allProcessedBlacklistTasks
                        )
                    }
                }
            }

            // ================= Step 4: [新增] 任务完成后一键收取芝麻粒 =================
            Log.sesame(TAG, "芝麻炼金⚗️[任务处理完毕，准备收取芝麻粒]")
            ActionDelayUtil.humanActionDelay(2000L) // 稍作等待，确保任务奖励到账

            val feedbackItems = queryUnclaimedSesameFeedbackItems("芝麻炼金⚗️")
            if (feedbackItems == null) {
                Log.sesame(TAG, "芝麻炼金⚗️[查询待收取芝麻粒失败]")
            } else if (feedbackItems.isEmpty()) {
                Log.sesame(TAG, "芝麻炼金⚗️[当前无待收取芝麻粒]")
            } else {
                Log.sesame(TAG, "芝麻炼金⚗️[发现" + feedbackItems.size + "个待收取项，执行一键收取]")
                val collectedCount = collectSesameFeedbackItems(feedbackItems, true, "芝麻炼金⚗️")
                if (collectedCount > 0) {
                    Log.sesame("芝麻炼金⚗️[收取完成]#本次处理" + collectedCount + "项")
                }
            }

            // 新增浏览任务可能奖励炼金次数（LJCS），任务后仅补跑免费炼金，避免额外消耗新到账芝麻粒。
            runSesameAlchemyCycles(allowPaidAlchemy = false)
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.doSesameAlchemy", t)
        }
    }

    private suspend fun runSesameAlchemyCycles(allowPaidAlchemy: Boolean = true) {
        val homeRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryHome()
        val homeJo = JSONObject(homeRes)
        if (!ResChecker.checkRes(TAG, homeJo)) {
            Log.error(TAG, "芝麻炼金首页查询失败")
            return
        }
        val data = homeJo.optJSONObject("data") ?: return
        var zmlBalance = data.optInt("zmlBalance", 0)
        val cost = data.optInt("alchemyCostZml", 5).coerceAtLeast(1)
        var capReached = data.optBoolean("capReached", false)
        var currentLevel = data.optInt("currentLevel", 0)
        var freeAlchemyNum = data.optInt("freeAlchemyNum", 0)

        while (freeAlchemyNum > 0 || (allowPaidAlchemy && zmlBalance >= cost && !capReached)) {
            delay(1500)
            val alchemyRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyExecute()
            val alchemyJo = JSONObject(alchemyRes)

            if (isSesameAlchemyCapReached(alchemyJo) && freeAlchemyNum <= 0) {
                Log.sesame(TAG, "芝麻炼金⚗️[已达盖帽值，停止自动炼金]")
                break
            }
            if (!ResChecker.checkRes(TAG, alchemyJo)) {
                Log.error(TAG, "芝麻炼金失败: " + alchemyJo.optString("resultView", alchemyRes))
                break
            }

            val alData = alchemyJo.optJSONObject("data") ?: break
            val levelUp = alData.optBoolean("levelUp", false)
            val levelFull = alData.optBoolean("levelFull", false)
            val goldNum = alData.optInt("goldNum", 0)
            val usedFreeAlchemy =
                alData.optBoolean("free", false) || (freeAlchemyNum > 0 && (!allowPaidAlchemy || capReached))

            if (levelUp) {
                currentLevel++
            }
            if (levelFull) {
                capReached = true
            }

            val consumeText = if (usedFreeAlchemy) {
                if (freeAlchemyNum > 0) {
                    freeAlchemyNum--
                }
                "消耗免费次数1次"
            } else {
                zmlBalance -= cost
                "消耗${cost}粒"
            }

            Log.sesame(
                "芝麻炼金⚗️[炼金成功]#$consumeText | 获得" + goldNum + "金" +
                    " | 当前等级Lv." + currentLevel +
                    (if (levelUp) "（升级🎉）" else "") +
                    (if (levelFull) "（满级🏆）" else "")
            )
        }
    }

    /**
     * 处理芝麻炼金任务列表
     * @param taskList 任务列表
     * @param processedBlacklistTasks 已处理的黑名单任务集合（用于避免重复日志）
     */
    @Throws(JSONException::class)
    private suspend fun processAlchemyTasks(
        taskList: JSONArray?, processedBlacklistTasks: MutableSet<String>
    ) {
        if (taskList == null || taskList.length() == 0) return

        for (i in 0..<taskList.length()) {
            val task = taskList.getJSONObject(i)
            val title = task.optString("title")
            val templateId = task.optString("templateId")
            val finishFlag = task.optBoolean("finishFlag", false)
            val bizType = task.optString("bizType", "")

            if (finishFlag) continue

            // 使用TaskBlacklist进行黑名单检查
            if (isTaskInBlacklist(sesameAlchemyTaskBlacklistModule, title)) {
                // 只有在所有任务组中未处理过时才记录日志
                if (!processedBlacklistTasks.contains(title)) {
                    Log.sesame(TAG, "跳过黑名单任务: $title")
                    processedBlacklistTasks.add(title)
                }
                continue
            }

            if (shouldSkipShareAssistSesameTask(task)) {
                Log.sesame(TAG, "芝麻炼金任务: 跳过助力型任务 $title")
                continue
            }

            // 特殊处理：广告浏览任务（逛15秒商品橱窗 / 浏览15秒视频广告 等）
            // 这类任务没有有效 templateId，需要用 logExtMap.bizId 走 com.alipay.adtask.biz.mobilegw.service.task.finish
            if ("AD_TASK" == bizType) {
                try {
                    handleSesameAdTask(task, title, "芝麻炼金⚗️", sesameAlchemyTaskBlacklistModule)
                } catch (e: Throwable) {
                    Log.printStackTrace("$TAG.processAlchemyTasks.adTask", e)
                }
                // 广告任务不再走 templateId / recordId 这套逻辑
                continue
            }

            // 普通任务：仍然使用模板+recordId 的 Promise 流程
            if (templateId.contains("invite") || templateId.contains("upload") || templateId.contains("auth") || templateId.contains("banli")) {
                continue
            }
            val actionUrl = task.optString("actionUrl", "")
            if (actionUrl.startsWith("alipays://") && !actionUrl.contains("chInfo")) {
                // 需要外部 App，无法仅靠 hook 完成
                continue
            }

            Log.sesame(TAG, "芝麻炼金任务: $title 准备执行")

            var recordId = task.optString("recordId", "")

            if (recordId.isEmpty()) {
                // templateId 为空或无效时，直接跳过，避免 "参数[templateId]不是有效的入参"
                if (templateId == null || templateId.trim { it <= ' ' }.isEmpty()) {
                    Log.sesame(TAG, "芝麻炼金任务: 模板为空，跳过 $title")
                    continue
                }
                val joinRes = AntMemberRpcCall.joinSesameTask(templateId)
                val joinJo = JSONObject(joinRes)
                if (ResChecker.checkRes(TAG, joinJo)) {
                    val joinData = joinJo.optJSONObject("data")
                    if (joinData != null) {
                        recordId = joinData.optString("recordId")
                    }
                    Log.sesame(TAG, "任务领取成功: $title")
                    ActionDelayUtil.humanActionDelay(1000L)
                } else {
                    Log.error(
                        TAG, "任务领取失败: " + title + " - " + joinJo.optString("resultView", joinRes)
                    )
                    continue
                }
            }

            if (!reportSesameTaskFeedback(
                    task,
                    title,
                    "芝麻炼金⚗️",
                    sesameAlchemyTaskBlacklistModule,
                    version = "alchemy"
                )
            ) {
                continue
            }

            if (!recordId.isEmpty()) {
                val finishRes = AntMemberRpcCall.finishSesameTask(recordId)
                val finishJo = JSONObject(finishRes)
                if (ResChecker.checkRes(TAG, finishJo)) {
                    Log.sesame("芝麻炼金⚗️[任务完成: " + title + "]#获得" + formatSesameAlchemyReward(task))
                } else {
                    val errorCode = finishJo.optString("resultCode", "")
                    val resultView = finishJo.optString("resultView", finishRes)
                    //  val errorMsg = finishJo.optString("resultView", finishRes)
                    //  Log.error(TAG, "任务提交失败: $title - $errorMsg")
                    // 自动添加到黑名单
                    if (!errorCode.isEmpty()) {
                        autoBlacklistSesameTaskIfNeeded(sesameAlchemyTaskBlacklistModule, title, errorCode, resultView)
                    }
                }
            }
            delay(2000)
        }
    }

    internal suspend fun doZhimaTree(): Unit = CoroutineUtils.run {
        try {
            // 1. 执行首页的所有任务 (包括浏览任务和复访任务)
            doHomeTasks()

            // 2. 执行常规列表任务 (赚净化值列表)
            doRentGreenTasks()

            // 3. 消耗净化值进行净化
            doPurification()
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    /**
     * 处理首页返回的任务 (含浏览任务和状态列表任务)
     */
    private suspend fun doHomeTasks(): Unit = CoroutineUtils.run {
        try {
            val res = AntMemberRpcCall.zhimaTreeHomePage() ?: return@run

            val json = JSONObject(res)
            if (ResChecker.checkRes(TAG, json)) {
                val result = json.optJSONObject("extInfo") ?: return@run
                val queryResult = result.optJSONObject("zhimaTreeHomePageQueryResult") ?: return@run

                // 1. 处理 browseTaskList (如：芝麻树首页每日_浏览任务)
                val browseList = queryResult.optJSONArray("browseTaskList")
                if (browseList != null) {
                    for (i in 0..<browseList.length()) {
                        processSingleTask(browseList.getJSONObject(i))
                    }
                }

                // 2. 处理 taskStatusList (如：芝麻树复访任务70净化值)
                val statusList = queryResult.optJSONArray("taskStatusList")
                if (statusList != null) {
                    for (i in 0..<statusList.length()) {
                        processSingleTask(statusList.getJSONObject(i))
                    }
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理赚净化值列表任务
     */
    private suspend fun doRentGreenTasks(): Unit = CoroutineUtils.run {
        try {
            val res = AntMemberRpcCall.queryRentGreenTaskList() ?: return@run

            val json = JSONObject(res)
            if (ResChecker.checkRes(TAG, json)) {
                val extInfo = json.optJSONObject("extInfo") ?: return@run

                val taskDetailListObj = extInfo.optJSONObject("taskDetailList") ?: return@run

                val tasks = taskDetailListObj.optJSONArray("taskDetailList") ?: return@run

                for (i in 0..<tasks.length()) {
                    processSingleTask(tasks.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理单个任务对象的逻辑
     */
    private suspend fun processSingleTask(task: JSONObject) {
        try {
            val taskRef = buildZhimaTreeTaskRef(task) ?: return
            if ("NOT_DONE" == taskRef.status || "SIGNUP_COMPLETE" == taskRef.status) {
                if (taskRef.taskId == null) {
                    Log.sesame(
                        TAG,
                        "芝麻树🌳[跳过无有效任务ID] ${taskRef.title} | candidates=${taskRef.describeCandidates()}"
                    )
                    return
                }
                Log.sesame(
                    "芝麻树🌳[开始任务] " + taskRef.title +
                        (if (taskRef.prizeName.isEmpty()) "" else " (${taskRef.prizeName})")
                )
                performTask(taskRef)
            } else if ("TO_RECEIVE" == taskRef.status) {
                receiveZhimaTreeTask(taskRef, "领取奖励")
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    private fun buildZhimaTreeTaskRef(task: JSONObject): ZhimaTreeTaskRef? {
        val sendCampTriggerType = task.optString("sendCampTriggerType")
        if ("EVENT_TRIGGER" == sendCampTriggerType) {
            return null
        }
        val taskBaseInfo = task.optJSONObject("taskBaseInfo") ?: return null
        val taskIdCandidates = collectZhimaTreeTaskIdCandidates(task, taskBaseInfo)
        val taskId = taskIdCandidates.mapNotNull { normalizeZhimaTreeTaskId(it) }.firstOrNull()
        var title = taskBaseInfo.optString("appletName")
        if (title.isEmpty()) {
            title = taskBaseInfo.optString("title", taskId ?: "未知任务")
        }
        if (title.contains("邀请") || title.contains("下单") || title.contains("开通")) {
            return null
        }
        return ZhimaTreeTaskRef(
            title = title,
            prizeName = getPrizeName(task),
            status = task.optString("taskProcessStatus"),
            taskId = taskId,
            taskIdCandidates = taskIdCandidates,
            needManuallyReceiveAward = task.optBoolean("needManuallyReceiveAward", true)
        )
    }

    private fun normalizeZhimaTreeTaskId(rawTaskId: String?): String? {
        val normalized = rawTaskId?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("null", ignoreCase = true)) {
            return null
        }
        if (normalized == "{}" || normalized == "[]") {
            return null
        }
        if ((normalized.startsWith("{") && normalized.endsWith("}")) ||
            (normalized.startsWith("[") && normalized.endsWith("]"))
        ) {
            return null
        }
        return normalized
    }

    private fun collectZhimaTreeTaskIdCandidates(task: JSONObject, taskBaseInfo: JSONObject): List<String> {
        return sequenceOf(
            taskBaseInfo.opt("appletId"),
            taskBaseInfo.opt("taskId"),
            taskBaseInfo.opt("appId"),
            task.opt("taskId"),
            task.opt("appletId"),
            task.opt("appId")
        ).filterNotNull()
            .map { candidate ->
                when (candidate) {
                    JSONObject.NULL -> ""
                    is String -> candidate
                    else -> candidate.toString()
                }
            }
            .toList()
    }

    private suspend fun queryZhimaTreeTaskRefs(): ZhimaTreeTaskRefreshResult = CoroutineUtils.run {
        val refreshedTasks = mutableListOf<ZhimaTreeTaskRef>()
        var queriedSourceCount = 0
        if (appendZhimaTreeTaskRefsFromHomePage(refreshedTasks)) {
            queriedSourceCount++
        }
        if (appendZhimaTreeTaskRefsFromRentGreenList(refreshedTasks)) {
            queriedSourceCount++
        }
        ZhimaTreeTaskRefreshResult(refreshedTasks, queriedSourceCount)
    }

    private fun appendZhimaTreeTaskRefs(target: MutableList<ZhimaTreeTaskRef>, tasks: JSONArray?) {
        if (tasks == null) {
            return
        }
        for (i in 0..<tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            buildZhimaTreeTaskRef(task)?.let(target::add)
        }
    }

    private fun appendZhimaTreeTaskRefsFromHomePage(target: MutableList<ZhimaTreeTaskRef>): Boolean {
        val res = AntMemberRpcCall.zhimaTreeHomePage() ?: return false
        val json = JSONObject(res)
        if (!ResChecker.checkRes(TAG, json)) {
            return false
        }
        val queryResult = json.optJSONObject("extInfo")
            ?.optJSONObject("zhimaTreeHomePageQueryResult") ?: return true
        appendZhimaTreeTaskRefs(target, queryResult.optJSONArray("browseTaskList"))
        appendZhimaTreeTaskRefs(target, queryResult.optJSONArray("taskStatusList"))
        return true
    }

    private fun appendZhimaTreeTaskRefsFromRentGreenList(target: MutableList<ZhimaTreeTaskRef>): Boolean {
        val res = AntMemberRpcCall.queryRentGreenTaskList() ?: return false
        val json = JSONObject(res)
        if (!ResChecker.checkRes(TAG, json)) {
            return false
        }
        val tasks = json.optJSONObject("extInfo")
            ?.optJSONObject("taskDetailList")
            ?.optJSONArray("taskDetailList") ?: return true
        appendZhimaTreeTaskRefs(target, tasks)
        return true
    }

    private fun findMatchingZhimaTreeTask(
        originalTask: ZhimaTreeTaskRef,
        refreshedTasks: List<ZhimaTreeTaskRef>
    ): ZhimaTreeTaskRef? {
        return refreshedTasks.firstOrNull { refreshedTask ->
            isSameZhimaTreeTask(originalTask, refreshedTask, requireSameTaskId = true)
        } ?: refreshedTasks.firstOrNull { refreshedTask ->
            isSameZhimaTreeTask(originalTask, refreshedTask, requireSameTaskId = false)
        }
    }

    private fun isSameZhimaTreeTask(
        originalTask: ZhimaTreeTaskRef,
        refreshedTask: ZhimaTreeTaskRef,
        requireSameTaskId: Boolean
    ): Boolean {
        if (refreshedTask.title != originalTask.title) {
            return false
        }
        val prizeMatched = originalTask.prizeName.isEmpty() ||
            refreshedTask.prizeName.isEmpty() ||
            refreshedTask.prizeName == originalTask.prizeName
        if (!prizeMatched) {
            return false
        }
        if (!requireSameTaskId || originalTask.taskId == null) {
            return true
        }
        return refreshedTask.taskIdCandidates
            .mapNotNull(::normalizeZhimaTreeTaskId)
            .any { it == originalTask.taskId }
    }

    private fun buildZhimaTreeSuccessLog(action: String, taskRef: ZhimaTreeTaskRef): String {
        return "芝麻树🌳[$action] " + taskRef.title + " #" +
            taskRef.prizeName.ifEmpty { "奖励已领取" }
    }

    private fun classifyZhimaTreeActionFailure(response: JSONObject?): String {
        val code = response?.optString("errorCode")
            .orEmpty()
            .ifBlank { response?.optString("resultCode").orEmpty() }
            .ifBlank { response?.optString("code").orEmpty() }
        return when (code) {
            "20020012" -> "parameter_invalid"
            else -> "rpc_failed"
        }
    }

    private fun logZhimaTreeActionFailure(
        action: String,
        stageCode: String,
        taskRef: ZhimaTreeTaskRef,
        actionResult: ZhimaTreeActionResult
    ) {
        val response = actionResult.response
        val code = response?.optString("errorCode")
            .orEmpty()
            .ifBlank { response?.optString("resultCode").orEmpty() }
            .ifBlank { response?.optString("code").orEmpty() }
            .ifBlank { "<empty>" }
        val message = response?.optString("errorMsg")
            .orEmpty()
            .ifBlank { response?.optString("resultDesc").orEmpty() }
            .ifBlank { response?.optString("desc").orEmpty() }
            .ifBlank { "<empty>" }
        Log.error(
            TAG,
            "芝麻树🌳[${action}失败][${classifyZhimaTreeActionFailure(response)}] " +
                "${taskRef.title} | stage=$stageCode | taskId=${taskRef.taskId ?: "<null>"} " +
                "| candidates=${taskRef.describeCandidates()} | code=$code | msg=$message " +
                "| raw=${actionResult.rawResponse ?: "<empty>"}"
        )
    }

    private suspend fun receiveZhimaTreeTask(taskRef: ZhimaTreeTaskRef, successAction: String): Boolean {
        if (taskRef.taskId == null) {
            Log.sesame(
                TAG,
                "芝麻树🌳[跳过无有效任务ID] ${taskRef.title} | candidates=${taskRef.describeCandidates()}"
            )
            return false
        }
        val receiveResult = doTaskActionResult(taskRef.taskId, "receive")
        if (!receiveResult.success) {
            logZhimaTreeActionFailure("领取奖励", "receive", taskRef, receiveResult)
            return false
        }
        Log.sesame(buildZhimaTreeSuccessLog(successAction, taskRef))
        return true
    }

    private suspend fun tryReceiveZhimaTreeTaskFallback(
        taskRef: ZhimaTreeTaskRef,
        reason: String
    ): Boolean {
        if (!taskRef.needManuallyReceiveAward || taskRef.taskId == null) {
            return false
        }
        Log.sesame(
            TAG,
            "芝麻树🌳[$reason，尝试直接领取] ${taskRef.title} | candidates=${taskRef.describeCandidates()}"
        )
        return receiveZhimaTreeTask(taskRef, "完成任务")
    }

    /**
     * 执行任务动作：去完成 -> 刷新确认 -> 领取（必要时直接回退领取）
     */
    private suspend fun performTask(taskRef: ZhimaTreeTaskRef): Boolean {
        return try {
            val safeTaskId = taskRef.taskId
            if (safeTaskId == null) {
                Log.sesame(TAG, "芝麻树🌳[跳过执行，无有效任务ID] ${taskRef.title}")
                return false
            }
            val sendResult = doTaskActionResult(safeTaskId, "send")
            if (!sendResult.success) {
                logZhimaTreeActionFailure("开始任务", "send", taskRef, sendResult)
                return false
            }
            val refreshResult = queryZhimaTreeTaskRefs()
            val refreshedTask = findMatchingZhimaTreeTask(taskRef, refreshResult.tasks)
            if (refreshedTask == null) {
                if (!refreshResult.hasConfirmedSnapshot) {
                    Log.sesame(
                        TAG,
                        "芝麻树🌳[回查失败] ${taskRef.title} | candidates=${taskRef.describeCandidates()}"
                    )
                    return false
                }
                if (!taskRef.needManuallyReceiveAward) {
                    Log.sesame(buildZhimaTreeSuccessLog("完成任务", taskRef))
                    return true
                }
                if (tryReceiveZhimaTreeTaskFallback(taskRef, "回查未找到任务")) {
                    return true
                }
                return false
            }
            val receiveTarget = if (refreshedTask.taskId != null) refreshedTask else taskRef
            when (refreshedTask.status) {
                "TO_RECEIVE" -> return receiveZhimaTreeTask(receiveTarget, "完成任务")
                "DONE", "COMPLETE", "FINISHED", "RECEIVED" -> {
                    Log.sesame(buildZhimaTreeSuccessLog("完成任务", refreshedTask))
                    return true
                }
            }
            if (tryReceiveZhimaTreeTaskFallback(receiveTarget, "回查状态未终态")) {
                return true
            }
            Log.sesame(
                TAG,
                "芝麻树🌳[回查未完成] ${taskRef.title} | status=${refreshedTask.status.ifEmpty { "<empty>" }} " +
                    "| candidates=${refreshedTask.describeCandidates()}"
            )
            false
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            false
        }
    }

    /**
     * 获取任务奖励名称
     */
    private fun getPrizeName(task: JSONObject): String {
        var prizeName = ""
        try {
            var prizes = task.optJSONArray("validPrizeDetailDTO")
            if (prizes == null || prizes.length() == 0) {
                prizes = task.optJSONArray("prizeDetailDTOList")
            }

            if (prizes != null && prizes.length() > 0) {
                val prizeBase = prizes.getJSONObject(0).optJSONObject("prizeBaseInfoDTO")
                if (prizeBase != null) {
                    val rawName = prizeBase.optString("prizeName", "")

                    if (rawName.contains("能量")) {
                        val p = Pattern.compile("(森林)?能量(\\d+g?)")
                        val m = p.matcher(rawName)
                        if (m.find()) {
                            prizeName = m.group(0) ?: ""
                        } else {
                            prizeName = rawName
                        }
                    } else if (rawName.contains("净化值")) {
                        val p = Pattern.compile("(\\d+净化值|净化值\\d+)")
                        val m = p.matcher(rawName)
                        if (m.find()) {
                            prizeName = m.group(1) ?: ""
                        } else {
                            prizeName = rawName
                        }
                    } else {
                        prizeName = rawName
                    }
                }
            }

            // 如果没找到 PrizeDTO，尝试从 taskExtProps 解析
            if (prizeName.isEmpty()) {
                val taskExtProps = task.optJSONObject("taskExtProps")
                if (taskExtProps != null && taskExtProps.has("TASK_MORPHO_DETAIL")) {
                    val detail = JSONObject(taskExtProps.getString("TASK_MORPHO_DETAIL"))
                    val `val` = detail.optString("finishOneTaskGetPurificationValue", "")
                    if (!`val`.isEmpty() && "0" != `val`) {
                        prizeName = `val` + "净化值"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return prizeName
    }

    private fun doTaskAction(taskId: String?, stageCode: String?): Boolean {
        return doTaskActionResult(taskId, stageCode).success
    }

    private fun doTaskActionResult(taskId: String?, stageCode: String?): ZhimaTreeActionResult {
        try {
            val safeTaskId = normalizeZhimaTreeTaskId(taskId)
                ?: return ZhimaTreeActionResult(false, null, null)
            val safeStageCode = stageCode?.takeIf { it.isNotBlank() }
                ?: return ZhimaTreeActionResult(false, null, null)
            val rawResponse = AntMemberRpcCall.rentGreenTaskFinish(safeTaskId, safeStageCode)
                ?: return ZhimaTreeActionResult(false, null, null)
            val json = JSONObject(rawResponse)
            return ZhimaTreeActionResult(
                success = ResChecker.checkRes(TAG, json),
                response = json,
                rawResponse = rawResponse
            )
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            return ZhimaTreeActionResult(false, null, null)
        }
    }

    /**
     * 净化逻辑
     */
    private suspend fun doPurification(): Unit = CoroutineUtils.run {
        try {
            val homeRes = AntMemberRpcCall.zhimaTreeHomePage() ?: return@run

            val homeJson = JSONObject(homeRes)
            if (!ResChecker.checkRes(TAG, homeJson)) return@run

            val result = homeJson.optJSONObject("extInfo")?.optJSONObject("zhimaTreeHomePageQueryResult")
            if (result == null) return@run

            // 获取净化分数（兼容 currentCleanNum）
            val score = result.optInt("purificationScore", result.optInt("currentCleanNum", 0))
            var treeCode = "ZHIMA_TREE"

            // 尝试获取 remainPurificationClickNum（新逻辑）
            var clicks = score / 100 // 默认兜底：按分数计算
            if (result.has("trees") && result.getJSONArray("trees").length() > 0) {
                val tree = result.getJSONArray("trees").getJSONObject(0)
                treeCode = tree.optString("treeCode", "ZHIMA_TREE")
                // 若服务端明确提供剩余点击次数，则优先使用
                if (tree.has("remainPurificationClickNum")) {
                    clicks = max(0, tree.optInt("remainPurificationClickNum", clicks))
                }
            }

            if (clicks <= 0) {
                Log.sesame("芝麻树🌳[无需净化] 净化值不足（当前: " + score + "g，可点击: " + clicks + "次）")
                return@run
            }

            Log.sesame("芝麻树🌳[开始净化] 可点击 $clicks 次")

            for (i in 0..<clicks) {
                val res = AntMemberRpcCall.zhimaTreeCleanAndPush(treeCode) ?: break

                val json = JSONObject(res)
                if (!ResChecker.checkRes(TAG, json)) break

                val ext = json.optJSONObject("extInfo") ?: continue

                // 优先从标准路径取分数
                var newScore = ext.optJSONObject("zhimaTreeCleanAndPushResult")?.optInt("purificationScore", -1) ?: -1
                // 兼容旧结构：直接在 extInfo 顶层
                if (newScore == -1) {
                    newScore = ext.optInt("purificationScore", score - (i + 1) * 100)
                }

                val growth = ext.optJSONObject("zhimaTreeCleanAndPushResult")?.optJSONObject("currentTreeInfo")?.optInt("scoreSummary", -1) ?: -1

                var log = "芝麻树🌳[净化]第" + (i + 1) + "次 | 剩:" + newScore + "g"
                if (growth != -1) log += "|成长:$growth"
                Log.sesame("$log ✅")

                delay(1500)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }


    /**
     * 查询 + 自动领取贴纸
     */
    @SuppressLint("DefaultLocale")
    fun queryAndCollectStickers() {
        try {
            if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_STICKER)) {
                Log.sesame(TAG, "今日已兑换贴纸，跳过")
                return
            }
            val date = Date()
            val year = SimpleDateFormat("yyyy",Locale.ENGLISH).format(Date())
            val month = SimpleDateFormat("MM",Locale.ENGLISH).format(Date())

            val queryResp = AntMemberRpcCall.queryStickerCanReceive(year, month)

            val queryJson = JSONObject(queryResp)
            if (!ResChecker.checkRes(TAG, queryJson)) {
                Log.error(TAG, "查询贴纸失败：$queryJson")
                return
            }

            val canReceivePageList = queryJson.optJSONArray("canReceivePageList") ?: return

            // 用于存储 ID -> Name 的映射
            val stickerNameMap = mutableMapOf<String, String>()
            val allStickerIds = mutableListOf<String>()

            for (i in 0 until canReceivePageList.length()) {
                val page = canReceivePageList.optJSONObject(i)
                val stickerList = page?.optJSONArray("stickerCanReceiveList") ?: continue
                for (j in 0 until stickerList.length()) {
                    val stickerObj = stickerList.optJSONObject(j) ?: continue
                    val id = stickerObj.optString("id")
                    val name = stickerObj.optString("name")
                    if (!id.isNullOrEmpty()) {
                        allStickerIds.add(id)
                        stickerNameMap[id] = name ?: "未知贴纸"
                    }
                }
            }

            if (allStickerIds.isEmpty()) {
                Log.sesame(TAG, "贴纸扫描：暂无可领取的贴纸")
                //  Status.setFlagToday(StatusFlags.FLAG_AntMember_STICKER)
                return
            }

            // 2. 领取阶段
            val collectResp = AntMemberRpcCall.receiveSticker(year, month, allStickerIds)

            val collectJson = JSONObject(collectResp)
            if (!ResChecker.checkRes(TAG, collectJson)) {
                Log.error(TAG, "领取贴纸失败：$collectJson")
                return
            }

            // 3. 结果解析与比对输出
            val specialList = collectJson.optJSONArray("specialStickerList")
            val obtainedIds = collectJson.optJSONArray("obtainedConfigId")

            Log.sesame(TAG, "贴纸领取成功，总数：${obtainedIds?.length() ?: 0}")

            if (specialList != null && specialList.length() > 0) {
                for (i in 0 until specialList.length()) {
                    val special = specialList.optJSONObject(i) ?: continue

                    // 获取领取结果中的 recordId
                    val recordId = special.optString("stickerRecordId")
                    // 从我们之前的 Map 中根据 ID 找到对应的 Name
                    val stickerName = stickerNameMap[recordId] ?: "特殊贴纸"

                    val ranking = special.optString("rankingText")

                    // 仅对特殊贴纸输出芝麻日志，显示真实的贴纸名称
                    Log.sesame(TAG, "获得特殊贴纸 → $stickerName ($ranking)")
                }
            }

            // 标记今日完成
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_STICKER)

        } catch (e: Exception) {
            Log.printStackTrace("$TAG stickerAutoCollect err", e)
        }
    }

    companion object {
        private val TAG: String = AntMember::class.java.getSimpleName()
        private const val sesameCreditTaskBlacklistModule = "芝麻信用"

        /**
         * 查询 + 自动领取可领取球（精简一行输出领取信息）
         */
        @SuppressLint("DefaultLocale")
        fun queryAndCollect() {
            try {
                var collectedRounds = 0
                var emptyRetryBeforeCollect = 0
                for (attempt in 0..2) {
                    val queryResp = AntMemberRpcCall.Zmxy.queryScoreProgress()
                    if (queryResp.isEmpty()) {
                        return
                    }

                    val json = JSONObject(queryResp)
                    if (!ResChecker.checkRes(TAG, json)) {
                        if (attempt == 0) {
                            Log.sesame(TAG, "攒芝麻分🎁[查询进度球失败，1.2秒后重试]")
                            Thread.sleep(1200)
                            continue
                        }
                        return
                    }

                    val totalWait = json.optJSONObject("totalWaitProcessVO") ?: return
                    val idList = totalWait.optJSONArray("totalProgressIdList")
                    if (idList == null || idList.length() == 0) {
                        if (collectedRounds == 0 && emptyRetryBeforeCollect == 0) {
                            emptyRetryBeforeCollect++
                            Thread.sleep(1200)
                            continue
                        }
                        return
                    }

                    val collectResp = AntMemberRpcCall.Zmxy.collectProgressBall(idList) ?: return
                    val collectJson = JSONObject(collectResp)
                    if (isSesameProgressBallEmpty(collectJson)) {
                        Log.sesame(TAG, "攒芝麻分🎁[暂无可领取进度球]")
                        return
                    }
                    if (!ResChecker.checkRes(TAG, collectJson)) {
                        if (attempt == 0) {
                            Log.sesame(TAG, "攒芝麻分🎁[领取进度球失败，1.2秒后重试]")
                            Thread.sleep(1200)
                            continue
                        }
                        Log.error(TAG, "攒芝麻分🎁[领取失败]#$collectResp")
                        return
                    }

                    Log.sesame(
                        TAG, String.format(
                            "领取完成 → 本次加速进度: %d, 当前加速倍率: %.2f",
                            collectJson.optInt("collectedAccelerateProgress", -1),
                            collectJson.optDouble("currentAccelerateValue", -1.0)
                        )
                    )
                    collectedRounds++
                    Thread.sleep(1200)
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG + "queryAndCollect err", e)
            }
        }

        /**
         * 会员积分收取
         * @param page 第几页
         * @param pageSize 每页数据条数
         */
        internal suspend fun queryPointCert(page: Int, pageSize: Int) {
            try {
                var s = AntMemberRpcCall.queryPointCertV2(page, pageSize)
                var jo = JSONObject(s)
                if (ResChecker.checkRes(TAG + "查询会员积分证书失败:", jo) && jo.has("pointToClaim")) {
                    val pointToClaim = jo.optInt("pointToClaim", 0)
                    if (pointToClaim > 0 && jo.optBoolean("showReceiveAllPointFunction")) {
                        s = AntMemberRpcCall.receiveAllPointByUser()
                        val receiveAllObject = JSONObject(s)
                        if (ResChecker.checkRes(TAG + "会员积分一键领取失败:", receiveAllObject)) {
                            val receiveSumPoint = receiveAllObject.optInt("receiveSumPoint", 0)
                            val receiveStatus = receiveAllObject.optString("receiveStatus")
                            if ("SUCCESS" == receiveStatus || receiveSumPoint > 0) {
                                Log.member("会员积分🎖️[一键领取]#${receiveSumPoint}积分")
                            } else {
                                Log.member(TAG, "会员积分🎖️[一键领取]#未返回SUCCESS(receiveStatus=$receiveStatus)")
                            }
                            return
                        }
                        Log.member(TAG, "会员积分🎖️[一键领取失败，回退逐条领取]")
                    }
                    val hasNextPage = jo.optBoolean("hasNextPage")
                    val jaCertList = jo.optJSONArray("certList") ?: JSONArray()
                    for (i in 0 until jaCertList.length()) {
                        jo = jaCertList.getJSONObject(i)
                        val bizTitle = jo.optString("bizTitle").ifEmpty { jo.optString("title", "会员积分") }
                        val id = jo.optString("id").ifEmpty { jo.optString("certId") }
                        if (id.isEmpty()) {
                            continue
                        }
                        val pointAmount = jo.optInt("pointAmount", jo.optInt("point", 0))
                        s = AntMemberRpcCall.receivePointByUser(id)
                        val receiveObject = JSONObject(s)
                        if (ResChecker.checkRes(TAG + "会员积分领取失败:", receiveObject)) {
                            Log.member("会员积分🎖️[领取$bizTitle]#${pointAmount}积分")
                        } else {
                            Log.member(receiveObject.optString("resultDesc"))
                            Log.member(s)
                        }
                    }
                    if (hasNextPage) {
                        queryPointCert(page + 1, pageSize)
                    }
                    return
                }

                s = AntMemberRpcCall.queryPointCert(page, pageSize)
                jo = JSONObject(s)
                if (ResChecker.checkRes(TAG + "查询会员积分证书失败:", jo)) {
                    val hasNextPage = jo.optBoolean("hasNextPage")
                    val jaCertList = jo.optJSONArray("certList") ?: JSONArray()
                    for (i in 0 until jaCertList.length()) {
                        jo = jaCertList.getJSONObject(i)
                        val bizTitle = jo.optString("bizTitle").ifEmpty { jo.optString("title", "会员积分") }
                        val id = jo.optString("id").ifEmpty { jo.optString("certId") }
                        if (id.isEmpty()) {
                            continue
                        }
                        val pointAmount = jo.optInt("pointAmount", jo.optInt("point", 0))
                        s = AntMemberRpcCall.receivePointByUser(id)
                        val receiveObject = JSONObject(s)
                        if (ResChecker.checkRes(TAG + "会员积分领取失败:", receiveObject)) {
                            Log.member("会员积分🎖️[领取$bizTitle]#${pointAmount}积分")
                        } else {
                            Log.member(receiveObject.optString("resultDesc"))
                            Log.member(s)
                        }
                    }
                    if (hasNextPage) {
                        queryPointCert(page + 1, pageSize)
                    }
                } else {
                    Log.member(jo.getString("resultDesc"))
                    Log.member(s)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryPointCert err:", t)
            }
        }

        /**
         * 检查是否满足运行芝麻信用任务的条件
         * @return bool
         */
        internal fun checkSesameCanRun(): Boolean {
            try {
                val s = AntMemberRpcCall.queryHome()
                val jo = JSONObject(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    val entrance = jo.optJSONObject("entrance") ?: return false
                    if (!entrance.optBoolean("openApp")) {
                        Log.sesame("芝麻信用💳[未开通，本轮跳过]")
                        return false
                    }
                    return true
                }
                Log.sesame(TAG, "芝麻信用💳[V7首页探活失败，回退V8]")
            } catch (t: Throwable) {
                Log.sesame(TAG, "芝麻信用💳[V7首页探活异常，回退V8]#${t.message}")
            }

            try {
                val s = AntMemberRpcCall.queryHomeV8()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.checkSesameCanRun.queryHomeV8", "芝麻信用💳[首页响应失败]#$s")
                    return false
                }
                val entrance = jo.optJSONObject("entrance") ?: return false
                if (!entrance.optBoolean("openApp")) {
                    Log.sesame("芝麻信用💳[未开通，本轮跳过]")
                    return false
                }
                return true
            } catch (t: Throwable) {
                Log.printStackTrace("$TAG.checkSesameCanRun", t)
                return false
            }
        }

        /**
         * 检查任务是否在黑名单中
         * @param taskTitle 任务标题
         * @return true表示在黑名单中，应该跳过
         */
        private fun isTaskInBlacklist(moduleName: String, taskTitle: String?): Boolean {
            return TaskBlacklist.isTaskInBlacklist(moduleName, taskTitle)
        }

        private fun shouldSkipShareAssistSesameTask(task: JSONObject): Boolean {
            return task.optBoolean("shareAssist", false) ||
                task.optString("title").contains("邀请好友") ||
                task.optString("subTitle").contains("邀请成功")
        }

        private fun isTransientSesameTaskError(errorCode: String, resultView: String = ""): Boolean {
            if (errorCode.isEmpty() && resultView.isEmpty()) {
                return false
            }
            return errorCode in setOf(
                "OP_REPEAT_CHECK",
                "SYSTEM_BUSY",
                "NETWORK_ERROR",
                "COLLECT_CREDIT_FEEDBACK_FAILED"
            ) || resultView.contains("请稍后") ||
                resultView.contains("频繁") ||
                resultView.contains("网络不可用")
        }

        private fun isSesameProgressBallEmpty(response: JSONObject): Boolean {
            val resultCode = response.optString("resultCode", response.optString("errorCode", ""))
            val resultView = response.optString("resultView")
            return resultCode == "INIT_SCORE_BALL_EMPTY" ||
                resultCode == "无可领取的信用球" ||
                resultView.contains("无可领取的信用球")
        }

        private fun isSesameAlchemyCapReached(response: JSONObject): Boolean {
            val resultCode = response.optString("resultCode", response.optString("errorCode", ""))
            val resultView = response.optString("resultView")
            return resultCode == "CAP_REACHED" || resultView.contains("盖帽值拦截")
        }

        private fun formatSesameAlchemyReward(task: JSONObject): String {
            val rewardAmount = task.optInt("rewardAmount", 0)
            return when (task.optString("rewardType", "ZML")) {
                "LJCS" -> rewardAmount.toString() + "次炼金次数"
                "ZML" -> rewardAmount.toString() + "粒"
                else -> {
                    val rewardType = task.optString("rewardType")
                    if (rewardType.isEmpty()) {
                        rewardAmount.toString() + "粒"
                    } else {
                        rewardAmount.toString() + rewardType
                    }
                }
            }
        }

        private fun autoBlacklistSesameTaskIfNeeded(
            moduleName: String,
            taskTitle: String,
            errorCode: String,
            resultView: String = ""
        ) {
            if (taskTitle.isBlank() || errorCode.isBlank()) {
                return
            }
            if (isTransientSesameTaskError(errorCode, resultView)) {
                return
            }
            autoAddToBlacklist(moduleName, taskTitle, taskTitle, errorCode)
        }

        private suspend fun joinSesameTaskWithFallback(
            taskTemplateId: String,
            taskTitle: String,
            logPrefix: String,
            primarySceneCode: String? = null
        ): Pair<String, JSONObject> {
            var joinRes = AntMemberRpcCall.joinSesameTask(taskTemplateId, primarySceneCode)
            var joinJo = JSONObject(joinRes)
            val joinResultCode = joinJo.optString("resultCode", joinJo.optString("errorCode", ""))
            if (!ResChecker.checkRes(TAG, joinJo) &&
                !primarySceneCode.isNullOrBlank() &&
                "PROMISE_TODAY_FINISH_TIMES_LIMIT" != joinResultCode
            ) {
                Log.sesame(TAG, "$logPrefix[领取任务扩展参数失败，回退简版参数]#$taskTitle")
                joinRes = AntMemberRpcCall.joinSesameTask(taskTemplateId)
                joinJo = JSONObject(joinRes)
            }
            return joinRes to joinJo
        }

        private suspend fun reportSesameTaskFeedback(
            task: JSONObject,
            taskTitle: String,
            logPrefix: String,
            moduleName: String,
            version: String = "new",
            sceneCode: String? = null,
            preferExtended: Boolean = false
        ): Boolean {
            val templateId = task.optString("templateId")
            if (templateId.isBlank()) {
                Log.sesame(TAG, "$logPrefix[任务回调缺少templateId]#$taskTitle")
                return false
            }

            val bizType = task.optString("bizType")
            val hasExtendedArgs = bizType.isNotBlank() && !sceneCode.isNullOrBlank()
            val feedbackAttempts = mutableListOf<Pair<String, suspend () -> String>>()
            if (preferExtended && hasExtendedArgs) {
                feedbackAttempts.add(
                    "扩展参数" to suspend {
                        AntMemberRpcCall.feedBackSesameTask(templateId, bizType, sceneCode, version)
                    }
                )
            }
            feedbackAttempts.add("简版参数" to suspend { AntMemberRpcCall.feedBackSesameTask(templateId) })
            if (!preferExtended && hasExtendedArgs) {
                feedbackAttempts.add(
                    "扩展参数" to suspend {
                        AntMemberRpcCall.feedBackSesameTask(templateId, bizType, sceneCode, version)
                    }
                )
            }

            var lastErrorCode = ""
            var lastResultView = ""
            var lastFeedbackRes = ""
            for ((index, attempt) in feedbackAttempts.withIndex()) {
                val (attemptLabel, call) = attempt
                val feedbackRes = call()
                lastFeedbackRes = feedbackRes
                val feedbackJo = JSONObject(feedbackRes)
                if (ResChecker.checkRes(TAG, feedbackJo)) {
                    return true
                }
                lastErrorCode = feedbackJo.optString(
                    "errorCode",
                    feedbackJo.optString("resultCode", "")
                )
                lastResultView = feedbackJo.optString("resultView").ifEmpty {
                    feedbackJo.optString("errorMessage", feedbackRes)
                }
                if (index < feedbackAttempts.lastIndex) {
                    Log.sesame(
                        TAG,
                        "$logPrefix[任务回调${attemptLabel}失败，尝试兼容参数]#$taskTitle - $lastResultView"
                    )
                }
            }
            Log.error(TAG, "$logPrefix[任务回调失败]#$taskTitle - $lastResultView")
            autoBlacklistSesameTaskIfNeeded(
                moduleName,
                taskTitle,
                lastErrorCode,
                lastResultView.ifEmpty { lastFeedbackRes }
            )
            return false
        }

    private suspend fun handleSesameAdTask(
        task: JSONObject,
        taskTitle: String,
        logPrefix: String,
        moduleName: String
    ): Boolean {
        val logExtMap = task.optJSONObject("logExtMap")
        if (logExtMap == null) {
            Log.sesame(TAG, "$logPrefix[广告任务缺少logExtMap]#$taskTitle")
            return false
        }
        val bizId = logExtMap.optString("bizId")
        if (bizId.isEmpty()) {
            Log.sesame(TAG, "$logPrefix[广告任务缺少bizId]#$taskTitle")
            return false
        }
        Log.sesame(TAG, "$logPrefix[广告任务准备]#$taskTitle")
        if ("LJCS" == task.optString("rewardType")) {
            val adTaskBizId = task.optString("adTaskBizId").ifEmpty { bizId }
            val rewardRes = AntMemberRpcCall.adRewardLjcs(adTaskBizId)
            val rewardJo = JSONObject(rewardRes)
            if (!ResChecker.checkRes(TAG, rewardJo)) {
                val rewardMsg = rewardJo.optString("resultView").ifEmpty {
                    rewardJo.optString("errorMessage", rewardRes)
                }
                Log.error(TAG, "$logPrefix[炼金次数登记失败]#$taskTitle - $rewardMsg")
                return false
            }
        }
        val adFinishRes = AntMemberRpcCall.taskFinish(bizId)
        val adFinishJo = JSONObject(adFinishRes)
        if (ResChecker.checkRes(TAG, adFinishJo) || "0" == adFinishJo.optString("errCode")) {
            Log.sesame("$logPrefix[广告任务完成: " + taskTitle + "]#获得" + formatSesameAlchemyReward(task))
            return true
        }
        val errorCode = adFinishJo.optString(
            "errorCode",
            adFinishJo.optString("resultCode", adFinishJo.optString("errCode", ""))
        )
        val resultView = adFinishJo.optString("resultView").ifEmpty {
            adFinishJo.optString("errorMessage", adFinishRes)
        }
        Log.error(TAG, "$logPrefix[广告任务上报失败]#$taskTitle - $resultView")
        autoBlacklistSesameTaskIfNeeded(moduleName, taskTitle, errorCode, resultView)
        return false
    }

        /**
         * 芝麻信用-领取并完成任务（带结果统计）
         * @param taskList 任务列表
         * @return 任务处理结果
         * @throws JSONException JSON解析异常，上抛处理
         */
        @Throws(JSONException::class)
        private suspend fun joinAndFinishSesameTaskWithResult(
            taskList: JSONArray,
            transientSkippedTasks: MutableSet<String>
        ): SesameTaskBatchResult {
            var completedCount = 0
            var skippedCount = 0
            var interrupted = false
            var joinLimitReached = hasFlagToday(StatusFlags.FLAG_ANTMEMBER_SESAME_JOIN_LIMIT_REACHED)
            var joinLimitLogged = false

            for (i in 0..<taskList.length()) {
                val task = taskList.getJSONObject(i)
                val taskTitle = if (task.has("title")) task.getString("title") else "未知任务"

                val finishFlag = task.optBoolean("finishFlag", false)
                val actionText = task.optString("actionText", "")

                if (transientSkippedTasks.contains(taskTitle)) {
                    Log.sesame(TAG, "芝麻信用💳[跳过本轮频控任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                if (finishFlag || "已完成" == actionText) {
                    Log.sesame(TAG, "芝麻信用💳[跳过已完成任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                var recordId = task.optString("recordId", "")
                if (recordId.isEmpty() && joinLimitReached) {
                    if (!joinLimitLogged) {
                        Log.sesame(TAG, "芝麻信用💳[领取任务已达当日上限] 今日不再领取新任务")
                        joinLimitLogged = true
                    }
                    skippedCount++
                    continue
                }

                if (isTaskInBlacklist(sesameCreditTaskBlacklistModule, taskTitle)) {
                    Log.sesame(TAG, "芝麻信用💳[跳过黑名单任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                if (shouldSkipShareAssistSesameTask(task)) {
                    Log.sesame(TAG, "芝麻信用💳[跳过助力型任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                val bizType = task.optString("bizType", "")
                if ("AD_TASK" == bizType) {
                    if (handleSesameAdTask(task, taskTitle, "芝麻信用💳", sesameCreditTaskBlacklistModule)) {
                        completedCount++
                    } else {
                        skippedCount++
                    }
                    continue
                }

                if (!task.has("templateId")) {
                    Log.sesame(TAG, "芝麻信用💳[跳过缺少templateId任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                val taskTemplateId = task.getString("templateId")
                val needCompleteNum = if (task.has("needCompleteNum")) task.getInt("needCompleteNum") else 1
                val completedNum = task.optInt("completedNum", 0)
                if (completedNum >= needCompleteNum && needCompleteNum > 0) {
                    Log.sesame(TAG, "芝麻信用💳[跳过已达完成次数]#$taskTitle")
                    skippedCount++
                    continue
                }
                var s: String?
                var responseObj: JSONObject?

                val actionUrl = task.optString("actionUrl", "")
                if (actionUrl.contains("jumpAction") && !actionUrl.contains("jumpAction=userGrowth")) {
                    Log.sesame(TAG, "芝麻信用💳[跳过跳转APP任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                var taskCompleted = false
                if (recordId.isEmpty()) {
                    val joinResult = joinSesameTaskWithFallback(
                        taskTemplateId,
                        taskTitle,
                        "芝麻信用💳",
                        "zml"
                    )
                    s = joinResult.first
                    responseObj = joinResult.second
                    val joinResultCode = responseObj.optString("resultCode", responseObj.optString("errorCode", ""))
                    if ("PROMISE_TODAY_FINISH_TIMES_LIMIT" == joinResultCode) {
                        joinLimitReached = true
                        setFlagToday(StatusFlags.FLAG_ANTMEMBER_SESAME_JOIN_LIMIT_REACHED)
                        Log.sesame(TAG, "芝麻信用💳[领取任务已达当日上限] 今日不再领取新任务")
                        joinLimitLogged = true
                        skippedCount++
                        continue
                    }
                    val joinResultView = responseObj.optString("resultView").ifEmpty {
                        responseObj.optString("errorMessage", s ?: "")
                    }
                    if (isTransientSesameTaskError(joinResultCode, joinResultView)) {
                        transientSkippedTasks.add(taskTitle)
                        Log.sesame(TAG, "芝麻信用💳[领取任务暂时跳过]#$taskTitle#$joinResultView")
                        skippedCount++
                        continue
                    }
                    if (!ResChecker.checkRes(TAG, responseObj)) {
                        Log.error(TAG, "芝麻信用💳[领取任务" + taskTitle + "失败]#" + s)
                        val errorCode = responseObj.optString("errorCode", responseObj.optString("resultCode", ""))
                        val resultView = responseObj.optString("resultView", s ?: "")
                        if (!errorCode.isEmpty()) {
                            autoBlacklistSesameTaskIfNeeded(sesameCreditTaskBlacklistModule, taskTitle, errorCode, resultView)
                        }
                        skippedCount++
                        if (isSesameTaskFlowInterrupted(responseObj)) {
                            interrupted = true
                            break
                        }
                        continue
                    }
                    recordId = responseObj.optJSONObject("data")?.optString("recordId").orEmpty()
                    if (recordId.isEmpty()) {
                        Log.error(TAG, "芝麻信用💳[任务" + taskTitle + "未获取到recordId]#" + task)
                        skippedCount++
                        continue
                    }
                }

                if (!reportSesameTaskFeedback(
                        task,
                        taskTitle,
                        "芝麻信用💳",
                        sesameCreditTaskBlacklistModule,
                        sceneCode = "zml",
                        preferExtended = true
                    )
                ) {
                    skippedCount++
                    if (isSesameTaskFlowInterrupted()) {
                        interrupted = true
                        break
                    }
                    continue
                }

                s = AntMemberRpcCall.finishSesameTask(recordId)
                responseObj = JSONObject(s)
                val errorCode = responseObj.optString("errorCode", responseObj.optString("resultCode", ""))
                val resultView = responseObj.optString("resultView").ifEmpty {
                    responseObj.optString("errorMessage", s ?: "")
                }
                if (isTransientSesameTaskError(errorCode, resultView)) {
                    transientSkippedTasks.add(taskTitle)
                    Log.sesame(TAG, "芝麻信用💳[完成任务暂时跳过]#$taskTitle#$resultView")
                    if (isSesameTaskFlowInterrupted(responseObj)) {
                        interrupted = true
                    }
                } else if (ResChecker.checkRes(TAG, responseObj)) {
                    Log.sesame(
                        TAG,
                        "芝麻信用💳[完成任务" + taskTitle + "]#(" + (completedNum + 1) + "/" + needCompleteNum + "天)"
                    )
                    taskCompleted = true
                } else {
                    Log.error(TAG, "芝麻信用💳[完成任务" + taskTitle + "失败]#" + s)
                    if (!errorCode.isEmpty()) {
                        autoBlacklistSesameTaskIfNeeded(sesameCreditTaskBlacklistModule, taskTitle, errorCode, resultView)
                    }
                    if (isSesameTaskFlowInterrupted(responseObj)) {
                        interrupted = true
                    }
                }

                if (taskCompleted) {
                    completedCount++
                } else {
                    skippedCount++
                }
                if (interrupted) {
                    break
                }
            }

            return SesameTaskBatchResult(completedCount, skippedCount, interrupted)
        }

        private fun isSesameTaskFlowInterrupted(response: JSONObject? = null): Boolean {
            if (ApplicationHookConstants.isOffline()) {
                return true
            }
            if (response == null) {
                return false
            }
            val resultCode = response.optString("resultCode").ifEmpty {
                response.optString("errorCode").ifEmpty {
                    response.optString("code")
                }
            }
            val resultDesc = response.optString("resultDesc").ifEmpty {
                response.optString("errorMsg")
            }
            val resultView = response.optString("resultView")
            return resultCode == "I07" ||
                resultDesc.contains("需要验证") ||
                resultView.contains("需要验证")
        }

        /**
         * 商家开门打卡签到
         */
        private fun kmdkSignIn(): Boolean = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.queryActivity()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.member(TAG, "queryActivity $s")
                    return@run false
                }

                when (jo.optString("signInStatus")) {
                    "SIGN_IN_ENABLE" -> {
                        val activityNo = jo.optString("activityNo")
                        if (activityNo.isEmpty()) return@run false
                        val joSignIn = JSONObject(AntMemberRpcCall.signIn(activityNo))
                        if (ResChecker.checkRes(TAG, joSignIn)) {
                            Log.member("商家服务🏬[开门打卡签到成功]")
                            return@run true
                        }
                        Log.member(TAG, joSignIn.optString("errorMsg"))
                        Log.member(TAG, joSignIn.toString())
                        return@run false
                    }

                    "SIGN_IN_DISABLE" -> return@run true // 通常表示已签到
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "kmdkSignIn err:", t)
            }
            false
        }

        /**
         * 商家开门打卡报名
         */
        private suspend fun kmdkSignUp(): Boolean = CoroutineUtils.run {
            try {
                for (i in 0..4) {
                    val jo = JSONObject(AntMemberRpcCall.queryActivity())
                    if (ResChecker.checkRes(TAG, jo)) {
                        val activityNo = jo.optString("activityNo")
                        if (activityNo.isEmpty()) {
                            delay(500)
                            continue
                        }
                        if (TimeUtil.getFormatDate().replace("-", "") != activityNo.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2]) {
                            break
                        }
                        if ("SIGN_UP" == jo.getString("signUpStatus")) {
                            return@run true
                        }
                        if ("UN_SIGN_UP" == jo.getString("signUpStatus")) {
                            val activityPeriodName = jo.getString("activityPeriodName")
                            val joSignUp = JSONObject(AntMemberRpcCall.signUp(activityNo))
                            if (ResChecker.checkRes(TAG, joSignUp)) {
                                Log.member("商家服务🏬[" + activityPeriodName + "开门打卡报名]")
                                return@run true
                            } else {
                                Log.member(TAG, joSignUp.getString("errorMsg"))
                                Log.member(TAG, joSignUp.toString())
                            }
                        }
                    } else {
                        Log.member(TAG, "queryActivity")
                        Log.member(TAG, jo.toString())
                    }
                    delay(500)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "kmdkSignUp err:", t)
            }
            false
        }

        /**
         * 商家积分签到
         */
        private fun doMerchantSign(): Boolean = CoroutineUtils.run {
            var handled = false
            try {
                if (doMerchantZcjSignIn()) {
                    handled = true
                }
                val s = AntMemberRpcCall.merchantSign()
                var jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    if (!handled) {
                        Log.member(TAG, "doMerchantSign err:$s")
                    }
                    return@run handled
                }
                jo = jo.getJSONObject("data")
                val signResult = jo.optString("signInResult")
                val reward = jo.optString("todayReward")
                if ("SUCCESS" == signResult) {
                    Log.member("商家服务🏬[每日签到]#获得积分$reward")
                    return@run true
                } else {
                    // 对于「已签到 / 不可签到」等情况，直接视为今日已处理，避免反复请求触发风控
                    Log.member(TAG, "商家服务🏬[每日签到]#未返回SUCCESS(signInResult=$signResult,todayReward=$reward)")
                    Log.member(TAG, s)
                    return@run true
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "kmdkSignIn err:", t)
            }
            handled
        }

        /**
         * 商家积分任务
         */
        private suspend fun doMerchantMoreTask(): Unit = CoroutineUtils.run {
            try {
                repeat(3) { roundIndex ->
                    var taskStateChanged = false
                    val taskGroups = queryMerchantTaskGroups()
                    if (taskGroups.isEmpty()) {
                        if (roundIndex == 0) {
                            Log.member(TAG, "商家服务🏬[积分任务]#未查询到任务列表")
                        }
                        return@run
                    }
                    for (taskList in taskGroups) {
                        for (i in 0..<taskList.length()) {
                            val task = taskList.optJSONObject(i) ?: continue
                            if (processMerchantTask(task)) {
                                taskStateChanged = true
                            }
                        }
                    }
                    if (collectMerchantPointBalls()) {
                        taskStateChanged = true
                    }
                    if (!taskStateChanged) {
                        return@run
                    }
                    delay(500)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "taskListQuery err:", t)
            } finally {
                try {
                    delay(1000)
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }
            }
        }

        /**
         * 完成商家积分任务
         * @param taskCode 任务代码
         * @param actionCodes 行为代码候选
         * @param title 标题
         */
        private fun receiveMerchantTask(taskCode: String): Boolean = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.taskReceive(taskCode)
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.member(TAG, "taskReceive $s")
                    return@run false
                }
                return@run true
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "receiveMerchantTask err:", t)
            }
            false
        }

        private suspend fun executeMerchantBrowseTask(
            taskCode: String,
            actionCodes: List<String>,
            title: String,
            taskStatus: String,
            targetCount: Int = 1
        ): Boolean = CoroutineUtils.run {
            try {
                if ("UNRECEIVED" == taskStatus && !receiveMerchantTask(taskCode)) {
                    return@run false
                }

                ActionDelayUtil.humanActionDelay(500L)
                for (actionCode in actionCodes) {
                    var jo = JSONObject(AntMemberRpcCall.actioncode(actionCode))
                    if (!ResChecker.checkRes(TAG, jo)) {
                        Log.member(TAG, "taskQueryByActionCode $jo")
                        continue
                    }

                    var produceSuccess = false
                    var remainingCount = max(1, targetCount)
                    for (index in 0 until max(1, targetCount)) {
                        if (index > 0) {
                            delay(5000)
                        }
                        jo = JSONObject(AntMemberRpcCall.produce(actionCode))
                        if (!ResChecker.checkRes(TAG, jo)) {
                            Log.member(TAG, "taskProduce $jo")
                            break
                        }
                        produceSuccess = true

                        val refreshedTask = queryMerchantTaskByCode(taskCode) ?: break
                        val refreshedStatus = refreshedTask.optString("status")
                        if ("NEED_RECEIVE" == refreshedStatus || ("PROCESSING" != refreshedStatus && "UNRECEIVED" != refreshedStatus)) {
                            break
                        }

                        val refreshedRemainingCount = resolveMerchantTaskRemainingCount(refreshedTask) ?: break
                        if (refreshedRemainingCount <= 0 || refreshedRemainingCount >= remainingCount) {
                            break
                        }
                        remainingCount = refreshedRemainingCount
                    }

                    if (produceSuccess) {
                        Log.member("商家服务🏬[完成任务$title]")
                        return@run true
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "executeMerchantBrowseTask err:", t)
            }
            false
        }

        private fun queryMerchantTaskByCode(taskCode: String): JSONObject? {
            if (taskCode.isEmpty()) {
                return null
            }
            val taskGroups = queryMerchantTaskGroups()
            for (taskList in taskGroups) {
                for (i in 0..<taskList.length()) {
                    val task = taskList.optJSONObject(i) ?: continue
                    if (taskCode == task.optString("taskCode")) {
                        return task
                    }
                }
            }
            return null
        }

        private fun canRunMerchantService(): Boolean = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.transcodeCheck()
                val jo = JSONObject(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    val data = jo.optJSONObject("data")
                    if (data?.optBoolean("isOpened") == true) {
                        return@run true
                    }
                    Log.member(TAG, "商家服务🏬[未开通，本轮跳过]")
                    return@run false
                }
                val errorCode = jo.optInt("error", jo.optInt("errorNo", 0))
                val errorTip = jo.optString("errorTip")
                val errorMessage = jo.optString("errorMessage", jo.optString("errorMsg"))
                if (errorCode == 1009 || errorTip == "1009" || errorMessage.contains("訪問被拒絕") || errorMessage.contains("访问被拒绝")) {
                    Log.member(TAG, "商家服务🏬[开通检查返回1009，本轮跳过]")
                    return@run false
                }
                Log.member(TAG, "商家服务🏬[开通检查失败，本轮跳过]#$s")
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "canRunMerchantService err:", t)
            }
            false
        }

        private fun doMerchantZcjSignIn(): Boolean = CoroutineUtils.run {
            try {
                val queryResp = JSONObject(AntMemberRpcCall.zcjSignInQuery())
                if (!ResChecker.checkRes(TAG, queryResp)) {
                    return@run false
                }
                val button = queryResp.optJSONObject("data")?.optJSONObject("button") ?: return@run false
                when (button.optString("status")) {
                    "RECEIVED" -> return@run true
                    "UNRECEIVED" -> {
                        val executeResp = JSONObject(AntMemberRpcCall.zcjSignInExecute())
                        if (!ResChecker.checkRes(TAG, executeResp)) {
                            Log.member(TAG, "doMerchantZcjSignIn err:$executeResp")
                            return@run false
                        }
                        val data = executeResp.optJSONObject("data")
                        val reward = data?.optString("todayReward").orEmpty()
                        val widgetName = data?.optString("widgetName").orEmpty().ifEmpty { "招财金签到" }
                        if (reward.isNotEmpty()) {
                            Log.member("商家服务🏬[$widgetName]#获得积分$reward")
                        } else {
                            Log.member("商家服务🏬[$widgetName]")
                        }
                        return@run true
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "doMerchantZcjSignIn err:", t)
            }
            false
        }

        private fun queryMerchantTaskGroups(): List<JSONArray> {
            val taskGroups = mutableListOf<JSONArray>()
            try {
                val zcjResp = JSONObject(AntMemberRpcCall.zcjTaskListQueryV2())
                if (ResChecker.checkRes(TAG, zcjResp)) {
                    val moduleList = zcjResp.optJSONObject("data")?.optJSONArray("moduleList")
                    if (moduleList != null) {
                        for (i in 0..<moduleList.length()) {
                            val module = moduleList.optJSONObject(i) ?: continue
                            if (module.optString("planCode") == "MORE") {
                                module.optJSONArray("taskList")?.let(taskGroups::add)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryMerchantTaskGroups zcjTaskListQueryV2 err:", t)
            }

            if (taskGroups.isNotEmpty()) {
                return taskGroups
            }

            try {
                val legacyResp = JSONObject(AntMemberRpcCall.taskListQuery())
                if (ResChecker.checkRes(TAG, legacyResp)) {
                    legacyResp.optJSONObject("data")?.optJSONArray("taskList")?.let(taskGroups::add)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryMerchantTaskGroups taskListQuery err:", t)
            }
            return taskGroups
        }

        private suspend fun processMerchantTask(task: JSONObject): Boolean = CoroutineUtils.run {
            val taskStatus = task.optString("status")
            if (taskStatus.isEmpty()) {
                return@run false
            }

            val title = task.optString("title", task.optString("taskName", "商家任务"))
            val reward = task.optString("reward", task.optString("point"))

            if ("NEED_RECEIVE" == taskStatus) {
                val pointBallId = task.optString("pointBallId")
                if (pointBallId.isNotEmpty()) {
                    return@run receiveMerchantPointBall(pointBallId, title, reward)
                }
                return@run false
            }

            if ("PROCESSING" != taskStatus && "UNRECEIVED" != taskStatus) {
                return@run false
            }

            val bizId = resolveMerchantBizId(task)
            if ("PROCESSING" == taskStatus && bizId.isNotEmpty()) {
                val jo = JSONObject(AntMemberRpcCall.taskFinish(bizId))
                if (ResChecker.checkRes(TAG, jo)) {
                    Log.member("商家服务🏬[$title]#领取积分$reward")
                    return@run true
                }
                return@run false
            }

            val taskCode = task.optString("taskCode")
            val actionCodes = resolveMerchantActionCodes(task)
            if (taskCode.isEmpty() || actionCodes.isEmpty()) {
                return@run false
            }

            return@run executeMerchantBrowseTask(taskCode, actionCodes, title, taskStatus, resolveMerchantTaskTargetCount(task))
        }

        private fun resolveMerchantBizId(task: JSONObject): String {
            return task.optJSONObject("extendLog")
                ?.optJSONObject("bizExtMap")
                ?.optString("bizId")
                .orEmpty()
        }

        private fun resolveMerchantActionCodes(task: JSONObject): List<String> {
            val candidates = LinkedHashSet<String>()
            val buttonActionCode = task.optJSONObject("button")
                ?.optJSONObject("extInfo")
                ?.optString("actionCode")
                .orEmpty()
            addMerchantActionCodeCandidates(candidates, buttonActionCode)

            val taskActionCode = task.optString("actionCode")
            addMerchantActionCodeCandidates(candidates, taskActionCode)

            val taskCode = task.optString("taskCode")
            if (task.has("sendPointImmediately") && taskCode.isNotEmpty()) {
                addMerchantActionCodeCandidate(candidates, "${taskCode}_VIEWED")
            }
            addMerchantActionCodeCandidate(candidates, when (taskCode) {
                "SYH_CPC_DYNAMIC" -> "SYH_CPC_DYNAMIC_VIEWED"
                "JFLLRW_TASK" -> "JFLL_VIEWED"
                "ZFBHYLLRW_TASK" -> "ZFBHYLL_VIEWED"
                "QQKLLRW_TASK" -> "QQKLL_VIEWED"
                "RCR_RWZX_LLRW_TASK" -> "rcr_llrw_VIEWED"
                "SSLLRW_TASK" -> "SSLL_VIEWED"
                "CYLLRW_TASK" -> "CYLLRW_VIEWED"
                "ELMGYLLRW2_TASK" -> "ELMGYLL_VIEWED"
                "ZMXYLLRW_TASK" -> "ZMXYLL_VIEWED"
                "GXYKPDDYH_TASK" -> "xykhkzd_VIEWED"
                "HHKLLRW_TASK" -> "HHKLLX_VIEWED"
                "TBNCLLRW_TASK" -> "TBNCLLRW_TASK_VIEWED"
                else -> null
            })
            return candidates.toList()
        }

        private fun addMerchantActionCodeCandidates(candidates: LinkedHashSet<String>, actionCode: String?) {
            val normalizedActionCode = actionCode.orEmpty().trim()
            if (normalizedActionCode.isEmpty()) {
                return
            }
            candidates.add(normalizedActionCode)
            if (!normalizedActionCode.endsWith("_VIEWED")) {
                candidates.add("${normalizedActionCode}_VIEWED")
            }
        }

        private fun addMerchantActionCodeCandidate(candidates: LinkedHashSet<String>, actionCode: String?) {
            val normalizedActionCode = actionCode.orEmpty().trim()
            if (normalizedActionCode.isNotEmpty()) {
                candidates.add(normalizedActionCode)
            }
        }

        private fun resolveMerchantTaskTargetCount(task: JSONObject): Int {
            return max(1, resolveMerchantTaskRemainingCount(task) ?: 1)
        }

        private fun resolveMerchantTaskRemainingCount(task: JSONObject): Int? {
            val target = task.optInt("target", Int.MIN_VALUE)
            val current = task.optInt("current", 0)
            if (target != Int.MIN_VALUE) {
                return (target - current).coerceAtLeast(0)
            }

            val targetCount = task.optInt("targetCount", Int.MIN_VALUE)
            val currentCount = task.optInt("currentCount", 0)
            if (targetCount != Int.MIN_VALUE) {
                return (targetCount - currentCount).coerceAtLeast(0)
            }

            return null
        }

        private suspend fun collectMerchantPointBalls(): Boolean = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.merchantBallQuery()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    return@run false
                }
                val pointBalls = jo.optJSONObject("data")?.optJSONArray("pointBalls") ?: return@run false
                var received = false
                for (i in 0..<pointBalls.length()) {
                    val pointBall = pointBalls.optJSONObject(i) ?: continue
                    val ballId = pointBall.optString("id")
                    if (ballId.isEmpty()) {
                        continue
                    }
                    val ballName = pointBall.optString("name", "积分球")
                    val receiveResp = JSONObject(AntMemberRpcCall.ballReceive(ballId))
                    if (!ResChecker.checkRes(TAG, receiveResp)) {
                        continue
                    }
                    val pointReceived = receiveResp.optJSONObject("data")?.optString("pointReceived").orEmpty()
                    if (pointReceived.isNotEmpty()) {
                        Log.member("商家服务🏬领取[$ballName]#获得积分$pointReceived")
                    } else {
                        Log.member("商家服务🏬领取[$ballName]")
                    }
                    received = true
                }
                return@run received
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "collectMerchantPointBalls err:", t)
            }
            false
        }

        private suspend fun receiveMerchantPointBall(
            pointBallId: String,
            title: String,
            reward: String
        ): Boolean = CoroutineUtils.run {
            try {
                val jo = JSONObject(AntMemberRpcCall.ballReceive(pointBallId))
                if (!ResChecker.checkRes(TAG, jo)) {
                    return@run false
                }
                val pointReceived = jo.optJSONObject("data")?.optString("pointReceived").orEmpty()
                if (pointReceived.isNotEmpty()) {
                    Log.member("商家服务🏬[$title]#领取积分$pointReceived")
                } else if (reward.isNotEmpty()) {
                    Log.member("商家服务🏬[$title]#领取积分$reward")
                } else {
                    Log.member("商家服务🏬[$title]#领取积分")
                }
                return@run true
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "receiveMerchantPointBall err:", t)
            }
            false
        }
    }

    /**
     * 【新增】芝麻粒兑换道具
     * 仿照会员积分兑换逻辑：遍历列表更新Map，同时匹配用户设置进行兑换
     */
    internal suspend fun doSesameGrainExchange(): Unit = CoroutineUtils.run {
        // 每日只运行一次，避免重复请求
        if (hasFlagToday(StatusFlags.FLAG_ZMXY_GRAIN_EXCHANGE_DONE)) {
            return@run
        }

        try {
            val userId = UserMap.currentUid
            // 获取用户在配置中选中的商品ID列表（白名单）
            val targetIds = sesameGrainExchangeList?.value ?: emptySet()
            var currentPage = 1
            // 限制最大页数，防止无限循环（抓包看大概也就3-5页）
            val maxPage = 10
            val pageSize = 99 //适当调整pageSize 减少请求
            var hasNextPage = true

            while (hasNextPage && currentPage <= maxPage) {
                // 稍微延时，避免请求过快被风控
                GlobalThreadPools.sleepCompat(1500L)
                // 调用 RPC 获取列表
                val jo = JSONObject(AntMemberRpcCall.queryExchangeList(currentPage, pageSize))
//                所有的请求使用这个类方法检查过滤就行了
                if (!ResChecker.checkRes(TAG, jo)) {//一次失败直接return不要break
                    Log.error(TAG, "芝麻粒商品列表校验失败: $jo")
                    return@run
                }

                val data = jo.optJSONObject("data") ?: return@run //没数据也return
                val list = data.optJSONArray("awardTemplateList") ?: return@run

                // 遍历当前页的商品
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val name = item.optString("awardName", "未知商品")
                    val id = item.optString("awardTemplateId")
                    val pointNeeded = item.optString("point", "0")
                    val remainingBudget = item.optInt("remainingBudget", 0) // 库存
                    if (id.isEmpty()) continue
                    // 1. 核心步骤：记录 ID 和 名称 的映射关系
                    // 这样下次进入设置界面，就能看到中文名称了
                    IdMapManager.getInstance(SesameGiftMap::class.java).add(id, name)
                    // 2. 检查是否在用户的待兑换列表里（白名单）
                    val inWhiteList = targetIds.contains(id)
                    if (!inWhiteList) {
                        // 如果没勾选，就跳过，不做处理
                        continue
                    }
                    // 3. 检查库存
                    if (remainingBudget <= 0) {
                        Log.sesame(TAG, "跳过[$name]: 库存不足")
                        continue
                    }
                    // 4. 执行兑换 (这里不加每日限制判断了，只要有库存且勾选了就尝试兑换)
                    Log.sesame(TAG, "准备兑换[$name], ID: $id, 需芝麻粒: $pointNeeded")
                    if (exchangeSesameGift(id, name, pointNeeded)) {
                        // 兑换成功后，稍微等待一下
                        delay(2000)
                    }
                }
                // 判断是否有下一页
                hasNextPage = data.optBoolean("hasNext", false)
                currentPage++
            }

            // 保存映射关系到本地文件 sesame_gift.json
            IdMapManager.getInstance(SesameGiftMap::class.java).save(userId)
            Log.sesame(TAG, "芝麻粒兑换任务处理完毕，商品列表已更新")
            // 标记今日已完成
            setFlagToday(StatusFlags.FLAG_ZMXY_GRAIN_EXCHANGE_DONE)

        } catch (t: Throwable) {//这里
            Log.printStackTrace(TAG, "doSesameGrainExchange 运行异常:", t)
        }
    }

    /**
     * 执行具体的芝麻粒兑换请求
     */
    private fun exchangeSesameGift(templateId: String, name: String, point: String): Boolean {
        try {
            // 调用兑换接口
            val resString = AntMemberRpcCall.obtainAward(templateId)
            val jo = JSONObject(resString)

            // 检查结果
            if (ResChecker.checkRes(TAG, jo)) {
                val recordId = jo.optJSONObject("data")?.optString("awardRecordId", "")
                Log.sesame("芝麻粒兑换🛒[成功] $name #消耗${point}粒")
                return true
            } else {
                val errorMsg = jo.optString("resultView", resString)
                // 如果是“积分不足”等错误，也会在这里打印
                Log.error(TAG, "兑换失败[$name]: $errorMsg")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "exchangeSesameGift 错误:", t)
        }
        return false
    }
}


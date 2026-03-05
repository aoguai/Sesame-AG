package fansirsqi.xposed.sesame.task.antMember

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status.Companion.canMemberPointExchangeBenefitToday
import fansirsqi.xposed.sesame.data.Status.Companion.canMemberSignInToday
import fansirsqi.xposed.sesame.data.Status.Companion.hasFlagToday
import fansirsqi.xposed.sesame.data.Status.Companion.memberPointExchangeBenefitToday
import fansirsqi.xposed.sesame.data.Status.Companion.memberSignInToday
import fansirsqi.xposed.sesame.data.Status.Companion.setFlagToday
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.entity.MemberBenefit
import fansirsqi.xposed.sesame.entity.SesameGift
import fansirsqi.xposed.sesame.hook.internal.LocationHelper.requestLocationSuspend
import fansirsqi.xposed.sesame.hook.internal.SecurityBodyHelper.getSecurityBodyData
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.util.TaskBlacklist.autoAddToBlacklist
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.antOrchard.AntOrchardRpcCall.orchardSpreadManure
import fansirsqi.xposed.sesame.util.CoroutineUtils
import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Log.record
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.TaskBlacklist
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.maps.IdMapManager
import fansirsqi.xposed.sesame.util.maps.MemberBenefitsMap
import fansirsqi.xposed.sesame.util.maps.SesameGiftMap
import fansirsqi.xposed.sesame.util.maps.UserMap
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

    private var memberSign: BooleanModelField? = null
    private var memberTask: BooleanModelField? = null
    private var memberPointExchangeBenefit: BooleanModelField? = null
    private var memberPointExchangeBenefitList: SelectModelField? = null
    private var collectSesame: BooleanModelField? = null
    private var collectSesameWithOneClick: BooleanModelField? = null
    private var sesameTask: BooleanModelField? = null
    private var collectInsuredGold: BooleanModelField? = null
    private var enableGameCenter: BooleanModelField? = null
    private var merchantSign: BooleanModelField? = null
    private var merchantKmdk: BooleanModelField? = null
    private var merchantMoreTask: BooleanModelField? = null
    private var beanSignIn: BooleanModelField? = null
    private var beanExchangeBubbleBoost: BooleanModelField? = null

    // 芝麻炼金
    private var sesameAlchemy: BooleanModelField? = null

    // 芝麻树
    private var enableZhimaTree: BooleanModelField? = null

    /*//年度回顾
    private var annualReview: BooleanModelField? = null*/

    // 黄金票配置 - 签到
    private var enableGoldTicket: BooleanModelField? = null

    // 黄金票配置 - 提取/兑换
    private var enableGoldTicketConsume: BooleanModelField? = null

    /** 账单 贴纸 功能开关 */
    private var collectStickers: BooleanModelField? = null

    // 【新增】芝麻粒兑换
    private var sesameGrainExchange: BooleanModelField? = null
    private var sesameGrainExchangeList: SelectModelField? = null

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(BooleanModelField("memberSign", "会员签到", false).also {
            memberSign = it
        })
        modelFields.addField(BooleanModelField("memberTask", "会员任务", false).also {
            memberTask = it
        })



        modelFields.addField(
            BooleanModelField(
                "memberPointExchangeBenefit", "会员积分 | 兑换权益", false
            ).also { memberPointExchangeBenefit = it })
        modelFields.addField(
            SelectModelField(
                "memberPointExchangeBenefitList",
                "会员积分 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                MemberBenefit.getList()
            }.also { memberPointExchangeBenefitList = it })


        modelFields.addField(
            BooleanModelField(
                "sesameGrainExchange", "芝麻信用 | 芝麻粒兑换道具", false
            ).also { sesameGrainExchange = it })

        // 使用 SesameGiftMap 来存储和回显商品名称
        modelFields.addField(
            SelectModelField(
                "sesameGrainExchangeList",
                "芝麻信用 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                SesameGift.getList()
            }.also { sesameGrainExchangeList = it })

        modelFields.addField(
            BooleanModelField(
                "sesameTask", "芝麻信用|芝麻粒信用任务", false
            ).also { sesameTask = it })
        modelFields.addField(BooleanModelField("collectSesame", "芝麻信用|芝麻粒领取", false).also {
            collectSesame = it
        })
        modelFields.addField(
            BooleanModelField(
                "collectSesameWithOneClick", "芝麻信用|芝麻粒领取使用一键收取", false
            ).also { collectSesameWithOneClick = it })
        // 芝麻炼金
        modelFields.addField(
            BooleanModelField(
                "sesameAlchemy", "芝麻炼金", false
            ).also { sesameAlchemy = it })
        // 芝麻树
        modelFields.addField(BooleanModelField("enableZhimaTree", "芝麻信用|芝麻树", false).also {
            enableZhimaTree = it
        })


        modelFields.addField(
            BooleanModelField(
                "collectInsuredGold", "蚂蚁保|保障金领取", false
            ).also { collectInsuredGold = it })

        // 黄金票配置
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicket", "黄金票签到", false
            ).also { enableGoldTicket = it })
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicketConsume", "黄金票提取(兑换黄金)", false
            ).also { enableGoldTicketConsume = it })
        modelFields.addField(BooleanModelField("enableGameCenter", "游戏中心签到", false).also {
            enableGameCenter = it
        })
        modelFields.addField(
            BooleanModelField(
                "merchantSign", "商家服务|签到", false
            ).also { merchantSign = it })
        modelFields.addField(
            BooleanModelField(
                "merchantKmdk", "商家服务|开门打卡", false
            ).also { merchantKmdk = it })
        modelFields.addField(
            BooleanModelField(
                "merchantMoreTask", "商家服务|积分任务", false
            ).also {
                merchantMoreTask = it
            })
        modelFields.addField(
            BooleanModelField(
                "beanSignIn", "安心豆签到", false
            ).also { beanSignIn = it })
        modelFields.addField(
            BooleanModelField(
                "beanExchangeBubbleBoost", "安心豆兑换时光加速器", false
            ).also { beanExchangeBubbleBoost = it })
       /* modelFields.addField(
            BooleanModelField(
                "annualReview", "年度回顾", false
            ).also { annualReview = it })*/


        modelFields.addField(
            BooleanModelField("CollectStickers", "领取贴纸", false).also { collectStickers = it }
        )



        return modelFields
    }

    override fun runJava() {
        // 使用协程上下文运行
        runBlocking {
            try {
                record(TAG, "执行开始-${getName()}")
                // 异步获取位置信息-for 2101
                requestLocationSuspend()
                // 芝麻信用相关检测
                val isSesameOpened: Boolean = checkSesameCanRun()

                // 并行执行独立任务
                val deferredTasks = mutableListOf<Deferred<Unit>>()

                if (memberSign?.value == true) {
                    if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_SIGN_DONE)) {
                        record(TAG, "⏭️ 今天已处理过会员签到，跳过执行")
                    } else {
                        deferredTasks.add(async(Dispatchers.IO) { doMemberSign() })
                    }
                }

                if (memberTask?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { doAllMemberAvailableTask() })
                }

                if (memberPointExchangeBenefit?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { memberPointExchangeBenefit() })
                }
                if (isSesameOpened) {

                    // 芝麻粒兑换入口
                    if (sesameGrainExchange?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) { doSesameGrainExchange() })
                    }
                    if (sesameTask?.value == true || collectSesame?.value == true) {
                        // 芝麻粒福利签到（每日兜底：避免重复请求触发风控）
                        if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_ZML_CHECKIN_DONE)) {
                            record(TAG, "⏭️ 今天已处理过芝麻粒福利签到，跳过执行")
                        } else {
                            doSesameZmlCheckIn()
                        }
                        if (sesameTask?.value == true) {
                            if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK)) {
                                record(TAG, "⏭️ 今天已完成过芝麻信用任务，跳过执行")
                            } else {
                                record(TAG, "🎮 开始执行芝麻信用任务")
                                doAllAvailableSesameTask()
                                handleGrowthGuideTasks()
                                queryAndCollect() //做完任务领取球
                                record(TAG, "✅ 芝麻信用任务已完成，今天不再执行")
                            }
                        }
                        if (collectSesame?.value == true) {
                            if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_COLLECT_SESAME_DONE)) {
                                record(TAG, "⏭️ 今天已处理过芝麻粒领取，跳过执行")
                            } else {
                                deferredTasks.add(async(Dispatchers.IO) {
                                    collectSesame(
                                        collectSesameWithOneClick?.value == true
                                    )
                                })
                            }
                        }
                    }

                    // 芝麻炼金
                    if (sesameAlchemy?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) {
                            doSesameAlchemy()
                            // ===== 次日奖励：只有今天还没领过才执行 =====
                            if (!hasFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)) {
                                doSesameAlchemyNextDayAward()
                            } else record(TAG, "✅ 芝麻粒次日奖励已领取，今天不再执行")
                        })
                    }

                    // 芝麻树
                    if (enableZhimaTree?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) { doZhimaTree() })
                    }
                }


                //保障金
                if (collectInsuredGold?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { collectInsuredGold() })
                }

                // 【更新】执行黄金票任务，替换旧的 goldTicket()
                if (enableGoldTicket?.value == true || enableGoldTicketConsume?.value == true) {
                    // 传入签到和提取的开关值
                    deferredTasks.add(async(Dispatchers.IO) {
                        doGoldTicketTask(
                            enableGoldTicket?.value == true, enableGoldTicketConsume?.value == true
                        )
                    })
                }

                if (enableGameCenter?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { enableGameCenter() })
                }

                if (beanSignIn?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { beanSignIn() })
                }

               /* if (annualReview!!.value) {   //年度回顾已下线
                    deferredTasks.add(async(Dispatchers.IO) { doAnnualReview() })
                }*/

                if (beanExchangeBubbleBoost?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { beanExchangeBubbleBoost() })
                }



                if (merchantSign?.value == true || merchantKmdk?.value == true || merchantMoreTask?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) {
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
                            record(TAG, "⏭️ 今天已处理过商家服务相关任务，跳过执行")
                            return@async
                        }

                        val jo = JSONObject(AntMemberRpcCall.transcodeCheck())
                        if (!ResChecker.checkRes(TAG, jo)) {
                            return@async
                        }
                        val data = jo.getJSONObject("data")
                        if (!data.optBoolean("isOpened")) {
                            record(TAG, "商家服务👪未开通")
                            return@async
                        }
                        if (merchantKmdk?.value == true) {
                            if (needKmdkSignIn) {
                                if (kmdkSignIn()) {
                                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE)
                                }
                            } else if (TimeUtil.isNowAfterTimeStr("1200")) {
                                // 过了时间窗不再尝试，避免重复请求
                                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE)
                            }
                            if (needKmdkSignUp) {
                                if (kmdkSignUp()) {
                                    setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNUP_DONE)
                                }
                            }
                        }
                        if (needMerchantSign) {
                            if (doMerchantSign()) {
                                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MERCHANT_SIGN_DONE)
                            }
                        }
                        if (needMerchantMoreTask) {
                            doMerchantMoreTask()
                        }
                    })
                }





                if (collectStickers?.value == true) {
                    queryAndCollectStickers()
                }


                // 等待所有异步任务完成
                deferredTasks.awaitAll()

            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
            } finally {
                record(TAG, "执行结束-${getName()}")
            }
        }
    }

    private fun handleGrowthGuideTasks() {
        try {
            record("$TAG.", "开始执行信誉任务领取")
            var resp: String?
            try {
                resp = AntMemberRpcCall.Zmxy.queryGrowthGuideToDoList("yuebao_7d", "1.0.2025.10.27")
            } catch (e: Throwable) {
                Log.printStackTrace("$TAG.handleGrowthGuideTasks.queryGrowthGuideToDoList", e)
                return
            }

            if (resp.isNullOrEmpty()) {
                record("$TAG.handleGrowthGuideTasks", "信誉任务列表返回空")
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
                record(
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
                            Log.other("信誉任务[领取成功] $title")
                        } else {
                            record(
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
                        Log.other("信誉任务[每日问答] $subTitle（跳过答题）")
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
                            Log.other(
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
                        Log.other("信誉任务[视频问答提交成功] → ")
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
                        record(TAG, "set Wua $wua")

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
                            record(
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

                        Log.forest("今日农场已施肥💩 $dailyAppWateringCount 次 [$stageText]")

                        Log.other(
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
            record("$TAG.doAnnualReview", "年度回顾🎞[开始执行]")

            val resp = AntMemberRpcCall.annualReviewQueryTasks()
            if (resp == null || resp.isEmpty()) {
                record("$TAG.doAnnualReview", "年度回顾[查询返回空]")
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
                record("$TAG.doAnnualReview", "年度回顾[查询失败]#$resp")
                return
            }

            val components = root.optJSONObject("components")
            if (components == null || components.length() == 0) {
                record("$TAG.doAnnualReview", "年度回顾[components 为空]")
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
                record("$TAG.doAnnualReview", "年度回顾[未找到查询组件]")
                return
            }
            if (!queryComp.optBoolean("isSuccess", true)) {
                record("$TAG.doAnnualReview", "年度回顾[查询组件返回失败]")
                return
            }

            val content = queryComp.optJSONObject("content")
            if (content == null) {
                record("$TAG.doAnnualReview", "年度回顾[content 为空]")
                return
            }

            val taskList = content.optJSONArray("playTaskOrderInfoList")
            if (taskList == null || taskList.length() == 0) {
                record("$TAG.doAnnualReview", "年度回顾[当前无可处理任务]")
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
                    record("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#响应为空")
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
                    record("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#$applyResp")
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

                delay(500)

                // ========== Step 2: 提交任务完成 (process) ==========
                val processResp = AntMemberRpcCall.annualReviewProcessTask(code, recordNo)
                if (processResp == null || processResp.isEmpty()) {
                    record("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#响应为空")
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
                    record("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#$processResp")
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
                Log.other("年度回顾🎞[任务完成]$taskName#状态=$newStatus 奖励状态=$rewardStatus")
            }

            record(
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
    private fun memberPointExchangeBenefit() {
        if (hasFlagToday("memberBenefit::refresh")) {
            return
        }
        val whiteList: Set<String> = memberPointExchangeBenefitList?.value
            ?.filterNotNull()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        if (whiteList.isNotEmpty() && whiteList.all { !canMemberPointExchangeBenefitToday(it) }) {
            record(TAG, "会员积分🎐兑换列表今日已全部处理，跳过执行")
            setFlagToday("memberBenefit::refresh")
            return
        }
        try {
            val userId = UserMap.currentUid
            record(TAG, "会员积分商品加载..")
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
                            record(TAG, "商品[$name] 没有 benefitId，跳过")
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
                            record(TAG, "跳过[$name]: 今日已兑换过")
                            continue
                        }
                        // 5. 执行兑换
                        record(TAG, "准备兑换[$name], ID: $benefitId, 需积分: $pointNeeded")
                        if (exchangeBenefit(benefitId, itemId, userId)) {
                            Log.other("会员积分🎐兑换[$name]#花费[$pointNeeded 积分]")
                        } else {
                            record(TAG, "兑换失败: $name (ItemId: $itemId)")
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
                        record(TAG, "会员积分🎐兑换列表已全部扫描到，提前结束")
                        setFlagToday("memberBenefit::refresh")
                        return
                    }
                }
                IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
                record(TAG, "分类[$catName]处理完毕，已执行中间保存")
            }
            // 7. 保存映射表
            IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
            record(TAG, "会员积分🎐全部分类任务处理完毕")
            setFlagToday("memberBenefit::refresh")

        } catch (t: Throwable) {
            record(TAG, "memberPointExchangeBenefit 运行异常: ${t.message}")
            Log.printStackTrace(TAG, t)
        }
    }

    private fun exchangeBenefit(benefitId: String, itemid: String, userid: String?): Boolean {
        try {
            val resString = AntMemberRpcCall.exchangeBenefit(benefitId, itemid, userid)
            val jo = JSONObject(resString)
            val resultCode = jo.optString("resultCode")

            if (resultCode == "BEYOND_BUYING_TIMES") {
                record(TAG, "会员权益兑换已达上限，标记任务今日完成")
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
    private suspend fun doMemberSign(): Unit = CoroutineUtils.run {
        var doneToday = false
        try {
            val uid = UserMap.currentUid
            if (!canMemberSignInToday(uid)) {
                doneToday = true
            } else {
                val s = AntMemberRpcCall.queryMemberSigninCalendar()
                delay(500)
                val jo = JSONObject(s)
                if (ResChecker.checkRes(TAG + "会员签到失败:", jo)) {
                    Log.other(
                        "会员签到📅[" + jo.getString("signinPoint") + "积分]#已签到" + jo.getString(
                            "signinSumDay"
                        ) + "天"
                    )
                    memberSignInToday(uid)
                    doneToday = true
                } else {
                    val resultDesc = jo.optString("resultDesc", "")
                    if (resultDesc.contains("已签到")) {
                        memberSignInToday(uid)
                        doneToday = true
                    }
                    record(resultDesc)
                    record(s)
                }
            }
            queryPointCert(1, 8)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doMemberSign err:", t)
        } finally {
            if (doneToday) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_SIGN_DONE)
            }
        }
    }

    /**
     * 会员任务-逛一逛
     * 单次执行 1
     */
    private suspend fun doAllMemberAvailableTask(): Unit = CoroutineUtils.run {
        try {
            val str = AntMemberRpcCall.queryAllStatusTaskList()
            delay(500)
            val jsonObject = JSONObject(str)
            if (!ResChecker.checkRes(TAG, jsonObject)) {
                Log.error(
                    "$TAG.doAllMemberAvailableTask", "会员任务响应失败: " + jsonObject.getString("resultDesc")
                )
                return@run
            }
            if (!jsonObject.has("availableTaskList")) {
                return@run
            }
            val taskList = jsonObject.getJSONArray("availableTaskList")
            for (j in 0 until taskList.length()) {
                val task = taskList.getJSONObject(j)
                processTask(task)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "doAllMemberAvailableTask err:", t)
        }
    }

    /**
     * 芝麻信用任务
     */
    private suspend fun doAllAvailableSesameTask(): Unit = CoroutineUtils.run {
        try {
            val s = AntMemberRpcCall.queryAvailableSesameTask()
            delay(500)
            var jo = JSONObject(s)
            if (jo.has("resData")) {
                jo = jo.getJSONObject("resData")
            }
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error(
                    "$TAG.doAllAvailableSesameTask.queryAvailableSesameTask", "芝麻信用💳[查询任务响应失败]#$s"
                )
                return
            }

            val taskObj = jo.getJSONObject("data")
            var totalTasks = 0
            var completedTasks = 0
            var skippedTasks = 0

            // 处理日常任务
            if (taskObj.has("dailyTaskListVO")) {
                val dailyTaskListVO = taskObj.getJSONObject("dailyTaskListVO")

                if (dailyTaskListVO.has("waitCompleteTaskVOS")) {
                    val waitCompleteTaskVOS = dailyTaskListVO.getJSONArray("waitCompleteTaskVOS")
                    totalTasks += waitCompleteTaskVOS.length()
                    record(
                        TAG, "芝麻信用💳[待完成任务]#开始处理(" + waitCompleteTaskVOS.length() + "个)"
                    )
                    val results: IntArray = joinAndFinishSesameTaskWithResult(waitCompleteTaskVOS)
                    completedTasks += results[0]
                    skippedTasks += results[1]
                }

                if (dailyTaskListVO.has("waitJoinTaskVOS")) {
                    val waitJoinTaskVOS = dailyTaskListVO.getJSONArray("waitJoinTaskVOS")
                    totalTasks += waitJoinTaskVOS.length()
                    record(
                        TAG, "芝麻信用💳[待加入任务]#开始处理(" + waitJoinTaskVOS.length() + "个)"
                    )
                    val results: IntArray = joinAndFinishSesameTaskWithResult(waitJoinTaskVOS)
                    completedTasks += results[0]
                    skippedTasks += results[1]
                }
            }

            // 处理toCompleteVOS任务
            if (taskObj.has("toCompleteVOS")) {
                val toCompleteVOS = taskObj.getJSONArray("toCompleteVOS")
                totalTasks += toCompleteVOS.length()
                record(
                    TAG, "芝麻信用💳[toCompleteVOS任务]#开始处理(" + toCompleteVOS.length() + "个)"
                )
                val results: IntArray = joinAndFinishSesameTaskWithResult(toCompleteVOS)
                completedTasks += results[0]
                skippedTasks += results[1]
            }

            // 统计结果并决定是否关闭开关
            record(
                TAG, "芝麻信用💳[任务处理完成]#总任务:" + totalTasks + "个, 完成:" + completedTasks + "个, 跳过:" + skippedTasks + "个"
            )

            if (totalTasks == 0 || (completedTasks + skippedTasks) >= totalTasks) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK)
                record(TAG, if (totalTasks == 0) "芝麻信用💳[无可做任务，今日跳过]" else "芝麻信用💳[已全部完成任务，今日跳过]")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG + "doAllAvailableSesameTask err", t)
        }
    }

    /**
     * 芝麻粒信用福利签到  与芝麻粒炼金的签到方法都一样 alchemyQueryCheckIn 只不过scenecode不一样
     * 基于 HomeV8RpcManager.queryServiceCard 返回的 serviceCardVOList
     * 通过 itemAttrs.checkInModuleVO.currentDateCheckInTaskVO 判断今日是否可签到
     */
    private fun doSesameZmlCheckIn() {
        try {
            val checkInRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryCheckIn("zml")
            val checkInJo = JSONObject(checkInRes)
            if (!ResChecker.checkRes(TAG, checkInJo)) return
            val data = checkInJo.optJSONObject("data") ?: return
            val currentDay = data.optJSONObject("currentDateCheckInTaskVO") ?: return

            val status = currentDay.optString("status")
            val checkInDate = currentDay.optString("checkInDate")

            if ("CAN_COMPLETE" == status && checkInDate.isNotEmpty()) {
                // 信誉主页签到
                val completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml")
                val completeJo = JSONObject(completeRes)
                if (ResChecker.checkRes(TAG, completeJo)) {
                    val prize = completeJo.optJSONObject("data")
                    val num = if (prize == null) {
                        0
                    } else {
                        val prizeObj = prize.optJSONObject("prize")
                        prize.optInt("zmlNum", prizeObj?.optInt("num", 0) ?: 0)
                    }
                    Log.other("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒")
                } else {
                    Log.error("$TAG.doSesameAlchemy", "炼金签到失败:$completeRes")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.doSesameZmlCheckIn", t)
        } finally {
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_ZML_CHECKIN_DONE)
        }
    }

    private fun doSesameAlchemyNextDayAward() = CoroutineUtils.run {
        try {
            // ===== 调用领取奖励 RPC =====

            val awardRes = AntMemberRpcCall.Zmxy.Alchemy.claimAward()

            val jo = JSONObject(awardRes)

            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error("芝麻炼金⚗️[次日奖励失败]：$awardRes")
                // 即使失败也要设 flag，避免卡死重复调用
                setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
                return@run
            }

            val data = jo.optJSONObject("data")
            var gotNum = 0

            if (data != null) {
                // 解析奖励数组
                val arr = data.optJSONArray("alchemyAwardSendResultVOS")
                if (arr != null && arr.length() > 0) {
                    val item = arr.optJSONObject(0)
                    if (item != null) {
                        gotNum = item.optInt("pointNum", 0)
                    }
                }
            }

            if (gotNum > 0) {
                Log.other("芝麻炼金⚗️[次日奖励领取成功]#获得" + gotNum + "粒")
            } else {
                record("芝麻炼金⚗️[次日奖励无奖励] 已领取或无可领奖励")
            }

            // ★★★★★ 不论有无奖励都标记今日完成 ★★★★★
            setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
        } catch (t: Throwable) {
            Log.printStackTrace("doSesameAlchemyNextDayAward", t)
            // 异常也要标记，否则会无限尝试
            setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
        }
    }

    /**
     * 芝麻粒收取
     * @param withOneClick 启用一键收取
     */
    private suspend fun collectSesame(withOneClick: Boolean): Unit = CoroutineUtils.run {
        try {
            var jo = JSONObject(AntMemberRpcCall.queryCreditFeedback())
            delay(500)
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error(
                    "$TAG.collectSesame.queryCreditFeedback", "芝麻信用💳[查询未领取芝麻粒响应失败]#$jo"
                )
                return
            }
            val availableCollectList = jo.getJSONArray("creditFeedbackVOS")

            var hasUnclaimed = false
            for (i in 0..<availableCollectList.length()) {
                val item = availableCollectList.optJSONObject(i) ?: continue
                if ("UNCLAIMED" == item.optString("status")) {
                    hasUnclaimed = true
                    break
                }
            }

            if (!hasUnclaimed) {
                record(TAG, "芝麻信用💳[当前无待收取芝麻粒]")
                return
            }

            if (withOneClick) {
                delay(2000)
                jo = JSONObject(AntMemberRpcCall.collectAllCreditFeedback())
                delay(2000)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error(
                        "$TAG.collectSesame.collectAllCreditFeedback", "芝麻信用💳[一键收取芝麻粒响应失败]#$jo"
                    )
                    return
                }
            }

            for (i in 0..<availableCollectList.length()) {
                jo = availableCollectList.getJSONObject(i)
                if ("UNCLAIMED" != jo.getString("status")) {
                    continue
                }
                val title = jo.getString("title")
                val creditFeedbackId = jo.getString("creditFeedbackId")
                val potentialSize = jo.getString("potentialSize")
                if (!withOneClick) {
                    jo = JSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId))
                    delay(2000)
                    if (!ResChecker.checkRes(TAG, jo)) {
                        Log.error(
                            "$TAG.collectSesame.collectCreditFeedback", "芝麻信用💳[收取芝麻粒响应失败]#$jo"
                        )
                        continue
                    }
                }
                Log.other("芝麻信用💳[" + title + "]#" + potentialSize + "粒" + (if (withOneClick) "(一键收取)" else ""))
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.collectSesame", t)
        } finally {
            // 今日仅尝试一次，避免重复请求触发风控
            setFlagToday(StatusFlags.FLAG_ANTMEMBER_COLLECT_SESAME_DONE)
        }
    }

    /**
     * 保障金领取
     */
    private suspend fun collectInsuredGold(): Unit = CoroutineUtils.run {
        try {
            var s = AntMemberRpcCall.queryAvailableCollectInsuredGold()
            delay(200)
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
                delay(2000)
                jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.collectInsuredGold.collectInsuredGold", "保障金🏥[响应失败]#$s")
                    return@run
                }
                val gainGold = jo.getJSONObject("data").getString("gainSumInsuredYuan")
                Log.other("保障金🏥[领取保证金]#+" + gainGold + "元")
            }
            for (i in 0..<otherBallList.length()) {
                val anotherBall = otherBallList.getJSONObject(i)
                s = AntMemberRpcCall.collectInsuredGold(anotherBall)
                delay(2000)
                jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.collectInsuredGold.collectInsuredGold", "保障金🏥[响应失败]#$s")
                    return@run
                }
                val gainGold = jo.getJSONObject("data").getJSONObject("gainSumInsuredDTO").getString("gainSumInsuredYuan")
                Log.other("保障金🏥[领取保证金]+" + gainGold + "元")
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
    private suspend fun processTask(task: JSONObject): Unit = CoroutineUtils.run {
        val taskConfigInfo = task.getJSONObject("taskConfigInfo")
        val name = taskConfigInfo.getString("name")
        val id = taskConfigInfo.getLong("id")
        val awardParamPoint = taskConfigInfo.getJSONObject("awardParam").getString("awardParamPoint")
        val targetBusiness = taskConfigInfo.getJSONArray("targetBusiness").getString(0)
        val targetBusinessArray = targetBusiness.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (targetBusinessArray.size < 3) {
            Log.error(TAG, "processTask target param err:" + targetBusinessArray.contentToString())
            return@run
        }
        val bizType = targetBusinessArray[0]
        val bizSubType = targetBusinessArray[1]
        val bizParam = targetBusinessArray[2]
        delay(16000)
        val str = AntMemberRpcCall.executeTask(bizParam, bizSubType, bizType, id)
        val jo = JSONObject(str)
        if (!ResChecker.checkRes(TAG + "执行会员任务失败:", jo)) {
            Log.error(TAG, "执行任务失败:" + jo.optString("resultDesc"))
            return@run
        }
        if (checkMemberTaskFinished(id)) {
            Log.other("会员任务🎖️[$name]#获得积分$awardParamPoint")
        }
    }

    /**
     * 查询指定会员任务是否完成
     * @param taskId 任务id
     */
    private suspend fun checkMemberTaskFinished(taskId: Long): Boolean {
        return try {
            val str = AntMemberRpcCall.queryAllStatusTaskList()
            delay(500)
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

    /**
     * 黄金票任务入口 (整合签到和提取)
     * @param doSignIn 是否执行签到
     * @param doConsume 是否执行提取
     */
    private fun doGoldTicketTask(doSignIn: Boolean, doConsume: Boolean) {
        val needSignIn = doSignIn && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE)
        val needConsume = doConsume && !hasFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_CONSUME_DONE)

        if (!needSignIn && !needConsume) {
            record("黄金票🎫[今日已处理] 跳过执行")
            return
        }

        try {
            record("开始执行黄金票...")

            // 1. 签到：每日一次（避免重复触发风控）
            if (needSignIn) {
                try {
                    // 获取首页数据 (签到需要)
                    val homeRes = AntMemberRpcCall.queryWelfareHome()
                    val homeResult = if (!homeRes.isNullOrEmpty()) {
                        val homeJson = JSONObject(homeRes)
                        if (ResChecker.checkRes(TAG, homeJson)) homeJson.optJSONObject("result") else null
                    } else null

                    if (homeResult != null) {
                        if (doGoldTicketSignIn(homeResult)) {
                            setFlagToday(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE)
                        }
                    }
                } catch (e: Exception) {
                    Log.printStackTrace(TAG, e)
                }
            }

            // 2. 提取：仅在实际提交提取后标记 done（避免重复提交触发风控）
            if (needConsume) {
                doGoldTicketConsume()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 黄金票签到逻辑 (使用新接口 welfareCenterTrigger)
     */
    private fun doGoldTicketSignIn(homeResult: JSONObject): Boolean {
        return try {
            val signObj = homeResult.optJSONObject("sign")
            if (signObj == null) return false

            val todayHasSigned = signObj.optBoolean("todayHasSigned", false)
            if (todayHasSigned) {
                record("黄金票🎫[今日已签到]")
                true
            } else {
                record("黄金票🎫[准备签到]")
                // 调用新接口进行签到
                val signRes = AntMemberRpcCall.welfareCenterTrigger("SIGN")
                val signJson = JSONObject(signRes)

                if (ResChecker.checkRes(TAG, signJson)) {
                    val signResult = signJson.optJSONObject("result")
                    var amount = ""
                    if (signResult != null && signResult.has("prize")) {
                        amount = signResult.getJSONObject("prize").optString("amount")
                    }
                    Log.other("黄金票🎫[签到成功]#获得: $amount")
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            false
        }
    }

    /**
     * 黄金票提取逻辑 (使用新接口 queryConsumeHome 和 submitConsume)
     */
    private fun doGoldTicketConsume() {
        var consumeDone = false
        try {
            record("黄金票🎫[准备检查余额及提取]")

            // 1. 调用新接口 queryConsumeHome 获取最新的资产信息
            val queryRes = AntMemberRpcCall.queryConsumeHome() ?: return
            val queryJson = JSONObject(queryRes)
            if (!ResChecker.checkRes(TAG, queryJson)) return

            val result = queryJson.optJSONObject("result") ?: return

            // 2. 获取余额
            val assetInfo = result.optJSONObject("assetInfo") ?: return

            val availableAmount = assetInfo.optInt("availableAmount", 0)

            // 3. 计算提取数量 (整百提取逻辑)
            val extractAmount = (availableAmount / 100) * 100

            if (extractAmount < 100) {
                record("黄金票🎫[余额不足] 当前: $availableAmount，最低需100")
                return
            }

            // 4. 获取必要参数 productId 和 bonusAmount
            var productId = ""
            val product = result.optJSONObject("product")
            if (product != null) {
                productId = product.optString("productId")
            } else if (result.has("productList") && result.optJSONArray("productList") != null && (result.optJSONArray(
                    "productList"
                )?.length() ?: 0) > 0
            ) {
                productId = result.optJSONArray("productList")?.optJSONObject(0)?.optString("productId") ?: ""
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
            record("黄金票🎫[开始提取] 计划: $extractAmount 份 (持有: $availableAmount)")
            val submitRes = AntMemberRpcCall.submitConsume(extractAmount, productId, bonusAmount)

            if (submitRes != null) {
                val submitJson = JSONObject(submitRes)
                if (ResChecker.checkRes(TAG, submitJson)) {
                    val submitResult = submitJson.optJSONObject("result")
                    val writeOffNo = if (submitResult != null) submitResult.optString("writeOffNo") else ""

                    if (!writeOffNo.isEmpty()) {
                        Log.other("黄金票🎫[提取成功]#消耗: $extractAmount 份")
                        consumeDone = true
                    } else {
                        Log.error("黄金票🎫[提取失败] 未返回核销码")
                    }
                }
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
                        record("$TAG.enableGameCenter.signIn", "游戏中心🎮[今日已签到]")
                    } else {
                        val signResp = AntMemberRpcCall.continueSignIn()
                        delay(300)
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
                                Log.other(sb.toString())
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
                                            record(
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
                                            delay(300)
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
                                        delay(300)
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
                                                Log.other(
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
                                    record(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务处理完成]#待做:$total 完成:$finished 失败:$failed"
                                    )
                                } else {
                                    record(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[无待处理的平台任务]"
                                    )
                                }
                            } else {
                                record("$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务列表为空]")
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
                        record("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
                    } else {
                        val batchResp = AntMemberRpcCall.batchReceivePointBall()
                        delay(300)
                        val batchJo = JSONObject(batchResp)
                        if (ResChecker.checkRes(TAG, batchJo)) {
                            val batchData = batchJo.optJSONObject("data")
                            val receiveAmount = batchData?.optInt("receiveAmount", 0) ?: 0
                            val totalAmount = batchData?.optInt("totalAmount", receiveAmount) ?: receiveAmount
                            if (receiveAmount > 0) {
                                Log.other("游戏中心🎮[一键领取乐豆成功]#本次领取" + receiveAmount + " | 当前累计" + totalAmount + "玩乐豆")
                            } else {
                                record("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
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

    private fun beanSignIn() {
        try {
            try {
                val signInProcessStr = AntMemberRpcCall.querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN")

                var jo = JSONObject(signInProcessStr)
                if (!ResChecker.checkRes(TAG, jo)) {
                    record(jo.toString())
                    return
                }

                if (jo.getJSONObject("result").getBoolean("canPush")) {
                    val signInTriggerStr = AntMemberRpcCall.signInTrigger("AP16242232", "INS_BLUE_BEAN_SIGN")

                    jo = JSONObject(signInTriggerStr)
                    if (ResChecker.checkRes(TAG, jo)) {
                        val prizeName = jo.getJSONObject("result").getJSONArray("prizeSendOrderDTOList").getJSONObject(0).getString("prizeName")
                        record(TAG, "安心豆🫘[$prizeName]")
                    } else {
                        record(jo.toString())
                    }
                }
            } catch (e: NullPointerException) {
                Log.printStackTrace(TAG, "安心豆🫘[RPC桥接失败]#可能是RpcBridge未初始化", e)
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "beanSignIn err:", t)
        }
    }

    private fun beanExchangeBubbleBoost() {
        try {
            // 检查RPC调用是否可用
            try {
                val accountInfo = AntMemberRpcCall.queryUserAccountInfo("INS_BLUE_BEAN")

                var jo = JSONObject(accountInfo)
                if (!ResChecker.checkRes(TAG, jo)) {
                    record(jo.toString())
                    return
                }

                val userCurrentPoint = jo.getJSONObject("result").getInt("userCurrentPoint")

                // 检查beanExchangeDetail调用
                val exchangeDetailStr = AntMemberRpcCall.beanExchangeDetail("IT20230214000700069722")

                jo = JSONObject(exchangeDetailStr)
                if (!ResChecker.checkRes(TAG, jo)) {
                    record(jo.toString())
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
                    record(TAG, "安心豆🫘[兑换:$itemName]")
                } else {
                    record(jo.toString())
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
    private suspend fun doSesameAlchemy(): Unit = CoroutineUtils.run {
        try {
            record(TAG, "开始执行芝麻炼金⚗️")

            // ================= Step 1: 自动炼金 (消耗芝麻粒升级) =================
            val homeRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyQueryHome()
            val homeJo = JSONObject(homeRes)
            if (ResChecker.checkRes(TAG, homeJo)) {
                val data = homeJo.optJSONObject("data")
                if (data != null) {
                    var zmlBalance = data.optInt("zmlBalance", 0) // 当前芝麻粒
                    val cost = data.optInt("alchemyCostZml", 5) // 单次消耗
                    var capReached = data.optBoolean("capReached", false) // 是否达到上限
                    var currentLevel = data.optInt("currentLevel", 0)

                    // 循环炼金逻辑
                    while (zmlBalance >= cost && !capReached) {
                        delay(1500)
                        val alchemyRes = AntMemberRpcCall.Zmxy.Alchemy.alchemyExecute()
                        val alchemyJo = JSONObject(alchemyRes)

                        if (ResChecker.checkRes(TAG, alchemyJo)) {
                            val alData = alchemyJo.optJSONObject("data")
                            if (alData != null) {
                                val levelUp = alData.optBoolean("levelUp", false)
                                val levelFull = alData.optBoolean("levelFull", false)
                                val goldNum = alData.optInt("goldNum", 0)


                                if (levelUp) currentLevel++
                                if (levelFull) capReached = true

                                Log.other(
                                    ("芝麻炼金⚗️[炼金成功]" + "#消耗" + cost + "粒" + " | 获得" + goldNum + "金" + " | 当前等级Lv." + currentLevel + (if (levelUp) "（升级🎉）" else "") + (if (levelFull) "（满级🏆）" else ""))
                                )
                                zmlBalance -= cost
                            } else {
                                break
                            }
                        } else {
                            Log.error(TAG, "芝麻炼金失败: " + alchemyJo.optString("resultView"))
                            break
                        }
                    }
                }
            } else {
                Log.error(TAG, "芝麻炼金首页查询失败")
            }

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
                                    Log.other("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒")
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
            record(TAG, "芝麻炼金⚗️[检查时段奖励]")

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
                record(TAG, "芝麻炼金⚗️[当前没有时段奖励任务]")
                return
            }

            // 2. 获取任务信息
            val taskName = timeLimitedTaskVO.optString("longTitle", "未知任务")
            val templateId = timeLimitedTaskVO.getString("templateId") // 动态获取
            val state = timeLimitedTaskVO.optInt("state", 0) // 1: 可领取, 2: 未到时间
            val tomorrow = timeLimitedTaskVO.optBoolean("tomorrow", false)
            val rewardAmount = timeLimitedTaskVO.optInt("rewardAmount", 0)

            record(
                TAG, "芝麻炼金⚗️[任务检查] 任务=$taskName 状态=$state 奖励=$rewardAmount 明天=$tomorrow"
            )

            // 3. 如果是明天任务，跳过
            if (tomorrow) {
                record(TAG, "芝麻炼金⚗️[任务跳过] 任务=$taskName 是明天的奖励")
                return
            }

            // 4. 如果状态是可领取，则领取奖励
            if (state == 1) { // 可领取
                record(TAG, "芝麻炼金⚗️[开始领取任务奖励] 任务=$taskName")

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
                    record(TAG, "芝麻炼金⚗️[领取成功] 获得芝麻粒=$zmlNum 提示=$toast")
                }
            } else { // 其他状态
                record(TAG, "芝麻炼金⚗️[当前不可领取] 任务=$taskName")
            }


            // ================= Step 3: 自动做任务 =================
            record(TAG, "芝麻炼金⚗️[开始扫描任务列表]")
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
            record(TAG, "芝麻炼金⚗️[任务处理完毕，准备收取芝麻粒]")
            delay(2000) // 稍作等待，确保任务奖励到账

            // 4.1 查询是否有可收取的芝麻粒
            val queryFeedbackRes = AntMemberRpcCall.queryCreditFeedback()
            val feedbackJo = JSONObject(queryFeedbackRes)
            if (ResChecker.checkRes(TAG, feedbackJo)) {
                val feedbackList = feedbackJo.optJSONArray("creditFeedbackVOS")
                var unclaimedCount = 0
                if (feedbackList != null && feedbackList.length() > 0) {
                    for (i in 0 until feedbackList.length()) {
                        val item = feedbackList.optJSONObject(i) ?: continue
                        if ("UNCLAIMED" == item.optString("status")) {
                            unclaimedCount += 1
                        }
                    }
                }

                if (unclaimedCount > 0) {
                    record(
                        TAG, "芝麻炼金⚗️[发现" + unclaimedCount + "个待收取项，执行一键收取]"
                    )

                    // 4.2 执行一键收取
                    val collectRes = AntMemberRpcCall.collectAllCreditFeedback()
                    val collectJo = JSONObject(collectRes)
                    if (ResChecker.checkRes(TAG, collectJo)) {
                        Log.other("芝麻炼金⚗️[一键收取成功]#收割完毕")
                    } else {
                        record(TAG, "芝麻炼金⚗️[一键收取失败]#" + collectJo.optString("resultView"))
                    }
                } else {
                    record(TAG, "芝麻炼金⚗️[当前无待收取芝麻粒]")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace("$TAG.doSesameAlchemy", t)
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
            if (isTaskInBlacklist(title)) {
                // 只有在所有任务组中未处理过时才记录日志
                if (!processedBlacklistTasks.contains(title)) {
                    record(TAG, "跳过黑名单任务: $title")
                    processedBlacklistTasks.add(title)
                }
                continue
            }

            // 特殊处理：广告浏览任务（逛15秒商品橱窗 / 浏览15秒视频广告 等）
            // 这类任务没有有效 templateId，需要用 logExtMap.bizId 走 com.alipay.adtask.biz.mobilegw.service.task.finish
            if ("AD_TASK" == bizType) {
                val logExtMap = task.optJSONObject("logExtMap")
                if (logExtMap == null) {
                    record(TAG, "芝麻炼金广告任务缺少logExtMap, 跳过: $title")
                    continue
                }
                val bizId = logExtMap.optString("bizId", "")
                if (bizId.isEmpty()) {
                    record(TAG, "芝麻炼金广告任务缺少bizId, 跳过: $title")
                    continue
                }

                record(TAG, "芝麻炼金广告任务: $title 准备执行") //(bizId=" + bizId + ")

                var sleepTime = 8000
                if (title.contains("15秒") || title.contains("15s")) {
                    // 抓包规则里写明“每次浏览不少于15秒”
                    sleepTime = 10000
                }
                delay(sleepTime.toLong())

                try {
                    val adFinishRes = AntMemberRpcCall.taskFinish(bizId)
                    val adFinishJo = JSONObject(adFinishRes)
                    // 兼容返回中只有 errCode=0 的情况
                    if (ResChecker.checkRes(
                            TAG, adFinishJo
                        ) || "0" == adFinishJo.optString("errCode")
                    ) {
                        val reward = task.optInt("rewardAmount", 0)
                        Log.other("芝麻炼金⚗️[广告任务完成: " + title + "]#获得" + reward + "粒")
                    } else {
                        Log.error(TAG, "芝麻炼金广告任务上报失败: $title - $adFinishRes")
                    }
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

            record(TAG, "芝麻炼金任务: $title 准备执行")

            var recordId = task.optString("recordId", "")

            if (recordId.isEmpty()) {
                // templateId 为空或无效时，直接跳过，避免 "参数[templateId]不是有效的入参"
                if (templateId == null || templateId.trim { it <= ' ' }.isEmpty()) {
                    record(TAG, "芝麻炼金任务: 模板为空，跳过 $title")
                    continue
                }
                val joinRes = AntMemberRpcCall.joinSesameTask(templateId)
                val joinJo = JSONObject(joinRes)
                if (ResChecker.checkRes(TAG, joinJo)) {
                    val joinData = joinJo.optJSONObject("data")
                    if (joinData != null) {
                        recordId = joinData.optString("recordId")
                    }
                    record(TAG, "任务领取成功: $title")
                    delay(1000)
                } else {
                    Log.error(
                        TAG, "任务领取失败: " + title + " - " + joinJo.optString("resultView", joinRes)
                    )
                    continue
                }
            }

            AntMemberRpcCall.feedBackSesameTask(templateId)

            var sleepTime = 3000
            if (title.contains("浏览") || title.contains("逛")) {
                sleepTime = 15000
            }
            delay(sleepTime.toLong())

            if (!recordId.isEmpty()) {
                val finishRes = AntMemberRpcCall.finishSesameTask(recordId)
                val finishJo = JSONObject(finishRes)
                if (ResChecker.checkRes(TAG, finishJo)) {
                    val reward = task.optInt("rewardAmount", 0)
                    Log.other("芝麻炼金⚗️[任务完成: " + title + "]#获得" + reward + "粒")
                } else {
                    val errorCode = finishJo.optString("resultCode", "")
                    //  val errorMsg = finishJo.optString("resultView", finishRes)
                    //  Log.error(TAG, "任务提交失败: $title - $errorMsg")
                    // 自动添加到黑名单
                    if (!errorCode.isEmpty()) {
                        autoAddToBlacklist(title, title, errorCode)
                    }
                }
            }
            delay(2000)
        }
    }

    private suspend fun doZhimaTree(): Unit = CoroutineUtils.run {
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
            val sendCampTriggerType = task.optString("sendCampTriggerType")
            if ("EVENT_TRIGGER" == sendCampTriggerType) {
                // 不处理事件触发类型的任务
                return
            }

            val taskBaseInfo = task.optJSONObject("taskBaseInfo") ?: return

            var taskId = taskBaseInfo.optString("appletId")
            // 有些任务ID在taskId字段，有些在appletId，做个兼容
            if (taskId == null || taskId.isEmpty()) {
                taskId = task.optString("taskId")
            }

            var title = taskBaseInfo.optString("appletName")
            if (title.isEmpty()) title = taskBaseInfo.optString("title", taskId)

            val status = task.optString("taskProcessStatus")

            // 过滤掉明显无法自动完成的任务（如包含邀请、下单、开通），但保留复访任务
            if (title.contains("邀请") || title.contains("下单") || title.contains("开通")) {
                return
            }

            // 解析奖励信息.
            val prizeName = getPrizeName(task)

            if ("NOT_DONE" == status || "SIGNUP_COMPLETE" == status) {
                // SIGNUP_COMPLETE 通常表示已报名但未做，或者对于复访任务表示可以去完成
                record("芝麻树🌳[开始任务] " + title + (if (prizeName.isEmpty()) "" else " ($prizeName)"))
                performTask(taskId, title, prizeName)
                // 任务完成
            } else if ("TO_RECEIVE" == status) {
                // 待领取状态
                if (doTaskAction(taskId, "receive")) {
                    val logMsg = "芝麻树🌳[领取奖励] " + title + " #" + (prizeName.ifEmpty { "奖励已领取" })
                    Log.forest(logMsg) // 输出到 forest
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * 执行任务动作：去完成 -> 等待 -> 领取
     */
    private suspend fun performTask(taskId: String?, title: String, prizeName: String): Boolean {
        return try {
            // 发送"去完成"指令
            if (doTaskAction(taskId, "send")) {
                val waitTime = 16000L // 默认等待16秒，覆盖大多数浏览任务

                delay(waitTime)

                // 发送"领取"指令
                if (doTaskAction(taskId, "receive")) {
                    val logMsg = "芝麻树🌳[完成任务] " + title + " #" + (prizeName.ifEmpty { "奖励已领取" })
                    Log.forest(logMsg) // 这里输出到 forest
                    return true
                }
            }
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
        try {
            val safeTaskId = taskId?.takeIf { it.isNotBlank() } ?: return false
            val safeStageCode = stageCode?.takeIf { it.isNotBlank() } ?: return false
            val s = AntMemberRpcCall.rentGreenTaskFinish(safeTaskId, safeStageCode) ?: return false
            val json = JSONObject(s)
            return ResChecker.checkRes(TAG, json)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
            return false
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
                record("芝麻树🌳[无需净化] 净化值不足（当前: " + score + "g，可点击: " + clicks + "次）")
                return@run
            }

            record("芝麻树🌳[开始净化] 可点击 $clicks 次")

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
                Log.forest("$log ✅")

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
                record(TAG, "今日已兑换贴纸，跳过")
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
                record(TAG, "贴纸扫描：暂无可领取的贴纸")
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

            record(TAG, "贴纸领取成功，总数：${obtainedIds?.length() ?: 0}")

            if (specialList != null && specialList.length() > 0) {
                for (i in 0 until specialList.length()) {
                    val special = specialList.optJSONObject(i) ?: continue

                    // 获取领取结果中的 recordId
                    val recordId = special.optString("stickerRecordId")
                    // 从我们之前的 Map 中根据 ID 找到对应的 Name
                    val stickerName = stickerNameMap[recordId] ?: "特殊贴纸"

                    val ranking = special.optString("rankingText")

                    // 仅对特殊贴纸进行 other 输出，显示真实的贴纸名称
                    Log.other(TAG, "获得特殊贴纸 → $stickerName ($ranking)")
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

        /**
         * 查询 + 自动领取可领取球（精简一行输出领取信息）
         */
        @SuppressLint("DefaultLocale")
        fun queryAndCollect() {
            try {
                // 1. 查询进度球状态
                val queryResp = AntMemberRpcCall.Zmxy.queryScoreProgress()
                if (queryResp.isEmpty()) return

                val json = JSONObject(queryResp)

                // 检查 success
                if (!ResChecker.checkRes(TAG, json)) return

                val totalWait = json.optJSONObject("totalWaitProcessVO") ?: return

                val idList = totalWait.optJSONArray("totalProgressIdList")
                if (idList == null || idList.length() == 0) return

                // 直接传 JSONArray
                val collectResp = AntMemberRpcCall.Zmxy.collectProgressBall(idList) ?: return

                val collectJson = JSONObject(collectResp)

                Log.other(
                    TAG, String.format(
                        "领取完成 → 本次加速进度: %d, 当前加速倍率: %.2f",
                        collectJson.optInt("collectedAccelerateProgress", -1),
                        collectJson.optDouble("currentAccelerateValue", -1.0)
                    )
                )
            } catch (e: Exception) {
                Log.printStackTrace(TAG + "queryAndCollect err", e)
            }
        }

        /**
         * 会员积分收取
         * @param page 第几页
         * @param pageSize 每页数据条数
         */
        private suspend fun queryPointCert(page: Int, pageSize: Int) {
            try {
                var s = AntMemberRpcCall.queryPointCert(page, pageSize)
                delay(500)
                var jo = JSONObject(s)
                if (ResChecker.checkRes(TAG + "查询会员积分证书失败:", jo)) {
                    val hasNextPage = jo.getBoolean("hasNextPage")
                    val jaCertList = jo.getJSONArray("certList")
                    for (i in 0..<jaCertList.length()) {
                        jo = jaCertList.getJSONObject(i)
                        val bizTitle = jo.getString("bizTitle")
                        val id = jo.getString("id")
                        val pointAmount = jo.getInt("pointAmount")
                        s = AntMemberRpcCall.receivePointByUser(id)
                        jo = JSONObject(s)
                        if (ResChecker.checkRes(TAG + "会员积分领取失败:", jo)) {
                            Log.other("会员积分🎖️[领取" + bizTitle + "]#" + pointAmount + "积分")
                        } else {
                            record(jo.getString("resultDesc"))
                            record(s)
                        }
                    }
                    if (hasNextPage) {
                        queryPointCert(page + 1, pageSize)
                    }
                } else {
                    record(jo.getString("resultDesc"))
                    record(s)
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "queryPointCert err:", t)
            }
        }

        /**
         * 检查是否满足运行芝麻信用任务的条件
         * @return bool
         */
        private fun checkSesameCanRun(): Boolean {
            try {
                val s = AntMemberRpcCall.queryHome()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    Log.error("$TAG.checkSesameCanRun.queryHome", "芝麻信用💳[首页响应失败]#$s")
                    return false
                }
                val entrance = jo.getJSONObject("entrance")
                if (!entrance.optBoolean("openApp")) {
                    Log.other("芝麻信用💳[未开通芝麻信用]")
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
        private fun isTaskInBlacklist(taskTitle: String?): Boolean {
            return TaskBlacklist.isTaskInBlacklist(taskTitle)
        }

        /**
         * 芝麻信用-领取并完成任务（带结果统计）
         * @param taskList 任务列表
         * @return int数组 [完成数量, 跳过数量]
         * @throws JSONException JSON解析异常，上抛处理
         */
        @Throws(JSONException::class)
        private suspend fun joinAndFinishSesameTaskWithResult(taskList: JSONArray): IntArray {
            var completedCount = 0
            var skippedCount = 0
            var joinLimitReached = hasFlagToday(StatusFlags.FLAG_ANTMEMBER_SESAME_JOIN_LIMIT_REACHED)
            var joinLimitLogged = false

            for (i in 0..<taskList.length()) {
                val task = taskList.getJSONObject(i)
                val taskTitle = if (task.has("title")) task.getString("title") else "未知任务"

                // 打印任务状态信息用于调试
                val finishFlag = task.optBoolean("finishFlag", false)
                val actionText = task.optString("actionText", "")

                //   record(TAG, "芝麻信用💳[任务状态调试]#" + taskTitle + " - finishFlag:" + finishFlag + ", actionText:" + actionText);

                // 检查任务是否已完成
                if (finishFlag || "已完成" == actionText) {
                    record(TAG, "芝麻信用💳[跳过已完成任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                // 检查黑名单
                if (isTaskInBlacklist(taskTitle)) {
                    record(TAG, "芝麻信用💳[跳过黑名单任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                // 添加检查，确保templateId存在
                if (!task.has("templateId")) {
                    record(TAG, "芝麻信用💳[跳过缺少templateId任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                val taskTemplateId = task.getString("templateId")
                val needCompleteNum = if (task.has("needCompleteNum")) task.getInt("needCompleteNum") else 1
                val completedNum = task.optInt("completedNum", 0)
                var s: String?
                val recordId: String?
                var responseObj: JSONObject?


                if (task.has("actionUrl") && task.getString("actionUrl").contains("jumpAction")) {
                    // 跳转APP任务 依赖跳转的APP发送请求鉴别任务完成 仅靠hook目标应用无法完成
                    record(TAG, "芝麻信用💳[跳过跳转APP任务]#$taskTitle")
                    skippedCount++
                    continue
                }

                var taskCompleted = false
                if (!task.has("todayFinish")) {
                    if (joinLimitReached) {
                        // 当天加入任务次数已达上限：避免继续请求触发风控
                        if (!joinLimitLogged) {
                            record(TAG, "芝麻信用💳[领取任务已达当日上限] 今日不再领取新任务")
                            joinLimitLogged = true
                        }
                        skippedCount++
                        continue
                    }
                    // 领取任务
                    s = AntMemberRpcCall.joinSesameTask(taskTemplateId)
                    delay(200)
                    responseObj = JSONObject(s)
                    val joinResultCode = responseObj.optString("resultCode", responseObj.optString("errorCode", ""))
                    if ("PROMISE_TODAY_FINISH_TIMES_LIMIT" == joinResultCode) {
                        // “当天完成任务次数超过限制，请隔天再加入”
                        joinLimitReached = true
                        setFlagToday(StatusFlags.FLAG_ANTMEMBER_SESAME_JOIN_LIMIT_REACHED)
                        record(TAG, "芝麻信用💳[领取任务已达当日上限] 今日不再领取新任务")
                        joinLimitLogged = true
                        skippedCount++
                        continue
                    }
                     if (!ResChecker.checkRes(TAG, responseObj)) {
                          Log.error(TAG, "芝麻信用💳[领取任务" + taskTitle + "失败]#" + s)
                          // 自动添加到黑名单
                          val errorCode = responseObj.optString("errorCode", responseObj.optString("resultCode", ""))
                          if (!errorCode.isEmpty()) {
                             autoAddToBlacklist(taskTitle, taskTitle, errorCode)
                         }
                         skippedCount++
                         continue
                    }
                    recordId = responseObj.getJSONObject("data").getString("recordId")
                } else {
                    if (!task.has("recordId")) {
                        Log.error(TAG, "芝麻信用💳[任务" + taskTitle + "未获取到recordId]#" + task)
                        skippedCount++
                        continue
                    }
                    recordId = task.getString("recordId")
                }

                // 完成任务
                for (j in completedNum..<needCompleteNum) {
                    s = AntMemberRpcCall.finishSesameTask(recordId)
                    delay(200)
                    responseObj = JSONObject(s)
                    if (ResChecker.checkRes(TAG, responseObj)) {
                        record(
                            TAG, "芝麻信用💳[完成任务" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)"
                        )
                        taskCompleted = true
                     } else {
                         Log.error(TAG, "芝麻信用💳[完成任务" + taskTitle + "失败]#" + s)
                         // 自动添加到黑名单
                         val errorCode = responseObj.optString("errorCode", responseObj.optString("resultCode", ""))
                         if (!errorCode.isEmpty()) {
                             autoAddToBlacklist(taskTitle, taskTitle, errorCode)
                         }
                         break
                     }
                 }

                if (taskCompleted) {
                    completedCount++
                } else {
                    skippedCount++
                }
            }

            return intArrayOf(completedCount, skippedCount)
        }

        /**
         * 商家开门打卡签到
         */
        private fun kmdkSignIn(): Boolean = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.queryActivity()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    record(TAG, "queryActivity $s")
                    return@run false
                }

                when (jo.optString("signInStatus")) {
                    "SIGN_IN_ENABLE" -> {
                        val activityNo = jo.optString("activityNo")
                        if (activityNo.isEmpty()) return@run false
                        val joSignIn = JSONObject(AntMemberRpcCall.signIn(activityNo))
                        if (ResChecker.checkRes(TAG, joSignIn)) {
                            Log.other("商家服务🏬[开门打卡签到成功]")
                            return@run true
                        }
                        record(TAG, joSignIn.optString("errorMsg"))
                        record(TAG, joSignIn.toString())
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
                                Log.other("商家服务🏬[" + activityPeriodName + "开门打卡报名]")
                                return@run true
                            } else {
                                record(TAG, joSignUp.getString("errorMsg"))
                                record(TAG, joSignUp.toString())
                            }
                        }
                    } else {
                        record(TAG, "queryActivity")
                        record(TAG, jo.toString())
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
            try {
                val s = AntMemberRpcCall.merchantSign()
                var jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG, jo)) {
                    record(TAG, "doMerchantSign err:$s")
                    return@run false
                }
                jo = jo.getJSONObject("data")
                val signResult = jo.optString("signInResult")
                val reward = jo.optString("todayReward")
                if ("SUCCESS" == signResult) {
                    Log.other("商家服务🏬[每日签到]#获得积分$reward")
                    return@run true
                } else {
                    // 对于「已签到 / 不可签到」等情况，直接视为今日已处理，避免反复请求触发风控
                    record(TAG, "商家服务🏬[每日签到]#未返回SUCCESS(signInResult=$signResult,todayReward=$reward)")
                    record(TAG, s)
                    return@run true
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "kmdkSignIn err:", t)
            }
            false
        }

        /**
         * 商家积分任务
         */
        private suspend fun doMerchantMoreTask(): Unit = CoroutineUtils.run {
            val s = AntMemberRpcCall.taskListQuery()
            try {
                var doubleCheck = false
                var jo = JSONObject(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    val taskList = jo.getJSONObject("data").getJSONArray("taskList")
                    for (i in 0..<taskList.length()) {
                        val task = taskList.getJSONObject(i)
                        if (!task.has("status")) {
                            continue
                        }
                        val title = task.getString("title")
                        val reward = task.getString("reward")
                        val taskStatus = task.getString("status")
                        if ("NEED_RECEIVE" == taskStatus) {
                            if (task.has("pointBallId")) {
                                jo = JSONObject(AntMemberRpcCall.ballReceive(task.getString("pointBallId")))
                                if (ResChecker.checkRes(TAG, jo)) {
                                    Log.other("商家服务🏬[$title]#领取积分$reward")
                                }
                            }
                        } else if ("PROCESSING" == taskStatus || "UNRECEIVED" == taskStatus) {
                            if (task.has("extendLog")) {
                                val bizExtMap = task.getJSONObject("extendLog").getJSONObject("bizExtMap")
                                jo = JSONObject(AntMemberRpcCall.taskFinish(bizExtMap.getString("bizId")))
                                if (ResChecker.checkRes(TAG, jo)) {
                                    Log.other("商家服务🏬[$title]#领取积分$reward")
                                }
                                doubleCheck = true
                            } else {
                                when (val taskCode = task.getString("taskCode")) {
                                    "SYH_CPC_DYNAMIC" ->                   // 逛一逛商品橱窗
                                        taskReceive(taskCode, "SYH_CPC_DYNAMIC_VIEWED", title)

                                    "JFLLRW_TASK" ->                   // 逛一逛得缴费红包
                                        taskReceive(taskCode, "JFLL_VIEWED", title)

                                    "ZFBHYLLRW_TASK" ->                   // 逛一逛目标应用会员
                                        taskReceive(taskCode, "ZFBHYLL_VIEWED", title)

                                    "QQKLLRW_TASK" ->                   // 逛一逛目标应用亲情卡
                                        taskReceive(taskCode, "QQKLL_VIEWED", title)

                                    "SSLLRW_TASK" ->                   // 逛逛领优惠得红包
                                        taskReceive(taskCode, "SSLL_VIEWED", title)

                                    "ELMGYLLRW2_TASK" ->                   // 去饿了么果园0元领水果
                                        taskReceive(taskCode, "ELMGYLL_VIEWED", title)

                                    "ZMXYLLRW_TASK" ->                   // 去逛逛芝麻攒粒攻略
                                        taskReceive(taskCode, "ZMXYLL_VIEWED", title)

                                    "GXYKPDDYH_TASK" ->                   // 逛信用卡频道得优惠
                                        taskReceive(taskCode, "xykhkzd_VIEWED", title)

                                    "HHKLLRW_TASK" ->                   // 49999元花呗红包集卡抽
                                        taskReceive(taskCode, "HHKLLX_VIEWED", title)

                                    "TBNCLLRW_TASK" ->                   // 去淘宝芭芭农场领水果百货
                                        taskReceive(taskCode, "TBNCLLRW_TASK_VIEWED", title)
                                }
                            }
                        }
                    }
                    if (doubleCheck) {
                        doMerchantMoreTask()
                    }
                } else {
                    record(TAG, "taskListQuery err: $s")
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
         * @param actionCode 行为代码
         * @param title 标题
         */
        private suspend fun taskReceive(
            taskCode: String,
            actionCode: String,
            title: String
        ): Unit = CoroutineUtils.run {
            try {
                val s = AntMemberRpcCall.taskReceive(taskCode)
                var jo = JSONObject(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    delay(500)
                    jo = JSONObject(AntMemberRpcCall.actioncode(actionCode))
                    if (ResChecker.checkRes(TAG, jo)) {
                        delay(16000)
                        jo = JSONObject(AntMemberRpcCall.produce(actionCode))
                        if (ResChecker.checkRes(TAG, jo)) {
                            Log.other("商家服务🏬[完成任务$title]")
                        }
                    }
                } else {
                    record(TAG, "taskReceive $s")
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "taskReceive err:", t)
            }
        }
    }

    /**
     * 【新增】芝麻粒兑换道具
     * 仿照会员积分兑换逻辑：遍历列表更新Map，同时匹配用户设置进行兑换
     */
    private suspend fun doSesameGrainExchange(): Unit = CoroutineUtils.run {
        // 每日只运行一次，避免重复请求
        if (hasFlagToday("sesameGrainExchange::done")) {
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
                        record(TAG, "跳过[$name]: 库存不足")
                        continue
                    }
                    // 4. 执行兑换 (这里不加每日限制判断了，只要有库存且勾选了就尝试兑换)
                    record(TAG, "准备兑换[$name], ID: $id, 需芝麻粒: $pointNeeded")
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
            record(TAG, "芝麻粒兑换任务处理完毕，商品列表已更新")
            // 标记今日已完成
            setFlagToday("sesameGrainExchange::done")

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
                Log.other("芝麻粒兑换🛒[成功] $name #消耗${point}粒")
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

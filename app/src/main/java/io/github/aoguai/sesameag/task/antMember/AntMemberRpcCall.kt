package io.github.aoguai.sesameag.task.antMember

import io.github.aoguai.sesameag.hook.RequestManager
import io.github.aoguai.sesameag.util.RandomUtil
import io.github.aoguai.sesameag.util.TimeUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

object AntMemberRpcCall {

    private const val SESAME_CHECK_IN_VERSION = "2025-10-22"
    private const val SESAME_GROWTH_GUIDE_INVOKE_VERSION = "1.0.2025.10.27"
    private const val SESAME_TASK_VERSION = "new"
    private const val SESAME_TASK_SCENE_CODE = "DAILY_MUST_DO_CARD"
    private const val SESAME_TASK_SCENE_ZML = "zml"
    private const val SESAME_TASK_CH_INFO = "ch_zmxy_zmlsy__chsub_zmsy_jingangwei"
    private const val SESAME_TASK_JOIN_CH_INFO = "seasameList"
    private const val GAME_CENTER_SOURCE = "ch_appcollect__chsub_my-recentlyUsed"
    
    private fun getUniqueId(): String {
        return System.currentTimeMillis().toString() + RandomUtil.nextLong()
    }

    private fun buildMemberSourcePassMap(): JSONObject {
        return JSONObject().apply {
            put("innerSource", "")
            put("source", "mytab")
            put("unid", "")
        }
    }

    /* ant member point */
    @JvmStatic
    fun queryPointCert(page: Int, pageSize: Int): String {
        val args1 = """[{"page":$page,"pageSize":$pageSize}]"""
        return RequestManager.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", args1)
    }

    @JvmStatic
    fun receivePointByUser(certId: String): String {
        val args1 = """[{"certId":$certId}]"""
        return RequestManager.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", args1)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun receiveAllPointByUser(): String {
        val args = JSONObject().apply {
            put("bizSource", "mytab")
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        val params = "[$args]"
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.pointcert.h5.receiveAllPointByUser", params)
    }

    @JvmStatic
    fun queryPointCertV2(page: Int, pageSize: Int): String {
        val args = JSONObject().apply {
            put("abTestInfo", JSONArray())
            put("dbExpireDt", 0)
            put("dbId", 0)
            put("pageNum", page)
            put("pageSize", pageSize)
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.alipaymember.biz.rpc.pointcert.h5.queryPointCertV2",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryMemberSigninCalendar(): String {
        val args = JSONObject().apply {
            put("autoSignIn", true)
            put("chInfo", "memberHomePage_ch_mytab")
            put("invitorUserId", "")
            put("sceneCode", "QUERY")
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryReSignInCardInfo(): String {
        val args = JSONObject().apply {
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.amic.biz.rpc.signin.h5.queryReSignInCardInfo",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun querySimpleIndex(): String {
        val args = JSONObject().apply {
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.alipaymember.biz.rpc.member.h5.querySimpleIndex",
            JSONArray().put(args).toString()
        )
    }

    /* 商家开门打卡任务 */
    @JvmStatic
    fun signIn(activityNo: String): String {
        return RequestManager.requestString(
            "alipay.merchant.kmdk.signIn",
            """[{"activityNo":"$activityNo"}]"""
        )
    }

    @JvmStatic
    fun signUp(activityNo: String): String {
        return RequestManager.requestString(
            "alipay.merchant.kmdk.signUp",
            """[{"activityNo":"$activityNo"}]"""
        )
    }

    /* 商家服务 */
    @JvmStatic
    fun transcodeCheck(): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.mrchbusiness.sign.transcode.check",
            "[{}]"
        )
    }

    @JvmStatic
    fun merchantSign(): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.mrchpoint.sqyj.homepage.signin.v1",
            "[{}]"
        )
    }

    @JvmStatic
    fun zcjSignInQuery(): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.zcj.view.invoke",
            """[{"compId":"ZCJ_SIGN_IN_QUERY"}]"""
        )
    }

    @JvmStatic
    fun zcjSignInExecute(): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.zcj.view.invoke",
            """[{"compId":"ZCJ_SIGN_IN_EXECUTE"}]"""
        )
    }

    @JvmStatic
    fun taskListQuery(): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.task.more.query",
            """[{"paramMap":{"platform":"Android"},"taskItemCode":""}]"""
        )
    }

    @JvmStatic
    fun queryActivity(): String {
        return RequestManager.requestString(
            "alipay.merchant.kmdk.query.activity",
            """[{"scene":"activityCenter"}]"""
        )
    }

    /* 商家服务任务 */
    @JvmStatic
    fun taskFinish(bizId: String, includeExtendInfo: Boolean = false): String {
        val args = JSONObject().apply {
            put("bizId", bizId)
            if (includeExtendInfo) {
                put("extendInfo", JSONObject())
            }
        }
        return RequestManager.requestString(
            "com.alipay.adtask.biz.mobilegw.service.task.finish",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun adTaskApplayerQuery(spaceCode: String): String {
        val args = JSONObject().apply {
            put("spaceCode", spaceCode)
        }
        return RequestManager.requestString(
            "com.alipay.adtask.biz.mobilegw.service.applayer.query",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun taskReceive(taskCode: String): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.sqyj.task.receive",
            """[{"compId":"ZTS_TASK_RECEIVE","extInfo":{"taskCode":"$taskCode"}}]"""
        )
    }

    @JvmStatic
    fun actioncode(actionCode: String): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.task.query.by.actioncode",
            """[{"actionCode":"$actionCode"}]"""
        )
    }

    @JvmStatic
    fun produce(actionCode: String): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.biz.task.action.produce",
            """[{"actionCode":"$actionCode"}]"""
        )
    }

    @JvmStatic
    fun merchantBallQuery(): String {
        val args = JSONObject().apply {
            put(
                "context",
                JSONObject().apply {
                    put("autoCheckIn", "true")
                    put("isGuide", "true")
                    put("underTakeTrace", "NULL")
                    put("userPath", "undertakeVisit")
                }
            )
        }
        return RequestManager.requestString(
            "alipay.mrchservbase.mrchpoint.ball.query.v1",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun ballReceive(ballIds: String): String {
        return RequestManager.requestString(
            "alipay.mrchservbase.mrchpoint.ball.receive",
            """[{"ballIds":["$ballIds"],"channel":"MRCH_SELF","outBizNo":"${getUniqueId()}"}]"""
        )
    }

    @JvmStatic
    fun applyTask(darwinName: String, taskConfigId: Long): String {
        return RequestManager.requestString(
            "alipay.antmember.biz.rpc.membertask.h5.applyTask",
            """[{"darwinExpParams":{"darwinName":"$darwinName"},"sourcePassMap":{"innerSource":"","source":"mytab","unid":""},"taskConfigId":$taskConfigId}]"""
        )
    }

    @JvmStatic
    fun executeTask(bizParam: String, bizSubType: String, bizType: String, taskConfigId: Long): String {
        val bizOutNo = TimeUtil.getFormatDate().replace("-", "")
        return RequestManager.requestString(
            "alipay.antmember.biz.rpc.membertask.h5.executeTask",
            """[{"bizOutNo":"$bizOutNo","bizParam":"$bizParam","bizSubType":"$bizSubType","bizType":"$bizType","sourcePassMap":{"innerSource":"","source":"mytab","unid":""},"syncProcess":true,"taskConfigId":"$taskConfigId"}]"""
        )
    }

    @JvmStatic
    fun queryLegacyAllStatusTaskList(): String {
        return RequestManager.requestString(
            "alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList",
            """[{"sourceBusiness":"signInAd","sourcePassMap":{"innerSource":"","source":"mytab","unid":""}}]"""
        )
    }

    @JvmStatic
    fun queryMemberTaskList(): String {
        val args = JSONObject().apply {
            put("source", "signInAd")
        }
        return RequestManager.requestString(
            "com.alipay.amic.memtask.h5.MemTaskListQueryFacade.queryAllStatusTaskList",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun querySignFloatingBall(): String {
        val args = JSONObject().apply {
            put("extMap", JSONObject())
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.amic.biz.rpc.signin.h5.querySignFloatingBall",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun triggerSignFloatingBall(bizNo: String, taskType: String): String {
        val args = JSONObject().apply {
            put("bizNo", bizNo)
            put("extMap", JSONObject())
            put("sourcePassMap", buildMemberSourcePassMap())
            put("taskType", taskType)
        }
        return RequestManager.requestString(
            "com.alipay.amic.biz.rpc.signin.h5.triggerSignFloatingBall",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun querySignFloatingBallAdTask(bizNo: String, adType: String = "AD_VIDEO_TASK"): String {
        val args = JSONObject().apply {
            put("adType", adType)
            put("bizNo", bizNo)
            put("extMap", JSONObject())
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.amic.biz.rpc.signin.h5.querySignFloatingBallAdTask",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryMemberTaskProcessList(): String {
        val args = JSONObject().apply {
            put("relatedChannel", "MEMBERPOINT")
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.alipaymember.biz.rpc.membertask.h5.queryTaskList",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun executeMemberTask(bizParam: String, bizSubType: String, bizType: String): String {
        val args = JSONObject().apply {
            put("bizParam", bizParam)
            put("bizSubType", bizSubType)
            put("bizType", bizType)
            put("outBizNo", TimeUtil.getFormatDate().replace("-", ""))
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.amic.memtask.h5.MemTaskManagerFacade.executeTask",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun querySingleTaskProcessDetail(taskProcessId: String): String {
        val args = JSONObject().apply {
            put("sourcePassMap", buildMemberSourcePassMap())
            put("taskProcessId", taskProcessId)
        }
        return RequestManager.requestString(
            "com.alipay.amic.memtask.h5.MemTaskListQueryFacade.querySingleTaskProcessDetail",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun querySingleAdTaskProcessDetail(taskConfigId: String, adBizId: String): String {
        val args = JSONObject().apply {
            put("adBizId", adBizId)
            put("adTaskFlag", true)
            put("alipayGrowthFlag", false)
            put("configId", taskConfigId)
            put("sourcePassMap", buildMemberSourcePassMap())
            put("taskProcessId", "")
        }
        return RequestManager.requestString(
            "com.alipay.amic.memtask.h5.MemTaskListQueryFacade.querySingleTaskProcessDetail",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun awardMemberTaskProcess(awardRelatedOutBizNo: String, taskProcessId: String): String {
        val args = JSONObject().apply {
            put("awardRelatedOutBizNo", awardRelatedOutBizNo)
            put("taskProcessId", taskProcessId)
            put("sourcePassMap", buildMemberSourcePassMap())
        }
        return RequestManager.requestString(
            "com.alipay.alipaymember.biz.rpc.membertask.h5.award",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun rpcCall_signIn(): String {
        val args1 = """[{"sceneCode":"KOUBEI_INTEGRAL","source":"ALIPAY_TAB","version":"2.0"}]"""
        return RequestManager.requestString("alipay.kbmemberprod.action.signIn", args1)
    }

    /**
     * 黄金票收取
     *
     * @param str signInfo
     * @return 结果
     */
    @JvmStatic
    fun goldBillCollect(str: String): String {
        return RequestManager.requestString(
            "com.alipay.wealthgoldtwa.goldbill.v2.index.collect",
            """[{$str"trigger":"Y"}]"""
        )
    }

    @JvmStatic
    fun goldBillCollect(
        campId: String? = null,
        campScene: String? = null,
        from: String? = null,
        directModeDisableCollect: Boolean? = null
    ): String {
        val args = JSONObject().apply {
            if (!campId.isNullOrBlank()) {
                put("campId", campId)
            }
            if (!campScene.isNullOrBlank()) {
                put("campScene", campScene)
            }
            if (!from.isNullOrBlank()) {
                put("from", from)
            }
            if (directModeDisableCollect != null) {
                put("directModeDisableCollect", directModeDisableCollect)
            }
            put("trigger", "Y")
        }
        return RequestManager.requestString(
            "com.alipay.wealthgoldtwa.goldbill.v2.index.collect",
            JSONArray().put(args).toString()
        )
    }

    /**
     * 游戏中心签到查询
     */
    @JvmStatic
    fun querySignInBall(): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall",
            """[{"source":"$GAME_CENTER_SOURCE"}]"""
        )
    }

    /**
     * 游戏中心签到
     */
    @JvmStatic
    fun continueSignIn(): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.continueSignIn",
            """[{"sceneId":"GAME_CENTER","signType":"NORMAL_SIGN","source":"$GAME_CENTER_SOURCE"}]"""
        )
    }

    /**
     * 游戏中心任务列表
     */
    @JvmStatic
    fun queryGameCenterTaskList(): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList",
            """[{"source":"$GAME_CENTER_SOURCE"}]"""
        )
    }

    /**
     * 游戏中心普通平台任务完成（如貔貅任务）
     */
    @JvmStatic
    fun doTaskSend(taskId: String): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend",
            """[{"taskId":"$taskId"}]"""
        )
    }

    /**
     * 游戏中心签到类平台任务完成（needSignUp = true）
     */
    @JvmStatic
    fun doTaskSignup(taskId: String): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup",
            """[{"source":"$GAME_CENTER_SOURCE","taskId":"$taskId"}]"""
        )
    }

    /**
     * 游戏中心查询待领取乐豆列表
     */
    @JvmStatic
    fun queryPointBallList(): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList",
            """[{"source":"$GAME_CENTER_SOURCE"}]"""
        )
    }

    /**
     * 游戏中心全部领取
     */
    @JvmStatic
    fun batchReceivePointBall(): String {
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall",
            "[{}]"
        )
    }

    /**
     * 游戏中心赚现金首页
     */
    @JvmStatic
    fun queryGameCenterP2eHomePage(): String {
        val args = JSONObject().apply {
            put("canAddHome", true)
            put("deviceLevel", "high")
            put("screenType", 10)
            put("source", GAME_CENTER_SOURCE)
            put("unityDeviceLevel", "high")
        }
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.p2e.queryHomePage",
            JSONArray().put(args).toString()
        )
    }

    /**
     * 游戏中心赚现金签到
     */
    @JvmStatic
    fun gameCenterP2eSignIn(date: String, index: Int, signSequenceId: String): String {
        val args = JSONObject().apply {
            put("date", date)
            put("index", index)
            put("signSequenceId", signSequenceId)
        }
        return RequestManager.requestString(
            "com.alipay.gamecenteruprod.biz.rpc.p2e.signIn",
            JSONArray().put(args).toString()
        )
    }

    /**
     * 芝麻信用首页
     */
    @JvmStatic
    fun queryHome(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV7RpcManager.queryHome",
            """[{"invokeSource":"zmHome","miniZmGrayInside":"","version":"week"}]"""
        )
    }

    /**
     * 芝麻信用首页（V8 兼容）
     */
    @JvmStatic
    fun queryHomeV8(): String {
        val args = JSONObject().apply {
            put("invokeSource", "zmHome")
            put("miniZmGrayInside", "")
            put("switchNavigation", true)
            put("switchNewPage", true)
            put("version", "week")
        }
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryHome",
            JSONArray().put(args).toString()
        )
    }

    /**
     * 获取芝麻信用任务列表
     */
    @JvmStatic
    fun queryAvailableSesameTask(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
            """[{"chInfo":"$SESAME_TASK_CH_INFO","deliverStatus":"","deliveryTemplateId":"","sceneCode":"$SESAME_TASK_SCENE_CODE","searchAddToHomeTask":true,"searchGuidePopFlag":true,"searchShareAssistTask":true,"searchSubscribeTask":true,"supportJumpAuth":true,"version":"$SESAME_TASK_VERSION"}]"""
        )
    }

    /**
     * 芝麻信用领取任务
     */
    @JvmStatic
    fun joinSesameTask(taskTemplateId: String, sceneCode: String? = null): String {
        val args = JSONObject().apply {
            put("chInfo", SESAME_TASK_JOIN_CH_INFO)
            put("joinFromOuter", false)
            if (!sceneCode.isNullOrBlank()) {
                put("sceneCode", sceneCode)
            }
            put("templateId", taskTemplateId)
        }
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity",
            JSONArray().put(args).toString()
        )
    }

    /**
     * 芝麻信用获取任务回调
     */
    @JvmStatic
    fun feedBackSesameTask(
        taskTemplateId: String,
        bizType: String? = null,
        sceneCode: String? = null,
        version: String? = null
    ): String {
        val args = JSONObject().apply {
            put("actionType", "TO_COMPLETE")
            if (!bizType.isNullOrBlank()) {
                put("bizType", bizType)
            }
            if (!sceneCode.isNullOrBlank()) {
                put("sceneCode", sceneCode)
            }
            put("templateId", taskTemplateId)
            if (!version.isNullOrBlank()) {
                put("version", version)
            }
        }
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
            JSONArray().put(args).toString(),
            "zmmemberop", "taskFeedback", "CreditAccumulateStrategyRpcManager"
        )
    }

    /**
     * 芝麻信用完成任务
     */
    @JvmStatic
    fun finishSesameTask(recordId: String): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity",
            """[{"recordId":"$recordId"}]"""
        )
    }

    @JvmStatic
    fun adRewardLjcs(adTaskBizId: String): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.adRewardLjcs",
            """[{"adTaskBizId":"$adTaskBizId"}]"""
        )
    }

    /**
     * 查询可收取的芝麻粒
     */
    @JvmStatic
    fun queryCreditFeedback(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback",
            """[{"queryPotential":false,"size":20,"status":"UNCLAIMED"}]"""
        )
    }

    /**
     * 一键收取芝麻粒
     */
    @JvmStatic
    fun collectAllCreditFeedback(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
            """[{"collectAll":true,"status":"UNCLAIMED"}]"""
        )
    }

    /**
     * 收取芝麻粒
     *
     * @param creditFeedbackId creditFeedbackId
     */
    @JvmStatic
    fun collectCreditFeedback(creditFeedbackId: String): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
            """[{"collectAll":false,"creditFeedbackId":"$creditFeedbackId","status":"UNCLAIMED"}]"""
        )
    }

    /**
     * 芝麻粒首页
     */
    @JvmStatic
    fun queryPointHome(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.PointHomeRpcManager.queryHome",
            "[{}]"
        )
    }

    /**
     * 查询最近一次被操作的芝麻任务
     */
    @JvmStatic
    fun queryLastOperateTask(version: String = SESAME_TASK_VERSION): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryLastOperateTask",
            """[{"version":"$version"}]"""
        )
    }

    /**
     * 芝麻粒福利签到列表
     */
    @JvmStatic
    fun zmlCheckInQueryTaskLists(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
            """[{"supportMakeUp":true,"version":"$SESAME_CHECK_IN_VERSION"}]"""
        )
    }

    /**
     * 获取保障金信息
     */
    @JvmStatic
    fun queryInsuredHome(): String {
        return RequestManager.requestString(
            "com.alipay.insplatformbff.insgift.accountService.queryAccountForPlat",
            """[{"includePolicy":true,"specialChannel":"wealth_entry"}]"""
        )
    }

    /**
     * 获取所有可领取的保障金
     */
    @JvmStatic
    fun queryAvailableCollectInsuredGold(): String {
        return RequestManager.requestString(
            "com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList",
            """[{"entrance":"cfsy","eventToWaitParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"helpChildParamDTO":{"giftProdCode":"GIFT_HEALTH_GOLD_CHILD","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"priorityChannelParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"signInParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]}}]""",
            "insgiftbff", "queryMultiSceneWaitToGainList", "insgiftMain"
        )
    }

    /**
     * 领取保障金
     */
    @JvmStatic
    fun collectInsuredGold(goldBallObj: JSONObject): String {
        return RequestManager.requestString(
            "com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured",
            JSONArray().put(goldBallObj).toString(), "insgiftbff", "gainMyAndFamilySumInsured", "insgiftMain"
        )
    }

    /**
     * 查询生活记录
     *
     * @return 结果
     */
    @JvmStatic
    fun promiseQueryHome(): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryHome",
            null
        )
    }

    /**
     * 查询生活记录明细
     *
     * @param recordId recordId
     * @return 结果
     */
    @JvmStatic
    fun promiseQueryDetail(recordId: String): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryDetail",
            """[{"recordId":"$recordId"}]"""
        )
    }

    /**
     * 生活记录加入新纪录
     *
     * @param data data
     * @return 结果
     */
    @JvmStatic
    fun promiseJoin(data: String): String {
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.join",
            "[$data]"
        )
    }

    /**
     * 查询待领取的保障金
     *
     * @return 结果
     */
    @JvmStatic
    fun queryMultiSceneWaitToGainList(): String {
        return RequestManager.requestString(
            "com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList",
            """[{"entrance":"jkj_zhima_dairy66","eventToWaitParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"helpChildParamDTO":{"giftProdCode":"GIFT_HEALTH_GOLD_CHILD","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"priorityChannelParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]},"signInParamDTO":{"giftProdCode":"GIFT_UNIVERSAL_COVERAGE","rightNoList":["UNIVERSAL_ACCIDENT","UNIVERSAL_HOSPITAL","UNIVERSAL_OUTPATIENT","UNIVERSAL_SERIOUSNESS","UNIVERSAL_WEALTH","UNIVERSAL_TRANS","UNIVERSAL_FRAUD_LIABILITY"]}}]"""
        )
    }

    /**
     * 领取保障金
     *
     * @param jsonObject jsonObject
     * @return 结果
     */
    @JvmStatic
    @Throws(JSONException::class)
    fun gainMyAndFamilySumInsured(jsonObject: JSONObject): String {
        jsonObject.apply {
            put("disabled", false)
            put("entrance", "jkj_zhima_dairy66")
        }
        return RequestManager.requestString(
            "com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured",
            "[$jsonObject]"
        )
    }

    // 安心豆
    @JvmStatic
    fun querySignInProcess(appletId: String, scene: String): String {
        val args = JSONObject().apply {
            put("appletId", appletId)
            put("bizData", JSONObject().apply {
                put("checkMultiAccountFrequency", "true")
            })
            put("scene", scene)
        }
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.bean.querySignInProcess",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun signInTrigger(appletId: String, scene: String): String {
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.bean.signInTrigger",
            """[{"appletId":"$appletId","scene":"$scene"}]"""
        )
    }

    @JvmStatic
    fun queryGuardianGradeAwards(): String {
        val args = JSONObject().apply {
            put("entrance", "myb_tab_axd_qd")
            put("queryAwardStatus", true)
            put("sceneCode", "POSITION")
        }
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.guardian.queryGradeAwards",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun guardianAwardSend(skuId: String): String {
        val args = JSONObject().apply {
            put("entrance", "myb_tab_axd_qd")
            put("sceneCode", "POSITION")
            put("skuId", skuId)
        }
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.guardian.awardSend",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun beanExchangeDetail(itemId: String): String {
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.onestop.planTrigger",
            """[{"extParams":{"itemId":"$itemId"},"planCode":"bluebean_onestop","planOperateCode":"exchangeDetail"}]"""
        )
    }

    @JvmStatic
    fun beanExchange(itemId: String, pointAmount: Int): String {
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.onestop.planTrigger",
            """[{"extParams":{"itemId":"$itemId","pointAmount":"$pointAmount"},"planCode":"bluebean_onestop","planOperateCode":"exchange"}]"""
        )
    }

    @JvmStatic
    fun queryUserAccountInfo(pointProdCode: String): String {
        return RequestManager.requestString(
            "com.alipay.insmarketingbff.point.queryUserAccountInfo",
            """[{"channel":"HiChat","pointProdCode":"$pointProdCode","pointUnitType":"COUNT"}]"""
        )
    }

    /**
     * 查询会员信息
     */
    @JvmStatic
    fun queryMemberInfo(): String {
        val data = """[{"needExpirePoint":true,"needGrade":true,"needPoint":true,"queryScene":"POINT_EXCHANGE_SCENE","source":"POINT_EXCHANGE_SCENE","sourcePassMap":{"innerSource":"","source":"","unid":""}}]"""
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.member.h5.queryMemberInfo", data)
    }

    /**
     * 查询0元兑公益道具列表
     *
     * @param userId       userId
     * @param pointBalance 当前可用会员积分
     */
    @JvmStatic
    fun queryShandieEntityList(userId: String, pointBalance: String): String {
        val uniqueId = "${System.currentTimeMillis()}${userId}94000SR202501061144200394000SR2025010611458003"
        val data = """[{"blackIds":[],"deliveryIdList":["94000SR2025010611442003","94000SR2025010611458003"],"filterCityCode":false,"filterPointNoEnough":false,"filterStockNoEnough":false,"pageNum":1,"pageSize":18,"point":$pointBalance,"previewCopyDbId":"","queryType":"DELIVERY_ID_LIST","source":"member_day","sourcePassMap":{"innerSource":"","source":"0yuandui","unid":""},"topIds":[],"uniqueId":"$uniqueId"}]"""
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryShandieEntityList", data)
    }

    /**
     * 会员积分兑换道具
     *
     * @param benefitId benefitId
     * @param itemId    itemId
     * @return 结果
     */
    @JvmStatic
    fun exchangeBenefit(benefitId: String, itemId: String): String {
        val requestId = "requestId${System.currentTimeMillis()}"
        val alipayClientVersion = io.github.aoguai.sesameag.hook.ApplicationHook.alipayVersion.versionString
        val data = """[{"benefitId":"$benefitId","cityCode":"","exchangeType":"POINT_PAY","itemId":"$itemId","miniAppId":"","orderSource":"","requestId":"$requestId","requestSourceInfo":"","sourcePassMap":{"alipayClientVersion":"$alipayClientVersion","innerSource":"","mobileOsType":"Android","source":"","unid":""},"userOutAccount":""}]"""
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", data)
    }

    @JvmStatic
    fun exchangeBenefit(benefitId: String, itemId: String, userId: String?): String {
        val now = System.currentTimeMillis()
        val requestId = "requestId$now"
        val unid = UUID.randomUUID().toString()
        val uniqueId = userId.orEmpty() + now
        val requestSourceInfo = "SID:$uniqueId|0"

        val alipayClientVersion = io.github.aoguai.sesameag.hook.ApplicationHook.alipayVersion.versionString
        val data =
            """[{"benefitId":"$benefitId","cityCode":"","exchangeType":"POINT_PAY","itemId":"$itemId","miniAppId":"","orderSource":"","requestId":"$requestId","requestSourceInfo":"$requestSourceInfo","sourcePassMap":{"alipayClientVersion":"$alipayClientVersion","bid":"","feedsIndex":"0","innerSource":"a159.b52659","isCpc":"","mobileOsType":"Android","source":"","unid":"$unid","uniqueId":"$uniqueId"},"userOutAccount":""}]"""
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", data)
    }

    @JvmStatic
    fun queryDeliveryZoneDetail(deliveryIdList: List<String>, pageNum: Int, pageSize: Int): String {
        if (deliveryIdList.isEmpty()) return ""

        val idsJoined = deliveryIdList.joinToString(",")
        val uniqueId = "17665547901390and99999999INTELLIGENT_SORT92524974$idsJoined"
        val deliveryIdListJson = deliveryIdList.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

        val data =
            """[{"deliveryIdList":$deliveryIdListJson,"lowerPoint":0,"pageNum":$pageNum,"pageSize":$pageSize,"queryNoReserve":true,"resourceCardChannel":"ZERO_EXCHANGE_CHANNEL","sourcePassMap":{"innerSource":"","source":"","unid":""},"startPageFirstQuery":false,"topIdList":["202412231259661040"],"uniqueId":"$uniqueId","upperPoint":99999999,"withPointRange":false}]"""
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", data)
    }

    @JvmStatic
    fun queryExchangeList(page: Int, pageSize: Int): String {
        val args =
            """[{"currentPage":$page,"formDelivery":"false","pageSize":$pageSize,"privilegeSource":"","privilegeTab":"","tabList":[]}]"""
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryListV2",
            args
        )
    }

    @JvmStatic
    fun obtainAward(templateId: String): String {
        val args = """[{"awardTemplateId":"$templateId"}]"""
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.obtainAward",
            args
        )
    }

    private const val ZHIMATREE_PLAY_INFO = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3"
    private const val ZHIMATREE_REFER =
        "https://render.alipay.com/p/yuyan/180020010001269849/zmTree.html?caprMode=sync&chInfo=chInfo=ch_zmzltf__chsub_xinyongsyyingxiaowei"

    @JvmStatic
    fun zhimaTreeHomePage(): String? {
        return try {
            val args = JSONObject().apply {
                put("operation", "ZHIMA_TREE_HOME_PAGE")
                put("playInfo", ZHIMATREE_PLAY_INFO)
                put("refer", ZHIMATREE_REFER)
                put("extInfo", JSONObject())
            }
            RequestManager.requestString(
                "alipay.promoprod.play.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun zhimaTreeCleanAndPush(treeCode: String): String? {
        return try {
            val extInfo = JSONObject().apply {
                put("clickNum", "1")
                put("treeCode", treeCode)
            }
            val args = JSONObject().apply {
                put("operation", "ZHIMA_TREE_CLEAN_AND_PUSH")
                put("playInfo", ZHIMATREE_PLAY_INFO)
                put("refer", ZHIMATREE_REFER)
                put("extInfo", extInfo)
            }
            RequestManager.requestString(
                "alipay.promoprod.play.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun queryRentGreenTaskList(): String? {
        return try {
            val extInfo = JSONObject().apply {
                put("chInfo", "ch_share__chsub_ALPContact")
                put("batchId", "")
            }
            val args = JSONObject().apply {
                put("operation", "RENT_GREEN_TASK_LIST_QUERY")
                put("playInfo", ZHIMATREE_PLAY_INFO)
                put("refer", ZHIMATREE_REFER)
                put("extInfo", extInfo)
            }
            RequestManager.requestString(
                "alipay.promoprod.play.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun rentGreenTaskFinish(taskId: String, stageCode: String): String? {
        return try {
            val extInfo = JSONObject().apply {
                put("chInfo", "ch_share__chsub_ALPContact")
                put("taskId", taskId)
                put("stageCode", stageCode)
            }
            val args = JSONObject().apply {
                put("operation", "RENT_GREEN_TASK_FINISH")
                put("playInfo", ZHIMATREE_PLAY_INFO)
                put("refer", ZHIMATREE_REFER)
                put("extInfo", extInfo)
            }
            RequestManager.requestString(
                "alipay.promoprod.play.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun queryGoldTicketHome(taskId: String = ""): String? {
        return try {
            val args = JSONObject().apply {
                put("bizScene", "goldpage")
                put("chInfo", "goldpage")
                put("taskId", taskId)
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.needle.v2.index",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun goldTicketIndexCollect(
        triggerCollect: Boolean = true,
        directModeDisableCollect: Boolean = false
    ): String? {
        return try {
            val args = JSONObject().apply {
                if (triggerCollect) {
                    put("trigger", "Y")
                } else if (directModeDisableCollect) {
                    put("directModeDisableCollect", 1)
                }
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.needle.index.collect",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun queryWelfareHome(): String? {
        return try {
            val args = JSONObject().apply {
                put("isResume", true)
            }
            RequestManager.requestString(
                "com.alipay.finaggexpbff.needle.welfareCenter.index",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun welfareCenterTrigger(type: String): String {
        return try {
            val args = JSONObject().apply {
                put("type", type)
            }
            RequestManager.requestString(
                "com.alipay.finaggexpbff.needle.welfareCenter.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    fun queryConsumeHome(): String? {
        return try {
            val args = JSONObject().apply {
                put("tabBubbleDeliverParam", JSONObject())
                put("tabTypeDeliverParam", JSONObject())
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.needle.consume.query",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun submitConsume(amount: Int, productId: String, bonusAmount: Int): String? {
        return try {
            val args = JSONObject().apply {
                put("exchangeAmount", amount)
                put("exchangeMoney", String.format("%.2f", amount / 1000.0))
                put("prizeType", "GOLD")
                put("productId", productId)
                put("bonusAmount", bonusAmount)
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.needle.consume.submit",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun taskQueryPush(taskId: String): String? {
        return try {
            val args = JSONObject().apply {
                put("mode", 1)
                put("taskId", taskId)
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.needle.taskQueryPush",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun goldBillTaskTrigger(taskId: String): String? {
        return try {
            val args = JSONObject().apply {
                put("taskId", taskId)
            }
            RequestManager.requestString(
                "com.alipay.wealthgoldtwa.goldbill.v4.task.trigger",
                JSONArray().put(args).toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun queryStickerCanReceive(year: String, month: String): String {
        val data =
            """[{"isFirstShow":"false","month":"$month","scene":"","year":"$year"}]"""
        return RequestManager.requestString("alipay.memberasset.sticker.queryStickerCanReceive", data)
    }

    @JvmStatic
    fun receiveSticker(year: String, month: String, stickerIds: List<String>): String {
        if (stickerIds.isEmpty()) return ""
        val args = JSONObject().apply {
            put("month", month)
            put("stickerIds", JSONArray().apply {
                stickerIds.forEach { put(it) }
            })
            put("year", year)
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.receiveSticker",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryStickerHomePage(year: String, month: String, day: String): String {
        val args = JSONObject().apply {
            put("day", day)
            put("gmtBiz", "")
            put("month", month)
            put("scene", "")
            put("source", "")
            put("stickerConfigId", "")
            put("year", year)
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.queryHomePage",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun upgradeStickerBatch(upgradeReqList: JSONArray): String {
        val args = JSONObject().apply {
            put("upgradeReqList", upgradeReqList)
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.upgradeStickerBatch",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryStickerDetailPage(year: String, month: String, stickerConfigId: String): String {
        val args = JSONObject().apply {
            put("month", month)
            put("stickerConfigId", stickerConfigId)
            put("stickerStatus", "received")
            put("year", year)
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.queryDetailPage",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun triggerStickerUpgradePrize(stickerConfigId: String): String {
        val args = JSONObject().apply {
            put("levelCode", "")
            put("stickerCfgId", stickerConfigId)
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.triggerUpgradePrize",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun queryStickerPrizeHomePage(): String {
        val args = JSONObject().apply {
            put("externParams", JSONObject())
        }
        return RequestManager.requestString(
            "alipay.memberasset.sticker.prize.home.page",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun triggerStickerDrawing(prizeQuotaRecordId: String): String {
        val args = JSONObject().apply {
            put("prizeQuotaRecordId", prizeQuotaRecordId)
            put("type", "Drawing")
        }
        return RequestManager.requestString(
            "alipay.memberasset.prize.trigger",
            JSONArray().put(args).toString()
        )
    }

    @JvmStatic
    fun zmCheckInCompleteTask(checkInDate: String, sceneCode: String): String {
        val args = """[{"checkInDate":"$checkInDate","sceneCode":"$sceneCode"}]"""
        return RequestManager.requestString(
            "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask",
            args
        )
    }

    /**
     * 兼容旧实现：将芝麻信用相关 RPC 归类到 Zmxy 命名空间下，供 AntMember 调用。
     *
     * 说明：这些方法来自历史实现（Sesame-GR），在 AG Kotlin 迁移过程中被引用但未补齐。
     * 这里按最小集补齐，确保编译通过与功能可用。
     */
    object Zmxy {

        /**
         * 查询“成长引导/信誉任务”待办列表
         */
        @JvmStatic
        fun queryGrowthGuideToDoList(
            guideBehaviorId: String = "",
            invokeVersion: String = SESAME_GROWTH_GUIDE_INVOKE_VERSION
        ): String {
            val requestData =
                """[{"guideBehaviorId":"$guideBehaviorId","invokeVersion":"$invokeVersion","switchNewPage":true}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.queryToDoList",
                requestData
            )
        }

        /**
         * 接受/开启一个行为任务
         */
        @JvmStatic
        fun openBehaviorCollect(behaviorId: String): String {
            val requestData = """[{"behaviorId":"$behaviorId"}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.openBehaviorCollect",
                requestData
            )
        }

        /**
         * 查询每日问答题目
         */
        @JvmStatic
        fun queryDailyQuiz(behaviorId: String): String {
            val requestData = """[{"behaviorId":"$behaviorId"}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.queryDailyQuiz",
                requestData
            )
        }

        /**
         * 提交每日问答/视频问答答案
         */
        @JvmStatic
        fun pushDailyTask(
            behaviorId: String,
            bizDate: Long,
            answerId: String,
            questionId: String,
            answerStatus: String
        ): String {
            val extInfo =
                """{"answerId":"$answerId","answerStatus":"$answerStatus","questionId":"$questionId"}"""
            val requestData = """[{"behaviorId":"$behaviorId","bizDate":$bizDate,"extInfo":$extInfo}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask",
                requestData
            )
        }

        /**
         * 查询当前可领取的进度球
         */
        @JvmStatic
        fun queryScoreProgress(): String {
            val requestData = """[{"needTotalProcess":"TRUE","queryGuideInfo":true,"switchNewPage":true}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress",
                requestData
            )
        }

        /**
         * 收集一个或多个进度球
         */
        @JvmStatic
        fun collectProgressBall(ballIdList: JSONArray?): String? {
            if (ballIdList == null || ballIdList.length() == 0) return null
            val requestData = """[{"ballIdList":${ballIdList}}]"""
            return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.collectProgressBall",
                requestData
            )
        }

        object Alchemy {
            @JvmStatic
            fun alchemyQueryCheckIn(sceneCode: String): String {
                val requestData = """[{"sceneCode":"$sceneCode","version":"$SESAME_CHECK_IN_VERSION"}]"""
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
                    requestData
                )
            }

            @JvmStatic
            fun alchemyQueryEntryList(): String {
                val requestData = """[{"version":"$SESAME_CHECK_IN_VERSION"}]"""
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryEntryList",
                    requestData
                )
            }

            @JvmStatic
            fun claimAward(awardId: String = ""): String {
                val requestData = if (awardId.isBlank()) {
                    "[{}]"
                } else {
                    """[{"awardId":"$awardId"}]"""
                }
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward",
                    requestData
                )
            }

            @JvmStatic
            fun alchemyQueryHome(): String {
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome",
                    "[{}]"
                )
            }

            @JvmStatic
            fun alchemyExecute(): String {
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy",
                    "[null]"
                )
            }

            @JvmStatic
            fun alchemyQueryTimeLimitedTask(): String {
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask",
                    "[{}]"
                )
            }

            @JvmStatic
            fun alchemyCompleteTimeLimitedTask(templateId: String): String {
                val requestData = """[{"templateId":"$templateId"}]"""
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
                    requestData
                )
            }

            @JvmStatic
            fun alchemyQueryListV3(): String {
                val requestData =
                    """[{"chInfo":"","deliverStatus":"","deliveryTemplateId":"","searchSubscribeTask":true,"supportRewardLJCS":true,"version":"alchemy"}]"""
                return RequestManager.requestString(
                    "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
                    requestData
                )
            }
        }
    }
}


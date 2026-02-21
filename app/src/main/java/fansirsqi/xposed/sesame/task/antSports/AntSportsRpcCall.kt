package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.RequestManager

/**
 * @file AntSportsRpcCall.kt
 * @brief 支付宝蚂蚁运动（AntSports）RPC 接口调用集合
 * 
 * @details
 * 本模块提供了支付宝蚂蚁运动所有功能的 RPC 接口的 Kotlin 封装版本。
 * 包括：
 * - 运动任务查询与完成
 * - 能量球（金币）收集与捐赠
 * - 行走路线管理与进度查询
 * - 运动币兑换与礼品领取
 * - 健康岛（Neverland）任务系统
 * - 抢好友大战功能
 * 
 * @author [Original Java Author]
 * @since 2025.01.20
 * @version 1.0.0
 */
object AntSportsRpcCall {

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 常量定义
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 应用渠道信息 - 应用中心子频道9patch
     */
    private const val CH_INFO = "ch_appcenter__chsub_9patch"

    /**
     * @brief 时区信息 - 亚洲/上海
     */
    private const val TIME_ZONE = "Asia\\/Shanghai"

    /**
     * @brief 版本号 - 蚂蚁运动版本
     */
    private const val VERSION = "3.0.1.2"

    /**
     * @brief 支付宝应用版本 - 动态获取
     */
    private val ALIPAY_APP_VERSION =ApplicationHook.alipayVersion

    /**
     * @brief 城市代码 - 杭州
     */
    private const val CITY_CODE = "330100"

    /**
     * @brief 应用ID - 蚂蚁运动小程序ID
     */
    private const val APP_ID = "2021002116659397"

    /**
     * @brief 功能特性列表 - JSON 格式字符串
     * 
     * 包含运动各项功能的支持标识符，用于API请求。
     */
    private const val FEATURES = """["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"]"""

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 运动任务面板接口
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询运动币任务面板
     * 
     * @details 获取首页运动任务列表，包括能量获取任务和活动任务。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.queryCoinTaskPanel
     */
    fun queryCoinTaskPanel(): String {
        val args1 = """[{"apiVersion":"energy","canAddHome":false,"chInfo":"medical_health","clientAuthStatus":"not_support","clientOS":"android","features":$FEATURES,"topTaskId":""}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.queryCoinTaskPanel", args1)
    }

    /**
     * @brief 运动任务签到
     * 
     * @param taskId 任务ID - 特定任务的唯一标识
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signUpTask
     */
    fun signUpTask(taskId: String): String {
        val args = """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES,"taskCenId":"","taskId":"$taskId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signUpTask", args)
    }

    /**
     * @brief 完成运动锻炼任务（旧版接口）
     * 
     * @param taskId 任务ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该接口为旧版，建议使用 #completeTask
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask
     */
    fun completeExerciseTasks(taskId: String): String {
        val args1 = """[{"chInfo":"ch_appcenter__chsub_9patch","clientOS":"android","features":$FEATURES,"taskAction":"JUMP","taskId":"$taskId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args1)
    }

    /**
     * @brief 完成运动任务（新版接口）
     * 
     * @param taskId 任务ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask
     */
    fun completeTask(taskId: String): String {
        val args1 = """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES,"taskAction":"JUMP","taskId":"$taskId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask", args1)
    }

    /**
     * @brief 查询运动主页信息
     * 
     * @details 获取运动首页的数据，包括个人信息、排行榜等。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.queryMainPage
     */
    fun queryMainPage(): String {
        val args = """{"apiVersion":"energy","chInfo":"ch_shouquan_shouye","cityCode":"$CITY_CODE","clientOS":"android","features":$FEATURES,"timezone":"Asia/Shanghai"}"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.queryMainPage", args)
    }

    /**
     * @brief 运动健康签到/查询接口
     * 
     * @param operatorType 操作类型
     *   - "signIn" - 执行签到
     *   - "query" - 查询签到状态
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signInCoinTask
     */
    fun signInCoinTask(operatorType: String): String {
        val args1 = """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES,"operatorType":"$operatorType"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.signInCoinTask", args1)
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 能量球模块接口
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询能量球泡泡模块
     * 
     * @details 获取首页可领取的能量球列表。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryCoinBubbleModule
     */
    fun queryCoinBubbleModule(): String {
        return RequestManager.requestString(
            "com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryCoinBubbleModule",
            """[{"bubbleId":"","canAddHome":false,"chInfo":"$CH_INFO","clientAuthStatus":"not_support","clientOS":"android","distributionChannel":"","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"]}]"""
        )
    }

    /**
     * @brief 领取能量球任务能量（无指定方式，默认不抢好友能量球）
     * 
     * @param medEnergyBallInfoRecordId 能量球记录ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
     */
    fun pickBubbleTaskEnergy(medEnergyBallInfoRecordId: String): String {
        return pickBubbleTaskEnergy(medEnergyBallInfoRecordId, false)
    }

    /**
     * @brief 领取能量球任务能量（可指定是否抢好友能量球）
     * 
     * @param medEnergyBallInfoRecordId 能量球记录ID
     * @param pickAllEnergyBall 是否领取所有能量球（包括好友的）
     *   - true - 领取所有能量球，包括好友的
     *   - false - 仅领取自己的
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
     */
    fun pickBubbleTaskEnergy(medEnergyBallInfoRecordId: String, pickAllEnergyBall: Boolean): String {
        val args1 = """[{"apiVersion":"energy","chInfo":"medical_health","medEnergyBallInfoRecordIds":["$medEnergyBallInfoRecordId"],"pickAllEnergyBall":$pickAllEnergyBall,"source":"SPORT"}]"""
        return RequestManager.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args1)
    }

    /**
     * @brief 查询能量球模块 - 推荐列表
     * 
     * @details 获取首页能量球推荐列表，包含广告和活动任务。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryEnergyBubbleModule
     */
    fun queryEnergyBubbleModule(): String {
        val features = """["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"]"""
        val args1 = """[{"apiVersion":"energy","bubbleId":"","canAddHome":false,"chInfo":"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity","clientAuthStatus":"not_support","clientOS":"android","distributionChannel":"","features":$features,"outBizNo":""}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryEnergyBubbleModule", args1)
    }

    /**
     * @brief 拾取能量球（无参数版本）
     * 
     * @details 领取所有待领取的能量球，传递空数组。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
     */
    fun pickBubbleTaskEnergy(): String {
        val features = """["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_AI","SUPPORT_TAB3","SUPPORT_FLYRABBIT","SUPPORT_NEW_MATCH","EXTERNAL_ADVERTISEMENT_TASK","PROP","PROPV2","ASIAN_GAMES"]"""
        val args1 = """[{"apiVersion":"energy","chInfo":"ch_appid-20001003__chsub_pageid-com.alipay.android.phone.businesscommon.globalsearch.ui.MainSearchActivity","clientOS":"android","features":$features,"medEnergyBallInfoRecordIds":[],"pickAllEnergyBall":true,"source":"SPORT"}]"""
        return RequestManager.requestString("com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy", args1)
    }

    /**
     * @brief 收集金币资产
     * 
     * @param assetId 资产ID - 金币资产唯一标识
     * @param coinAmount 金币数量 - 收集的金币数量
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthCoinCenterRpc.receiveCoinAsset
     */
    fun receiveCoinAsset(assetId: String, coinAmount: Int): String {
        return RequestManager.requestString(
            "com.alipay.sportshealth.biz.rpc.SportsHealthCoinCenterRpc.receiveCoinAsset",
            """[{"assetId":"$assetId","chInfo":"$CH_INFO","clientOS":"android","coinAmount":$coinAmount,"features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"tracertPos":"首页金币收集"}]"""
        )
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 运动币兑换模块
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询礼品详情
     * 
     * @param itemId 礼品ID - 要查询的礼品唯一标识
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryItemDetail
     */
    fun queryItemDetail(itemId: String): String {
        val arg = """[{"itemId":"$itemId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryItemDetail", arg)
    }

    /**
     * @brief 兑换礼品
     * 
     * @param itemId 礼品ID
     * @param coinAmount 消耗运动币数量
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.exchangeItem
     */
    fun exchangeItem(itemId: String, coinAmount: Int): String {
        val arg = """[{"coinAmount":$coinAmount,"itemId":"$itemId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.exchangeItem", arg)
    }

    /**
     * @brief 查询兑换记录详情
     * 
     * @param exchangeRecordId 兑换记录ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryExchangeRecordPage
     */
    fun queryExchangeRecordPage(exchangeRecordId: String): String {
        val arg = """[{"exchangeRecordId":"$exchangeRecordId"}]"""
        return RequestManager.requestString("com.alipay.sportshealth.biz.rpc.SportsHealthItemCenterRpc.queryExchangeRecordPage", arg)
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 旧版行走路线模块（已过时，保留兼容性）
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询个人首页 - 旧版运动路线
     * 
     * @details 获取用户在旧版蚂蚁行走中的个人主页信息。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口，新版应使用新API
     * @remark 对应API：alipay.antsports.walk.map.queryMyHomePage
     */
    fun queryMyHomePage(): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.map.queryMyHomePage",
            """[{"alipayAppVersion":"$ALIPAY_APP_VERSION","chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"pathListUsePage":true,"timeZone":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 加入旧版运动路线
     * 
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.map.join
     */
    fun join(pathId: String): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.map.join",
            """[{"chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"pathId":"$pathId"}]"""
        )
    }

    /**
     * @brief 首次开启并加入旧版运动路线
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.user.openAndJoinFirst
     */
    fun openAndJoinFirst(): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.user.openAndJoinFirst",
            """[{"chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"]}]"""
        )
    }

    /**
     * @brief 行走旧版运动路线
     * 
     * @param day 日期字符串，格式如 "yyyy-MM-dd"
     * @param rankCacheKey 排行榜缓存键
     * @param stepCount 使用的步数
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.map.go
     */
    fun go(day: String, rankCacheKey: String, stepCount: Int): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.map.go",
            """[{"chInfo":"$CH_INFO","clientOS":"android","day":"$day","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"needAllBox":true,"rankCacheKey":"$rankCacheKey","timeZone":"$TIME_ZONE","useStepCount":$stepCount}]"""
        )
    }

    /**
     * @brief 开启旧版运动宝箱
     * 
     * @param boxNo 宝箱编号
     * @param userId 用户ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.treasureBox.openTreasureBox
     */
    fun openTreasureBox(boxNo: String, userId: String): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.treasureBox.openTreasureBox",
            """[{"boxNo":"$boxNo","chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"userId":"$userId"}]"""
        )
    }

    /**
     * @brief 查询路线基础列表
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.path.queryBaseList
     */
    fun queryBaseList(): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.path.queryBaseList",
            """[{"chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"]}]"""
        )
    }

    /**
     * @brief 查询项目列表
     * 
     * @param index 页码索引，从0开始
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.charity.queryProjectList
     */
    fun queryProjectList(index: Int): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.charity.queryProjectList",
            """[{"chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"index":$index,"projectListUseVertical":true}]"""
        )
    }

    /**
     * @brief 捐赠慈善能量
     * 
     * @param donateCharityCoin 捐赠慈善能量数量
     * @param projectId 项目ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.charity.donate
     */
    fun donate(donateCharityCoin: Int, projectId: String): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.charity.donate",
            """[{"chInfo":"$CH_INFO","clientOS":"android","donateCharityCoin":$donateCharityCoin,"features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"projectId":"$projectId"}]"""
        )
    }

    /**
     * @brief 查询行走步数
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.antsports.walk.user.queryWalkStep
     */
    fun queryWalkStep(): String {
        return RequestManager.requestString(
            "alipay.antsports.walk.user.queryWalkStep",
            """[{"chInfo":"$CH_INFO","clientOS":"android","features":["DAILY_STEPS_RANK_V2","STEP_BATTLE","CLUB_HOME_CARD","NEW_HOME_PAGE_STATIC","CLOUD_SDK_AUTH","STAY_ON_COMPLETE","EXTRA_TREASURE_BOX","NEW_HOME_PAGE_STATIC","SUPPORT_TAB3","SUPPORT_FLYRABBIT","PROP","PROPV2","ASIAN_GAMES"],"timeZone":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 行走捐赠签到信息
     * 
     * @param count 步数
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.charity.mobile.donate.walk.walkDonateSignInfo
     */
    fun walkDonateSignInfo(count: Int): String {
        return RequestManager.requestString(
            "alipay.charity.mobile.donate.walk.walkDonateSignInfo",
            """[{"needDonateAction":false,"source":"walkDonateHome","steps":$count,"timezoneId":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 行走捐赠首页
     * 
     * @param count 步数
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.charity.mobile.donate.walk.home
     */
    fun donateWalkHome(count: Int): String {
        return RequestManager.requestString(
            "alipay.charity.mobile.donate.walk.home",
            """[{"module":"3","steps":$count,"timezoneId":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 兑换捐赠步数
     * 
     * @param actId 活动ID
     * @param count 步数
     * @param donateToken 捐赠令牌
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该为旧版接口
     * @remark 对应API：alipay.charity.mobile.donate.walk.exchange
     */
    fun exchange(actId: String, count: Int, donateToken: String): String {
        return RequestManager.requestString(
            "alipay.charity.mobile.donate.walk.exchange",
            """[{"actId":"$actId","count":$count,"donateToken":"$donateToken","timezoneId":"$TIME_ZONE","ver":0}]"""
        )
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 新版行走路线模块（推荐使用）
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询用户信息 - 新版
     * 
     * @details 获取新版蚂蚁运动中用户的基本信息。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.queryUser
     */

    fun queryUser(): String {
        // 补齐 mainPage 和 timeZone，直接一行搞定
        val data = """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES,"mainPage":true,"timeZone":"$TIME_ZONE"}]"""

        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.queryUser", data)
    }

    /**
     * @brief 查询主题列表 - 新版
     * 
     * @details 获取可用的运动路线主题列表。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.theme.queryThemeList
     */
    fun queryThemeList(): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.theme.queryThemeList",
            """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES}]"""
        )
    }

    /**
     * @brief 查询世界地图 - 新版
     * 
     * @param themeId 主题ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.queryWorldMap
     */
    fun queryWorldMap(themeId: String): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.queryWorldMap",
            """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","features":$FEATURES,"themeId":"$themeId"}]"""
        )
    }

    /**
     * @brief 查询城市路线 - 新版
     * 
     * @param cityId 城市ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.queryCityPath
     */
    fun queryCityPath(cityId: String): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.queryCityPath",
            """[{"apiVersion":"energy","chInfo":"ch_othertinyapp","cityId":"$cityId","clientOS":"android","features":$FEATURES}]"""
        )
    }

    /**
     * @brief 查询路线详情 - 新版
     * 
     * @param date 日期字符串，格式如 "yyyy-MM-dd"
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.queryPath
     */
    fun queryPath(date: String, pathId: String): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.queryPath",
            """[{"apiVersion":"energy","chInfo":"medical_health","clientOS":"android","date":"$date","enableNewVersion":true,"features":$FEATURES,"pathId":"$pathId","timeZone":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 加入运动路线 - 新版
     * 
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.joinPath
     */
    fun joinPath(pathId: String): String {
        val requestBody = """[{"apiVersion":"energy","chInfo":"ch_othertinyapp","clientOS":"android","features":$FEATURES,"pathId":"$pathId"}]"""
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.joinPath", requestBody)
    }

    /**
     * @brief 行走运动路线 - 新版
     * 
     * @param date 日期字符串，格式如 "yyyy-MM-dd"
     * @param pathId 路线ID
     * @param useStepCount 使用的步数
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.go
     */
    fun walkGo(date: String, pathId: String, useStepCount: Int): String {
        val requestBody = """[{"apiVersion":"energy","chInfo":"ch_othertinyapp","clientOS":"android","date":"$date","features":$FEATURES,"pathId":"$pathId","source":"ch_othertinyapp","timeZone":"$TIME_ZONE","useStepCount":$useStepCount}]"""
        return RequestManager.requestString("com.alipay.sportsplay.biz.rpc.walk.go", requestBody)
    }

    /**
     * @brief 领取路线事件奖励（如宝箱）- 新版
     * 
     * @param eventBillNo 事件账单号（宝箱号）
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.receiveEvent
     */
    fun receiveEvent(eventBillNo: String): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.receiveEvent",
            """[{"eventBillNo":"$eventBillNo"}]"""
        )
    }

    /**
     * @brief 查询路线奖励 - 新版
     * 
     * @param appId 应用ID
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：com.alipay.sportsplay.biz.rpc.walk.queryPathReward
     */
    fun queryPathReward(appId: String, pathId: String): String {
        return RequestManager.requestString(
            "com.alipay.sportsplay.biz.rpc.walk.queryPathReward",
            """[{"appId":"$appId","pathId":"$pathId","source":"ch_appcenter__chsub_9patch"}]"""
        )
    }

    /**
     * @brief 兑换成功回调 - 旧版（已过时）
     * 
     * @param exchangeId 兑换ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @deprecated 该接口已过时
     * @remark 对应API：alipay.charity.mobile.donate.exchange.success
     */
    fun exchangeSuccess(exchangeId: String): String {
        val args1 = """[{"exchangeId":"$exchangeId","timezone":"GMT+08:00","version":"$VERSION"}]"""
        return RequestManager.requestString("alipay.charity.mobile.donate.exchange.success", args1)
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 文体中心模块
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询用户任务组
     * 
     * @param groupId 任务组ID
     *   - "SPORTS_DAILY_SIGN_GROUP" - 日常签到组
     *   - "SPORTS_DAILY_GROUP" - 日常任务组
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.sports.userTaskGroup.query
     */
    fun userTaskGroupQuery(groupId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.sports.userTaskGroup.query",
            """[{"cityCode":"$CITY_CODE","groupId":"$groupId"}]"""
        )
    }

    /**
     * @brief 完成用户任务
     * 
     * @param bizType 业务类型
     * @param taskId 任务ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.sports.userTask.complete
     */
    fun userTaskComplete(bizType: String, taskId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.sports.userTask.complete",
            """[{"bizType":"$bizType","cityCode":"$CITY_CODE","completedTime":${System.currentTimeMillis()},"taskId":"$taskId"}]"""
        )
    }

    /**
     * @brief 领取用户任务权益
     * 
     * @param taskId 任务ID
     * @param userTaskId 用户任务ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.sports.userTaskRights.receive
     */
    fun userTaskRightsReceive(taskId: String, userTaskId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.sports.userTaskRights.receive",
            """[{"taskId":"$taskId","userTaskId":"$userTaskId"}]"""
        )
    }

    /**
     * @brief 查询账户信息
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.user.asset.query.account
     */
    fun queryAccount(): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.user.asset.query.account",
            """[{"accountType":"TIYU_SEED"}]"""
        )
    }

    /**
     * @brief 查询赛轮列表
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.wenti.walk.queryRoundList
     */
    fun queryRoundList(): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.wenti.walk.queryRoundList",
            """[{}]"""
        )
    }

    /**
     * @brief 参与比赛
     * 
     * @param bettingPoints 下注积分
     * @param instanceId 实例ID
     * @param resultId 结果ID
     * @param roundId 轮次ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.wenti.walk.participate
     */
    fun participate(bettingPoints: Int, instanceId: String, resultId: String, roundId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.wenti.walk.participate",
            """[{"bettingPoints":$bettingPoints,"guessInstanceId":"$instanceId","guessResultId":"$resultId","newParticipant":false,"roundId":"$roundId","stepTimeZone":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 查询路线功能特性
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.feature.query
     */
    fun pathFeatureQuery(): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.feature.query",
            """[{"appId":"$APP_ID","features":["USER_CURRENT_PATH_SIMPLE"],"sceneCode":"wenti_shijiebei"}]"""
        )
    }

    /**
     * @brief 加入路线地图
     * 
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.map.join
     */
    fun pathMapJoin(pathId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.map.join",
            """[{"appId":"$APP_ID","pathId":"$pathId"}]"""
        )
    }

    /**
     * @brief 查询路线地图首页
     * 
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.map.homepage
     */
    fun pathMapHomepage(pathId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.map.homepage",
            """[{"appId":"$APP_ID","pathId":"$pathId"}]"""
        )
    }

    /**
     * @brief 查询步数
     * 
     * @param countDate 统计日期
     * @param pathId 路线ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.map.step.query
     */
    fun stepQuery(countDate: String, pathId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.map.step.query",
            """[{"appId":"$APP_ID","countDate":"$countDate","pathId":"$pathId","timeZone":"$TIME_ZONE"}]"""
        )
    }

    /**
     * @brief 行走路线（文体中心版本）
     * 
     * @param countDate 统计日期
     * @param goStepCount 行走步数
     * @param pathId 路线ID
     * @param userPathRecordId 用户路线记录ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.map.go
     */
    fun tiyubizGo(countDate: String, goStepCount: Int, pathId: String, userPathRecordId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.map.go",
            """[{"appId":"$APP_ID","countDate":"$countDate","goStepCount":$goStepCount,"pathId":"$pathId","timeZone":"$TIME_ZONE","userPathRecordId":"$userPathRecordId"}]"""
        )
    }

    /**
     * @brief 领取路线奖励（文体中心版本）
     * 
     * @param pathId 路线ID
     * @param userPathRewardId 用户路线奖励ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.tiyubiz.path.map.reward.receive
     */
    fun rewardReceive(pathId: String, userPathRewardId: String): String {
        return RequestManager.requestString(
            "alipay.tiyubiz.path.map.reward.receive",
            """[{"appId":"$APP_ID","pathId":"$pathId","userPathRewardId":"$userPathRewardId"}]"""
        )
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 抢好友大战模块
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 查询抢好友俱乐部首页
     * 
     * @details 获取抢好友大战中的俱乐部首页数据。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.home.queryClubHome
     */
    fun queryClubHome(): String {
        val args = """[{"apiVersion":"energy","chInfo":"healthstep","timeZone":"$TIME_ZONE"}]"""
        return RequestManager.requestString("alipay.antsports.club.home.queryClubHome", args)
    }

    /**
     * @brief 查询训练项目
     * 
     * @details 获取可用的训练项目列表。
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.train.queryTrainItem
     */
    fun queryTrainItem(): String {
        val args = """[{"apiVersion":"energy","chInfo":"healthstep"}]"""
        return RequestManager.requestString("alipay.antsports.club.train.queryTrainItem", args)
    }

    /**
     * @brief 训练好友
     * 
     * @param bizId 业务ID
     * @param itemType 训练项目类型（如 "skate"）
     * @param memberId 成员ID
     * @param originBossId 原老板ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.train.trainMember
     */
    fun trainMember(bizId: String, itemType: String, memberId: String, originBossId: String): String {
        val args = """[{"apiVersion":"energy","bizId":"$bizId","chInfo":"healthstep","itemType":"$itemType","memberId":"$memberId","originBossId":"$originBossId"}]"""
        return RequestManager.requestString("alipay.antsports.club.train.trainMember", args)
    }

    /**
     * @brief 查询成员价格排行
     * 
     * @param coinBalance 当前能量余额
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.ranking.queryMemberPriceRanking
     */
    fun queryMemberPriceRanking(coinBalance: Int): String {
        val args = """[{"apiVersion":"energy","buyMember":true,"chInfo":"healthstep","coinBalance":$coinBalance}]"""
        return RequestManager.requestString("alipay.antsports.club.ranking.queryMemberPriceRanking", args)
    }

    /**
     * @brief 查询俱乐部成员详情
     * 
     * @param memberId 成员ID
     * @param originBossId 原老板ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.trade.queryClubMember
     */
    fun queryClubMember(memberId: String, originBossId: String): String {
        val args = """[{"apiVersion":"energy","chInfo":"healthstep","memberId":"$memberId","originBossId":"$originBossId"}]"""
        return RequestManager.requestString("alipay.antsports.club.trade.queryClubMember", args)
    }

    /**
     * @brief 抢购好友
     * 
     * @param currentBossId 当前老板ID
     * @param memberId 成员ID
     * @param originBossId 原老板ID
     * @param priceInfo 价格信息（JSON字符串）
     * @param roomId 房间ID
     * 
     * @return RPC调用结果的 JSON 字符串
     * 
     * @remark 对应API：alipay.antsports.club.trade.buyMember
     */
    fun buyMember(currentBossId: String, memberId: String, originBossId: String, priceInfo: String, roomId: String): String {
        val requestData = """[{"apiVersion":"energy","chInfo":"healthstep","currentBossId":"$currentBossId","memberId":"$memberId","originBossId":"$originBossId","priceInfo":$priceInfo,"roomId":"$roomId"}]"""
        return RequestManager.requestString("alipay.antsports.club.trade.buyMember", requestData)
    }

    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 健康岛（Neverland）内部类
    //━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * @brief 健康岛（Neverland）RPC 接口集合
     * 
     * @details 专门用于健康岛任务系统的 RPC 接口组合。
     * 包括签到、任务、泡泡、建造、走路等功能。
     */
    object NeverlandRpcCall {

        /**
         * @brief 查询签到状态
         * 
         * @param signType 签到类型（健康岛固定为 3）
         * @param source 来源标识（固定为 "jkdsportcard"）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.querySign
         */
        fun querySign(signType: Int, source: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.querySign",
                """[{"signType":$signType,"source":"$source"}]"""
            )
        }

        /**
         * @brief 执行签到
         * 
         * @param signType 签到类型（健康岛固定为 3）
         * @param source 来源标识（固定为 "jkdsportcard"）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.takeSign
         */
        fun takeSign(signType: Int, source: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.takeSign",
                """[{"signType":$signType,"source":"$source"}]"""
            )
        }

        /**
         * @brief 查询泡泡任务列表
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryBubbleTask
         */
        fun queryBubbleTask(): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryBubbleTask",
                """[{"source":"jkdsportcard","sportsAuthed":true}]"""
            )
        }

        /**
         * @brief 领取泡泡能量
         *
         * @param ids 泡泡记录ID列表
         *
         * @return RPC调用结果的 JSON 字符串
         *
         * @remark 对应API：com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy
         */
        fun pickBubbleTaskEnergy(ids: List<String>): String {
            val obj = JSONObject().apply {
                put("medEnergyBallInfoRecordIds", JSONArray().apply {
                    ids.forEach { put(it) }
                })
                put("pickAllEnergyBall", true)
                put("source", "jkdsportcard")
            }
            val arr = JSONArray().put(obj)
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.pickBubbleTaskEnergy",
                arr.toString()
            )
        }

        /**
         * @brief 查询任务中心
         * 
         * @details 获取健康岛任务大厅的任务列表。
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryTaskCenter
         */
        fun queryTaskCenter(): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryTaskCenter",
                """[{"apDid":"6b30jO17Z6Wbr2ggRytFxB09hZdhixfSekjytgi9Ytc=","cityCode":"","deviceLevel":"high","newGame":0,"source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 查询指定类型的任务信息
         * 
         * @param source 来源标识（如 "health-island"）
         * @param type 任务类型（如 "LIGHT_FEEDS_TASK"）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryTaskInfo
         */
        fun queryTaskInfo(source: String, type: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryTaskInfo",
                """[{"source":"$source","type":"$type"}]"""
            )
        }

        /**
         * @brief 领取能量任务奖励
         *
         * @param encryptValue 任务加密值
         * @param energyNum 能量数量
         * @param type 任务类型
         * @param lightTaskId 轻任务ID（可选）
         *
         * @return RPC调用结果的 JSON 字符串
         *
         * @remark 对应API：com.alipay.neverland.biz.rpc.energyReceive
         */
        fun energyReceive(encryptValue: String, energyNum: Int, type: String, lightTaskId: String?): String {
            val obj = JSONObject().apply {
                put("encryptValue", encryptValue)
                put("energyNum", energyNum)
                put("source", "jkdsportcard")
                put("type", type)
                if (!lightTaskId.isNullOrEmpty()) {
                    put("lightTaskId", lightTaskId)
                }
            }
            val arr = JSONArray().put(obj)
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.energyReceive",
                arr.toString()
            )
        }

        /**
         * @brief 提交任务
         * 
         * @param taskObj 任务对象（JSONObject）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.taskSend
         */
        fun taskSend(taskObj: JSONObject): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.taskSend",
                """[${taskObj}]"""
            )
        }

        /**
         * @brief 领取任务
         * 
         * @param taskObj 任务对象（JSONObject）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.taskReceive
         */
        fun taskReceive(taskObj: JSONObject): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.taskReceive",
                """[${taskObj}]"""
            )
        }

        /**
         * @brief 完成广告任务
         * 
         * @param bizId 业务ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.adtask.biz.mobilegw.service.task.finish
         */
        fun finish(bizId: String): String {
            return RequestManager.requestString(
                "com.alipay.adtask.biz.mobilegw.service.task.finish",
                """[{"bizId":"$bizId"}]"""
            )
        }

        /**
         * @brief 查询地图列表
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryMapList
         */
        fun queryMapList(): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryMapList",
                """[{"source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 查询地图信息（旧版）
         * 
         * @param mapId 地图ID
         * @param branchId 分支ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @throws JSONException JSON解析异常
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryMapInfo
         */
        @Throws(JSONException::class)
        fun queryMapInfo(mapId: String, branchId: String): String {
            val obj = JSONObject()
            obj.put("branchId", branchId)
            obj.put("drilling", false)
            obj.put("mapId", mapId)
            obj.put("source", "jkdsportcard")

            val arr = JSONArray()
            arr.put(obj)
            return RequestManager.requestString("com.alipay.neverland.biz.rpc.queryMapInfo", arr.toString())
        }

        /**
         * @brief 查询地图信息（新版）
         * 
         * @param mapId 地图ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryMapInfoNew
         */
        fun queryMapInfoNew(mapId: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryMapInfoNew",
                """[{"mapId":"$mapId","source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 查询基础信息
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryBaseinfo
         */
        fun queryBaseinfo(): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryBaseinfo",
                """[{"source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 建造建筑
         * 
         * @param branchId 分支ID
         * @param mapId 地图ID
         * @param multiNum 建造倍数（1-10）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.build
         */
        fun build(branchId: String, mapId: String, multiNum: Int): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.build",
                """[{"branchId":"$branchId","mapId":"$mapId","multiNum":$multiNum,"source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 查询地图详情
         * 
         * @param mapId 地图ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryMapDetail
         */
        fun queryMapDetail(mapId: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryMapDetail",
                """[{"mapId":"$mapId","source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 领取地图关卡奖励
         * 
         * @param branchId 分支ID
         * @param level 关卡等级
         * @param mapId 地图ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.mapStageReward
         */
        fun mapStageReward(branchId: String, level: Int, mapId: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.mapStageReward",
                """[{"branchId":"$branchId","level":$level,"mapId":"$mapId","source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 选择奖励
         * 
         * @param branchId 分支ID
         * @param mapId 地图ID
         * @param rewardId 奖励ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.mapChooseReward
         */
        fun chooseReward(branchId: String, mapId: String, rewardId: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.mapChooseReward",
                """[{"branchId":"$branchId","channel":"jkdsportcard","mapId":"$mapId","rewardId":"$rewardId","source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 选择地图
         * 
         * @param branchId 分支ID
         * @param mapId 地图ID
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.mapChooseFree
         */
        fun chooseMap(branchId: String, mapId: String): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.mapChooseFree",
                """[{"branchId":"$branchId","mapId":"$mapId","source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 行走地格
         * 
         * @param branchId 分支ID
         * @param mapId 地图ID
         * @param drilling 是否钻探（通常为 false）
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.walkGrid
         */
        fun walkGrid(branchId: String, mapId: String, drilling: Boolean): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.walkGrid",
                """[{"branchId":"$branchId","mapId":"$mapId","drilling":$drilling,"source":"jkdsportcard"}]"""
            )
        }

        /**
         * @brief 查询用户能量
         * 
         * @return RPC调用结果的 JSON 字符串
         * 
         * @remark 对应API：com.alipay.neverland.biz.rpc.queryUserAccount
         */
        fun queryUserEnergy(): String {
            return RequestManager.requestString(
                "com.alipay.neverland.biz.rpc.queryUserAccount",
                """[{"source":"jkdsportcard"}]"""
            )
        }
    }
}

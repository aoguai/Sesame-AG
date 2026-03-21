package io.github.aoguai.sesameag.hook.rpc.debug

import io.github.aoguai.sesameag.hook.RequestManager

/**
 * 调试RPC调用工具类
 */
object DebugRpcCall {
    private const val VERSION = "2.0"

    @JvmStatic
    fun queryBaseinfo(): String? = RequestManager.requestString(
        "com.alipay.neverland.biz.rpc.queryBaseinfo",
        "[{\"branchId\":\"WUFU\",\"source\":\"fuqiTown\"}]"
    )

    /** 行走格子 */
    @JvmStatic
    fun walkGrid(): String? = RequestManager.requestString(
        "com.alipay.neverland.biz.rpc.walkGrid",
        "[{\"drilling\":false,\"mapId\":\"MF1\",\"source\":\"fuqiTown\"}]"
    )

    /** 小游戏 */
    @JvmStatic
    fun miniGameFinish(gameId: String, gameKey: String): String? = RequestManager.requestString(
        "com.alipay.neverland.biz.rpc.miniGameFinish",
        "[{\"gameId\":\"$gameId\",\"gameKey\":\"$gameKey\",\"mapId\":\"MF1\",\"score\":490,\"source\":\"fuqiTown\"}]"
    )

    @JvmStatic
    fun taskFinish(bizId: String): String? = RequestManager.requestString(
        "com.alipay.adtask.biz.mobilegw.service.task.finish",
        "[{\"bizId\":\"$bizId\"}]"
    )

    @JvmStatic
    fun queryAdFinished(bizId: String, scene: String): String? = RequestManager.requestString(
        "com.alipay.neverland.biz.rpc.queryAdFinished",
        "[{\"adBizNo\":\"$bizId\",\"scene\":\"$scene\",\"source\":\"fuqiTown\"}]"
    )

    @JvmStatic
    fun queryWufuTaskHall(): String? = RequestManager.requestString(
        "com.alipay.neverland.biz.rpc.queryWufuTaskHall",
        "[{\"source\":\"fuqiTown\"}]"
    )

    @JvmStatic
    fun fuQiTaskQuery(): String? = RequestManager.requestString(
        "com.alipay.wufudragonprod.biz.wufu2024.fuQiTown.fuQiTask.query",
        "[{}]"
    )

    @JvmStatic
    fun fuQiTaskTrigger(appletId: String, stageCode: String): String? = RequestManager.requestString(
        "com.alipay.wufudragonprod.biz.wufu2024.fuQiTown.fuQiTask.trigger",
        "[{\"appletId\":\"$appletId\",\"stageCode\":\"$stageCode\"}]"
    )

    @JvmStatic
    fun queryEnvironmentCertDetailList(alias: String, pageNum: Int, targetUserID: String): String? = 
        RequestManager.requestString(
            "alipay.antforest.forest.h5.queryEnvironmentCertDetailList",
            "[{\"alias\":\"$alias\",\"certId\":\"\",\"pageNum\":$pageNum," +
            "\"shareId\":\"\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"," +
            "\"targetUserID\":\"$targetUserID\",\"version\":\"20230701\"}]"
        )

    @JvmStatic
    fun sendTree(certificateId: String, friendUserId: String): String? = RequestManager.requestString(
        "alipay.antforest.forest.h5.sendTree",
        "[{\"blessWords\":\"梭梭没有叶子，四季常青，从不掉发，祝你发量如梭。\"," +
        "\"certificateId\":\"$certificateId\",\"friendUserId\":\"$friendUserId\"," +
        "\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]"
    )
}


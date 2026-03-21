package io.github.aoguai.sesameag.task.antDodo

import io.github.aoguai.sesameag.hook.RequestManager
import io.github.aoguai.sesameag.util.RandomUtil

/**
 * 神奇物种RPC调用
 */
object AntDodoRpcCall {

    /**
     * 查询动物状态
     */
    @JvmStatic
    fun queryAnimalStatus(): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.queryAnimalStatus",
            "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]"
        )
    }

    /**
     * 神奇物种首页
     */
    @JvmStatic
    fun homePage(): String {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.homePage", "[{}]")
    }

    /**
     * 任务入口
     */
    @JvmStatic
    fun taskEntrance(): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.taskEntrance",
            "[{\"statusList\":[\"TODO\",\"FINISHED\"]}]"
        )
    }

    /**
     * 收集
     */
    @JvmStatic
    fun collect(): String {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.collect", "[{}]")
    }

    /**
     * 任务列表
     */
    @JvmStatic
    fun taskList(): String {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.taskList", "[{}]")
    }

    /**
     * 完成任务
     *
     * @param sceneCode 场景代码
     * @param taskType 任务类型
     */
    @JvmStatic
    fun finishTask(sceneCode: String, taskType: String): String {
        val uniqueId = getUniqueId()
        return RequestManager.requestString(
            "com.alipay.antiep.finishTask",
            "[{\"outBizNo\":\"$uniqueId\",\"requestType\":\"rpc\",\"sceneCode\":\"$sceneCode\",\"source\":\"af-biodiversity\",\"taskType\":\"$taskType\",\"uniqueId\":\"$uniqueId\"}]"
        )
    }

    /**
     * 生成唯一ID
     */
    private fun getUniqueId(): String {
        return "${System.currentTimeMillis()}${RandomUtil.nextLong()}"
    }

    /**
     * 领取任务奖励
     *
     * @param sceneCode 场景代码
     * @param taskType 任务类型
     */
    @JvmStatic
    fun receiveTaskAward(sceneCode: String, taskType: String): String {
        return RequestManager.requestString(
            "com.alipay.antiep.receiveTaskAward",
            "[{\"ignoreLimit\":0,\"requestType\":\"rpc\",\"sceneCode\":\"$sceneCode\",\"source\":\"af-biodiversity\",\"taskType\":\"$taskType\"}]"
        )
    }

    /**
     * 道具列表
     */
    @JvmStatic
    fun propList(): String {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.propList", "[{}]")
    }

    /**
     * 使用道具
     *
     * @param propId 道具ID
     * @param propType 道具类型
     */
    @JvmStatic
    fun consumeProp(propId: String, propType: String): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.consumeProp",
            "[{\"propId\":\"$propId\",\"propType\":\"$propType\"}]"
        )
    }

    /**
     * 查询图鉴信息
     *
     * @param bookId 图鉴ID
     */
    @JvmStatic
    fun queryBookInfo(bookId: String): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.queryBookInfo",
            "[{\"bookId\":\"$bookId\"}]"
        )
    }

    /**
     * 送卡片给好友
     *
     * @param targetAnimalId 目标动物ID
     * @param targetUserId 目标用户ID
     */
    @JvmStatic
    fun social(targetAnimalId: String, targetUserId: String): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.social",
            "[{\"actionCode\":\"GIFT_TO_FRIEND\",\"source\":\"GIFT_TO_FRIEND_FROM_CC\",\"targetAnimalId\":\"$targetAnimalId\",\"targetUserId\":\"$targetUserId\",\"triggerTime\":\"${System.currentTimeMillis()}\"}]"
        )
    }

    /**
     * 查询好友
     */
    @JvmStatic
    fun queryFriend(): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.queryFriend",
            "[{\"sceneCode\":\"EXCHANGE\"}]"
        )
    }

    /**
     * 收集（好友）
     *
     * @param targetUserId 目标用户ID
     */
    @JvmStatic
    fun collect(targetUserId: String): String {
        return RequestManager.requestString(
            "alipay.antdodo.rpc.h5.collect",
            "[{\"targetUserId\":$targetUserId}]"
        )
    }

    /**
     * 查询图鉴列表
     *
     * @param pageSize 每页数量
     * @param pageStart 起始页
     */
    @JvmStatic
    fun queryBookList(pageSize: Int, pageStart: Int): String {
        val args = "[{\"pageSize\":$pageSize,\"pageStart\":\"$pageStart\",\"v2\":\"true\"}]"
        return RequestManager.requestString("alipay.antdodo.rpc.h5.queryBookList", args)
    }

    /**
     * 生成图鉴勋章
     *
     * @param bookId 图鉴ID
     */
    @JvmStatic
    fun generateBookMedal(bookId: String): String {
        val args = "[{\"bookId\":\"$bookId\"}]"
        return RequestManager.requestString("alipay.antdodo.rpc.h5.generateBookMedal", args)
    }
}


package fansirsqi.xposed.sesame.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.RandomUtil;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class AntMemberRpcCall {
    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    /* ant member point */
    public static String queryPointCert(int page, int pageSize) {
        String args1 = "[{\"page\":" + page + ",\"pageSize\":" + pageSize + "}]";
        return RequestManager.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", args1);
    }

    public static String receivePointByUser(String certId) {
        String args1 = "[{\"certId\":" + certId + "}]";
        return RequestManager.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", args1);
    }

    public static String queryMemberSigninCalendar() {
        return RequestManager.requestString("com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar",
                "[{\"autoSignIn\":true,\"invitorUserId\":\"\",\"sceneCode\":\"QUERY\"}]");
    }

    /* 商家开门打卡任务 */
    public static String signIn(String activityNo) {
        return RequestManager.requestString("alipay.merchant.kmdk.signIn",
                "[{\"activityNo\":\"" + activityNo + "\"}]");
    }

    public static String signUp(String activityNo) {
        return RequestManager.requestString("alipay.merchant.kmdk.signUp",
                "[{\"activityNo\":\"" + activityNo + "\"}]");
    }

    /* 商家服务 */
    public static String transcodeCheck() {
        return RequestManager.requestString("alipay.mrchservbase.mrchbusiness.sign.transcode.check",
                "[{}]");
    }

    public static String merchantSign() {
        return RequestManager.requestString("alipay.mrchservbase.mrchpoint.sqyj.homepage.signin.v1",
                "[{}]");
    }

    public static String taskListQuery() {
        return RequestManager.requestString("alipay.mrchservbase.task.more.query",
                "[{\"paramMap\":{\"platform\":\"Android\"},\"taskItemCode\":\"\"}]");
    }

    public static String queryActivity() {
        return RequestManager.requestString("alipay.merchant.kmdk.query.activity",
                "[{\"scene\":\"activityCenter\"}]");
    }

    /* 商家服务任务 */
    public static String taskFinish(String bizId) {
        return RequestManager.requestString("com.alipay.adtask.biz.mobilegw.service.task.finish",
                "[{\"bizId\":\"" + bizId + "\"}]");
    }

    public static String taskReceive(String taskCode) {
        return RequestManager.requestString("alipay.mrchservbase.sqyj.task.receive",
                "[{\"compId\":\"ZTS_TASK_RECEIVE\",\"extInfo\":{\"taskCode\":\"" + taskCode + "\"}}]");
    }

    public static String actioncode(String actionCode) {
        return RequestManager.requestString("alipay.mrchservbase.task.query.by.actioncode",
                "[{\"actionCode\":\"" + actionCode + "\"}]");
    }

    public static String produce(String actionCode) {
        return RequestManager.requestString("alipay.mrchservbase.biz.task.action.produce",
                "[{\"actionCode\":\"" + actionCode + "\"}]");
    }

    public static String ballReceive(String ballIds) {
        return RequestManager.requestString("alipay.mrchservbase.mrchpoint.ball.receive",
                "[{\"ballIds\":[\"" + ballIds
                        + "\"],\"channel\":\"MRCH_SELF\",\"outBizNo\":\"" + getUniqueId() + "\"}]");
    }

    public static String executeTask(String bizParam, String bizSubType, String bizType, Long taskConfigId) {
        return RequestManager.requestString("alipay.antmember.biz.rpc.membertask.h5.executeTask",
                "[{\"bizOutNo\":\"" + TimeUtil.getFormatDate().replaceAll("-", "") +
                        "\",\"bizParam\":\"" + bizParam + "\",\"bizSubType\":\"" + bizSubType + "\",\"bizType\":\"" + bizType +
                        "\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"}" +
                        ",\"syncProcess\":true,\"taskConfigId\":\"" + taskConfigId + "\"}]");
    }

    public static String queryAllStatusTaskList() {
        return RequestManager.requestString("alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList",
                "[{\"sourceBusiness\":\"signInAd\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"}}]");
    }

    /**
     * 游戏中心签到查询
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall
     */
    public static String querySignInBall() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall",
                "[{\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }

    /**
     * 游戏中心签到
     * 对应: com.alipay.gamecenteruprod.biz.rpc.continueSignIn
     */
    public static String continueSignIn() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.continueSignIn",
                "[{\"sceneId\":\"GAME_CENTER\",\"signType\":\"NORMAL_SIGN\",\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }

    /**
     * 游戏中心任务列表
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList
     */
    public static String queryGameCenterTaskList() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList",
                "[{\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }

    /**
     * 游戏中心查询待领取乐豆列表
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList
     */
    public static String queryPointBallList() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList",
                "[{\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }

    /**
     * 游戏中心全部领取
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall
     */
    public static String batchReceivePointBall() {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall",
                "[{}]");
    }

    /**
     * 游戏中心普通平台任务完成（如貔貅任务）
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend
     */
    public static String doTaskSend(String taskId) {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend",
                "[{\"taskId\":\"" + taskId + "\"}]");
    }

    /**
     * 游戏中心签到类平台任务完成（needSignUp = true）
     * 对应: com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup
     */
    public static String doTaskSignup(String taskId) {
        return RequestManager.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup",
                "[{\"source\":\"ch_alipaysearch__chsub_normal\",\"taskId\":\"" + taskId + "\"}]");
    }

    /**
     * 芝麻信用首页
     */
    public static String queryHome() {
        return RequestManager.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV7RpcManager.queryHome",
                "[{\"invokeSource\":\"zmHome\",\"miniZmGrayInside\":\"\",\"version\":\"week\"}]");
    }

    /**
     * 芝麻信用首页 - 服务卡片（含芝麻粒签到卡片）
     * 对应: com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryServiceCard
     */
    public static String queryServiceCard() {
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryServiceCard",
                "[{}]");
    }

    /**
     * 芝麻签到 - 通用完成接口（芝麻粒/炼金等）
     * 对应: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask
     * @param checkInDate yyyyMMdd
     * @param sceneCode   "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
     */
    public static String zmCheckInCompleteTask(String checkInDate, String sceneCode) {
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask",
                "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"" + sceneCode + "\"}]");
    }

    /**
     * 获取芝麻信用任务列表
     */
    public static String queryAvailableSesameTask() {
        return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{}]");
    }

    /**
     * 芝麻信用领取任务
     */
    public static String joinSesameTask(String taskTemplateId) {
        return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity",
                "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"templateId\":\"" + taskTemplateId + "\"}]");
    }

    /**
     * 芝麻信用获取任务回调
     */
    public static String feedBackSesameTask(String taskTemplateId) {
        return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
                "[{\"actionType\":\"TO_COMPLETE\",\"templateId\":\"" + taskTemplateId + "\"}]",
                "zmmemberop", "taskFeedback", "CreditAccumulateStrategyRpcManager");
    }

    /**
     * 芝麻信用完成任务
     */
    public static String finishSesameTask(String recordId) {
        return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity",
                "[{\"recordId\":\"" + recordId + "\"}]");
    }

    /**
     * 查询可收取的芝麻粒
     */
    public static String queryCreditFeedback() {
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback",
                "[{\"queryPotential\":false,\"size\":20,\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 一键收取芝麻粒
     */
    public static String collectAllCreditFeedback() {
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
                "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 收取芝麻粒
     *
     * @param creditFeedbackId creditFeedbackId
     */
    public static String collectCreditFeedback(String creditFeedbackId) {
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback",
                "[{\"collectAll\":false,\"creditFeedbackId\":\"" + creditFeedbackId + "\",\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 获取所有可领取的保障金
     */
    public static String queryAvailableCollectInsuredGold() {
        return RequestManager.requestString("com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList",
                "[{\"entrance\":\"wealth_entry\",\"eventToWaitParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"helpChildParamDTO\":{\"giftProdCode\":\"GIFT_HEALTH_GOLD_CHILD\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"priorityChannelParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]},\"signInParamDTO\":{\"giftProdCode\":\"GIFT_UNIVERSAL_COVERAGE\",\"rightNoList\":[\"UNIVERSAL_ACCIDENT\",\"UNIVERSAL_HOSPITAL\",\"UNIVERSAL_OUTPATIENT\",\"UNIVERSAL_SERIOUSNESS\",\"UNIVERSAL_WEALTH\",\"UNIVERSAL_TRANS\",\"UNIVERSAL_FRAUD_LIABILITY\"]}}]",
                "insgiftbff", "queryMultiSceneWaitToGainList", "insgiftMain");
    }

    /**
     * 领取保障金
     */
    public static String collectInsuredGold(JSONObject goldBallObj) {
        return RequestManager.requestString("com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured",
                goldBallObj.toString(), "insgiftbff", "gainMyAndFamilySumInsured", "insgiftMain");
    }

    // 安心豆
    public static String querySignInProcess(String appletId, String scene) {
        return RequestManager.requestString("com.alipay.insmarketingbff.bean.querySignInProcess",
                "[{\"appletId\":\"" + appletId + "\",\"scene\":\"" + scene + "\"}]");
    }

    public static String signInTrigger(String appletId, String scene) {
        return RequestManager.requestString("com.alipay.insmarketingbff.bean.signInTrigger",
                "[{\"appletId\":\"" + appletId + "\",\"scene\":\"" + scene + "\"}]");
    }

    public static String beanExchangeDetail(String itemId) {
        return RequestManager.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\"},"
                        + "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchangeDetail\"}]");
    }

    public static String beanExchange(String itemId, int pointAmount) {
        return RequestManager.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\",\"pointAmount\":\"" + Integer.toString(pointAmount) + "\"},"
                        + "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchange\"}]");
    }

    public static String queryUserAccountInfo(String pointProdCode) {
        return RequestManager.requestString("com.alipay.insmarketingbff.point.queryUserAccountInfo",
                "[{\"channel\":\"HiChat\",\"pointProdCode\":\"" + pointProdCode + "\",\"pointUnitType\":\"COUNT\"}]");
    }

    /**
     * 查询会员信息
     */
    public static String queryMemberInfo() {
        String data = "[{\"needExpirePoint\":true,\"needGrade\":true,\"needPoint\":true,\"queryScene\":\"POINT_EXCHANGE_SCENE\",\"source\":\"POINT_EXCHANGE_SCENE\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}}]";
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.member.h5.queryMemberInfo", data);
    }

    /**
     * 查询0元兑公益道具列表
     *
     * @param userId       userId
     * @param pointBalance 当前可用会员积分
     */
    public static String queryShandieEntityList(String userId, String pointBalance) {
        String uniqueId = System.currentTimeMillis() + userId + "94000SR202501061144200394000SR2025010611458003";
        String data = "[{\"blackIds\":[],\"deliveryIdList\":[\"94000SR2025010611442003\",\"94000SR2025010611458003\"],\"filterCityCode\":false,\"filterPointNoEnough\":false,\"filterStockNoEnough\":false,\"pageNum\":1,\"pageSize\":18,\"point\":" + pointBalance + ",\"previewCopyDbId\":\"\",\"queryType\":\"DELIVERY_ID_LIST\",\"source\":\"member_day\",\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"0yuandui\",\"unid\":\"\"},\"topIds\":[],\"uniqueId\":\"" + uniqueId + "\"}]";
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryShandieEntityList", data);
    }




    public static String queryDeliveryZoneDetail(List<String> deliveryIdList, int pageNum, int pageSize) {
        // 1. 处理 uniqueId 的拼接逻辑
        // 固定前缀：17665547901390and99999999INTELLIGENT_SORT92524974
        String idsJoined = String.join(",", deliveryIdList);
        String uniqueId = "17665547901390and99999999INTELLIGENT_SORT92524974" + idsJoined;

        // 2. 将 deliveryIdList 转换为 JSON 数组字符串格式
        // 这里为了简单直观使用 String.format，如果项目中有 Fastjson/Gson 建议使用序列化
        StringBuilder deliveryIdListJson = new StringBuilder("[");
        for (int i = 0; i < deliveryIdList.size(); i++) {
            deliveryIdListJson.append("\"").append(deliveryIdList.get(i)).append("\"");
            if (i < deliveryIdList.size() - 1) {
                deliveryIdListJson.append(",");
            }
        }
        deliveryIdListJson.append("]");

        // 3. 构造完整的请求 Data 字符串
        String data = "[{" +
                "\"deliveryIdList\":" + deliveryIdListJson.toString() + "," +
                "\"lowerPoint\":0," +
                "\"pageNum\":" + pageNum + "," +
                "\"pageSize\":" + pageSize + "," +
                "\"queryNoReserve\":true," +
                "\"resourceCardChannel\":\"ZERO_EXCHANGE_CHANNEL\"," +
                "\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"\",\"unid\":\"\"}," +
                "\"startPageFirstQuery\":false," +
                "\"topIdList\":[\"202412231259661040\"]," +
                "\"uniqueId\":\"" + uniqueId + "\"," +
                "\"upperPoint\":99999999," +
                "\"withPointRange\":false" +
                "}]";

        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", data);
    }


    /**
     * 会员积分兑换道具（适配新请求结构）
     *
     * @param benefitId benefitId（兑换权益ID）
     * @param userId    userId（用户ID，格式如）
     * @return 接口请求结果
     */
    /*
    public static String exchangeBenefit(String benefitId, String userId) {
        // 1. 生成请求ID（前缀+当前毫秒时间戳）
        String requestId = "requestId" + System.currentTimeMillis();
        // 2. 生成唯一unid（UUID随机生成，也可使用时间戳，此处UUID更规范）
        String unid = UUID.randomUUID().toString();
        // 3. 拼接requestSourceInfo（用户ID+|0+当前毫秒时间戳）
        String requestSourceInfo = String.format("SID:%s%s|0", userId, System.currentTimeMillis());
        // 4. 构建符合新结构的请求体（移除废弃字段，新增sceneId等必填字段）
        String data = String.format("[{\"benefitId\":\"%s\",\"exchangeType\":\"POINT_PAY\",\"requestId\":\"%s\",\"requestSourceInfo\":\"%s\",\"sceneId\":\"1209\",\"sourcePassMap\":{\"bid\":\"\",\"feedsIndex\":\"0\",\"innerSource\":\"a169.b52659\",\"isCpc\":\"\",\"source\":\"\",\"unid\":\"%s\",\"uniqueId\":\"%s%s\"}]",
                benefitId,
                requestId,
                requestSourceInfo,
                unid,
                userId,
                System.currentTimeMillis());
        // 5. 发起接口请求并返回结果
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", data);
    }*/

    public static String exchangeBenefit(String benefitId, String itemId, String userId) {
        long now = System.currentTimeMillis();

        // 1. 生成请求ID
        String requestId = "requestId" + now;

        // 2. 生成唯一unid (UUID)
        String unid = UUID.randomUUID().toString();

        // 3. 生成 uniqueId (通常是 userId + 时间戳，或者直接是 userId)
        // 根据你提供的 JSON，这里似乎直接是 userId 拼接了一个标记或时间戳
        String uniqueId = userId + now;

        // 4. 拼接 requestSourceInfo
        String requestSourceInfo = String.format("SID:%s|0", uniqueId);

        // 5. 构建符合最新结构的 JSON 数据
        // 注意：增加了 itemId, cityCode, miniAppId 等字段
        String data = String.format("[" +
                        "{" +
                        "\"benefitId\":\"%s\"," +
                        "\"cityCode\":\"\"," +
                        "\"exchangeType\":\"POINT_PAY\"," +
                        "\"itemId\":\"%s\"," +
                        "\"miniAppId\":\"\"," +
                        "\"orderSource\":\"\"," +
                        "\"requestId\":\"%s\"," +
                        "\"requestSourceInfo\":\"%s\"," +
                        "\"sourcePassMap\":{" +
                        "\"alipayClientVersion\":\"10.7.80.8000\"," +
                        "\"bid\":\"\"," +
                        "\"feedsIndex\":\"0\"," +
                        "\"innerSource\":\"a159.b52659\"," +
                        "\"isCpc\":\"\"," +
                        "\"mobileOsType\":\"Android\"," +
                        "\"source\":\"\"," +
                        "\"unid\":\"%s\"," +
                        "\"uniqueId\":\"%s\"" +
                        "}," +
                        "\"userOutAccount\":\"\"" +
                        "}" +
                        "]",
                benefitId,
                itemId,
                requestId,
                requestSourceInfo,
                unid,
                uniqueId);

        // 6. 发起接口请求
        return RequestManager.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", data);
    }

    /**
     * 查询芝麻粒兑换商品列表
     * 对应接口: com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryListV2
     *
     * @param page     页码
     * @param pageSize 每页数量
     */
    public static String queryExchangeList(int page, int pageSize) {
        // 参数构造参考抓包: [{"currentPage":1,"formDelivery":"false","pageSize":20,"privilegeSource":"","privilegeTab":"","tabList":[]}]
        String args = "[{\"currentPage\":" + page + ",\"formDelivery\":\"false\",\"pageSize\":" + pageSize + ",\"privilegeSource\":\"\",\"privilegeTab\":\"\",\"tabList\":[]}]";
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryListV2",
                args
        );
    }

    /**
     * 执行芝麻粒兑换
     * 对应接口: com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.obtainAward
     *
     * @param templateId 商品ID (awardTemplateId)
     */
    public static String obtainAward(String templateId) {
        // 参数构造参考抓包: [{"awardTemplateId":"245213012"}]
        String args = "[{\"awardTemplateId\":\"" + templateId + "\"}]";
        return RequestManager.requestString(
                "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.obtainAward",
                args
        );
    }

    // ================= 年度回顾（任务中心） =================
    public static final String ANNUAL_REVIEW_OPERATION_IDENTIFY =
            "independent_component_program2025111803036407";
    public static final String ANNUAL_REVIEW_COMPONENT_PREFIX =
            "independent_component_task_reward_v2_02888775";
    public static final String ANNUAL_REVIEW_QUERY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_query";
    public static final String ANNUAL_REVIEW_APPLY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_apply";
    public static final String ANNUAL_REVIEW_PROCESS_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_process";
    public static final String ANNUAL_REVIEW_Get =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_process";
    public static final String ANNUAL_REVIEW_GET_REWARD_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_get_reward";

    private static JSONObject buildAnnualReviewBasePayload() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("channel", "share");
        root.put("cityCode", "110000");
        root.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
        // 默认 source 为查询组件，具体请求中可覆盖
        root.put("source", ANNUAL_REVIEW_QUERY_COMPONENT);
        return root;
    }

    /**
     * 年度回顾 - 查询任务列表
     *
     * 对应文档示例：components 中携带
     *   independent_component_task_reward_v2_02888775_independent_component_task_reward_query
     */
    public static String annualReviewQueryTasks() {
        try {
            JSONObject body = buildAnnualReviewBasePayload();
            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_QUERY_COMPONENT, new JSONObject());
            body.put("components", components);
            body.put("source", ANNUAL_REVIEW_QUERY_COMPONENT);

            return RequestManager.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取单个任务（apply）
     *
     * 请求示例参见文档：components 中携带
     *   independent_component_task_reward_v2_02888775_independent_component_task_reward_apply
     */
    public static String annualReviewApplyTask(String code) {
        try {
            JSONObject body = buildAnnualReviewBasePayload();

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_APPLY_COMPONENT, compBody);

            body.put("components", components);
            body.put("source", ANNUAL_REVIEW_APPLY_COMPONENT);

            return RequestManager.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 提交任务完成（process）
     *
     * 请求示例参见文档：components 中携带
     *   independent_component_task_reward_v2_02888775_independent_component_task_reward_process
     */
    public static String annualReviewProcessTask(String code, String recordNo) {
        try {
            JSONObject body = buildAnnualReviewBasePayload();

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("recordNo", recordNo);

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_PROCESS_COMPONENT, compBody);

            body.put("components", components);
            body.put("source", ANNUAL_REVIEW_PROCESS_COMPONENT);

            return RequestManager.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取奖励（get_reward）
     *
     * 在任务完成后，根据 code + recordNo 领取成长值奖励。
     */
    public static String annualReviewGetReward(String code, String recordNo) {
        try {
            JSONObject body = buildAnnualReviewBasePayload();

            JSONObject compBody = new JSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("recordNo", recordNo);
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = new JSONObject();
            components.put(ANNUAL_REVIEW_GET_REWARD_COMPONENT, compBody);

            body.put("components", components);
            body.put("source", ANNUAL_REVIEW_GET_REWARD_COMPONENT);

            return RequestManager.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    // ================= 芝麻树 =================
    private static final String ZHIMATREE_PLAY_INFO = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3";
    private static final String ZHIMATREE_REFER = "https://render.alipay.com/p/yuyan/180020010001269849/zmTree.html?caprMode=sync&chInfo=chInfo=ch_zmzltf__chsub_xinyongsyyingxiaowei";

    /**
     * 查询芝麻树首页
     */
    public static String zhimaTreeHomePage() {
        try {
            JSONObject args = new JSONObject();
            args.put("operation", "ZHIMA_TREE_HOME_PAGE");
            args.put("playInfo", ZHIMATREE_PLAY_INFO);
            args.put("refer", ZHIMATREE_REFER);
            args.put("extInfo", new JSONObject());

            return RequestManager.requestString("alipay.promoprod.play.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 净化芝麻树 (消耗净化值)
     */
    public static String zhimaTreeCleanAndPush(String treeCode) {
        try {
            JSONObject args = new JSONObject();
            args.put("operation", "ZHIMA_TREE_CLEAN_AND_PUSH");
            args.put("playInfo", ZHIMATREE_PLAY_INFO);
            args.put("refer", ZHIMATREE_REFER);

            JSONObject extInfo = new JSONObject();
            extInfo.put("clickNum", "1");
            extInfo.put("treeCode", treeCode);

            args.put("extInfo", extInfo);

            return RequestManager.requestString("alipay.promoprod.play.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询做任务赚净化值列表
     */
    public static String queryRentGreenTaskList() {
        try {
            JSONObject args = new JSONObject();
            args.put("operation", "RENT_GREEN_TASK_LIST_QUERY");
            args.put("playInfo", ZHIMATREE_PLAY_INFO);
            args.put("refer", ZHIMATREE_REFER);

            JSONObject extInfo = new JSONObject();
            extInfo.put("chInfo", "ch_share__chsub_ALPContact");
            extInfo.put("batchId", "");
            args.put("extInfo", extInfo);

            return RequestManager.requestString("alipay.promoprod.play.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 完成/领取净化值任务
     * @param stageCode "send" 表示去完成/开始, "receive" 表示领取奖励
     */
    public static String rentGreenTaskFinish(String taskId, String stageCode) {
        try {
            JSONObject args = new JSONObject();
            args.put("operation", "RENT_GREEN_TASK_FINISH");
            args.put("playInfo", ZHIMATREE_PLAY_INFO);
            args.put("refer", ZHIMATREE_REFER);

            JSONObject extInfo = new JSONObject();
            extInfo.put("chInfo", "ch_share__chsub_ALPContact");
            extInfo.put("taskId", taskId);
            extInfo.put("stageCode", stageCode);
            args.put("extInfo", extInfo);

            return RequestManager.requestString("alipay.promoprod.play.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新] 福利中心首页
     */
    public static String queryWelfareHome() {
        try {
            JSONObject args = new JSONObject();
            args.put("isResume", true);
            // 接口: com.alipay.finaggexpbff.needle.welfareCenter.index
            return RequestManager.requestString("com.alipay.finaggexpbff.needle.welfareCenter.index",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新] 任务查询推送
     */
    public static String taskQueryPush(String taskId) {
        try {
            JSONObject args = new JSONObject();
            args.put("mode", 1); // 固定参数
            args.put("taskId", taskId);

            // 接口: com.alipay.wealthgoldtwa.needle.taskQueryPush
            return RequestManager.requestString("com.alipay.wealthgoldtwa.needle.taskQueryPush",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 签到 / 领取奖励
     * @param type "SIGN"
     */
    public static String welfareCenterTrigger(String type) {
        try {
            JSONObject args = new JSONObject();
            args.put("type", type);
            return RequestManager.requestString("com.alipay.finaggexpbff.needle.welfareCenter.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 任务触发/报名
     */
    public static String goldBillTaskTrigger(String taskId) {
        try {
            JSONObject args = new JSONObject();
            args.put("taskId", taskId);
            return RequestManager.requestString("com.alipay.wealthgoldtwa.goldbill.v4.task.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 查询黄金票提取页信息
     * 用于获取最新的可用数量、基金ID (productId) 和 赠送份数 (bonusAmount)
     */
    public static String queryConsumeHome() {
        try {
            JSONObject args = new JSONObject();
            args.put("tabBubbleDeliverParam", new JSONObject());
            args.put("tabTypeDeliverParam", new JSONObject());
            // 接口: com.alipay.wealthgoldtwa.needle.consume.query
            return RequestManager.requestString("com.alipay.wealthgoldtwa.needle.consume.query",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 提交提取黄金
     * @param amount 提取数量 (如 100, 200, 2900)
     * @param productId 基金ID
     * @param bonusAmount 额外赠送数量
     */
    public static String submitConsume(int amount, String productId, int bonusAmount) {
        try {
            JSONObject args = new JSONObject();
            args.put("exchangeAmount", amount);
            // 计算金额：100份 = 0.10元。公式：份数 / 1000.0
            args.put("exchangeMoney", String.format("%.2f", amount / 1000.0));
            args.put("prizeType", "GOLD"); // 固定为黄金
            args.put("productId", productId);
            args.put("bonusAmount", bonusAmount);
            // 接口: com.alipay.wealthgoldtwa.needle.consume.submit
            return RequestManager.requestString("com.alipay.wealthgoldtwa.needle.consume.submit",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * @brief 查询当月是否有可领取的贴纸
     * @param year 年份
     * @param month 月份
     */
    public static String queryStickerCanReceive(String year, String month) {
        String data = "[{" +
                "\"isFirstShow\":\"false\"," +
                "\"month\":\"" + month + "\"," +
                "\"scene\":\"\"," +
                "\"year\":\"" + year + "\"" +
                "}]";
        return RequestManager.requestString("alipay.memberasset.sticker.queryStickerCanReceive", data);
    }

    /**
     * 领取指定的贴纸
     * @param stickerIds 贴纸ID集合
     */
    public static String receiveSticker(String year, String month, List<String> stickerIds) {
        if (stickerIds == null || stickerIds.isEmpty()) return null;

        // 构建 stickerIds 的 JSON 数组字符串
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stickerIds.size(); i++) {
            sb.append("\"").append(stickerIds.get(i)).append("\"");
            if (i < stickerIds.size() - 1) sb.append(",");
        }
        sb.append("]");

        String data = "[{\"month\":\"" + month + "\",\"stickerIds\":" + sb.toString() + ",\"year\":\"" + year + "\"}]";
        return RequestManager.requestString("alipay.memberasset.sticker.receiveSticker", data);
    }


    //芝麻信誉 部分
    public static class Zmxy {
        private static String Version="2025-10-22";
        //芝麻粒炼金

        /**
         * 信誉获取任务列表（成长任务）
         * <p>
         * 对应抓包：
         * <pre>
         *   Method: com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.queryToDoList
         *   requestData: [{"guideBehaviorId":"yuebao_7d","invokeVersion":"1.0.2025.10.27","switchNewPage":true}]
         * </pre>
         * 说明：
         * <ul>
         *   <li>__apiCallStartTime / __apiNativeCallId 属于容器层元数据，不需要拼在 requestData 里</li>
         *   <li>guideBehaviorId 用来指定「引导任务」的入口，通常传 yuebao_7d 即可拉全量列表</li>
         *   <li>invokeVersion 建议保持和抓包一致，方便服务器做灰度控制</li>
         * </ul>
         *
         * @param guideBehaviorId 抓包中的 guideBehaviorId，例如 "yuebao_7d"
         * @param invokeVersion   抓包中的 invokeVersion，例如 "1.0.2025.10.27"
         */
        public static String queryGrowthGuideToDoList(String guideBehaviorId, String invokeVersion) {
            if (guideBehaviorId == null || guideBehaviorId.isEmpty()) {
                guideBehaviorId = "yuebao_7d";
            }
            if (invokeVersion == null || invokeVersion.isEmpty()) {
                // 默认使用抓包中观察到的版本号，避免服务端按版本做限流/灰度
                invokeVersion = "1.0.2025.10.27";
            }
            String data = "[{" +
                    "\"guideBehaviorId\":\"" + guideBehaviorId + "\"," +
                    "\"invokeVersion\":\"" + invokeVersion + "\"," +
                    "\"switchNewPage\":true" +
                    "}]";
            return RequestManager.requestString(
                    "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.queryToDoList",
                    data
            );
        }

        /**
         * 信誉任务「领取任务 / 触发接收」接口。
         * <p>
         * 对应抓包：
         * <pre>
         *   Method: com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.openBehaviorCollect
         *   requestData: [{"behaviorId":"babanongchang_7d"}]
         * </pre>
         * behaviorId 直接来自 queryToDoList 返回的 toDoList[i].behaviorId。
         */
        public static String openBehaviorCollect(String behaviorId) {
            String data = "[{\"behaviorId\":\"" + behaviorId + "\"}]";
            return RequestManager.requestString(
                    "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.openBehaviorCollect",
                    data
            );
        }

        /**
         * 查询每日答题题目（每日问答）。
         *
         * 对应抓包：
         *   Method: com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.queryDailyQuiz
         *   requestData: [{"behaviorId":"meiriwenda"}]
         *
         * @param behaviorId 行为 ID（例如 "meiriwenda"）
         */
        public static String queryDailyQuiz(String behaviorId) {
            String data = "[{\"behaviorId\":\"" + behaviorId + "\"}]";
            return RequestManager.requestString(
                    "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.queryDailyQuiz",
                    data
            );
        }

        /**
         * 提交每日答题结果。
         *
         * 对应抓包：
         *   Method: com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask
         *   requestData: [{
         *       "behaviorId":"meiriwenda",
         *       "bizDate":1764564388751,
         *       "extInfo":{
         *           "answerId":"20250925_3_0",
         *           "answerStatus":"RIGHT",
         *           "questionId":"20250925_3"
         *       }
         *   }]
         *
         * @param behaviorId    行为 ID（meiriwenda）
         * @param bizDate       业务时间戳（直接使用 queryDailyQuiz 返回的 data.bizDate）
         * @param answerId      选中的答案 ID（data.questionVo.rightAnswer.answerId）
         * @param questionId    题目 ID（data.questionVo.questionId）
         * @param answerStatus  答案状态：RIGHT / WRONG
         */
        public static String pushDailyTask(String behaviorId, long bizDate,
                                           String answerId, String questionId,
                                           String answerStatus) {
            if (answerStatus == null || answerStatus.isEmpty()) {
                answerStatus = "RIGHT";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[{\"behaviorId\":\"")
                    .append(behaviorId)
                    .append("\",\"bizDate\":")
                    .append(bizDate)
                    .append(",\"extInfo\":{")
                    .append("\"answerId\":\"").append(answerId).append("\",")
                    .append("\"answerStatus\":\"").append(answerStatus).append("\",")
                    .append("\"questionId\":\"").append(questionId).append("\"")
                    .append("}}]");
            String data = sb.toString();
            return RequestManager.requestString(
                    "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask",
                    data
            );
        }

        /**
         * 提交信用知识视频答题（shipingwenda）
         *
         * 对应抓包：
         *   Method: com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask
         *
         * requestData:
         * [{
         *     "behaviorId": "shipingwenda",
         *     "bizDate": 1765254295706,
         *     "extInfo": {
         *         "answerId": "A",
         *         "answerStatus": "RIGHT",
         *         "questionId": "question3"
         *     }
         * }]
         *
         * @param bizDate      业务时间戳
         * @param answerId     选中的答案 ID
         * @param questionId   题目 ID
         * @param answerStatus RIGHT / WRONG，默认 RIGHT
         */
        public static String pushVideoQuizTask(long bizDate,
                                               String answerId,
                                               String questionId,
                                               String answerStatus) {

            if (answerStatus == null || answerStatus.isEmpty()) {
                answerStatus = "RIGHT";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[{")
                    .append("\"behaviorId\":\"shipingwenda\",")
                    .append("\"bizDate\":").append(bizDate).append(',')
                    .append("\"extInfo\":{")
                    .append("\"answerId\":\"").append(answerId).append("\",")
                    .append("\"answerStatus\":\"").append(answerStatus).append("\",")
                    .append("\"questionId\":\"").append(questionId).append("\"")
                    .append("}")
                    .append("}]");

            String data = sb.toString();

            return RequestManager.requestString(
                    "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask",
                    data
            );
        }

        /**
         * 查询芝麻分进度
         * 接口: com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress
         */
        public static String queryScoreProgress() {
            try {
                JSONObject args = new JSONObject();
                args.put("needTotalProcess", "TRUE");
                args.put("queryGuideInfo", true);
                args.put("switchNewPage", true);

                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress",
                        new JSONArray().put(args).toString()
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * 领取进度球
         * 接口: com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.api.GrowthBehaviorRpcManager.collectProgressBall
         */
        public static String collectProgressBall(JSONArray ballIdList) {
            try {
                JSONObject args = new JSONObject();
                args.put("ballIdList", ballIdList); // 直接用 JSONArray

                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.collectProgressBall",
                        new JSONArray().put(args).toString()
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        public static class Alchemy {
            /**
             * 芝麻炼金/积分首页
             */
            public static String alchemyQueryHome() {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome",
                        "[{}]");
            }

            /**
             * [日志对应] 芝麻炼金-执行炼金
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy
             * Params: [null]
             */
            public static String alchemyExecute() {
                // 日志中 requestData 为 [null]
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", "[{}]");
            }

            /**
             * [日志对应] 芝麻炼 /金-签到列表查询
             *
             *
             *      * @param checkInDate yyyyMMdd
             *      * @param sceneCode   "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
             *      *
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists
             */
            public static String alchemyQueryCheckIn(String scenecode) {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
                        "[{\"sceneCode\":\""+scenecode+"\",\"version\":\""+Version+"\"}]");
            }

            /**
             * [日志对应] 芝麻炼金-时段奖励查询 (午饭/晚饭)
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask
             */
            public static String alchemyQueryTimeLimitedTask() {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask",
                        "[{}]");
            }

            /**
             * [日志对应] 芝麻炼金-完成时段任务 (午饭/晚饭)
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask
             *
             * 请求示例:
             * {
             *     "templateId": "wujianli"
             * }
             *
             * 返回示例:
             * {
             *     "ariverRpcTraceId": "client`aBYSOR/y0xEDACWu2y9mPoqMPhTTaIz_5694806",
             *     "data": {
             *         "degrade": false,
             *         "toast": "领取成功,得10芝麻粒",
             *         "zmlNum": 10
             *     },
             *     "resultCode": "SUCCESS",
             *     "resultView": "成功",
             *     "success": true,
             *     "traceId": "21d0e34417646521077286391ee43a"
             * }
             */
            public static String alchemyCompleteTimeLimitedTask(String templateId) {
                String body = "[{\n" +
                        "    \"templateId\": \"" + templateId + "\"\n" +
                        "}]";

                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
                        body
                );
            }

            /**
             * [日志对应] 芝麻炼金-任务列表 V3 (参数精确匹配日志)
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3
             */
            public static String alchemyQueryListV3() {
                return RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
                        "[{\"chInfo\":\"\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"searchSubscribeTask\":true,\"version\":\"alchemy\"}]");
            }
            /**
             * [日志对应] 芝麻炼金 - 领取奖励
             *
             * Method: com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward
             */
            public static String claimAward() {
                return RequestManager.requestString(
                        "com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward",
                        "[{}]"
                );
            }
        }











    }
}
package fansirsqi.xposed.sesame.task.antForest;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import fansirsqi.xposed.sesame.entity.AlipayVersion;
import fansirsqi.xposed.sesame.entity.RpcEntity;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.RandomUtil;

/** 森林 RPC 调用类 */
public class AntForestRpcCall {
    private static String VERSION = "20250813";

    public static void init() {
        AlipayVersion alipayVersion = ApplicationHook.getAlipayVersion();
        Log.record("AntForestRpcCall", "当前目标应用版本: " + alipayVersion);
        try {
            switch (alipayVersion.getVersionString()) {
                case "10.7.30.8000":
                    VERSION = "20250813"; // 2025年版本
                    break;
                case "10.5.88.8000":
                    VERSION = "20240403"; // 2024年版本
                    break;
                case "10.3.96.8100":
                    VERSION = "20230501"; // 2023年版本
                    break;
                default:
                    VERSION = "20250813";
            }
            Log.record("AntForestRpcCall", "使用API版本: " + VERSION);
        } catch (Exception e) {
            Log.error("AntForestRpcCall", "版本初始化异常，使用默认版本: " + VERSION);
            Log.printStackTrace(e);
        }
    }

    public static String queryFriendsEnergyRanking() {
        try {
            JSONObject arg = new JSONObject();
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("periodType", "total");
            arg.put("rankType", "energyRank");
            arg.put("version", VERSION);
            String param = "[" + arg + "]";
            JSONObject correlationLocal = new JSONObject();
            correlationLocal.put("pathList", new JSONArray().put("friendRanking").put("myself").put("totalDatas"));
            String relationLocal = "[" + correlationLocal + "]";
            return RequestManager.requestString("alipay.antmember.forest.h5.queryEnergyRanking", param, relationLocal);
        } catch (Exception e) {
            return "";
        }
    }

    public static String queryTopEnergyChallengeRanking() {
        try {
            JSONObject arg = new JSONObject();
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            String param = "[" + arg + "]";
            return RequestManager.requestString("alipay.antforest.forest.h5.queryTopEnergyChallengeRanking", param);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
    }

    /** 批量获取好友能量信息（标准版） */
    public static String fillUserRobFlag(JSONArray userIdList) {
        try {
            JSONObject arg = new JSONObject();
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("userIdList", userIdList);
            String param = "[" + arg + "]";
            JSONObject joRelationLocal = new JSONObject();
            joRelationLocal.put("pathList", new JSONArray().put("friendRanking"));
            String relationLocal = "[" + joRelationLocal + "]";
            return RequestManager.requestString("alipay.antforest.forest.h5.fillUserRobFlag", param, relationLocal);
        } catch (Exception e) {
            return "";
        }
    }

    /** 批量获取好友能量信息（增强版 - PK排行榜专用） */
    public static String fillUserRobFlag(JSONArray userIdList, boolean needFillUserInfo) {
        try {
            JSONObject arg = new JSONObject();
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("userIdList", userIdList);
            arg.put("needFillUserInfo", needFillUserInfo);
            String param = "[" + arg + "]";
            return RequestManager.requestString("alipay.antforest.forest.h5.fillUserRobFlag", param);
        } catch (Exception e) {
            return "";
        }
    }

    public static String queryHomePage() throws JSONException {
        JSONObject requestObject = new JSONObject()
                .put("activityParam", new JSONObject())
                .put("configVersionMap", new JSONObject().put("wateringBubbleConfig", "0"))
                .put("skipWhackMole", false)
                .put("source", "chInfo_ch_appcenter__chsub_9patch")
                .put("version", VERSION);
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.queryHomePage",
                new JSONArray().put(requestObject).toString(),
                3,
                1000
        );
    }

    public static String queryDynamicsIndex() {
        try {
            JSONObject arg = new JSONObject();
            arg.put("autoRefresh", false);
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("version", VERSION);
            return RequestManager.requestString(
                    "alipay.antforest.forest.h5.queryDynamicsIndex",
                    new JSONArray().put(arg).toString()
            );
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
    }

    public static String queryFriendHomePage(String userId, String fromAct) {
        try {
            if (fromAct == null) {
                fromAct = "TAKE_LOOK_FRIEND";
            }
            JSONObject arg = new JSONObject();
            JSONObject arg1 = new JSONObject();
            arg1.put("wateringBubbleConfig", "0");
            arg.put("canRobFlags", "T,F,F,F,F");
            arg.put("configVersionMap", arg1);
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("userId", userId);
            arg.put("fromAct", fromAct);
            arg.put("version", VERSION);
            String param = "[" + arg + "]";
            return RequestManager.requestString("alipay.antforest.forest.h5.queryFriendHomePage", param, 3, 1000);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
    }

    /** 找能量方法 - 查找可收取能量的好友（带跳过用户列表） */
    public static String takeLook(JSONObject skipUsers) {
        try {
            JSONObject requestData = new JSONObject();
            requestData.put("contactsStatus", "N");
            requestData.put("exposedUserId", "");
            requestData.put("skipUsers", skipUsers);
            requestData.put("source", "chInfo_ch_appcenter__chsub_9patch");
            requestData.put("takeLookEnd", false);
            requestData.put("takeLookStart", true);
            requestData.put("version", VERSION);
            return RequestManager.requestString("alipay.antforest.forest.h5.takeLook",
                    "[" + requestData + "]");
        } catch (JSONException e) {
            Log.printStackTrace("AntForestRpcCall", "takeLook构建请求参数失败", e);
            return "";
        }
    }

    public static RpcEntity energyRpcEntity(String bizType, String userId, long bubbleId) {
        try {
            JSONObject args = new JSONObject();
            JSONArray bubbleIds = new JSONArray();
            bubbleIds.put(bubbleId);
            args.put("bizType", bizType);
            args.put("bubbleIds", bubbleIds);
            args.put("source", "chInfo_ch_appcenter__chsub_9patch");
            args.put("userId", userId);
            args.put("version", VERSION);
            String param = "[" + args + "]";
            return new RpcEntity("alipay.antmember.forest.h5.collectEnergy", param, null);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return null;
        }
    }

    public static String collectEnergy(String bizType, String userId, Long bubbleId) {
        RpcEntity r = energyRpcEntity(bizType, userId, bubbleId);
        if (r == null) {
            return "";
        }
        return RequestManager.requestString(r);
    }

    public static RpcEntity batchEnergyRpcEntity(String bizType, String userId, List<
            Long> bubbleIds)
            throws JSONException {
        JSONObject arg = new JSONObject();
        arg.put("bizType", bizType);
        arg.put("bubbleIds", new JSONArray(bubbleIds));
        arg.put("fromAct", "BATCH_ROB_ENERGY");
        arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
        arg.put("userId", userId);
        arg.put("version", VERSION);
        String param = "[" + arg + "]";
        return new RpcEntity("alipay.antmember.forest.h5.collectEnergy", param);
    }

    /** 收取复活能量 */
    public static String collectRebornEnergy() {
        try {
            JSONObject arg = new JSONObject();
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            String param = "[" + arg + "]";
            return RequestManager.requestString("alipay.antforest.forest.h5.collectRebornEnergy", param);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
    }

    public static String transferEnergy(String targetUser, String bizNo, int energyId, boolean notifyFriend) {
        try {
            JSONObject arg = new JSONObject();
            arg.put("bizNo", bizNo + UUID.randomUUID().toString());
            arg.put("energyId", energyId);
            arg.put("extInfo", new JSONObject().put("sendChat", notifyFriend ? "Y" : "N"));
            arg.put("from", "friendIndex");
            arg.put("source", "chInfo_ch_appcenter__chsub_9patch");
            arg.put("targetUser", targetUser);
            arg.put("transferType", "WATERING");
            arg.put("version", VERSION);
            String param = "[" + arg + "]";
            return RequestManager.requestString("alipay.antmember.forest.h5.transferEnergy", param);
        } catch (Exception e) {
            Log.printStackTrace(e);
            return "";
        }
    }

    public static String queryEnergyRainHome() {
        return RequestManager.requestString("alipay.antforest.forest.h5.queryEnergyRainHome", "[{\"source\":\"senlinguangchuangrukou\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String queryEnergyRainCanGrantList() {
        return RequestManager.requestString("alipay.antforest.forest.h5.queryEnergyRainCanGrantList", "[{}]");
    }

    public static String grantEnergyRainChance(String targetUserId) {
        return RequestManager.requestString("alipay.antforest.forest.h5.grantEnergyRainChance", "[{\"targetUserId\":" + targetUserId + "}]");
    }

    public static String startEnergyRain() {
        return RequestManager.requestString("alipay.antforest.forest.h5.startEnergyRain", "[{\"version\":\"" + VERSION + "\"}]");
    }


    public static String energyRainSettlement(int saveEnergy, String token) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.energyRainSettlement",
                "[{\"activityPropNums\":0,\"saveEnergy\":" + saveEnergy + ",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }
    /** 查询能量雨/游戏结束列表奖励 */
    public static String queryEnergyRainEndGameList() {
        return RequestManager.requestString("alipay.antforest.forest.h5.queryEnergyRainEndGameList",
                "[ {} ]");
    }

    public static String queryTaskList() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("extend", new JSONObject());
        jo.put("fromAct", "home_task_list");
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        jo.put("version", VERSION);
        return RequestManager.requestString("alipay.antforest.forest.h5.queryTaskList", new JSONArray().put(jo).toString());
    }

    /*青春特权道具任务状态查询🔍*/
    public static String queryTaskListV2(String firstTaskType) throws JSONException {
        JSONObject jo = new JSONObject();
        JSONObject extend = new JSONObject();
        extend.put("firstTaskType", firstTaskType); // DNHZ_SL_college,DXS_BHZ，DXS_JSQ
        jo.put("extend", extend);
        jo.put("fromAct", "home_task_list");
        if (firstTaskType.equals("DNHZ_SL_college")) {
            jo.put("source", firstTaskType);
        }
        if (firstTaskType.equals("DXS_BHZ") || firstTaskType.equals("DXS_JSQ")) {
            jo.put("source", "202212TJBRW");
        }
        jo.put("version", VERSION);
        return RequestManager.requestString("alipay.antforest.forest.h5.queryTaskList", new JSONArray().put(jo).toString());
    }

    public static String receiveTaskAward(String sceneCode, String taskType) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("ignoreLimit", false);
        jo.put("requestType", "H5");
        jo.put("sceneCode", sceneCode);
        jo.put("source", "ANTFOREST");
        jo.put("taskType", taskType);
        return RequestManager.requestString("com.alipay.antiep.receiveTaskAward", new JSONArray().put(jo).toString());
    }

    /** 领取青春特权道具 */
    public static String receiveTaskAwardV2(String taskType) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("ignoreLimit", false);
        jo.put("requestType", "H5");
        jo.put("sceneCode", "ANTFOREST_VITALITY_TASK");
        jo.put("source", "ANTFOREST");
        jo.put("taskType", taskType); // DAXUESHENG_SJK,NENGLIANGZHAO_20230807,JIASUQI_20230808
        return RequestManager.requestString("com.alipay.antiep.receiveTaskAward", new JSONArray().put(jo).toString());
    }

    public static String finishTask(String sceneCode, String taskType) throws JSONException {
        String outBizNo = taskType + "_" + RandomUtil.nextDouble();
        JSONObject jo = new JSONObject();
        jo.put("outBizNo", outBizNo);
        jo.put("requestType", "H5");
        jo.put("sceneCode", sceneCode);
        jo.put("source", "ANTFOREST");
        jo.put("taskType", taskType);
        String args = "[" + jo + "]";
        return RequestManager.requestString("com.alipay.antiep.finishTask", args);
    }

    public static String antiepSign(String entityId, String userId, String sceneCode)
            throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("entityId", entityId);
        jo.put("requestType", "rpc");
        jo.put("sceneCode", sceneCode);
        jo.put("source", "ANTFOREST");
        jo.put("userId", userId);
        String args = "[" + jo + "]";
        return RequestManager.requestString("com.alipay.antiep.sign", args);
    }

    /** 查询背包道具列表 */
    public static String queryPropList(boolean onlyGive) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("onlyGive", onlyGive ? "Y" : "");
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        jo.put("version", VERSION);
        return RequestManager.requestString("alipay.antforest.forest.h5.queryPropList", new JSONArray().put(jo).toString());
    }

    public static String queryAnimalPropList() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        return RequestManager.requestString("alipay.antforest.forest.h5.queryAnimalPropList", new JSONArray().put(jo).toString());
    }

    /** 创建使用道具的请求数据 */
    private static JSONObject createConsumePropRequestData(String propGroup, String propId, String propType, Boolean secondConfirm)
            throws JSONException {
        JSONObject jo = new JSONObject();
        if (propGroup != null && !propGroup.isEmpty()) {
            jo.put("propGroup", propGroup);
        }
        jo.put("propId", propId);
        jo.put("propType", propType);
        jo.put("sToken", System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8));
        if (secondConfirm != null) {
            jo.put("secondConfirm", secondConfirm);
        }
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        jo.put("timezoneId", "Asia/Shanghai");
        jo.put("version", VERSION);
        return jo;
    }

    /** 调用蚂蚁森林 RPC 使用道具 (可续写/二次确认) */
    public static String consumeProp(String propGroup, String propId, String propType, boolean secondConfirm)
            throws JSONException {
        JSONObject requestData = createConsumePropRequestData(propGroup, propId, propType, secondConfirm);
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.consumeProp",
                "[" + requestData + "]"
        );
    }

    /** 调用蚂蚁森林 RPC 使用道具 (不可续写/直接使用) */
    public static String consumeProp2(String propGroup, String propId, String propType)
            throws JSONException {
        JSONObject requestData = createConsumePropRequestData(propGroup, propId, propType, null);
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.consumeProp",
                "[" + requestData + "]"
        );
    }

    public static String giveProp(String giveConfigId, String propId, String targetUserId)
            throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("giveConfigId", giveConfigId);
        jo.put("propId", propId);
        jo.put("source", "self_corner");
        jo.put("targetUserId", targetUserId);
        return RequestManager.requestString("alipay.antforest.forest.h5.giveProp", new JSONArray().put(jo).toString());
    }

    public static String collectProp(String giveConfigId, String giveId) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("giveConfigId", giveConfigId);
        jo.put("giveId", giveId);
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        return RequestManager.requestString("alipay.antforest.forest.h5.collectProp", new JSONArray().put(jo).toString());
    }

    /** 收取能量炸弹卡 */
    public static String collectBombCardEnergy(String propId) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("propId", propId);
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        return RequestManager.requestString("alipay.antforest.forest.h5.collectBombCardEnergy", new JSONArray().put(jo).toString());
    }

    public static String itemList(String labelType) {
        return RequestManager.requestString(
                "com.alipay.antiep.itemList",
                "[{\"extendInfo\":\"{}\",\"labelType\":\""
                        + labelType
                        + "\",\"pageSize\":20,\"requestType\":\"rpc\",\"sceneCode\":\"ANTFOREST_VITALITY\",\"source\":\"afEntry\",\"startIndex\":0}]");
    }

    public static String itemDetail(String spuId) {
        return RequestManager.requestString(
                "com.alipay.antiep.itemDetail",
                "[{\"requestType\":\"rpc\",\"sceneCode\":\"ANTFOREST_VITALITY\",\"source\":\"afEntry\",\"spuId\":\"" + spuId + "\"}]");
    }

    public static String exchangeBenefit(String spuId, String skuId) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("sceneCode", "ANTFOREST_VITALITY");
        jo.put("requestId", System.currentTimeMillis() + "_" + RandomUtil.getRandomInt(17));
        jo.put("spuId", spuId);
        jo.put("skuId", skuId);
        jo.put("source", "GOOD_DETAIL");
        return RequestManager.requestString("com.alipay.antcommonweal.exchange.h5.exchangeBenefit", new JSONArray().put(jo).toString());
    }

    /** 巡护保护地 */
    public static String queryUserPatrol() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("source", "ant_forest");
        jo.put("timezoneId", "Asia/Shanghai");
        return RequestManager.requestString("alipay.antforest.forest.h5.queryUserPatrol", new JSONArray().put(jo).toString());
    }

    public static String queryMyPatrolRecord() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("source", "ant_forest");
        jo.put("timezoneId", "Asia/Shanghai");
        return RequestManager.requestString("alipay.antforest.forest.h5.queryMyPatrolRecord", new JSONArray().put(jo).toString());
    }

    public static String switchUserPatrol(String targetPatrolId) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("source", "ant_forest");
        jo.put("targetPatrolId", targetPatrolId);
        jo.put("timezoneId", "Asia/Shanghai");
        return RequestManager.requestString("alipay.antforest.forest.h5.switchUserPatrol", new JSONArray().put(jo).toString());
    }

    public static String patrolGo(int nodeIndex, int patrolId) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.patrolGo", "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"source\":\"ant_forest\"," +
                        "\"timezoneId\":\"Asia/Shanghai\"}]");
    }

    public static String patrolKeepGoing(int nodeIndex, int patrolId, String eventType) {
        String args = switch (eventType) {
            case "video" ->
                    "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"viewed\":\"Y\"},\"source\":\"ant_forest\"," +
                            "\"timezoneId\":\"Asia/Shanghai\"}]";
            case "chase" ->
                    "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"sendChat\":\"Y\"},\"source\":\"ant_forest\"," +
                            "\"timezoneId\":\"Asia/Shanghai\"}]";
            case "quiz" ->
                    "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{\"answer\":\"correct\"},\"source\":\"ant_forest\"," +
                            "\"timezoneId\":\"Asia/Shanghai\"}]";
            default ->
                    "[{\"nodeIndex\":" + nodeIndex + ",\"patrolId\":" + patrolId + ",\"reactParam\":{},\"source\":\"ant_forest\"," +
                            "\"timezoneId\":\"Asia/Shanghai\"}]";
        };
        return RequestManager.requestString("alipay.antforest.forest.h5.patrolKeepGoing", args);
    }

    public static String exchangePatrolChance(int costStep) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.exchangePatrolChance", "[{\"costStep\":" + costStep + ",\"source\":\"ant_forest\"," +
                        "\"timezoneId\":\"Asia/Shanghai\"}]");
    }

    public static String queryAnimalAndPiece(int animalId) {
        String args;
        if (animalId != 0) {
            args = "[{\"animalId\":" + animalId + ",\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\"}]";
        } else {
            args = "[{\"source\":\"ant_forest\",\"timezoneId\":\"Asia/Shanghai\",\"withDetail\":\"N\",\"withGift\":true}]";
        }
        return RequestManager.requestString("alipay.antforest.forest.h5.queryAnimalAndPiece", args);
    }

    public static String combineAnimalPiece(int animalId, String piecePropIds) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.combineAnimalPiece",
                "[{\"animalId\":" + animalId + ",\"piecePropIds\":" + piecePropIds + ",\"timezoneId\":\"Asia/Shanghai\",\"source\":\"ant_forest\"}]");
    }

    public static String AnimalConsumeProp(String propGroup, String propId, String propType) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.consumeProp",
                "[{\"propGroup\":\"" + propGroup + "\",\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source\":\"ant_forest\"," +
                        "\"timezoneId\":\"Asia/Shanghai\"}]");
    }

    public static String collectAnimalRobEnergy(String propId, String propType, String shortDay) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.collectAnimalRobEnergy",
                "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"shortDay\":\"" + shortDay + "\",\"source" +
                        "\":\"chInfo_ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    /** 复活能量 */
    public static String protectBubble(String targetUserId) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.protectBubble",
                "[{\"source\":\"ANT_FOREST_H5\",\"targetUserId\":\"" + targetUserId + "\",\"version\":\"" + VERSION + "\"}]");
    }

    /** 森林礼盒 */
    public static String collectFriendGiftBox(String targetId, String targetUserId) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.collectFriendGiftBox",
                "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\",\"targetId\":\"" + targetId + "\",\"targetUserId\":\"" + targetUserId + "\"}]");
    }

    /** 6秒拼手速 打地鼠 */
    public static String startWhackMole() throws JSONException
    {
        JSONObject param = new JSONObject();
        param.put("source", "senlinguangchangdadishu");
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.startWhackMole",
                "[" + param + "]"
        );
    }

    /** 6秒拼手速 兼容模式打地鼠 */
    public static String oldstartWhackMole(String source) {
        return RequestManager.requestString("alipay.antforest.forest.h5.startWhackMole", "[{\"source\":\"" + source + "\"}]");
    }

    /** 打单个地鼠 道具 */
    public static String whackMole(long moleId, String token) throws JSONException
    {
        JSONObject param = new JSONObject();
        param.put("moleId", moleId);
        param.put("source", "senlinguangchangdadishu");
        param.put("token", token);
        param.put("version", VERSION);

        return RequestManager.requestString(
                "alipay.antforest.forest.h5.whackMole",
                "[" + param + "]"
        );
    }

    /**
     * 兼容模式打单个地鼠
     */
    public static String oldwhackMole(long moleId, String token, String source) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.whackMole",
                "[{\"moleId\":" + moleId + ",\"source\":\"" + source + "\",\"token\":\"" + token + "\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String settlementWhackMole(String token)
            throws JSONException {
        // moleIdList 改为 1 ,20（包含 1-20）
        List<Integer> moleIdList = IntStream.rangeClosed(1, 15)
                .boxed()
                .collect(Collectors.toList());
        JSONObject param = new JSONObject();
        param.put("moleIdList", new JSONArray(moleIdList));
        param.put("settlementScene", "NORMAL");
        param.put("source", "senlinguangchangdadishu");
        param.put("token", token);
        param.put("version", VERSION);
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.settlementWhackMole",
                "[" + param + "]"
        );
    }

    //兼容模式结算
    public static String oldsettlementWhackMole(String token, List<String> moleIdList, String source) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.settlementWhackMole",
                "[{\"moleIdList\":["
                        + String.join(",", moleIdList)
                        + "],\"settlementScene\":\"NORMAL\",\"source\":\"" + source + "\",\"token\":\""
                        + token
                        + "\",\"version\":\""
                        + VERSION
                        + "\"}]");
    }



    /** 森林集市 */
    public static String consultForSendEnergyByAction(String sourceType) {
        return RequestManager.requestString("alipay.bizfmcg.greenlife.consultForSendEnergyByAction", "[{\"sourceType\":\"" + sourceType + "\"}]");
    }

    /** 森林集市 */
    public static String sendEnergyByAction(String sourceType) {
        return RequestManager.requestString(
                "alipay.bizfmcg.greenlife.sendEnergyByAction",
                "[{\"actionType\":\"GOODS_BROWSE\",\"requestId\":\"" + RandomUtil.getRandomString(8) + "\",\"sourceType\":\"" + sourceType + "\"}]");
    }

    /** 翻倍额外能量收取 */
    public static String collectRobExpandEnergy(String propId, String propType) {
        return RequestManager.requestString(
                "alipay.antforest.forest.h5.collectRobExpandEnergy",
                "[{\"propId\":\"" + propId + "\",\"propType\":\"" + propType + "\",\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }

    public static String studentQqueryCheckInModel() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("chInfo", "ch_appcollect__chsub_my-recentlyUsed");
        jo.put("skipTaskModule", false);
        return RequestManager.requestString("alipay.membertangram.biz.rpc.student.queryCheckInModel", new JSONArray().put(jo).toString());
    }

    /*青春特权领红包*/
    public static String studentCheckin() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("source", "chInfo_ch_appcenter__chsub_9patch");
        return RequestManager.requestString("alipay.membertangram.biz.rpc.student.checkIn", new JSONArray().put(jo).toString());
    }

    /** 查询绿色行动 */
    public static String ecolifeQueryHomePage() {
        return RequestManager.requestString("alipay.ecolife.rpc.h5.queryHomePage",
                "[{\"channel\":\"ALIPAY\",\"source\":\"search_brandbox\"}]");
    }

    /** 开通绿色行动 */
    public static String ecolifeOpenEcolife() {
        return RequestManager.requestString("alipay.ecolife.rpc.h5.openEcolife",
                "[{\"channel\":\"ALIPAY\",\"source\":\"renwuGD\"}]");
    }

    /** 执行任务 */
    public static String ecolifeTick(String actionId, String dayPoint, String source) {
        String args1 = "[{\"actionId\":\"" + actionId + "\",\"channel\":\"ALIPAY\",\"dayPoint\":\""
                + dayPoint + "\",\"generateEnergy\":false,\"source\":\"" + source + "\"}]";
        return RequestManager.requestString("alipay.ecolife.rpc.h5.tick", args1);
    }

    /** 查询任务信息 */
    public static String ecolifeQueryDish(String source, String dayPoint) {
        return RequestManager.requestString("alipay.ecolife.rpc.h5.queryDish",
                "[{\"channel\":\"ALIPAY\",\"dayPoint\":\"" + dayPoint
                        + "\",\"source\":\"" + source + "\"}]");
    }

    /** 上传照片 */
    public static String ecolifeUploadDishImage(String operateType, String imageId,
                                                double conf1, double conf2, double conf3, String dayPoint) {
        return RequestManager.requestString("alipay.ecolife.rpc.h5.uploadDishImage",
                "[{\"channel\":\"ALIPAY\",\"dayPoint\":\"" + dayPoint +
                        "\",\"source\":\"photo-comparison\",\"uploadParamMap\":{\"AIResult\":[{\"conf\":" + conf1 + ",\"kvPair\":false," +
                        "\"label\":\"other\",\"pos\":[1.0002995,0.22104378,0.0011976048,0.77727276],\"value\":\"\"}," +
                        "{\"conf\":" + conf2 + ",\"kvPair\":false,\"label\":\"guangpan\",\"pos\":[1.0002995,0.22104378,0.0011976048,0.77727276]," +
                        "\"value\":\"\"},{\"conf\":" + conf3 + ",\"kvPair\":false,\"label\":\"feiguangpan\"," +
                        "\"pos\":[1.0002995,0.22104378,0.0011976048,0.77727276],\"value\":\"\"}],\"existAIResult\":true,\"imageId\":\"" +
                        imageId + "\",\"imageUrl\":\"https://mdn.alipayobjects.com/afts/img/" + imageId +
                        "/original?bz=APM_20000067\",\"operateType\":\"" + operateType + "\"}}]");
    }

    // 查询森林能量
    public static String queryForestEnergy(String scene) {
        String args = "[{\"activityCode\":\"query_forest_energy\",\"activityId\":\"2024052300762675\",\"body\":{\"scene\":\"" + scene + "\"},\"version\":\"2" +
                ".0\"}]";
        return RequestManager.requestString("alipay.iblib.channel.data", args);
    }

    // 生成森林能量
    public static String produceForestEnergy(String scene) {
        long uniqueId = System.currentTimeMillis();
        String args = "[{\"activityCode\":\"produce_forest_energy\",\"activityId\":\"2024052300762674\",\"body\":{\"scene\":\"" + scene + "\",\"uniqueId" +
                "\":\"" + uniqueId + "\"},\"version\":\"2.0\"}]";
        return RequestManager.requestString("alipay.iblib.channel.data", args);
    }

    // 领取森林能量
    public static String harvestForestEnergy(String scene, JSONArray bubbles) {
        String args = "[{\"activityCode\":\"harvest_forest_energy\",\"activityId\":\"2024052300762676\",\"body\":{\"bubbles\":" + bubbles + ",\"scene\":\"" + scene + "\"},\"version\":\"2.0\"}]";
        return RequestManager.requestString("alipay.iblib.channel.data", args);
    }

    // ==================== 森林抽抽乐相关方法（最终修复版） ====================

    /** 森林抽抽乐-活动列表（最终修复版） 根据抓包日志，正确的参数结构应该是直接传递参数，不需要requestData包装 */
    public static String enterDrawActivityopengreen(String activityId, String sceneCode, String source)
            throws JSONException {
        // 根据抓包日志，正确的参数结构是直接传递，不需要requestData包装
        JSONObject requestData = new JSONObject();
        if (activityId != null && !activityId.isEmpty()) {
            requestData.put("activityId", activityId);
        } else {
            requestData.put("activityId", "");
        }
        requestData.put("requestType", "RPC");
        requestData.put("sceneCode", sceneCode); // 必须传递 sceneCode
        requestData.put("source", source); // 必须传递 source

        String args = "[" + requestData + "]";
        Log.record("AntForestRpcCall", "enterDrawActivityopengreen - 活动: " + activityId + ", 场景: " + sceneCode + ", source: " + source);
        return RequestManager.requestString("com.alipay.antiepdrawprod.enterDrawActivityopengreen", args);
    }

    /** 森林抽抽乐-请求任务列表（最终修复版） */
    public static String listTaskopengreen(String sceneCode, String source) throws JSONException {
        // 根据抓包日志，正确的参数结构是直接传递，不需要requestData包装
        JSONObject requestData = new JSONObject();
        requestData.put("requestType", "RPC");
        requestData.put("sceneCode", sceneCode); // 必须传递 sceneCode
        requestData.put("source", source); // 必须传递 source

        String args = "[" + requestData + "]";
        Log.record("AntForestRpcCall", "listTaskopengreen - 场景: " + sceneCode + ", source: " + source);
        return RequestManager.requestString("com.alipay.antieptask.listTaskopengreen", args);
    }

    /** 森林抽抽乐-抽奖（最终修复版） */
    public static String drawopengreen(String activityId, String sceneCode, String source, String userId)
            throws JSONException {
        // 根据抓包日志，正确的参数结构是直接传递，不需要requestData包装
        JSONObject requestData = new JSONObject();
        requestData.put("activityId", activityId);
        requestData.put("requestType", "RPC");
        requestData.put("sceneCode", sceneCode); // 必须传递 sceneCode
        requestData.put("source", source); // 必须传递 source
        requestData.put("userId", userId);

        String args = "[" + requestData + "]";
        Log.record("AntForestRpcCall", "drawopengreen - 活动: " + activityId + ", 场景: " + sceneCode + ", source: " + source);
        return RequestManager.requestString("com.alipay.antiepdrawprod.drawopengreen", args);
    }

    /** 森林抽抽乐-签到领取次数（最终修复版） */
    public static String receiveTaskAwardopengreen(String source, String sceneCode, String taskType)
            throws JSONException {
        // 根据抓包日志，正确的参数结构是直接传递，不需要requestData包装
        JSONObject requestData = new JSONObject();
        requestData.put("ignoreLimit", true);
        requestData.put("requestType", "RPC");
        requestData.put("sceneCode", sceneCode);
        requestData.put("source", source); // 必须传递 source
        requestData.put("taskType", taskType);

        String args = "[" + requestData + "]";
        Log.record("AntForestRpcCall", "receiveTaskAwardopengreen - 任务: " + taskType + ", source: " + source);
        return RequestManager.requestString("com.alipay.antieptask.receiveTaskAwardopengreen", args);
    }

    /** 森林抽抽乐-任务-活力值兑换抽奖次数（最终修复版） */
    public static String exchangeTimesFromTaskopengreen(String activityId, String sceneCode, String source, String taskSceneCode, String taskType)
            throws JSONException {
        // 根据抓包日志，正确的参数结构是直接传递，不需要requestData包装
        JSONObject requestData = new JSONObject();
        requestData.put("activityId", activityId);
        requestData.put("requestType", "RPC");
        requestData.put("sceneCode", sceneCode);
        requestData.put("source", source); // 必须传递 source
        requestData.put("taskSceneCode", taskSceneCode);
        requestData.put("taskType", taskType);

        String args = "[" + requestData + "]";
        Log.record("AntForestRpcCall", "exchangeTimesFromTaskopengreen - 活动: " + activityId + ", 任务: " + taskType + ", source: " + source);
        return RequestManager.requestString("com.alipay.antiepdrawprod.exchangeTimesFromTaskopengreen", args);
    }

    /** 森林抽抽乐-任务-广告（支持普通版和活动版） */
    public static String finishTask4Chouchoule(String taskType, String sceneCode)
            throws JSONException {
        JSONObject params = new JSONObject();
        params.put("outBizNo", taskType + RandomUtil.getRandomTag());
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);

        // 根据任务类型设置不同的source
        if (taskType.contains("XLIGHT")) {
            params.put("source", "ADBASICLIB");
        } else if (taskType.startsWith("FOREST_ACTIVITY_DRAW")) {
            params.put("source", "task_entry"); // 活动版任务使用task_entry
        } else {
            params.put("source", "task_entry"); // 默认使用task_entry
        }

        params.put("taskType", taskType);
        String args = "[" + params + "]";
        Log.record("AntForestRpcCall", "finishTask4Chouchoule - 任务: " + taskType);
        return RequestManager.requestString("com.alipay.antiep.finishTask", args);
    }

    /** 完成森林抽抽乐任务（支持普通版和活动版） */
    public static String finishTaskopengreen(String taskType, String sceneCode)
            throws JSONException {
        JSONObject params = new JSONObject();
        params.put("outBizNo", taskType + RandomUtil.getRandomTag());
        params.put("requestType", "RPC");
        params.put("sceneCode", sceneCode);

        // 统一使用 task_entry，因为从日志看两种任务都使用这个source
        params.put("source", "task_entry");

        params.put("taskType", taskType);
        String args = "[" + params + "]";
        Log.record("AntForestRpcCall", "finishTaskopengreen - 任务: " + taskType);
        return RequestManager.requestString("com.alipay.antieptask.finishTaskopengreen", args);
    }

    /** 根据道具类型获取道具组 */
    public static String getPropGroup(String propType) {
        if (propType.contains("SHIELD")) {
            return "shield";
        } else if (propType.contains("DOUBLE_CLICK")) {
            return "doubleClick";
        } else if (propType.contains("STEALTH")) {
            return "stealthCard";
        } else if (propType.contains("BOMB_CARD") || propType.contains("NO_EXPIRE")) {
            return "energyBombCard";
        } else if (propType.contains("ROB_EXPAND")) {
            return "robExpandCard";
        } else if (propType.contains("BUBBLE_BOOST")) {
            return "boost";
        }
        return ""; // 默认返回空字符串
    }

    /** 查询游戏列表 */
    public static String queryGameList() {
        return RequestManager.requestString("com.alipay.charitygamecenter.queryGameList",
                "[{" +
                        "  \"bizType\": \"ANTFOREST\"," +
                        "  \"commonDegradeFilterRequest\": {" +
                        "    \"deviceLevel\": \"high\"," +
                        "    \"platform\": \"Android\"," +
                        "    \"unityDeviceLevel\": \"high\"" +
                        "  }," +
                        "  \"requestType\": \"RPC\"," +
                        "  \"sceneCode\": \"ANTFOREST\"," +
                        "  \"source\": \"chInfo_ch_appcenter__chsub_9patch\"," +
                        "  \"version\": \"" + VERSION + "\"" +
                        "}]");
    }
    /**
     * 领取游戏中心奖励 (批量开宝箱)
     * @param batchDrawCount 批量领取的次数 (例如 1 或 10)
     */
    public static String drawGameCenterAward(int batchDrawCount) {
        return RequestManager.requestString("com.alipay.charitygamecenter.drawGameCenterAward",
                "[{" +
                        "  \"batchDrawCount\": " + batchDrawCount + "," +
                        "  \"bizType\": \"ANTFOREST\"," +
                        "  \"requestType\": \"RPC\"," +
                        "  \"sceneCode\": \"ANTFOREST\"," +
                        "  \"source\": \"leyuan\"," +
                        "  \"version\": \"" + VERSION + "\"" +
                        "}]");
    }
    /** 初始化/上报游戏任务 */
    public static String initTask(String taskType) {
        // 生成类似 GAME_DONE_SLJYD_1769062463227_569cf36c 的 outBizNo
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String outBizNo = taskType + "_" + timestamp + "_" + randomSuffix;

        return RequestManager.requestString("com.alipay.antiep.initTask",
                "[{" +
                        "  \"outBizNo\": \"" + outBizNo + "\"," +
                        "  \"requestType\": \"H5\"," +
                        "  \"sceneCode\": \"ANTFOREST_ENERGY_RAIN_TASK\"," +
                        "  \"source\": \"ANTFOREST\"," +
                        "  \"taskType\": \"" + taskType + "\"" +
                        "}]");
    }

}

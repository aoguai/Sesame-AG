package fansirsqi.xposed.sesame.task.antDodo;

import org.json.JSONObject;

import fansirsqi.xposed.sesame.hook.RequestManager;
import fansirsqi.xposed.sesame.util.RandomUtil;

public class AntDodoRpcCall {
    private static final String VERSION = "20241203";
    /* 神奇物种 */
    public static String queryAnimalStatus() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.queryAnimalStatus",
                "[{\"source\":\"chInfo_ch_appcenter__chsub_9patch\"}]");
    }

    public static String homePage() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.homePage",
                "[{}]");
    }

    public static String collect() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.collect",
                "[{}]");
    }

    public static String taskList() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.taskList",
                "[{\"version\":\""+VERSION+"\"}]");
    }

    public static String finishTask(String sceneCode, String taskType) {
        String uniqueId = getUniqueId();
        return RequestManager.requestString("com.alipay.antiep.finishTask",
                "[{\"outBizNo\":\"" + uniqueId + "\",\"requestType\":\"rpc\",\"sceneCode\":\""
                        + sceneCode + "\",\"source\":\"af-biodiversity\",\"taskType\":\""
                        + taskType + "\",\"uniqueId\":\"" + uniqueId + "\"}]");
    }

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    public static String receiveTaskAward(String sceneCode, String taskType) {
        return RequestManager.requestString("com.alipay.antiep.receiveTaskAward",
                "[{\"ignoreLimit\":0,\"requestType\":\"rpc\",\"sceneCode\":\"" + sceneCode
                        + "\",\"source\":\"af-biodiversity\",\"taskType\":\"" + taskType
                        + "\"}]");
    }

    public static String propList() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.propList",
                "[{}]");
    }


    //使用道具
    public static String consumeProp(String propId, String propType, String animalId) {
        // 基础参数
        StringBuilder params = new StringBuilder("[{");

        // 如果 animalId 不为空，则构建 extendInfo 字段
        if (animalId != null && !animalId.isEmpty()) {
            params.append("\"extendInfo\":{")
                    .append("\"animalId\":\"").append(animalId).append("\"")
                    .append("},");
        }

        // 拼接 propId 和 propType
        params.append("\"propId\":\"").append(propId).append("\",")
                .append("\"propType\":\"").append(propType).append("\"")
                .append("}]");

        return RequestManager.requestString("alipay.antdodo.rpc.h5.consumeProp", params.toString());
    }

    /**
     * 专门用于：抽好友卡道具 的消耗请求
     * 参数格式：[{"propId":"...","propType":"..."}]
     */
    public static String consumePropForFriend(String propId, String propType) {
        // 构造不含 extendInfo 的参数
        String params = "[{" +
                "\"propId\":\"" + propId + "\"," +
                "\"propType\":\"" + propType + "\"" +
                "}]";

        return RequestManager.requestString("alipay.antdodo.rpc.h5.consumeProp", params);
    }

    //查询图鉴详情
    public static String queryBookInfo(String bookId) {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.queryBookInfo",
                "[{\"bookId\":\"" + bookId + "\"}]");
    }

    // 送卡片给好友
    public static String social(String targetAnimalId, String targetUserId) {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.social",
                "[{\"actionCode\":\"GIFT_TO_FRIEND\",\"source\":\"GIFT_TO_FRIEND_FROM_CC\",\"targetAnimalId\":\""
                        + targetAnimalId + "\",\"targetUserId\":\"" + targetUserId
                        + "\",\"triggerTime\":\"" + System.currentTimeMillis() + "\"}]");
    }

    public static String queryFriend() {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.queryFriend",
                "[{\"sceneCode\":\"EXCHANGE\"}]");
    }

    public static String collecttarget(String targetUserId) {
        return RequestManager.requestString("alipay.antdodo.rpc.h5.collect",
                "[{\"targetUserId\":" + targetUserId + "}]");
    }

    public static String queryBookList(int pageSize, String pageStart) {
        try {
            // 使用 JSONObject 构造可以避免手动拼接字符串导致的转义和逗号错误
            JSONObject params = new JSONObject();
            params.put("pageSize", pageSize);
            params.put("v2", "true");

            // 仅在 pageStart 不为空时才添加该字段
            if (pageStart != null && !pageStart.isEmpty()) {
                params.put("pageStart", pageStart);
            }


            return RequestManager.requestString("alipay.antdodo.rpc.h5.queryBookList", "[" + params.toString() + "]");
        } catch (Exception e) {
            return "";
        }
    }

    public static String generateBookMedal(String bookId) {
        String args = "[{\"bookId\":\"" + bookId + "\"}]";
        return RequestManager.requestString("alipay.antdodo.rpc.h5.generateBookMedal", args);
    }
}
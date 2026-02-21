package fansirsqi.xposed.sesame.task.antDodo;
import static fansirsqi.xposed.sesame.entity.OtherEntityProvider.listPropGroupOptions;

import com.fasterxml.jackson.core.type.TypeReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import fansirsqi.xposed.sesame.entity.AlipayUser;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.ChoiceModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField;
import fansirsqi.xposed.sesame.util.DataStore;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskStatus;
import fansirsqi.xposed.sesame.util.GlobalThreadPools;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.maps.UserMap;
import fansirsqi.xposed.sesame.util.ResChecker;
import fansirsqi.xposed.sesame.util.TimeUtil;
public class AntDodo extends ModelTask {

    /**
     * 仅限 AntDodo 内部使用的道具组常量定义
     */
    public interface PropGroupType {
        /** 当前图鉴抽卡券 🎴 */
        String COLLECT_ANIMAL = "COLLECT_ANIMAL";
        /** 好友卡抽卡券 👥 */
        String ADD_COLLECT_TO_FRIEND_LIMIT = "ADD_COLLECT_TO_FRIEND_LIMIT";
        /** 万能卡 🃏 */
        String UNIVERSAL_CARD = "UNIVERSAL_CARD";
    }

    private static final String TAG = AntDodo.class.getSimpleName();
    @Override
    public String getName() {
        return "神奇物种";
    }
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }
    @Override
    public String getIcon() {
        return "AntDodo.png";
    }
    private BooleanModelField collectToFriend;
    private ChoiceModelField collectToFriendType;
    private SelectModelField collectToFriendList;
    private SelectModelField sendFriendCard;

    private SelectModelField usepropGroup;  //道具使用类型
    private ChoiceModelField usePropUNIVERSALCARDType;         //万能卡使用方法

    private BooleanModelField autoGenerateBook;
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(collectToFriend = new BooleanModelField("collectToFriend", "帮抽卡 | 开启", false));
        modelFields.addField(collectToFriendType = new ChoiceModelField("collectToFriendType", "帮抽卡 | 动作", CollectToFriendType.COLLECT, CollectToFriendType.nickNames));
        modelFields.addField(collectToFriendList = new SelectModelField("collectToFriendList", "帮抽卡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(sendFriendCard = new SelectModelField("sendFriendCard", "送卡片好友列表(当前图鉴所有卡片)", new LinkedHashSet<>(), AlipayUser::getList));

        // 道具组类型：使用你刚刚定义的列表提供者
        modelFields.addField(usepropGroup = new SelectModelField("usepropGroup", "使用道具类型", new LinkedHashSet<>(), listPropGroupOptions()));

        modelFields.addField(usePropUNIVERSALCARDType = new ChoiceModelField("usePropUNIVERSALCARDType", "万能卡 | 使用方式", UniversalCardUseType.EXCLUDE_CURRENT, UniversalCardUseType.nickNames));
        modelFields.addField(autoGenerateBook = new BooleanModelField("autoGenerateBook", "自动合成图鉴", false));
        return modelFields;
    }
    @Override
    protected void runJava() {
        try {
            Log.record(TAG,"执行开始-" + getName());
            receiveTaskAward();
            propList();
            collect();
            if (collectToFriend.getValue()) {
                collectToFriend();
            }
            if(autoGenerateBook.getValue()){
                autoGenerateBook();//自动 兑换
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,"start Dodo.run err:", t);
        }finally {
            Log.record(TAG,"执行结束-" + getName());
        }
    }
    /*
     * 神奇物种
     */
    private boolean lastDay(String endDate) {
        long timeStep = System.currentTimeMillis();
        long endTimeStep = TimeUtil.timeToStamp(endDate);
        return timeStep < endTimeStep && (endTimeStep - timeStep) < 86400000L;
    }
    public boolean in8Days(String endDate) {
        long timeStep = System.currentTimeMillis();
        long endTimeStep = TimeUtil.timeToStamp(endDate);
        return timeStep < endTimeStep && (endTimeStep - timeStep) < 691200000L;
    }
    private void collect() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryAnimalStatus());
            if (ResChecker.checkRes(TAG,jo)) {
                JSONObject data = jo.getJSONObject("data");
                if (data.getBoolean("collect")) {
                    Log.record(TAG,"神奇物种卡片今日收集完成！");
                } else {
                    collectAnimalCard();
                }
            } else {
                Log.record(TAG, "collect错误"+jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "AntDodo Collect err:",t);
        }
    }
    private void collectAnimalCard() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.homePage());
            if (ResChecker.checkRes(TAG,jo)) {
                JSONObject data = jo.getJSONObject("data");
                JSONObject animalBook = data.getJSONObject("animalBook");
                String bookId = animalBook.getString("bookId");
                String endDate = animalBook.getString("endDate") + " 23:59:59";
                receiveTaskAward();
                if (!in8Days(endDate) || lastDay(endDate))
                    propList();
                JSONArray ja = data.getJSONArray("limit");
                int index = -1;
                for (int i = 0; i < ja.length(); i++) {
                    jo = ja.getJSONObject(i);
                    if ("DAILY_COLLECT".equals(jo.getString("actionCode"))) {
                        index = i;
                        break;
                    }
                }
                Set<String> set = sendFriendCard.getValue();
                if (index >= 0) {
                    int leftFreeQuota = jo.getInt("leftFreeQuota");
                    for (int j = 0; j < leftFreeQuota; j++) {
                        jo = new JSONObject(AntDodoRpcCall.collect());
                        if (ResChecker.checkRes(TAG,jo)) {
                            data = jo.getJSONObject("data");
                            JSONObject animal = data.getJSONObject("animal");
                            String ecosystem = animal.getString("ecosystem");
                            String name = animal.getString("name");
                            Log.forest("神奇物种🦕[" + ecosystem + "]#" + name);
                            if (!set.isEmpty()) {
                                for (String userId : set) {
                                    if (!UserMap.INSTANCE.getCurrentUid().equals(userId)) {
                                        int fantasticStarQuantity = animal.optInt("fantasticStarQuantity", 0);
                                        if (fantasticStarQuantity == 3) {
                                            sendCard(animal, userId);
                                        }
                                        break;
                                    }
                                }
                            }
                        } else {
                            Log.record(TAG,"collectAnimalCard错误"+ jo.getString("resultDesc"));
                        }
                    }
                }
                if (!set.isEmpty()) {
                    for (String userId : set) {
                        if (!UserMap.INSTANCE.getCurrentUid().equals(userId)) {
                            sendAntDodoCard(bookId, userId);
                            break;
                        }
                    }
                }
            } else {
                Log.record(TAG, "collectAnimalCard错误2 "+ jo.getString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG,"AntDodo CollectAnimalCard err:",t);
        }
    }
    /**
     * 神奇物种任务
     */
    private void receiveTaskAward() {
        try {
            // 获取不能完成的任务列表
            Set<String> presetBad = new LinkedHashSet<>(List.of("HELP_FRIEND_COLLECT"));
            TypeReference<Set<String>> typeRef = new TypeReference<>() {};
            Set<String> badTaskSet = DataStore.INSTANCE.getOrCreate("badDodoTaskList", typeRef);
            if (badTaskSet.isEmpty()) {
                badTaskSet.addAll(presetBad);
                DataStore.INSTANCE.put("badDodoTaskList", badTaskSet);
            }
            while (true) {
                boolean doubleCheck = false;
                String response = AntDodoRpcCall.taskList(); // 调用任务列表接口
                JSONObject jsonResponse = new JSONObject(response); // 解析响应为 JSON 对象
                // 检查响应结果码是否成功
                if (!ResChecker.checkRes(TAG, jsonResponse)) {
                    Log.record(TAG, "查询任务列表失败：" + jsonResponse.getString("resultDesc"));
                    break;
                }
                // 获取任务组信息列表
                JSONArray taskGroupInfoList = jsonResponse.getJSONObject("data").optJSONArray("taskGroupInfoList");
                if (taskGroupInfoList == null) return; // 如果任务组为空则返回
                // 遍历每个任务组
                for (int i = 0; i < taskGroupInfoList.length(); i++) {
                    JSONObject antDodoTask = taskGroupInfoList.getJSONObject(i);
                    JSONArray taskInfoList = antDodoTask.getJSONArray("taskInfoList"); // 获取任务信息列表
                    // 遍历每个任务
                    for (int j = 0; j < taskInfoList.length(); j++) {
                        JSONObject taskInfo = taskInfoList.getJSONObject(j);
                        JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo"); // 获取任务基本信息
                        JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo")); // 获取业务信息
                        String taskType = taskBaseInfo.getString("taskType"); // 获取任务类型
                        String taskTitle = bizInfo.optString("taskTitle", taskType); // 获取任务标题
                        String awardCount = bizInfo.optString("awardCount", "1"); // 获取奖励数量
                        String sceneCode = taskBaseInfo.getString("sceneCode"); // 获取场景代码
                        String taskStatus = taskBaseInfo.getString("taskStatus"); // 获取任务状态
                        // 如果任务已完成，领取任务奖励
                        if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                            JSONObject joAward = new JSONObject(
                                    AntDodoRpcCall.receiveTaskAward(sceneCode, taskType)); // 领取奖励请求
                            if (joAward.optBoolean("success")) {
                                doubleCheck = true;
                                Log.forest("任务奖励🎖️[" + taskTitle + "]#" + awardCount + "个");
                            } else {
                                Log.record(TAG,"领取失败，" + response); // 记录领取失败信息
                            }
                            Log.record(TAG,joAward.toString()); // 打印奖励响应
                        }
                        // 如果任务待完成，处理特定类型的任务
                        else if (TaskStatus.TODO.name().equals(taskStatus)) {
                            if (!badTaskSet.contains(taskType)) {
                                // 尝试完成任务
                                JSONObject joFinishTask = new JSONObject(
                                        AntDodoRpcCall.finishTask(sceneCode, taskType)); // 完成任务请求
                                if (joFinishTask.optBoolean("success")) {
                                    Log.forest("物种任务🧾️[" + taskTitle + "]");
                                    doubleCheck = true;
                                } else {
                                    Log.record(TAG,"完成任务失败，" + taskTitle); // 记录完成任务失败信息
                                    badTaskSet.add(taskType);
                                    DataStore.INSTANCE.put("badDodoTaskList", badTaskSet);
                                }

                            }
                        }
                        GlobalThreadPools.sleepCompat(500);
                    }
                }
                if (!doubleCheck) break;
            }
        } catch (JSONException e) {
            Log.printStackTrace(TAG,"神奇物种 JSON解析错误: " + e.getMessage(),e);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "AntDodo ReceiveTaskAward 错误:",t); // 打印异常栈
        }
    }


    public void propList() {
            try {
                // 获取道具列表
                String s = AntDodoRpcCall.propList();
                JSONObject jo = new JSONObject(s);
                if (!ResChecker.checkRes(TAG, jo))
                {
                    Log.error(TAG, "获取道具列表失败:"+jo);
                    return;
                }

                JSONArray propList = jo.getJSONObject("data").getJSONArray("propList");

                // --- A. 初始进度检查 (针对当前图鉴) ---
                int currentCount = 0;
                int totalCount = 0;
                try {
                    JSONObject homeJo = new JSONObject(AntDodoRpcCall.homePage());
                    JSONObject homeData = homeJo.optJSONObject("data");
                    if (homeData != null) {
                        currentCount = homeData.optInt("curCollectionCategoryCount");
                        JSONObject animalBook = homeData.optJSONObject("animalBook");
                        if (animalBook != null) {
                            totalCount = animalBook.optInt("totalCount");
                        }
                    }
                } catch (Exception e) {
                    Log.record(TAG, "获取初始进度失败，将尝试默认抽卡");
                }

                // 标记位：如果一开始就满了，后面 COLLECT_ANIMAL 直接跳过
                boolean isBookFull = (totalCount > 0 && currentCount >= totalCount);

                // 获取 UI 配置 (用户勾选了哪些类型的道具自动使用)
                Set<String> selectedConfigs = usepropGroup.getValue();
                if (selectedConfigs == null) return;

                for (int i = 0; i < propList.length(); i++) {
                    JSONObject prop = propList.getJSONObject(i);
                    JSONObject config = prop.optJSONObject("propConfig");
                    String currentPropGroup = config != null ? config.optString("propGroup") : "";
                    String propType = prop.getString("propType");
                    JSONArray propIdList = prop.getJSONArray("propIdList");
                    int holdsNum = prop.getInt("holdsNum");

                    if (holdsNum <= 0) continue;

                    // --- 逻辑分发 ---

                    // 1. 万能卡逻辑 (UNIVERSAL_CARD)
                    if (PropGroupType.UNIVERSAL_CARD.equals(currentPropGroup) &&
                            selectedConfigs.contains(PropGroupType.UNIVERSAL_CARD)) {
                        if (isBookFull) continue;

                        for (int j = 0; j < propIdList.length(); j++) {
                            String pId = propIdList.getString(j);
                            String animalId = getTargetAnimalIdForUniversalCard(); // 你原有的找缺失ID函数
                            if (!animalId.isEmpty()) {
                                String res = AntDodoRpcCall.consumeProp(pId, propType, animalId);
                                if (ResChecker.checkRes(TAG, res)) {
                                    currentCount++; // 万能卡必中新卡
                                    if (currentCount >= totalCount) isBookFull = true;
                                    Log.forest("万能卡使用成功，补全动物ID: " + animalId + " | 进度: " + currentCount + "/" + totalCount);
                                }
                                GlobalThreadPools.sleepCompat(2000L);
                            }
                        }
                    }

                    // 2. 好友抽卡逻辑 (ADD_COLLECT_TO_FRIEND_LIMIT)
                    else if (PropGroupType.ADD_COLLECT_TO_FRIEND_LIMIT.equals(currentPropGroup) &&
                            selectedConfigs.contains(PropGroupType.ADD_COLLECT_TO_FRIEND_LIMIT)) {
                        for (int j = 0; j < propIdList.length(); j++) {
                            String pId = propIdList.getString(j);
                            String res = AntDodoRpcCall.consumePropForFriend(pId, propType);
                            if (ResChecker.checkRes(TAG, res)) {
                                Log.record(TAG, "成功使用 [好友抽卡道具]");
                            }
                            GlobalThreadPools.sleepCompat(2000L);
                        }
                    }

                    // 3. 普通抽卡券逻辑 (COLLECT_ANIMAL)
                    else if (PropGroupType.COLLECT_ANIMAL.equals(currentPropGroup) &&
                            selectedConfigs.contains(PropGroupType.COLLECT_ANIMAL)) {

                        for (int j = 0; j < propIdList.length(); j++) {
                            if (isBookFull) {
                                Log.record(TAG, "图鉴已集满，自动关停后续抽卡动作");
                                break;
                            }

                            String pId = propIdList.getString(j);
                            String res = AntDodoRpcCall.consumeProp(pId, propType, null);

                            if (ResChecker.checkRes(TAG, res)) {
                                try {
                                    JSONObject resJo = new JSONObject(res);
                                    JSONObject data = resJo.optJSONObject("data");
                                    if (data == null) continue;

                                    // 提取道具名
                                    String pName = data.optJSONObject("propConfig").optString("propName", "抽卡道具");

                                    JSONObject useResult = data.optJSONObject("useResult");
                                    if (useResult != null) {
                                        JSONObject animal = useResult.optJSONObject("animal");
                                        String ecosystem = animal != null ? animal.optString("ecosystem") : "当前特辑";
                                        String animalName = animal != null ? animal.optString("name") : "未知物种";

                                        // 解析是否新卡并更新进度
                                        JSONObject collectDetail = useResult.optJSONObject("collectDetail");
                                        boolean isNew = collectDetail != null && collectDetail.optBoolean("newCard");

                                        if (isNew) {
                                            currentCount++;
                                            if (currentCount >= totalCount) isBookFull = true;
                                        }

                                        Log.forest(String.format("使用[%s] 抽到: %s-%s%s | 进度: %d/%d",
                                                pName, ecosystem, animalName, (isNew ? " [新!]" : " (重复)"),
                                                currentCount, totalCount));
                                    }
                                } catch (Throwable t) {
                                    Log.printStackTrace(TAG, "解析抽卡结果 JSON 异常", t);
                                }
                            } else {
                                Log.error(TAG, "使用道具请求失败: " + res);
                            }
                            GlobalThreadPools.sleepCompat(2000L);
                        }
                    }

                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, "propList 处理异常", t);
            }
        }


    /**
     * 发送神奇物种卡片
     * @param bookId 卡片图鉴ID
     * @param targetUser 目标用户ID
     */
    private void sendAntDodoCard(String bookId, String targetUser) {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookInfo(bookId));
            if (ResChecker.checkRes(TAG,jo)) {
                JSONArray animalForUserList = jo.getJSONObject("data").optJSONArray("animalForUserList");
                for (int i = 0; i < Objects.requireNonNull(animalForUserList).length(); i++) {
                    JSONObject animalForUser = animalForUserList.getJSONObject(i);
                    int count = animalForUser.getJSONObject("collectDetail").optInt("count");
                    if (count <= 0)
                        continue;
                    JSONObject animal = animalForUser.getJSONObject("animal");
                    for (int j = 0; j < count; j++) {
                        sendCard(animal, targetUser);
                        GlobalThreadPools.sleepCompat(500L);
                    }
                }
            }
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "AntDodo SendAntDodoCard err:",th);
        }
    }
    private void sendCard(JSONObject animal, String targetUser) {
        try {
            String animalId = animal.getString("animalId");
            String ecosystem = animal.getString("ecosystem");
            String name = animal.getString("name");
            JSONObject jo = new JSONObject(AntDodoRpcCall.social(animalId, targetUser));
            if (ResChecker.checkRes(TAG,jo)) {
                Log.forest("赠送卡片🦕[" + UserMap.getMaskName(targetUser) + "]#" + ecosystem + "-" + name);
            } else {
                Log.record(TAG, "sendCard错误"+jo.getString("resultDesc"));
            }
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "AntDodo SendCard err:",th);
        }
    }
    private void collectToFriend() {
        try {
            JSONObject jo = new JSONObject(AntDodoRpcCall.queryFriend());
            if (!ResChecker.checkRes(TAG, jo)) {
                Log.error(TAG, "神奇物种帮好友抽卡失败："+jo.getString("resultDesc"));
                return;
            }

            // 获取可用次数
            int count = 0;
            JSONArray limitList = jo.getJSONObject("data").getJSONObject("extend").getJSONArray("limit");
            for (int i = 0; i < limitList.length(); i++) {
                JSONObject limit = limitList.getJSONObject(i);
                if ("COLLECT_TO_FRIEND".equals(limit.getString("actionCode"))) {
                    // 检查是否有开始时间限制
                    if (limit.has("startTime") && limit.getLong("startTime") > System.currentTimeMillis()) {
                        Log.record("神奇物种🦕帮好友抽卡未到开放时间: " + limit.getString("startTimeStr"));
                        return;
                    }
                    count = limit.getInt("leftLimit");
                    break;
                }
            }

            if (count <= 0) {
                Log.record("神奇物种🦕帮好友抽卡次数已用完");
                return;
            }

            // 遍历好友列表
            JSONArray friendList = jo.getJSONObject("data").getJSONArray("friends");
            for (int i = 0; i < friendList.length() && count > 0; i++) {
                JSONObject friend = friendList.getJSONObject(i);

                // 跳过今日已帮助的好友
                if (friend.getBoolean("dailyCollect")) {
                    continue;
                }

                String userId = friend.getString("userId");

                // 判断是否应该帮助该好友
                boolean inList = collectToFriendList.getValue().contains(userId);
                boolean shouldCollect = (collectToFriendType.getValue() == CollectToFriendType.COLLECT) ? inList : !inList;

                if (!shouldCollect) {
                    continue;
                }

                // 执行抽卡
                jo = new JSONObject(AntDodoRpcCall.collecttarget(userId));
                if (ResChecker.checkRes(TAG, jo)) {
                    String ecosystem = jo.getJSONObject("data").getJSONObject("animal").getString("ecosystem");
                    String name = jo.getJSONObject("data").getJSONObject("animal").getString("name");
                    String userName = UserMap.getMaskName(userId);
                    Log.forest("神奇物种🦕帮好友[" + userName + "]抽卡[" + ecosystem + "]#" + name);
                    count--;
                } else {
                    Log.record(TAG, "collecttarget错误"+jo.getString("resultDesc"));
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "AntDodo CollectHelpFriend err:",t);
        }
    }


    /**
     * 辅助逻辑：获取万能卡要兑换的精准动物ID
     */
    private String getTargetAnimalIdForUniversalCard() {
        try {
            JSONArray allBooks = getAllBookList();
            if (allBooks == null || allBooks.length() == 0) {
                Log.record(TAG, "万能卡：未获取到任何图鉴数据");
                return "";
            }

            String targetBookId = "";
            int strategy = usePropUNIVERSALCARDType.getValue();

            String currentDoingBookId = "";
            String bestOtherBookId = "";
            double maxOtherRate = -1.0;

            String bestOverallBookId = "";
            double maxOverallRate = -1.0;

            for (int i = 0; i < allBooks.length(); i++) {
                JSONObject book = allBooks.optJSONObject(i); // 使用 opt 防止 null
                if (book == null || isBookFinished(book)) continue;

                JSONObject result = book.optJSONObject("animalBookResult");
                if (result == null) continue;

                String bookId = result.optString("bookId");
                String status = book.optString("bookStatus");

                // --- 进度解析与计算 ---
                String prog = book.optString("collectProgress", "0/0");
                double rate = 0;
                try {
                    String[] p = prog.split("/");
                    if (p.length == 2) {
                        double current = Double.parseDouble(p[0]);
                        double total = Double.parseDouble(p[1]);
                        if (total > 0) {
                            rate = current / total;
                        }
                    }
                } catch (Exception ignored) {}

                // --- 策略分类收集 ---
                // 1. 识别当前正在进行的 (DOING)
                if ("DOING".equals(status)) {
                    currentDoingBookId = bookId;
                } else {
                    // 2. 识别非当前图鉴中进度最高的
                    if (rate > maxOtherRate) {
                        maxOtherRate = rate;
                        bestOtherBookId = bookId;
                    }
                }

                // 3. 识别全局进度最高的
                if (rate > maxOverallRate) {
                    maxOverallRate = rate;
                    bestOverallBookId = bookId;
                }
            }

            // --- 逻辑分支匹配 ---
            if (strategy == UniversalCardUseType.EXCLUDE_CURRENT) {
                targetBookId = bestOtherBookId;
                Log.record(TAG, "万能卡策略 [排除当前]: 选中非DOING最高进度图鉴 " + targetBookId);
            }
            else if (strategy == UniversalCardUseType.PRIORITY_MAX_PROGRESS) {
                targetBookId = bestOverallBookId;
                Log.record(TAG, "万能卡策略 [进度优先]: 选中全局最高进度图鉴 " + targetBookId);
            }
            else {
                // 模式：所有。优先进行中，进行中已满则选最高进度
                targetBookId = !currentDoingBookId.isEmpty() ? currentDoingBookId : bestOverallBookId;
                Log.record(TAG, "万能卡策略 [全部]: 优先进行中图鉴 " + targetBookId);
            }

            if (targetBookId.isEmpty()) return "";

            // --- 查询具体缺失卡片 ---
            String detailJson = AntDodoRpcCall.queryBookInfo(targetBookId);
            JSONObject detailObj = new JSONObject(detailJson);

            // 增加对 detail 接口返回结果的校验
            if (detailObj.optBoolean("success", false) || "SUCCESS".equals(detailObj.optString("resultCode"))) {
                JSONObject data = detailObj.optJSONObject("data");
                JSONArray animals = (data != null) ? data.optJSONArray("animalForUserList") : null;

                if (animals != null) {
                    for (int i = 0; i < animals.length(); i++) {
                        JSONObject item = animals.optJSONObject(i);
                        if (item == null) continue;

                        JSONObject collectDetail = item.optJSONObject("collectDetail");
                        // 只有 collect 为 false 才说明是缺的
                        if (collectDetail != null && !collectDetail.optBoolean("collect", false)) {
                            JSONObject animalInfo = item.optJSONObject("animal");
                            if (animalInfo != null) {
                                String animalId = animalInfo.optString("animalId");
                                String name = animalInfo.optString("name");
                                Log.record(TAG, "万能卡目标锁定: " + name + " (" + animalId + ")");
                                return animalId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.record(TAG, "万能卡逻辑执行失败: " + e.getMessage());
        }
        return "";
    }


    /**
     * 判断某个图鉴是否已经“完成” (不需要再投入万能卡)
     */
    private static boolean isBookFinished(JSONObject book) {
        if (book == null) return true;

        // 1. 优先判断合成状态：如果已经可以合成或者已经合成，则认为该图鉴已完成
        String medalStatus = book.optString("medalGenerationStatus");
        if ("CAN_GENERATE".equals(medalStatus) || "GENERATED".equals(medalStatus)) {
            return true;
        }

        // 2. 判断数字进度：例如 "10/10"
        String progress = book.optString("collectProgress", "");
        if (progress.contains("/")) {
            try {
                String[] parts = progress.split("/");
                if (parts.length == 2) {
                    int current = Integer.parseInt(parts[0].trim());
                    int total = Integer.parseInt(parts[1].trim());
                    return current >= total; // 只要现有的不小于总数，就不需要万能卡
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }


    /* 获取所有图鉴列表*/
    /**
     * 获取完整的图鉴数组 (自动处理翻页合并)
     * @return 包含所有图鉴对象的 JSONArray
     *
     * [
     *   {
     *     "animalBookResult": {
     *       "bookId": "dxmlyBook",
     *       "ecosystem": "东喜马拉雅高山森林生态系统",
     *       "name": "东喜马拉雅高山森林生态系统",
     *       "totalCount": 10,
     *       "magicCount": 1,
     *       "rareCount": 2,
     *       "commonCount": 7
     *       // ..
     *     },
     *     "bookStatus": "END",
     *     "bookCollectedStatus": "NOT_COMPLETED",
     *     "collectProgress": "1/10",
     *     "hasRedDot": false
     *   },
     *   {
     *     "animalBookResult": {
     *       "bookId": "zhbhtbhxcr202503",
     *       "name": "当前正在进行的某个图鉴",
     *       "totalCount": 10
     *       // ...
     *     },
     *     "bookStatus": "GOING",
     *     "bookCollectedStatus": "NOT_COMPLETED",
     *     "collectProgress": "5/10",
     *     "hasRedDot": true
     *   }
     *   // ...
     * ]
     */

    public static JSONArray getAllBookList() {
        JSONArray allBooks = new JSONArray();
        String pageStart = null; // 首页传 null
        boolean hasMore = true;

        try {
            while (hasMore) {
                // 调用上面修改后的接口
                String res = AntDodoRpcCall.queryBookList(64, pageStart);
                JSONObject jo = new JSONObject(res);

                if (!ResChecker.checkRes(TAG,jo)) {
                    Log.error(TAG, "queryBookList 失败: " + jo.optString("resultDesc"));
                    break;
                }

                JSONObject data = jo.optJSONObject("data");
                if (data == null) break;

                // 1. 提取并合并数据
                JSONArray currentList = data.optJSONArray("bookForUserList");
                if (currentList != null) {
                    for (int i = 0; i < currentList.length(); i++) {
                        allBooks.put(currentList.get(i));
                    }
                }

                // 2. 判断翻页逻辑
                hasMore = data.optBoolean("hasMore", false);
                pageStart = data.optString("nextPageStart", null);

                // 如果没有更多了，或者 nextPageStart 为空，直接跳出
                if (!hasMore || pageStart == null || pageStart.isEmpty()) {
                    break;
                }

                // 稍微控制一下频率
                GlobalThreadPools.sleepCompat(300);
            }
        } catch (Throwable th) {
            Log.printStackTrace(TAG, "获取全量图鉴异常", th);
        }
        return allBooks;
    }





    /**
     * 自动合成图鉴
     */
    private void autoGenerateBook() {
        try {
            // 1. 直接获取所有页合并后的完整图鉴数组
            JSONArray allBooks = getAllBookList();

            if (allBooks.length() == 0) {
                return;
            }

            // 2. 遍历全量数组
            for (int i = 0; i < allBooks.length(); i++) {
                JSONObject bookItem = allBooks.getJSONObject(i);

                // 判断是否可以合成勋章
                if (!"CAN_GENERATE".equals(bookItem.optString("medalGenerationStatus"))) {
                    continue;
                }

                JSONObject animalBookResult = bookItem.optJSONObject("animalBookResult");
                if (animalBookResult == null) {
                    Log.record(TAG,"animalBookResult为空，停止合成");
                    continue;

                }

                String bookId = animalBookResult.optString("bookId");
                String ecosystem = animalBookResult.optString("ecosystem");

                // 3. 调用合成接口
                String res = AntDodoRpcCall.generateBookMedal(bookId);
                JSONObject genResp = new JSONObject(res);

                if (ResChecker.checkRes(TAG, genResp)) {
                    Log.forest("神奇物种🦕合成勋章[" + ecosystem + "]");
                } else {
                    Log.record(TAG, "合成勋章失败[" + ecosystem + "]: " + genResp.optString("resultDesc"));
                }

                // 合成操作建议稍微加一点点延迟，保护接口
                GlobalThreadPools.sleepCompat(300);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "autoGenerateBook err:", t);
        }
    }

    public interface CollectToFriendType {
        int COLLECT = 0;
        int DONT_COLLECT = 1;
        String[] nickNames = {"选中帮抽卡", "选中不帮抽卡"};
    }


    //万能卡使用方法
    public interface UniversalCardUseType {

        /** 所有图鉴都可使用 */
        int ALL_COLLECTION = 0;

        /** 排除当前图鉴 */
        int EXCLUDE_CURRENT = 1;

        /** 优先合成进度最高的图鉴 */
        int PRIORITY_MAX_PROGRESS = 2;

        String[] nickNames = {
                "所有图鉴",
                "除当前图鉴",
                "优先合成进度最高"
        };
    }


}
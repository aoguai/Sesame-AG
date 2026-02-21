import json
import os
from typing import Dict, Any, List

from fastapi import FastAPI, Request, Response
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

app = FastAPI()

# ================= 配置路径 =================
WEB_DIR = "web"  # 你的 assets/web 文件夹路径
CONFIG_FILE = "config.json"
FRIEND_FILE = "friend.json"

# ================= 1. 元数据定义 (保持不变) =================
TABS_META = json.loads(
    '[{"groupCode":"BASE","modelCode":"BaseModel","modelIcon":"BaseModel.png","modelName":"基础"},{"groupCode":"FOREST","modelCode":"AntForest","modelIcon":"AntForest.png","modelName":"森林"},{"groupCode":"FARM","modelCode":"AntFarm","modelIcon":"AntFarm.png","modelName":"庄园"},{"groupCode":"FOREST","modelCode":"AntOcean","modelIcon":"AntOcean.png","modelName":"海洋"},{"groupCode":"ORCHARD","modelCode":"AntOrchard","modelIcon":"AntOrchard.png","modelName":"农场"},{"groupCode":"STALL","modelCode":"AntStall","modelIcon":"AntStall.png","modelName":"新村"},{"groupCode":"FOREST","modelCode":"AntDodo","modelIcon":"AntDodo.png","modelName":"神奇物种"},{"groupCode":"FOREST","modelCode":"AntCooperate","modelIcon":"AntCooperate.png","modelName":"合种"},{"groupCode":"SPORTS","modelCode":"AntSports","modelIcon":"AntSports.png","modelName":"运动"},{"groupCode":"MEMBER","modelCode":"AntMember","modelIcon":"AntMember.png","modelName":"会员"},{"groupCode":"FOREST","modelCode":"AncientTree","modelIcon":"AncientTree.png","modelName":"古树"},{"groupCode":"OTHER","modelCode":"GreenFinance","modelIcon":"GreenFinance.png","modelName":"绿色经营"},{"groupCode":"FOREST","modelCode":"Reserve","modelIcon":"Reserve.png","modelName":"保护地"},{"groupCode":"OTHER","modelCode":"AnswerAI","modelIcon":"AnswerAI.svg","modelName":"AI答题"}]'
)

MODELS_META = {
    "BaseModel": json.loads(
        '[{"code":"enable","configValue":"true","name":"启用模块","type":"BOOLEAN"},{"code":"stayAwake","configValue":"true","name":"保持唤醒","type":"BOOLEAN"},{"code":"manualTriggerAutoSchedule","configValue":"false","name":"手动触发支付宝运行","type":"BOOLEAN"},{"code":"checkInterval","configValue":"50","name":"执行间隔(分钟)","type":"MULTIPLY_INTEGER"},{"code":"taskExecutionRounds","configValue":"2","name":"任务执行轮数","type":"INTEGER"},{"code":"modelSleepTime","configValue":"0200-0201","name":"模块休眠时间(范围|关闭:-1)","type":"LIST"},{"code":"execAtTimeList","configValue":"0010,0030,0100,0700,0730,1200,1230,1700,1730,2000,2030,2359","name":"定时执行(关闭:-1)","type":"LIST"},{"code":"wakenAtTimeList","configValue":"0010,0030,0100,0650,2350","name":"定时唤醒(关闭:-1)","type":"LIST"},{"code":"energyTime","configValue":"0700-0730","name":"只收能量时间(范围|关闭:-1)","type":"LIST"},{"code":"timedTaskModel","configValue":"0","expandKey":["🤖系统计时","📦程序计时"],"name":"定时任务模式","type":"CHOICE"},{"code":"timeoutRestart","configValue":"true","name":"超时重启","type":"BOOLEAN"},{"code":"waitWhenException","configValue":"60","name":"异常等待时间(分钟)","type":"MULTIPLY_INTEGER"},{"code":"errNotify","configValue":"false","name":"开启异常通知","type":"BOOLEAN"},{"code":"setMaxErrorCount","configValue":"8","name":"异常次数阈值","type":"INTEGER"},{"code":"newRpc","configValue":"true","name":"使用新接口(最低支持v10.3.96.8100)","type":"BOOLEAN"},{"code":"debugMode","configValue":"true","name":"开启抓包(基于新接口)","type":"BOOLEAN"},{"code":"sendHookData","configValue":"false","name":"启用Hook数据转发","type":"BOOLEAN"},{"code":"sendHookDataUrl","configValue":"http://127.0.0.1:9527/hook","name":"Hook数据转发地址","type":"STRING"},{"code":"batteryPerm","configValue":"true","name":"为支付宝申请后台运行权限","type":"BOOLEAN"},{"code":"enableCaptchaUIHook","configValue":"false","name":"🛡️拒绝访问VPN弹窗拦截","type":"BOOLEAN"},{"code":"recordLog","configValue":"true","name":"全部 | 记录record日志","type":"BOOLEAN"},{"code":"runtimeLog","configValue":"false","name":"全部 | 记录runtime日志","type":"BOOLEAN"},{"code":"showToast","configValue":"true","name":"气泡提示","type":"BOOLEAN"},{"code":"enableOnGoing","configValue":"false","name":"开启状态栏禁删","type":"BOOLEAN"},{"code":"languageSimplifiedChinese","configValue":"true","name":"只显示中文并设置时区","type":"BOOLEAN"},{"code":"toastOffsetY","configValue":"99","name":"气泡纵向偏移","type":"INTEGER"}]'
    ),
    "AntForest": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启森林","type":"BOOLEAN"},{"code":"collectEnergy","configValue":"false","name":"收集能量 | 开关","type":"BOOLEAN"},{"code":"batchRobEnergy","configValue":"false","name":"一键收取 | 开关","type":"BOOLEAN"},{"code":"pkEnergy","configValue":"false","name":"Pk榜收取 | 开关","type":"BOOLEAN"},{"code":"closeWhackMole","configValue":"false","name":"🎮 6秒拼手速 | 开关","type":"BOOLEAN"},{"code":"energyRain","configValue":"false","name":"能量雨 | 开关","type":"BOOLEAN"},{"code":"energyRainTime","configValue":"0810","name":"能量雨 | 默认8点10分后执行","type":"STRING"},{"code":"dontCollectList","configValue":"[ ]","name":"不收能量 | 配置列表","type":"SELECT"},{"code":"giveEnergyRainList","configValue":"[ ]","name":"赠送能量雨 | 配置列表","type":"SELECT"},{"code":"energyRainChance","configValue":"false","name":"兑换使用能量雨次卡 | 开关","type":"BOOLEAN"},{"code":"collectWateringBubble","configValue":"false","name":"收取浇水金球 | 开关","type":"BOOLEAN"},{"code":"doubleCard","configValue":"0","expandKey":["关闭","所有道具","限时道具"],"name":"双击卡开关 | 消耗类型","type":"CHOICE"},{"code":"doubleCountLimit","configValue":"6","name":"双击卡 | 使用次数","type":"INTEGER"},{"code":"doubleCardTime","configValue":"0700,0730,1200,1230,1700,1730,2000,2030,2359","name":"双击卡 | 使用时间/范围","type":"LIST"},{"code":"DoubleCardConstant","configValue":"false","name":"限时双击永动机 | 开关","type":"BOOLEAN"},{"code":"bubbleBoostCard","configValue":"0","expandKey":["关闭","所有道具","限时道具"],"name":"加速器开关 | 消耗类型","type":"CHOICE"},{"code":"bubbleBoostTime","configValue":"0030,0630,0700,1200,1730,2359","name":"加速器 | 使用时间/不能范围","type":"LIST"},{"code":"shieldCard","configValue":"0","expandKey":["关闭","所有道具","限时道具"],"name":"保护罩开关 | 消耗类型","type":"CHOICE"},{"code":"shieldCardConstant","configValue":"false","name":"限时保护永动机 | 开关","type":"BOOLEAN"},{"code":"energyBombCardType","configValue":"0","desc":"若开启了保护罩，则不会使用炸弹卡","expandKey":["关闭","所有道具","限时道具"],"name":"炸弹卡开关 | 消耗类型","type":"CHOICE"},{"code":"robExpandCard","configValue":"0","expandKey":["关闭","所有道具","限时道具"],"name":"1.1倍能量卡开关 | 消耗类型","type":"CHOICE"},{"code":"robExpandCardTime","configValue":"0700,0730,1200,1230,1700,1730,2000,2030,2359","name":"1.1倍能量卡 | 使用时间/不能范围","type":"LIST"},{"code":"stealthCard","configValue":"0","expandKey":["关闭","所有道具","限时道具"],"name":"隐身卡开关 | 消耗类型","type":"CHOICE"},{"code":"stealthCardConstant","configValue":"false","name":"限时隐身永动机 | 开关","type":"BOOLEAN"},{"code":"returnWater10","configValue":"0","name":"返水 | 10克需收能量(关闭:0)","type":"INTEGER"},{"code":"returnWater18","configValue":"0","name":"返水 | 18克需收能量(关闭:0)","type":"INTEGER"},{"code":"returnWater33","configValue":"0","name":"返水 | 33克需收能量(关闭:0)","type":"INTEGER"},{"code":"waterFriendList","configValue":"{ }","desc":"设置浇水次数","name":"浇水 | 好友列表","type":"SELECT_AND_COUNT"},{"code":"waterFriendCount","configValue":"66","name":"浇水 | 克数(10 18 33 66)","type":"INTEGER"},{"code":"notifyFriend","configValue":"false","name":"浇水 | 通知好友","type":"BOOLEAN"},{"code":"giveProp","configValue":"false","name":"赠送道具","type":"BOOLEAN"},{"code":"whoYouWantToGiveTo","configValue":"[ ]","desc":"所有可赠送的道具将全部赠","name":"赠送 | 道具","type":"SELECT"},{"code":"collectProp","configValue":"false","name":"收集道具","type":"BOOLEAN"},{"code":"helpFriendCollectType","configValue":"0","expandKey":["关闭","选中复活","选中不复活"],"name":"复活能量 | 选项","type":"CHOICE"},{"code":"helpFriendCollectList","configValue":"[ ]","name":"复活能量 | 好友列表","type":"SELECT"},{"code":"alternativeAccountList","configValue":"[ ]","name":"小号列表","type":"SELECT"},{"code":"vitalityExchange","configValue":"true","name":"活力值 | 兑换开关","type":"BOOLEAN"},{"code":"vitalityExchangeList","configValue":"{\\n  \\"CR20230516000371\\" : 1\\n}","desc":"兑换次数","name":"活力值 | 兑换列表","type":"SELECT_AND_COUNT"},{"code":"userPatrol","configValue":"false","name":"保护地巡护","type":"BOOLEAN"},{"code":"combineAnimalPiece","configValue":"false","name":"合成动物碎片","type":"BOOLEAN"},{"code":"consumeAnimalProp","configValue":"false","name":"派遣动物伙伴","type":"BOOLEAN"},{"code":"receiveForestTaskAward","configValue":"false","name":"森林任务","type":"BOOLEAN"},{"code":"forestChouChouLe","configValue":"false","name":"森林寻宝任务","type":"BOOLEAN"},{"code":"collectGiftBox","configValue":"false","name":"领取礼盒","type":"BOOLEAN"},{"code":"medicalHealth","configValue":"false","name":"健康医疗任务 | 开关","type":"BOOLEAN"},{"code":"medicalHealthOption","configValue":"[ ]","desc":"医疗健康需要先完成一次医疗打卡","name":"健康医疗 | 选项","type":"SELECT"},{"code":"forestMarket","configValue":"false","name":"森林集市","type":"BOOLEAN"},{"code":"youthPrivilege","configValue":"false","name":"青春特权 | 森林道具","type":"BOOLEAN"},{"code":"studentCheckIn","configValue":"false","name":"青春特权 | 签到红包","type":"BOOLEAN"},{"code":"ecoLife","configValue":"false","name":"绿色行动 | 开关","type":"BOOLEAN"},{"code":"ecoLifeTime","configValue":"0800","name":"绿色行动 | 默认8点后执行","type":"STRING"},{"code":"ecoLifeOpen","configValue":"false","name":"绿色任务 |  自动开通","type":"BOOLEAN"},{"code":"ecoLifeOption","configValue":"[ ]","desc":"光盘行动需要先完成一次光盘打卡","name":"绿色行动 | 选项","type":"SELECT"},{"code":"queryInterval","configValue":"1000-2000","name":"查询间隔(毫秒或毫秒范围)","type":"STRING"},{"code":"collectInterval","configValue":"1000-1500","name":"收取间隔(毫秒或毫秒范围)","type":"STRING"},{"code":"doubleCollectInterval","configValue":"800-2400","name":"双击间隔(毫秒或毫秒范围)","type":"STRING"},{"code":"balanceNetworkDelay","configValue":"true","name":"平衡网络延迟","type":"BOOLEAN"},{"code":"advanceTime","configValue":"0","name":"提前时间(毫秒)","type":"INTEGER"},{"code":"tryCount","configValue":"1","name":"尝试收取(次数)","type":"INTEGER"},{"code":"retryInterval","configValue":"1200","name":"重试间隔(毫秒)","type":"INTEGER"},{"code":"cycleinterval","configValue":"5000","name":"循环间隔(毫秒)","type":"INTEGER"},{"code":"showBagList","configValue":"true","name":"显示背包内容","type":"BOOLEAN"}]'
    ),
    "AntFarm": json.loads(
        '[{"code":"enable","configValue":"true","name":"开启庄园","type":"BOOLEAN"},{"code":"sleepTime","configValue":"2330","name":"小鸡睡觉时间(关闭:-1)","type":"STRING"},{"code":"sleepMinutes","configValue":"360","name":"小鸡睡觉时长(分钟)","type":"INTEGER"},{"code":"recallAnimalType","configValue":"0","expandKey":["始终召回","偷吃召回","饥饿召回","暂不召回"],"name":"召回小鸡","type":"CHOICE"},{"code":"rewardFriend","configValue":"false","name":"打赏好友","type":"BOOLEAN"},{"code":"feedAnimal","configValue":"false","name":"自动喂小鸡","type":"BOOLEAN"},{"code":"feedFriendAnimalList","configValue":"{ }","name":"喂小鸡好友列表","type":"SELECT_AND_COUNT"},{"code":"getFeed","configValue":"false","name":"一起拿饲料","type":"BOOLEAN"},{"code":"getFeedType","configValue":"0","expandKey":["选中赠送","随机赠送"],"name":"一起拿饲料 | 动作","type":"CHOICE"},{"code":"getFeedlList","configValue":"[ ]","name":"一起拿饲料 | 好友列表","type":"SELECT"},{"code":"acceptGift","configValue":"false","name":"收麦子","type":"BOOLEAN"},{"code":"visitFriendList","configValue":"{ }","name":"送麦子好友列表","type":"SELECT_AND_COUNT"},{"code":"hireAnimal","configValue":"false","name":"雇佣小鸡 | 开启","type":"BOOLEAN"},{"code":"hireAnimalType","configValue":"1","expandKey":["选中雇佣","选中不雇佣"],"name":"雇佣小鸡 | 动作","type":"CHOICE"},{"code":"hireAnimalList","configValue":"[ ]","name":"雇佣小鸡 | 好友列表","type":"SELECT"},{"code":"sendBackAnimal","configValue":"false","name":"遣返 | 开启","type":"BOOLEAN"},{"code":"sendBackAnimalWay","configValue":"1","expandKey":["攻击","常规"],"name":"遣返 | 方式","type":"CHOICE"},{"code":"sendBackAnimalType","configValue":"1","expandKey":["选中遣返","选中不遣返"],"name":"遣返 | 动作","type":"CHOICE"},{"code":"dontSendFriendList","configValue":"[ ]","name":"遣返 | 好友列表","type":"SELECT"},{"code":"notifyFriend","configValue":"false","name":"通知赶鸡 | 开启","type":"BOOLEAN"},{"code":"notifyFriendType","configValue":"0","expandKey":["选中通知","选中不通知"],"name":"通知赶鸡 | 动作","type":"CHOICE"},{"code":"notifyFriendList","configValue":"[ ]","name":"通知赶鸡 | 好友列表","type":"SELECT"},{"code":"donation","configValue":"false","name":"每日捐蛋 | 开启","type":"BOOLEAN"},{"code":"donationCount","configValue":"0","expandKey":["随机一次","随机多次"],"name":"每日捐蛋 | 次数","type":"CHOICE"},{"code":"useBigEaterTool","configValue":"false","name":"加饭卡 | 使用","type":"BOOLEAN"},{"code":"useAccelerateTool","configValue":"false","name":"加速卡 | 使用","type":"BOOLEAN"},{"code":"useAccelerateToolContinue","configValue":"false","name":"加速卡 | 连续使用","type":"BOOLEAN"},{"code":"useAccelerateToolWhenMaxEmotion","configValue":"false","name":"加速卡 | 仅在满状态时使用","type":"BOOLEAN"},{"code":"useSpecialFood","configValue":"false","name":"使用特殊食品","type":"BOOLEAN"},{"code":"useNewEggCard","configValue":"false","name":"使用新蛋卡","type":"BOOLEAN"},{"code":"doFarmTask","configValue":"false","name":"做饲料任务","type":"BOOLEAN"},{"code":"doFarmTaskTime","configValue":"0830","name":"饲料任务执行时间 | 默认8:30后执行","type":"STRING"},{"code":"receiveFarmTaskAward","configValue":"false","name":"收取饲料奖励","type":"BOOLEAN"},{"code":"receiveFarmToolReward","configValue":"false","name":"收取道具奖励","type":"BOOLEAN"},{"code":"harvestProduce","configValue":"false","name":"收获爱心鸡蛋","type":"BOOLEAN"},{"code":"kitchen","configValue":"false","name":"小鸡厨房","type":"BOOLEAN"},{"code":"chickenDiary","configValue":"false","name":"小鸡日记","type":"BOOLEAN"},{"code":"diaryTietze","configValue":"false","name":"小鸡日记 | 贴贴","type":"BOOLEAN"},{"code":"collectChickenDiary","configValue":"0","expandKey":["不开启","一次","当月","所有"],"name":"小鸡日记 | 点赞","type":"CHOICE"},{"code":"enableChouchoule","configValue":"true","name":"开启小鸡抽抽乐","type":"BOOLEAN"},{"code":"enableChouchouleTime","configValue":"0900","name":"小鸡抽抽乐执行时间 | 默认9:00后执行","type":"STRING"},{"code":"listOrnaments","configValue":"false","name":"小鸡每日换装","type":"BOOLEAN"},{"code":"enableDdrawGameCenterAward","configValue":"false","name":"开宝箱","type":"BOOLEAN"},{"code":"recordFarmGame","configValue":"false","name":"游戏改分(星星球、登山赛、飞行赛、揍小鸡)","type":"BOOLEAN"},{"code":"farmGameTime","configValue":"2200-2400","name":"小鸡游戏时间(范围)","type":"LIST"},{"code":"family","configValue":"false","name":"家庭 | 开启","type":"BOOLEAN"},{"code":"familyOptions","configValue":"[ ]","name":"家庭 | 选项","type":"SELECT"},{"code":"notInviteList","configValue":"[ ]","name":"家庭 | 好友分享排除列表","type":"SELECT"},{"code":"paradiseCoinExchangeBenefit","configValue":"false","name":"小鸡乐园 | 兑换权益","type":"BOOLEAN"},{"code":"paradiseCoinExchangeBenefitList","configValue":"[ ]","name":"小鸡乐园 | 权益列表","type":"SELECT"},{"code":"visitAnimal","configValue":"false","name":"到访小鸡送礼","type":"BOOLEAN"}]'
    ),
    "AntOcean": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启海洋","type":"BOOLEAN"},{"code":"dailyOceanTask","configValue":"false","name":"海洋任务","type":"BOOLEAN"},{"code":"cleanOcean","configValue":"false","name":"清理 | 开启","type":"BOOLEAN"},{"code":"cleanOceanType","configValue":"1","expandKey":["选中清理","选中不清理"],"name":"清理 | 动作","type":"CHOICE"},{"code":"cleanOceanList","configValue":"[ ]","name":"清理 | 好友列表","type":"SELECT"},{"code":"exchangeProp","configValue":"false","name":"神奇海洋 | 制作万能拼图","type":"BOOLEAN"},{"code":"usePropByType","configValue":"false","name":"神奇海洋 | 使用万能拼图","type":"BOOLEAN"},{"code":"userprotectType","configValue":"0","expandKey":["不保护","保护全部","仅保护沙滩"],"name":"保护 | 类型","type":"CHOICE"},{"code":"protectOceanList","configValue":"{ }","name":"保护 | 海洋列表","type":"SELECT_AND_COUNT"},{"code":"PDL_task","configValue":"false","name":"潘多拉任务","type":"BOOLEAN"}]'
    ),
    "AntOrchard": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启农场","type":"BOOLEAN"},{"code":"executeInterval","configValue":"500","name":"执行间隔(毫秒)","type":"INTEGER"},{"code":"receiveOrchardTaskAward","configValue":"false","name":"收取农场任务奖励","type":"BOOLEAN"},{"code":"orchardSpreadManureCount","configValue":"5","name":"农场每日施肥次数","type":"INTEGER"}]'
    ),
    "AntStall": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启新村","type":"BOOLEAN"},{"code":"stallAutoOpen","configValue":"false","name":"摆摊 | 开启","type":"BOOLEAN"},{"code":"stallOpenType","configValue":"0","expandKey":["选中摆摊","选中不摆摊"],"name":"摆摊 | 动作","type":"CHOICE"},{"code":"stallOpenList","configValue":"[ ]","name":"摆摊 | 好友列表","type":"SELECT"},{"code":"stallAutoClose","configValue":"false","name":"收摊 | 开启","type":"BOOLEAN"},{"code":"stallSelfOpenTime","configValue":"120","name":"收摊 | 摆摊时长(分钟)","type":"INTEGER"},{"code":"stallAutoTicket","configValue":"false","name":"贴罚单 | 开启","type":"BOOLEAN"},{"code":"stallTicketType","configValue":"1","expandKey":["选中贴罚单","选中不贴罚单"],"name":"贴罚单 | 动作","type":"CHOICE"},{"code":"stallTicketList","configValue":"[ ]","name":"贴罚单 | 好友列表","type":"SELECT"},{"code":"stallThrowManure","configValue":"false","name":"丢肥料 | 开启","type":"BOOLEAN"},{"code":"stallThrowManureType","configValue":"1","expandKey":["选中丢肥料","选中不丢肥料"],"name":"丢肥料 | 动作","type":"CHOICE"},{"code":"stallThrowManureList","configValue":"[ ]","name":"丢肥料 | 好友列表","type":"SELECT"},{"code":"stallInviteShop","configValue":"false","name":"邀请摆摊 | 开启","type":"BOOLEAN"},{"code":"stallInviteShopType","configValue":"0","expandKey":["选中邀请","选中不邀请"],"name":"邀请摆摊 | 动作","type":"CHOICE"},{"code":"stallInviteShopList","configValue":"[ ]","name":"邀请摆摊 | 好友列表","type":"SELECT"},{"code":"stallAllowOpenReject","configValue":"false","name":"请走小摊 | 开启","type":"BOOLEAN"},{"code":"stallAllowOpenTime","configValue":"121","name":"请走小摊 | 允许摆摊时长(分钟)","type":"INTEGER"},{"code":"stallWhiteList","configValue":"[ ]","name":"请走小摊 | 白名单(超时也不赶)","type":"SELECT"},{"code":"stallBlackList","configValue":"[ ]","name":"请走小摊 | 黑名单(不超时也赶)","type":"SELECT"},{"code":"stallAutoTask","configValue":"false","name":"自动任务","type":"BOOLEAN"},{"code":"stallReceiveAward","configValue":"false","name":"自动领奖","type":"BOOLEAN"},{"code":"stallDonate","configValue":"false","name":"自动捐赠","type":"BOOLEAN"},{"code":"roadmap","configValue":"false","name":"自动进入下一村","type":"BOOLEAN"},{"code":"stallInviteRegister","configValue":"false","name":"邀请 | 邀请好友开通新村","type":"BOOLEAN"},{"code":"stallInviteRegisterList","configValue":"[ ]","name":"邀请 | 好友列表","type":"SELECT"},{"code":"assistFriendList","configValue":"[ ]","name":"助力好友列表","type":"SELECT"}]'
    ),
    "AntDodo": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启神奇物种","type":"BOOLEAN"},{"code":"collectToFriend","configValue":"false","name":"帮抽卡 | 开启","type":"BOOLEAN"},{"code":"collectToFriendType","configValue":"0","expandKey":["选中帮抽卡","选中不帮抽卡"],"name":"帮抽卡 | 动作","type":"CHOICE"},{"code":"collectToFriendList","configValue":"[ ]","name":"帮抽卡 | 好友列表","type":"SELECT"},{"code":"sendFriendCard","configValue":"[ ]","name":"送卡片好友列表(当前图鉴所有卡片)","type":"SELECT"},{"code":"useProp","configValue":"false","name":"使用道具 | 所有","type":"BOOLEAN"},{"code":"usePropCollectTimes7Days","configValue":"false","name":"使用道具 | 抽卡道具","type":"BOOLEAN"},{"code":"usePropCollectHistoryAnimal7Days","configValue":"false","name":"使用道具 | 抽历史卡道具","type":"BOOLEAN"},{"code":"usePropCollectToFriendTimes7Days","configValue":"false","name":"使用道具 | 抽好友卡道具","type":"BOOLEAN"},{"code":"autoGenerateBook","configValue":"false","name":"自动合成图鉴","type":"BOOLEAN"}]'
    ),
    "AntCooperate": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启合种","type":"BOOLEAN"},{"code":"cooperateWater","configValue":"false","name":"合种浇水|开启","type":"BOOLEAN"},{"code":"cooperateWaterList","configValue":"{ }","desc":"开启合种浇水后执行一次重载","name":"合种浇水列表","type":"SELECT_AND_COUNT"},{"code":"cooperateWaterTotalLimitList","configValue":"{ }","name":"浇水总量限制列表","type":"SELECT_AND_COUNT"},{"code":"cooperateSendCooperateBeckon","configValue":"false","name":"合种 | 召唤队友浇水| 仅队长 ","type":"BOOLEAN"},{"code":"loveCooperateWater","configValue":"false","name":"真爱合种 | 浇水","type":"BOOLEAN"},{"code":"loveCooperateWaterNum","configValue":"20","name":"真爱合种 | 浇水克数(最低20)","type":"INTEGER"}]'
    ),
    "AntSports": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启运动","type":"BOOLEAN"},{"code":"walk","configValue":"false","name":"行走路线 | 开启","type":"BOOLEAN"},{"code":"walkPathTheme","configValue":"0","expandKey":["大美中国","公益一小步","登顶芝麻山","维C大挑战","龙年祈福"],"name":"行走路线 | 主题","type":"CHOICE"},{"code":"walkCustomPath","configValue":"false","name":"行走路线 | 开启自定义路线","type":"BOOLEAN"},{"code":"walkCustomPathId","configValue":"p0002023122214520001","name":"行走路线 | 自定义路线代码(debug)","type":"STRING"},{"code":"openTreasureBox","configValue":"false","name":"开启宝箱","type":"BOOLEAN"},{"code":"sportsTasks","configValue":"false","name":"开启运动任务","type":"BOOLEAN"},{"code":"sportsTaskBlacklist","configValue":"开通包裹查询服务,添加支付宝小组件,领取价值1.7万元配置,支付宝积分可兑券","name":"运动任务黑名单 | 任务名称(用,分隔)","type":"STRING"},{"code":"receiveCoinAsset","configValue":"false","name":"收能量🎈","type":"BOOLEAN"},{"code":"donateCharityCoin","configValue":"false","name":"捐能量🎈 | 开启","type":"BOOLEAN"},{"code":"donateCharityCoinType","configValue":"0","expandKey":["捐赠一个项目","捐赠所有项目"],"name":"捐能量🎈 | 方式","type":"CHOICE"},{"code":"donateCharityCoinAmount","configValue":"100","name":"捐能量🎈 | 数量(每次)","type":"INTEGER"},{"code":"neverlandTask","configValue":"false","name":"健康岛 | 任务","type":"BOOLEAN"},{"code":"neverlandGrid","configValue":"false","name":"健康岛 | 自动走路建造","type":"BOOLEAN"},{"code":"neverlandGridStepCount","configValue":"20","name":"健康岛 | 今日走路最大次数","type":"INTEGER"},{"code":"battleForFriends","configValue":"false","name":"抢好友 | 开启","type":"BOOLEAN"},{"code":"battleForFriendType","configValue":"0","expandKey":["选中抢","选中不抢"],"name":"抢好友 | 动作","type":"CHOICE"},{"code":"originBossIdList","configValue":"[ ]","name":"抢好友 | 好友列表","type":"SELECT"},{"code":"trainFriend","configValue":"false","name":"训练好友 | 开启","type":"BOOLEAN"},{"code":"zeroCoinLimit","configValue":"5","name":"训练好友 | 0金币上限次数当天关闭","type":"INTEGER"},{"code":"tiyubiz","configValue":"false","name":"文体中心","type":"BOOLEAN"},{"code":"minExchangeCount","configValue":"0","name":"最小捐步步数","type":"INTEGER"},{"code":"latestExchangeTime","configValue":"22","name":"最晚捐步时间(24小时制)","type":"INTEGER"},{"code":"syncStepCount","configValue":"22000","name":"自定义同步步数","type":"INTEGER"},{"code":"coinExchangeDoubleCard","configValue":"false","name":"能量🎈兑换限时能量双击卡","type":"BOOLEAN"}]'
    ),
    "AntMember": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启会员","type":"BOOLEAN"},{"code":"memberSign","configValue":"false","name":"会员签到","type":"BOOLEAN"},{"code":"memberTask","configValue":"false","name":"会员任务","type":"BOOLEAN"},{"code":"memberPointExchangeBenefit","configValue":"false","name":"会员积分 | 兑换权益","type":"BOOLEAN"},{"code":"memberPointExchangeBenefitList","configValue":"[ ]","name":"会员积分 | 权益列表","type":"SELECT"},{"code":"sesameTask","configValue":"false","name":"芝麻信用|芝麻粒信用任务","type":"BOOLEAN"},{"code":"collectSesame","configValue":"false","name":"芝麻信用|芝麻粒领取","type":"BOOLEAN"},{"code":"collectSesameWithOneClick","configValue":"false","name":"芝麻信用|芝麻粒领取使用一键收取","type":"BOOLEAN"},{"code":"sesameAlchemy","configValue":"false","name":"芝麻炼金","type":"BOOLEAN"},{"code":"enableZhimaTree","configValue":"false","name":"芝麻信用|芝麻树","type":"BOOLEAN"},{"code":"collectInsuredGold","configValue":"false","name":"蚂蚁保|保障金领取","type":"BOOLEAN"},{"code":"enableGoldTicket","configValue":"false","name":"黄金票签到","type":"BOOLEAN"},{"code":"enableGoldTicketConsume","configValue":"false","name":"黄金票提取(兑换黄金)","type":"BOOLEAN"},{"code":"enableGameCenter","configValue":"false","name":"游戏中心签到","type":"BOOLEAN"},{"code":"merchantSign","configValue":"false","name":"商家服务|签到","type":"BOOLEAN"},{"code":"merchantKmdk","configValue":"false","name":"商家服务|开门打卡","type":"BOOLEAN"},{"code":"merchantMoreTask","configValue":"false","name":"商家服务|积分任务","type":"BOOLEAN"},{"code":"beanSignIn","configValue":"false","name":"安心豆签到","type":"BOOLEAN"},{"code":"beanExchangeBubbleBoost","configValue":"false","name":"安心豆兑换时光加速器","type":"BOOLEAN"},{"code":"AnnualReview","configValue":"false","name":"年度回顾","type":"BOOLEAN"}]'
    ),
    "AncientTree": json.loads('[{"code":"enable","configValue":"false","name":"开启古树","type":"BOOLEAN"},{"code":"ancientTreeOnlyWeek","configValue":"false","name":"仅星期一、三、五运行保护古树","type":"BOOLEAN"},{"code":"ancientTreeCityCodeList","configValue":"[ ]","name":"古树区划代码列表","type":"SELECT"}]'),
    "GreenFinance": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启绿色经营","type":"BOOLEAN"},{"code":"greenFinanceLsxd","configValue":"false","name":"打卡 | 绿色行动","type":"BOOLEAN"},{"code":"greenFinanceLscg","configValue":"false","name":"打卡 | 绿色采购","type":"BOOLEAN"},{"code":"greenFinanceLsbg","configValue":"false","name":"打卡 | 绿色办公","type":"BOOLEAN"},{"code":"greenFinanceWdxd","configValue":"false","name":"打卡 | 绿色销售","type":"BOOLEAN"},{"code":"greenFinanceLswl","configValue":"false","name":"打卡 | 绿色物流","type":"BOOLEAN"},{"code":"greenFinancePointFriend","configValue":"false","name":"收取 | 好友金币","type":"BOOLEAN"},{"code":"greenFinanceDonation","configValue":"false","name":"捐助 | 快过期金币","type":"BOOLEAN"}]'
    ),
    "Reserve": json.loads('[{"code":"enable","configValue":"false","name":"开启保护地","type":"BOOLEAN"},{"code":"reserveList","configValue":"{ }","name":"保护地列表","type":"SELECT_AND_COUNT"}]'),
    "AnswerAI": json.loads(
        '[{"code":"enable","configValue":"false","name":"开启AI答题","type":"BOOLEAN"},{"code":"useGeminiAI","configValue":"0","expandKey":["通义千问","Gemini","DeepSeek","自定义"],"name":"AI类型","type":"CHOICE"},{"code":"getTongyiAIToken","configValue":"https://help.aliyun.com/zh/dashscope/developer-reference/acquisition-and-configuration-of-api-key","name":"通义千问 | 获取令牌","type":"URL_TEXT"},{"code":"tongYiToken","configValue":"","name":"qwen-turbo | 设置令牌","type":"STRING"},{"code":"getGeminiAIToken","configValue":"https://aistudio.google.com/app/apikey","name":"Gemini | 获取令牌","type":"URL_TEXT"},{"code":"GeminiAIToken","configValue":"","name":"gemini-1.5-flash | 设置令牌","type":"STRING"},{"code":"getDeepSeekToken","configValue":"https://platform.deepseek.com/usage","name":"DeepSeek | 获取令牌","type":"URL_TEXT"},{"code":"DeepSeekToken","configValue":"","name":"DeepSeek-R1 | 设置令牌","type":"STRING"},{"code":"getCustomServiceToken","configValue":"下面这个不用动可以白嫖到3月10号让我们感谢讯飞大善人🙏","name":"粉丝福利😍","type":"READ_TEXT"},{"code":"CustomServiceToken","configValue":"sk-pQF9jek0CTTh3boKDcA9DdD7340a4e929eD00a13F681Cd8e","name":"自定义服务 | 设置令牌","type":"STRING"},{"code":"CustomServiceBaseUrl","configValue":"https://maas-api.cn-huabei-1.xf-yun.com/v1","name":"自定义服务 | 设置BaseUrl","type":"STRING"},{"code":"CustomServiceModel","configValue":"xdeepseekr1","name":"自定义服务 | 设置模型","type":"STRING"}]'
    ),
}

# ================= 2. 数据处理工具函数 =================


def load_json(filepath):
    if not os.path.exists(filepath):
        print(f"警告: 文件不存在 {filepath}")
        return {}
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)


def get_merged_models():
    """读取 config.json 并合并到 Metadata 中"""
    config_data = load_json(CONFIG_FILE)
    model_fields_map = config_data.get("modelFieldsMap", {})
    merged_data = json.loads(json.dumps(MODELS_META))
    for model_name, fields_list in merged_data.items():
        config_values = model_fields_map.get(model_name, {})
        for field_def in fields_list:
            code = field_def["code"]
            if code in config_values:
                raw_value = config_values[code].get("value")
                if isinstance(raw_value, bool):
                    field_def["configValue"] = str(raw_value).lower()
                elif isinstance(raw_value, (dict, list)):
                    field_def["configValue"] = json.dumps(raw_value, ensure_ascii=False)
                else:
                    field_def["configValue"] = str(raw_value)
    return merged_data


def get_friend_list():
    """读取 friend.json，优先使用 fullName（包含账号信息）"""
    friends_map = load_json(FRIEND_FILE)
    friend_list = []
    for user_id, user_info in friends_map.items():
        # 优先使用 fullName (格式如：超|程超(159******79))
        # 其次使用 showName
        name = user_info.get("fullName") or user_info.get("showName") or user_info.get("nickName") or user_id
        friend_list.append({"id": user_id, "name": name})
    return friend_list


def get_injection_script():
    tabs_json = json.dumps(TABS_META, ensure_ascii=False)
    # merged_models = get_merged_models()
    # models_json = json.dumps(merged_models, ensure_ascii=False)

    models_json = json.loads(json.dumps(MODELS_META))
    friend_data = get_friend_list()
    friend_data_json = json.dumps(friend_data, ensure_ascii=False)

    # 注意：这里去掉了 alert，改成了 console.log 以避免浏览器拦截报错
    return f"""
    <script>
    (function() {{
        console.log("🚀 FastAPI Debug Mode: Started");
        
        var MOCK_TABS = {tabs_json};
        var MOCK_MODELS = {models_json};
        var MOCK_FRIENDS = {friend_data_json};

        window.HOOK = {{
            getTabs: function() {{
                return JSON.stringify(MOCK_TABS);
            }},
            
            getBuildInfo: function() {{
                return "Sesame-TK:Byseven-Offical-Debug";
            }},
            
            isNightMode: function() {{
                return false;
            }},
            
            getModel: function(modelCode) {{
                console.log("[HOOK] getModel:", modelCode);
                var data = MOCK_MODELS[modelCode];
                return data ? JSON.stringify(data) : "[]";
            }},
            
            setModel: function(modelCode, jsonStr) {{
                console.log("[HOOK] setModel:", modelCode);
                console.log("Payload:", JSON.parse(jsonStr));
                return "SUCCESS";
            }},
            
            getField: function(modelCode, fieldCode) {{
                console.log("[HOOK] getField:", modelCode, fieldCode);
                var result = {{
                   "code": fieldCode,
                   "expandValue": MOCK_FRIENDS
                }};
                return JSON.stringify(result);
            }},
            
            saveOnExit: function() {{
                console.log("[HOOK] saveOnExit called");
                alert("模拟: Java端执行保存并关闭页面");
                return true;
            }},
            
            Log: function(msg) {{
                console.log("[Android Log]:", msg);
            }}
            
        }};

        window.Android = {{
            onBackPressed: function() {{
                console.log("模拟: Android 返回键");
            }},
            onExit: function() {{
                console.log("模拟: Android 退出");
            }}
        }};
    }})();
    </script>
    """


# ================= 3. 路由定义 =================

# 挂载静态资源
app.mount("/css", StaticFiles(directory=os.path.join(WEB_DIR, "css")), name="css")
app.mount("/js", StaticFiles(directory=os.path.join(WEB_DIR, "js")), name="js")
app.mount("/images", StaticFiles(directory=os.path.join(WEB_DIR, "images")), name="images")


@app.get("/")
def index():
    return serve_html("index.html")


@app.get("/{filename}")
def serve_html(filename: str):
    file_path = os.path.join(WEB_DIR, filename)
    if not filename.endswith(".html") or not os.path.exists(file_path):
        return Response(status_code=404)
    with open(file_path, "r", encoding="utf-8") as f:
        html_content = f.read()

    injection_script = get_injection_script()
    if "<head>" in html_content:
        html_content = html_content.replace("<head>", "<head>" + injection_script, 1)
    else:
        html_content = injection_script + html_content

    return HTMLResponse(content=html_content)


if __name__ == "__main__":
    import uvicorn

    print(f"正在启动服务: http://127.0.0.1:8080/index.html")
    uvicorn.run("webui:app", host="127.0.0.1", port=8080, reload=True)

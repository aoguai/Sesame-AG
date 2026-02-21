package fansirsqi.xposed.sesame.util

/**
 * 默认黑名单列表（包含常见无法完成的任务）
 *
 * 使用方法：
 * 1. 检查任务是否在黑名单中（模糊匹配）：
 *    if (TaskBlacklist.isTaskInBlacklist(taskInfo)) { 跳过任务 }
 *
 * 3. 根据错误码自动添加任务到黑名单：
 *    TaskBlacklist.autoAddToBlacklist(taskId, taskTitle, errorCode)
 *
 * 4. 手动添加/移除任务：
 *    TaskBlacklist.addToBlacklist(taskId)
 *    TaskBlacklist.removeFromBlacklist(taskId)
 */
val defaultBlacklist = setOf(
    // 芝麻信用任务
    "每日施肥领水果",           // 需要淘宝操作
    "坚持种水果",              // 需要淘宝操作
    "坚持去玩休闲小游戏",       // 需要游戏操作
    "去AQapp提问",            // 需要下载APP
    "去AQ提问",               // 需要下载APP
    "坚持看直播领福利",        // 需要淘宝直播
    "去淘金币逛一逛",          // 需要淘宝操作
    "坚持攒保障金",            // 参数错误：promiseActivityExtCheck
    "芝麻租赁下单得芝麻粒",     // 需要租赁操作
    "去玩小游戏",              // 参数错误：promiseActivityExtCheck
    "浏览租赁商家小程序",       // 需要小程序操作
    "订阅小组件",              // 参数错误：promiseActivityExtCheck
    "租1笔图书",               // 参数错误：promiseActivityExtCheck
    "去订阅芝麻小组件",         // 参数错误：promiseActivityExtCheck
    "坚持攒保障",              // 参数错误：promiseActivityExtCheck（与"坚持攒保障金"类似，防止匹配遗漏）
    "逛租赁会场",              // 操作太频繁：OP_REPEAT_CHECK
    "去花呗翻卡",               // 操作太频繁：OP_REPEAT_CHECK
    "逛网商福利",              // 操作太频繁：OP_REPEAT_CHECK
    "领视频红包",              // 操作太频繁：OP_REPEAT_CHECK
    "领点餐优惠",               // 操作太频繁：OP_REPEAT_CHECK
    "去抛竿钓鱼",              // 操作太频繁：OP_REPEAT_CHECK
    "逛商家积分兑好物",         // 操作太频繁：OP_REPEAT_CHECK
    "坚持浏览乐游记",           // 操作太频繁：OP_REPEAT_CHECK
    "去体验先用后付",           // 操作太频繁：OP_REPEAT_CHECK
    "0.1元起租会员攒粒",        // 参数错误：ILLEGAL_ARGUMENT
    "完成旧衣回收得现金",        // 参数错误：ILLEGAL_ARGUMENT
    "坚持刷视频赚福利",         // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去领目标应用积分",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去参与花呗活动",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "逛网商领福利金",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去浏览租赁大促会场",        // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "逛一逛免费领点餐优惠",      // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE

    // processAlchemyTasks方法中常见任务  芝麻炼金
    "每日施肥",
    "芝麻租赁",
    "休闲小游戏",
    "AQApp",
    "订阅炼金",
    "租游戏账号",
    "芝麻大表鸽",
    "坚持签到",
    "坚持去玩休闲小游戏",     // 参数错误：ILLEGAL_ARGUMENT
    "租游戏账号得芝麻粒",      // 参数错误：ILLEGAL_ARGUMENT

    // 农场任务
    "ORCHARD_NORMAL_KUAISHOU_MAX",  // 逛一逛快手
    "ORCHARD_NORMAL_DIAOYU1",       // 钓鱼1次
    "ZHUFANG3IN1",                  // 添加农场小组件并访问
    "12172",                        // 逛助农好货得肥料
    "12173",                        // 买好货
    "70000",                        // 逛好物最高得1500肥料（XLIGHT）
    "TOUTIAO",                      // 逛一逛今日头条
    "ORCHARD_NORMAL_ZADAN10_3000",  // 农场对对碰
    "TAOBAO2",                      // 逛一逛闲鱼
    "TAOBAO",                       // 下载阿福
    "ORCHARD_NORMAL_JIUYIHUISHOU_VISIT", // 旧衣服回收
    "ORCHARD_NORMAL_SHOUJISHUMAHUISHOU", // 数码回收
    "ORCHARD_NORMAL_TAB3_ZHIFA",    // 看视频领肥料
    "ORCHARD_NORMAL_AQ_XIAZAI",     // 下载AQ
    
    // 庄园任务
    "HEART_DONATION_ADVANCED_FOOD_V2",  //去买秋天第一杯奶茶
    "HEART_DONATE",  //爱心捐赠
    "SHANGOU_xiadan",  //逛闪购外卖1元起吃
    "OFFLINE_PAY",  //到店付款,线下支付
    "ONLINE_PAY",  //在线支付
    "HUABEI_MAP_180", //用花呗完成一笔支付

    // 森林任务 - 不支持RPC调用的任务
    "ZHRW_AQapp_202512",    // 去蚂蚁阿福健康问答
    "LSHS_huisho20_202508",  // 完成旧衣回收得能量
    "TEST_LEAF_TASK",        // 逛农场得落叶肥料
    "SHARETASK_NEW",         // 邀请1位好友助力
    "YUSHU_202511",          // 单种榆树，年年有榆
    "KTKZ_YS202511",         // 一起组团种榆树
    "mokuai_senlin_hlz",     // 去玩一玩得活力值

)
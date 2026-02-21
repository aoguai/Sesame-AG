package fansirsqi.xposed.sesame.task.other.credit2101

import fansirsqi.xposed.sesame.hook.RequestManager
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONArray
import org.json.JSONObject

object Credit2101RpcCall {

    /** 查询账户资产：包含信用印记、碎片、体力、宝箱等 */
    fun queryAccountAsset(): String {
        val data = "[{}]"
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryAccountAsset",
            data
        )
    }

    /** 开宝箱（触发收益） */
    fun triggerBenefit(): String {
        val data = "[{}]"
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.triggerBenefit",
            data
        )
    }

    /** 查询签到数据 */
    fun querySignInData(): String {
        val data = "[{}]"
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.querySignInData",
            data
        )
    }

    /** 执行签到，day 为 totalLoginDays */
    fun userSignIn(day: Int): String {
        val data = "[{\"day\":$day}]"
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.userSignIn",
            data
        )
    }

    /** 查询当前坐标附近事件 */
    fun queryGridEvent(cityCode: String, latitude: Double, longitude: Double, guideState: Boolean = false): String {
        val data = """[
                {
                  "extParams": {
                    "cityCode": "$cityCode",
                    "latitude": "$latitude",
                    "longitude": "$longitude"
                  },
                  "guideState": $guideState
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryGridEvent",
            data
        )
    }

    /** 小游戏开始：MINI_GAME_ELIMINATE / MINI_GAME_COLLECTYJ 通用 */
    fun eventGameStart(batchNo: String, eventId: String, miniGameStageId: String): String {
        val data = """[
                {
                  "batchNo": "$batchNo",
                  "eventId": "$eventId",
                  "miniGameStageId": "$miniGameStageId"
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.eventGameStart",
            data
        )
    }


    /**
     * 小游戏完成：收集 YJ 类型（带 collectedYJ 扩展参数）
     * @param batchNo 批次号（非空）
     * @param eventId 事件ID（非空）
     * @param miniGameStageId 小游戏关卡ID（非空）
     * @param collectedYJ 收集的印记数
     * @return 接口响应字符串
     */
    fun eventGameCompleteCollectYj(
        batchNo: String,
        eventId: String,
        miniGameStageId: String,
        collectedYJ: Int // 明确要求传入 Int 类型
    ): String {

        val extParams = JSONObject().apply {
            put("collectedYJ", collectedYJ) // JSONObject 存入 Int 时不会带引号
        }

        val requestObj = JSONObject().apply {
            put("batchNo", batchNo)
            put("eventId", eventId)
            put("extParams", extParams)
            put("miniGameStageId", miniGameStageId)
            put("passed", 1) // 保持为数字 1
        }

        // 包装成数组 [ {...} ]
        val data = JSONArray().apply {
            put(requestObj)
        }.toString()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.eventGameComplete",
            data
        )
    }

    /**
     * 小游戏完成（通用）
     *
     * @param extParams 奖励 / 扩展参数，完全由上层决定
     *                  例如：
     *                  {
     *                    "YJ_PRIZE": 118,
     *                    "killCount": 3,
     *                    "BX_PRIZE": 3
     *                  }
     */
    fun eventGameComplete(
        batchNo: String,
        eventId: String,
        miniGameStageId: String,
        extParams: JSONObject?
    ): String {

        val extParamsStr = extParams?.toString() ?: "null"

        val data = """
        [
          {
            "batchNo": "$batchNo",
            "eventId": "$eventId",
            "miniGameStageId": "$miniGameStageId",
            "passed": 1,
            "extParams": $extParamsStr
          }
        ]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.eventGameComplete",
            data
        )
    }

    /** 小游戏完成：普通消除类，不带 extParams */
    fun eventGameCompleteSimple(
        batchNo: String,
        eventId: String,
        miniGameStageId: String
    ): String {
        val data = """[
                {
                  "batchNo": "$batchNo",
                  "eventId": "$eventId",
                  "miniGameStageId": "$miniGameStageId",
                  "passed": 1
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.eventGameComplete",
            data
        )
    }

    /** 黄金印记事件领取 */
    fun collectCredit(
        batchNo: String,
        eventId: String,
        cityCode: String,
        latitude: Double,
        longitude: Double
    ): String {
        val data = """[
                {
                  "batchNo": "$batchNo",
                  "eventId": "$eventId",
                  "extParams": {
                    "cityCode": "$cityCode",
                    "latitude": $latitude,
                    "longitude": $longitude
                  }
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.collectCredit",
            data
        )
    }

    /** 查询黑色印记事件详情 */
    fun queryBlackMarkEvent(eventId: String): String {
        val data = "[{\"eventId\":\"$eventId\"}]"

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryBlackMarkEvent",
            data
        )
    }

    /** 加入黑色印记事件（最低 10 点能量） */
    fun joinBlackMarkEvent(creditEnergy: Int, eventId: String): String {
        val data = """[
                {
                  "creditEnergy": $creditEnergy,
                  "eventId": "$eventId"
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.joinBlackMarkEvent",
            data
        )
    }

    /** 黑色印记事件注能 */
    fun chargeBlackMarkEvent(creditEnergy: Int, eventId: String): String {
        val data = """[
                {
                  "creditEnergy": $creditEnergy,
                  "eventId": "$eventId"
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.chargeBlackMarkEvent",
            data
        )
    }

    /** 探测事件（消耗探索次数） */
    fun exploreGridEvent(cityCode: String, latitude: Double, longitude: Double): String {
        val data = """[
                {
                  "extParams": {
                    "cityCode": "$cityCode",
                    "latitude": "$latitude",
                    "longitude": "$longitude"
                  }
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.exploreGridEvent",
            data
        )
    }

    /** 查询每日任务列表 */
    fun queryUserTask(): String {
        val data = "[{}]"
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryUserTask",
            data
        )
    }

    /** 任务操作：例如 TASK_CLAIM */
    fun operateTask(taskAction: String, taskConfigId: String): String {
        val data = """[
                {
                  "taskAction": "$taskAction",
                  "taskConfigId": "$taskConfigId"
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.operateTask",
            data
        )
    }

    /** 领取任务奖励 */
    fun awardTask(taskConfigId: String): String {
        val data = """[
                {
                  "taskConfigId": "$taskConfigId"
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.awardTask",
            data
        )
    }

    /** 查询故事事件（时空之门） */
    fun queryEventGate(
        batchNo: String,
        eventId: String,
        cityCode: String,
        latitude: Double,
        longitude: Double
    ): String {
        val data = """[
                {
                  "batchNo": "$batchNo",
                  "eventId": "$eventId",
                  "extParams": {
                    "cityCode": "$cityCode",
                    "latitude": "$latitude",
                    "longitude": "$longitude"
                  }
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryEventGate",
            data
        )
    }

    /** 完成故事事件（时空之门） */
    fun completeEventGate(
        batchNo: String,
        eventId: String,
        cityCode: String,
        latitude: Double,
        longitude: Double,
        storyId: String
    ): String {
        val data = """[
                {
                  "batchNo": "$batchNo",
                  "eventId": "$eventId",
                  "extParams": {
                    "cityCode": "$cityCode",
                    "latitude": "$latitude",
                    "longitude": "$longitude",
                    "storyId": "$storyId"
                  }
                }
            ]""".trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.completeEventGate",
            data
        )
    }

    /**
     * 查询弹窗展示信息
     *
     * RPC: com.alipay.innovationprod.biz.rpc.queryPopupView
     *
     * 请求参数示例：
     * [
     *   {
     *     "popupId": "1"
     *   }
     * ]
     *
     * 响应示例 1：
     * {
     *   "ariverRpcTraceId": "client`aBYSOR/y0xEDACWu2y9mPoqMPiT3WMd_5849815",
     *   "degrade": false,
     *   "popupViewVO": {
     *     "resultMap": {
     *       "energyRecover": 78,
     *       "exploreRecover": 1
     *     },
     *     "showResult": true
     *   },
     *   "resultCode": "SUCCESS",
     *   "resultMsg": "成功",
     *   "success": true,
     *   "traceId": "0b407b1617657190168605938e22e7"
     * }
     *
     * 响应示例 2：
     * {
     *   "ariverRpcTraceId": "0b43b49517657210811446533ebbce",
     *   "degrade": false,
     *   "popupViewVO": {
     *     "resultMap": {
     *       "nextEnergyRecoverMinutes": 25,
     *       "nextExploreRecoverMinutes": 31
     *     },
     *     "showResult": true
     *   },
     *   "resultCode": "SUCCESS",
     *   "resultMsg": "成功",
     *   "success": true,
     *   "traceId": "0b43b49517657210811446533ebbce"
     * }
     *
     * @param popupId 弹窗 ID
     * @return RPC 返回的原始 JSON 字符串
     */
    fun queryPopupView(popupId: String = "1"): String {
        val data = """
        [
          {
            "popupId": "$popupId"
          }
        ]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryPopupView",
            data
        )
    }

    /**
     * 查询所有图鉴进度
     *
     * 示例响应：
     * {
     * "success": true,
     * "resultCode": "SUCCESS",
     * "charterProgress": [
     * { "chapter": "10008", "cardCount": 6, "obtainedCardCount": 6, "awardStatus": "CLAIMED" },
     * { "chapter": "10007", "cardCount": 6, "obtainedCardCount": 4, "awardStatus": "LOCKED" }
     * ]
     * }
     */
    fun queryChapterProgress(): String {
        val data = "[{}]"

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryChapterProgress",
            data
        )
    }

    /**
     * 执行图鉴动作（合成或领奖）
     *
     * @param action  动作类型：
     * "CHAPTER_COMPLETE" -> 合成图鉴
     * "CHAPTER_AWARD"    -> 领取图鉴奖励
     * @param chapter 图鉴章节ID (例如: "10005")
     *
     * 合成响应示例：{"success":true, "chapter":"10005", "awardStatus":"UNLOCKED"}
     * 领奖响应示例：{"success":true, "gainByCollectedAll":{"awardAmount":"1200","awardType":"YJ_PRIZE"}}
     */
    fun completeChapterAction(action: String, chapter: String): String {
        val data = """
        [
          {
            "action": "$action",
            "chapter": "$chapter"
          }
        ]
    """.trimIndent()

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.completeChapterAction",
            data
        )
    }

    /** 查询天赋状态 */
    fun queryRelationTalent(): String {
        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryRelationTalent",
            "[{}]"
        )
    }

    /** 升级具体属性 */
    fun upgradeTalentAttribute(
        attrType: String,
        treeType: String,
        targetLevel: Int
    ): String {
        val requestObj = JSONObject().apply {
            put("roleId", "")
            put("talentAttributeType", attrType)
            put("talentTreeType", treeType)
            put("targetAttributeLevel", targetLevel.toString()) // 严格对齐抓包：字符串类型
        }

        val requestArray = JSONArray().apply {
            put(requestObj)
        }

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.upgradeTalentAttribute",
            requestArray.toString()
        )
    }

    /** 查询修复列表 (黑色印记列表) */
    fun queryGuardMarkList(): String {
        // 构造查询参数，通常为 [{}]
        val data = "[{}]"

        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.queryGuardMarkList",
            data
        )
    }

    /** 领取修复列表奖励 */
    fun claimGuardMarkAward(): String {

        val data = "[{}]"


        return RequestManager.requestString(
            "com.alipay.innovationprod.biz.rpc.claimGuardMarkAward",
            data
        )
    }


}
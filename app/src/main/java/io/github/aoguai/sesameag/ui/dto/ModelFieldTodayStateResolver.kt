package io.github.aoguai.sesameag.ui.dto

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelFields

/**
 * 配置项在“今日状态”维度上的展示结果。
 *
 * inactive 只表示该配置项对应的自动任务在今天已经没有必要继续执行，
 * 用于设置页灰显或展示原因；它不会修改配置值，也不代表永久禁用该功能。
 */
data class ModelFieldTodayState(
    val inactive: Boolean = false,
    val reason: String = ""
)

/**
 * 将配置字段映射到今日状态标记，供设置页展示“今日已处理/今日已达上限”等说明。
 *
 * 设计边界：
 * - 这里只读 Status/StatusFlags，不写入状态。
 * - 这里只处理能从本地状态明确判断的字段；没有可靠 flag 的字段保持可用，避免误灰显。
 * - 对列表或多选配置，只在所有已选项都能由状态证明完成/达上限时才返回 inactive。
 */
object ModelFieldTodayStateResolver {
    private data class OptionFlagState(
        val flag: String,
        val reason: String
    )

    /**
     * 多选项到状态 flag 的展示层映射。
     * key 必须与对应 ModelField 中保存的 option value 一致，reason 会直接展示给用户。
     */
    private val antForestEcoLifeOptionStates = mapOf(
        "plate" to OptionFlagState(StatusFlags.FLAG_ANTFOREST_ECOLIFE_PHOTO_GUANGPAN, "今日光盘行动已处理")
    )

    private val antFarmFamilyOptionStates = mapOf(
        "familySign" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SIGNED, "今日家庭签到已处理"),
        "feedFamilyAnimal" to OptionFlagState(StatusFlags.FLAG_FARM_FEED_FRIEND_LIMIT, "今日帮喂次数已达上限"),
        "sleepTogether" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SLEEP_TOGETHER, "今日一起睡觉已处理"),
        "deliverMsgSend" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_DELIVER_MSG_SEND, "今日道早安已处理"),
        "shareToFriends" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SHARE_TO_FRIENDS, "今日家庭分享已处理"),
        "inviteFriendVisitFamily" to OptionFlagState(StatusFlags.FLAG_FARM_INVITE_FRIEND_VISIT_FAMILY, "今日好友串门邀请已处理"),
        "batchInviteP2P" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_BATCH_INVITE_P2P, "今日串门送扭蛋已处理")
    )

    @JvmStatic
    fun resolve(
        modelCode: String,
        modelFields: ModelFields,
        modelField: ModelField<*>
    ): ModelFieldTodayState {
        return when ("$modelCode.${modelField.code}") {
            "AntForest.pkEnergy" ->
                flag(StatusFlags.FLAG_ANTFOREST_PK_SKIP_TODAY, "今日 PK 榜无需处理")

            "AntForest.whackMoleMode",
            "AntForest.whackMoleGames",
            "AntForest.whackMoleMoleCount",
            "AntForest.whackMoleTime" ->
                whackMoleState(modelFields)

            "AntForest.youthPrivilege" ->
                flag(StatusFlags.FLAG_ANTFOREST_PRIVILEGE_RECEIVED, "今日青春特权森林道具已处理")

            "AntForest.studentCheckIn" ->
                flag(StatusFlags.FLAG_ANTFOREST_PRIVILEGE_STUDENT_TASK, "今日青春特权签到红包已处理")

            "AntForest.ecoLife",
            "AntForest.ecoLifeOption" ->
                ecoLifeOptionsState(modelFields["ecoLifeOption"] ?: modelField)

            "AntForest.vitalityExchange",
            "AntForest.vitalityExchangeList" ->
                vitalityExchangeState(modelFields)

            "AntMember.memberSign" ->
                flag(StatusFlags.FLAG_ANTMEMBER_MEMBER_SIGN_DONE, "今日会员签到已处理")

            "AntMember.memberTask" ->
                when {
                    Status.hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_RISK_STOP_TODAY) ->
                        inactive("今日会员任务已止损")

                    Status.hasFlagToday(StatusFlags.FLAG_ANTMEMBER_MEMBER_TASK_EMPTY_TODAY) ->
                        inactive("今日会员任务已无可执行项")

                    else -> ModelFieldTodayState()
                }

            "AntMember.memberPointExchangeBenefit" ->
                flag(StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE, "今日会员积分兑换权益已处理")

            "AntMember.memberPointExchangeBenefitList" ->
                selectedSetFlagState(
                    modelField,
                    StatusFlags.FLAG_ANTMEMBER_MEMBER_BENEFIT_REFRESH_DONE,
                    "今日会员积分兑换权益已处理"
                )

            "AntMember.sesameTask" ->
                flag(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK, "今日芝麻信用任务已处理")

            "AntMember.collectSesame",
            "AntMember.collectSesameWithOneClick" ->
                flag(StatusFlags.FLAG_ANTMEMBER_COLLECT_SESAME_DONE, "今日芝麻奖励已领取")

            "AntMember.sesameGrainExchange" ->
                flag(StatusFlags.FLAG_ZMXY_GRAIN_EXCHANGE_DONE, "今日芝麻粒兑换已处理")

            "AntMember.sesameGrainExchangeList" ->
                selectedSetFlagState(
                    modelField,
                    StatusFlags.FLAG_ZMXY_GRAIN_EXCHANGE_DONE,
                    "今日芝麻粒兑换已处理"
                )

            "AntMember.merchantSign" ->
                flag(StatusFlags.FLAG_ANTMEMBER_MERCHANT_SIGN_DONE, "今日商家签到已处理")

            "AntMember.merchantKmdk" ->
                allFlags(
                    StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNIN_DONE,
                    StatusFlags.FLAG_ANTMEMBER_MERCHANT_KMDK_SIGNUP_DONE,
                    reason = "今日开门打卡已处理"
                )

            "AntMember.enableGoldTicket" ->
                allFlags(
                    StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_SIGN_DONE,
                    StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_HOME_DONE,
                    StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_WELFARE_DONE,
                    reason = "今日黄金票签到已处理"
                )

            "AntMember.enableGoldTicketConsume" ->
                flag(StatusFlags.FLAG_ANTMEMBER_GOLD_TICKET_CONSUME_DONE, "今日黄金票提取已处理")

            "AntMember.CollectStickers" ->
                flag(StatusFlags.FLAG_ANTMEMBER_STICKER, "今日贴纸已领取")

            "AntSports.sportsTasks" ->
                flag(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE, "今日运动任务已完成")

            "AntSports.syncStepCount" ->
                if ((intValue(modelField) ?: 0) > 0) {
                    flag(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE, "今日步数已同步")
                } else {
                    ModelFieldTodayState()
                }

            "AntSports.walkReviveSteps",
            "AntSports.walkReviveTask" ->
                flag(StatusFlags.FLAG_ANTSPORTS_ROUTE_REVIVE_TRIED, "今日行走路线复活已尝试且不可继续")

            "AntSports.neverlandGrid",
            "AntSports.neverlandGridStepCount" ->
                neverlandGridState(modelFields)

            "AntCooperate.teamCooperateWaterNum" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT),
                    limit = intValue(modelField),
                    reason = "今日组队合种浇水已达目标"
                )

            "AntCooperate.loveCooperateWater" ->
                flag(StatusFlags.FLAG_ANTCOOPERATE_LOVE_TEAM_WATER, "今日真爱合种浇水已处理")

            "AntCooperate.loveCooperateWaterNum" ->
                flag(StatusFlags.FLAG_ANTCOOPERATE_LOVE_TEAM_WATER, "今日真爱合种浇水已处理")

            "AntOcean.cleanOcean",
            "AntOcean.cleanOceanType",
            "AntOcean.cleanOceanList" ->
                flag(StatusFlags.FLAG_ANTOCEAN_HELP_CLEAN_ALL_FRIEND_LIMIT, "今日帮助好友清理次数已达上限")

            "AntOrchard.orchardSpreadManureCount" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SPREAD_MANURE_COUNT),
                    limit = intValue(modelField),
                    reason = "今日果树施肥已达上限"
                )

            "AntOrchard.orchardSpreadManureCountYeb" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SPREAD_MANURE_COUNT_YEB),
                    limit = intValue(modelField),
                    reason = "今日摇钱树施肥已达上限"
                )

            "AntStall.stallThrowManure" ->
                flag(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT, "今日丢肥料已达上限")

            "AntFarm.doFarmTask",
            "AntFarm.farmTaskTrigger" ->
                flag(StatusFlags.FLAG_FARM_TASK_FINISHED, "今日饲料任务已处理")

            "AntFarm.paradiseCoinExchangeBenefit",
            "AntFarm.paradiseCoinExchangeBenefitList" ->
                paradiseCoinExchangeState(modelFields)

            "AntFarm.enableChouchoule",
            "AntFarm.chouChouLeTrigger" ->
                flag(StatusFlags.FLAG_FARM_CHOUCHOULE_FINISHED, "今日小鸡抽抽乐已处理")

            "AntFarm.recordFarmGame",
            "AntFarm.farmGameTrigger" ->
                flag(StatusFlags.FLAG_FARM_GAME_FINISHED, "今日小游戏改分已处理")

            "AntFarm.feedFriendAnimalList" ->
                if (mapValue(modelField)?.isNotEmpty() == true) {
                    flag(StatusFlags.FLAG_FARM_FEED_FRIEND_LIMIT, "今日帮喂次数已达上限")
                } else {
                    ModelFieldTodayState()
                }

            "AntFarm.family",
            "AntFarm.familyOptions" ->
                familyOptionsState(modelFields["familyOptions"] ?: modelField)

            "AntFarm.useAccelerateTool",
            "AntFarm.remainingTime",
            "AntFarm.accelerateToolDailyLimit" ->
                flag(StatusFlags.FLAG_FARM_ACCELERATE_LIMIT, "今日加速卡已达设定/系统上限")

            "AntFarm.useSpecialFood",
            "AntFarm.useSpecialFoodCount" ->
                specialFoodLimitState(modelFields)

            "AntFarm.signRegardless" ->
                flag(StatusFlags.FLAG_FARM_SIGNED, "今日庄园签到已处理")

            "OtherTask.credit2101",
            "OtherTask.CreditOptions" ->
                credit2101OptionsState(modelFields["CreditOptions"] ?: modelField)

            else -> ModelFieldTodayState()
        }
    }

    private fun flag(flag: String, reason: String): ModelFieldTodayState {
        return if (Status.hasFlagToday(flag)) inactive(reason) else ModelFieldTodayState()
    }

    private fun allFlags(vararg flags: String, reason: String): ModelFieldTodayState {
        return if (flags.isNotEmpty() && flags.all { Status.hasFlagToday(it) }) {
            inactive(reason)
        } else {
            ModelFieldTodayState()
        }
    }

    private fun neverlandGridState(modelFields: ModelFields): ModelFieldTodayState {
        if (Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_NEVERLAND_ENERGY_LIMIT)) {
            return inactive("今日健康岛能量不足以建造")
        }
        return limitReached(
            current = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT),
            limit = intValue(modelFields["neverlandGridStepCount"]),
            reason = "今日健康岛走路次数已达上限"
        )
    }

    private fun specialFoodLimitState(modelFields: ModelFields): ModelFieldTodayState {
        if (Status.hasFlagToday(StatusFlags.FLAG_FARM_SPECIAL_FOOD_LIMIT)) {
            return inactive("今日特殊食品使用已达上限")
        }
        return limitReached(
            current = Status.getIntFlagToday(StatusFlags.FLAG_FARM_SPECIAL_FOOD_DAILY_COUNT),
            limit = intValue(modelFields["useSpecialFoodCount"]),
            reason = "今日特殊食品使用已达上限"
        )
    }

    private fun paradiseCoinExchangeState(modelFields: ModelFields): ModelFieldTodayState {
        val selectedBenefits = stringSetValue(modelFields["paradiseCoinExchangeBenefitList"])
        if (selectedBenefits.isEmpty()) {
            return ModelFieldTodayState()
        }

        return if (selectedBenefits.all { benefitId ->
                Status.hasFlagToday(StatusFlags.FLAG_FARM_PARADISE_COIN_EXCHANGE_LIMIT_PREFIX + benefitId)
            }
        ) {
            inactive("今日乐园币兑换权益已全部达上限")
        } else {
            ModelFieldTodayState()
        }
    }

    private fun vitalityExchangeState(modelFields: ModelFields): ModelFieldTodayState {
        val configuredCounts = countMapValue(modelFields["vitalityExchangeList"])
            .filterValues { it != 0 }
        if (configuredCounts.isEmpty()) {
            return ModelFieldTodayState()
        }
        if (configuredCounts.values.any { it < 0 }) {
            return ModelFieldTodayState()
        }

        return if (configuredCounts.all { (skuId, limit) ->
                !Status.canVitalityExchangeToday(skuId, limit)
            }
        ) {
            inactive("今日活力值兑换已达设定次数/上限")
        } else {
            ModelFieldTodayState()
        }
    }

    private fun credit2101OptionsState(modelField: ModelField<*>): ModelFieldTodayState {
        val configuredCounts = countMapValue(modelField)
            .filterValues { it != 0 }
        if (configuredCounts.isEmpty()) {
            return ModelFieldTodayState()
        }
        if (configuredCounts.values.any { it < 0 }) {
            return ModelFieldTodayState()
        }

        return if (configuredCounts.all { (eventType, limit) ->
                (Status.getIntFlagToday(buildCredit2101EventCountFlag(eventType)) ?: 0) >= limit
            }
        ) {
            inactive("今日信用2101事件已达设定次数")
        } else {
            ModelFieldTodayState()
        }
    }

    private fun buildCredit2101EventCountFlag(eventType: String): String {
        return StatusFlags.FLAG_CREDIT2101_EVENT_COUNT_PREFIX +
            eventType +
            StatusFlags.FLAG_CREDIT2101_EVENT_COUNT_SUFFIX
    }

    private fun whackMoleState(modelFields: ModelFields): ModelFieldTodayState {
        val mode = intValue(modelFields["whackMoleMode"]) ?: 0
        if (mode <= 0) {
            return ModelFieldTodayState()
        }
        return flag(StatusFlags.FLAG_ANTFOREST_WHACK_MOLE_EXECUTED, "今日6秒拼手速已处理")
    }

    private fun ecoLifeOptionsState(modelField: ModelField<*>): ModelFieldTodayState {
        return optionFlagsState(
            modelField,
            antForestEcoLifeOptionStates,
            "今日已完成所有带状态标记的绿色行动选项"
        )
    }

    private fun familyOptionsState(modelField: ModelField<*>): ModelFieldTodayState {
        return optionFlagsState(
            modelField,
            antFarmFamilyOptionStates,
            "今日已完成所有带状态标记的家庭任务"
        )
    }

    private fun optionFlagsState(
        modelField: ModelField<*>,
        optionStates: Map<String, OptionFlagState>,
        allDoneReason: String
    ): ModelFieldTodayState {
        val selectedOptions = stringSetValue(modelField)
        if (selectedOptions.isEmpty()) {
            return ModelFieldTodayState()
        }

        if (selectedOptions.any { !optionStates.containsKey(it) }) {
            return ModelFieldTodayState()
        }

        val matchedStates = selectedOptions.mapNotNull { optionStates[it] }
        if (matchedStates.isEmpty()) {
            return ModelFieldTodayState()
        }

        return if (matchedStates.all { Status.hasFlagToday(it.flag) }) {
            val reason = if (matchedStates.size == 1) {
                matchedStates.first().reason
            } else {
                allDoneReason
            }
            inactive(reason)
        } else {
            ModelFieldTodayState()
        }
    }

    private fun selectedSetFlagState(
        modelField: ModelField<*>?,
        flagKey: String,
        reason: String
    ): ModelFieldTodayState {
        if (stringSetValue(modelField).isEmpty()) {
            return ModelFieldTodayState()
        }
        return flag(flagKey, reason)
    }

    private fun limitReached(current: Int?, limit: Int?, reason: String): ModelFieldTodayState {
        val safeLimit = limit ?: 0
        if (safeLimit <= 0) return ModelFieldTodayState()
        return if ((current ?: 0) >= safeLimit) inactive(reason) else ModelFieldTodayState()
    }

    private fun intValue(modelField: ModelField<*>?): Int? {
        return modelField?.value as? Int
    }

    private fun mapValue(modelField: ModelField<*>?): Map<*, *>? {
        return modelField?.value as? Map<*, *>
    }

    private fun countMapValue(modelField: ModelField<*>?): Map<String, Int> {
        return mapValue(modelField)
            ?.mapNotNull { (key, value) ->
                val eventType = key as? String ?: return@mapNotNull null
                val count = (value as? Number)?.toInt() ?: return@mapNotNull null
                eventType to count
            }
            ?.toMap()
            ?: emptyMap()
    }

    private fun stringSetValue(modelField: ModelField<*>?): Set<String> {
        return (modelField?.value as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
    }

    private fun inactive(reason: String): ModelFieldTodayState {
        return ModelFieldTodayState(inactive = true, reason = reason)
    }
}


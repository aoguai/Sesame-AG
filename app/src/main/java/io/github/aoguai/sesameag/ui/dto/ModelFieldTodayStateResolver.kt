package io.github.aoguai.sesameag.ui.dto

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelFields

data class ModelFieldTodayState(
    val inactive: Boolean = false,
    val reason: String = ""
)

object ModelFieldTodayStateResolver {
    private const val ANTORCHARD_SPREAD_MANURE_COUNT_YEB = "ANTORCHARD_SPREAD_MANURE_COUNT_YEB"

    private data class OptionFlagState(
        val flag: String,
        val reason: String
    )

    private val antFarmFamilyOptionStates = mapOf(
        "familySign" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SIGNED, "今日家庭签到已处理"),
        "feedFamilyAnimal" to OptionFlagState(StatusFlags.FLAG_FARM_FEED_FRIEND_LIMIT, "今日帮喂次数已达上限"),
        "sleepTogether" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SLEEP_TOGETHER, "今日一起睡觉已处理"),
        "deliverMsgSend" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_DELIVER_MSG_SEND, "今日道早安已处理"),
        "shareToFriends" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SHARE_TO_FRIENDS, "今日家庭分享已处理"),
        "inviteFriendVisitFamily" to OptionFlagState(StatusFlags.FLAG_FARM_FAMILY_SHARE_TO_FRIENDS, "今日家庭分享已处理")
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

            "AntMember.sesameTask" ->
                flag(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK, "今日芝麻信用任务已处理")

            "AntMember.collectSesame",
            "AntMember.collectSesameWithOneClick" ->
                flag(StatusFlags.FLAG_ANTMEMBER_COLLECT_SESAME_DONE, "今日芝麻奖励已领取")

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

            "AntSports.neverlandGrid",
            "AntSports.neverlandGridStepCount" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT),
                    limit = intValue(modelFields["neverlandGridStepCount"]),
                    reason = "今日健康岛走路次数已达上限"
                )

            "AntCooperate.teamCooperateWaterNum" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT),
                    limit = intValue(modelField),
                    reason = "今日组队合种浇水已达目标"
                )

            "AntOrchard.orchardSpreadManureCount" ->
                limitReached(
                    current = Status.getIntFlagToday(StatusFlags.FLAG_ANTORCHARD_SPREAD_MANURE_COUNT),
                    limit = intValue(modelField),
                    reason = "今日果树施肥已达上限"
                )

            "AntOrchard.orchardSpreadManureCountYeb" ->
                limitReached(
                    current = Status.getIntFlagToday(ANTORCHARD_SPREAD_MANURE_COUNT_YEB),
                    limit = intValue(modelField),
                    reason = "今日摇钱树施肥已达上限"
                )

            "AntStall.stallThrowManure" ->
                flag(StatusFlags.FLAG_ANTSTALL_THROW_MANURE_LIMIT, "今日丢肥料已达上限")

            "AntFarm.doFarmTask",
            "AntFarm.farmTaskTrigger" ->
                flag(StatusFlags.FLAG_FARM_TASK_FINISHED, "今日饲料任务已处理")

            "AntFarm.feedFriendAnimalList" ->
                if (mapValue(modelField)?.isNotEmpty() == true) {
                    flag(StatusFlags.FLAG_FARM_FEED_FRIEND_LIMIT, "今日帮喂次数已达上限")
                } else {
                    ModelFieldTodayState()
                }

            "AntFarm.familyOptions" ->
                familyOptionsState(modelField)

            "AntFarm.useAccelerateTool",
            "AntFarm.useAccelerateToolContinue",
            "AntFarm.remainingTime",
            "AntFarm.useAccelerateToolWhenMaxEmotion" ->
                flag(StatusFlags.FLAG_FARM_ACCELERATE_LIMIT, "今日加速卡已达上限")

            "AntFarm.signRegardless" ->
                flag(StatusFlags.FLAG_FARM_SIGNED, "今日庄园签到已处理")

            else -> ModelFieldTodayState()
        }
    }

    private fun flag(flag: String, reason: String): ModelFieldTodayState {
        return if (Status.hasFlagToday(flag)) inactive(reason) else ModelFieldTodayState()
    }

    private fun allFlags(flagA: String, flagB: String, reason: String): ModelFieldTodayState {
        return if (Status.hasFlagToday(flagA) && Status.hasFlagToday(flagB)) {
            inactive(reason)
        } else {
            ModelFieldTodayState()
        }
    }

    private fun familyOptionsState(modelField: ModelField<*>): ModelFieldTodayState {
        val selectedOptions = stringSetValue(modelField)
        if (selectedOptions.isEmpty()) {
            return ModelFieldTodayState()
        }

        val matchedStates = selectedOptions.map {
            antFarmFamilyOptionStates[it] ?: return ModelFieldTodayState()
        }

        return if (matchedStates.all { Status.hasFlagToday(it.flag) }) {
            val reason = if (matchedStates.size == 1) {
                matchedStates.first().reason
            } else {
                "今日已完成所有带状态标记的家庭任务"
            }
            inactive(reason)
        } else {
            ModelFieldTodayState()
        }
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

    private fun stringSetValue(modelField: ModelField<*>?): Set<String> {
        return (modelField?.value as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
    }

    private fun inactive(reason: String): ModelFieldTodayState {
        return ModelFieldTodayState(inactive = true, reason = reason)
    }
}


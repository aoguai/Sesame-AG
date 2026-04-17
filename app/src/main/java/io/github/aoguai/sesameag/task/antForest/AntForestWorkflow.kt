package io.github.aoguai.sesameag.task.antForest

import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.TimeCounter
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONObject

private val FOREST_TAG: String = AntForest::class.java.simpleName

internal suspend fun AntForest.runForestPreparationAndCollectionWorkflow(tc: TimeCounter): JSONObject? {
    updateSelfHomePage()
    tc.countDebug("查询道具状态")

    usePropBeforeCollectEnergy(UserMap.currentUid)
    tc.countDebug("使用自己道具卡")

    Log.forest(FOREST_TAG, "🚀 执行找能量功能（协程）")
    collectEnergyByTakeLook()
    tc.countDebug("找能量收取（协程）")

    if (pkEnergy?.value == true) {
        Log.forest(FOREST_TAG, "🚀 异步执行PK好友能量收取")
        collectPKEnergyCoroutine()
        tc.countDebug("收PK好友能量（同步）")
    } else {
        tc.countDebug("跳过PK好友能量（未开启）")
    }

    Log.forest(FOREST_TAG, "🌳 【正常流程】开始收取自己的能量...")
    val selfHomeObj = querySelfHome()
    tc.countDebug("获取自己主页对象信息")
    if (selfHomeObj != null) {
        collectEnergy(UserMap.currentUid, selfHomeObj, "self")
        Log.forest(FOREST_TAG, "✅ 【正常流程】收取自己的能量完成")
        tc.countDebug("收取自己的能量")
    } else {
        Log.error(FOREST_TAG, "❌ 【正常流程】获取自己主页信息失败，跳过能量收取")
        tc.countDebug("跳过自己的能量收取（主页获取失败）")
    }

    Log.forest(FOREST_TAG, "🚀 执行好友能量收取（协程）")
    collectFriendEnergyCoroutine()
    tc.countDebug("收取好友能量（同步）")
    return selfHomeObj
}

internal suspend fun AntForest.runForestHomeFollowUpWorkflow(selfHomeObj: JSONObject?, tc: TimeCounter) {
    if (selfHomeObj == null) {
        return
    }
    var shouldRefreshForestHomeAfterEnergyRain = false

    checkAndHandleWhackMole()
    tc.countDebug("拼手速")

    val processObj = if (isTeam(selfHomeObj)) {
        selfHomeObj.optJSONObject("teamHomeResult")
            ?.optJSONObject("mainMember")
    } else {
        selfHomeObj
    }

    if (collectWateringBubble?.value == true) {
        wateringBubbles(processObj)
        tc.countDebug("收取浇水金球")
    }
    if (collectProp?.value == true) {
        givenProps(processObj)
        tc.countDebug("收取道具")
    }
    if (userPatrol?.value == true) {
        queryUserPatrol()
        tc.countDebug("动物巡护任务")
    }

    handleUserProps(selfHomeObj)
    tc.countDebug("收取动物派遣能量")

    collectEnergyBomb(selfHomeObj)
    tc.countDebug("收取炸弹卡能量")

    if (canRunConsumeAnimalPropWorkflow()) {
        queryAndConsumeAnimal()
        tc.countDebug("森林巡护")
    } else {
        Log.forest("已经有动物伙伴在巡护森林~")
    }

    if (combineAnimalPiece?.value == true) {
        queryAnimalAndPiece()
        tc.countDebug("合成动物碎片")
    }

    if (receiveForestTaskAward?.value == true) {
        receiveTaskAward()
        tc.countDebug("森林任务")
    }
    if (ecoLife?.value == true) {
        if (ecoLifeTime?.isReachedToday() == true) {
            EcoLife.ecoLife()
            tc.countDebug("绿色行动")
        } else {
            Log.forest(FOREST_TAG, "绿色行动未到执行时间，跳过")
        }
    }

    waterFriends()
    tc.countDebug("给好友浇水")

    if (giveProp?.value == true) {
        giveProp()
        tc.countDebug("赠送道具")
    }

    if (vitalityExchange?.value == true) {
        handleVitalityExchange()
        tc.countDebug("活力值兑换")
    }

    if (energyRain?.value == true) {
        if (energyRainTime?.isReachedToday() == true) {
            if (energyRainChance?.value == true) {
                useEnergyRainChanceCard()
                tc.countDebug("使用能量雨卡")
            }
            if (EnergyRainCoroutine.execEnergyRain()) {
                shouldRefreshForestHomeAfterEnergyRain = true
                handleEnergyRainPostFlow()
            }
            tc.countDebug("能量雨")
        } else {
            Log.forest(FOREST_TAG, "能量雨未到执行时间，跳过")
        }
    }

    if (forestMarket?.value == true) {
        GreenLife.ForestMarket("GREEN_LIFE", "ANTFOREST")
        tc.countDebug("森林集市")
    }

    if (medicalHealth?.value == true) {
        if (AntForest.medicalHealthOption?.value?.contains("FEEDS") == true) {
            Healthcare.queryForestEnergy("FEEDS")
            tc.countDebug("绿色医疗")
        }
        if (AntForest.medicalHealthOption?.value?.contains("BILL") == true) {
            Healthcare.queryForestEnergy("BILL")
            tc.countDebug("电子小票")
        }
    }

    if (youthPrivilege?.value == true) {
        Privilege.youthPrivilege()
    }

    if (dailyCheckIn?.value == true) {
        Privilege.studentSignInRedEnvelope()
    }

    if (forestChouChouLe?.value == true) {
        ForestChouChouLe().chouChouLe()
        tc.countDebug("抽抽乐")
    }

    doforestgame()

    if (shouldRefreshForestHomeAfterEnergyRain && !hasPendingRobMultiplierEnergy()) {
        updateSelfHomePage(homePageSource = AntForestRpcCall.BACK_FROM_ENERGY_RAIN_SOURCE)
        tc.countDebug("能量雨后刷新主页")
    }

    if (hasPendingRobMultiplierEnergy()) {
        updateSelfHomePage(
            collectRobMultiplierEnergy = true,
            robMultiplierEnergySource = if (shouldRefreshForestHomeAfterEnergyRain) {
                AntForestRpcCall.BACK_FROM_ENERGY_RAIN_SOURCE
            } else {
                null
            },
            homePageSource = if (shouldRefreshForestHomeAfterEnergyRain) {
                AntForestRpcCall.BACK_FROM_ENERGY_RAIN_SOURCE
            } else {
                null
            }
        )
        tc.countDebug("领取N倍卡能量")
    }

    logForestEnergyInfo()
    tc.stop()
}

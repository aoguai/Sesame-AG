package fansirsqi.xposed.sesame.task.antForest

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 6秒拼手速打地鼠
 * 整合版本：适配最新 RPC 定义
 */
object WhackMole {
    private const val TAG = "WhackMole"
    private const val SOURCE = "senlinguangchangdadishu"
    private const val EXEC_FLAG = "forest::whackMole::executed"

    @Volatile
    private var totalGames = 5
    @Volatile
    private var moleCount = 15 // 兼容模式默认击打数
    private const val GAME_DURATION_MS = 12000L
    private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startTime = AtomicLong(0)

    @Volatile
    private var isRunning = false

    enum class Mode {
        COMPATIBLE, // 兼容模式 (对应 old系列 RPC)
        AGGRESSIVE  // 激进模式 (对应 标准系列 RPC)
    }

    data class GameSession(
        val token: String,
        val roundNumber: Int
    )

    fun setTotalGames(games: Int) {
        totalGames = games
    }

    fun setMoleCount(count: Int) {
        moleCount = count
    }

    private val intervalCalculator = GameIntervalCalculator

    /**
     * 挂起方式启动游戏，供 ManualTask 调用以等待完成
     */
    suspend fun startSuspend(mode: Mode) = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.record(TAG, "⏭️ 打地鼠游戏正在运行中，跳过重复启动")
            return@withContext
        }
        isRunning = true

        try {
            when (mode) {
                Mode.COMPATIBLE -> runCompatibleMode()
                Mode.AGGRESSIVE -> runAggressiveMode()
            }
            Status.setFlagToday(EXEC_FLAG)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "打地鼠异常: ", e)
        } finally {
            isRunning = false
            Log.record(TAG, "🎮 打地鼠运行状态已重置")
        }
    }

    fun start(mode: Mode) {
        globalScope.launch {
            startSuspend(mode)
        }
    }

    // ================= [ 兼容模式：对应 old 系列 RPC ] =================
    private suspend fun runCompatibleMode() {
        try {
            val startTs = System.currentTimeMillis()

            // 1. 开始游戏 (使用 oldstartWhackMole)
            val response = JSONObject(AntForestRpcCall.oldstartWhackMole(SOURCE))
            if (!response.optBoolean("success")) {
                Log.record(TAG, response.optString("resultDesc", "开始失败"))
                return
            }

            val moleInfoArray = response.optJSONArray("moleInfo")
            val token = response.optString("token")
            if (moleInfoArray == null || token.isEmpty()) return

            val allMoleIds = mutableListOf<Long>()
            val bubbleMoleIds = mutableListOf<Long>()

            for (i in 0 until moleInfoArray.length()) {
                val mole = moleInfoArray.getJSONObject(i)
                val moleId = mole.getLong("id")
                allMoleIds.add(moleId)
                if (mole.has("bubbleId")) bubbleMoleIds.add(moleId)
            }

            // 2. 打有能量球的地鼠 (使用 oldwhackMole)
            var hitCount = 0
            bubbleMoleIds.forEach { moleId ->
                try {
                    val whackResp = JSONObject(AntForestRpcCall.oldwhackMole(moleId, token, SOURCE))
                    if (whackResp.optBoolean("success")) {
                        val energy = whackResp.optInt("energyAmount", 0)
                        hitCount++
                        Log.forest("森林能量⚡️[兼容打地鼠:$moleId +${energy}g]")
                        if (hitCount < bubbleMoleIds.size) {
                            delay(100 + (0..200).random().toLong())
                        }
                    }
                } catch (t: Throwable) {
                }
            }

            // 3. 计算剩余 ID 并结算 (使用 oldsettlementWhackMole)
            val remainingIds = allMoleIds.filter { !bubbleMoleIds.contains(it) }
                .take(moleCount) // 限制击打数量
                .map { it.toString() }

            val elapsedTime = System.currentTimeMillis() - startTs
            delay(max(0L, 6000L - elapsedTime - 200L))

            val settleResp = JSONObject(AntForestRpcCall.oldsettlementWhackMole(token, remainingIds, SOURCE))
            if (ResChecker.checkRes(TAG, settleResp)) {
                val total = settleResp.optInt("totalEnergy", 0)
                Log.forest("森林能量⚡️[兼容模式完成(打${remainingIds.size + hitCount}个) 总能量+${total}g]")
            }
        } catch (t: Throwable) {
            Log.record(TAG, "兼容模式出错: ${t.message}")
        }
    }

    // ================= [ 激进模式：对应 标准系列 RPC ] =================

    @SuppressLint("DefaultLocale")
    private suspend fun runAggressiveMode() {
        startTime.set(System.currentTimeMillis())
        val dynamicInterval = intervalCalculator.calculateDynamicInterval(GAME_DURATION_MS, totalGames)

        val sessions = mutableListOf<GameSession>()
        try {
            for (roundNum in 1..totalGames) {
                // 1. 启动单局 (使用标准 startWhackMole)
                val session = startSingleRound(roundNum)
                if (session != null) sessions.add(session)

                if (roundNum < totalGames) {
                    val remaining = GAME_DURATION_MS - (System.currentTimeMillis() - startTime.get())
                    delay(intervalCalculator.calculateNextDelay(dynamicInterval, roundNum, totalGames, remaining))
                }
            }
        } catch (e: CancellationException) {
            return
        }

        // 等待结算窗口
        val waitTime = max(0L, GAME_DURATION_MS - (System.currentTimeMillis() - startTime.get()))
        delay(waitTime)

        // 2. 批量结算 (使用标准 settlementWhackMole)
        var totalEnergy = 0
        sessions.forEach { session ->
            delay(200)
            totalEnergy += settleStandardRound(session)
        }
        Log.forest("森林能量⚡️[激进模式${sessions.size}局 总计${totalEnergy}g]")
    }

    private suspend fun startSingleRound(round: Int): GameSession? {
        try {
            // 标准接口调用
            val startResp = JSONObject(AntForestRpcCall.startWhackMole())
            if (!ResChecker.checkRes(TAG, startResp)) return null

            if (!startResp.optBoolean("canPlayToday", true)) {
                Status.setFlagToday(EXEC_FLAG)
                throw CancellationException("Today limit reached")
            }

            val token = startResp.optString("token")
            Toast.show("打地鼠 第${round}局启动\nToken: $token")
            return GameSession(token, round)
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun settleStandardRound(session: GameSession): Int {
        try {
            // 标准结算调用 (RPC 内部会自动处理 moleIdList 1-15)
            val resp = JSONObject(AntForestRpcCall.settlementWhackMole(session.token))
            if (ResChecker.checkRes(TAG, resp)) {
                return resp.optInt("totalEnergy", 0)
            }
        } catch (e: Exception) {
        }
        return 0
    }
}
package io.github.aoguai.sesameag.task.antForest


/**
 * 动态间隔计算器：用于游戏场景下的智能间隔管理
 * 
 * 核心功能：
 * 1. 根据总时长和局数自动计算最优间隔
 * 2. 动态调整间隔，确保在限定时间内完成所有局数
 * 3. 添加随机变化，避免规律性操作触发服务器限流
 * 4. 提供安全边界保护，防止间隔过短或过长
 * 
 * @author Ghostxx
 */
object GameIntervalCalculator {
    
    /**
     * 动态间隔配置：基于总时长和局数计算的最优间隔参数
     * @param baseInterval 基础间隔时间（毫秒）
     * @param randomRange 随机变化范围（毫秒）
     */
    data class DynamicInterval(
        val baseInterval: Long,
        val randomRange: Long
    )

    /**
     * 根据游戏总时长和局数计算动态间隔参数
     * 目标：在总时长内均匀分布所有局数，避免服务器限流
     * @param totalDurationMs 游戏总时长（毫秒）
     * @param gameCount 游戏局数
     * @return 动态间隔配置
     */
    fun calculateDynamicInterval(totalDurationMs: Long, gameCount: Int): DynamicInterval {
        // 计算平均可用时间：总时长减去启动和结算的缓冲时间
        val totalAvailableTime = totalDurationMs - 2000L // 预留2秒缓冲
        
        // 如果只有1局，不需要间隔
        if (gameCount <= 1) {
            return DynamicInterval(0L, 0L)
        }
        
        val averageInterval = totalAvailableTime / (gameCount - 1) // 减1是因为最后一局后不需要间隔
        
        // 设置基础间隔为平均值的60%，确保有足够时间完成所有局
        val baseInterval = (averageInterval * 0.6).toLong()
        
        // 设置随机范围为平均值的20%，提供变化避免规律性
        val randomRange = (averageInterval * 0.2).toLong()
        
        // 确保最小间隔不低于800ms（防止过于频繁触发限流）
        val finalBaseInterval = maxOf(baseInterval, 800L)
        val finalRandomRange = maxOf(randomRange, 200L)
        
        return DynamicInterval(finalBaseInterval, finalRandomRange)
    }

    /**
     * 计算下一次的延迟时间
     * 考虑剩余时间和已完成的局数，动态调整间隔
     * @param interval 动态间隔配置
     * @param currentRound 当前完成的局数
     * @param totalRounds 总局数
     * @param remainingTime 剩余时间（毫秒）
     * @return 下一次的延迟时间（毫秒）
     */
    fun calculateNextDelay(
        interval: DynamicInterval,
        currentRound: Int,
        totalRounds: Int,
        remainingTime: Long
    ): Long {
        // 计算剩余局数（减去当前已完成的局数）
        val remainingRounds = totalRounds - currentRound
        
        // 如果没有剩余局数，返回0
        if (remainingRounds <= 0) {
            return 0L
        }
        
        // 如果剩余时间紧张，缩短间隔
        val maxSafeDelay = remainingTime / remainingRounds - 500L // 每局预留500ms缓冲
        val calculatedDelay = interval.baseInterval + (-interval.randomRange..interval.randomRange).random()
        
        // 确保不超过安全延迟时间，同时不低于最小间隔
        return minOf(maxOf(calculatedDelay, 500L), maxSafeDelay)
    }

}

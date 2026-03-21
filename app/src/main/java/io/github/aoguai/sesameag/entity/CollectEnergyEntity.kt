package io.github.aoguai.sesameag.entity

import org.json.JSONObject

/**
 * 表示一个能量收集实体，包含用户信息及操作相关的状态。
 *
 * @property userId 用户 ID
 * @property userHome 用户主页 JSON 对象
 * @property rpcEntity RPC 请求实体
 * @property fromTag 收取来源标识
 * @property skipPropCheck 是否跳过道具检查（用于蹲点收取快速通道）
 */
data class CollectEnergyEntity(
    val userId: String,
    var userHome: JSONObject? = null,
    var rpcEntity: RpcEntity? = null,
    var fromTag: String? = null,
    var skipPropCheck: Boolean = false
) {
    /** 收集次数 */
    private var collectCount: Int = 0
    
    /** 尝试次数 */
    private var tryCount: Int = 0
    
    /** 是否需要翻倍 */
    var needDouble: Boolean = false
    
    /** 是否需要重试 */
    var needRetry: Boolean = false

    /**
     * 增加尝试次数
     * @return 更新后的尝试次数
     */
    fun addTryCount(): Int {
        tryCount += 1
        return tryCount
    }

    /**
     * 重置尝试次数为 0
     */
    fun resetTryCount() {
        tryCount = 0
    }

    /**
     * 设置需要翻倍，并增加收集次数
     */
    fun setNeedDouble() {
        collectCount += 1
        needDouble = true
    }

    /**
     * 取消需要翻倍状态
     */
    fun unsetNeedDouble() {
        needDouble = false
    }

    /**
     * 设置需要重试状态
     */
    fun setNeedRetry() {
        needRetry = true
    }

    /**
     * 取消需要重试状态
     */
    fun unsetNeedRetry() {
        needRetry = false
    }
}


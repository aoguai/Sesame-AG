package io.github.aoguai.sesameag.util

import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val NANOS_PER_MILLISECOND = 1_000_000L

private fun sleepBlocking(millis: Long) {
    if (millis <= 0) return
    java.util.concurrent.locks.LockSupport.parkNanos(millis * NANOS_PER_MILLISECOND)
}

/**
 * 统一错误处理工具类
 * 
 * 提供安全的错误处理模式，减少重复代码，提高代码健壮性
 * 
 * @author AI Code Quality Assistant
 * @since 2025-10-27
 * @updated 2025-11-02 - 添加细粒度异常类型处理
 */
@Suppress("TooManyFunctions")
object ErrorHandler {
    
    // ==================== 自定义异常类型 ====================
    
    /**
     * RPC调用异常
     */
    class RpcException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    /**
     * RPC业务错误（服务端返回的业务级错误）
     */
    class RpcBusinessException(
        val code: String,
        val desc: String,
        message: String = "RPC业务错误: [$code] $desc"
    ) : Exception(message)
    
    /**
     * 数据解析异常
     */
    class DataParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    /**
     * 安全执行代码块，捕获异常并返回结果或fallback值
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param errorMsg 错误消息前缀
     * @param fallback 失败时的fallback值
     * @param block 要执行的代码块
     * @return 执行结果或fallback值
     * 
     * 示例:
     * ```kotlin
     * val result = ErrorHandler.safely("TAG", "操作失败", fallback = false) {
     *     // 可能抛出异常的操作
     *     performOperation()
     * }
     * ```
     */
    inline fun <T> safely(
        tag: String,
        errorMsg: String = "操作失败",
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            Log.error(tag, "$errorMsg: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        }
    }
    
    /**
     * 安全执行无返回值的代码块，捕获并记录异常
     * 
     * @param tag 日志标签
     * @param errorMsg 错误消息前缀
     * @param block 要执行的代码块
     * 
     * 示例:
     * ```kotlin
     * ErrorHandler.safelyRun("TAG", "通知创建失败") {
     *     createNotification()
     * }
     * ```
     */
    inline fun safelyRun(
        tag: String,
        errorMsg: String = "操作失败",
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Exception) {
            Log.error(tag, "$errorMsg: ${e.message}")
            Log.printStackTrace(tag, e)
        }
    }
    
    /**
     * 安全执行代码块，针对特定异常类型进行处理
     * 
     * @param T 返回值类型
     * @param E 异常类型
     * @param tag 日志标签
     * @param errorMsg 错误消息前缀
     * @param fallback fallback值
     * @param onError 异常处理回调
     * @param block 要执行的代码块
     * @return 执行结果或fallback值
     */
    inline fun <T, reified E : Exception> safelyWithHandler(
        tag: String,
        errorMsg: String = "操作失败",
        fallback: T? = null,
        crossinline onError: (E) -> Unit = {},
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            when (e) {
                is E -> {
                    Log.error(tag, "$errorMsg: ${e.message}")
                    onError(e)
                }
                else -> {
                    Log.error(tag, "$errorMsg (未处理的异常): ${e.message}")
                    Log.printStackTrace(tag, e)
                }
            }
            fallback
        }
    }
    
    /**
     * 带重试机制的安全执行
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试延迟(毫秒)
     * @param errorMsg 错误消息前缀
     * @param fallback fallback值
     * @param block 要执行的代码块
     * @return 执行结果或fallback值
     * 
     * 示例:
     * ```kotlin
     * val data = ErrorHandler.safelyWithRetry("TAG", maxRetries = 3, retryDelay = 1000) {
     *     fetchDataFromNetwork()
     * }
     * ```
     */
    fun <T> safelyWithRetry(
        tag: String,
        maxRetries: Int = 3,
        retryDelay: Long = 1000,
        errorMsg: String = "操作失败",
        fallback: T? = null,
        block: () -> T
    ): T? {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Log.runtime(tag, "$errorMsg，第${attempt + 1}次重试...")
                    sleepBlocking(retryDelay)
                }
            }
        }
        Log.error(tag, "$errorMsg，已重试${maxRetries}次: ${lastException?.message}")
        lastException?.let { Log.printStackTrace(tag, it) }
        return fallback
    }

    @Suppress("LongParameterList")
    suspend fun <T> safelyWithRetrySuspend(
        tag: String,
        maxRetries: Int = 3,
        retryDelay: Long = 1000,
        errorMsg: String = "操作失败",
        fallback: T? = null,
        block: suspend () -> T
    ): T? {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Log.runtime(tag, "$errorMsg，第${attempt + 1}次重试...")
                    delay(retryDelay)
                }
            }
        }
        Log.error(tag, "$errorMsg，已重试${maxRetries}次: ${lastException?.message}")
        lastException?.let { Log.printStackTrace(tag, it) }
        return fallback
    }
    
    /**
     * 检查参数有效性，无效时抛出IllegalArgumentException
     * 
     * @param condition 条件
     * @param lazyMessage 错误消息生成函数
     * @throws IllegalArgumentException 条件不满足时
     * 
     * 示例:
     * ```kotlin
     * ErrorHandler.require(userId != null) { "userId不能为空" }
     * ErrorHandler.require(count > 0) { "count必须大于0，当前值: $count" }
     * ```
     */
    inline fun require(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw IllegalArgumentException(lazyMessage())
        }
    }
    
    /**
     * 检查状态有效性，无效时抛出IllegalStateException
     * 
     * @param condition 条件
     * @param lazyMessage 错误消息生成函数
     * @throws IllegalStateException 条件不满足时
     */
    inline fun check(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw IllegalStateException(lazyMessage())
        }
    }
    
    // ==================== 细粒度错误处理 ====================
    
    /**
     * 安全执行RPC调用，针对不同类型的RPC错误进行细粒度处理
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param operation 操作描述
     * @param fallback fallback值
     * @param onBusinessError 业务错误回调
     * @param onNetworkError 网络错误回调
     * @param block RPC调用代码块
     * @return 执行结果或fallback值
     */
    inline fun <T> safelyRpcCall(
        tag: String,
        operation: String,
        fallback: T? = null,
        crossinline onBusinessError: (RpcBusinessException) -> Unit = {},
        crossinline onNetworkError: (IOException) -> Unit = {},
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            // 协程取消必须重新抛出
            throw e
        } catch (e: RpcBusinessException) {
            // RPC业务错误（服务端返回的错误码）
            Log.runtime(tag, "$operation - RPC业务错误: [${e.code}] ${e.desc}")
            onBusinessError(e)
            fallback
        } catch (e: SocketTimeoutException) {
            // 网络超时
            Log.error(tag, "$operation - 网络超时: ${e.message}")
            onNetworkError(e)
            fallback
        } catch (e: UnknownHostException) {
            // 网络不可达
            Log.error(tag, "$operation - 网络不可达: ${e.message}")
            onNetworkError(e)
            fallback
        } catch (e: IOException) {
            // 其他IO异常
            Log.error(tag, "$operation - 网络异常: ${e.message}")
            onNetworkError(e)
            fallback
        } catch (e: JSONException) {
            // JSON解析异常
            Log.error(tag, "$operation - 数据解析失败: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        } catch (e: Exception) {
            // 其他未知异常
            Log.error(tag, "$operation - 未知错误: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        }
    }
    
    /**
     * 安全执行JSON解析操作
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param jsonSource 数据源描述
     * @param fallback fallback值
     * @param block 解析代码块
     * @return 解析结果或fallback值
     */
    inline fun <T> safelyParseJson(
        tag: String,
        jsonSource: String,
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: JSONException) {
            Log.error(tag, "解析JSON失败 [$jsonSource]: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        } catch (e: NullPointerException) {
            Log.error(tag, "JSON数据为空或缺少必要字段 [$jsonSource]: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        } catch (e: Exception) {
            Log.error(tag, "处理JSON时发生错误 [$jsonSource]: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        }
    }
    
    /**
     * 安全执行协程代码，正确处理CancellationException
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param operation 操作描述
     * @param fallback fallback值
     * @param block 协程代码块
     * @return 执行结果或fallback值
     */
    inline fun <T> safelyCoroutine(
        tag: String,
        operation: String,
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            // 协程取消是正常流程，必须重新抛出
            Log.debug(tag, "$operation - 协程被取消")
            throw e
        } catch (e: Exception) {
            Log.error(tag, "$operation - 异常: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        }
    }
    
    /**
     * 安全执行文件IO操作
     * 
     * @param T 返回值类型
     * @param tag 日志标签
     * @param filePath 文件路径
     * @param operation 操作类型（读取/写入）
     * @param fallback fallback值
     * @param block IO操作代码块
     * @return 执行结果或fallback值
     */
    inline fun <T> safelyFileIo(
        tag: String,
        filePath: String,
        operation: String = "文件操作",
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: java.io.FileNotFoundException) {
            Log.error(tag, "$operation - 文件不存在: $filePath")
            fallback
        } catch (e: java.io.IOException) {
            Log.error(tag, "$operation - IO错误 [$filePath]: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        } catch (e: SecurityException) {
            Log.error(tag, "$operation - 权限不足 [$filePath]: ${e.message}")
            fallback
        } catch (e: Exception) {
            Log.error(tag, "$operation - 未知错误 [$filePath]: ${e.message}")
            Log.printStackTrace(tag, e)
            fallback
        }
    }
}


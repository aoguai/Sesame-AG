package io.github.aoguai.sesameag.util

/**
 * 字符串工具类 - Kotlin 版本
 *
 * 从 Java 迁移到 Kotlin，提供两种使用方式：
 * 1. 兼容Java的静态方法 (通过StringUtil.method())
 * 2. Kotlin风格的扩展函数 (通过string.method())
 *
 * @author Sesame-AG
 * @since 重构阶段1
 */
object StringUtil {
    
    // ==================== Java兼容方法 ====================
    // 保留原有的静态方法签名，供Java代码调用
    // 标记为@Deprecated，引导迁移到Kotlin扩展函数
    
    /**
     * 检查字符串是否为空
     * @deprecated 推荐使用Kotlin扩展函数 {@code string.isNullOrEmpty()}
     */
    @Deprecated(
        message = "Use String?.isNullOrEmpty() instead",
        replaceWith = ReplaceWith("str.isNullOrEmpty()")
    )
    @JvmStatic
    fun isEmpty(str: String?): Boolean = str.isNullOrEmpty()
    
    /**
     * 将集合元素用指定连接符连接成字符串
     * @deprecated 推荐使用Kotlin标准库 {@code collection.joinToString()}
     */
    @Deprecated(
        message = "Use collection.joinToString() instead",
        replaceWith = ReplaceWith("collection.joinToString(conjunction.toString())")
    )
    @JvmStatic
    fun collectionJoinString(conjunction: CharSequence, collection: Collection<*>): String {
        return collection.joinToString(separator = conjunction)
    }
    
    /**
     * 将数组元素用指定连接符连接成字符串
     * @deprecated 推荐使用Kotlin标准库 {@code array.joinToString()}
     */
    @Deprecated(
        message = "Use array.joinToString() instead",
        replaceWith = ReplaceWith("array.joinToString(conjunction.toString())")
    )
    @JvmStatic
    fun arrayJoinString(conjunction: CharSequence, vararg array: Any?): String {
        return array.joinToString(separator = conjunction)
    }
    
    /**
     * 将数组转换为逗号分隔的字符串
     * @deprecated 推荐使用Kotlin标准库 {@code array.joinToString()}
     */
    @Deprecated(
        message = "Use array.joinToString() instead",
        replaceWith = ReplaceWith("array.joinToString(\",\")")
    )
    @JvmStatic
    fun arrayToString(vararg array: Any?): String {
        return array.joinToString(",")
    }
    
    /**
     * 左填充字符串到指定宽度
     * @param str 数字
     * @param totalWidth 总宽度
     * @param padChar 填充字符
     */
    @JvmStatic
    fun padLeft(str: Int, totalWidth: Int, padChar: Char): String {
        return str.toString().padStart(totalWidth, padChar)
    }
    
    /**
     * 右填充字符串到指定宽度
     * @param str 数字
     * @param totalWidth 总宽度
     * @param padChar 填充字符
     */
    @JvmStatic
    fun padRight(str: Int, totalWidth: Int, padChar: Char): String {
        return str.toString().padEnd(totalWidth, padChar)
    }
    
    /**
     * 左填充字符串到指定宽度
     * @param str 字符串
     * @param totalWidth 总宽度
     * @param padChar 填充字符
     * @deprecated 推荐使用Kotlin扩展函数 {@code string.padStart()}
     */
    @Deprecated(
        message = "Use String.padStart() instead",
        replaceWith = ReplaceWith("str.padStart(totalWidth, padChar)")
    )
    @JvmStatic
    fun padLeft(str: String, totalWidth: Int, padChar: Char): String {
        return str.padStart(totalWidth, padChar)
    }
    
    /**
     * 右填充字符串到指定宽度
     * @param str 字符串
     * @param totalWidth 总宽度
     * @param padChar 填充字符
     * @deprecated 推荐使用Kotlin扩展函数 {@code string.padEnd()}
     */
    @Deprecated(
        message = "Use String.padEnd() instead",
        replaceWith = ReplaceWith("str.padEnd(totalWidth, padChar)")
    )
    @JvmStatic
    fun padRight(str: String, totalWidth: Int, padChar: Char): String {
        return str.padEnd(totalWidth, padChar)
    }
    
    /**
     * 提取两个标记之间的子字符串
     * @param text 源字符串
     * @param left 左标记
     * @param right 右标记
     * @return 提取的子字符串
     */
    @JvmStatic
    fun getSubString(text: String, left: String?, right: String?): String {
        val startIndex = if (left.isNullOrEmpty()) {
            0
        } else {
            val index = text.indexOf(left)
            if (index > -1) index + left.length else 0
        }
        
        val endIndex = if (right.isNullOrEmpty()) {
            text.length
        } else {
            val index = text.indexOf(right, startIndex)
            if (index < 0) text.length else index
        }
        
        return text.substring(startIndex, endIndex)
    }
}

// ==================== Kotlin 扩展函数 ====================
// 推荐的Kotlin惯用法

/**
 * 提取两个标记之间的子字符串（扩展函数版本）
 * 
 * 使用示例:
 * ```kotlin
 * val text = "Hello [World]"
 * val result = text.substringBetween("[", "]") // "World"
 * ```
 */
fun String.substringBetween(left: String?, right: String?): String {
    return StringUtil.getSubString(this, left, right)
}

/**
 * 安全的左填充（扩展函数版本）
 * 
 * 使用示例:
 * ```kotlin
 * val num = 5
 * val result = num.toString().padStartSafe(3, '0') // "005"
 * ```
 */
fun String.padStartSafe(totalWidth: Int, padChar: Char = ' '): String {
    return this.padStart(totalWidth, padChar)
}

/**
 * 安全的右填充（扩展函数版本）
 */
fun String.padEndSafe(totalWidth: Int, padChar: Char = ' '): String {
    return this.padEnd(totalWidth, padChar)
}

/**
 * 数字左填充（扩展函数版本）
 * 
 * 使用示例:
 * ```kotlin
 * val num = 5
 * val result = num.padStartWith(3, '0') // "005"
 * ```
 */
fun Int.padStartWith(totalWidth: Int, padChar: Char = ' '): String {
    return this.toString().padStart(totalWidth, padChar)
}

/**
 * 数字右填充（扩展函数版本）
 */
fun Int.padEndWith(totalWidth: Int, padChar: Char = ' '): String {
    return this.toString().padEnd(totalWidth, padChar)
}

// ==================== 私有辅助函数 ====================

/**
 * 将对象转换为字符串，null返回空字符串
 * (Kotlin版本的toStringOrEmpty)
 */
private fun Any?.toStringOrEmpty(): String = this?.toString() ?: ""


package io.github.aoguai.sesameag.util

import java.util.ArrayList
import java.util.Collections

/** 列表工具类，提供对列表的常用操作。 */
object ListUtil {

    /**
     * 创建一个新的ArrayList实例，并使用提供的元素进行初始化。
     *
     * @param objects 要添加到列表中的元素。
     * @return 返回包含所有提供元素的新ArrayList。
     */
    @JvmStatic
    fun <T> newArrayList(vararg objects: T): MutableList<T> {
        val list: MutableList<T> = ArrayList()
        Collections.addAll(list, *objects)
        return list
    }
}


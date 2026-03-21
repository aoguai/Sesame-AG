package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.util.maps.BeachMap
import io.github.aoguai.sesameag.util.maps.IdMapManager

/**
 * 表示支付宝海滩的实体类，包含 ID 和名称。
 *
 * @param i 海滩的 ID
 * @param n 海滩的名称
 */
class AlipayBeach(i: String, n: String) : MapperEntity() {
    init {
        id = i
        name = n
    }

    companion object {
        @Volatile
        private var list: List<AlipayBeach>? = null

        /**
         * 获取包含所有海滩的列表，首次调用时从 BeachMap 初始化。
         * 使用双重检查锁定机制实现懒加载以提高性能。
         *
         * @return 包含所有 AlipayBeach 对象的不可变列表
         */
        /**
         * 获取海滩列表（作为MapperEntity列表）
         * 用于Java互操作
         */
        @JvmStatic
        fun getListAsMapperEntity(): List<MapperEntity> = getList()

        @JvmStatic
        fun getList(): List<AlipayBeach> {
            if (list == null) {
                synchronized(AlipayBeach::class.java) {
                    if (list == null) {
                        list = IdMapManager.getInstance(BeachMap::class.java).map.entries.map { (key, value) ->
                            AlipayBeach(key, value)
                        }
                    }
                }
            }
            return list ?: emptyList()
        }

        /**
         * 根据给定的 ID 删除相应的 AlipayBeach 对象。
         * 首次调用 getList 方法以确保列表已初始化。
         *
         * @param id 要删除的海滩 ID
         */
        @JvmStatic
        fun remove(id: String) {
            getList()
            synchronized(AlipayBeach::class.java) {
                list = list?.filter { it.id != id }
            }
        }
    }
}


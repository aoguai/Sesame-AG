package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.task.antFarm.ChouChouLe
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlin.collections.iterator

class AntFarmIPChouChouLeBenefit(i: String, n: String) : MapperEntity() {
    init {
        id = i
        name = n
    }

    var limitCount: Int = 0
    var cent: Int = 0

    companion object {
        @JvmStatic
        fun getList(): List<AntFarmIPChouChouLeBenefit> {
            val list: MutableList<AntFarmIPChouChouLeBenefit> = ArrayList()
            val uid = UserMap.currentUid
            if (uid.isNullOrEmpty()) return list
            
            val data = ChouChouLe.loadData(uid)
            for ((key, value) in data.shopItems) {
                // 解析存储格式 "名称|限购次数|所需碎片"
                val split = value.split("|")
                val benefit = AntFarmIPChouChouLeBenefit(key, split[0])
                if (split.size >= 3) {
                    benefit.limitCount = split[1].toIntOrNull() ?: 0
                    benefit.cent = split[2].toIntOrNull() ?: 0
                }
                list.add(benefit)
            }
            return list
        }
    }
}

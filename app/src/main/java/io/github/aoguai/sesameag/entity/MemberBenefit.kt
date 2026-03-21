package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.MemberBenefitsMap

class MemberBenefit(i: String, n: String) : MapperEntity() {

    init {
        id = i
        name = n
    }

    companion object {
        fun getList(): List<MemberBenefit> {
            return IdMapManager.getInstance(MemberBenefitsMap::class.java).map
                .map { (key, value) -> MemberBenefit(key, value) }
        }
    }
}

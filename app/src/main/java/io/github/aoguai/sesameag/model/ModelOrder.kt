package io.github.aoguai.sesameag.model

import io.github.aoguai.sesameag.task.AnswerAI.AnswerAI
import io.github.aoguai.sesameag.task.EcoProtection.EcoProtection
import io.github.aoguai.sesameag.task.antCooperate.AntCooperate
import io.github.aoguai.sesameag.task.antDodo.AntDodo
import io.github.aoguai.sesameag.task.antFarm.AntFarm
import io.github.aoguai.sesameag.task.antForest.AntForest
import io.github.aoguai.sesameag.task.antMember.AntMember
import io.github.aoguai.sesameag.task.antOcean.AntOcean
import io.github.aoguai.sesameag.task.antOrchard.AntOrchard
import io.github.aoguai.sesameag.task.antSports.AntSports
import io.github.aoguai.sesameag.task.antStall.AntStall
import io.github.aoguai.sesameag.task.greenFinance.GreenFinance
import io.github.aoguai.sesameag.task.other.OtherTask
import io.github.aoguai.sesameag.task.reserve.Reserve

object ModelOrder {
    private val array = arrayOf(
        BaseModel::class.java,       // 基础设置
        AntForest::class.java,       // 森林
        AntFarm::class.java,         // 庄园
        AntOcean::class.java,        // 海洋
        AntStall::class.java,      // 蚂蚁新村
        AntDodo::class.java,       // 神奇物种
        AntCooperate::class.java,    // 合种
        AntMember::class.java,     // 会员
        AntOrchard::class.java,    // 农场
        AntSports::class.java,       // 运动
        EcoProtection::class.java,     // 古树
        GreenFinance::class.java,  // 绿色经营
        Reserve::class.java,       // 保护地
        OtherTask::class.java,      // 其他
        AnswerAI::class.java         // AI答题

    )

    val allConfig: List<Class<out Model>> = array.toList()
}

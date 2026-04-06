package io.github.aoguai.sesameag.task.antSports

import io.github.aoguai.sesameag.util.Log

private val sportsTag: String = AntSports::class.java.simpleName

internal fun AntSports.runNeverlandWorkflow() {
    if (neverlandTask.value != true && neverlandGrid.value != true) {
        return
    }
    Log.sports(sportsTag, "开始执行健康岛")
    NeverlandTaskHandler().runNeverland()
    Log.sports(sportsTag, "健康岛结束")
}

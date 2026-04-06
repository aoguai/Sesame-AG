package io.github.aoguai.sesameag.task.antMember

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

internal fun AntMember.scheduleBeanWorkflows(
    scope: CoroutineScope,
    deferredTasks: MutableList<Deferred<Unit>>
) {
    if (beanSignIn?.value == true) {
        deferredTasks.add(scope.async(Dispatchers.IO) { beanSignIn() })
    }
    if (beanExchangeBubbleBoost?.value == true) {
        deferredTasks.add(scope.async(Dispatchers.IO) { beanExchangeBubbleBoost() })
    }
}

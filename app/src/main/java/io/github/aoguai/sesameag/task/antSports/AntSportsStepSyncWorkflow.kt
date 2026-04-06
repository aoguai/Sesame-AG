package io.github.aoguai.sesameag.task.antSports

import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.data.StatusFlags

internal fun AntSports.runStepSyncWorkflow() {
    if (isSyncStepEnabled() &&
        !Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE) &&
        earliestSyncStepTime.hasReachedToday()
    ) {
        syncStepTask()
    }
}

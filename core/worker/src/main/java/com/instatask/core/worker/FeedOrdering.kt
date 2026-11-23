package com.instatask.core.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object FeedOrdering {
    fun enqueueBatch(workManager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<InterSessionBatchWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniqueWork(
            "instatask-inter-session",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}

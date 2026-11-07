package com.towfiqul.cocwidget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Re-renders the widget every 15 minutes so the countdowns keep ticking.
 * No network is involved: the worker only recomputes remaining times from
 * the stored export timestamps. */
class RefreshWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        CoCWidgetProvider.updateAllWidgets(applicationContext)
        return Result.success()
    }
}

object RefreshScheduler {
    private const val WORK_NAME = "coc_widget_refresh"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

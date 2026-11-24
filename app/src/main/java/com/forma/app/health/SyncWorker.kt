package com.forma.app.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forma.app.FormaApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync. Only scheduled when the user has granted Health Connect's background-read permission
 * and enabled background sync in FORMA; otherwise FORMA syncs when the app comes to the foreground.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FormaApp).container
        if (!c.healthSource.backgroundReadGranted()) return Result.success()
        return when (c.healthSyncer.sync(3)) {
            is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "forma-health-sync"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
            } else wm.cancelUniqueWork(NAME)
        }
    }
}

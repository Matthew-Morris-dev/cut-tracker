package dev.matthewmorris.cuttracker.data.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.matthewmorris.cuttracker.CutTrackerApp
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as CutTrackerApp).container.healthSync.sync()
        // A failure is retried at the next period or app open; retrying sooner won't fix a revoked permission.
        return Result.success()
    }

    companion object {
        private const val NAME = "health-connect-sync"

        /** Runs every 6 hours while background read is granted; cancelled as soon as it isn't. */
        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                workManager.enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build(),
                )
            } else {
                workManager.cancelUniqueWork(NAME)
            }
        }
    }
}

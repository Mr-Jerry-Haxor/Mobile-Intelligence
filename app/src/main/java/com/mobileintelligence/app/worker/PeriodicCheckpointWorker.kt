package com.mobileintelligence.app.worker

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.*
import com.mobileintelligence.app.data.preferences.AppPreferences
import com.mobileintelligence.app.service.MonitoringService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic checkpoint worker — runs every 15 minutes.
 * Acts as a secondary watchdog: ensures MonitoringService is alive.
 */
class PeriodicCheckpointWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "CheckpointWorker"
        private const val WORK_NAME = "periodic_checkpoint"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicCheckpointWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    10, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPreferences(applicationContext)
            val isTrackingEnabled = prefs.isTrackingEnabled.first()
            if (isTrackingEnabled && !isServiceRunning()) {
                Log.w(TAG, "MonitoringService not running — restarting via checkpoint")
                MonitoringService.start(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Checkpoint failed", e)
            try { MonitoringService.start(applicationContext) } catch (_: Exception) {}
            Result.retry()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in am.getRunningServices(Int.MAX_VALUE)) {
            if (MonitoringService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

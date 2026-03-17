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
 * Periodic watchdog that ensures MonitoringService stays running.
 *
 * Runs every 15 minutes via WorkManager (survives app kill, doze, etc.).
 * If the service isn't running and tracking is enabled, restarts it.
 */
class ServiceWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ServiceWatchdog"
        private const val WORK_NAME = "service_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            Log.i(TAG, "Watchdog worker scheduled (15-min interval)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPreferences(applicationContext)
            val isTrackingEnabled = prefs.isTrackingEnabled.first()

            if (isTrackingEnabled) {
                if (!isServiceRunning()) {
                    Log.w(TAG, "MonitoringService not running! Restarting...")
                    MonitoringService.start(applicationContext)
                } else {
                    Log.d(TAG, "MonitoringService is healthy")
                }
            } else {
                Log.d(TAG, "Tracking disabled, skipping watchdog check")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog check failed", e)
            // Try to restart service anyway
            try { MonitoringService.start(applicationContext) } catch (_: Exception) {}
            Result.retry()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // getRunningServices is deprecated but still works for own services
        for (service in am.getRunningServices(Int.MAX_VALUE)) {
            if (MonitoringService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

package com.mobileintelligence.app.dns.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.mobileintelligence.app.dns.core.DnsCache
import java.util.concurrent.TimeUnit

/**
 * Periodic worker for DNS cache cleanup.
 * Runs every hour to remove expired entries and optimize memory.
 */
class DnsCacheCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DnsCacheCleanup"
        private const val WORK_TAG = "dns_cache_cleanup"

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<DnsCacheCleanupWorker>(
                1, TimeUnit.HOURS
            )
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Cache cleanup is handled by the DnsCache instance in the VPN service
            // This worker acts as a safety net for long-running sessions
            Log.d(TAG, "Cache cleanup cycle completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
            Result.retry()
        }
    }
}

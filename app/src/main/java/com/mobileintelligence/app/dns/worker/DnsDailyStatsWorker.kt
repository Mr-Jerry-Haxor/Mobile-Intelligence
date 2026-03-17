package com.mobileintelligence.app.dns.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.dns.data.DnsPreferences
import com.mobileintelligence.app.dns.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Daily worker that generates DNS statistics summaries and purges old data.
 * Runs at midnight via WorkManager periodic task.
 */
class DnsDailyStatsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DnsDailyStats"
        private const val WORK_TAG = "dns_daily_stats"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val work = PeriodicWorkRequestBuilder<DnsDailyStatsWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .setInitialDelay(calculateDelayToMidnight(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        private fun calculateDelayToMidnight(): Long {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 5) // 00:05 to avoid clock edge cases
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return midnight.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = DnsDatabase.getInstance(applicationContext)
            val prefs = DnsPreferences(applicationContext)

            // Generate stats for yesterday
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
                Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            )

            generateDailyStats(database, yesterday)
            generateAppStats(database, yesterday)
            generateDomainStats(database, yesterday)

            // Purge old data
            val retentionDays = prefs.logRetentionDays.first()
            purgeOldData(database, retentionDays)

            Log.d(TAG, "Daily stats generated for $yesterday")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily stats", e)
            Result.retry()
        }
    }

    private suspend fun generateDailyStats(database: DnsDatabase, dateStr: String) {
        val dao = database.dnsQueryDao()
        val statsDao = database.dnsDailyStatsDao()

        val total = dao.getTotalQueriesForDateSync(dateStr)
        val blocked = dao.getBlockedQueriesForDateSync(dateStr)
        val cached = dao.getCachedCountForDateSync(dateStr)
        val avgResponseTime = dao.getAvgResponseTimeForDateSync(dateStr) ?: 0L
        val uniqueDomains = dao.getUniqueDomainCountForDate(dateStr)
        val uniqueApps = dao.getUniqueAppCountForDate(dateStr)
        val adsBlocked = dao.getAdsBlockedCount(dateStr)
        val trackersBlocked = dao.getTrackersBlockedCount(dateStr)
        val malwareBlocked = dao.getMalwareBlockedCount(dateStr)

        val stats = DnsDailyStats(
            dateStr = dateStr,
            totalQueries = total,
            blockedQueries = blocked,
            allowedQueries = total - blocked,
            cachedQueries = cached,
            avgResponseTimeMs = avgResponseTime,
            uniqueDomains = uniqueDomains,
            uniqueApps = uniqueApps,
            adsBlocked = adsBlocked,
            trackersBlocked = trackersBlocked,
            malwareBlocked = malwareBlocked
        )

        statsDao.insert(stats)
    }

    private suspend fun generateAppStats(database: DnsDatabase, dateStr: String) {
        val dao = database.dnsQueryDao()
        val appStatsDao = database.dnsAppStatsDao()

        // Aggregate per-app DNS stats from raw query log
        val topApps = dao.getTopAppsForDateSync(dateStr, 100)
        for (app in topApps) {
            val uniqueDomains = dao.getUniqueDomainCountForAppDate(dateStr, app.appPackage)
            val topDomain = dao.getTopDomainForAppDate(dateStr, app.appPackage)

            appStatsDao.insert(DnsAppStats(
                dateStr = dateStr,
                appPackage = app.appPackage,
                totalQueries = app.cnt,
                blockedQueries = app.blockedCnt,
                uniqueDomains = uniqueDomains,
                topDomain = topDomain
            ))
        }
        Log.d(TAG, "Generated app stats: ${topApps.size} apps for $dateStr")
    }

    private suspend fun generateDomainStats(database: DnsDatabase, dateStr: String) {
        val dao = database.dnsQueryDao()
        val domainStatsDao = database.dnsDomainStatsDao()

        // Aggregate per-domain stats from raw query log
        val topDomains = dao.getTopDomainsForDateSync(dateStr, 200)
        for (domain in topDomains) {
            val blockReason = if (domain.blocked) {
                dao.getBlockReasonForDomainDate(dateStr, domain.domain)
            } else null

            domainStatsDao.insert(DnsDomainStats(
                dateStr = dateStr,
                domain = domain.domain,
                queryCount = domain.cnt,
                blocked = domain.blocked,
                blockReason = blockReason
            ))
        }
        Log.d(TAG, "Generated domain stats: ${topDomains.size} domains for $dateStr")
    }

    private suspend fun purgeOldData(database: DnsDatabase, retentionDays: Int) {
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(cutoffTimestamp))

        val deleted = database.dnsQueryDao().deleteOlderThan(cutoffTimestamp)
        database.dnsDailyStatsDao().deleteOlderThan(cutoffDate)
        database.dnsAppStatsDao().deleteOlderThan(cutoffDate)
        database.dnsDomainStatsDao().deleteOlderThan(cutoffDate)

        Log.d(TAG, "Purged $deleted old query logs (retention: $retentionDays days)")
    }
}

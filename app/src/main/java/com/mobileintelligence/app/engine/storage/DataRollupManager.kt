package com.mobileintelligence.app.engine.storage

import android.content.Context
import android.util.Log
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.dns.data.DnsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data Rollup Manager — performs staged data compression.
 *
 * Midnight rollup pipeline:
 * 1. Purge expired raw data (> retention days)
 * 2. Purge expired DNS query logs
 * 3. Purge old daily stats beyond max retention
 * 4. Vacuum databases to reclaim space
 *
 * The existing MidnightRolloverWorker and PeriodicCheckpointWorker
 * already handle daily summary creation. This manager handles
 * the purge/archival layer on top of that.
 */
class DataRollupManager(private val context: Context) {

    companion object {
        private const val TAG = "DataRollupManager"
        private const val AVG_RAW_RECORD_BYTES = 200  // Estimated avg row size
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    data class RollupResult(
        val rawToHourly: Int = 0,
        val hourlyToDaily: Int = 0,
        val purgedRaw: Int = 0,
        val purgedHourly: Int = 0,
        val purgedDns: Int = 0,
        val estimatedBytesFreed: Long = 0,
        val duration: Long = 0
    )

    /**
     * Perform the full multi-level rollup.
     */
    suspend fun performFullRollup(policy: RetentionPolicy): RollupResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting full data rollup...")

            var totalPurgedRaw = 0
            var totalPurgedDns = 0
            var totalPurgedHourly = 0

            try {
                // ── Stage 1: Purge expired screen data ──────────
                totalPurgedRaw = purgeExpiredScreenData(policy)

                // ── Stage 2: Purge expired DNS data ─────────────
                totalPurgedDns = purgeExpiredDnsData(policy)

                // ── Stage 3: Purge old daily summaries ──────────
                totalPurgedHourly = purgeOldSummaries(policy)

                // ── Stage 4: Vacuum databases ───────────────────
                vacuumDatabases()

            } catch (e: Exception) {
                Log.e(TAG, "Rollup error", e)
            }

            val duration = System.currentTimeMillis() - startTime
            val estimatedFreed = (totalPurgedRaw + totalPurgedDns).toLong() * AVG_RAW_RECORD_BYTES

            Log.d(TAG, "Rollup complete in ${duration}ms: " +
                    "purgedRaw=$totalPurgedRaw, purgedDns=$totalPurgedDns, " +
                    "purgedSummaries=$totalPurgedHourly, " +
                    "~${estimatedFreed / 1024}KB freed")

            RollupResult(
                rawToHourly = totalPurgedRaw, // We purge raw after summaries exist
                purgedRaw = totalPurgedRaw,
                purgedDns = totalPurgedDns,
                purgedHourly = totalPurgedHourly,
                estimatedBytesFreed = estimatedFreed,
                duration = duration
            )
        }

    /**
     * Purge screen sessions and unlock events older than retention period.
     */
    private suspend fun purgeExpiredScreenData(policy: RetentionPolicy): Int {
        return try {
            val db = IntelligenceDatabase.getInstance(context)
            val cutoffDate = policy.cutoffDateStr(RetentionPolicy.RAW_RETENTION_DAYS)

            // Purge old screen sessions
            val sessionsDeleted = db.screenSessionDao().deleteOlderThan(cutoffDate)

            // Purge old unlock events
            val unlocksDeleted = db.unlockEventDao().deleteOlderThan(cutoffDate)

            Log.d(TAG, "Purged screen data: $sessionsDeleted sessions, $unlocksDeleted unlocks")
            sessionsDeleted + unlocksDeleted
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge screen data", e)
            0
        }
    }

    /**
     * Purge DNS query logs older than retention period.
     */
    private suspend fun purgeExpiredDnsData(policy: RetentionPolicy): Int {
        return try {
            val db = DnsDatabase.getInstance(context)
            val cutoffTimestamp = policy.cutoffTimestamp(RetentionPolicy.RAW_DNS_RETENTION_DAYS)

            // Purge old DNS queries
            val queriesDeleted = db.dnsQueryDao().deleteOlderThan(cutoffTimestamp)

            Log.d(TAG, "Purged DNS data: $queriesDeleted queries (cutoff: $cutoffTimestamp)")
            queriesDeleted
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge DNS data", e)
            0
        }
    }

    /**
     * Purge old daily summaries beyond max retention.
     */
    private suspend fun purgeOldSummaries(policy: RetentionPolicy): Int {
        return try {
            val db = IntelligenceDatabase.getInstance(context)
            val dnsDb = DnsDatabase.getInstance(context)

            val screenCutoff = policy.cutoffDateStr(RetentionPolicy.DAILY_RETENTION_DAYS)
            val dnsCutoff = policy.cutoffDateStr(RetentionPolicy.DAILY_DNS_RETENTION_DAYS)

            var count = 0

            // Purge old screen daily summaries
            count += db.dailySummaryDao().deleteOlderThan(screenCutoff)

            // Purge old DNS daily stats
            count += dnsDb.dnsDailyStatsDao().deleteOlderThan(dnsCutoff)

            // Purge old DNS app stats
            count += dnsDb.dnsAppStatsDao().deleteOlderThan(dnsCutoff)

            Log.d(TAG, "Purged old summaries: $count records")
            count
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge old summaries", e)
            0
        }
    }

    /**
     * Vacuum databases to reclaim freed space.
     */
    private fun vacuumDatabases() {
        try {
            val screenDb = IntelligenceDatabase.getInstance(context)
            screenDb.openHelper.writableDatabase.execSQL("VACUUM")
            Log.d(TAG, "Vacuumed screen database")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to vacuum screen DB", e)
        }

        try {
            val dnsDb = DnsDatabase.getInstance(context)
            dnsDb.openHelper.writableDatabase.execSQL("VACUUM")
            Log.d(TAG, "Vacuumed DNS database")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to vacuum DNS DB", e)
        }
    }

    /**
     * Estimate total data stored in bytes.
     */
    fun estimateTotalDataSize(): Long {
        val screenDb = context.getDatabasePath("mobile_intelligence.db")
        val dnsDb = context.getDatabasePath("dns_firewall.db")
        return (if (screenDb.exists()) screenDb.length() else 0) +
                (if (dnsDb.exists()) dnsDb.length() else 0)
    }

    /**
     * Get date string for display.
     */
    fun getOldestAllowedDate(retentionDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -retentionDays)
        return dateFormat.format(cal.time)
    }
}

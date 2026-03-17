package com.mobileintelligence.app.engine.storage

/**
 * Multi-level data retention policy for 3-year scalability.
 *
 * Level 1 — Raw (0-30 days):
 *   Full granularity. Every screen session, unlock event, DNS query.
 *   Enables timeline replay, detailed per-minute analysis.
 *
 * Level 2 — Hourly (30-180 days):
 *   Hourly aggregates. Session count, total screen time, query counts per hour.
 *   Enables trend analysis, heatmaps, weekly comparisons.
 *
 * Level 3 — Daily (180 days - 3 years):
 *   Daily summaries only. Total screen time, app ranking, DNS totals.
 *   Enables long-term trend, yearly comparisons, addiction scoring.
 *
 * Rollup schedule:
 *   - Midnight daily: compress yesterday's data if > 24h old
 *   - Weekly: compress raw → hourly for data > 30 days
 *   - Monthly: compress hourly → daily for data > 180 days
 *   - Data > 3 years: permanently deleted
 */
class RetentionPolicy {

    companion object {
        const val RAW_RETENTION_DAYS = 30
        const val HOURLY_RETENTION_DAYS = 180
        const val DAILY_RETENTION_DAYS = 1095  // 3 years

        const val RAW_DNS_RETENTION_DAYS = 14      // DNS logs are bulkier
        const val HOURLY_DNS_RETENTION_DAYS = 90
        const val DAILY_DNS_RETENTION_DAYS = 365

        // Rollup thresholds
        const val MIN_RAW_AGE_FOR_HOURLY_DAYS = 30
        const val MIN_HOURLY_AGE_FOR_DAILY_DAYS = 180
    }

    data class RetentionLevel(
        val level: Int,
        val name: String,
        val retentionDays: Int,
        val description: String
    )

    val screenRetention = listOf(
        RetentionLevel(1, "Raw", RAW_RETENTION_DAYS,
            "Full session details, unlock events, app transitions"),
        RetentionLevel(2, "Hourly", HOURLY_RETENTION_DAYS,
            "Hourly aggregates: session count, screen time, app switches"),
        RetentionLevel(3, "Daily", DAILY_RETENTION_DAYS,
            "Daily summaries: total screen time, top apps, scores")
    )

    val dnsRetention = listOf(
        RetentionLevel(1, "Raw", RAW_DNS_RETENTION_DAYS,
            "Individual DNS queries with domain, app, timing"),
        RetentionLevel(2, "Hourly", HOURLY_DNS_RETENTION_DAYS,
            "Hourly query/blocked/cached totals per app"),
        RetentionLevel(3, "Daily", DAILY_DNS_RETENTION_DAYS,
            "Daily DNS summary: totals, top domains, categories")
    )

    /**
     * Calculate cutoff timestamp for a given retention level.
     */
    fun cutoffTimestamp(retentionDays: Int): Long =
        System.currentTimeMillis() - (retentionDays.toLong() * 24 * 3600_000)

    /**
     * Calculate cutoff date string for a given retention level.
     */
    fun cutoffDateStr(retentionDays: Int): String {
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 3600_000)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = cutoff }
        return String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Check if a timestamp is eligible for rollup to the next level.
     */
    fun isEligibleForHourlyRollup(timestamp: Long): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > MIN_RAW_AGE_FOR_HOURLY_DAYS * 24 * 3600_000L
    }

    fun isEligibleForDailyRollup(timestamp: Long): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > MIN_HOURLY_AGE_FOR_DAILY_DAYS * 24 * 3600_000L
    }

    /**
     * Estimate storage savings from a rollup.
     * Raw → Hourly: ~95% reduction (24 hourly rows vs ~1000 raw rows/day)
     * Hourly → Daily: ~96% reduction (1 daily row vs 24 hourly rows)
     */
    fun estimateRollupSavings(rawRecordCount: Int, avgRecordSizeBytes: Int): Long {
        val rawBytes = rawRecordCount.toLong() * avgRecordSizeBytes
        // After hourly rollup, we keep ~5% of data
        return (rawBytes * 0.95).toLong()
    }
}

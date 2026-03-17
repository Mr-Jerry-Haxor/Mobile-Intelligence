package com.mobileintelligence.app.engine.intelligence

import kotlin.math.min

/**
 * Privacy Exposure Score (PES) — 0 to 100 scale.
 *
 * Measures privacy risk based on DNS activity patterns.
 * Higher score = more privacy exposure / risk.
 *
 * Scoring components (weighted):
 * - Tracker contact rate:       30% (more tracker domains = higher risk)
 * - Blocked vs allowed ratio:   20% (lower block rate = higher exposure)
 * - DoH bypass attempts:        20% (apps trying to bypass filtering)
 * - Unique tracker domains:     15% (diversity of tracking)
 * - App privacy behaviors:      15% (apps with most tracker contacts)
 */
class PrivacyExposureScore {

    companion object {
        private const val WINDOW_SIZE = 500
        private val TRACKER_CATEGORIES = setOf("ads", "analytics", "tracker", "malware")

        private const val WEIGHT_TRACKER_RATE = 0.30f
        private const val WEIGHT_BLOCK_RATIO = 0.20f
        private const val WEIGHT_DOH_BYPASS = 0.20f
        private const val WEIGHT_TRACKER_DIVERSITY = 0.15f
        private const val WEIGHT_APP_PRIVACY = 0.15f
    }

    data class DnsRecord(
        val domain: String,
        val blocked: Boolean,
        val appPackage: String?,
        val category: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DoHRecord(
        val domain: String,
        val appPackage: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val recentQueries = ArrayDeque<DnsRecord>(WINDOW_SIZE)
    private val dohAttempts = ArrayDeque<DoHRecord>(100)
    private val trackerDomainsToday = mutableSetOf<String>()
    private val appTrackerCounts = mutableMapOf<String, Int>()
    private var lastDayReset = dayOfYear()

    // ── Event Recording ─────────────────────────────────────────

    fun recordDnsQuery(domain: String, blocked: Boolean, appPackage: String?, category: String?) {
        val record = DnsRecord(domain, blocked, appPackage, category)
        recentQueries.addLast(record)
        if (recentQueries.size > WINDOW_SIZE) recentQueries.removeFirst()

        checkDayReset()

        // Track tracker domain diversity
        if (isTrackerCategory(category) || (!blocked && isLikelyTracker(domain))) {
            trackerDomainsToday.add(domain)
        }

        // Track per-app tracker contacts
        if (appPackage != null && (isTrackerCategory(category) || isLikelyTracker(domain))) {
            appTrackerCounts[appPackage] = (appTrackerCounts[appPackage] ?: 0) + 1
        }
    }

    fun recordDoHAttempt(domain: String, appPackage: String?) {
        dohAttempts.addLast(DoHRecord(domain, appPackage))
        if (dohAttempts.size > 100) dohAttempts.removeFirst()
    }

    // ── Score Calculation ───────────────────────────────────────

    /**
     * Calculate Privacy Exposure Score (0-100, higher = more exposure).
     */
    fun calculate(): Float {
        val trackerRate = calculateTrackerContactRate()
        val blockRatio = calculateBlockRatio()
        val dohScore = calculateDoHBypassScore()
        val diversityScore = calculateTrackerDiversityScore()
        val appPrivacy = calculateAppPrivacyScore()

        val raw = trackerRate * WEIGHT_TRACKER_RATE +
                blockRatio * WEIGHT_BLOCK_RATIO +
                dohScore * WEIGHT_DOH_BYPASS +
                diversityScore * WEIGHT_TRACKER_DIVERSITY +
                appPrivacy * WEIGHT_APP_PRIVACY

        return min(100f, raw)
    }

    /**
     * Tracker contact rate: what percentage of queries go to trackers.
     */
    private fun calculateTrackerContactRate(): Float {
        val todayQueries = recentQueries.filter { isToday(it.timestamp) }
        if (todayQueries.isEmpty()) return 0f

        val trackerQueries = todayQueries.count { isTrackerCategory(it.category) || isLikelyTracker(it.domain) }
        val rate = trackerQueries.toFloat() / todayQueries.size

        return when {
            rate < 0.10f -> rate / 0.10f * 20f           // < 10% tracker queries = low risk
            rate < 0.25f -> 20f + (rate - 0.10f) / 0.15f * 30f  // 10-25%
            rate < 0.50f -> 50f + (rate - 0.25f) / 0.25f * 30f  // 25-50%
            else -> 80f + min(20f, (rate - 0.50f) / 0.50f * 20f) // > 50%
        }
    }

    /**
     * Block ratio: inverse of block effectiveness.
     * If many trackers are getting through (allowed), score is higher.
     */
    private fun calculateBlockRatio(): Float {
        val todayQueries = recentQueries.filter { isToday(it.timestamp) }
        if (todayQueries.isEmpty()) return 0f

        val totalTrackers = todayQueries.count { isTrackerCategory(it.category) || isLikelyTracker(it.domain) }
        if (totalTrackers == 0) return 0f

        val blockedTrackers = todayQueries.count {
            it.blocked && (isTrackerCategory(it.category) || isLikelyTracker(it.domain))
        }

        val blockRate = blockedTrackers.toFloat() / totalTrackers

        // Higher block rate = lower exposure
        return (100f - blockRate * 100f).coerceIn(0f, 100f)
    }

    /**
     * DoH bypass attempts score.
     */
    private fun calculateDoHBypassScore(): Float {
        val todayAttempts = dohAttempts.count { isToday(it.timestamp) }
        return when {
            todayAttempts == 0 -> 0f
            todayAttempts <= 5 -> 30f
            todayAttempts <= 20 -> 60f
            todayAttempts <= 50 -> 80f
            else -> 100f
        }
    }

    /**
     * Tracker diversity: how many unique tracker domains contacted today.
     */
    private fun calculateTrackerDiversityScore(): Float {
        val count = trackerDomainsToday.size
        return when {
            count <= 5 -> count.toFloat() / 5f * 20f
            count <= 20 -> 20f + (count - 5).toFloat() / 15f * 30f
            count <= 50 -> 50f + (count - 20).toFloat() / 30f * 30f
            else -> 80f + min(20f, (count - 50).toFloat() / 50f * 20f)
        }
    }

    /**
     * App privacy: score based on which apps contact the most trackers.
     */
    private fun calculateAppPrivacyScore(): Float {
        if (appTrackerCounts.isEmpty()) return 0f

        val topApp = appTrackerCounts.maxByOrNull { it.value } ?: return 0f
        val topCount = topApp.value

        return when {
            topCount <= 10 -> topCount.toFloat() / 10f * 20f
            topCount <= 50 -> 20f + (topCount - 10).toFloat() / 40f * 30f
            topCount <= 200 -> 50f + (topCount - 50).toFloat() / 150f * 30f
            else -> 80f + min(20f, (topCount - 200).toFloat() / 200f * 20f)
        }
    }

    // ── Accessors ───────────────────────────────────────────────

    /**
     * Get top tracker-contacting apps.
     */
    fun getTopTrackerApps(limit: Int = 10): List<Pair<String, Int>> =
        appTrackerCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    /**
     * Get unique tracker domain count today.
     */
    fun getTrackerDomainCount(): Int = trackerDomainsToday.size

    // ── Helpers ─────────────────────────────────────────────────

    private fun isTrackerCategory(category: String?): Boolean =
        category != null && TRACKER_CATEGORIES.any { category.contains(it, ignoreCase = true) }

    private fun isLikelyTracker(domain: String): Boolean {
        val keywords = listOf("track", "analytics", "telemetry", "beacon", "metric", "pixel",
            "adservice", "ads.", "doubleclick", "adsrvr", "criteo", "moat")
        return keywords.any { domain.contains(it, ignoreCase = true) }
    }

    private fun isToday(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val year = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.DAY_OF_YEAR) == today &&
                cal.get(java.util.Calendar.YEAR) == year
    }

    private fun dayOfYear(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun checkDayReset() {
        val today = dayOfYear()
        if (today != lastDayReset) {
            trackerDomainsToday.clear()
            appTrackerCounts.clear()
            lastDayReset = today
        }
    }
}

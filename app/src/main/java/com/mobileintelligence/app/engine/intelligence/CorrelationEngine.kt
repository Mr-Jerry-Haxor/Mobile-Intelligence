package com.mobileintelligence.app.engine.intelligence

import com.mobileintelligence.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Correlation Engine — Cross-analyzes Screen and DNS data streams
 * to produce human-readable actionable insights.
 *
 * Example insights:
 * - "You opened Instagram at 11:12 PM. 37 trackers contacted in 2 minutes."
 * - "Twitter session (45 min): 128 analytics calls, 23 ad networks contacted."
 * - "Between 10 PM - 12 AM, 68% of DNS queries were tracker-related."
 * - "Chrome contacted 15 unique tracking domains during your 3-minute session."
 *
 * Architecture: Listens to EngineEventBus for both Screen and DNS events,
 * maintains a time-correlated buffer, and generates insights when patterns emerge.
 */
class CorrelationEngine {

    companion object {
        private const val CORRELATION_WINDOW_MS = 5 * 60_000L  // 5 min correlation window
        private const val MAX_INSIGHTS = 50
        private const val MAX_EVENTS = 500
    }

    data class ScreenActivity(
        val appPackage: String,
        val appName: String,
        val startTime: Long,
        val endTime: Long = 0,
        val sessionType: SessionType? = null
    )

    data class DnsActivity(
        val domain: String,
        val blocked: Boolean,
        val appPackage: String?,
        val category: String?,
        val timestamp: Long
    )

    data class CorrelationInsight(
        val message: String,
        val category: InsightCategory,
        val severity: PatternSeverity,
        val appPackage: String?,
        val timestamp: Long = System.currentTimeMillis(),
        val data: Map<String, Any> = emptyMap()
    )

    enum class InsightCategory {
        TRACKER_BURST,        // App caused many tracker calls
        APP_PRIVACY_PROFILE,  // App's privacy behavior summary
        NIGHT_TRACKING,       // Trackers active during sleep hours
        SESSION_PRIVACY_COST, // Privacy cost of a specific session
        DOH_EVASION,          // App trying to bypass DNS filtering
        HOURLY_PRIVACY_TREND  // Hours with highest tracker activity
    }

    // Buffered event streams
    private val screenActivities = ArrayDeque<ScreenActivity>(MAX_EVENTS)
    private val dnsActivities = ArrayDeque<DnsActivity>(MAX_EVENTS)

    // Generated insights
    private val _insights = MutableStateFlow<List<CorrelationInsight>>(emptyList())
    val insights: StateFlow<List<CorrelationInsight>> = _insights.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    // ── Event Ingestion ─────────────────────────────────────────

    fun recordScreenActivity(activity: ScreenActivity) {
        screenActivities.addLast(activity)
        if (screenActivities.size > MAX_EVENTS) screenActivities.removeFirst()
        checkCorrelations()
    }

    fun recordDnsActivity(activity: DnsActivity) {
        dnsActivities.addLast(activity)
        if (dnsActivities.size > MAX_EVENTS) dnsActivities.removeFirst()
    }

    fun recordAppTransition(appPackage: String, appName: String, timestamp: Long) {
        // Close previous activity
        val prev = screenActivities.lastOrNull()
        if (prev != null && prev.endTime == 0L) {
            val closed = prev.copy(endTime = timestamp)
            screenActivities.removeLast()
            screenActivities.addLast(closed)
        }

        // Start new activity
        screenActivities.addLast(ScreenActivity(
            appPackage = appPackage,
            appName = appName,
            startTime = timestamp
        ))
        if (screenActivities.size > MAX_EVENTS) screenActivities.removeFirst()
    }

    // ── Correlation Analysis ────────────────────────────────────

    private fun checkCorrelations() {
        val newInsights = mutableListOf<CorrelationInsight>()

        // Check latest screen activity for tracker burst
        val latestScreen = screenActivities.lastOrNull() ?: return
        if (latestScreen.endTime > 0) {
            detectTrackerBurst(latestScreen)?.let { newInsights.add(it) }
            detectSessionPrivacyCost(latestScreen)?.let { newInsights.add(it) }
        }

        if (newInsights.isNotEmpty()) {
            val current = _insights.value.toMutableList()
            current.addAll(newInsights)
            if (current.size > MAX_INSIGHTS) {
                _insights.value = current.takeLast(MAX_INSIGHTS)
            } else {
                _insights.value = current
            }
        }
    }

    /**
     * Detect tracker burst: many tracker DNS queries within a short time of opening an app.
     */
    private fun detectTrackerBurst(screen: ScreenActivity): CorrelationInsight? {
        val windowStart = screen.startTime
        val windowEnd = if (screen.endTime > 0) screen.endTime else screen.startTime + CORRELATION_WINDOW_MS

        // Find DNS queries during this screen session
        val dnsInWindow = dnsActivities.filter { dns ->
            dns.timestamp in windowStart..windowEnd &&
                    (dns.appPackage == screen.appPackage || dns.appPackage == null)
        }

        val trackerCount = dnsInWindow.count { isTracker(it.category, it.domain) }
        val blockedTrackers = dnsInWindow.count { it.blocked && isTracker(it.category, it.domain) }

        if (trackerCount >= 10) {
            val timeStr = timeFormat.format(Date(screen.startTime))
            val durationSec = (windowEnd - windowStart) / 1000

            return CorrelationInsight(
                message = "You opened ${screen.appName} at $timeStr. " +
                        "$trackerCount trackers contacted in ${durationSec}s " +
                        "($blockedTrackers blocked).",
                category = InsightCategory.TRACKER_BURST,
                severity = if (trackerCount > 30) PatternSeverity.ALERT else PatternSeverity.WARNING,
                appPackage = screen.appPackage,
                data = mapOf(
                    "trackerCount" to trackerCount,
                    "blockedCount" to blockedTrackers,
                    "totalDns" to dnsInWindow.size,
                    "durationSec" to durationSec
                )
            )
        }
        return null
    }

    /**
     * Summarize privacy cost of a completed session.
     */
    private fun detectSessionPrivacyCost(screen: ScreenActivity): CorrelationInsight? {
        if (screen.endTime == 0L) return null
        val durationMin = (screen.endTime - screen.startTime) / 60_000

        if (durationMin < 2) return null // Skip very short sessions

        val dnsInSession = dnsActivities.filter { dns ->
            dns.timestamp in screen.startTime..screen.endTime
        }

        val totalQueries = dnsInSession.size
        val uniqueDomains = dnsInSession.map { it.domain }.toSet().size
        val trackerDomains = dnsInSession.filter { isTracker(it.category, it.domain) }
            .map { it.domain }.toSet().size
        val blocked = dnsInSession.count { it.blocked }

        if (totalQueries >= 20 && trackerDomains >= 5) {
            return CorrelationInsight(
                message = "${screen.appName} session (${durationMin} min): " +
                        "$totalQueries DNS queries, $uniqueDomains unique domains, " +
                        "$trackerDomains tracker domains ($blocked blocked).",
                category = InsightCategory.SESSION_PRIVACY_COST,
                severity = if (trackerDomains > 15) PatternSeverity.ALERT else PatternSeverity.INFO,
                appPackage = screen.appPackage,
                data = mapOf(
                    "durationMin" to durationMin,
                    "totalQueries" to totalQueries,
                    "uniqueDomains" to uniqueDomains,
                    "trackerDomains" to trackerDomains,
                    "blockedQueries" to blocked
                )
            )
        }
        return null
    }

    // ── On-Demand Analysis ──────────────────────────────────────

    /**
     * Generate a privacy profile for a specific app based on correlated data.
     */
    fun getAppPrivacyProfile(appPackage: String): AppPrivacyProfile {
        val appDns = dnsActivities.filter { it.appPackage == appPackage }
        val appSessions = screenActivities.filter { it.appPackage == appPackage }

        val totalQueries = appDns.size
        val trackerQueries = appDns.count { isTracker(it.category, it.domain) }
        val blockedQueries = appDns.count { it.blocked }
        val uniqueTrackers = appDns.filter { isTracker(it.category, it.domain) }
            .map { it.domain }.toSet()

        val totalSessionTime = appSessions
            .filter { it.endTime > 0 }
            .sumOf { it.endTime - it.startTime }

        val trackersPerMinute = if (totalSessionTime > 60_000) {
            trackerQueries.toFloat() / (totalSessionTime / 60_000f)
        } else 0f

        return AppPrivacyProfile(
            appPackage = appPackage,
            totalDnsQueries = totalQueries,
            trackerQueries = trackerQueries,
            blockedQueries = blockedQueries,
            uniqueTrackerDomains = uniqueTrackers,
            totalSessionTimeMs = totalSessionTime,
            trackersPerMinute = trackersPerMinute,
            privacyRating = calculateAppPrivacyRating(trackerQueries, totalQueries, trackersPerMinute)
        )
    }

    /**
     * Get hourly privacy trend for today.
     */
    fun getHourlyPrivacyTrend(): List<HourlyPrivacy> {
        val hourly = mutableMapOf<Int, MutableList<DnsActivity>>()
        for (dns in dnsActivities) {
            if (isToday(dns.timestamp)) {
                val hour = hourOfDay(dns.timestamp)
                hourly.getOrPut(hour) { mutableListOf() }.add(dns)
            }
        }

        return (0..23).map { hour ->
            val queries = hourly[hour] ?: emptyList()
            HourlyPrivacy(
                hour = hour,
                totalQueries = queries.size,
                trackerQueries = queries.count { isTracker(it.category, it.domain) },
                blockedQueries = queries.count { it.blocked }
            )
        }
    }

    // ── Data classes ────────────────────────────────────────────

    data class AppPrivacyProfile(
        val appPackage: String,
        val totalDnsQueries: Int,
        val trackerQueries: Int,
        val blockedQueries: Int,
        val uniqueTrackerDomains: Set<String>,
        val totalSessionTimeMs: Long,
        val trackersPerMinute: Float,
        val privacyRating: PrivacyRating
    )

    enum class PrivacyRating(val label: String) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        MODERATE("Moderate"),
        POOR("Poor"),
        TERRIBLE("Terrible")
    }

    data class HourlyPrivacy(
        val hour: Int,
        val totalQueries: Int,
        val trackerQueries: Int,
        val blockedQueries: Int
    ) {
        val trackerPercentage: Float
            get() = if (totalQueries > 0) trackerQueries.toFloat() / totalQueries * 100f else 0f
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun isTracker(category: String?, domain: String): Boolean {
        val trackerCategories = setOf("ads", "analytics", "tracker", "malware", "ad_network")
        if (category != null && trackerCategories.any { category.contains(it, true) }) return true
        val keywords = listOf("track", "analytics", "telemetry", "ad", "pixel", "beacon", "metric")
        return keywords.any { domain.contains(it, true) }
    }

    private fun calculateAppPrivacyRating(
        trackerQueries: Int,
        totalQueries: Int,
        trackersPerMinute: Float
    ): PrivacyRating {
        val ratio = if (totalQueries > 0) trackerQueries.toFloat() / totalQueries else 0f
        return when {
            ratio < 0.05f && trackersPerMinute < 0.5f -> PrivacyRating.EXCELLENT
            ratio < 0.15f && trackersPerMinute < 1f -> PrivacyRating.GOOD
            ratio < 0.30f || trackersPerMinute < 3f -> PrivacyRating.MODERATE
            ratio < 0.50f || trackersPerMinute < 5f -> PrivacyRating.POOR
            else -> PrivacyRating.TERRIBLE
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        cal.timeInMillis = timestamp
        return cal.get(Calendar.DAY_OF_YEAR) == today && cal.get(Calendar.YEAR) == year
    }

    private fun hourOfDay(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Clear all correlated data.
     */
    fun clear() {
        screenActivities.clear()
        dnsActivities.clear()
        _insights.value = emptyList()
    }
}

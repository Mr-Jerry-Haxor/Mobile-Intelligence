package com.mobileintelligence.app.engine.features

import com.mobileintelligence.app.engine.dns.DomainCategoryEngine
import com.mobileintelligence.app.engine.dns.DoHDetector
import com.mobileintelligence.app.engine.dns.TieredBlocklistManager
import kotlinx.coroutines.flow.*

/**
 * Tracker Simulation — "What If" mode.
 *
 * Shows the user what their network traffic would look like
 * WITHOUT the DNS firewall — how many trackers would have
 * contacted them, how much data would leak, and a privacy
 * cost estimate.
 *
 * Uses historical DNS query data to produce a simulation report.
 */
class TrackerSimulation(
    private val blocklistManager: TieredBlocklistManager,
    private val categoryEngine: DomainCategoryEngine,
    private val dohDetector: DoHDetector
) {
    /**
     * Run a simulation on a list of DNS queries.
     */
    fun simulate(queries: List<SimulationQuery>): SimulationReport {
        var totalQueries = queries.size
        var blockedCount = 0
        var trackerCount = 0
        var adCount = 0
        var malwareCount = 0
        var dohBypassCount = 0

        val appExposure = mutableMapOf<String, AppExposureDetail>()
        val trackerDomains = mutableSetOf<String>()
        val categoryBreakdown = mutableMapOf<String, Int>()

        queries.forEach { query ->
            val decision = blocklistManager.check(query.domain)
            val wouldBlock = decision.shouldBlock
            val category = categoryEngine.categorize(query.domain)
            val isDoh = dohDetector.isDoHEndpoint(query.domain)

            val catName = category.displayName
            categoryBreakdown[catName] = (categoryBreakdown[catName] ?: 0) + 1

            if (wouldBlock) blockedCount++
            if (isDoh) dohBypassCount++

            when (category) {
                DomainCategoryEngine.DomainCategory.ADS -> adCount++
                DomainCategoryEngine.DomainCategory.ANALYTICS,
                DomainCategoryEngine.DomainCategory.SOCIAL -> {
                    trackerCount++
                    trackerDomains.add(query.domain)
                }
                DomainCategoryEngine.DomainCategory.MALWARE -> malwareCount++
                else -> {}
            }

            // Per-app exposure
            val pkg = query.appPackage ?: "unknown"
            val detail = appExposure.getOrPut(pkg) {
                AppExposureDetail(pkg, 0, 0, 0, mutableSetOf())
            }
            appExposure[pkg] = detail.copy(
                totalQueries = detail.totalQueries + 1,
                blockedQueries = detail.blockedQueries + if (wouldBlock) 1 else 0,
                trackerQueries = detail.trackerQueries + if (trackerDomains.contains(query.domain)) 1 else 0,
                uniqueTrackers = (detail.uniqueTrackers as MutableSet).apply {
                    if (trackerDomains.contains(query.domain)) add(query.domain)
                }
            )
        }

        // Estimate data leak (rough: each tracker contact ≈ 500 bytes avg)
        val estimatedDataLeakBytes = (trackerCount + adCount) * 500L

        return SimulationReport(
            totalQueries = totalQueries,
            blockedCount = blockedCount,
            trackerCount = trackerCount,
            adCount = adCount,
            malwareCount = malwareCount,
            dohBypassCount = dohBypassCount,
            uniqueTrackerDomains = trackerDomains.size,
            blockRate = if (totalQueries > 0) blockedCount * 100f / totalQueries else 0f,
            estimatedDataLeakBytes = estimatedDataLeakBytes,
            privacyCostScore = calculatePrivacyCost(
                trackerCount, adCount, malwareCount,
                dohBypassCount, trackerDomains.size, totalQueries
            ),
            appExposure = appExposure.values
                .sortedByDescending { it.trackerQueries }
                .take(20),
            categoryBreakdown = categoryBreakdown
                .entries
                .sortedByDescending { it.value }
                .associate { it.key to it.value },
            topTrackerDomains = trackerDomains.take(20).toList()
        )
    }

    private fun calculatePrivacyCost(
        trackers: Int,
        ads: Int,
        malware: Int,
        doh: Int,
        uniqueTrackers: Int,
        total: Int
    ): Float {
        if (total == 0) return 0f
        // Weighted scoring: 0-100 where higher = worse privacy
        val trackerRatio = trackers.toFloat() / total * 30f
        val adRatio = ads.toFloat() / total * 20f
        val malwareWeight = (malware * 10f).coerceAtMost(20f)
        val dohWeight = (doh * 5f).coerceAtMost(15f)
        val diversityWeight = (uniqueTrackers * 0.5f).coerceAtMost(15f)
        return (trackerRatio + adRatio + malwareWeight + dohWeight + diversityWeight).coerceIn(0f, 100f)
    }

    // ── Data Classes ────────────────────────────────────────────

    data class SimulationQuery(
        val domain: String,
        val appPackage: String?,
        val timestamp: Long
    )

    data class SimulationReport(
        val totalQueries: Int,
        val blockedCount: Int,
        val trackerCount: Int,
        val adCount: Int,
        val malwareCount: Int,
        val dohBypassCount: Int,
        val uniqueTrackerDomains: Int,
        val blockRate: Float,
        val estimatedDataLeakBytes: Long,
        val privacyCostScore: Float,
        val appExposure: List<AppExposureDetail>,
        val categoryBreakdown: Map<String, Int>,
        val topTrackerDomains: List<String>
    ) {
        val protectionSummary: String
            get() {
                val blocked = blockRate.toInt()
                return when {
                    blocked >= 50 -> "Strong protection — $blocked% of harmful queries blocked"
                    blocked >= 25 -> "Moderate protection — $blocked% blocked, consider stricter tier"
                    blocked >= 10 -> "Light protection — only $blocked% blocked"
                    else -> "Minimal protection — most trackers are reaching your device"
                }
            }

        val estimatedDataLeakFormatted: String
            get() = when {
                estimatedDataLeakBytes < 1024 -> "${estimatedDataLeakBytes} B"
                estimatedDataLeakBytes < 1024 * 1024 -> "${"%.1f".format(estimatedDataLeakBytes / 1024f)} KB"
                else -> "${"%.1f".format(estimatedDataLeakBytes / (1024f * 1024f))} MB"
            }
    }

    data class AppExposureDetail(
        val packageName: String,
        val totalQueries: Int,
        val blockedQueries: Int,
        val trackerQueries: Int,
        val uniqueTrackers: Set<String>
    )
}

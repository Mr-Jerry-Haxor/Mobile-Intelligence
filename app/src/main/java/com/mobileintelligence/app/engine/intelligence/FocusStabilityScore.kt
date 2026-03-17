package com.mobileintelligence.app.engine.intelligence

import kotlin.math.min

/**
 * Focus Stability Score (FSS) — 0 to 100 scale.
 *
 * Measures the user's ability to maintain focused attention.
 * Higher score = better focus and more sustained attention spans.
 *
 * Scoring components (weighted):
 * - Average session length:     30% (longer sustained sessions = higher)
 * - App switching frequency:    25% (less switching = higher)
 * - Interruption recovery:      20% (quick return to same app = higher)
 * - Screen-free gaps:           15% (longer gaps between sessions = higher)
 * - Consistency:                10% (regular patterns = higher)
 */
class FocusStabilityScore {

    companion object {
        private const val WINDOW_SIZE = 150
        private const val OPTIMAL_SESSION_MS = 25 * 60_000L    // 25 min (Pomodoro-like)
        private const val GOOD_GAP_MS = 30 * 60_000L           // 30 min between uses
        private const val RAPID_SWITCH_THRESHOLD_MS = 10_000L  // <10s = rapid switching

        private const val WEIGHT_SESSION_LENGTH = 0.30f
        private const val WEIGHT_APP_SWITCHING = 0.25f
        private const val WEIGHT_INTERRUPTION_RECOVERY = 0.20f
        private const val WEIGHT_SCREEN_FREE_GAPS = 0.15f
        private const val WEIGHT_CONSISTENCY = 0.10f
    }

    data class FocusEvent(
        val type: FocusEventType,
        val timestamp: Long,
        val metadata: String? = null
    )

    enum class FocusEventType {
        SCREEN_ON, SCREEN_OFF, UNLOCK, APP_SWITCH
    }

    private val recentEvents = ArrayDeque<FocusEvent>(WINDOW_SIZE)

    // Track app switch patterns
    private val appSwitches = ArrayDeque<AppSwitchRecord>(WINDOW_SIZE)

    data class AppSwitchRecord(
        val fromPackage: String?,
        val toPackage: String,
        val timestamp: Long
    )

    // ── Event Recording ─────────────────────────────────────────

    fun recordScreenOn(timestamp: Long) {
        addEvent(FocusEvent(FocusEventType.SCREEN_ON, timestamp))
    }

    fun recordScreenOff(timestamp: Long) {
        addEvent(FocusEvent(FocusEventType.SCREEN_OFF, timestamp))
    }

    fun recordUnlock(timestamp: Long) {
        addEvent(FocusEvent(FocusEventType.UNLOCK, timestamp))
    }

    fun recordAppSwitch(fromPackage: String?, toPackage: String, timestamp: Long) {
        addEvent(FocusEvent(FocusEventType.APP_SWITCH, timestamp, toPackage))
        appSwitches.addLast(AppSwitchRecord(fromPackage, toPackage, timestamp))
        if (appSwitches.size > WINDOW_SIZE) appSwitches.removeFirst()
    }

    private fun addEvent(event: FocusEvent) {
        recentEvents.addLast(event)
        if (recentEvents.size > WINDOW_SIZE) recentEvents.removeFirst()
    }

    // ── Score Calculation ───────────────────────────────────────

    /**
     * Calculate Focus Stability Score (0-100, higher = better).
     */
    fun calculate(): Float {
        if (recentEvents.size < 3) return 75f // Neutral default

        val sessionLengthScore = calculateSessionLengthScore()
        val switchingScore = calculateSwitchingScore()
        val recoveryScore = calculateInterruptionRecoveryScore()
        val gapScore = calculateScreenFreeGapScore()
        val consistencyScore = calculateConsistencyScore()

        val raw = sessionLengthScore * WEIGHT_SESSION_LENGTH +
                switchingScore * WEIGHT_APP_SWITCHING +
                recoveryScore * WEIGHT_INTERRUPTION_RECOVERY +
                gapScore * WEIGHT_SCREEN_FREE_GAPS +
                consistencyScore * WEIGHT_CONSISTENCY

        return raw.coerceIn(0f, 100f)
    }

    /**
     * Average session length score.
     * Sessions near the 25-min "Pomodoro" mark get highest scores.
     */
    private fun calculateSessionLengthScore(): Float {
        val sessions = extractSessions()
        if (sessions.isEmpty()) return 50f

        val avgDuration = sessions.map { it.durationMs }.average()

        return when {
            avgDuration < 30_000 -> 15f                    // < 30s — very low focus
            avgDuration < 120_000 -> 30f                   // < 2 min
            avgDuration < 600_000 -> 50f                   // < 10 min
            avgDuration < OPTIMAL_SESSION_MS -> 75f        // < 25 min
            avgDuration < 3600_000 -> 90f                  // < 1hr — great focus
            avgDuration < 7200_000 -> 70f                  // > 1hr may be doom scroll
            else -> 40f                                     // > 2hr = likely doom scroll
        }
    }

    /**
     * App switching frequency score. Less switching = higher score.
     */
    private fun calculateSwitchingScore(): Float {
        val todaySwitches = appSwitches.filter { isToday(it.timestamp) }
        if (todaySwitches.isEmpty()) return 80f

        // Rapid switches (< 10s apart)
        var rapidCount = 0
        for (i in 1 until todaySwitches.size) {
            if (todaySwitches[i].timestamp - todaySwitches[i - 1].timestamp < RAPID_SWITCH_THRESHOLD_MS) {
                rapidCount++
            }
        }

        val rapidRatio = if (todaySwitches.size > 1) {
            rapidCount.toFloat() / (todaySwitches.size - 1)
        } else 0f

        // Low rapid switching ratio = high focus
        return (100f - rapidRatio * 100f).coerceIn(0f, 100f)
    }

    /**
     * Interruption recovery: after screen off, does user return to the same app?
     * High recovery = returning to same app (was genuinely interrupted).
     * Low recovery = always switching apps (can't focus).
     */
    private fun calculateInterruptionRecoveryScore(): Float {
        if (appSwitches.size < 4) return 50f

        // Look for patterns: App A → screen off → screen on → App A (recovered)
        // vs: App A → screen off → screen on → App B (didn't recover)
        val switchList = appSwitches.toList()
        var recoveries = 0
        var opportunities = 0

        for (i in 2 until switchList.size) {
            // Find screen-off gaps (same app before and after)
            val prev = switchList[i - 2]
            val curr = switchList[i]

            if (curr.timestamp - prev.timestamp > 30_000) { // Gap > 30s
                opportunities++
                if (curr.toPackage == prev.toPackage) {
                    recoveries++ // Returned to same app
                }
            }
        }

        if (opportunities == 0) return 60f
        return (recoveries.toFloat() / opportunities * 100f).coerceIn(0f, 100f)
    }

    /**
     * Screen-free gap duration. Longer gaps = better (phone not dominating attention).
     */
    private fun calculateScreenFreeGapScore(): Float {
        val onOffEvents = recentEvents.filter {
            it.type == FocusEventType.SCREEN_ON || it.type == FocusEventType.SCREEN_OFF
        }.sortedBy { it.timestamp }

        if (onOffEvents.size < 2) return 50f

        val gaps = mutableListOf<Long>()
        for (i in 1 until onOffEvents.size) {
            if (onOffEvents[i - 1].type == FocusEventType.SCREEN_OFF &&
                onOffEvents[i].type == FocusEventType.SCREEN_ON
            ) {
                gaps.add(onOffEvents[i].timestamp - onOffEvents[i - 1].timestamp)
            }
        }

        if (gaps.isEmpty()) return 30f
        val avgGap = gaps.average()

        return when {
            avgGap > GOOD_GAP_MS -> 90f           // 30+ min gaps — excellent
            avgGap > 15 * 60_000 -> 75f            // 15+ min
            avgGap > 5 * 60_000 -> 55f             // 5+ min
            avgGap > 60_000 -> 35f                  // 1+ min
            else -> 15f                              // < 1 min — can't put it down
        }
    }

    /**
     * Usage consistency. Regular, predictable patterns = higher focus discipline.
     */
    private fun calculateConsistencyScore(): Float {
        val todayEvents = recentEvents.filter {
            isToday(it.timestamp) && it.type == FocusEventType.UNLOCK
        }

        if (todayEvents.size < 3) return 60f

        // Calculate standard deviation of gaps between unlocks
        val gaps = todayEvents.zipWithNext { a, b -> b.timestamp - a.timestamp }
        if (gaps.isEmpty()) return 50f

        val mean = gaps.average()
        val variance = gaps.map { (it - mean) * (it - mean) }.average()
        val stdDev = Math.sqrt(variance)

        // Lower relative std dev (coefficient of variation) = more consistent
        val cv = if (mean > 0) stdDev / mean else 1.0

        return when {
            cv < 0.3 -> 90f    // Very consistent
            cv < 0.5 -> 70f    // Moderately consistent
            cv < 0.8 -> 50f    // Some regularity
            cv < 1.2 -> 35f    // Irregular
            else -> 15f         // Very erratic
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    data class SessionInfo(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    private fun extractSessions(): List<SessionInfo> {
        val events = recentEvents.filter {
            it.type == FocusEventType.SCREEN_ON || it.type == FocusEventType.SCREEN_OFF
        }.sortedBy { it.timestamp }

        val sessions = mutableListOf<SessionInfo>()
        var sessionStart: Long? = null

        for (event in events) {
            when (event.type) {
                FocusEventType.SCREEN_ON -> sessionStart = event.timestamp
                FocusEventType.SCREEN_OFF -> {
                    if (sessionStart != null) {
                        sessions.add(SessionInfo(sessionStart, event.timestamp))
                        sessionStart = null
                    }
                }
                else -> {}
            }
        }

        return sessions
    }

    private fun isToday(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val year = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.DAY_OF_YEAR) == today &&
                cal.get(java.util.Calendar.YEAR) == year
    }
}

package com.mobileintelligence.app.engine.intelligence

import com.mobileintelligence.app.engine.SessionType
import com.mobileintelligence.app.engine.WakeSource
import kotlin.math.min

/**
 * Digital Addiction Index (DAI) — 0 to 100 scale.
 *
 * Measures how addictive the user's phone usage patterns are.
 * Higher score = more concerning usage patterns.
 *
 * Scoring components (weighted):
 * - Unlock frequency:          25% (more unlocks = higher score)
 * - Session type distribution: 25% (doom scrolls/compulsive = high, normal = low)
 * - Total screen time:         20% (exceeding 4h/day = escalating)
 * - Night usage:               15% (usage between 11PM-5AM)
 * - Compulsive patterns:       15% (rapid re-checks, micro sessions)
 */
class DigitalAddictionIndex {

    companion object {
        // Scoring thresholds
        private const val HEALTHY_UNLOCK_COUNT = 40      // < 40 unlocks/day is normal
        private const val HIGH_UNLOCK_COUNT = 120         // > 120 is concerning
        private const val HEALTHY_SCREEN_TIME_MS = 4 * 3600_000L  // 4 hours
        private const val MAX_SCREEN_TIME_MS = 10 * 3600_000L     // 10+ hours saturates

        // Component weights
        private const val WEIGHT_UNLOCK_FREQ = 0.25f
        private const val WEIGHT_SESSION_TYPE = 0.25f
        private const val WEIGHT_SCREEN_TIME = 0.20f
        private const val WEIGHT_NIGHT_USAGE = 0.15f
        private const val WEIGHT_COMPULSIVE = 0.15f

        // Sliding window
        private const val WINDOW_SIZE = 200  // Recent events to consider
    }

    // Windowed metrics
    private val recentUnlocks = ArrayDeque<Long>(WINDOW_SIZE)          // timestamps
    private val recentSessions = ArrayDeque<SessionRecord>(WINDOW_SIZE)
    private val recentAppTransitions = ArrayDeque<AppRecord>(WINDOW_SIZE)

    private var totalScreenTimeToday = 0L
    private var nightUnlockCount = 0
    private var lastDayReset = dayOfYear()

    data class SessionRecord(
        val type: SessionType,
        val durationMs: Long,
        val timestamp: Long
    )

    data class AppRecord(
        val packageName: String,
        val timestamp: Long
    )

    // ── Event Recording ─────────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    fun recordScreenOn(timestamp: Long) {
        // tracked via session durations
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordScreenOff(timestamp: Long, durationMs: Long) {
        totalScreenTimeToday += durationMs
        checkDayReset()
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordUnlock(timestamp: Long, wakeSource: WakeSource) {
        recentUnlocks.addLast(timestamp)
        if (recentUnlocks.size > WINDOW_SIZE) recentUnlocks.removeFirst()

        val hour = hourOfDay(timestamp)
        if (hour >= 23 || hour < 5) {
            nightUnlockCount++
        }
        checkDayReset()
    }

    fun recordSessionType(type: SessionType, durationMs: Long) {
        recentSessions.addLast(SessionRecord(type, durationMs, System.currentTimeMillis()))
        if (recentSessions.size > WINDOW_SIZE) recentSessions.removeFirst()
    }

    fun recordAppTransition(packageName: String, timestamp: Long) {
        recentAppTransitions.addLast(AppRecord(packageName, timestamp))
        if (recentAppTransitions.size > WINDOW_SIZE) recentAppTransitions.removeFirst()
    }

    // ── Score Calculation ───────────────────────────────────────

    /**
     * Calculate the Digital Addiction Index (0-100).
     */
    fun calculate(): Float {
        val unlockScore = calculateUnlockScore()
        val sessionScore = calculateSessionTypeScore()
        val screenTimeScore = calculateScreenTimeScore()
        val nightScore = calculateNightUsageScore()
        val compulsiveScore = calculateCompulsiveScore()

        val raw = unlockScore * WEIGHT_UNLOCK_FREQ +
                sessionScore * WEIGHT_SESSION_TYPE +
                screenTimeScore * WEIGHT_SCREEN_TIME +
                nightScore * WEIGHT_NIGHT_USAGE +
                compulsiveScore * WEIGHT_COMPULSIVE

        return min(100f, raw)
    }

    /**
     * Unlock frequency score (0-100).
     */
    private fun calculateUnlockScore(): Float {
        val todayUnlocks = recentUnlocks.count { isToday(it) }
        return when {
            todayUnlocks <= HEALTHY_UNLOCK_COUNT -> todayUnlocks.toFloat() / HEALTHY_UNLOCK_COUNT * 30f
            todayUnlocks <= HIGH_UNLOCK_COUNT -> 30f + (todayUnlocks - HEALTHY_UNLOCK_COUNT).toFloat() /
                    (HIGH_UNLOCK_COUNT - HEALTHY_UNLOCK_COUNT) * 50f
            else -> 80f + min(20f, (todayUnlocks - HIGH_UNLOCK_COUNT).toFloat() / 50f * 20f)
        }
    }

    /**
     * Session type distribution score (0-100).
     * More doom scrolls and compulsive checks = higher score.
     */
    private fun calculateSessionTypeScore(): Float {
        if (recentSessions.isEmpty()) return 0f

        val todaySessions = recentSessions.filter { isToday(it.timestamp) }
        if (todaySessions.isEmpty()) return 0f

        var score = 0f
        for (session in todaySessions) {
            score += when (session.type) {
                SessionType.MICRO -> 2f
                SessionType.QUICK_CHECK -> 3f
                SessionType.SHORT -> 5f
                SessionType.NORMAL -> 3f          // Normal is healthy
                SessionType.EXTENDED -> 8f
                SessionType.LONG -> 15f
                SessionType.DOOM_SCROLL -> 25f     // Very concerning
                SessionType.COMPULSIVE_RECHECK -> 20f
            }
        }

        return min(100f, score)
    }

    /**
     * Screen time score (0-100).
     */
    private fun calculateScreenTimeScore(): Float {
        return when {
            totalScreenTimeToday <= HEALTHY_SCREEN_TIME_MS ->
                totalScreenTimeToday.toFloat() / HEALTHY_SCREEN_TIME_MS * 30f
            totalScreenTimeToday <= MAX_SCREEN_TIME_MS ->
                30f + (totalScreenTimeToday - HEALTHY_SCREEN_TIME_MS).toFloat() /
                        (MAX_SCREEN_TIME_MS - HEALTHY_SCREEN_TIME_MS) * 70f
            else -> 100f
        }
    }

    /**
     * Night usage score (0-100).
     */
    private fun calculateNightUsageScore(): Float {
        return when {
            nightUnlockCount == 0 -> 0f
            nightUnlockCount <= 2 -> 20f
            nightUnlockCount <= 5 -> 50f
            nightUnlockCount <= 10 -> 75f
            else -> 100f
        }
    }

    /**
     * Compulsive patterns score (0-100).
     */
    private fun calculateCompulsiveScore(): Float {
        val todaySessions = recentSessions.filter { isToday(it.timestamp) }
        if (todaySessions.isEmpty()) return 0f

        // Ratio of micro + compulsive sessions
        val compulsiveCount = todaySessions.count {
            it.type == SessionType.MICRO || it.type == SessionType.COMPULSIVE_RECHECK
        }
        val ratio = compulsiveCount.toFloat() / todaySessions.size

        // Rapid app switching count
        val rapidSwitches = countRapidAppSwitches()

        val ratioScore = ratio * 60f
        val switchScore = min(40f, rapidSwitches.toFloat() * 4f)

        return min(100f, ratioScore + switchScore)
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun countRapidAppSwitches(): Int {
        val todayTransitions = recentAppTransitions.filter { isToday(it.timestamp) }
        if (todayTransitions.size < 2) return 0

        var count = 0
        for (i in 1 until todayTransitions.size) {
            val gap = todayTransitions[i].timestamp - todayTransitions[i - 1].timestamp
            if (gap < 5_000) count++ // < 5s between switches
        }
        return count
    }

    private fun isToday(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.DAY_OF_YEAR) == today &&
                cal.get(java.util.Calendar.YEAR) == todayYear
    }

    private fun hourOfDay(timestamp: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    private fun dayOfYear(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun checkDayReset() {
        val today = dayOfYear()
        if (today != lastDayReset) {
            totalScreenTimeToday = 0
            nightUnlockCount = 0
            lastDayReset = today
        }
    }
}

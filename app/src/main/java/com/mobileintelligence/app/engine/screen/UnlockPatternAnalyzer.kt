package com.mobileintelligence.app.engine.screen

import com.mobileintelligence.app.engine.SessionType
import com.mobileintelligence.app.engine.WakeSource
import kotlin.math.abs

/**
 * Analyzes unlock patterns to detect compulsive behavior, night wakeups,
 * and habitual usage patterns.
 *
 * Maintains a sliding window of recent unlock events for real-time analysis.
 */
class UnlockPatternAnalyzer {

    companion object {
        private const val SLIDING_WINDOW_SIZE = 100
        private const val COMPULSIVE_WINDOW_MS = 5 * 60_000L   // 5 min
        private const val COMPULSIVE_THRESHOLD = 4              // 4 unlocks in 5 min
        private const val NIGHT_START_HOUR = 23
        private const val NIGHT_END_HOUR = 5
        private const val RAPID_SUCCESSION_MS = 30_000L         // 30s between unlocks
    }

    data class UnlockRecord(
        val timestamp: Long,
        val hourOfDay: Int,
        val wakeSource: WakeSource,
        val sessionType: SessionType?,
        val sessionDurationMs: Long = 0
    )

    private val recentUnlocks = ArrayDeque<UnlockRecord>(SLIDING_WINDOW_SIZE)

    fun recordUnlock(record: UnlockRecord) {
        recentUnlocks.addLast(record)
        if (recentUnlocks.size > SLIDING_WINDOW_SIZE) {
            recentUnlocks.removeFirst()
        }
    }

    // ── Pattern Detection ────────────────────────────────────────

    /**
     * Detect compulsive unlock loop: [COMPULSIVE_THRESHOLD]+ unlocks in [COMPULSIVE_WINDOW_MS].
     */
    fun isCompulsiveLoop(): Boolean {
        if (recentUnlocks.size < COMPULSIVE_THRESHOLD) return false
        val now = System.currentTimeMillis()
        val windowStart = now - COMPULSIVE_WINDOW_MS
        return recentUnlocks.count { it.timestamp >= windowStart } >= COMPULSIVE_THRESHOLD
    }

    /**
     * Night wakeup detection: unlocks between 11 PM and 5 AM.
     */
    fun detectNightWakeups(windowHours: Int = 24): List<UnlockRecord> {
        val cutoff = System.currentTimeMillis() - (windowHours * 3600_000L)
        return recentUnlocks.filter { record ->
            record.timestamp >= cutoff && isNightHour(record.hourOfDay)
        }
    }

    /**
     * Get the most common unlock hour based on recent data.
     */
    fun peakUnlockHour(): Int? {
        if (recentUnlocks.isEmpty()) return null
        return recentUnlocks
            .groupingBy { it.hourOfDay }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    /**
     * Calculate average time between unlocks (indicates pickup frequency).
     */
    fun averageTimeBetweenUnlocksMs(): Long? {
        if (recentUnlocks.size < 2) return null
        val sorted = recentUnlocks.sortedBy { it.timestamp }
        val gaps = sorted.zipWithNext { a, b -> b.timestamp - a.timestamp }
        return gaps.average().toLong()
    }

    /**
     * Micro session ratio: what % of recent sessions are micro (<10s).
     */
    fun microSessionRatio(): Float {
        val withType = recentUnlocks.filter { it.sessionType != null }
        if (withType.isEmpty()) return 0f
        val micros = withType.count {
            it.sessionType == SessionType.MICRO || it.sessionType == SessionType.COMPULSIVE_RECHECK
        }
        return micros.toFloat() / withType.size
    }

    /**
     * Doom scroll ratio: what % of sessions are doom scrolls.
     */
    fun doomScrollRatio(): Float {
        val withType = recentUnlocks.filter { it.sessionType != null }
        if (withType.isEmpty()) return 0f
        return withType.count { it.sessionType == SessionType.DOOM_SCROLL }.toFloat() / withType.size
    }

    /**
     * Detect rapid succession unlocks (unlock → lock → unlock within 30s).
     */
    fun rapidSuccessionCount(): Int {
        if (recentUnlocks.size < 2) return 0
        val sorted = recentUnlocks.sortedBy { it.timestamp }
        var count = 0
        for (i in 1 until sorted.size) {
            val gap = sorted[i].timestamp - sorted[i - 1].timestamp
            if (gap < RAPID_SUCCESSION_MS) count++
        }
        return count
    }

    /**
     * Generate a pattern summary for the Intelligence Engine.
     */
    fun patternSummary(): PatternSummary {
        return PatternSummary(
            totalUnlocks = recentUnlocks.size,
            compulsiveLoop = isCompulsiveLoop(),
            nightWakeups = detectNightWakeups().size,
            peakHour = peakUnlockHour(),
            avgGapMs = averageTimeBetweenUnlocksMs(),
            microRatio = microSessionRatio(),
            doomScrollRatio = doomScrollRatio(),
            rapidSuccessions = rapidSuccessionCount()
        )
    }

    private fun isNightHour(hour: Int): Boolean =
        hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR

    data class PatternSummary(
        val totalUnlocks: Int,
        val compulsiveLoop: Boolean,
        val nightWakeups: Int,
        val peakHour: Int?,
        val avgGapMs: Long?,
        val microRatio: Float,
        val doomScrollRatio: Float,
        val rapidSuccessions: Int
    )
}

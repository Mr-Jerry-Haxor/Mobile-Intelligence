package com.mobileintelligence.app.engine.intelligence

import com.mobileintelligence.app.engine.PatternSeverity
import com.mobileintelligence.app.engine.SessionType

/**
 * Pattern Detector — identifies concerning behavioral patterns
 * from accumulated session data and event streams.
 *
 * Patterns detected:
 * - Doom scrolling (extended single-app sessions)
 * - Compulsive checking (rapid unlock cycles)
 * - Night disruption (usage in sleep hours)
 * - App addiction cycles (same app repeatedly)
 * - Rapid app switching (can't settle on one task)
 * - Extended continuous use (no breaks)
 * - Increasing usage trend (week-over-week growth)
 */
class PatternDetector {

    companion object {
        private const val WINDOW_SIZE = 200
        private const val COMPULSIVE_THRESHOLD = 4           // 4+ micro sessions in 5 min
        private const val COMPULSIVE_WINDOW_MS = 5 * 60_000L
        private const val NIGHT_START_HOUR = 23
        private const val NIGHT_END_HOUR = 5
        private const val DOOM_SCROLL_THRESHOLD_MS = 90 * 60_000L  // 90+ min continuous
    }

    data class SessionRecord(
        val type: SessionType,
        val durationMs: Long,
        val timestamp: Long
    )

    private val recentSessions = ArrayDeque<SessionRecord>(WINDOW_SIZE)
    private val detectedPatternIds = mutableMapOf<String, Long>() // id → detection timestamp
    private val PATTERN_ID_EXPIRY_MS = 24 * 3600_000L  // Prune after 24h

    fun recordSession(type: SessionType, durationMs: Long, timestamp: Long) {
        recentSessions.addLast(SessionRecord(type, durationMs, timestamp))
        if (recentSessions.size > WINDOW_SIZE) recentSessions.removeFirst()
    }

    /**
     * Run all pattern detectors and return findings.
     */
    fun detectAll(): List<IntelligenceEngine.DetectedPattern> {
        // Prune old pattern IDs to prevent unbounded growth
        val now = System.currentTimeMillis()
        detectedPatternIds.entries.removeAll { (_, ts) -> now - ts > PATTERN_ID_EXPIRY_MS }

        val patterns = mutableListOf<IntelligenceEngine.DetectedPattern>()

        detectDoomScrolling()?.let { patterns.add(it) }
        detectCompulsiveChecking()?.let { patterns.add(it) }
        detectNightDisruption()?.let { patterns.add(it) }
        detectExtendedContinuousUse()?.let { patterns.add(it) }
        detectRapidAppSwitching()?.let { patterns.add(it) }

        return patterns
    }

    // ── Individual Detectors ────────────────────────────────────

    private fun detectDoomScrolling(): IntelligenceEngine.DetectedPattern? {
        val recent = recentSessions.lastOrNull() ?: return null

        if (recent.type == SessionType.DOOM_SCROLL ||
            (recent.durationMs > DOOM_SCROLL_THRESHOLD_MS && recent.type == SessionType.LONG)
        ) {
            val id = "doom_scroll_${recent.timestamp / 3600_000}"
            if (detectedPatternIds.containsKey(id)) return null
            detectedPatternIds[id] = System.currentTimeMillis()

            val minutes = recent.durationMs / 60_000
            return IntelligenceEngine.DetectedPattern(
                type = IntelligenceEngine.PatternType.DOOM_SCROLLING,
                description = "You've been scrolling for $minutes minutes non-stop. Consider taking a break.",
                severity = if (minutes > 120) PatternSeverity.CRITICAL else PatternSeverity.ALERT,
                isNew = true
            )
        }
        return null
    }

    private fun detectCompulsiveChecking(): IntelligenceEngine.DetectedPattern? {
        val now = System.currentTimeMillis()
        val windowStart = now - COMPULSIVE_WINDOW_MS

        val recentMicro = recentSessions.count { session ->
            session.timestamp > windowStart &&
                    (session.type == SessionType.MICRO || session.type == SessionType.COMPULSIVE_RECHECK)
        }

        if (recentMicro >= COMPULSIVE_THRESHOLD) {
            val id = "compulsive_${now / (5 * 60_000)}" // Dedup per 5-min window
            if (detectedPatternIds.containsKey(id)) return null
            detectedPatternIds[id] = System.currentTimeMillis()

            return IntelligenceEngine.DetectedPattern(
                type = IntelligenceEngine.PatternType.COMPULSIVE_CHECKING,
                description = "You've checked your phone $recentMicro times in the last 5 minutes. " +
                        "Try putting it in another room.",
                severity = PatternSeverity.WARNING,
                isNew = true
            )
        }
        return null
    }

    private fun detectNightDisruption(): IntelligenceEngine.DetectedPattern? {
        val now = System.currentTimeMillis()
        val hour = hourOfDay(now)

        if (hour < NIGHT_START_HOUR && hour >= NIGHT_END_HOUR) return null

        // Check if there are sessions in the current night window
        val nightSessions = recentSessions.count { session ->
            val sessionHour = hourOfDay(session.timestamp)
            (sessionHour >= NIGHT_START_HOUR || sessionHour < NIGHT_END_HOUR) &&
                    isToday(session.timestamp)
        }

        if (nightSessions >= 2) {
            val id = "night_${dayOfYear()}"
            if (detectedPatternIds.containsKey(id)) return null
            detectedPatternIds[id] = System.currentTimeMillis()

            return IntelligenceEngine.DetectedPattern(
                type = IntelligenceEngine.PatternType.NIGHT_DISRUPTION,
                description = "You've used your phone $nightSessions times during sleep hours. " +
                        "This can disrupt your sleep cycle.",
                severity = PatternSeverity.ALERT,
                isNew = true
            )
        }
        return null
    }

    private fun detectExtendedContinuousUse(): IntelligenceEngine.DetectedPattern? {
        // Check for continuous usage without a 15+ min break
        val todaySessions = recentSessions.filter { isToday(it.timestamp) }
            .sortedBy { it.timestamp }

        if (todaySessions.size < 3) return null

        var continuousMs = 0L
        for (i in 1 until todaySessions.size) {
            val gap = todaySessions[i].timestamp -
                    (todaySessions[i - 1].timestamp + todaySessions[i - 1].durationMs)

            if (gap < 15 * 60_000) {
                // Less than 15 min break — continuous
                continuousMs += todaySessions[i].durationMs + gap
            } else {
                continuousMs = todaySessions[i].durationMs
            }

            if (continuousMs > 2 * 3600_000) { // 2+ hours continuous
                val id = "extended_${todaySessions[i].timestamp / 3600_000}"
                if (detectedPatternIds.containsKey(id)) return null
                detectedPatternIds[id] = System.currentTimeMillis()

                return IntelligenceEngine.DetectedPattern(
                    type = IntelligenceEngine.PatternType.EXTENDED_CONTINUOUS_USE,
                    description = "You've been using your phone continuously for over " +
                            "${continuousMs / 3600_000} hours. Take a 20-20-20 break: " +
                            "look at something 20 feet away for 20 seconds.",
                    severity = PatternSeverity.WARNING,
                    isNew = true
                )
            }
        }
        return null
    }

    private fun detectRapidAppSwitching(): IntelligenceEngine.DetectedPattern? {
        val recent5min = recentSessions.filter {
            System.currentTimeMillis() - it.timestamp < 5 * 60_000
        }

        if (recent5min.size < 8) return null // Need lots of sessions = lots of switching

        val microCount = recent5min.count {
            it.type == SessionType.MICRO || it.type == SessionType.QUICK_CHECK
        }
        val ratio = microCount.toFloat() / recent5min.size

        if (ratio > 0.6f && recent5min.size >= 10) {
            val id = "rapid_switch_${System.currentTimeMillis() / (5 * 60_000)}"
            if (detectedPatternIds.containsKey(id)) return null
            detectedPatternIds[id] = System.currentTimeMillis()

            return IntelligenceEngine.DetectedPattern(
                type = IntelligenceEngine.PatternType.RAPID_APP_SWITCHING,
                description = "You're switching between apps very quickly. " +
                        "This scattered attention pattern indicates difficulty focusing.",
                severity = PatternSeverity.INFO,
                isNew = true
            )
        }
        return null
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun hourOfDay(timestamp: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
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

    /**
     * Clear pattern history (for testing or daily reset).
     */
    fun clearHistory() {
        recentSessions.clear()
        detectedPatternIds.clear()
    }
}

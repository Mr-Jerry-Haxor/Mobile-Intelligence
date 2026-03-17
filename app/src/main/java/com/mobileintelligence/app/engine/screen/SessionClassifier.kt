package com.mobileintelligence.app.engine.screen

import com.mobileintelligence.app.engine.SessionType

/**
 * Classifies screen sessions into behavioral categories based on
 * duration, app switching frequency, and temporal proximity to
 * previous sessions.
 */
class SessionClassifier {

    companion object {
        private const val MICRO_MS = 10_000L            // < 10s
        private const val QUICK_CHECK_MS = 30_000L      // 10-30s
        private const val SHORT_MS = 120_000L           // 30s - 2m
        private const val NORMAL_MS = 900_000L          // 2 - 15m
        private const val EXTENDED_MS = 2_700_000L      // 15 - 45m
        private const val LONG_MS = 7_200_000L          // 45m - 2h
        private const val COMPULSIVE_WINDOW_MS = 60_000L // Recheck within 1 min
    }

    /**
     * Classify a completed session.
     *
     * @param durationMs   How long the screen was on.
     * @param appSwitchCount Number of app transitions during session.
     * @param timeSinceLastLock Time between the previous lock event and this
     *   session's unlock. If null, we can't determine compulsive pattern.
     */
    @Suppress("UNUSED_PARAMETER")
    fun classify(
        durationMs: Long,
        appSwitchCount: Int = 0,
        timeSinceLastLock: Long? = null
    ): SessionType {
        // 1. Compulsive recheck — very short interval between lock/unlock
        if (timeSinceLastLock != null && timeSinceLastLock < COMPULSIVE_WINDOW_MS && durationMs < QUICK_CHECK_MS) {
            return SessionType.COMPULSIVE_RECHECK
        }

        // 2. Duration-based classification
        return when {
            durationMs < MICRO_MS -> SessionType.MICRO
            durationMs < QUICK_CHECK_MS -> SessionType.QUICK_CHECK
            durationMs < SHORT_MS -> SessionType.SHORT
            durationMs < NORMAL_MS -> SessionType.NORMAL
            durationMs < EXTENDED_MS -> SessionType.EXTENDED
            durationMs < LONG_MS -> SessionType.LONG
            else -> SessionType.DOOM_SCROLL
        }
    }

    /**
     * Detect compulsive unlock loop: 3+ micro/quick sessions within 5 minutes.
     */
    fun detectCompulsiveLoop(recentSessions: List<Pair<Long, Long>>): Boolean {
        if (recentSessions.size < 3) return false
        val windowMs = 5 * 60_000L
        val now = System.currentTimeMillis()
        val recent = recentSessions.filter { (timestamp, _) ->
            now - timestamp < windowMs
        }
        val quickSessions = recent.count { (_, duration) ->
            duration < QUICK_CHECK_MS
        }
        return quickSessions >= 3
    }

    /**
     * Detect doom scroll pattern: session > 2 hours with minimal app switching.
     */
    fun isDoomScroll(durationMs: Long, appSwitchCount: Int): Boolean {
        return durationMs > LONG_MS && appSwitchCount <= 2
    }

    /**
     * Detect rapid app switching: > 10 app switches in 2 minutes.
     */
    fun isRapidAppSwitching(
        transitions: List<Long>, // timestamps of transitions
        windowMs: Long = 120_000L,
        threshold: Int = 10
    ): Boolean {
        if (transitions.size < threshold) return false
        val now = System.currentTimeMillis()
        return transitions.count { now - it < windowMs } >= threshold
    }

    /**
     * Get a human-readable label for a session type.
     */
    fun label(type: SessionType): String = when (type) {
        SessionType.MICRO -> "Micro Session (<10s)"
        SessionType.QUICK_CHECK -> "Quick Check (10-30s)"
        SessionType.SHORT -> "Short Session (30s-2m)"
        SessionType.NORMAL -> "Normal Session (2-15m)"
        SessionType.EXTENDED -> "Extended Session (15-45m)"
        SessionType.LONG -> "Long Session (45m-2h)"
        SessionType.DOOM_SCROLL -> "Doom Scroll (>2h)"
        SessionType.COMPULSIVE_RECHECK -> "Compulsive Recheck"
    }
}

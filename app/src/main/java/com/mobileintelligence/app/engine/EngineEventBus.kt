package com.mobileintelligence.app.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Flow-based event bus for inter-engine communication.
 * Engines publish typed events; any engine can subscribe to event streams.
 *
 * Design:
 * - No tight coupling — engines only know about [EngineEvent] subtypes
 * - Buffered replay for late subscribers (extraBufferCapacity = 256)
 * - Back-pressure strategy: DROP_OLDEST to never block publishers
 */
class EngineEventBus {

    private val _events = MutableSharedFlow<EngineEvent>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Publish an event. Non-suspending due to buffer capacity. */
    fun publish(event: EngineEvent): Boolean =
        _events.tryEmit(event)

    /** Suspending publish — waits if buffer is full (shouldn't happen with DROP strategy). */
    suspend fun emit(event: EngineEvent) =
        _events.emit(event)
}

// ═══════════════════════════════════════════════════════════════════
// Engine Events — Each engine defines its own event subtypes
// ═══════════════════════════════════════════════════════════════════

sealed interface EngineEvent {
    val timestamp: Long
    val source: String
}

// ─── Screen State Engine Events ─────────────────────────────────

data class ScreenOnEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "ScreenEngine"
) : EngineEvent

data class ScreenOffEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "ScreenEngine",
    val sessionDurationMs: Long = 0
) : EngineEvent

data class UserUnlockedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "ScreenEngine",
    val timeSinceLastUnlockMs: Long? = null,
    val wakeSource: WakeSource = WakeSource.UNKNOWN
) : EngineEvent

data class AppTransitionEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "ScreenEngine",
    val fromPackage: String?,
    val toPackage: String,
    val toAppName: String,
    val transitionDurationMs: Long = 0
) : EngineEvent

data class SessionClassifiedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "ScreenEngine",
    val sessionType: SessionType,
    val durationMs: Long,
    val appCount: Int = 0
) : EngineEvent

enum class WakeSource {
    NOTIFICATION,
    MANUAL_UNLOCK,
    BIOMETRIC,
    LIFT_TO_WAKE,
    DOUBLE_TAP,
    POWER_BUTTON,
    ALARM,
    UNKNOWN
}

enum class SessionType {
    MICRO,              // < 10 seconds
    QUICK_CHECK,        // 10-30 seconds
    SHORT,              // 30s - 2 min
    NORMAL,             // 2 - 15 min
    EXTENDED,           // 15 - 45 min
    LONG,               // 45 min - 2 hr
    DOOM_SCROLL,        // > 2 hr continuous
    COMPULSIVE_RECHECK  // Rapid unlock within 60s of previous lock
}

// ─── DNS Firewall Engine Events ─────────────────────────────────

data class DnsQueryProcessedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "DnsEngine",
    val domain: String,
    val blocked: Boolean,
    val cached: Boolean,
    val responseTimeMs: Long,
    val appPackage: String?,
    val category: String? = null,
    val blockReason: String? = null
) : EngineEvent

data class DnsVpnStateChangedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "DnsEngine",
    val isRunning: Boolean
) : EngineEvent

data class DoHAttemptDetectedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "DnsEngine",
    val domain: String,
    val appPackage: String?,
    val wasBlocked: Boolean
) : EngineEvent

// ─── Intelligence Engine Events ─────────────────────────────────

data class ScoresUpdatedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "IntelligenceEngine",
    val addictionIndex: Float,
    val focusScore: Float,
    val privacyExposure: Float
) : EngineEvent

data class PatternDetectedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "IntelligenceEngine",
    val patternType: String,
    val description: String,
    val severity: PatternSeverity
) : EngineEvent

data class CorrelationInsightEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "IntelligenceEngine",
    val insight: String,
    val category: String,
    val appPackage: String? = null
) : EngineEvent

enum class PatternSeverity { INFO, WARNING, ALERT, CRITICAL }

// ─── Storage Engine Events ──────────────────────────────────────

data class DataRollupCompletedEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "StorageEngine",
    val level: Int, // 1=raw→hourly, 2=hourly→daily
    val recordsProcessed: Int,
    val recordsPurged: Int,
    val bytesFreed: Long = 0
) : EngineEvent

data class StorageHealthEvent(
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "StorageEngine",
    val totalDbSizeBytes: Long,
    val oldestRecordDate: String?,
    val retentionHealthy: Boolean
) : EngineEvent

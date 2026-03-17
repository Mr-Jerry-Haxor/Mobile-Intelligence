package com.mobileintelligence.app.engine.intelligence

import android.content.Context
import android.util.Log
import com.mobileintelligence.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Intelligence Engine — Engine #4
 *
 * The "brain" of the system. Consumes events from Screen & DNS engines,
 * computes derived intelligence metrics, detects behavioral patterns,
 * and generates actionable insights.
 *
 * Three core scores (0-100 scale):
 * - **Digital Addiction Index (DAI)**: Higher = more addictive usage patterns
 * - **Focus Stability Score (FSS)**: Higher = better sustained focus
 * - **Privacy Exposure Score (PES)**: Higher = more privacy risk
 *
 * Pattern detection:
 * - Doom scrolling sessions
 * - Compulsive phone checking
 * - Night-time pattern disruption
 * - Tracker burst detection
 * - App addiction cycles
 */
class IntelligenceEngine(private val context: Context) : Engine {

    companion object {
        private const val TAG = "IntelligenceEngine"
        private const val SCORE_UPDATE_INTERVAL_MS = 60_000L  // Recalculate every 60s
    }

    override val name: String = "Intelligence"

    private val _state = MutableStateFlow(EngineState.Created)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private lateinit var eventBus: EngineEventBus

    // Sub-components
    lateinit var addictionIndex: DigitalAddictionIndex
        private set
    lateinit var focusScore: FocusStabilityScore
        private set
    lateinit var privacyScore: PrivacyExposureScore
        private set
    lateinit var patternDetector: PatternDetector
        private set
    lateinit var correlationEngine: CorrelationEngine
        private set

    // Current scores (exposed for UI)
    private val _currentScores = MutableStateFlow(IntelligenceScores())
    val currentScores: StateFlow<IntelligenceScores> = _currentScores.asStateFlow()

    // Detected patterns
    private val _detectedPatterns = MutableStateFlow<List<DetectedPattern>>(emptyList())
    val detectedPatterns: StateFlow<List<DetectedPattern>> = _detectedPatterns.asStateFlow()

    // Metrics
    private val startTime = AtomicLong(0)
    private val eventsProcessed = AtomicLong(0)
    private var lastError: String? = null
    private var lastErrorTimestamp: Long? = null

    private var eventCollectorJob: Job? = null
    private var scoreUpdateJob: Job? = null

    override suspend fun initialize(scope: CoroutineScope, eventBus: EngineEventBus) {
        _state.value = EngineState.Initializing
        this.scope = scope
        this.eventBus = eventBus

        try {
            addictionIndex = DigitalAddictionIndex()
            focusScore = FocusStabilityScore()
            privacyScore = PrivacyExposureScore()
            patternDetector = PatternDetector()
            correlationEngine = CorrelationEngine()

            Log.d(TAG, "Initialized: all scoring engines + correlation engine ready")
            _state.value = EngineState.Stopped
        } catch (e: Exception) {
            lastError = e.message
            lastErrorTimestamp = System.currentTimeMillis()
            _state.value = EngineState.Error
            throw e
        }
    }

    override suspend fun start() {
        if (_state.value == EngineState.Running) return

        _state.value = EngineState.Running
        startTime.set(System.currentTimeMillis())
        Log.d(TAG, "Engine started")

        // Listen to events from other engines
        eventCollectorJob = scope.launch {
            eventBus.events.collect { event ->
                processEvent(event)
            }
        }

        // Calculate scores immediately, then periodically
        scoreUpdateJob = scope.launch {
            // Immediate first calculation so UI gets data right away
            try { updateScores() } catch (_: Exception) {}

            while (isActive) {
                delay(SCORE_UPDATE_INTERVAL_MS)
                try { updateScores() } catch (_: Exception) {}
            }
        }
    }

    override suspend fun stop() {
        if (_state.value == EngineState.Stopped) return
        _state.value = EngineState.Stopping

        eventCollectorJob?.cancel()
        scoreUpdateJob?.cancel()

        _state.value = EngineState.Stopped
        Log.d(TAG, "Engine stopped")
    }

    override suspend fun recover() {
        Log.w(TAG, "Recovering intelligence engine...")
        stop()
        delay(1_000)
        start()
    }

    override fun diagnostics(): EngineDiagnostics {
        val uptime = if (startTime.get() > 0) System.currentTimeMillis() - startTime.get() else 0
        val scores = _currentScores.value
        return EngineDiagnostics(
            engineName = name,
            state = _state.value,
            uptimeMs = uptime,
            lastError = lastError,
            lastErrorTimestamp = lastErrorTimestamp,
            eventsProcessed = eventsProcessed.get(),
            customMetrics = mapOf(
                "addictionIndex" to scores.addictionIndex,
                "focusScore" to scores.focusScore,
                "privacyExposure" to scores.privacyExposure,
                "patternsDetected" to _detectedPatterns.value.size
            )
        )
    }

    // ── Event Processing ────────────────────────────────────────

    private fun processEvent(event: EngineEvent) {
        eventsProcessed.incrementAndGet()

        when (event) {
            is ScreenOnEvent -> {
                addictionIndex.recordScreenOn(event.timestamp)
                focusScore.recordScreenOn(event.timestamp)
            }
            is ScreenOffEvent -> {
                addictionIndex.recordScreenOff(event.timestamp, event.sessionDurationMs)
                focusScore.recordScreenOff(event.timestamp)
            }
            is UserUnlockedEvent -> {
                addictionIndex.recordUnlock(event.timestamp, event.wakeSource)
                focusScore.recordUnlock(event.timestamp)
            }
            is AppTransitionEvent -> {
                addictionIndex.recordAppTransition(event.toPackage, event.timestamp)
                focusScore.recordAppSwitch(event.fromPackage, event.toPackage, event.timestamp)
                // Feed correlation engine with app transitions
                correlationEngine.recordAppTransition(
                    event.toPackage,
                    event.toAppName,
                    event.timestamp
                )
            }
            is SessionClassifiedEvent -> {
                addictionIndex.recordSessionType(event.sessionType, event.durationMs)
                patternDetector.recordSession(event.sessionType, event.durationMs, event.timestamp)
            }
            is DnsQueryProcessedEvent -> {
                privacyScore.recordDnsQuery(event.domain, event.blocked, event.appPackage, event.category)
                // Feed correlation engine with DNS activity
                correlationEngine.recordDnsActivity(CorrelationEngine.DnsActivity(
                    domain = event.domain,
                    blocked = event.blocked,
                    appPackage = event.appPackage,
                    category = event.category,
                    timestamp = event.timestamp
                ))
            }
            is DoHAttemptDetectedEvent -> {
                privacyScore.recordDoHAttempt(event.domain, event.appPackage)
            }
            else -> { /* Other events not processed by intelligence engine */ }
        }
    }

    private fun updateScores() {
        val dai = addictionIndex.calculate()
        val fss = focusScore.calculate()
        val pes = privacyScore.calculate()

        val scores = IntelligenceScores(
            addictionIndex = dai,
            focusScore = fss,
            privacyExposure = pes,
            timestamp = System.currentTimeMillis()
        )
        _currentScores.value = scores

        // Detect patterns
        val patterns = patternDetector.detectAll()
        _detectedPatterns.value = patterns

        // Publish scores update to event bus
        eventBus.publish(ScoresUpdatedEvent(
            addictionIndex = dai,
            focusScore = fss,
            privacyExposure = pes
        ))

        // Publish detected patterns
        for (pattern in patterns) {
            if (pattern.isNew) {
                eventBus.publish(PatternDetectedEvent(
                    patternType = pattern.type.name,
                    description = pattern.description,
                    severity = pattern.severity
                ))
            }
        }
    }

    // ── Data Classes ────────────────────────────────────────────

    data class IntelligenceScores(
        val addictionIndex: Float = 0f,    // 0-100, higher = more addictive
        val focusScore: Float = 100f,      // 0-100, higher = better focus
        val privacyExposure: Float = 0f,   // 0-100, higher = more exposure
        val timestamp: Long = 0
    ) {
        val overallHealth: Float
            get() = ((100f - addictionIndex) + focusScore + (100f - privacyExposure)) / 3f
    }

    data class DetectedPattern(
        val type: PatternType,
        val description: String,
        val severity: PatternSeverity,
        val detectedAt: Long = System.currentTimeMillis(),
        val isNew: Boolean = true
    )

    enum class PatternType {
        DOOM_SCROLLING,
        COMPULSIVE_CHECKING,
        NIGHT_DISRUPTION,
        APP_ADDICTION_CYCLE,
        TRACKER_BURST,
        RAPID_APP_SWITCHING,
        EXTENDED_CONTINUOUS_USE,
        INCREASING_TREND,
        PRIVACY_DEGRADATION,
        FOCUS_BREAKDOWN
    }
}

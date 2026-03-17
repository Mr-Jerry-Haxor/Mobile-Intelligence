package com.mobileintelligence.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.engine.EngineManager
import com.mobileintelligence.app.engine.EngineState
import com.mobileintelligence.app.engine.intelligence.CorrelationEngine
import com.mobileintelligence.app.engine.intelligence.IntelligenceEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Intelligence Console — the main "brain" dashboard.
 *
 * Exposes:
 * - Three core scores (DAI, FSS, PES) with overall health
 * - Active detected patterns and alerts
 * - Correlation insights (Screen + DNS)
 * - Engine health status
 * - Loading state for when engines haven't started yet
 */
class IntelligenceConsoleViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "IntConsoleVM"
        private const val ENGINE_RETRY_DELAY_MS = 2_000L
        private const val MAX_ENGINE_RETRIES = 30  // up to 60 seconds of retrying
    }

    // ── Loading State ───────────────────────────────────────────

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _engineConnected = MutableStateFlow(false)
    val engineConnected: StateFlow<Boolean> = _engineConnected.asStateFlow()

    // ── Scores ──────────────────────────────────────────────────

    private val _addictionIndex = MutableStateFlow(0f)
    val addictionIndex: StateFlow<Float> = _addictionIndex.asStateFlow()

    private val _focusScore = MutableStateFlow(75f)
    val focusScore: StateFlow<Float> = _focusScore.asStateFlow()

    private val _privacyExposure = MutableStateFlow(0f)
    val privacyExposure: StateFlow<Float> = _privacyExposure.asStateFlow()

    private val _overallHealth = MutableStateFlow(75f)
    val overallHealth: StateFlow<Float> = _overallHealth.asStateFlow()

    // ── Patterns ────────────────────────────────────────────────

    private val _detectedPatterns = MutableStateFlow<List<PatternAlert>>(emptyList())
    val detectedPatterns: StateFlow<List<PatternAlert>> = _detectedPatterns.asStateFlow()

    // ── Insights ────────────────────────────────────────────────

    private val _correlationInsights = MutableStateFlow<List<InsightItem>>(emptyList())
    val correlationInsights: StateFlow<List<InsightItem>> = _correlationInsights.asStateFlow()

    // ── Engine Health ───────────────────────────────────────────

    private val _engineStatuses = MutableStateFlow<List<EngineStatusItem>>(emptyList())
    val engineStatuses: StateFlow<List<EngineStatusItem>> = _engineStatuses.asStateFlow()

    // ── Score Trends ────────────────────────────────────────────

    private val _addictionTrend = MutableStateFlow<List<Float>>(emptyList())
    val addictionTrend: StateFlow<List<Float>> = _addictionTrend.asStateFlow()

    private val _focusTrend = MutableStateFlow<List<Float>>(emptyList())
    val focusTrend: StateFlow<List<Float>> = _focusTrend.asStateFlow()

    private val manager = EngineManager.getInstance(application)
    private var scoresJob: Job? = null
    private var patternsJob: Job? = null
    private var insightsJob: Job? = null

    init {
        observeEngineScoresWithRetry()
        observeEngineHealth()
    }

    /**
     * Retries finding the IntelligenceEngine until it's available.
     * Engines start asynchronously, so they may not be ready when the ViewModel initializes.
     */
    private fun observeEngineScoresWithRetry() {
        viewModelScope.launch {
            var retries = 0
            while (retries < MAX_ENGINE_RETRIES) {
                val intelligenceEngine = manager.engines
                    .map { it.engine }
                    .filterIsInstance<IntelligenceEngine>()
                    .firstOrNull()

                if (intelligenceEngine != null &&
                    intelligenceEngine.state.value == EngineState.Running) {
                    Log.i(TAG, "IntelligenceEngine found and running after $retries retries")
                    _engineConnected.value = true
                    _isLoading.value = false
                    subscribeToEngine(intelligenceEngine)
                    return@launch
                }

                retries++
                Log.d(TAG, "IntelligenceEngine not ready yet, retry $retries/$MAX_ENGINE_RETRIES")
                delay(ENGINE_RETRY_DELAY_MS)
            }

            // After max retries, stop loading but mark as not connected
            Log.w(TAG, "IntelligenceEngine not found after $MAX_ENGINE_RETRIES retries")
            _isLoading.value = false
            _engineConnected.value = false
        }
    }

    /**
     * Subscribe to IntelligenceEngine's live score and pattern StateFlows.
     */
    private fun subscribeToEngine(engine: IntelligenceEngine) {
        // Collect live scores
        scoresJob?.cancel()
        scoresJob = viewModelScope.launch {
            engine.currentScores.collect { scores -> updateScores(scores) }
        }

        // Collect detected patterns
        patternsJob?.cancel()
        patternsJob = viewModelScope.launch {
            engine.detectedPatterns.collect { patterns -> updatePatterns(patterns) }
        }

        // Collect correlation insights from the engine's CorrelationEngine
        insightsJob?.cancel()
        insightsJob = viewModelScope.launch {
            engine.correlationEngine.insights.collect { insights -> updateInsights(insights) }
        }
    }

    /**
     * Poll engine manager for diagnostics (health status).
     */
    private fun observeEngineHealth() {
        viewModelScope.launch {
            while (true) {
                try {
                    val diagnostics = manager.getDiagnostics()
                    val statuses = diagnostics.map { diag ->
                        EngineStatusItem(
                            name = diag.engineName,
                            state = diag.state.name,
                            uptimeMs = diag.uptimeMs,
                            eventsProcessed = diag.eventsProcessed,
                            isHealthy = diag.state == EngineState.Running
                        )
                    }
                    _engineStatuses.value = statuses
                } catch (_: Exception) {}

                kotlinx.coroutines.delay(10_000)
            }
        }
    }

    /**
     * Called internally when the Intelligence Engine publishes score updates.
     */
    private fun updateScores(scores: IntelligenceEngine.IntelligenceScores) {
        _addictionIndex.value = scores.addictionIndex
        _focusScore.value = scores.focusScore
        _privacyExposure.value = scores.privacyExposure
        _overallHealth.value = scores.overallHealth

        // Update trends (keep last 24 data points)
        val aTrend = _addictionTrend.value.toMutableList()
        aTrend.add(scores.addictionIndex)
        if (aTrend.size > 24) _addictionTrend.value = aTrend.takeLast(24)
        else _addictionTrend.value = aTrend

        val fTrend = _focusTrend.value.toMutableList()
        fTrend.add(scores.focusScore)
        if (fTrend.size > 24) _focusTrend.value = fTrend.takeLast(24)
        else _focusTrend.value = fTrend
    }

    /**
     * Called internally when patterns are detected.
     */
    private fun updatePatterns(patterns: List<IntelligenceEngine.DetectedPattern>) {
        _detectedPatterns.value = patterns.map { p ->
            PatternAlert(
                type = p.type.name,
                description = p.description,
                severity = p.severity.name,
                timestamp = p.detectedAt
            )
        }
    }

    /**
     * Called internally when correlation insights are generated.
     */
    private fun updateInsights(insights: List<CorrelationEngine.CorrelationInsight>) {
        _correlationInsights.value = insights.map { i ->
            InsightItem(
                message = i.message,
                category = i.category.name,
                severity = i.severity.name,
                appPackage = i.appPackage,
                timestamp = i.timestamp
            )
        }
    }

    // ── UI Data Classes ─────────────────────────────────────────

    data class PatternAlert(
        val type: String,
        val description: String,
        val severity: String,
        val timestamp: Long
    )

    data class InsightItem(
        val message: String,
        val category: String,
        val severity: String,
        val appPackage: String?,
        val timestamp: Long
    )

    data class EngineStatusItem(
        val name: String,
        val state: String,
        val uptimeMs: Long,
        val eventsProcessed: Long,
        val isHealthy: Boolean
    )
}

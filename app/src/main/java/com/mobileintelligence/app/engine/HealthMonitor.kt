package com.mobileintelligence.app.engine

import android.app.Service
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Health monitor that continuously checks engine vitals and publishes metrics.
 *
 * Monitors:
 * - Engine state transitions
 * - Memory pressure (steady-state target < 100MB)
 * - Event processing rates
 * - Crash/recovery counts
 * - Watchdog heartbeats
 *
 * Exposes a [healthReport] StateFlow for UI consumption.
 */
class HealthMonitor(private val engineManager: EngineManager) {

    companion object {
        private const val TAG = "HealthMonitor"
        private const val REPORT_INTERVAL_MS = 10_000L
        private const val MEMORY_WARNING_BYTES = 80L * 1024 * 1024  // 80MB
        private const val MEMORY_CRITICAL_BYTES = 100L * 1024 * 1024 // 100MB
    }

    private val _healthReport = MutableStateFlow(HealthReport())
    val healthReport: StateFlow<HealthReport> = _healthReport.asStateFlow()

    private val _alerts = MutableSharedFlow<HealthAlert>(extraBufferCapacity = 32)
    val alerts: SharedFlow<HealthAlert> = _alerts.asSharedFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                val report = generateReport()
                _healthReport.value = report
                checkThresholds(report)
                delay(REPORT_INTERVAL_MS)
            }
        }
        Log.i(TAG, "Health monitor started")
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        Log.i(TAG, "Health monitor stopped")
    }

    private fun generateReport(): HealthReport {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()

        val diagnostics = engineManager.getDiagnostics()
        val overall = engineManager.overallHealth.value

        return HealthReport(
            timestamp = System.currentTimeMillis(),
            memoryUsedBytes = usedMemory,
            memoryMaxBytes = maxMemory,
            memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100),
            engineCount = diagnostics.size,
            enginesRunning = diagnostics.count { it.state == EngineState.Running },
            enginesError = diagnostics.count { it.state == EngineState.Error },
            totalEventsProcessed = overall.totalEventsProcessed,
            engineDetails = diagnostics,
            overallHealthy = overall.allHealthy && usedMemory < MEMORY_CRITICAL_BYTES
        )
    }

    private suspend fun checkThresholds(report: HealthReport) {
        // Memory warnings
        if (report.memoryUsedBytes > MEMORY_CRITICAL_BYTES) {
            _alerts.emit(
                HealthAlert(
                    level = AlertLevel.CRITICAL,
                    message = "Memory usage critical: ${report.memoryUsedBytes / 1024 / 1024}MB",
                    suggestion = "Consider clearing caches or reducing log retention"
                )
            )
        } else if (report.memoryUsedBytes > MEMORY_WARNING_BYTES) {
            _alerts.emit(
                HealthAlert(
                    level = AlertLevel.WARNING,
                    message = "Memory usage high: ${report.memoryUsedBytes / 1024 / 1024}MB"
                )
            )
        }

        // Engine error alerts
        if (report.enginesError > 0) {
            val errorEngines = report.engineDetails
                .filter { it.state == EngineState.Error }
                .joinToString { it.engineName }
            _alerts.emit(
                HealthAlert(
                    level = AlertLevel.ERROR,
                    message = "Engines in error state: $errorEngines",
                    suggestion = "Auto-recovery in progress"
                )
            )
        }
    }

    data class HealthReport(
        val timestamp: Long = System.currentTimeMillis(),
        val memoryUsedBytes: Long = 0,
        val memoryMaxBytes: Long = 0,
        val memoryUsagePercent: Float = 0f,
        val engineCount: Int = 0,
        val enginesRunning: Int = 0,
        val enginesError: Int = 0,
        val totalEventsProcessed: Long = 0,
        val engineDetails: List<EngineDiagnostics> = emptyList(),
        val overallHealthy: Boolean = true
    )

    data class HealthAlert(
        val timestamp: Long = System.currentTimeMillis(),
        val level: AlertLevel,
        val message: String,
        val suggestion: String? = null
    )

    enum class AlertLevel { INFO, WARNING, ERROR, CRITICAL }
}

package com.mobileintelligence.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Central lifecycle manager for all engines.
 *
 * Responsibilities:
 * - Ordered initialization and startup of engines
 * - Automatic crash recovery with exponential backoff
 * - Health monitoring and diagnostics
 * - Watchdog to detect stuck engines
 * - Graceful shutdown on app termination
 *
 * All engines run under a single SupervisorJob so one engine crashing
 * does not take down the others.
 */
class EngineManager(private val context: Context) {

    companion object {
        private const val TAG = "EngineManager"
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val MAX_RECOVERY_ATTEMPTS = 5
        private const val BASE_BACKOFF_MS = 1_000L

        @Volatile
        private var INSTANCE: EngineManager? = null

        fun getInstance(context: Context): EngineManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: EngineManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    val eventBus = EngineEventBus()

    private val supervisorJob = SupervisorJob()
    private val managerScope = CoroutineScope(supervisorJob + Dispatchers.Default)

    private val _engines = mutableListOf<EngineEntry>()
    val engines: List<EngineEntry> get() = _engines.toList()

    private val _overallHealth = MutableStateFlow(OverallHealth())
    val overallHealth: StateFlow<OverallHealth> = _overallHealth.asStateFlow()

    private val recoveryAttempts = mutableMapOf<String, Int>()
    private var watchdogJob: Job? = null
    private var isStarted = false

    // ── Registration ─────────────────────────────────────────────

    fun register(engine: Engine, priority: Int = 100): EngineManager {
        _engines.add(EngineEntry(engine, priority))
        _engines.sortBy { it.priority }
        Log.i(TAG, "Registered engine: ${engine.name} (priority=$priority)")
        return this
    }

    // ── Lifecycle ────────────────────────────────────────────────

    suspend fun startAll() {
        if (isStarted) {
            Log.w(TAG, "EngineManager already started")
            return
        }
        isStarted = true
        Log.i(TAG, "Starting ${_engines.size} engines...")

        // Initialize in priority order
        for (entry in _engines) {
            try {
                val engineScope = CoroutineScope(
                    supervisorJob + Dispatchers.Default + CoroutineName(entry.engine.name)
                )
                entry.engine.initialize(engineScope, eventBus)
                Log.i(TAG, "Initialized: ${entry.engine.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ${entry.engine.name}", e)
                entry.lastError = e.message
            }
        }

        // Start in priority order
        for (entry in _engines) {
            try {
                entry.engine.start()
                entry.startTimeMs = System.currentTimeMillis()
                Log.i(TAG, "Started: ${entry.engine.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ${entry.engine.name}", e)
                entry.lastError = e.message
            }
        }

        // Start watchdog
        startWatchdog()

        // Monitor engine state changes
        monitorEngines()

        updateHealth()
    }

    suspend fun stopAll() {
        Log.i(TAG, "Stopping all engines...")
        watchdogJob?.cancel()

        // Stop in reverse priority order
        for (entry in _engines.reversed()) {
            try {
                withTimeout(5000) {
                    entry.engine.stop()
                }
                Log.i(TAG, "Stopped: ${entry.engine.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ${entry.engine.name}", e)
            }
        }
        isStarted = false
        updateHealth()
    }

    // ── Watchdog ─────────────────────────────────────────────────

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = managerScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                checkEngineHealth()
            }
        }
    }

    private suspend fun checkEngineHealth() {
        for (entry in _engines) {
            val state = entry.engine.state.value
            if (state == EngineState.Error) {
                val attempts = recoveryAttempts.getOrDefault(entry.engine.name, 0)
                if (attempts < MAX_RECOVERY_ATTEMPTS) {
                    val backoff = BASE_BACKOFF_MS * (1L shl attempts.coerceAtMost(4))
                    Log.w(TAG, "Engine ${entry.engine.name} in error state. " +
                            "Recovery attempt ${attempts + 1}/$MAX_RECOVERY_ATTEMPTS " +
                            "(backoff=${backoff}ms)")
                    delay(backoff)
                    try {
                        entry.engine.recover()
                        entry.engine.start()
                        recoveryAttempts[entry.engine.name] = attempts + 1
                        entry.recoveryCount++
                        Log.i(TAG, "Recovered: ${entry.engine.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Recovery failed for ${entry.engine.name}", e)
                        recoveryAttempts[entry.engine.name] = attempts + 1
                    }
                } else {
                    Log.e(TAG, "Engine ${entry.engine.name} exceeded max recovery attempts. " +
                            "Manual intervention required.")
                }
            }
        }
        updateHealth()
    }

    // ── State Monitoring ─────────────────────────────────────────

    private fun monitorEngines() {
        for (entry in _engines) {
            managerScope.launch {
                entry.engine.state.collect { state ->
                    Log.d(TAG, "${entry.engine.name} → $state")
                    if (state == EngineState.Error) {
                        entry.lastError = try {
                            entry.engine.diagnostics().lastError
                        } catch (e: Exception) {
                            "Failed to get diagnostics: ${e.message}"
                        }
                    }
                    updateHealth()
                }
            }
        }
    }

    private fun updateHealth() {
        val safeDiagnostics = _engines.mapNotNull { entry ->
            try {
                entry.engine.diagnostics()
            } catch (e: Exception) {
                Log.w(TAG, "diagnostics() failed for ${entry.engine.name} in updateHealth", e)
                null
            }
        }
        _overallHealth.value = OverallHealth(
            engineStates = _engines.associate { it.engine.name to it.engine.state.value },
            allHealthy = _engines.all { it.engine.state.value == EngineState.Running },
            totalEventsProcessed = safeDiagnostics.sumOf { it.eventsProcessed },
            engineDiagnostics = safeDiagnostics
        )
    }

    fun getDiagnostics(): List<EngineDiagnostics> =
        _engines.mapNotNull { entry ->
            try {
                entry.engine.diagnostics()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get diagnostics for ${entry.engine.name}", e)
                null
            }
        }

    // ── Data Classes ─────────────────────────────────────────────

    data class EngineEntry(
        val engine: Engine,
        val priority: Int,
        var startTimeMs: Long = 0,
        var lastError: String? = null,
        var recoveryCount: Int = 0
    )

    data class OverallHealth(
        val engineStates: Map<String, EngineState> = emptyMap(),
        val allHealthy: Boolean = false,
        val totalEventsProcessed: Long = 0,
        val engineDiagnostics: List<EngineDiagnostics> = emptyList()
    )
}

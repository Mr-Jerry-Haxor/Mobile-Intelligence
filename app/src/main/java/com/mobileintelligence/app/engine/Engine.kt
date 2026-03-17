package com.mobileintelligence.app.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Base contract for all independent engines.
 * Each engine manages its own lifecycle, runs in its own scope,
 * and communicates with others exclusively via [EngineEventBus].
 */
interface Engine {

    /** Human-readable engine name for logs/diagnostics. */
    val name: String

    /** Engine health status observable by EngineManager. */
    val state: StateFlow<EngineState>

    /**
     * Initialize the engine. Called once by [EngineManager].
     * @param scope SupervisorScope provided by EngineManager — the engine
     *   should launch all its coroutines in this scope (or a child of it).
     * @param eventBus Shared bus for inter-engine communication.
     */
    suspend fun initialize(scope: CoroutineScope, eventBus: EngineEventBus)

    /**
     * Start the engine's main processing loop.
     * Must be idempotent — calling start() on a running engine is a no-op.
     */
    suspend fun start()

    /**
     * Graceful shutdown. Release resources, flush buffers, close connections.
     * Engine must transition to [EngineState.Stopped] when done.
     */
    suspend fun stop()

    /**
     * Called by EngineManager when the engine has crashed.
     * Should reset internal state and prepare for a fresh [start()].
     */
    suspend fun recover()

    /** Return engine health diagnostics for the health monitor. */
    fun diagnostics(): EngineDiagnostics
}

enum class EngineState {
    Created,
    Initializing,
    Running,
    Paused,
    Stopping,
    Stopped,
    Error
}

data class EngineDiagnostics(
    val engineName: String,
    val state: EngineState,
    val uptimeMs: Long = 0,
    val lastError: String? = null,
    val lastErrorTimestamp: Long? = null,
    val eventsProcessed: Long = 0,
    val memoryUsageBytes: Long = 0,
    val customMetrics: Map<String, Any> = emptyMap()
)

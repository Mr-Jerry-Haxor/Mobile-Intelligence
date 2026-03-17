package com.mobileintelligence.app.engine.storage

import android.content.Context
import android.util.Log
import com.mobileintelligence.app.data.database.IntelligenceDatabase
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Storage & Retention Engine — Engine #3
 *
 * Manages the entire data lifecycle for 3-year scalability:
 *
 * Level 1 (0-30 days):   Raw granular data — every session, every query
 * Level 2 (30-180 days): Hourly aggregates — merge raw into hourly summaries
 * Level 3 (6mo-3 years): Daily aggregates — compress hourly into daily
 *
 * Runs nightly rollup at midnight (WorkManager trigger) + on-demand compaction.
 * Publishes events for completed rollups and storage health.
 */
class StorageEngine(private val context: Context) : Engine {

    companion object {
        private const val TAG = "StorageEngine"
        private const val HEALTH_CHECK_INTERVAL_MS = 6 * 3600_000L    // 6 hours
        private const val MAX_RAW_DB_SIZE_MB = 500L                    // Alert if DB > 500 MB
    }

    override val name: String = "Storage"

    private val _state = MutableStateFlow(EngineState.Created)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private lateinit var eventBus: EngineEventBus

    lateinit var retentionPolicy: RetentionPolicy
        private set
    lateinit var rollupManager: DataRollupManager
        private set

    private val startTime = AtomicLong(0)
    private val eventsProcessed = AtomicLong(0)
    private var lastError: String? = null
    private var lastErrorTimestamp: Long? = null

    private var healthCheckJob: Job? = null

    override suspend fun initialize(scope: CoroutineScope, eventBus: EngineEventBus) {
        _state.value = EngineState.Initializing
        this.scope = scope
        this.eventBus = eventBus

        try {
            retentionPolicy = RetentionPolicy()
            rollupManager = DataRollupManager(context)

            Log.d(TAG, "Initialized: retention policy loaded, rollup manager ready")
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

        // Periodic health check
        healthCheckJob = scope.launch {
            while (isActive) {
                try {
                    checkStorageHealth()
                } catch (e: Exception) {
                    Log.w(TAG, "Health check failed", e)
                }
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }

        // Run initial check
        scope.launch {
            delay(5_000) // Wait for other engines to start
            try {
                checkStorageHealth()
                // Run rollup if data is stale
                performScheduledRollup()
            } catch (e: Exception) {
                Log.w(TAG, "Initial storage check failed", e)
            }
        }
    }

    override suspend fun stop() {
        if (_state.value == EngineState.Stopped) return
        _state.value = EngineState.Stopping

        healthCheckJob?.cancel()
        _state.value = EngineState.Stopped
        Log.d(TAG, "Engine stopped")
    }

    override suspend fun recover() {
        Log.w(TAG, "Recovering storage engine...")
        stop()
        delay(2_000)
        start()
    }

    override fun diagnostics(): EngineDiagnostics {
        val uptime = if (startTime.get() > 0) System.currentTimeMillis() - startTime.get() else 0
        return EngineDiagnostics(
            engineName = name,
            state = _state.value,
            uptimeMs = uptime,
            lastError = lastError,
            lastErrorTimestamp = lastErrorTimestamp,
            eventsProcessed = eventsProcessed.get(),
            customMetrics = buildMap {
                try {
                    val dbFile = context.getDatabasePath("mobile_intelligence.db")
                    val dnsDbFile = context.getDatabasePath("dns_firewall.db")
                    put("screenDbSizeMB", if (dbFile.exists()) dbFile.length() / (1024 * 1024) else 0)
                    put("dnsDbSizeMB", if (dnsDbFile.exists()) dnsDbFile.length() / (1024 * 1024) else 0)
                } catch (_: Exception) {
                    put("screenDbSizeMB", -1)
                    put("dnsDbSizeMB", -1)
                }
            }
        )
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Trigger an on-demand rollup. Called by WorkManager or manually.
     */
    suspend fun performScheduledRollup() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting scheduled data rollup...")

            val result = rollupManager.performFullRollup(retentionPolicy)

            eventBus.publish(DataRollupCompletedEvent(
                level = 1,
                recordsProcessed = result.rawToHourly,
                recordsPurged = result.purgedRaw,
                bytesFreed = result.estimatedBytesFreed
            ))
            eventsProcessed.incrementAndGet()

            if (result.hourlyToDaily > 0) {
                eventBus.publish(DataRollupCompletedEvent(
                    level = 2,
                    recordsProcessed = result.hourlyToDaily,
                    recordsPurged = result.purgedHourly
                ))
                eventsProcessed.incrementAndGet()
            }

            Log.d(TAG, "Rollup complete: ${result.rawToHourly} raw→hourly, " +
                    "${result.hourlyToDaily} hourly→daily, " +
                    "${result.purgedRaw + result.purgedHourly} records purged")

        } catch (e: Exception) {
            lastError = "Rollup failed: ${e.message}"
            lastErrorTimestamp = System.currentTimeMillis()
            Log.e(TAG, "Rollup failed", e)
        }
    }

    /**
     * Check storage health and publish events.
     */
    private suspend fun checkStorageHealth() {
        withContext(Dispatchers.IO) {
        try {
            val screenDbFile = context.getDatabasePath("mobile_intelligence.db")
            val dnsDbFile = context.getDatabasePath("dns_firewall.db")

            val totalSize = (if (screenDbFile.exists()) screenDbFile.length() else 0) +
                    (if (dnsDbFile.exists()) dnsDbFile.length() else 0)

            val healthy = totalSize < MAX_RAW_DB_SIZE_MB * 1024 * 1024

            eventBus.publish(StorageHealthEvent(
                totalDbSizeBytes = totalSize,
                oldestRecordDate = null, // Could query DB for oldest record
                retentionHealthy = healthy
            ))
            eventsProcessed.incrementAndGet()

            if (!healthy) {
                Log.w(TAG, "Storage health warning: total DB size = ${totalSize / (1024 * 1024)} MB")
            }
            Unit
        } catch (e: Exception) {
            Log.w(TAG, "Storage health check error", e)
        }
        }
    }

    /**
     * Get database file sizes for display.
     */
    fun getDatabaseSizes(): DatabaseSizes {
        val screenDb = context.getDatabasePath("mobile_intelligence.db")
        val dnsDb = context.getDatabasePath("dns_firewall.db")
        return DatabaseSizes(
            screenDbBytes = if (screenDb.exists()) screenDb.length() else 0,
            dnsDbBytes = if (dnsDb.exists()) dnsDb.length() else 0
        )
    }

    data class DatabaseSizes(
        val screenDbBytes: Long,
        val dnsDbBytes: Long
    ) {
        val totalBytes: Long get() = screenDbBytes + dnsDbBytes
        val screenDbMB: Float get() = screenDbBytes / (1024f * 1024f)
        val dnsDbMB: Float get() = dnsDbBytes / (1024f * 1024f)
        val totalMB: Float get() = totalBytes / (1024f * 1024f)
    }
}

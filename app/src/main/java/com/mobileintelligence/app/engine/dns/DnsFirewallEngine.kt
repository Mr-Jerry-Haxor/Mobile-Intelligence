package com.mobileintelligence.app.engine.dns

import android.content.Context
import android.util.Log
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.dns.data.DnsPreferences
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import com.mobileintelligence.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/**
 * DNS Firewall Engine — Engine #2
 *
 * Wraps the existing [LocalDnsVpnService] with the engine lifecycle contract.
 * Adds production hardening:
 *  - DoH detection & blocking
 *  - Tiered blocklist management (Essential → Paranoid)
 *  - Domain categorization (ads/analytics/social/malware/CDN/push)
 *  - Battery-optimized packet processing via RingBufferPool
 *  - Event publishing to EngineEventBus
 */
class DnsFirewallEngine(private val context: Context) : Engine {

    companion object {
        private const val TAG = "DnsFirewallEngine"
    }

    override val name: String = "DnsFirewall"

    private val _state = MutableStateFlow(EngineState.Created)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private lateinit var eventBus: EngineEventBus

    // Sub-components
    private lateinit var dohDetector: DoHDetector
    private lateinit var tieredBlocklistManager: TieredBlocklistManager
    private lateinit var domainCategoryEngine: DomainCategoryEngine
    private lateinit var ringBufferPool: RingBufferPool

    // Metrics
    private val startTime = AtomicLong(0)
    private val eventsPublished = AtomicLong(0)
    private var lastError: String? = null
    private var lastErrorTimestamp: Long? = null

    // VPN state monitoring
    private var vpnMonitorJob: Job? = null
    private var dnsEventJob: Job? = null

    override suspend fun initialize(scope: CoroutineScope, eventBus: EngineEventBus) {
        _state.value = EngineState.Initializing
        this.scope = scope
        this.eventBus = eventBus

        try {
            // Initialize sub-components
            dohDetector = DoHDetector()
            tieredBlocklistManager = TieredBlocklistManager(context)
            domainCategoryEngine = DomainCategoryEngine()
            ringBufferPool = RingBufferPool(poolSize = 64, bufferSize = 2048)

            // Pre-load tiered blocklists
            tieredBlocklistManager.initialize()

            Log.d(TAG, "Initialized: DoH detector ready, " +
                    "${tieredBlocklistManager.getTotalDomainCount()} domains in tiered lists, " +
                    "buffer pool allocated (${ringBufferPool.poolSize} x ${ringBufferPool.bufferSize})")

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

        // Monitor VPN running state and republish to event bus
        vpnMonitorJob = scope.launch {
            LocalDnsVpnService.isRunning.collect { running ->
                eventBus.publish(DnsVpnStateChangedEvent(isRunning = running))
                eventsPublished.incrementAndGet()
            }
        }

        // Monitor DNS queries from the service counters and generate events
        dnsEventJob = scope.launch {
            monitorDnsActivity()
        }
    }

    /**
     * Periodic monitoring of DNS activity for event generation.
     * Publishes aggregated stats as events without duplicating the per-query processing
     * which remains in LocalDnsVpnService.
     */
    private suspend fun monitorDnsActivity() {
        var lastQueryCount = 0
        var lastBlockedCount = 0

        while (scope.isActive) {
            delay(5_000) // Check every 5 seconds

            val currentQueries = LocalDnsVpnService.todayQueries.value
            val currentBlocked = LocalDnsVpnService.todayBlocked.value

            // Publish batch summary if there's new activity
            if (currentQueries > lastQueryCount) {
                val newQueries = currentQueries - lastQueryCount
                val newBlocked = currentBlocked - lastBlockedCount

                eventBus.publish(DnsQueryProcessedEvent(
                    domain = "_batch_summary",
                    blocked = newBlocked > 0,
                    cached = false,
                    responseTimeMs = 0,
                    appPackage = null,
                    category = "batch",
                    blockReason = if (newBlocked > 0) "batch: $newBlocked/$newQueries blocked" else null
                ))
                eventsPublished.incrementAndGet()
            }

            lastQueryCount = currentQueries
            lastBlockedCount = currentBlocked
        }
    }

    override suspend fun stop() {
        if (_state.value == EngineState.Stopped) return
        _state.value = EngineState.Stopping

        vpnMonitorJob?.cancel()
        dnsEventJob?.cancel()
        ringBufferPool.clear()

        _state.value = EngineState.Stopped
        Log.d(TAG, "Engine stopped")
    }

    override suspend fun recover() {
        Log.w(TAG, "Recovering DNS Firewall engine...")
        stop()
        delay(1_000)
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
            eventsProcessed = eventsPublished.get(),
            memoryUsageBytes = try { ringBufferPool.estimateMemoryUsage() } catch (_: UninitializedPropertyAccessException) { 0 },
            customMetrics = buildMap {
                put("vpnRunning", LocalDnsVpnService.isRunning.value)
                put("todayQueries", LocalDnsVpnService.todayQueries.value)
                put("todayBlocked", LocalDnsVpnService.todayBlocked.value)
                try {
                    put("tieredDomains", tieredBlocklistManager.getTotalDomainCount())
                    put("dohEndpoints", dohDetector.getKnownEndpointCount())
                    put("bufferPoolActive", ringBufferPool.activeBufferCount())
                } catch (_: UninitializedPropertyAccessException) {
                    put("tieredDomains", 0)
                    put("dohEndpoints", 0)
                    put("bufferPoolActive", 0)
                }
            }
        )
    }

    // ── Public accessors for UI/ViewModels ──────────────────────

    fun getDohDetector(): DoHDetector = dohDetector
    fun getTieredBlocklistManager(): TieredBlocklistManager = tieredBlocklistManager
    fun getDomainCategoryEngine(): DomainCategoryEngine = domainCategoryEngine

    /**
     * Classify a domain through the full enhanced pipeline:
     * 1. DoH detection
     * 2. Category classification
     * 3. Tiered blocklist check
     */
    fun classifyDomain(domain: String, appPackage: String? = null): DomainClassification {
        val isDoh = dohDetector.isDoHEndpoint(domain)
        val category = domainCategoryEngine.categorize(domain)
        val tierDecision = tieredBlocklistManager.check(domain)

        if (isDoh) {
            scope.launch {
                eventBus.publish(DoHAttemptDetectedEvent(
                    domain = domain,
                    appPackage = appPackage,
                    wasBlocked = tierDecision.shouldBlock
                ))
                eventsPublished.incrementAndGet()
            }
        }

        return DomainClassification(
            domain = domain,
            isDoH = isDoh,
            category = category,
            tieredDecision = tierDecision,
            appPackage = appPackage
        )
    }

    data class DomainClassification(
        val domain: String,
        val isDoH: Boolean,
        val category: DomainCategoryEngine.DomainCategory,
        val tieredDecision: TieredBlocklistManager.TierDecision,
        val appPackage: String?
    )
}

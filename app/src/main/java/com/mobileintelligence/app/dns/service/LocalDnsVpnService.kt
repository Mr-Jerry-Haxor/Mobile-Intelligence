package com.mobileintelligence.app.dns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobileintelligence.app.R
import com.mobileintelligence.app.dns.core.*
import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.dns.data.DnsPreferences
import com.mobileintelligence.app.dns.filter.DomainFilter
import com.mobileintelligence.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Local DNS filtering VPN service.
 * Creates a TUN interface to intercept DNS queries,
 * filters them against blocklists, and returns forged or upstream responses.
 *
 * Does NOT tunnel any non-DNS traffic.
 * Does NOT decrypt HTTPS.
 * Does NOT proxy or inspect packet payloads.
 * Processes ONLY DNS domain names.
 */
class LocalDnsVpnService : VpnService() {

    companion object {
        private const val TAG = "LocalDnsVpn"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "dns_vpn_channel"
        private const val VPN_MTU = 1500
        private const val TUN_ADDRESS_V4 = "10.0.0.2"
        private const val TUN_ROUTE_V4 = "0.0.0.0"
        private const val TUN_DNS_V4 = "10.0.0.1"
        private const val TUN_ADDRESS_V6 = "fd00::2"
        private const val TUN_DNS_V6 = "fd00::1"
        private const val BUFFER_SIZE = 32768 // 32KB packet buffer

        // Service state observable
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _todayBlocked = MutableStateFlow(0)
        val todayBlocked: StateFlow<Int> = _todayBlocked

        private val _todayQueries = MutableStateFlow(0)
        val todayQueries: StateFlow<Int> = _todayQueries

        fun start(context: Context) {
            val intent = Intent(context, LocalDnsVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalDnsVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private const val ACTION_START = "com.mobileintelligence.dns.START"
        private const val ACTION_STOP = "com.mobileintelligence.dns.STOP"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vpnInterface: ParcelFileDescriptor? = null

    // Core components
    private lateinit var domainFilter: DomainFilter
    private lateinit var upstreamResolver: UpstreamResolver
    private lateinit var dnsCache: DnsCache
    private lateinit var appResolver: AppResolver
    private lateinit var queryLogger: DnsQueryLogger
    private lateinit var dnsPreferences: DnsPreferences
    private lateinit var dnsDatabase: DnsDatabase

    // Counters
    private val queriesCount = AtomicInteger(0)
    private val blockedCount = AtomicInteger(0)
    private val cacheHitCount = AtomicInteger(0)
    private val lastNotificationUpdate = AtomicLong(0)

    // Block mode
    @Volatile
    private var blockMode = DnsParser.BlockMode.ZERO_IP

    // Wakelock
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()

        // Initialize components
        dnsDatabase = DnsDatabase.getInstance(this)
        dnsPreferences = DnsPreferences(this)
        domainFilter = DomainFilter(this)
        upstreamResolver = UpstreamResolver()
        dnsCache = DnsCache()
        appResolver = AppResolver(this)
        queryLogger = DnsQueryLogger(dnsDatabase, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                serviceScope.launch {
                    initializeAndStart()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun initializeAndStart() {
        try {
            // Load preferences
            loadPreferences()

            // Load blocklists
            domainFilter.loadAll()

            // Start logger
            queryLogger.start()

            // Establish VPN
            establishVpn()

            _isRunning.value = true
            Log.d(TAG, "VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private suspend fun loadPreferences() {
        // Block mode
        val modeStr = dnsPreferences.blockMode.first()
        blockMode = when (modeStr) {
            "nxdomain" -> DnsParser.BlockMode.NXDOMAIN
            "zero_ipv6" -> DnsParser.BlockMode.ZERO_IPV6
            else -> DnsParser.BlockMode.ZERO_IP
        }

        // DNS provider
        val provider = dnsPreferences.dnsProvider.first()
        upstreamResolver.providers = when (provider) {
            "google" -> listOf(UpstreamResolver.GOOGLE, UpstreamResolver.CLOUDFLARE)
            "quad9" -> listOf(UpstreamResolver.QUAD9, UpstreamResolver.CLOUDFLARE)
            "custom" -> {
                val primary = dnsPreferences.customDnsPrimary.first()
                val secondary = dnsPreferences.customDnsSecondary.first()
                val custom = UpstreamResolver.DnsProvider("Custom", primary, secondary.ifEmpty { null })
                listOf(custom, UpstreamResolver.CLOUDFLARE)
            }
            else -> listOf(UpstreamResolver.CLOUDFLARE, UpstreamResolver.GOOGLE)
        }

        // Filter categories
        domainFilter.adsEnabled = dnsPreferences.blockAds.first()
        domainFilter.trackersEnabled = dnsPreferences.blockTrackers.first()
        domainFilter.malwareEnabled = dnsPreferences.blockMalware.first()

        // Logging
        queryLogger.enabled = dnsPreferences.loggingEnabled.first()
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("Mobile Intelligence DNS")
            .setMtu(VPN_MTU)
            .addAddress(TUN_ADDRESS_V4, 32)
            .addRoute(TUN_DNS_V4, 32)       // Route only the fake DNS server IP
            .addDnsServer(TUN_DNS_V4)
            .addAddress(TUN_ADDRESS_V6, 128)
            .addRoute(TUN_DNS_V6, 128)
            .addDnsServer(TUN_DNS_V6)
            .setBlocking(true)

        // Don't route this app's own traffic through VPN (prevent loops)
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to exclude self from VPN", e)
        }

        // Exclude bypassed apps
        for (pkg in domainFilter.getBypassedApps()) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {}
        }

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            throw IllegalStateException("VPN interface is null — VPN permission not granted")
        }

        // Start packet processing
        startPacketLoop()
    }

    private fun startPacketLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return

        // Reader coroutine
        serviceScope.launch(Dispatchers.IO) {
            acquireWakeLock()
            try {
                readLoop(fd)
            } finally {
                releaseWakeLock()
            }
        }
    }

    private suspend fun readLoop(fd: FileDescriptor) {
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteArray(BUFFER_SIZE)

        while (serviceScope.isActive && vpnInterface != null) {
            try {
                val length = input.read(buffer)
                if (length <= 0) {
                    delay(1) // Avoid busy-spin on empty reads
                    continue
                }

                // Parse packet
                val parsed = PacketParser.parse(buffer, length) ?: continue

                if (!parsed.isDnsQuery) continue

                // Process DNS query in parallel
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val response = processDnsQuery(parsed)
                        if (response != null) {
                            val responsePacket = PacketParser.buildResponsePacket(parsed, response)
                            synchronized(output) {
                                output.write(responsePacket)
                                output.flush()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing DNS query", e)
                    }
                }
            } catch (e: Exception) {
                if (serviceScope.isActive) {
                    Log.w(TAG, "Read loop error", e)
                    delay(10)
                } else {
                    break
                }
            }
        }

        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    /**
     * Core DNS processing pipeline.
     */
    private suspend fun processDnsQuery(packet: PacketParser.ParsedPacket): ByteArray? {
        val startTime = System.nanoTime()

        // Parse DNS message
        val dnsMessage = DnsParser.parse(packet.dnsPayload) ?: return null
        if (!dnsMessage.header.isQuery) return null

        val domain = DnsParser.normalizeDomain(dnsMessage.primaryDomain)
        if (domain.isEmpty()) return null

        val recordType = dnsMessage.primaryType
        val recordTypeName = DnsParser.typeName(recordType)

        // Resolve app
        val uid = appResolver.resolveUidFromPort(packet.sourcePort)
        val appPackage = appResolver.getPackageName(uid)

        // Update counters
        val queryNum = queriesCount.incrementAndGet()
        _todayQueries.value = queryNum

        // Check filter decision
        val decision = domainFilter.check(domain, appPackage)

        if (decision.isBlocked) {
            // BLOCKED — return forged response
            val blockedNum = blockedCount.incrementAndGet()
            _todayBlocked.value = blockedNum

            val responseTime = (System.nanoTime() - startTime) / 1_000_000

            // Log blocked query
            val reason = when (decision.result) {
                DomainFilter.FilterResult.BLOCKED_ADS -> "ads"
                DomainFilter.FilterResult.BLOCKED_TRACKER -> "tracker"
                DomainFilter.FilterResult.BLOCKED_MALWARE -> "malware"
                DomainFilter.FilterResult.BLOCKED_USER_RULE -> "user"
                DomainFilter.FilterResult.BLOCKED_REGEX -> "regex"
                else -> "blocked"
            }

            queryLogger.log(
                domain = domain,
                blocked = true,
                blockReason = reason,
                responseTimeMs = responseTime,
                recordType = recordType,
                recordTypeName = recordTypeName,
                uid = uid,
                appPackage = appPackage
            )

            updateNotification()

            return DnsParser.buildBlockedResponse(dnsMessage, blockMode)
        }

        // ALLOWED — check cache first
        val cached = dnsCache.get(domain, recordType)
        if (cached != null) {
            val responseTime = (System.nanoTime() - startTime) / 1_000_000
            cacheHitCount.incrementAndGet()

            queryLogger.log(
                domain = domain,
                blocked = false,
                responseTimeMs = responseTime,
                recordType = recordType,
                recordTypeName = recordTypeName,
                cached = true,
                uid = uid,
                appPackage = appPackage
            )

            // Rebuild response with original query ID
            return rewriteDnsId(cached.dnsResponse, dnsMessage.header.id)
        }

        // Forward to upstream DNS
        val upstreamResponse = upstreamResolver.resolve(packet.dnsPayload) ?: return null
        val responseTime = (System.nanoTime() - startTime) / 1_000_000

        // Parse response for caching
        val responseDns = DnsParser.parse(upstreamResponse)
        if (responseDns != null) {
            // Cache with TTL from first answer record
            val ttl = responseDns.answers.firstOrNull()?.ttl ?: 300
            dnsCache.put(domain, recordType, upstreamResponse, ttl)

            val responseIp = responseDns.answers.firstOrNull()?.getIpAddress()

            queryLogger.log(
                domain = domain,
                blocked = false,
                responseTimeMs = responseTime,
                recordType = recordType,
                recordTypeName = recordTypeName,
                uid = uid,
                appPackage = appPackage,
                upstreamDns = upstreamResolver.providers.firstOrNull()?.primaryIp,
                responseIp = responseIp
            )
        }

        updateNotification()

        return upstreamResponse
    }

    /**
     * Rewrite the DNS transaction ID in a cached response.
     */
    private fun rewriteDnsId(response: ByteArray, newId: Int): ByteArray {
        val copy = response.copyOf()
        copy[0] = ((newId shr 8) and 0xFF).toByte()
        copy[1] = (newId and 0xFF).toByte()
        return copy
    }

    /**
     * Update the persistent notification (throttled to max once per second).
     */
    private fun updateNotification() {
        val now = System.currentTimeMillis()
        val last = lastNotificationUpdate.get()
        if (now - last < 1000) return
        if (!lastNotificationUpdate.compareAndSet(last, now)) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val queries = queriesCount.get()
        val blocked = blockedCount.get()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "network_protection")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network protection active")
            .setContentText("Queries: $queries | Blocked: $blocked")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DNS Firewall",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows DNS filtering status"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN...")
        _isRunning.value = false

        queryLogger.stop()

        serviceScope.coroutineContext.cancelChildren()

        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        releaseWakeLock()

        // Persist stats
        serviceScope.launch {
            try {
                dnsPreferences.incrementLifetimeStats(
                    queriesCount.get().toLong(),
                    blockedCount.get().toLong()
                )
            } catch (_: Exception) {}
        }

        queriesCount.set(0)
        blockedCount.set(0)
        _todayQueries.value = 0
        _todayBlocked.value = 0
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MobileIntelligence::DnsVpn"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minute max, will re-acquire
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN revoked by system")
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Auto-restart after removal from recents
        val restartIntent = Intent(this, LocalDnsVpnService::class.java).apply {
            action = ACTION_START
        }

        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }
}

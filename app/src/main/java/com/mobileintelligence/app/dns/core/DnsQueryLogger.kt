package com.mobileintelligence.app.dns.core

import com.mobileintelligence.app.dns.data.DnsDatabase
import com.mobileintelligence.app.dns.data.entity.DnsQueryEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.*

/**
 * High-throughput DNS query logger.
 * Batches inserts for efficiency using a coroutine channel.
 * Processes queries in batches to minimize database I/O.
 */
class DnsQueryLogger(
    private val database: DnsDatabase,
    private val scope: CoroutineScope
) {
    companion object {
        private const val BATCH_SIZE = 50
        private const val FLUSH_INTERVAL_MS = 2000L
    }

    private val channel = Channel<DnsQueryEntity>(Channel.BUFFERED)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var loggerJob: Job? = null

    @Volatile
    var enabled = true

    fun start() {
        loggerJob = scope.launch(Dispatchers.IO) {
            val batch = mutableListOf<DnsQueryEntity>()
            var lastFlush = System.currentTimeMillis()

            while (isActive) {
                val result = withTimeoutOrNull(FLUSH_INTERVAL_MS) {
                    channel.receive()
                }

                if (result != null) {
                    batch.add(result)
                }

                val shouldFlush = batch.size >= BATCH_SIZE ||
                        (batch.isNotEmpty() && System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL_MS)

                if (shouldFlush) {
                    try {
                        database.dnsQueryDao().insertAll(batch.toList())
                    } catch (e: Exception) {
                        // Log error but don't crash
                    }
                    batch.clear()
                    lastFlush = System.currentTimeMillis()
                }
            }

            // Final flush on shutdown
            if (batch.isNotEmpty()) {
                try {
                    database.dnsQueryDao().insertAll(batch)
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        loggerJob?.cancel()
        loggerJob = null
    }

    /**
     * Log a DNS query asynchronously.
     */
    suspend fun log(
        domain: String,
        blocked: Boolean,
        blockReason: String? = null,
        responseTimeMs: Long = 0,
        recordType: Int = DnsParser.TYPE_A,
        recordTypeName: String = "A",
        cached: Boolean = false,
        uid: Int = -1,
        appPackage: String = "",
        upstreamDns: String? = null,
        responseIp: String? = null
    ) {
        if (!enabled) return

        val now = System.currentTimeMillis()
        val entity = DnsQueryEntity(
            timestamp = now,
            uid = uid,
            appPackage = appPackage,
            domain = domain,
            blocked = blocked,
            blockReason = blockReason,
            responseTimeMs = responseTimeMs,
            recordType = recordType,
            recordTypeName = recordTypeName,
            cached = cached,
            upstreamDns = upstreamDns,
            responseIp = responseIp,
            dateStr = dateFormat.format(Date(now))
        )

        channel.trySend(entity)
    }

    /**
     * Purge logs older than retention period.
     */
    suspend fun purgeOldLogs(retentionDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        database.dnsQueryDao().deleteOlderThan(cutoff)
    }
}

package com.mobileintelligence.app.dns.core

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory LRU DNS cache.
 * Respects TTL, supports 10k entries, auto-cleanup.
 */
class DnsCache(maxEntries: Int = 10_000) {

    data class CacheKey(val domain: String, val recordType: Int)

    data class CacheEntry(
        val dnsResponse: ByteArray,
        val insertedAt: Long = System.currentTimeMillis(),
        val ttlSeconds: Int,
        val domain: String,
        val recordType: Int
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() > insertedAt + (ttlSeconds * 1000L)

        val remainingTtlSeconds: Int
            get() {
                val remaining = (ttlSeconds * 1000L - (System.currentTimeMillis() - insertedAt)) / 1000L
                return remaining.coerceAtLeast(0).toInt()
            }
    }

    private val cache = object : LruCache<CacheKey, CacheEntry>(maxEntries) {
        override fun sizeOf(key: CacheKey, value: CacheEntry): Int = 1
    }

    private val mutex = Mutex()

    /**
     * Lookup a cached DNS response.
     * Returns null if not found or expired.
     */
    suspend fun get(domain: String, recordType: Int): CacheEntry? = mutex.withLock {
        val key = CacheKey(domain.lowercase(), recordType)
        val entry = cache.get(key) ?: return null

        if (entry.isExpired) {
            cache.remove(key)
            return null
        }

        return entry
    }

    /**
     * Store a DNS response with TTL.
     */
    suspend fun put(domain: String, recordType: Int, dnsResponse: ByteArray, ttlSeconds: Int) = mutex.withLock {
        if (ttlSeconds <= 0) return@withLock

        val key = CacheKey(domain.lowercase(), recordType)
        cache.put(key, CacheEntry(
            dnsResponse = dnsResponse.copyOf(),
            ttlSeconds = ttlSeconds,
            domain = domain.lowercase(),
            recordType = recordType
        ))
    }

    /**
     * Remove expired entries.
     */
    suspend fun cleanup() = mutex.withLock {
        val snapshot = cache.snapshot()
        for ((key, entry) in snapshot) {
            if (entry.isExpired) {
                cache.remove(key)
            }
        }
    }

    /**
     * Clear all cached entries.
     */
    suspend fun clear() = mutex.withLock {
        cache.evictAll()
    }

    /**
     * Get current cache statistics.
     */
    suspend fun stats(): CacheStats = mutex.withLock {
        val snapshot = cache.snapshot()
        val expired = snapshot.values.count { it.isExpired }
        CacheStats(
            totalEntries = snapshot.size,
            expiredEntries = expired,
            activeEntries = snapshot.size - expired,
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            maxSize = cache.maxSize()
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val expiredEntries: Int,
        val activeEntries: Int,
        val hitCount: Int,
        val missCount: Int,
        val maxSize: Int
    )
}

package com.mobileintelligence.app.engine.dns

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ring Buffer Pool — zero-copy ByteBuffer reuse for DNS packet processing.
 *
 * Eliminates per-packet ByteArray allocation by maintaining a pool of
 * pre-allocated direct ByteBuffers. Buffers are borrowed for processing
 * and returned when done.
 *
 * Battery optimization: Reduces GC pressure significantly under high
 * query throughput (100+ queries/sec).
 *
 * Design:
 * - Pre-allocates [poolSize] direct ByteBuffers of [bufferSize] each
 * - ConcurrentLinkedQueue for lock-free borrow/return
 * - Fallback: creates on-heap buffer if pool is exhausted (never blocks)
 * - Statistics tracking for monitoring pool utilization
 */
class RingBufferPool(
    val poolSize: Int = 64,
    val bufferSize: Int = 2048 // Enough for most DNS packets
) {
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    private val activeCount = AtomicInteger(0)
    private val borrowCount = AtomicInteger(0)
    private val fallbackCount = AtomicInteger(0)

    init {
        // Pre-allocate direct ByteBuffers
        repeat(poolSize) {
            pool.offer(ByteBuffer.allocateDirect(bufferSize))
        }
    }

    /**
     * Borrow a buffer from the pool.
     * If pool is empty, creates a heap buffer as fallback.
     * Always call [returnBuffer] when done.
     */
    fun borrow(): PooledBuffer {
        borrowCount.incrementAndGet()
        val buffer = pool.poll()

        return if (buffer != null) {
            buffer.clear()
            activeCount.incrementAndGet()
            PooledBuffer(buffer, isDirect = true, this)
        } else {
            // Pool exhausted — fallback to heap allocation
            fallbackCount.incrementAndGet()
            activeCount.incrementAndGet()
            PooledBuffer(ByteBuffer.allocate(bufferSize), isDirect = false, this)
        }
    }

    /**
     * Return a buffer to the pool.
     * Only direct (pooled) buffers are returned; heap fallbacks are discarded.
     */
    internal fun returnBuffer(buffer: ByteBuffer, isDirect: Boolean) {
        activeCount.decrementAndGet()
        if (isDirect) {
            buffer.clear()
            pool.offer(buffer)
        }
        // Heap buffers are GC'd
    }

    /**
     * Get number of currently borrowed (active) buffers.
     */
    fun activeBufferCount(): Int = activeCount.get()

    /**
     * Get number of available buffers in pool.
     */
    fun availableCount(): Int = pool.size

    /**
     * Estimate total memory usage of the pool.
     */
    fun estimateMemoryUsage(): Long =
        poolSize.toLong() * bufferSize

    /**
     * Get pool utilization statistics.
     */
    fun stats(): PoolStats = PoolStats(
        totalAllocated = poolSize,
        available = pool.size,
        active = activeCount.get(),
        totalBorrows = borrowCount.get(),
        fallbackAllocations = fallbackCount.get(),
        estimatedMemoryBytes = estimateMemoryUsage()
    )

    /**
     * Clear all buffers. Call on engine shutdown.
     */
    fun clear() {
        pool.clear()
        activeCount.set(0)
    }

    data class PoolStats(
        val totalAllocated: Int,
        val available: Int,
        val active: Int,
        val totalBorrows: Int,
        val fallbackAllocations: Int,
        val estimatedMemoryBytes: Long
    ) {
        val utilizationPercent: Float
            get() = if (totalAllocated > 0)
                ((totalAllocated - available).toFloat() / totalAllocated * 100f)
            else 0f

        val fallbackRate: Float
            get() = if (totalBorrows > 0)
                (fallbackAllocations.toFloat() / totalBorrows * 100f)
            else 0f
    }

    /**
     * RAII-style buffer wrapper. Use with try-finally or Kotlin's use pattern.
     *
     * ```kotlin
     * pool.borrow().use { buf ->
     *     buf.buffer.put(data)
     *     // process...
     * }  // auto-returned
     * ```
     */
    class PooledBuffer(
        val buffer: ByteBuffer,
        private val isDirect: Boolean,
        private val pool: RingBufferPool
    ) : AutoCloseable {

        @Volatile
        private var returned = false

        fun release() {
            if (!returned) {
                returned = true
                pool.returnBuffer(buffer, isDirect)
            }
        }

        override fun close() = release()

        /**
         * Kotlin `use`-compatible execution block.
         */
        inline fun <R> use(block: (PooledBuffer) -> R): R {
            return try {
                block(this)
            } finally {
                release()
            }
        }
    }
}

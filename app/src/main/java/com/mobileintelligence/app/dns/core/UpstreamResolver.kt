package com.mobileintelligence.app.dns.core

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Upstream DNS resolver.
 * Forwards allowed queries to configured DNS providers using UDP.
 * Supports timeout, retry, fallback, and parallel resolution.
 */
class UpstreamResolver {

    data class DnsProvider(
        val name: String,
        val primaryIp: String,
        val secondaryIp: String? = null,
        val port: Int = 53
    )

    companion object {
        val GOOGLE = DnsProvider("Google", "8.8.8.8", "8.8.4.4")
        val CLOUDFLARE = DnsProvider("Cloudflare", "1.1.1.1", "1.0.0.1")
        val QUAD9 = DnsProvider("Quad9", "9.9.9.9", "149.112.112.112")

        val DEFAULT_PROVIDERS = listOf(CLOUDFLARE, GOOGLE, QUAD9)

        private const val TIMEOUT_MS = 3000
        private const val MAX_RETRIES = 2
        private const val MAX_RESPONSE_SIZE = 4096
    }

    @Volatile
    var providers: List<DnsProvider> = listOf(CLOUDFLARE)

    /**
     * Resolve a DNS query against upstream providers.
     * Tries primary provider first, falls back to secondaries.
     * Returns the raw DNS response bytes, or null on failure.
     */
    suspend fun resolve(queryPayload: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        for (provider in providers) {
            val result = resolveWithProvider(queryPayload, provider)
            if (result != null) return@withContext result
        }
        null
    }

    /**
     * Resolve using parallel queries to all configured providers.
     * Returns the first successful response.
     */
    suspend fun resolveParallel(queryPayload: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val deferreds = providers.map { provider ->
            async {
                try {
                    resolveWithProvider(queryPayload, provider)
                } catch (e: Exception) {
                    null
                }
            }
        }

        // Return first non-null result
        for (deferred in deferreds) {
            val result = deferred.await()
            if (result != null) {
                // Cancel remaining
                deferreds.forEach { it.cancel() }
                return@withContext result
            }
        }
        null
    }

    private suspend fun resolveWithProvider(
        queryPayload: ByteArray,
        provider: DnsProvider
    ): ByteArray? {
        // Try primary
        val primary = resolveUdp(queryPayload, provider.primaryIp, provider.port)
        if (primary != null) return primary

        // Try secondary if available
        if (provider.secondaryIp != null) {
            return resolveUdp(queryPayload, provider.secondaryIp, provider.port)
        }

        return null
    }

    private fun resolveUdp(
        queryPayload: ByteArray,
        serverIp: String,
        port: Int
    ): ByteArray? {
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var lastException: Exception? = null

        repeat(MAX_RETRIES) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = TIMEOUT_MS

                val address = InetAddress.getByName(serverIp)
                val sendPacket = DatagramPacket(queryPayload, queryPayload.size, address, port)
                socket.send(sendPacket)

                val recvBuffer = ByteArray(MAX_RESPONSE_SIZE)
                val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                socket.receive(recvPacket)

                val response = ByteArray(recvPacket.length)
                System.arraycopy(recvBuffer, 0, response, 0, recvPacket.length)
                return response
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: Exception) {
                lastException = e
                return null // Non-timeout errors don't retry
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        return null
    }
}

package com.mobileintelligence.app.dns.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance IPv4/IPv6 + UDP/TCP packet parser.
 * Extracts DNS payloads from raw TUN interface packets.
 */
object PacketParser {

    const val IP_VERSION_4 = 4
    const val IP_VERSION_6 = 6
    const val PROTOCOL_UDP = 17
    const val PROTOCOL_TCP = 6
    const val DNS_PORT = 53

    data class ParsedPacket(
        val ipVersion: Int,
        val protocol: Int,           // UDP=17, TCP=6
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val dnsPayload: ByteArray,   // Raw DNS message bytes
        val ipHeaderLength: Int,
        val transportHeaderLength: Int,
        val totalLength: Int,
        val identification: Int,     // IPv4 ID field for response matching
        val ttl: Int,
        val rawPacket: ByteArray     // Full original packet for response building
    ) {
        val isDnsQuery: Boolean get() = destPort == DNS_PORT
        val isDnsResponse: Boolean get() = sourcePort == DNS_PORT
    }

    /**
     * Parse a raw IP packet from the TUN interface.
     * Returns null if the packet is not a DNS packet.
     */
    fun parse(packet: ByteArray, length: Int): ParsedPacket? {
        if (length < 20) return null

        val buffer = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN)
        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val ipVersion = (versionAndIhl shr 4) and 0x0F

        return when (ipVersion) {
            IP_VERSION_4 -> parseIPv4(buffer, packet, length)
            IP_VERSION_6 -> parseIPv6(buffer, packet, length)
            else -> null
        }
    }

    private fun parseIPv4(buffer: ByteBuffer, rawPacket: ByteArray, length: Int): ParsedPacket? {
        if (length < 20) return null

        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val totalLength = buffer.getShort(2).toInt() and 0xFFFF
        val identification = buffer.getShort(4).toInt() and 0xFFFF
        val ttl = buffer.get(8).toInt() and 0xFF
        val protocol = buffer.get(9).toInt() and 0xFF

        // Only care about UDP and TCP
        if (protocol != PROTOCOL_UDP && protocol != PROTOCOL_TCP) return null

        val sourceIp = ByteArray(4)
        val destIp = ByteArray(4)
        buffer.position(12)
        buffer.get(sourceIp)
        buffer.get(destIp)

        return parseTransport(
            buffer = buffer,
            rawPacket = rawPacket,
            length = length,
            ipVersion = IP_VERSION_4,
            protocol = protocol,
            sourceIp = sourceIp,
            destIp = destIp,
            ipHeaderLength = ihl,
            totalLength = totalLength,
            identification = identification,
            ttl = ttl
        )
    }

    private fun parseIPv6(buffer: ByteBuffer, rawPacket: ByteArray, length: Int): ParsedPacket? {
        if (length < 40) return null

        val payloadLength = buffer.getShort(4).toInt() and 0xFFFF
        val nextHeader = buffer.get(6).toInt() and 0xFF
        val hopLimit = buffer.get(7).toInt() and 0xFF

        // Only care about UDP and TCP (skip extension headers for simplicity)
        if (nextHeader != PROTOCOL_UDP && nextHeader != PROTOCOL_TCP) return null

        val sourceIp = ByteArray(16)
        val destIp = ByteArray(16)
        buffer.position(8)
        buffer.get(sourceIp)
        buffer.get(destIp)

        return parseTransport(
            buffer = buffer,
            rawPacket = rawPacket,
            length = length,
            ipVersion = IP_VERSION_6,
            protocol = nextHeader,
            sourceIp = sourceIp,
            destIp = destIp,
            ipHeaderLength = 40,
            totalLength = 40 + payloadLength,
            identification = 0,
            ttl = hopLimit
        )
    }

    private fun parseTransport(
        buffer: ByteBuffer,
        rawPacket: ByteArray,
        length: Int,
        ipVersion: Int,
        protocol: Int,
        sourceIp: ByteArray,
        destIp: ByteArray,
        ipHeaderLength: Int,
        totalLength: Int,
        identification: Int,
        ttl: Int
    ): ParsedPacket? {
        val transportOffset = ipHeaderLength
        if (length < transportOffset + 8) return null

        val sourcePort = buffer.getShort(transportOffset).toInt() and 0xFFFF
        val destPort = buffer.getShort(transportOffset + 2).toInt() and 0xFFFF

        // Only DNS traffic
        if (sourcePort != DNS_PORT && destPort != DNS_PORT) return null

        val transportHeaderLength: Int
        val dnsOffset: Int

        when (protocol) {
            PROTOCOL_UDP -> {
                transportHeaderLength = 8
                dnsOffset = transportOffset + 8
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xFF) shr 4) * 4
                transportHeaderLength = dataOffset
                // TCP DNS has 2-byte length prefix
                dnsOffset = transportOffset + dataOffset + 2
            }
            else -> return null
        }

        if (dnsOffset >= length) return null

        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return null // Minimum DNS header size

        val dnsPayload = ByteArray(dnsLength)
        System.arraycopy(rawPacket, dnsOffset, dnsPayload, 0, dnsLength)

        val packetCopy = ByteArray(length)
        System.arraycopy(rawPacket, 0, packetCopy, 0, length)

        return ParsedPacket(
            ipVersion = ipVersion,
            protocol = protocol,
            sourceIp = sourceIp,
            destIp = destIp,
            sourcePort = sourcePort,
            destPort = destPort,
            dnsPayload = dnsPayload,
            ipHeaderLength = ipHeaderLength,
            transportHeaderLength = transportHeaderLength,
            totalLength = totalLength,
            identification = identification,
            ttl = ttl,
            rawPacket = packetCopy
        )
    }

    /**
     * Build an IP+UDP response packet from a DNS response payload.
     * Swaps source/dest IPs and ports from the original query packet.
     */
    fun buildResponsePacket(
        originalQuery: ParsedPacket,
        dnsResponse: ByteArray
    ): ByteArray {
        return when (originalQuery.ipVersion) {
            IP_VERSION_4 -> buildIPv4UdpResponse(originalQuery, dnsResponse)
            IP_VERSION_6 -> buildIPv6UdpResponse(originalQuery, dnsResponse)
            else -> throw IllegalArgumentException("Unsupported IP version")
        }
    }

    private fun buildIPv4UdpResponse(query: ParsedPacket, dnsResponse: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponse.size

        val packet = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put(0, 0x45.toByte())           // Version=4, IHL=5
        buf.put(1, 0x00.toByte())           // DSCP/ECN
        buf.putShort(2, totalLen.toShort()) // Total length
        buf.putShort(4, (query.identification).toShort()) // ID
        buf.putShort(6, 0x4000.toShort())   // Flags: Don't Fragment
        buf.put(8, 64.toByte())              // TTL
        buf.put(9, PROTOCOL_UDP.toByte())    // Protocol: UDP
        buf.putShort(10, 0)                  // Checksum placeholder

        // Swap src and dst
        buf.position(12)
        buf.put(query.destIp)        // Src = original dst
        buf.put(query.sourceIp)      // Dst = original src

        // IPv4 header checksum
        var checksum = 0L
        for (i in 0 until ipHeaderLen step 2) {
            checksum += (buf.getShort(i).toLong() and 0xFFFF)
        }
        while (checksum shr 16 != 0L) {
            checksum = (checksum and 0xFFFF) + (checksum shr 16)
        }
        buf.putShort(10, (checksum.inv().toShort()))

        // UDP header
        val udpOffset = ipHeaderLen
        buf.putShort(udpOffset, query.destPort.toShort())    // Src port (was dst)
        buf.putShort(udpOffset + 2, query.sourcePort.toShort()) // Dst port (was src)
        buf.putShort(udpOffset + 4, (udpHeaderLen + dnsResponse.size).toShort()) // UDP length
        buf.putShort(udpOffset + 6, 0) // Checksum (optional for IPv4 UDP)

        // DNS payload
        System.arraycopy(dnsResponse, 0, packet, udpOffset + udpHeaderLen, dnsResponse.size)

        return packet
    }

    private fun buildIPv6UdpResponse(query: ParsedPacket, dnsResponse: ByteArray): ByteArray {
        val ipHeaderLen = 40
        val udpHeaderLen = 8
        val udpLen = udpHeaderLen + dnsResponse.size
        val totalLen = ipHeaderLen + udpLen

        val packet = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // IPv6 header
        buf.putInt(0, 0x60000000)               // Version=6, TC=0, Flow Label=0
        buf.putShort(4, udpLen.toShort())        // Payload length
        buf.put(6, PROTOCOL_UDP.toByte())        // Next header: UDP
        buf.put(7, 64.toByte())                   // Hop limit

        // Swap src and dst
        buf.position(8)
        buf.put(query.destIp)        // Src = original dst
        buf.put(query.sourceIp)      // Dst = original src

        // UDP header
        val udpOffset = ipHeaderLen
        buf.putShort(udpOffset, query.destPort.toShort())
        buf.putShort(udpOffset + 2, query.sourcePort.toShort())
        buf.putShort(udpOffset + 4, udpLen.toShort())
        buf.putShort(udpOffset + 6, 0) // Checksum (TODO: compute pseudo-header checksum for IPv6)

        // DNS payload
        System.arraycopy(dnsResponse, 0, packet, udpOffset + udpHeaderLen, dnsResponse.size)

        return packet
    }

    /**
     * Compute the UID for a packet based on source port (for ConnectivityManager lookup).
     */
    @Suppress("UNUSED_PARAMETER")
    fun getUidForPacket(sourcePort: Int): Int {
        // Will be resolved via /proc/net/udp or ConnectivityManager
        return -1
    }
}

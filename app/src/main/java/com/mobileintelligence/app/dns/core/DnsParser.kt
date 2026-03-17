package com.mobileintelligence.app.dns.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Full DNS protocol parser and builder.
 * Supports parsing question/answer sections, building queries and forged responses.
 */
object DnsParser {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_CNAME = 5
    const val TYPE_MX = 15
    const val TYPE_TXT = 16
    const val TYPE_NS = 2
    const val TYPE_SOA = 6
    const val TYPE_PTR = 12
    const val TYPE_SRV = 33
    const val TYPE_HTTPS = 65
    const val CLASS_IN = 1

    const val RCODE_NOERROR = 0
    const val RCODE_NXDOMAIN = 3

    data class DnsHeader(
        val id: Int,
        val flags: Int,
        val qdCount: Int,
        val anCount: Int,
        val nsCount: Int,
        val arCount: Int
    ) {
        val isQuery: Boolean get() = (flags and 0x8000) == 0
        val isResponse: Boolean get() = !isQuery
        val opcode: Int get() = (flags shr 11) and 0xF
        val rcode: Int get() = flags and 0xF
    }

    data class DnsQuestion(
        val name: String,       // e.g. "example.com"
        val type: Int,          // e.g. TYPE_A
        val clazz: Int          // e.g. CLASS_IN
    )

    data class DnsRecord(
        val name: String,
        val type: Int,
        val clazz: Int,
        val ttl: Int,
        val data: ByteArray
    ) {
        fun getIpAddress(): String? {
            return when {
                type == TYPE_A && data.size == 4 -> {
                    "${data[0].toInt() and 0xFF}.${data[1].toInt() and 0xFF}.${data[2].toInt() and 0xFF}.${data[3].toInt() and 0xFF}"
                }
                type == TYPE_AAAA && data.size == 16 -> {
                    val sb = StringBuilder()
                    for (i in 0 until 16 step 2) {
                        if (sb.isNotEmpty()) sb.append(':')
                        sb.append(String.format("%02x%02x", data[i], data[i + 1]))
                    }
                    sb.toString()
                }
                else -> null
            }
        }
    }

    data class DnsMessage(
        val header: DnsHeader,
        val questions: List<DnsQuestion>,
        val answers: List<DnsRecord>,
        val authorities: List<DnsRecord>,
        val additionals: List<DnsRecord>,
        val rawBytes: ByteArray
    ) {
        val primaryDomain: String get() = questions.firstOrNull()?.name ?: ""
        val primaryType: Int get() = questions.firstOrNull()?.type ?: TYPE_A
    }

    /**
     * Parse a raw DNS message.
     */
    fun parse(data: ByteArray): DnsMessage? {
        if (data.size < 12) return null

        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            val header = DnsHeader(
                id = buf.getShort(0).toInt() and 0xFFFF,
                flags = buf.getShort(2).toInt() and 0xFFFF,
                qdCount = buf.getShort(4).toInt() and 0xFFFF,
                anCount = buf.getShort(6).toInt() and 0xFFFF,
                nsCount = buf.getShort(8).toInt() and 0xFFFF,
                arCount = buf.getShort(10).toInt() and 0xFFFF
            )

            var offset = 12
            val questions = mutableListOf<DnsQuestion>()
            for (i in 0 until header.qdCount) {
                val (name, newOffset) = readDomainName(data, offset)
                offset = newOffset
                if (offset + 4 > data.size) break
                val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                val clazz = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
                offset += 4
                questions.add(DnsQuestion(name, type, clazz))
            }

            val answers = readRecords(data, offset, header.anCount)
            offset = advanceRecords(data, offset, header.anCount)

            val authorities = readRecords(data, offset, header.nsCount)
            offset = advanceRecords(data, offset, header.nsCount)

            val additionals = readRecords(data, offset, header.arCount)

            DnsMessage(header, questions, answers, authorities, additionals, data.copyOf())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read a DNS domain name, handling compression pointers.
     */
    fun readDomainName(data: ByteArray, startOffset: Int): Pair<String, Int> {
        val parts = mutableListOf<String>()
        var offset = startOffset
        var jumped = false
        var jumpedOffset = -1
        var safetyCounter = 0

        while (offset < data.size && safetyCounter++ < 128) {
            val len = data[offset].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (!jumped) offset++
                    else offset = jumpedOffset
                    break
                }
                (len and 0xC0) == 0xC0 -> {
                    // Compression pointer
                    if (offset + 1 >= data.size) break
                    val pointer = ((len and 0x3F) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    if (!jumped) {
                        jumpedOffset = offset + 2
                        jumped = true
                    }
                    offset = pointer
                }
                else -> {
                    offset++
                    if (offset + len > data.size) break
                    parts.add(String(data, offset, len, StandardCharsets.UTF_8))
                    offset += len
                }
            }
        }

        val name = parts.joinToString(".").lowercase()
        return name to offset
    }

    private fun readRecords(data: ByteArray, startOffset: Int, count: Int): List<DnsRecord> {
        val records = mutableListOf<DnsRecord>()
        var offset = startOffset

        for (i in 0 until count) {
            if (offset >= data.size) break
            try {
                val (name, newOffset) = readDomainName(data, offset)
                offset = newOffset
                if (offset + 10 > data.size) break

                val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                val clazz = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
                val ttl = ((data[offset + 4].toInt() and 0xFF) shl 24) or
                        ((data[offset + 5].toInt() and 0xFF) shl 16) or
                        ((data[offset + 6].toInt() and 0xFF) shl 8) or
                        (data[offset + 7].toInt() and 0xFF)
                val rdLength = ((data[offset + 8].toInt() and 0xFF) shl 8) or (data[offset + 9].toInt() and 0xFF)
                offset += 10

                if (offset + rdLength > data.size) break
                val rdata = ByteArray(rdLength)
                System.arraycopy(data, offset, rdata, 0, rdLength)
                offset += rdLength

                records.add(DnsRecord(name, type, clazz, ttl, rdata))
            } catch (e: Exception) {
                break
            }
        }
        return records
    }

    private fun advanceRecords(data: ByteArray, startOffset: Int, count: Int): Int {
        var offset = startOffset
        for (i in 0 until count) {
            if (offset >= data.size) break
            try {
                val (_, newOffset) = readDomainName(data, offset)
                offset = newOffset
                if (offset + 10 > data.size) break
                val rdLength = ((data[offset + 8].toInt() and 0xFF) shl 8) or (data[offset + 9].toInt() and 0xFF)
                offset += 10 + rdLength
            } catch (e: Exception) {
                break
            }
        }
        return offset
    }

    /**
     * Normalize a domain name for matching.
     */
    fun normalizeDomain(domain: String): String {
        return domain.lowercase().trimEnd('.')
    }

    /**
     * Encode a domain name to DNS wire format.
     */
    fun encodeDomainName(domain: String): ByteArray {
        val parts = domain.split('.')
        val result = mutableListOf<Byte>()
        for (part in parts) {
            if (part.isEmpty()) continue
            result.add(part.length.toByte())
            result.addAll(part.toByteArray(StandardCharsets.UTF_8).toList())
        }
        result.add(0) // Terminator
        return result.toByteArray()
    }

    /**
     * Build a forged DNS response for blocked domains.
     */
    fun buildBlockedResponse(
        query: DnsMessage,
        blockMode: BlockMode
    ): ByteArray {
        return when (blockMode) {
            BlockMode.NXDOMAIN -> buildNxdomainResponse(query)
            BlockMode.ZERO_IP -> buildZeroIpResponse(query)
            BlockMode.ZERO_IPV6 -> buildZeroIpv6Response(query)
        }
    }

    enum class BlockMode {
        NXDOMAIN,
        ZERO_IP,
        ZERO_IPV6
    }

    private fun buildNxdomainResponse(query: DnsMessage): ByteArray {
        val buf = ByteBuffer.allocate(query.rawBytes.size + 64).order(ByteOrder.BIG_ENDIAN)

        // Copy original question section
        val questionBytes = buildQuestionSection(query.questions)

        val flags = 0x8183 // Response, Recursion Desired, Recursion Available, NXDOMAIN
        buf.putShort(query.header.id.toShort())
        buf.putShort(flags.toShort())
        buf.putShort(query.header.qdCount.toShort())
        buf.putShort(0) // ANCOUNT
        buf.putShort(0) // NSCOUNT
        buf.putShort(0) // ARCOUNT
        buf.put(questionBytes)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    private fun buildZeroIpResponse(query: DnsMessage): ByteArray {
        val questionBytes = buildQuestionSection(query.questions)
        val answerBytes = buildAnswerRecord(query.questions.firstOrNull(), TYPE_A, byteArrayOf(0, 0, 0, 0))

        val flags = 0x8180 // Response, RD, RA, NOERROR
        val buf = ByteBuffer.allocate(12 + questionBytes.size + answerBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(query.header.id.toShort())
        buf.putShort(flags.toShort())
        buf.putShort(query.header.qdCount.toShort())
        buf.putShort(1) // ANCOUNT
        buf.putShort(0) // NSCOUNT
        buf.putShort(0) // ARCOUNT
        buf.put(questionBytes)
        buf.put(answerBytes)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    private fun buildZeroIpv6Response(query: DnsMessage): ByteArray {
        val questionBytes = buildQuestionSection(query.questions)
        val answerBytes = buildAnswerRecord(
            query.questions.firstOrNull(),
            TYPE_AAAA,
            ByteArray(16) // All zeros = ::
        )

        val flags = 0x8180
        val buf = ByteBuffer.allocate(12 + questionBytes.size + answerBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(query.header.id.toShort())
        buf.putShort(flags.toShort())
        buf.putShort(query.header.qdCount.toShort())
        buf.putShort(1)
        buf.putShort(0)
        buf.putShort(0)
        buf.put(questionBytes)
        buf.put(answerBytes)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    private fun buildQuestionSection(questions: List<DnsQuestion>): ByteArray {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        for (q in questions) {
            buf.put(encodeDomainName(q.name))
            buf.putShort(q.type.toShort())
            buf.putShort(q.clazz.toShort())
        }
        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    private fun buildAnswerRecord(
        question: DnsQuestion?,
        type: Int,
        rdata: ByteArray,
        ttl: Int = 300
    ): ByteArray {
        val domain = question?.name ?: ""
        val nameBytes = encodeDomainName(domain)
        val buf = ByteBuffer.allocate(nameBytes.size + 10 + rdata.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(nameBytes)
        buf.putShort(type.toShort())
        buf.putShort(CLASS_IN.toShort())
        buf.putInt(ttl)
        buf.putShort(rdata.size.toShort())
        buf.put(rdata)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    /**
     * Get human-readable record type name.
     */
    fun typeName(type: Int): String = when (type) {
        TYPE_A -> "A"
        TYPE_AAAA -> "AAAA"
        TYPE_CNAME -> "CNAME"
        TYPE_MX -> "MX"
        TYPE_TXT -> "TXT"
        TYPE_NS -> "NS"
        TYPE_SOA -> "SOA"
        TYPE_PTR -> "PTR"
        TYPE_SRV -> "SRV"
        TYPE_HTTPS -> "HTTPS"
        else -> "TYPE$type"
    }
}

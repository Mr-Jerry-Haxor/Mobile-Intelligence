package com.mobileintelligence.app.engine.dns

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects DNS-over-HTTPS (DoH) endpoint domains.
 *
 * Many apps bypass local DNS filtering by using their own DoH resolvers.
 * This detector maintains a comprehensive list of known DoH endpoints
 * and provides pattern-based detection for unknown ones.
 *
 * Detection without MITM:
 * - Known DoH domain list (dns.google, cloudflare-dns.com, etc.)
 * - Pattern matching for common DoH URL structures
 * - HTTPS record type (TYPE 65) detection as DoH indicator
 * - Connection counting to known DoH IPs
 */
class DoHDetector {

    companion object {
        private const val TAG = "DoHDetector"

        /**
         * Comprehensive list of known DoH endpoints.
         * Updated from public DNS resolver lists + browser default configs.
         */
        val KNOWN_DOH_DOMAINS = setOf(
            // Google
            "dns.google",
            "dns.google.com",
            "dns64.dns.google",

            // Cloudflare
            "cloudflare-dns.com",
            "one.one.one.one",
            "1dot1dot1dot1.cloudflare-dns.com",
            "dns.cloudflare.com",
            "family.cloudflare-dns.com",
            "security.cloudflare-dns.com",

            // Quad9
            "dns.quad9.net",
            "dns9.quad9.net",
            "dns10.quad9.net",
            "dns11.quad9.net",

            // Mozilla / Firefox
            "mozilla.cloudflare-dns.com",
            "trr.dns.nextdns.io",
            "firefox.dns.nextdns.io",

            // NextDNS
            "dns.nextdns.io",
            "anycast.dns.nextdns.io",

            // AdGuard
            "dns.adguard.com",
            "dns-unfiltered.adguard.com",
            "dns-family.adguard.com",

            // OpenDNS / Cisco
            "doh.opendns.com",
            "doh.familyshield.opendns.com",
            "dns.umbrella.com",

            // Comcast
            "doh.xfinity.com",

            // CleanBrowsing
            "doh.cleanbrowsing.org",
            "family-filter-dns.cleanbrowsing.org",
            "adult-filter-dns.cleanbrowsing.org",

            // Mullvad
            "doh.mullvad.net",
            "dns.mullvad.net",
            "adblock.doh.mullvad.net",

            // Control D
            "freedns.controld.com",

            // LibreDNS
            "doh.libredns.gr",

            // Digitale Gesellschaft
            "dns.digitale-gesellschaft.ch",

            // DNS.SB
            "doh.dns.sb",

            // DNS0.eu
            "dns0.eu",
            "open.dns0.eu",
            "kids.dns0.eu",

            // AliDNS
            "dns.alidns.com",

            // 360 Secure DNS
            "doh.360.cn",

            // Tencent
            "doh.pub",
            "sm2.doh.pub",

            // TWNIC
            "dns.twnic.tw",

            // Apple Private Relay (iCloud+)
            "mask.icloud.com",
            "mask-h2.icloud.com",
            "mask-api.icloud.com",

            // Rethink DNS
            "basic.rethinkdns.com",
            "max.rethinkdns.com",
            "sky.rethinkdns.com"
        )

        /**
         * Known DoH IP addresses (for detecting IP-based DoH).
         */
        val KNOWN_DOH_IPS = setOf(
            // Google
            "8.8.8.8", "8.8.4.4",
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            // Cloudflare
            "1.1.1.1", "1.0.0.1",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            // Quad9
            "9.9.9.9", "149.112.112.112",
            // AdGuard
            "94.140.14.14", "94.140.15.15",
            // CleanBrowsing
            "185.228.168.168", "185.228.169.168"
        )

        /** Patterns that indicate DoH URL paths. */
        val DOH_PATH_PATTERNS = listOf(
            "dns-query",
            "resolve",
            "dns-api",
            "doh"
        )
    }

    // Track per-app DoH attempts
    private val appDohAttempts = ConcurrentHashMap<String, Int>()

    // Track all detected DoH domains (known + discovered)
    private val detectedDohDomains = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(KNOWN_DOH_DOMAINS)
    }

    /**
     * Check if a domain is a known DoH endpoint.
     */
    fun isDoHEndpoint(domain: String): Boolean {
        val normalized = domain.lowercase().trimEnd('.')

        // Direct match
        if (detectedDohDomains.contains(normalized)) return true

        // Subdomain match (e.g., "abc123.dns.nextdns.io" matches "dns.nextdns.io")
        for (known in detectedDohDomains) {
            if (normalized.endsWith(".$known")) return true
        }

        // Pattern-based detection
        if (isLikelyDoHByPattern(normalized)) {
            detectedDohDomains.add(normalized)
            Log.d(TAG, "Pattern-detected DoH: $normalized")
            return true
        }

        return false
    }

    /**
     * Check if a DNS record type (65 = HTTPS/SVCB) suggests DoH capability.
     */
    fun isDoHIndicatorRecordType(recordType: Int): Boolean {
        return recordType == 65 // HTTPS/SVCB record type
    }

    /**
     * Record a DoH attempt for tracking per-app DoH usage.
     */
    fun recordDoHAttempt(appPackage: String, domain: String) {
        appDohAttempts.merge(appPackage, 1) { old, new -> old + new }
        detectedDohDomains.add(domain.lowercase().trimEnd('.'))
    }

    /**
     * Get DoH attempt count for a specific app.
     */
    fun getDoHAttemptsForApp(appPackage: String): Int =
        appDohAttempts[appPackage] ?: 0

    /**
     * Get all apps that have attempted DoH with their attempt counts.
     */
    fun getAllDoHAttempts(): Map<String, Int> =
        appDohAttempts.toMap()

    /**
     * Get total number of known/detected DoH endpoints.
     */
    fun getKnownEndpointCount(): Int = detectedDohDomains.size

    /**
     * Check if an IP is a known DoH resolver IP.
     */
    fun isDoHIp(ip: String): Boolean = KNOWN_DOH_IPS.contains(ip)

    /**
     * Heuristic pattern-based DoH detection.
     */
    private fun isLikelyDoHByPattern(domain: String): Boolean {
        // Domains starting with "dns" or "doh"
        val parts = domain.split(".")
        if (parts.isEmpty()) return false

        val firstLabel = parts[0]
        if (firstLabel == "dns" || firstLabel == "doh" || firstLabel == "dot") return true

        // Contains "dns-query" or similar patterns
        if (domain.contains("dns-query") || domain.contains("dns-api")) return true

        // NextDNS pattern: *.dns.nextdns.io
        if (domain.endsWith(".dns.nextdns.io")) return true

        // Rethink pattern: *.rethinkdns.com
        if (domain.endsWith(".rethinkdns.com")) return true

        return false
    }

    /**
     * Reset all DoH tracking data.
     */
    fun reset() {
        appDohAttempts.clear()
        detectedDohDomains.clear()
        detectedDohDomains.addAll(KNOWN_DOH_DOMAINS)
    }
}

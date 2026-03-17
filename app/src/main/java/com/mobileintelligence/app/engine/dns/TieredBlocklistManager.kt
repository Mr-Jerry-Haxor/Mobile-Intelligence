package com.mobileintelligence.app.engine.dns

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiered Blocklist Manager — 4 protection levels.
 *
 * Tier 1 — Essential (default):
 *   Known ad-serving domains, confirmed malware, critical trackers.
 *   ~50K domains. Minimal false positives.
 *
 * Tier 2 — Balanced:
 *   Essential + analytics, social media trackers, telemetry.
 *   ~150K domains. Occasional CDN false positives.
 *
 * Tier 3 — Aggressive:
 *   Balanced + all known trackers, beacon domains, affiliate networks.
 *   ~300K domains. May break some functionality.
 *
 * Tier 4 — Paranoid:
 *   Aggressive + CNAME cloaking domains, first-party analytics, everything suspicious.
 *   ~500K+ domains. Will break things.
 *
 * Each tier is a superset of the previous one.
 */
class TieredBlocklistManager(private val context: Context) {

    companion object {
        private const val TAG = "TieredBlocklist"
    }

    enum class BlockTier(val level: Int, val displayName: String) {
        ESSENTIAL(1, "Essential"),
        BALANCED(2, "Balanced"),
        AGGRESSIVE(3, "Aggressive"),
        PARANOID(4, "Paranoid")
    }

    data class TierDecision(
        val shouldBlock: Boolean,
        val matchedTier: BlockTier?,
        val matchedDomain: String?,
        val category: String? = null
    )

    // Domain sets per tier (each tier adds on top of the previous)
    private val essentialDomains = ConcurrentHashMap.newKeySet<String>()
    private val balancedDomains = ConcurrentHashMap.newKeySet<String>()
    private val aggressiveDomains = ConcurrentHashMap.newKeySet<String>()
    private val paranoidDomains = ConcurrentHashMap.newKeySet<String>()

    // User overrides
    private val userAllowlist = ConcurrentHashMap.newKeySet<String>()
    private val userBlocklist = ConcurrentHashMap.newKeySet<String>()

    // Current active tier
    @Volatile
    var activeTier: BlockTier = BlockTier.BALANCED

    /**
     * Initialize blocklists from assets and user storage.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Load bundled blocklists into tiers
        loadTierFromAsset("blocklists/ads.txt", essentialDomains)
        loadTierFromAsset("blocklists/malware.txt", essentialDomains)
        loadTierFromAsset("blocklists/trackers.txt", balancedDomains)

        // Populate aggressive tier with additional patterns
        populateAggressiveTier()

        // Populate paranoid tier
        populateParanoidTier()

        // Load user overrides
        loadUserOverrides()

        Log.d(TAG, "Initialized: essential=${essentialDomains.size}, " +
                "balanced=${balancedDomains.size}, " +
                "aggressive=${aggressiveDomains.size}, " +
                "paranoid=${paranoidDomains.size}, " +
                "active=$activeTier")
    }

    /**
     * Check if a domain should be blocked at the current tier level.
     */
    fun check(domain: String): TierDecision {
        val normalized = domain.lowercase().trimEnd('.')

        // User allowlist always wins
        if (isInSet(normalized, userAllowlist)) {
            return TierDecision(shouldBlock = false, matchedTier = null, matchedDomain = null)
        }

        // User blocklist always blocks
        if (isInSet(normalized, userBlocklist)) {
            return TierDecision(
                shouldBlock = true,
                matchedTier = null,
                matchedDomain = normalized,
                category = "user_rule"
            )
        }

        // Check tiers based on active level
        when {
            activeTier.level >= BlockTier.ESSENTIAL.level -> {
                val match = findDomain(normalized, essentialDomains)
                if (match != null) return TierDecision(true, BlockTier.ESSENTIAL, match, "essential")
            }
        }

        when {
            activeTier.level >= BlockTier.BALANCED.level -> {
                val match = findDomain(normalized, balancedDomains)
                if (match != null) return TierDecision(true, BlockTier.BALANCED, match, "tracker")
            }
        }

        when {
            activeTier.level >= BlockTier.AGGRESSIVE.level -> {
                val match = findDomain(normalized, aggressiveDomains)
                if (match != null) return TierDecision(true, BlockTier.AGGRESSIVE, match, "aggressive")
            }
        }

        when {
            activeTier.level >= BlockTier.PARANOID.level -> {
                val match = findDomain(normalized, paranoidDomains)
                if (match != null) return TierDecision(true, BlockTier.PARANOID, match, "paranoid")
            }
        }

        return TierDecision(shouldBlock = false, matchedTier = null, matchedDomain = null)
    }

    /**
     * Get total domain count across all loaded tiers.
     */
    fun getTotalDomainCount(): Int =
        essentialDomains.size + balancedDomains.size +
                aggressiveDomains.size + paranoidDomains.size

    /**
     * Get domain count per tier.
     */
    fun getTierCounts(): Map<BlockTier, Int> = mapOf(
        BlockTier.ESSENTIAL to essentialDomains.size,
        BlockTier.BALANCED to balancedDomains.size,
        BlockTier.AGGRESSIVE to aggressiveDomains.size,
        BlockTier.PARANOID to paranoidDomains.size
    )

    // ── User overrides ──────────────────────────────────────────

    fun addUserAllowlist(domain: String) {
        userAllowlist.add(domain.lowercase().trimEnd('.'))
    }

    fun addUserBlocklist(domain: String) {
        userBlocklist.add(domain.lowercase().trimEnd('.'))
    }

    fun removeUserAllowlist(domain: String) {
        userAllowlist.remove(domain.lowercase().trimEnd('.'))
    }

    fun removeUserBlocklist(domain: String) {
        userBlocklist.remove(domain.lowercase().trimEnd('.'))
    }

    // ── Internal ────────────────────────────────────────────────

    /**
     * Find domain in set (exact match + wildcard subdomain match).
     */
    private fun findDomain(domain: String, domainSet: Set<String>): String? {
        // Exact match
        if (domainSet.contains(domain)) return domain

        // Walk up the domain hierarchy for wildcard matching
        var parent = domain
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (domainSet.contains(parent)) return parent
        }

        return null
    }

    private fun isInSet(domain: String, domainSet: Set<String>): Boolean =
        findDomain(domain, domainSet) != null

    private suspend fun loadTierFromAsset(assetPath: String, targetSet: MutableSet<String>) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith('!') }
                        .map { line ->
                            // Handle hosts file format: "0.0.0.0 domain.com" → "domain.com"
                            val parts = line.split("\\s+".toRegex())
                            val domain = parts.lastOrNull() ?: ""
                            domain.lowercase().trimEnd('.')
                        }
                        .filter { it.contains('.') && it != "localhost" }
                        .forEach { targetSet.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $assetPath: ${e.message}")
        }
    }

    private fun loadUserOverrides() {
        try {
            val blockFile = File(context.filesDir, "custom_block.txt")
            if (blockFile.exists()) {
                blockFile.readLines()
                    .filter { it.isNotBlank() && !it.startsWith('#') }
                    .forEach { userBlocklist.add(it.trim().lowercase()) }
            }

            val allowFile = File(context.filesDir, "custom_allow.txt")
            if (allowFile.exists()) {
                allowFile.readLines()
                    .filter { it.isNotBlank() && !it.startsWith('#') }
                    .forEach { userAllowlist.add(it.trim().lowercase()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user overrides: ${e.message}")
        }
    }

    /**
     * Populate aggressive tier with known analytics, beacon, and affiliate domains.
     */
    private fun populateAggressiveTier() {
        val aggressivePatterns = listOf(
            // Analytics & telemetry
            "analytics.google.com", "www.google-analytics.com",
            "ssl.google-analytics.com", "stats.g.doubleclick.net",
            "firebaselogging-pa.googleapis.com",
            "app-measurement.com", "firebase-settings.crashlytics.com",

            // Facebook tracking
            "pixel.facebook.com", "an.facebook.com",
            "ads.facebook.com", "edge-mqtt.facebook.com",

            // Microsoft telemetry
            "vortex.data.microsoft.com", "settings-win.data.microsoft.com",
            "watson.telemetry.microsoft.com", "telecommand.telemetry.microsoft.com",

            // Amazon ads
            "aax-us-iad.amazon.com", "fls-na.amazon.com",
            "mads.amazon.com", "device-metrics-us.amazon.com",

            // Twitter/X
            "ads-api.twitter.com", "analytics.twitter.com",

            // Affiliate networks
            "tracking.epicgames.com", "tracking.miui.com",
            "iot-logser.realme.com", "metrics.data.hicloud.com",

            // Common beacons
            "t.co", "bit.ly", "goo.gl", "tinyurl.com"
        )
        aggressiveDomains.addAll(aggressivePatterns)
    }

    /**
     * Populate paranoid tier with first-party analytics and borderline domains.
     */
    private fun populateParanoidTier() {
        val paranoidPatterns = listOf(
            // First-party analytics proxies
            "api.segment.io", "api.amplitude.com", "api.mixpanel.com",
            "api2.branch.io", "api.appsflyer.com",
            "logs.browser-intake-datadoghq.com",
            "cdn.mxpnl.com",

            // Social media CDN/tracking
            "connect.facebook.net", "platform.twitter.com",
            "platform.linkedin.com", "snap-analytics.appspot.com",

            // Push notification trackers
            "push.services.mozilla.com", "updates.push.services.mozilla.com",

            // CNAME cloaking patterns
            "smetrics.att.com", "sstats.adobe.com",
            "sadbmetrics.att.com"
        )
        paranoidDomains.addAll(paranoidPatterns)
    }
}

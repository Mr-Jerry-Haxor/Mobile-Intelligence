package com.mobileintelligence.app.dns.filter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * High-performance domain filtering engine.
 * Uses HashSet for O(1) lookups against blocklists.
 * Supports allowlists, user rules, regex rules, and category lists.
 *
 * Priority order:
 * 1. Allowlist (always allow)
 * 2. User block rules
 * 3. Regex rules
 * 4. Category blocklists (ads, trackers, malware)
 * 5. Default: allow
 */
class DomainFilter(private val context: Context) {

    companion object {
        private const val TAG = "DomainFilter"
        private const val CUSTOM_BLOCK_FILE = "custom_block.txt"
        private const val CUSTOM_ALLOW_FILE = "custom_allow.txt"
    }

    enum class FilterResult {
        ALLOWED,
        BLOCKED_ADS,
        BLOCKED_TRACKER,
        BLOCKED_MALWARE,
        BLOCKED_USER_RULE,
        BLOCKED_REGEX,
        BLOCKED_CUSTOM_LIST,
        ALLOWED_WHITELIST
    }

    data class FilterDecision(
        val result: FilterResult,
        val matchedRule: String? = null
    ) {
        val isBlocked: Boolean
            get() = result != FilterResult.ALLOWED && result != FilterResult.ALLOWED_WHITELIST
    }

    // Thread-safe domain sets
    private val allowlist = ConcurrentHashMap.newKeySet<String>()
    private val userBlocklist = ConcurrentHashMap.newKeySet<String>()
    private val adsBlocklist = ConcurrentHashMap.newKeySet<String>()
    private val trackersBlocklist = ConcurrentHashMap.newKeySet<String>()
    private val malwareBlocklist = ConcurrentHashMap.newKeySet<String>()
    private val customBlocklistDomains = ConcurrentHashMap.newKeySet<String>()
    private val regexRules = mutableListOf<Pattern>()
    private val regexMutex = Mutex()

    // Per-app bypass settings
    private val bypassedApps = ConcurrentHashMap.newKeySet<String>()
    private val blockOnlyApps = ConcurrentHashMap.newKeySet<String>()

    // Category enable/disable
    @Volatile var adsEnabled = true
    @Volatile var trackersEnabled = true
    @Volatile var malwareEnabled = true

    /**
     * Load all blocklists from assets and user files.
     */
    suspend fun loadAll() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading blocklists...")

        // Load bundled lists from assets
        loadAssetList("blocklists/ads.txt", adsBlocklist)
        loadAssetList("blocklists/trackers.txt", trackersBlocklist)
        loadAssetList("blocklists/malware.txt", malwareBlocklist)

        // Load user lists from internal storage
        loadUserList(CUSTOM_BLOCK_FILE, userBlocklist)
        loadUserList(CUSTOM_ALLOW_FILE, allowlist)

        // Load regex rules
        loadRegexRules()

        Log.d(TAG, "Loaded: ads=${adsBlocklist.size}, trackers=${trackersBlocklist.size}, " +
                "malware=${malwareBlocklist.size}, user_block=${userBlocklist.size}, " +
                "allow=${allowlist.size}, regex=${regexRules.size}")
    }

    /**
     * Check if a domain should be blocked.
     * O(1) average for hash lookups, linear for regex rules.
     */
    suspend fun check(domain: String, appPackage: String? = null): FilterDecision {
        val normalized = normalizeDomain(domain)

        // Check per-app bypass
        if (appPackage != null && bypassedApps.contains(appPackage)) {
            return FilterDecision(FilterResult.ALLOWED)
        }

        // 1. Allowlist always wins
        if (allowlist.contains(normalized) || matchWildcard(normalized, allowlist)) {
            return FilterDecision(FilterResult.ALLOWED_WHITELIST, normalized)
        }

        // 2. User rules (highest block priority)
        if (userBlocklist.contains(normalized) || matchWildcard(normalized, userBlocklist)) {
            return FilterDecision(FilterResult.BLOCKED_USER_RULE, normalized)
        }

        // 3. Regex rules
        regexMutex.withLock {
            for (pattern in regexRules) {
                if (pattern.matcher(normalized).find()) {
                    return FilterDecision(FilterResult.BLOCKED_REGEX, pattern.pattern())
                }
            }
        }

        // 4. Category lists
        if (malwareEnabled && (malwareBlocklist.contains(normalized) || matchWildcard(normalized, malwareBlocklist))) {
            return FilterDecision(FilterResult.BLOCKED_MALWARE, normalized)
        }
        if (trackersEnabled && (trackersBlocklist.contains(normalized) || matchWildcard(normalized, trackersBlocklist))) {
            return FilterDecision(FilterResult.BLOCKED_TRACKER, normalized)
        }
        if (adsEnabled && (adsBlocklist.contains(normalized) || matchWildcard(normalized, adsBlocklist))) {
            return FilterDecision(FilterResult.BLOCKED_ADS, normalized)
        }

        // 5. Custom blocklist domains
        if (customBlocklistDomains.contains(normalized) || matchWildcard(normalized, customBlocklistDomains)) {
            return FilterDecision(FilterResult.BLOCKED_CUSTOM_LIST, normalized)
        }

        // 6. Default allow
        return FilterDecision(FilterResult.ALLOWED)
    }

    /**
     * Match domain against wildcard entries.
     * Checks all parent domains (e.g., sub.example.com → example.com → com).
     */
    private fun matchWildcard(domain: String, set: MutableSet<String>): Boolean {
        val parts = domain.split('.')
        for (i in 1 until parts.size) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (set.contains(parent)) return true
        }
        return false
    }

    /**
     * Add domain to user blocklist.
     */
    suspend fun addUserBlock(domain: String) {
        val normalized = normalizeDomain(domain)
        userBlocklist.add(normalized)
        saveUserList(CUSTOM_BLOCK_FILE, userBlocklist)
    }

    /**
     * Remove domain from user blocklist.
     */
    suspend fun removeUserBlock(domain: String) {
        val normalized = normalizeDomain(domain)
        userBlocklist.remove(normalized)
        saveUserList(CUSTOM_BLOCK_FILE, userBlocklist)
    }

    /**
     * Add domain to allowlist.
     */
    suspend fun addAllowlist(domain: String) {
        val normalized = normalizeDomain(domain)
        allowlist.add(normalized)
        saveUserList(CUSTOM_ALLOW_FILE, allowlist)
    }

    /**
     * Remove domain from allowlist.
     */
    suspend fun removeAllowlist(domain: String) {
        val normalized = normalizeDomain(domain)
        allowlist.remove(normalized)
        saveUserList(CUSTOM_ALLOW_FILE, allowlist)
    }

    /**
     * Add a regex rule.
     */
    suspend fun addRegexRule(pattern: String) = regexMutex.withLock {
        try {
            regexRules.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
            saveRegexRules()
        } catch (e: Exception) {
            Log.w(TAG, "Invalid regex: $pattern")
        }
    }

    /**
     * Remove a regex rule.
     */
    suspend fun removeRegexRule(pattern: String) = regexMutex.withLock {
        regexRules.removeAll { it.pattern() == pattern }
        saveRegexRules()
    }

    /**
     * Get all regex rules.
     */
    suspend fun getRegexRules(): List<String> = regexMutex.withLock {
        regexRules.map { it.pattern() }
    }

    /**
     * Set per-app bypass.
     */
    fun setAppBypassed(packageName: String, bypassed: Boolean) {
        if (bypassed) bypassedApps.add(packageName) else bypassedApps.remove(packageName)
    }

    /**
     * Check if an app is bypassed.
     */
    fun isAppBypassed(packageName: String): Boolean = bypassedApps.contains(packageName)

    /**
     * Get all bypassed apps.
     */
    fun getBypassedApps(): Set<String> = bypassedApps.toSet()

    /**
     * Get blocklist sizes for UI.
     */
    fun getBlocklistStats(): Map<String, Int> = mapOf(
        "ads" to adsBlocklist.size,
        "trackers" to trackersBlocklist.size,
        "malware" to malwareBlocklist.size,
        "user_block" to userBlocklist.size,
        "allowlist" to allowlist.size,
        "regex" to regexRules.size,
        "custom_lists" to customBlocklistDomains.size
    )

    /**
     * Replace domains from custom blocklists atomically.
     * Called by BlocklistViewModel when blocklists are toggled or refreshed.
     */
    fun replaceCustomBlocklistDomains(domains: Set<String>) {
        customBlocklistDomains.clear()
        customBlocklistDomains.addAll(domains.map { normalizeDomain(it) })
        Log.d(TAG, "Replaced custom blocklist domains: ${customBlocklistDomains.size}")
    }

    /**
     * Get all user-blocked domains.
     */
    fun getUserBlockedDomains(): Set<String> = userBlocklist.toSet()

    /**
     * Get all allowed domains.
     */
    fun getAllowedDomains(): Set<String> = allowlist.toSet()

    /**
     * Replace blocklist contents atomically (for updates).
     */
    suspend fun replaceBlocklist(category: String, domains: Set<String>) = withContext(Dispatchers.IO) {
        val targetSet = when (category) {
            "ads" -> adsBlocklist
            "trackers" -> trackersBlocklist
            "malware" -> malwareBlocklist
            else -> return@withContext
        }
        targetSet.clear()
        targetSet.addAll(domains.map { normalizeDomain(it) })
        Log.d(TAG, "Replaced $category list with ${domains.size} domains")
    }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase().trimEnd('.').trim()
    }

    private fun loadAssetList(assetPath: String, target: MutableSet<String>) {
        try {
            context.assets.open(assetPath).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith('#') && !trimmed.startsWith('!')) {
                        // Handle hosts file format: "0.0.0.0 domain.com" or plain "domain.com"
                        val domain = trimmed.split("\\s+".toRegex()).last()
                        if (domain.contains('.') && domain != "0.0.0.0" && domain != "127.0.0.1") {
                            target.add(normalizeDomain(domain))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load asset: $assetPath", e)
        }
    }

    private fun loadUserList(fileName: String, target: MutableSet<String>) {
        val file = File(context.filesDir, "blocklists/$fileName")
        if (!file.exists()) return
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
                        target.add(normalizeDomain(trimmed))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user list: $fileName", e)
        }
    }

    private suspend fun saveUserList(fileName: String, data: Set<String>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "blocklists")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(data.joinToString("\n"))
    }

    private fun loadRegexRules() {
        val file = File(context.filesDir, "blocklists/regex_rules.txt")
        if (!file.exists()) return
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            regexRules.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE))
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load regex rules", e)
        }
    }

    private suspend fun saveRegexRules() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "blocklists")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "regex_rules.txt")
        file.writeText(regexRules.joinToString("\n") { it.pattern() })
    }
}

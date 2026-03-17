package com.mobileintelligence.app.dns.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dnsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dns_preferences")

/**
 * DataStore preferences for DNS firewall settings.
 */
class DnsPreferences(private val context: Context) {

    companion object {
        // VPN state
        val VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val VPN_AUTO_START = booleanPreferencesKey("vpn_auto_start")

        // Block mode
        val BLOCK_MODE = stringPreferencesKey("block_mode") // "nxdomain", "zero_ip", "zero_ipv6"

        // DNS provider
        val DNS_PROVIDER = stringPreferencesKey("dns_provider") // "cloudflare", "google", "quad9", "custom"
        val CUSTOM_DNS_PRIMARY = stringPreferencesKey("custom_dns_primary")
        val CUSTOM_DNS_SECONDARY = stringPreferencesKey("custom_dns_secondary")

        // Filtering categories
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val BLOCK_TRACKERS = booleanPreferencesKey("block_trackers")
        val BLOCK_MALWARE = booleanPreferencesKey("block_malware")

        // Logging
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")

        // Stats
        val TOTAL_QUERIES_LIFETIME = longPreferencesKey("total_queries_lifetime")
        val TOTAL_BLOCKED_LIFETIME = longPreferencesKey("total_blocked_lifetime")

        // Misc
        val FIRST_ENABLED_TIMESTAMP = longPreferencesKey("first_enabled_timestamp")
        val LAST_BLOCKLIST_UPDATE = longPreferencesKey("last_blocklist_update")
    }

    // ─── VPN State ──────────────────────────────────────────────

    val vpnEnabled: Flow<Boolean> = context.dnsDataStore.data.map { it[VPN_ENABLED] ?: false }
    val vpnAutoStart: Flow<Boolean> = context.dnsDataStore.data.map { it[VPN_AUTO_START] ?: true }

    suspend fun setVpnEnabled(enabled: Boolean) {
        context.dnsDataStore.edit { it[VPN_ENABLED] = enabled }
    }

    suspend fun setVpnAutoStart(autoStart: Boolean) {
        context.dnsDataStore.edit { it[VPN_AUTO_START] = autoStart }
    }

    // ─── Block Mode ─────────────────────────────────────────────

    val blockMode: Flow<String> = context.dnsDataStore.data.map { it[BLOCK_MODE] ?: "zero_ip" }

    suspend fun setBlockMode(mode: String) {
        context.dnsDataStore.edit { it[BLOCK_MODE] = mode }
    }

    // ─── DNS Provider ───────────────────────────────────────────

    val dnsProvider: Flow<String> = context.dnsDataStore.data.map { it[DNS_PROVIDER] ?: "cloudflare" }
    val customDnsPrimary: Flow<String> = context.dnsDataStore.data.map { it[CUSTOM_DNS_PRIMARY] ?: "" }
    val customDnsSecondary: Flow<String> = context.dnsDataStore.data.map { it[CUSTOM_DNS_SECONDARY] ?: "" }

    suspend fun setDnsProvider(provider: String) {
        context.dnsDataStore.edit { it[DNS_PROVIDER] = provider }
    }

    suspend fun setCustomDns(primary: String, secondary: String) {
        context.dnsDataStore.edit {
            it[CUSTOM_DNS_PRIMARY] = primary
            it[CUSTOM_DNS_SECONDARY] = secondary
        }
    }

    // ─── Filtering Categories ───────────────────────────────────

    val blockAds: Flow<Boolean> = context.dnsDataStore.data.map { it[BLOCK_ADS] ?: true }
    val blockTrackers: Flow<Boolean> = context.dnsDataStore.data.map { it[BLOCK_TRACKERS] ?: true }
    val blockMalware: Flow<Boolean> = context.dnsDataStore.data.map { it[BLOCK_MALWARE] ?: true }

    suspend fun setBlockAds(enabled: Boolean) {
        context.dnsDataStore.edit { it[BLOCK_ADS] = enabled }
    }

    suspend fun setBlockTrackers(enabled: Boolean) {
        context.dnsDataStore.edit { it[BLOCK_TRACKERS] = enabled }
    }

    suspend fun setBlockMalware(enabled: Boolean) {
        context.dnsDataStore.edit { it[BLOCK_MALWARE] = enabled }
    }

    // ─── Logging ────────────────────────────────────────────────

    val loggingEnabled: Flow<Boolean> = context.dnsDataStore.data.map { it[LOGGING_ENABLED] ?: true }
    val logRetentionDays: Flow<Int> = context.dnsDataStore.data.map { it[LOG_RETENTION_DAYS] ?: 30 }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.dnsDataStore.edit { it[LOGGING_ENABLED] = enabled }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dnsDataStore.edit { it[LOG_RETENTION_DAYS] = days }
    }

    // ─── Lifetime Stats ─────────────────────────────────────────

    val totalQueriesLifetime: Flow<Long> = context.dnsDataStore.data.map { it[TOTAL_QUERIES_LIFETIME] ?: 0L }
    val totalBlockedLifetime: Flow<Long> = context.dnsDataStore.data.map { it[TOTAL_BLOCKED_LIFETIME] ?: 0L }

    suspend fun incrementLifetimeStats(queries: Long, blocked: Long) {
        context.dnsDataStore.edit {
            it[TOTAL_QUERIES_LIFETIME] = (it[TOTAL_QUERIES_LIFETIME] ?: 0L) + queries
            it[TOTAL_BLOCKED_LIFETIME] = (it[TOTAL_BLOCKED_LIFETIME] ?: 0L) + blocked
        }
    }

    // ─── Misc ───────────────────────────────────────────────────

    val firstEnabledTimestamp: Flow<Long> = context.dnsDataStore.data.map { it[FIRST_ENABLED_TIMESTAMP] ?: 0L }

    suspend fun setFirstEnabledTimestamp(timestamp: Long) {
        context.dnsDataStore.edit {
            if (it[FIRST_ENABLED_TIMESTAMP] == null || it[FIRST_ENABLED_TIMESTAMP] == 0L) {
                it[FIRST_ENABLED_TIMESTAMP] = timestamp
            }
        }
    }

    suspend fun setLastBlocklistUpdate(timestamp: Long) {
        context.dnsDataStore.edit { it[LAST_BLOCKLIST_UPDATE] = timestamp }
    }

    val lastBlocklistUpdate: Flow<Long> = context.dnsDataStore.data.map { it[LAST_BLOCKLIST_UPDATE] ?: 0L }
}

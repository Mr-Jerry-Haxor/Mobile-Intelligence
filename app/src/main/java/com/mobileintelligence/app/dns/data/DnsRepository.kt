package com.mobileintelligence.app.dns.data

import android.content.Context
import com.mobileintelligence.app.dns.core.AppResolver
import com.mobileintelligence.app.dns.data.dao.*
import com.mobileintelligence.app.dns.data.entity.*
import com.mobileintelligence.app.dns.filter.BlocklistManager
import com.mobileintelligence.app.dns.filter.DomainFilter
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository layer for all DNS firewall data.
 * Aggregates data from database, service state, and preferences.
 */
class DnsRepository(private val context: Context) {

    private val database = DnsDatabase.getInstance(context)
    private val preferences = DnsPreferences(context)
    private val appResolver by lazy { AppResolver(context) }
    private val blocklistManager by lazy { BlocklistManager(context) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // DAOs
    private val queryDao = database.dnsQueryDao()
    private val dailyStatsDao = database.dnsDailyStatsDao()
    private val appStatsDao = database.dnsAppStatsDao()
    private val domainStatsDao = database.dnsDomainStatsDao()

    // ─── Today's Date ───────────────────────────────────────────

    private fun todayStr(): String = dateFormat.format(Date())

    // ─── Live Service State ─────────────────────────────────────

    val isVpnRunning: StateFlow<Boolean> = LocalDnsVpnService.isRunning
    val todayBlocked: StateFlow<Int> = LocalDnsVpnService.todayBlocked
    val todayQueries: StateFlow<Int> = LocalDnsVpnService.todayQueries

    // ─── Preferences ────────────────────────────────────────────

    val vpnEnabled: Flow<Boolean> = preferences.vpnEnabled
    val vpnAutoStart: Flow<Boolean> = preferences.vpnAutoStart
    val blockMode: Flow<String> = preferences.blockMode
    val dnsProvider: Flow<String> = preferences.dnsProvider
    val blockAds: Flow<Boolean> = preferences.blockAds
    val blockTrackers: Flow<Boolean> = preferences.blockTrackers
    val blockMalware: Flow<Boolean> = preferences.blockMalware
    val loggingEnabled: Flow<Boolean> = preferences.loggingEnabled
    val logRetentionDays: Flow<Int> = preferences.logRetentionDays
    val totalQueriesLifetime: Flow<Long> = preferences.totalQueriesLifetime
    val totalBlockedLifetime: Flow<Long> = preferences.totalBlockedLifetime
    val customDnsPrimary: Flow<String> = preferences.customDnsPrimary
    val customDnsSecondary: Flow<String> = preferences.customDnsSecondary

    // ─── Today's Live Stats ─────────────────────────────────────

    fun getTodayTotalQueries(): Flow<Int> = queryDao.getTotalQueriesForDate(todayStr())
    fun getTodayBlockedQueries(): Flow<Int> = queryDao.getBlockedQueriesForDate(todayStr())
    fun getTodayCachedQueries(): Flow<Int> = queryDao.getCachedQueriesForDate(todayStr())
    fun getTodayAvgResponseTime(): Flow<Double?> = queryDao.getAvgResponseTimeForDate(todayStr())

    fun getTodayBlockedPercentage(): Flow<Float> {
        return combine(
            getTodayTotalQueries(),
            getTodayBlockedQueries()
        ) { total, blocked ->
            if (total > 0) (blocked.toFloat() / total * 100f) else 0f
        }
    }

    // ─── Live Query Log ─────────────────────────────────────────

    fun getRecentQueries(limit: Int = 100): Flow<List<DnsQueryEntity>> =
        queryDao.getRecentQueries(limit)

    fun getRecentBlockedQueries(limit: Int = 100): Flow<List<DnsQueryEntity>> =
        queryDao.getRecentBlockedQueries(limit)

    fun searchQueries(query: String): Flow<List<DnsQueryEntity>> =
        queryDao.searchQueries(query)

    // ─── Top Domains ────────────────────────────────────────────

    fun getTodayTopDomains(limit: Int = 20): Flow<List<DnsQueryDao.DomainCount>> =
        queryDao.getTopDomainsForDate(todayStr(), limit)

    fun getTodayTopBlockedDomains(limit: Int = 20): Flow<List<DnsQueryDao.DomainCount>> =
        queryDao.getTopBlockedDomainsForDate(todayStr(), limit)

    // ─── Top Apps ───────────────────────────────────────────────

    fun getTodayTopApps(limit: Int = 20): Flow<List<DnsQueryDao.AppQueryCount>> =
        queryDao.getTopAppsForDate(todayStr(), limit)

    // ─── Historical Stats ───────────────────────────────────────

    fun getDailyStats(limit: Int = 30): Flow<List<DnsDailyStats>> =
        dailyStatsDao.getRecentStats(limit)

    fun getDailyStatsRange(startDate: String, endDate: String): Flow<List<DnsDailyStats>> =
        dailyStatsDao.getStatsRange(startDate, endDate)

    // ─── App Details ────────────────────────────────────────────

    fun getAppStats(dateStr: String): Flow<List<DnsAppStats>> =
        appStatsDao.getForDate(dateStr)

    fun getAppHistory(packageName: String, limit: Int = 30): Flow<List<DnsAppStats>> =
        appStatsDao.getForApp(packageName, limit)

    // ─── Domain Details ─────────────────────────────────────────

    fun getTopBlockedDomains(dateStr: String, limit: Int = 50): Flow<List<DnsDomainStats>> =
        domainStatsDao.getTopBlockedForDate(dateStr, limit)

    // ─── Utility ────────────────────────────────────────────────

    fun getAppLabel(packageName: String): String = appResolver.getAppLabel(packageName)

    fun getBlocklistInfo(): List<BlocklistManager.BlocklistInfo> = blocklistManager.getBlocklistInfo()

    // ─── Settings Mutators ──────────────────────────────────────

    suspend fun setVpnEnabled(enabled: Boolean) = preferences.setVpnEnabled(enabled)
    suspend fun setVpnAutoStart(autoStart: Boolean) = preferences.setVpnAutoStart(autoStart)
    suspend fun setBlockMode(mode: String) = preferences.setBlockMode(mode)
    suspend fun setDnsProvider(provider: String) = preferences.setDnsProvider(provider)
    suspend fun setCustomDns(primary: String, secondary: String) = preferences.setCustomDns(primary, secondary)
    suspend fun setBlockAds(enabled: Boolean) = preferences.setBlockAds(enabled)
    suspend fun setBlockTrackers(enabled: Boolean) = preferences.setBlockTrackers(enabled)
    suspend fun setBlockMalware(enabled: Boolean) = preferences.setBlockMalware(enabled)
    suspend fun setLoggingEnabled(enabled: Boolean) = preferences.setLoggingEnabled(enabled)
    suspend fun setLogRetentionDays(days: Int) = preferences.setLogRetentionDays(days)

    // ─── Data Management ────────────────────────────────────────

    suspend fun clearAllDnsData() {
        queryDao.deleteAll()
        dailyStatsDao.deleteAll()
        appStatsDao.deleteAll()
        domainStatsDao.deleteAll()
    }

    fun triggerBlocklistUpdate() = blocklistManager.triggerImmediateUpdate()
    fun schedulePeriodicBlocklistUpdate() = blocklistManager.schedulePeriodicUpdate()
}

package com.mobileintelligence.app.dns.data.dao

import androidx.room.*
import com.mobileintelligence.app.dns.data.entity.DnsQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsQueryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: DnsQueryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(queries: List<DnsQueryEntity>)

    // ─── Live Query Log ─────────────────────────────────────────

    @Query("SELECT * FROM dns_queries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentQueries(limit: Int = 100): Flow<List<DnsQueryEntity>>

    @Query("SELECT * FROM dns_queries WHERE blocked = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBlockedQueries(limit: Int = 100): Flow<List<DnsQueryEntity>>

    @Query("SELECT * FROM dns_queries WHERE date_str = :dateStr ORDER BY timestamp DESC")
    fun getQueriesForDate(dateStr: String): Flow<List<DnsQueryEntity>>

    @Query("SELECT * FROM dns_queries WHERE domain LIKE '%' || :search || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchQueries(search: String, limit: Int = 200): Flow<List<DnsQueryEntity>>

    // ─── Today's Counters ───────────────────────────────────────

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr")
    fun getTotalQueriesForDate(dateStr: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND blocked = 1")
    fun getBlockedQueriesForDate(dateStr: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND cached = 1")
    fun getCachedQueriesForDate(dateStr: String): Flow<Int>

    @Query("SELECT AVG(response_time_ms) FROM dns_queries WHERE date_str = :dateStr AND response_time_ms > 0")
    fun getAvgResponseTimeForDate(dateStr: String): Flow<Double?>

    // ─── Top Domains ────────────────────────────────────────────

    @Query("""
        SELECT domain, COUNT(*) as cnt, blocked 
        FROM dns_queries 
        WHERE date_str = :dateStr 
        GROUP BY domain 
        ORDER BY cnt DESC 
        LIMIT :limit
    """)
    fun getTopDomainsForDate(dateStr: String, limit: Int = 20): Flow<List<DomainCount>>

    @Query("""
        SELECT domain, COUNT(*) as cnt, blocked 
        FROM dns_queries 
        WHERE date_str = :dateStr AND blocked = 1 
        GROUP BY domain 
        ORDER BY cnt DESC 
        LIMIT :limit
    """)
    fun getTopBlockedDomainsForDate(dateStr: String, limit: Int = 20): Flow<List<DomainCount>>

    // ─── Top Apps ───────────────────────────────────────────────

    @Query("""
        SELECT app_package, COUNT(*) as cnt, SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_cnt
        FROM dns_queries 
        WHERE date_str = :dateStr AND app_package != '' 
        GROUP BY app_package 
        ORDER BY cnt DESC 
        LIMIT :limit
    """)
    fun getTopAppsForDate(dateStr: String, limit: Int = 20): Flow<List<AppQueryCount>>

    // ─── Historical Queries ─────────────────────────────────────

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr")
    suspend fun getTotalQueriesForDateSync(dateStr: String): Int

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND blocked = 1")
    suspend fun getBlockedQueriesForDateSync(dateStr: String): Int

    @Query("SELECT COUNT(DISTINCT domain) FROM dns_queries WHERE date_str = :dateStr")
    suspend fun getUniqueDomainCountForDate(dateStr: String): Int

    @Query("SELECT COUNT(DISTINCT app_package) FROM dns_queries WHERE date_str = :dateStr AND app_package != ''")
    suspend fun getUniqueAppCountForDate(dateStr: String): Int

    @Query("SELECT AVG(response_time_ms) FROM dns_queries WHERE date_str = :dateStr AND response_time_ms > 0")
    suspend fun getAvgResponseTimeForDateSync(dateStr: String): Long?

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND cached = 1")
    suspend fun getCachedCountForDateSync(dateStr: String): Int

    // ─── Block reason counts ────────────────────────────────────

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND block_reason = 'ads'")
    suspend fun getAdsBlockedCount(dateStr: String): Int

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND block_reason = 'tracker'")
    suspend fun getTrackersBlockedCount(dateStr: String): Int

    @Query("SELECT COUNT(*) FROM dns_queries WHERE date_str = :dateStr AND block_reason = 'malware'")
    suspend fun getMalwareBlockedCount(dateStr: String): Int

    // ─── Sync aggregation for stats worker ──────────────────────

    @Query("""
        SELECT app_package, COUNT(*) as cnt, SUM(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked_cnt
        FROM dns_queries 
        WHERE date_str = :dateStr AND app_package != '' 
        GROUP BY app_package 
        ORDER BY cnt DESC 
        LIMIT :limit
    """)
    suspend fun getTopAppsForDateSync(dateStr: String, limit: Int = 100): List<AppQueryCount>

    @Query("SELECT COUNT(DISTINCT domain) FROM dns_queries WHERE date_str = :dateStr AND app_package = :appPackage")
    suspend fun getUniqueDomainCountForAppDate(dateStr: String, appPackage: String): Int

    @Query("SELECT domain FROM dns_queries WHERE date_str = :dateStr AND app_package = :appPackage GROUP BY domain ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getTopDomainForAppDate(dateStr: String, appPackage: String): String?

    @Query("""
        SELECT domain, COUNT(*) as cnt, MAX(CASE WHEN blocked = 1 THEN 1 ELSE 0 END) as blocked
        FROM dns_queries 
        WHERE date_str = :dateStr 
        GROUP BY domain 
        ORDER BY cnt DESC 
        LIMIT :limit
    """)
    suspend fun getTopDomainsForDateSync(dateStr: String, limit: Int = 200): List<DomainCount>

    @Query("SELECT block_reason FROM dns_queries WHERE date_str = :dateStr AND domain = :domain AND blocked = 1 AND block_reason IS NOT NULL LIMIT 1")
    suspend fun getBlockReasonForDomainDate(dateStr: String, domain: String): String?

    // ─── Cleanup ────────────────────────────────────────────────

    @Query("DELETE FROM dns_queries WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM dns_queries")
    suspend fun deleteAll()

    // ─── Hourly aggregation for heatmap ─────────────────────────

    @Query("""
        SELECT CAST((timestamp / 3600000 + :utcOffsetHours) % 24 AS INTEGER) as hour, 
               COUNT(*) as count
        FROM dns_queries 
        WHERE date_str = :dateStr
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getHourlyQueryCountsForDate(dateStr: String, utcOffsetHours: Int = 0): List<HourlyDnsCount>

    // ─── Data classes for aggregate queries ──────────────────────

    data class DomainCount(
        val domain: String,
        val cnt: Int,
        val blocked: Boolean
    )

    data class AppQueryCount(
        @ColumnInfo(name = "app_package") val appPackage: String,
        val cnt: Int,
        @ColumnInfo(name = "blocked_cnt") val blockedCnt: Int
    )

    data class HourlyDnsCount(
        @ColumnInfo(name = "hour") val hour: Int,
        @ColumnInfo(name = "count") val count: Int
    )
}

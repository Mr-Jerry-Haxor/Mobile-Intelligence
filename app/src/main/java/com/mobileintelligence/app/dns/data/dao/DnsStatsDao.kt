package com.mobileintelligence.app.dns.data.dao

import androidx.room.*
import com.mobileintelligence.app.dns.data.entity.DnsAppStats
import com.mobileintelligence.app.dns.data.entity.DnsDailyStats
import com.mobileintelligence.app.dns.data.entity.DnsDomainStats
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsDailyStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DnsDailyStats)

    @Query("SELECT * FROM dns_daily_stats WHERE date_str = :dateStr")
    suspend fun getForDate(dateStr: String): DnsDailyStats?

    @Query("SELECT * FROM dns_daily_stats ORDER BY date_str DESC LIMIT :limit")
    fun getRecentStats(limit: Int = 30): Flow<List<DnsDailyStats>>

    @Query("SELECT * FROM dns_daily_stats WHERE date_str BETWEEN :startDate AND :endDate ORDER BY date_str ASC")
    fun getStatsRange(startDate: String, endDate: String): Flow<List<DnsDailyStats>>

    @Query("SELECT SUM(total_queries) FROM dns_daily_stats WHERE date_str BETWEEN :startDate AND :endDate")
    suspend fun getTotalQueriesInRange(startDate: String, endDate: String): Int?

    @Query("SELECT SUM(blocked_queries) FROM dns_daily_stats WHERE date_str BETWEEN :startDate AND :endDate")
    suspend fun getBlockedQueriesInRange(startDate: String, endDate: String): Int?

    @Query("DELETE FROM dns_daily_stats WHERE date_str < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int

    @Query("DELETE FROM dns_daily_stats")
    suspend fun deleteAll()
}

@Dao
interface DnsAppStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DnsAppStats)

    @Query("SELECT * FROM dns_app_stats WHERE date_str = :dateStr ORDER BY total_queries DESC")
    fun getForDate(dateStr: String): Flow<List<DnsAppStats>>

    @Query("SELECT * FROM dns_app_stats WHERE app_package = :pkg ORDER BY date_str DESC LIMIT :limit")
    fun getForApp(pkg: String, limit: Int = 30): Flow<List<DnsAppStats>>

    @Query("""
        SELECT app_package, SUM(total_queries) as total, SUM(blocked_queries) as blocked
        FROM dns_app_stats 
        WHERE date_str BETWEEN :startDate AND :endDate 
        GROUP BY app_package 
        ORDER BY total DESC 
        LIMIT :limit
    """)
    fun getTopAppsInRange(startDate: String, endDate: String, limit: Int = 20): Flow<List<AppRangeSummary>>

    @Query("DELETE FROM dns_app_stats WHERE date_str < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int

    @Query("DELETE FROM dns_app_stats")
    suspend fun deleteAll()

    data class AppRangeSummary(
        @ColumnInfo(name = "app_package") val appPackage: String,
        val total: Int,
        val blocked: Int
    )
}

@Dao
interface DnsDomainStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DnsDomainStats)

    @Query("SELECT * FROM dns_domain_stats WHERE date_str = :dateStr AND blocked = 1 ORDER BY query_count DESC LIMIT :limit")
    fun getTopBlockedForDate(dateStr: String, limit: Int = 50): Flow<List<DnsDomainStats>>

    @Query("SELECT * FROM dns_domain_stats WHERE date_str = :dateStr ORDER BY query_count DESC LIMIT :limit")
    fun getTopDomainsForDate(dateStr: String, limit: Int = 50): Flow<List<DnsDomainStats>>

    @Query("""
        SELECT domain, SUM(query_count) as total_queries, MAX(blocked) as is_blocked
        FROM dns_domain_stats
        WHERE date_str BETWEEN :startDate AND :endDate AND blocked = 1
        GROUP BY domain
        ORDER BY total_queries DESC
        LIMIT :limit
    """)
    fun getTopBlockedInRange(startDate: String, endDate: String, limit: Int = 50): Flow<List<DomainRangeSummary>>

    @Query("DELETE FROM dns_domain_stats WHERE date_str < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String): Int

    @Query("DELETE FROM dns_domain_stats")
    suspend fun deleteAll()

    data class DomainRangeSummary(
        val domain: String,
        @ColumnInfo(name = "total_queries") val totalQueries: Int,
        @ColumnInfo(name = "is_blocked") val isBlocked: Boolean
    )
}

package com.mobileintelligence.app.dns.data.entity

import androidx.room.*

/**
 * Daily DNS statistics summary.
 * One row per day for historical analytics.
 */
@Entity(
    tableName = "dns_daily_stats",
    indices = [
        Index(value = ["date_str"], unique = true)
    ]
)
data class DnsDailyStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date_str")
    val dateStr: String,              // "2026-02-28"

    @ColumnInfo(name = "total_queries")
    val totalQueries: Int = 0,

    @ColumnInfo(name = "blocked_queries")
    val blockedQueries: Int = 0,

    @ColumnInfo(name = "allowed_queries")
    val allowedQueries: Int = 0,

    @ColumnInfo(name = "cached_queries")
    val cachedQueries: Int = 0,

    @ColumnInfo(name = "avg_response_time_ms")
    val avgResponseTimeMs: Long = 0,

    @ColumnInfo(name = "unique_domains")
    val uniqueDomains: Int = 0,

    @ColumnInfo(name = "unique_apps")
    val uniqueApps: Int = 0,

    @ColumnInfo(name = "ads_blocked")
    val adsBlocked: Int = 0,

    @ColumnInfo(name = "trackers_blocked")
    val trackersBlocked: Int = 0,

    @ColumnInfo(name = "malware_blocked")
    val malwareBlocked: Int = 0
)

/**
 * Per-app DNS summary for a given day.
 */
@Entity(
    tableName = "dns_app_stats",
    indices = [
        Index(value = ["date_str", "app_package"], unique = true),
        Index(value = ["app_package"])
    ]
)
data class DnsAppStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date_str")
    val dateStr: String,

    @ColumnInfo(name = "app_package")
    val appPackage: String,

    @ColumnInfo(name = "total_queries")
    val totalQueries: Int = 0,

    @ColumnInfo(name = "blocked_queries")
    val blockedQueries: Int = 0,

    @ColumnInfo(name = "unique_domains")
    val uniqueDomains: Int = 0,

    @ColumnInfo(name = "top_domain")
    val topDomain: String? = null
)

/**
 * Frequently queried domains tracker (for "Top Trackers" view).
 */
@Entity(
    tableName = "dns_domain_stats",
    indices = [
        Index(value = ["date_str", "domain"], unique = true),
        Index(value = ["blocked"])
    ]
)
data class DnsDomainStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date_str")
    val dateStr: String,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "query_count")
    val queryCount: Int = 0,

    @ColumnInfo(name = "blocked")
    val blocked: Boolean = false,

    @ColumnInfo(name = "block_reason")
    val blockReason: String? = null
)

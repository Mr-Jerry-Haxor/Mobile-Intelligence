package com.mobileintelligence.app.dns.data.entity

import androidx.room.*

/**
 * DNS query log entity.
 * Stores every DNS query with metadata for analytics.
 * 30-day retention with auto-purge.
 */
@Entity(
    tableName = "dns_queries",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["domain"]),
        Index(value = ["blocked"]),
        Index(value = ["app_package"]),
        Index(value = ["date_str"])
    ]
)
data class DnsQueryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "uid")
    val uid: Int = -1,

    @ColumnInfo(name = "app_package")
    val appPackage: String = "",

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "blocked")
    val blocked: Boolean = false,

    @ColumnInfo(name = "block_reason")
    val blockReason: String? = null,   // "ads", "tracker", "malware", "user", "regex"

    @ColumnInfo(name = "response_time_ms")
    val responseTimeMs: Long = 0,

    @ColumnInfo(name = "record_type")
    val recordType: Int = 1,           // 1=A, 28=AAAA, etc.

    @ColumnInfo(name = "record_type_name")
    val recordTypeName: String = "A",

    @ColumnInfo(name = "cached")
    val cached: Boolean = false,

    @ColumnInfo(name = "upstream_dns")
    val upstreamDns: String? = null,

    @ColumnInfo(name = "response_ip")
    val responseIp: String? = null,

    @ColumnInfo(name = "date_str")
    val dateStr: String = ""           // "2026-02-28" for date-based queries
)

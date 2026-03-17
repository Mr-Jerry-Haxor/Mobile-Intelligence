package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_daily",
    indices = [
        Index(value = ["date", "package_name"], unique = true),
        Index(value = ["date"]),
        Index(value = ["package_name"])
    ]
)
data class AppUsageDaily(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "total_time_ms")
    val totalTimeMs: Long = 0,

    @ColumnInfo(name = "session_count")
    val sessionCount: Int = 0,

    @ColumnInfo(name = "foreground_time_ms")
    val foregroundTimeMs: Long = 0,

    @ColumnInfo(name = "last_used_time")
    val lastUsedTime: Long? = null,

    @ColumnInfo(name = "open_count")
    val openCount: Int = 0
)

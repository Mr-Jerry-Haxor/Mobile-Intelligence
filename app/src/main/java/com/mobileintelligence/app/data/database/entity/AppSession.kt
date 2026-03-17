package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_sessions",
    indices = [
        Index(value = ["start_time"]),
        Index(value = ["date"]),
        Index(value = ["package_name", "date"]),
        Index(value = ["date", "start_time"])
    ]
)
data class AppSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "screen_session_id")
    val screenSessionId: Long? = null,

    @ColumnInfo(name = "is_foreground")
    val isForeground: Boolean = true
)

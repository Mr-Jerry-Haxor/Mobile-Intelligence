package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_summary",
    indices = [
        Index(value = ["date"], unique = true)
    ]
)
data class DailySummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "total_screen_time_ms")
    val totalScreenTimeMs: Long = 0,

    @ColumnInfo(name = "total_sessions")
    val totalSessions: Int = 0,

    @ColumnInfo(name = "total_unlocks")
    val totalUnlocks: Int = 0,

    @ColumnInfo(name = "first_unlock_time")
    val firstUnlockTime: Long? = null,

    @ColumnInfo(name = "last_screen_on_time")
    val lastScreenOnTime: Long? = null,

    @ColumnInfo(name = "longest_session_ms")
    val longestSessionMs: Long = 0,

    @ColumnInfo(name = "average_session_ms")
    val averageSessionMs: Long = 0,

    @ColumnInfo(name = "night_usage_ms")
    val nightUsageMs: Long = 0,

    @ColumnInfo(name = "night_sessions")
    val nightSessions: Int = 0,

    @ColumnInfo(name = "most_used_app")
    val mostUsedApp: String? = null,

    @ColumnInfo(name = "most_used_app_time_ms")
    val mostUsedAppTimeMs: Long = 0,

    @ColumnInfo(name = "unique_apps_used")
    val uniqueAppsUsed: Int = 0,

    @ColumnInfo(name = "addiction_score")
    val addictionScore: Float = 0f, // 0-100

    @ColumnInfo(name = "focus_score")
    val focusScore: Float = 0f, // 0-100

    @ColumnInfo(name = "sleep_disturbance_index")
    val sleepDisturbanceIndex: Float = 0f // 0-100
)

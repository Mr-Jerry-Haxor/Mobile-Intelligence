package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screen_sessions",
    indices = [
        Index(value = ["screen_on_time"]),
        Index(value = ["date"]),
        Index(value = ["date", "screen_on_time"])
    ]
)
data class ScreenSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "screen_on_time")
    val screenOnTime: Long,

    @ColumnInfo(name = "screen_off_time")
    val screenOffTime: Long? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "was_unlocked")
    val wasUnlocked: Boolean = false,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "hour_of_day")
    val hourOfDay: Int,

    @ColumnInfo(name = "is_night_usage")
    val isNightUsage: Boolean = false, // 11PM - 5AM

    @ColumnInfo(name = "is_after_boot")
    val isAfterBoot: Boolean = false
)

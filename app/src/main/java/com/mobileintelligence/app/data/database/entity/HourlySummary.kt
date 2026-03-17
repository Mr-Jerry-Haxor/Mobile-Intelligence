package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hourly_summary",
    indices = [
        Index(value = ["date", "hour"], unique = true),
        Index(value = ["date"])
    ]
)
data class HourlySummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "hour")
    val hour: Int, // 0-23

    @ColumnInfo(name = "screen_time_ms")
    val screenTimeMs: Long = 0,

    @ColumnInfo(name = "session_count")
    val sessionCount: Int = 0,

    @ColumnInfo(name = "unlock_count")
    val unlockCount: Int = 0
)

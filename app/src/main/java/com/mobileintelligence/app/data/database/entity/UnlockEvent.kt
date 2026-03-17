package com.mobileintelligence.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unlock_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["date"])
    ]
)
data class UnlockEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd

    @ColumnInfo(name = "hour_of_day")
    val hourOfDay: Int,

    @ColumnInfo(name = "is_first_after_boot")
    val isFirstAfterBoot: Boolean = false,

    @ColumnInfo(name = "time_since_last_unlock_ms")
    val timeSinceLastUnlockMs: Long? = null
)

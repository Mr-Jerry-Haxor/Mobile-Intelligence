package com.mobileintelligence.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mobileintelligence.app.data.database.dao.*
import com.mobileintelligence.app.data.database.entity.*

@Database(
    entities = [
        ScreenSession::class,
        AppSession::class,
        DailySummary::class,
        HourlySummary::class,
        AppUsageDaily::class,
        UnlockEvent::class
    ],
    version = 1,
    exportSchema = true
)
abstract class IntelligenceDatabase : RoomDatabase() {

    abstract fun screenSessionDao(): ScreenSessionDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun appUsageDailyDao(): AppUsageDailyDao
    abstract fun unlockEventDao(): UnlockEventDao

    companion object {
        @Volatile
        private var INSTANCE: IntelligenceDatabase? = null

        fun getInstance(context: Context): IntelligenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): IntelligenceDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                IntelligenceDatabase::class.java,
                "mobile_intelligence.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

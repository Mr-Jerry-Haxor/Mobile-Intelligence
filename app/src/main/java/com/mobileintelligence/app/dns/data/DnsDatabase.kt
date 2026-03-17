package com.mobileintelligence.app.dns.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mobileintelligence.app.dns.data.dao.*
import com.mobileintelligence.app.dns.data.entity.*

@Database(
    entities = [
        DnsQueryEntity::class,
        DnsDailyStats::class,
        DnsAppStats::class,
        DnsDomainStats::class,
        BlocklistEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class DnsDatabase : RoomDatabase() {

    abstract fun dnsQueryDao(): DnsQueryDao
    abstract fun dnsDailyStatsDao(): DnsDailyStatsDao
    abstract fun dnsAppStatsDao(): DnsAppStatsDao
    abstract fun dnsDomainStatsDao(): DnsDomainStatsDao
    abstract fun blocklistDao(): BlocklistDao

    companion object {
        @Volatile
        private var INSTANCE: DnsDatabase? = null

        fun getInstance(context: Context): DnsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DnsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DnsDatabase::class.java,
                "dns_firewall.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

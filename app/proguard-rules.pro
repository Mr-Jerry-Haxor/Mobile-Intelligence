# Mobile Intelligence ProGuard Rules

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
}

# Room Entities (both databases)
-keep class com.mobileintelligence.app.data.database.entity.** { *; }
-keep class com.mobileintelligence.app.dns.data.entity.** { *; }

# Room DAOs
-keep interface com.mobileintelligence.app.data.database.dao.** { *; }
-keep interface com.mobileintelligence.app.dns.data.dao.** { *; }

# Device Admin Receiver
-keep class com.mobileintelligence.app.receiver.AppProtectionAdmin { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

-dontwarn java.lang.invoke.StringConcatFactory

package com.mobileintelligence.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mi_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val IS_TRACKING_ENABLED = booleanPreferencesKey("is_tracking_enabled")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_BOOT_TIME = longPreferencesKey("last_boot_time")
        val LAST_SCREEN_ON_TIME = longPreferencesKey("last_screen_on_time")
        val CURRENT_SCREEN_SESSION_ID = longPreferencesKey("current_screen_session_id")
        val CURRENT_APP_SESSION_ID = longPreferencesKey("current_app_session_id")
        val LAST_FOREGROUND_APP = stringPreferencesKey("last_foreground_app")
        val DATA_RETENTION_YEARS = intPreferencesKey("data_retention_years")
        val LAST_CLEANUP_DATE = stringPreferencesKey("last_cleanup_date")
        val SERVICE_RUNNING = booleanPreferencesKey("service_running")
        val NUM_LOCK_ENABLED = booleanPreferencesKey("num_lock_enabled")
        val NUM_LOCK_PIN_HASH = stringPreferencesKey("num_lock_pin_hash")
    }

    val isTrackingEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[IS_TRACKING_ENABLED] ?: true }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { it[IS_FIRST_LAUNCH] ?: true }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE] ?: "auto" }

    val dataRetentionYears: Flow<Int> = context.dataStore.data
        .map { it[DATA_RETENTION_YEARS] ?: 3 }

    val isServiceRunning: Flow<Boolean> = context.dataStore.data
        .map { it[SERVICE_RUNNING] ?: false }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_TRACKING_ENABLED] = enabled }
    }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[IS_FIRST_LAUNCH] = false }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setLastBootTime(time: Long) {
        context.dataStore.edit { it[LAST_BOOT_TIME] = time }
    }

    suspend fun getLastBootTime(): Long {
        return context.dataStore.data.first()[LAST_BOOT_TIME] ?: 0L
    }

    suspend fun setCurrentScreenSessionId(id: Long) {
        context.dataStore.edit { it[CURRENT_SCREEN_SESSION_ID] = id }
    }

    suspend fun getCurrentScreenSessionId(): Long {
        return context.dataStore.data.first()[CURRENT_SCREEN_SESSION_ID] ?: -1L
    }

    suspend fun setCurrentAppSessionId(id: Long) {
        context.dataStore.edit { it[CURRENT_APP_SESSION_ID] = id }
    }

    suspend fun getCurrentAppSessionId(): Long {
        return context.dataStore.data.first()[CURRENT_APP_SESSION_ID] ?: -1L
    }

    suspend fun setLastForegroundApp(packageName: String) {
        context.dataStore.edit { it[LAST_FOREGROUND_APP] = packageName }
    }

    suspend fun getLastForegroundApp(): String {
        return context.dataStore.data.first()[LAST_FOREGROUND_APP] ?: ""
    }

    suspend fun setServiceRunning(running: Boolean) {
        context.dataStore.edit { it[SERVICE_RUNNING] = running }
    }

    suspend fun setLastCleanupDate(date: String) {
        context.dataStore.edit { it[LAST_CLEANUP_DATE] = date }
    }

    suspend fun getLastCleanupDate(): String {
        return context.dataStore.data.first()[LAST_CLEANUP_DATE] ?: ""
    }

    // ── NumLock PIN ──────────────────────────────────────────────

    val isNumLockEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[NUM_LOCK_ENABLED] ?: false }

    suspend fun isNumLockSet(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[NUM_LOCK_ENABLED] == true && !prefs[NUM_LOCK_PIN_HASH].isNullOrEmpty()
    }

    suspend fun setNumLockPin(pin: String) {
        context.dataStore.edit {
            it[NUM_LOCK_PIN_HASH] = hashPin(pin)
            it[NUM_LOCK_ENABLED] = true
        }
    }

    suspend fun removeNumLock() {
        context.dataStore.edit {
            it[NUM_LOCK_ENABLED] = false
            it[NUM_LOCK_PIN_HASH] = ""
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val storedHash = context.dataStore.data.first()[NUM_LOCK_PIN_HASH] ?: return false
        return storedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

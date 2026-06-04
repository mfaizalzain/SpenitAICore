package com.fmz.spenitaicore.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spenit_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_DEFAULT_CURRENCY = stringPreferencesKey("default_currency_code")
        val KEY_SALARY_PAY_DAY = intPreferencesKey("salary_pay_day")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_GOOGLE_ID = stringPreferencesKey("google_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val KEY_AUTH_METHOD = stringPreferencesKey("auth_method")
        
        // Cached user info (persists after logout)
        val KEY_CACHED_USER_NAME = stringPreferencesKey("cached_user_name")
        val KEY_CACHED_USER_PHOTO_URL = stringPreferencesKey("cached_user_photo_url")

        val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val KEY_BACKUP_ACCOUNT = stringPreferencesKey("backup_account")
        val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")

        // AI Provider
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_CUSTOM_URL = stringPreferencesKey("ai_custom_url")
        val KEY_HAS_DISMISSED_AICORE_DIALOG = booleanPreferencesKey("has_dismissed_aicore_dialog")
    }

    val defaultCurrency: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_CURRENCY] ?: "$"
    }

    suspend fun getDefaultCurrency(): String = context.dataStore.data.first()[KEY_DEFAULT_CURRENCY] ?: "$"

    suspend fun setDefaultCurrency(currency: String) {
        context.dataStore.edit { it[KEY_DEFAULT_CURRENCY] = currency }
    }

    val salaryPayDay: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SALARY_PAY_DAY] ?: 1
    }

    suspend fun getSalaryPayDay(): Int = context.dataStore.data.first()[KEY_SALARY_PAY_DAY] ?: 1

    suspend fun setSalaryPayDay(payDay: Int) {
        context.dataStore.edit { it[KEY_SALARY_PAY_DAY] = payDay.coerceIn(1, 31) }
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LANGUAGE] ?: "en"
    }

    suspend fun getAppLanguage(): String = context.dataStore.data.first()[KEY_APP_LANGUAGE] ?: "en"

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = language }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: THEME_MODE_DAY
    }

    suspend fun getThemeMode(): String =
        context.dataStore.data.first()[KEY_THEME_MODE] ?: THEME_MODE_DAY

    suspend fun setThemeMode(mode: String) {
        val normalizedMode = when (mode) {
            THEME_MODE_DAY, THEME_MODE_NIGHT, THEME_MODE_SYSTEM -> mode
            else -> THEME_MODE_DAY
        }
        context.dataStore.edit { it[KEY_THEME_MODE] = normalizedMode }
    }

    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    suspend fun getAppLockEnabled(): Boolean =
        context.dataStore.data.first()[KEY_APP_LOCK_ENABLED] ?: false

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    // --- Auth state ---

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_LOGGED_IN] ?: false
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: ""
    }

    suspend fun getIsLoggedIn(): Boolean =
        context.dataStore.data.first()[KEY_IS_LOGGED_IN] ?: false

    suspend fun setLoggedIn(
        googleId: String,
        name: String,
        email: String,
        photoUrl: String?,
        authMethod: String = "google"
    ) {
        context.dataStore.edit {
            it[KEY_IS_LOGGED_IN] = true
            it[KEY_GOOGLE_ID] = googleId
            it[KEY_USER_NAME] = name
            it[KEY_USER_EMAIL] = email
            it[KEY_AUTH_METHOD] = authMethod
            if (photoUrl != null) it[KEY_USER_PHOTO_URL] = photoUrl
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it.remove(KEY_IS_LOGGED_IN)
            it.remove(KEY_GOOGLE_ID)
            it.remove(KEY_USER_NAME)
            it.remove(KEY_USER_EMAIL)
            it.remove(KEY_USER_PHOTO_URL)
            it.remove(KEY_AUTH_METHOD)
        }
    }

    suspend fun getAuthMethod(): String? =
        context.dataStore.data.first()[KEY_AUTH_METHOD]

    suspend fun getUserName(): String =
        context.dataStore.data.first()[KEY_USER_NAME] ?: ""

    suspend fun getUserEmail(): String =
        context.dataStore.data.first()[KEY_USER_EMAIL] ?: ""

    suspend fun getUserPhotoUrl(): String? =
        context.dataStore.data.first()[KEY_USER_PHOTO_URL]

    // --- Cached User Info ---

    suspend fun getCachedUserName(): String? =
        context.dataStore.data.first()[KEY_CACHED_USER_NAME]

    suspend fun getCachedUserPhotoUrl(): String? =
        context.dataStore.data.first()[KEY_CACHED_USER_PHOTO_URL]

    suspend fun setCachedUserInfo(name: String, photoUrl: String?) {
        context.dataStore.edit {
            it[KEY_CACHED_USER_NAME] = name
            if (photoUrl != null) it[KEY_CACHED_USER_PHOTO_URL] = photoUrl
            else it.remove(KEY_CACHED_USER_PHOTO_URL)
        }
    }

    // ── Backup ────────────────────────────────────────────────────

    suspend fun isBackupEnabled(): Boolean =
        context.dataStore.data.first()[KEY_BACKUP_ENABLED] ?: false

    suspend fun setBackupEnabled(enabled: Boolean, accountName: String?) {
        context.dataStore.edit {
            it[KEY_BACKUP_ENABLED] = enabled
            if (accountName != null) it[KEY_BACKUP_ACCOUNT] = accountName
            else it.remove(KEY_BACKUP_ACCOUNT)
        }
    }

    suspend fun getBackupAccountName(): String? =
        context.dataStore.data.first()[KEY_BACKUP_ACCOUNT]

    suspend fun setLastBackupTime(time: Long) {
        context.dataStore.edit { it[KEY_LAST_BACKUP_TIME] = time }
    }

    suspend fun getLastBackupTime(): Long =
        context.dataStore.data.first()[KEY_LAST_BACKUP_TIME] ?: 0L

    // ── AI Provider ─────────────────────────────────────────────────

    suspend fun getAiProvider(): String =
        context.dataStore.data.first()[KEY_AI_PROVIDER] ?: "aicore"

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun getAiApiKey(): String =
        context.dataStore.data.first()[KEY_AI_API_KEY] ?: ""

    suspend fun setAiApiKey(key: String) {
        context.dataStore.edit { it[KEY_AI_API_KEY] = key }
    }

    suspend fun getAiModel(): String =
        context.dataStore.data.first()[KEY_AI_MODEL] ?: ""

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[KEY_AI_MODEL] = model }
    }

    suspend fun getAiCustomUrl(): String =
        context.dataStore.data.first()[KEY_AI_CUSTOM_URL] ?: ""

    suspend fun setAiCustomUrl(url: String) {
        context.dataStore.edit { it[KEY_AI_CUSTOM_URL] = url }
    }

    suspend fun hasAiApiKey(): Boolean =
        getAiApiKey().isNotBlank()

    suspend fun clearAiProvider() {
        context.dataStore.edit {
            it.remove(KEY_AI_PROVIDER)
            it.remove(KEY_AI_API_KEY)
            it.remove(KEY_AI_MODEL)
            it.remove(KEY_AI_CUSTOM_URL)
        }
    }

    suspend fun hasDismissedAiCoreDialog(): Boolean =
        context.dataStore.data.first()[KEY_HAS_DISMISSED_AICORE_DIALOG] ?: false

    suspend fun setDismissedAiCoreDialog(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_HAS_DISMISSED_AICORE_DIALOG] = dismissed }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}

const val THEME_MODE_DAY = "day"
const val THEME_MODE_NIGHT = "night"
const val THEME_MODE_SYSTEM = "system"

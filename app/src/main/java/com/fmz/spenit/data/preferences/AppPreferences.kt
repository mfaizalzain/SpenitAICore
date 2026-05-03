package com.fmz.spenit.data.preferences

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
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
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

    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    suspend fun getAppLockEnabled(): Boolean =
        context.dataStore.data.first()[KEY_APP_LOCK_ENABLED] ?: false

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }
}

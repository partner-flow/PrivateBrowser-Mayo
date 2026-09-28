package com.privacybrowser.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.privacybrowser.app.model.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Non-sensitive user preferences. Sensitive data (the app-lock PIN) is deliberately NOT stored
 * here — see [com.privacybrowser.app.security.AppLockManager], which uses EncryptedSharedPreferences.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DARK_THEME = stringPreferencesKey("dark_theme") // "system" | "light" | "dark"
    }

    val searchEngine: Flow<SearchEngine> = context.dataStore.data.map { prefs ->
        SearchEngine.entries.find { it.name == prefs[Keys.SEARCH_ENGINE] } ?: SearchEngine.DUCKDUCKGO
    }

    val adBlockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AD_BLOCK_ENABLED] ?: true }
    val javascriptEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.JAVASCRIPT_ENABLED] ?: true }
    val cookiesEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.COOKIES_ENABLED] ?: true }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: "system" }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AD_BLOCK_ENABLED] = enabled }
    }

    suspend fun setJavascriptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setCookiesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.COOKIES_ENABLED] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.DARK_THEME] = mode }
    }
}

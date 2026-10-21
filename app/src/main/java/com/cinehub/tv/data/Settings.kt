package com.cinehub.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinehub.tv.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val tmdbKey: String,
    val language: String,
    val region: String,
    val profileId: Long,
    val deviceId: String,
)

class SettingsStore(private val context: Context) {
    private val kTmdb = stringPreferencesKey("tmdb_key")
    private val kLang = stringPreferencesKey("language")
    private val kRegion = stringPreferencesKey("region")
    private val kProfile = longPreferencesKey("profile_id")
    private val kDevice = stringPreferencesKey("device_id")

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            tmdbKey = p[kTmdb]?.takeIf { it.isNotBlank() } ?: BuildConfig.TMDB_API_KEY,
            language = p[kLang] ?: "ru-RU",
            region = p[kRegion] ?: "UA",
            profileId = p[kProfile] ?: 0L,
            deviceId = p[kDevice] ?: "",
        )
    }

    suspend fun current(): AppSettings {
        val s = flow.first()
        if (s.deviceId.isBlank()) {
            val id = java.util.UUID.randomUUID().toString()
            context.dataStore.edit { it[kDevice] = id }
            return s.copy(deviceId = id)
        }
        return s
    }

    suspend fun setTmdbKey(v: String) = context.dataStore.edit { it[kTmdb] = v.trim() }
    suspend fun setLanguage(v: String) = context.dataStore.edit { it[kLang] = v }
    suspend fun setRegion(v: String) = context.dataStore.edit { it[kRegion] = v }
    suspend fun setProfile(id: Long) = context.dataStore.edit { it[kProfile] = id }
}

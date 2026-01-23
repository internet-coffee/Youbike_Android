package data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val FAVORITE_STATIONS_JSON = stringPreferencesKey("favorite_stations_json")
    private val REFRESH_INTERVAL_SECONDS = intPreferencesKey("refresh_interval_seconds")

    val favoriteStations: Flow<List<StationInfo>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[FAVORITE_STATIONS_JSON]
            if (jsonString != null) {
                try {
                    commonJson.decodeFromString<List<StationInfo>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    val refreshInterval: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REFRESH_INTERVAL_SECONDS] ?: 0
        }

    suspend fun saveFavoriteStations(stations: List<StationInfo>) {
        val jsonString = commonJson.encodeToString(stations)
        context.dataStore.edit { preferences ->
            preferences[FAVORITE_STATIONS_JSON] = jsonString
        }
    }

    suspend fun saveRefreshInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_SECONDS] = seconds
        }
    }
}
package data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val FAVORITE_STATIONS_JSON = stringPreferencesKey("favorite_stations_json")

    val favoriteStations: Flow<List<StationInfo>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[FAVORITE_STATIONS_JSON]
            if (jsonString != null) {
                try {
                    commonJson.decodeFromString<List<StationInfo>>(jsonString)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    suspend fun saveFavoriteStations(stations: List<StationInfo>) {
        val jsonString = commonJson.encodeToString(stations)
        context.dataStore.edit { preferences ->
            preferences[FAVORITE_STATIONS_JSON] = jsonString
        }
    }
}
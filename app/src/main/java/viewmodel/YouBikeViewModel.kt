package viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import data.StationInfo
import data.StationRequest
import data.UserPreferencesRepository
import data.VehicleInfo
import data.YouBikeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private data class SearchIndex(
    val info: StationInfo,
    val searchKey: String
)

@Immutable
data class StationResult(
    val info: StationInfo,
    val isFavorite: Boolean = false,
    val availableBikes: Int? = null,
    val availableEBikes: Int? = null,
    val emptySpaces: Int? = null
)

@Immutable
data class YouBikeUiState(
    val searchResults: List<StationResult> = emptyList(),
    val favoriteStations: List<StationResult> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val currentQuery: String = ""
)

@OptIn(FlowPreview::class)
class YouBikeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(YouBikeUiState())
    val uiState: StateFlow<YouBikeUiState> = _uiState.asStateFlow()

    private var allStationsCache: List<StationInfo>? = null
    private var stationIdMap: Map<String, StationInfo> = emptyMap()
    private var searchableIndex: List<SearchIndex> = emptyList()

    val userPreferencesRepository = UserPreferencesRepository(application)

    init {
        viewModelScope.launch {
            userPreferencesRepository.favoriteStations
                .debounce(100)
                .distinctUntilChanged()
                .collect { favoriteStationInfos ->
                    loadAndRefreshFavoriteStations(favoriteStationInfos)
                }
        }
    }

    fun toggleFavorite(stationInfo: StationInfo) {
        viewModelScope.launch {
            val currentFavorites = userPreferencesRepository.favoriteStations.first().toMutableList()
            val existing = currentFavorites.find { it.stationNo == stationInfo.stationNo }

            if (existing != null) {
                currentFavorites.remove(existing)
            } else {
                currentFavorites.add(stationInfo)
            }
            userPreferencesRepository.saveFavoriteStations(currentFavorites)

            _uiState.update { currentState ->
                val updatedSearchResults = currentState.searchResults.map { result ->
                    if (result.info.stationNo == stationInfo.stationNo) {
                        result.copy(isFavorite = !result.isFavorite)
                    } else {
                        result
                    }
                }
                currentState.copy(searchResults = updatedSearchResults)
            }
        }
    }

    fun triggerManualRefresh() {
        val currentState = _uiState.value
        if (!currentState.isRefreshing && !currentState.isLoading) {
            if (currentState.isSearching) {
                refreshSearchResults(currentState.currentQuery)
            } else {
                refreshFavoriteStations()
            }
        }
    }

    fun searchStations(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSearching = true, errorMessage = null, currentQuery = query) }

            runCatching {
                if (allStationsCache == null) fetchAndCacheAllStations()

                val lowercaseQuery = query.lowercase().trim()

                val filteredStations = withContext(Dispatchers.Default) {
                    if (lowercaseQuery.isBlank()) {
                        allStationsCache ?: emptyList()
                    } else {
                        val exactMatch = stationIdMap[lowercaseQuery]
                        if (exactMatch != null) {
                            listOf(exactMatch)
                        } else {
                            searchableIndex
                                .filter { it.searchKey.contains(lowercaseQuery) }
                                .map { it.info }
                        }
                    }
                }

                val stationsToQuery = filteredStations.take(100)
                val favoriteIds = userPreferencesRepository.favoriteStations.first().map { it.stationNo }.toSet()
                fetchParkingInfoForSearch(stationsToQuery, favoriteIds)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "發生錯誤：${e.message}") }
            }
        }
    }

    private suspend fun fetchAndCacheAllStations(): List<StationInfo> {
        return try {
            val stations = YouBikeApi.retrofitService.getAllStations()
            allStationsCache = stations
            stationIdMap = stations.associateBy { it.stationNo }
            searchableIndex = stations.map {
                SearchIndex(it, "${it.name}|${it.address}|${it.stationNo}".lowercase())
            }
            stations
        } catch (e: IOException) {
            emptyList()
        }
    }

    private suspend fun performRefreshAction(
        fetchStations: suspend () -> List<StationInfo>,
        updateState: (List<StationResult>) -> YouBikeUiState
    ) {
        _uiState.update { it.copy(isRefreshing = true) }
        runCatching {
            val stations = fetchStations()
            if (stations.isNotEmpty()) {
                val results = aggregateStationData(stations)
                _uiState.update { updateState(results).copy(isRefreshing = false, toastMessage = "刷新成功") }
            } else {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }.onFailure {
            _uiState.update { it.copy(isRefreshing = false, toastMessage = "刷新失敗") }
        }
    }

    private suspend fun aggregateStationData(stations: List<StationInfo>): List<StationResult> {
        val favoriteIds = userPreferencesRepository.favoriteStations.first().map { it.stationNo }.toSet()
        val vehicleDataMap = fetchVehicleData(stations)
        return stations.map { info ->
            val vehicleInfo = vehicleDataMap[info.stationNo]
            StationResult(
                info = info,
                isFavorite = info.stationNo in favoriteIds,
                availableBikes = vehicleInfo?.vehicleDetails?.youbike2 ?: 0,
                availableEBikes = vehicleInfo?.vehicleDetails?.youbike2E ?: 0,
                emptySpaces = vehicleInfo?.emptySpaces ?: 0
            )
        }
    }

    fun refreshFavoriteStations() {
        viewModelScope.launch {
            performRefreshAction(
                fetchStations = { userPreferencesRepository.favoriteStations.first() },
                updateState = { results -> _uiState.value.copy(favoriteStations = results) }
            )
        }
    }

    fun refreshSearchResults(query: String) {
        viewModelScope.launch {
            performRefreshAction(
                fetchStations = {
                    val lowercaseQuery = query.lowercase().trim()
                    if (lowercaseQuery.isBlank()) allStationsCache ?: emptyList()
                    else searchableIndex.filter { it.searchKey.contains(lowercaseQuery) }.map { it.info }.take(100)
                },
                updateState = { results -> _uiState.value.copy(searchResults = results) }
            )
        }
    }

    fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, currentQuery = "") }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun loadAndRefreshFavoriteStations(favoriteStationInfos: List<StationInfo>) {
        viewModelScope.launch {
            if (favoriteStationInfos.isEmpty()) {
                _uiState.update { it.copy(favoriteStations = emptyList()) }
                return@launch
            }
            try {
                val results = aggregateStationData(favoriteStationInfos)
                _uiState.update { it.copy(favoriteStations = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "無法更新即時車輛資訊") }
            }
        }
    }

    private suspend fun fetchParkingInfoForSearch(stations: List<StationInfo>, favoriteIds: Set<String>) {
        if (stations.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isLoading = false) }
            return
        }
        runCatching {
            val vehicleDataMap = fetchVehicleData(stations)
            val results = stations.map { info ->
                val vehicleInfo = vehicleDataMap[info.stationNo]
                StationResult(
                    info = info,
                    isFavorite = info.stationNo in favoriteIds,
                    availableBikes = vehicleInfo?.vehicleDetails?.youbike2 ?: 0,
                    availableEBikes = vehicleInfo?.vehicleDetails?.youbike2E ?: 0,
                    emptySpaces = vehicleInfo?.emptySpaces ?: 0
                )
            }
            _uiState.update { it.copy(searchResults = results, isLoading = false) }
        }.onFailure {
            _uiState.update { it.copy(isLoading = false, errorMessage = "搜尋失敗") }
        }
    }

    private suspend fun fetchVehicleData(stations: List<StationInfo>): Map<String, VehicleInfo> = withContext(Dispatchers.IO) {
        val stationIds = stations.map { it.stationNo }
        val chunks = stationIds.chunked(50)

        val results = chunks.map { batchIds ->
            async {
                runCatching {
                    YouBikeApi.retrofitService.getParkingInfo(StationRequest(batchIds))
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        if (stations.isNotEmpty() && results.isEmpty()) {
            throw IOException("網路連線失敗")
        }

        results.flatMap { it.retVal.data }.associateBy { it.stationNo }
    }
}
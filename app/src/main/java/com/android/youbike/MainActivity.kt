package com.android.youbike

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.youbike.data.StationInfo
import com.android.youbike.ui.components.MySearchBar
import com.android.youbike.ui.screens.SettingsScreen
import com.android.youbike.ui.theme.YoubikeTheme
import com.android.youbike.ui.viewmodel.StationResult
import com.android.youbike.ui.viewmodel.ViewModelFactory
import com.android.youbike.ui.viewmodel.YouBikeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var followSystemTheme by remember { mutableStateOf(true) }
            var isDarkMode by remember { mutableStateOf(false) }
            val useDarkTheme = when {
                followSystemTheme -> isSystemInDarkTheme()
                else -> isDarkMode
            }
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            YoubikeTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val context = LocalContext.current
                val viewModel: YouBikeViewModel = viewModel(
                    factory = ViewModelFactory(context.applicationContext as Application)
                )

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                    composable("settings") {
                        val currentInterval by viewModel.userPreferencesRepository.refreshInterval.collectAsState(
                            initial = 0
                        )
                        SettingsScreen(
                            navController = navController,
                            followSystemTheme = followSystemTheme,
                            isDarkMode = isDarkMode,
                            onFollowSystemChange = { followSystemTheme = it },
                            onDarkModeChange = { isDarkMode = it },
                            currentInterval = currentInterval,
                            onIntervalSelected = { seconds ->
                                viewModel.viewModelScope.launch {
                                    viewModel.userPreferencesRepository.saveRefreshInterval(seconds)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: YouBikeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val stableOnFavoriteToggle: (StationInfo) -> Unit = remember(viewModel) {
        { stationInfo -> viewModel.toggleFavorite(stationInfo) }
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    // ✨ 新增點 1: 這是新的、能夠感知生命週期的自動刷新邏輯 ✨
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 這個區塊內的程式碼，只會在 App 處於前台 (Started 狀態) 時執行。
            // 當 App 進入背景，這個協程會被自動取消。回到前台時，再重新啟動。
            viewModel.userPreferencesRepository.refreshInterval.flatMapLatest { interval ->
                if (interval > 0) {
                    // 建立一個定時發射器
                    flow {
                        // 初始延遲，避免 App 一打開就刷新
                        delay(interval * 1000L)
                        while (true) {
                            emit(Unit)
                            delay(interval * 1000L)
                        }
                    }
                } else {
                    emptyFlow() // 如果設定為 0 (永不)，則不執行任何操作
                }
            }.collect {
                // 每當計時器發射信號，就根據目前狀態執行對應的刷新
                val currentState = viewModel.uiState.value
                if (!currentState.isRefreshing) {
                    if (currentState.isSearching) {
                        viewModel.refreshSearchResults(currentState.currentQuery)
                    } else {
                        viewModel.refreshFavoriteStations()
                    }
                }
            }
        }
    }


    val onRefresh = {
        if (uiState.isSearching) {
            viewModel.refreshSearchResults(query)
        } else {
            viewModel.refreshFavoriteStations()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = onRefresh
    )

    LaunchedEffect(key1 = uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    BackHandler(enabled = uiState.isSearching) {
        viewModel.clearSearchResults()
        query = ""
    }

    Scaffold(
        topBar = {
            MySearchBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                query = query,
                onQueryChange = { query = it },
                onSettingsClicked = { navController.navigate("settings") },
                onSearch = {
                    viewModel.searchStations(query)
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                },
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
                val stationsToShow =
                    if (uiState.isSearching) uiState.searchResults else uiState.favoriteStations
                when {
                    uiState.isLoading && stationsToShow.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    stationsToShow.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(
                                items = stationsToShow,
                                key = { it.info.stationNo },
                                contentType = { "station_item" }
                            ) { result ->
                                StationResultItem(
                                    result = result,
                                    onFavoriteClicked = stableOnFavoriteToggle
                                )
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val message =
                                if (uiState.isSearching) "找不到符合條件的站點" else "點擊卡片右上角的愛心來收藏站點"
                            Text(text = message, modifier = Modifier.padding(16.dp))
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = uiState.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}


@Composable
fun StationResultItem(
    result: StationResult,
    onFavoriteClicked: (StationInfo) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = result.info.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.info.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BikeInfo("YouBike 2.0", result.availableBikes?.toString() ?: "--")
                    BikeInfo("Youbike 2.0E", result.availableEBikes?.toString() ?: "--")
                    BikeInfo("可停空位", result.emptySpaces?.toString() ?: "--")
                }
            }
            IconButton(
                onClick = { onFavoriteClicked(result.info) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (result.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (result.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BikeInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import data.StationInfo
import components.MySearchBar
import screens.SettingsScreen
import com.android.youbike.ui.theme.YoubikeTheme
import viewmodel.StationResult
import viewmodel.ViewModelFactory
import viewmodel.YouBikeViewModel
import kotlinx.coroutines.delay
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
                        val currentInterval by viewModel.userPreferencesRepository.refreshInterval.collectAsState(initial = 0)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: YouBikeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentInterval by viewModel.userPreferencesRepository.refreshInterval.collectAsState(initial = 0)
    val stableOnFavoriteToggle: (StationInfo) -> Unit = remember(viewModel) {
        { stationInfo -> viewModel.toggleFavorite(stationInfo) }
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(currentInterval) {
        if (currentInterval > 0) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(currentInterval * 1000L)
                    val uiState = viewModel.uiState.value
                    if (!uiState.isRefreshing) {
                        if (uiState.isSearching) {
                            viewModel.refreshSearchResults(uiState.currentQuery)
                        } else {
                            viewModel.refreshFavoriteStations()
                        }
                    }
                }
            }
        }
    }


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
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.triggerManualRefresh() }
            ) {
                val stationsToShow = if (uiState.isSearching) uiState.searchResults else uiState.favoriteStations
                when {
                    uiState.isLoading && stationsToShow.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val message = if (uiState.isSearching) "找不到符合條件的站點" else "點擊卡片右上角的愛心來收藏站點"
                            Text(text = message, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
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
                Text(text = result.info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = result.info.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    BikeInfo("YouBike 2.0", result.availableBikes?.toString() ?: "--")
                    BikeInfo("YouBike 2.0E", result.availableEBikes?.toString() ?: "--")
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
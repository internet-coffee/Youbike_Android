package com.android.youbike

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.youbike.ui.theme.YoubikeTheme
import components.MySearchBar
import data.StationInfo
import screens.SettingsScreen
import viewmodel.StationResult
import viewmodel.ViewModelFactory
import viewmodel.YouBikeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YoubikeTheme {
                val navController = rememberNavController()
                val viewModel: YouBikeViewModel = viewModel(
                    factory = ViewModelFactory(applicationContext as Application)
                )

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                    composable("settings") {

                        SettingsScreen(
                            navController = navController,
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.toastMessage) {
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
                onSearch = { viewModel.searchStations(query) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.triggerManualRefresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { focusManager.clearFocus() }
            ) {
                val stationsToShow =
                    if (uiState.isSearching) uiState.searchResults else uiState.favoriteStations

                when {
                    uiState.isLoading && stationsToShow.isEmpty() -> {
                        LoadingPlaceholder()
                    }

                    uiState.errorMessage != null && stationsToShow.isEmpty() -> {
                        ErrorPlaceholder(uiState.errorMessage!!)
                    }

                    stationsToShow.isNotEmpty() -> {
                        StationList(
                            stations = stationsToShow,
                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                            contentPadding = innerPadding
                        )
                    }

                    else -> {
                        EmptyPlaceholder(isSearching = uiState.isSearching)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationList(
    stations: List<StationResult>,
    onFavoriteToggle: (StationInfo) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp
        )
    ) {
        items(
            items = stations,
            key = { it.info.stationNo }
        ) { result ->
            StationResultItem(
                result = result,
                onFavoriteClicked = onFavoriteToggle
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EmptyPlaceholder(isSearching: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val message =
            if (isSearching) stringResource(R.string.search_no_results) else stringResource(R.string.favorite_empty_hint)
        Text(text = message, modifier = Modifier.padding(16.dp))
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
                    BikeInfo(
                        stringResource(R.string.label_youbike_2_0),
                        result.availableBikes?.toString() ?: "--"
                    )
                    BikeInfo(
                        stringResource(R.string.label_youbike_2_0e),
                        result.availableEBikes?.toString() ?: "--"
                    )
                    BikeInfo(
                        stringResource(R.string.label_empty_spaces),
                        result.emptySpaces?.toString() ?: "--"
                    )
                }
            }

            IconButton(
                onClick = { onFavoriteClicked(result.info) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (result.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite_icon_desc),
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
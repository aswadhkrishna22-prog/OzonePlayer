/*
 * Ozone Player (2026)
 * 50,000+ Internet Radio Browser Screen
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens.radio

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.radio.RadioBrowserClient
import moe.rukamori.archivetune.radio.RadioCategory
import moe.rukamori.archivetune.radio.RadioPlayer
import moe.rukamori.archivetune.radio.RadioRepository
import moe.rukamori.archivetune.radio.RadioStation
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.theme.LocalOzoneColors

private const val TAG_FAVORITES = "_favorites_"
private const val TAG_RECENTS = "_recents_"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current

    val favorites by RadioRepository.favoritesFlow.collectAsState()
    val recents by RadioRepository.recentsFlow.collectAsState()

    var selectedTag by remember { mutableStateOf("popular") }
    var searchQuery by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var customStationName by remember { mutableStateOf("") }
    var customStationUrl by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val bottomInset = with(density) { windowInsets.getBottom(this).toDp() }

    // Query loading effect with debounce
    LaunchedEffect(selectedTag, searchQuery, favorites, recents) {
        if (searchQuery.isNotBlank()) {
            isLoading = true
            delay(350)
            stations = RadioBrowserClient.searchStations(searchQuery)
            isLoading = false
        } else {
            when (selectedTag) {
                TAG_FAVORITES -> {
                    stations = favorites
                    isLoading = false
                }
                TAG_RECENTS -> {
                    stations = recents
                    isLoading = false
                }
                "popular" -> {
                    isLoading = true
                    stations = RadioBrowserClient.getPopular()
                    isLoading = false
                }
                else -> {
                    isLoading = true
                    stations = RadioBrowserClient.getByTag(selectedTag)
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Internet Radio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = "Add Custom Station",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search 50,000+ radio stations worldwide...") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Search",
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            // Horizontal Category Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Favorites Chip
                FilterChip(
                    selected = selectedTag == TAG_FAVORITES && searchQuery.isEmpty(),
                    onClick = {
                        searchQuery = ""
                        selectedTag = TAG_FAVORITES
                    },
                    label = { Text("Favorites (${favorites.size})") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.favorite),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Red,
                        )
                    },
                )

                // Recents Chip
                if (recents.isNotEmpty()) {
                    FilterChip(
                        selected = selectedTag == TAG_RECENTS && searchQuery.isEmpty(),
                        onClick = {
                            searchQuery = ""
                            selectedTag = TAG_RECENTS
                        },
                        label = { Text("Recent (${recents.size})") },
                    )
                }

                // Curated Category Chips
                RadioBrowserClient.CURATED_GENRES.forEach { category ->
                    FilterChip(
                        selected = selectedTag == category.tag && searchQuery.isEmpty(),
                        onClick = {
                            searchQuery = ""
                            selectedTag = category.tag
                        },
                        label = { Text(category.title) },
                    )
                }
            }

            // Station Content List
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (stations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.radio),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTag == TAG_FAVORITES) "No favorite stations yet" else "No stations found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selectedTag == TAG_FAVORITES) {
                            Text(
                                text = "Tap the heart icon on any station to save it here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 6.dp,
                            bottom = bottomInset + 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(stations, key = { it.uuid }) { station ->
                            RadioStationItem(
                                station = station,
                                isFavorite = favorites.any { it.uuid == station.uuid },
                                onToggleFavorite = {
                                    RadioRepository.toggleFavorite(station)
                                },
                                onClick = {
                                    if (playerConnection != null && database != null) {
                                        Toast.makeText(context, "Tuning into ${station.name}...", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            RadioPlayer.playStation(station, playerConnection, database)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Custom Station Dialog
    if (showAddDialog) {
        DefaultDialog(
            onDismiss = {
                showAddDialog = false
                customStationName = ""
                customStationUrl = ""
            },
            title = { Text("Add Custom Radio Station") },
            buttons = {
                TextButton(onClick = {
                    showAddDialog = false
                    customStationName = ""
                    customStationUrl = ""
                }) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val url = customStationUrl.trim()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            val newStation = RadioRepository.addCustomStation(
                                name = customStationName.ifBlank { "Custom Stream" },
                                streamUrl = url,
                            )
                            showAddDialog = false
                            customStationName = ""
                            customStationUrl = ""
                            if (playerConnection != null && database != null) {
                                scope.launch {
                                    RadioPlayer.playStation(newStation, playerConnection, database)
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter a valid HTTP/HTTPS stream URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Save & Play")
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = customStationName,
                    onValueChange = { customStationName = it },
                    label = { Text("Station Name") },
                    placeholder = { Text("e.g. My Favorite FM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customStationUrl,
                    onValueChange = { customStationUrl = it },
                    label = { Text("Stream URL") },
                    placeholder = { Text("http://stream.example.com:8000/live.mp3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun RadioStationItem(
    station: RadioStation,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Station Favicon / Icon Box
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!station.favicon.isNullOrBlank()) {
                    AsyncImage(
                        model = station.favicon,
                        contentDescription = station.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    station.frequency?.let { freq ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text(
                                text = freq,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    Text(
                        text = station.details.ifEmpty { "Live broadcast" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.favorite else R.drawable.favorite_border
                    ),
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

package com.imusic.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// --- Theme Colors ---
val BackgroundBlack = Color(0xFF121212)
val PlayerGray = Color(0xFF242424)
val AccentPrimary = Color(0xFFFF0000) // YouTube Red
val TextPrimary = Color.White
val TextSecondary = Color(0xFFA7A7A7)

// Featured demo tracks loaded on startup
val FeaturedSongs = listOf(
    MusicTrack(
        id = "dQw4w9WgXcQ",
        title = "Featured Track 1",
        artist = "Trending Artist",
        albumArtUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop",
        streamUrl = ""
    ),
    MusicTrack(
        id = "jNQXAC9IVRw",
        title = "Featured Track 2",
        artist = "Trending Artist",
        albumArtUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=600&auto=format&fit=crop",
        streamUrl = ""
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+ (API 33+) to prevent SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }

        setContent {
            MaterialTheme {
                Surface(color = BackgroundBlack, modifier = Modifier.fillMaxSize()) {
                    IMusicApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IMusicApp(
    playerViewModel: PlayerViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        playerViewModel.initPlayer(context)

        // --- GITHUB UPDATER ---
        updateViewModel.checkForUpdates(
            context = context,
            githubOwner = "imagine-nexus",
            githubRepo = "imusic-official"
        )
    }

    val navController = rememberNavController()
    var showFullPlayerSheet by remember { mutableStateOf(false) }

    // --- UPDATE AVAILABLE DIALOG ---
    if (updateViewModel.updateAvailable.value) {
        AlertDialog(
            onDismissRequest = { updateViewModel.updateAvailable.value = false },
            containerColor = PlayerGray,
            title = { Text("Update Available \uD83D\uDE80", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Version ${updateViewModel.newVersionName.value} is ready to download. Get the latest features and bug fixes!", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        updateViewModel.updateAvailable.value = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateViewModel.updateUrl.value))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Download Update", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { updateViewModel.updateAvailable.value = false }) {
                    Text("Later", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) },
        containerColor = BackgroundBlack
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen(playerViewModel) }
                composable("search") { SearchScreen(playerViewModel) }
                composable("library") { LibraryScreen(playerViewModel) }
            }

            if (playerViewModel.currentTrack.value != null) {
                MiniPlayer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    playerViewModel = playerViewModel,
                    onExpand = { showFullPlayerSheet = true }
                )
            }
        }
    }

    if (showFullPlayerSheet && playerViewModel.currentTrack.value != null) {
        ModalBottomSheet(
            onDismissRequest = { showFullPlayerSheet = false },
            containerColor = BackgroundBlack,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
        ) {
            FullPlayerView(playerViewModel = playerViewModel, onCollapse = { showFullPlayerSheet = false })
        }
    }
}

// --- Screens ---

@Composable
fun HomeScreen(playerViewModel: PlayerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF2A2A2A), BackgroundBlack), startY = 0f, endY = 1000f)
        ),
        contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Good afternoon", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            CarouselSection(title = "Featured Hits", subtitle = "Trending tracks worldwide", playerViewModel = playerViewModel)
        }
    }
}

@Composable
fun CarouselSection(title: String, subtitle: String, playerViewModel: PlayerViewModel) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        Text(text = title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotEmpty()) Text(text = subtitle, color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(FeaturedSongs) { track ->
                Column(
                    modifier = Modifier.width(140.dp).clickable { playerViewModel.playTrack(track) }
                ) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = null,
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = track.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(text = track.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(playerViewModel: PlayerViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Your Library", color = TextPrimary, fontSize = 24.sp)
    }
}

// --- Player Controls ---

@Composable
fun MiniPlayer(modifier: Modifier = Modifier, playerViewModel: PlayerViewModel, onExpand: () -> Unit) {
    val track = playerViewModel.currentTrack.value ?: return

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PlayerGray)
            .clickable { onExpand() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumArtUrl, contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(track.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = { playerViewModel.togglePlayPause() }) {
            Icon(
                if (playerViewModel.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause", tint = TextPrimary, modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun FullPlayerView(playerViewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val track = playerViewModel.currentTrack.value ?: return

    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).background(BackgroundBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "Close", tint = TextPrimary) }
            Text("NOW PLAYING", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, "More", tint = TextPrimary) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        AsyncImage(
            model = track.albumArtUrl, contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = TextSecondary, fontSize = 16.sp, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = playerViewModel.currentPosition.value.toFloat(),
            onValueChange = { playerViewModel.seekTo(it.toLong()) },
            valueRange = 0f..(playerViewModel.duration.value.toFloat().coerceAtLeast(1f)),
            colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary, inactiveTrackColor = Color.DarkGray)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playerViewModel.currentPosition.value), color = TextSecondary, fontSize = 12.sp)
            Text(formatTime(playerViewModel.duration.value), color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { playerViewModel.skipBackward() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp)) }
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(TextPrimary).clickable { playerViewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (playerViewModel.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = BackgroundBlack, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { playerViewModel.skipForward() }) { Icon(Icons.Default.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(36.dp)) }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// --- Bottom Nav ---
@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(NavItem("Home", "home", Icons.Default.Home), NavItem("Search", "search", Icons.Default.Search), NavItem("Library", "library", Icons.Default.LibraryMusic))
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = Color.Black, contentColor = TextSecondary, modifier = Modifier.height(72.dp)) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(28.dp)) },
                label = { Text(item.title, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = TextPrimary, unselectedIconColor = TextSecondary, selectedTextColor = TextPrimary, unselectedTextColor = TextSecondary, indicatorColor = Color.Transparent)
            )
        }
    }
}
data class NavItem(val title: String, val route: String, val icon: ImageVector)

// --- Search Logic & UI (Official YouTube API) ---
class SearchViewModel : ViewModel() {
    val searchQuery = mutableStateOf("")
    val searchResults = mutableStateOf<List<MusicTrack>>(emptyList())
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    fun performSearch() {
        if (searchQuery.value.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                // Calls the official Google YouTube Data API
                val response = YouTubeClient.api.searchVideos(searchQuery.value, YouTubeClient.API_KEY)

                searchResults.value = response.items.mapNotNull { yt ->
                    val vidId = yt.id.videoId ?: return@mapNotNull null

                    MusicTrack(
                        id = vidId,
                        title = yt.snippet.title,
                        artist = yt.snippet.channelTitle,
                        albumArtUrl = yt.snippet.thumbnails.high.url,
                        streamUrl = "" // Extracted dynamically by PlayerManager!
                    )
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 400 || e.code() == 403) {
                    errorMessage.value = "API Error: Invalid YouTube API Key!"
                } else {
                    errorMessage.value = "YouTube API Error ${e.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Network Error: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(playerViewModel: PlayerViewModel, searchViewModel: SearchViewModel = viewModel()) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundBlack).padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Discover Music",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchViewModel.searchQuery.value,
            onValueChange = { searchViewModel.searchQuery.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search tracks, artists...", color = TextSecondary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = AccentPrimary,
                unfocusedIndicatorColor = Color.DarkGray,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { searchViewModel.performSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchViewModel.errorMessage.value != null) {
            Text(text = searchViewModel.errorMessage.value!!, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }

        if (searchViewModel.isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPrimary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(searchViewModel.searchResults.value) { musicTrack ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { playerViewModel.playTrack(musicTrack) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = musicTrack.albumArtUrl, contentDescription = "Cover",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = musicTrack.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(text = musicTrack.artist, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
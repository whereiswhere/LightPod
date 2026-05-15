package com.lightmusic.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightmusic.data.Album
import com.lightmusic.data.Artist
import com.lightmusic.data.Song
import com.lightmusic.ui.components.LightBottomBar
import com.lightmusic.ui.components.LightTopBar
import com.lightmusic.ui.components.MusicListItem
import com.lightmusic.ui.theme.LightOSBlack
import com.lightmusic.ui.theme.LightOSDim
import com.lightmusic.ui.theme.LightOSWhite
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lightmusic.R

// ── Navigation ────────────────────────────────────────────────────
sealed class Screen {
    object Home                                     : Screen()
    object Artists                                  : Screen()
    data class ArtistDetail(val artist: Artist)     : Screen()
    object Albums                                   : Screen()
    data class AlbumDetail(val album: Album)        : Screen()
    object Songs                                    : Screen()
    object NowPlaying : Screen()
}

private fun AudioManager.isAnyBluetoothConnected() =
getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET   ||
    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
}


@Composable
private fun Screen.localizedTitle(): String = when (this) {
    is Screen.Home         -> ""
    is Screen.Artists      -> stringResource(R.string.nav_artists)
    is Screen.ArtistDetail -> artist.name
    is Screen.Albums       -> stringResource(R.string.nav_albums)
    is Screen.AlbumDetail  -> album.title
    is Screen.Songs        -> stringResource(R.string.nav_songs)
    is Screen.NowPlaying   -> ""
}


// ── Root ──────────────────────────────────────────────────────────
@Composable
fun HomeScreen(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.loadMusic()
    }

    val audioManager = remember {
        context.getSystemService(AudioManager::class.java)
    }

    var isBluetoothConnected by remember { mutableStateOf(audioManager.isAnyBluetoothConnected()) }

    DisposableEffect(Unit) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                isBluetoothConnected = audioManager.isAnyBluetoothConnected()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                isBluetoothConnected = audioManager.isAnyBluetoothConnected()
            }
        }
        audioManager.registerAudioDeviceCallback(
            callback,
            Handler(Looper.getMainLooper())
        )
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }



    LaunchedEffect(Unit) {
        if (hasPermission) vm.loadMusic()
        else permissionLauncher.launch(permission)
    }


    var screenStack      by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val currentScreen     = screenStack.last()
    var searchActive      by remember { mutableStateOf(false) }
    var searchQuery       by remember { mutableStateOf("") }
    // True only the moment search opens — reset after focus is consumed.
    // Persists at this level so returning from NowPlaying finds it false
    // and does NOT re-show the keyboard.
    var searchJustOpened  by remember { mutableStateOf(false) }
    var savedScreenStack by remember { mutableStateOf<List<Screen>?>(null) }

    fun push(screen: Screen) { screenStack = screenStack + screen }
    fun back() {
        when {
            screenStack.size > 1 -> screenStack = screenStack.dropLast(1)
            searchActive -> {
                searchActive = false
                searchQuery  = ""
                // Restore the screen the user came from before opening search
                val saved = savedScreenStack
                if (saved != null) {
                    screenStack      = saved
                    savedScreenStack = null
                }
            }
        }
    }


    fun playSong(song: Song, queue: List<Song>) {
        if (vm.currentSong?.id != song.id) vm.playSong(song, queue)
        push(Screen.NowPlaying)
    }

    LaunchedEffect(vm.currentSong) {
        if (vm.currentSong == null && currentScreen is Screen.NowPlaying) {
            back()
        }
    }

    val isNowPlaying   = currentScreen is Screen.NowPlaying
    val isSearchOnHome = searchActive && currentScreen == Screen.Home

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightOSBlack)
            .systemBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────
        when {
            isSearchOnHome -> LightTopBar(
                title               = "",
                onBack              = { back() },
                searchActive        = true,
                searchQuery         = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                autoFocusSearch     = searchJustOpened,
                onAutoFocusConsumed = { searchJustOpened = false }
            )
            currentScreen != Screen.Home -> LightTopBar(
                title  = currentScreen.localizedTitle(),
                onBack = { back() }
            )
            else -> Spacer(modifier = Modifier.height(28.dp))
        }

        // ── Content ───────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasPermission -> PermissionMessage()
                vm.isLoading   -> { /* blank while loading */ }
                isSearchOnHome -> SearchResults(
                    query         = searchQuery,
                    songs         = vm.songs,
                    currentSongId = vm.currentSong?.id,
                    onSongClick   = { song ->
                        // Keep searchActive true so back from NowPlaying returns here
                        playSong(song, vm.songs)
                    }
                )
                currentScreen == Screen.Home -> HomeMenu(
                    onNavigate   = { push(it) },
                    currentSong  = vm.currentSong,
                    onNowPlaying = { push(Screen.NowPlaying) }
                )
                currentScreen == Screen.Artists -> ArtistsList(
                    artists       = vm.artists,
                    onArtistClick = { push(Screen.ArtistDetail(it)) },
                    vm            = vm
                )
                currentScreen is Screen.ArtistDetail -> AlbumsList(
                    albums       = currentScreen.artist.albums,
                    onAlbumClick = { push(Screen.AlbumDetail(it)) },
                    vm           = vm,
                    scrollKey    = "artist_${currentScreen.artist.name}"
                )
                currentScreen == Screen.Albums -> AlbumsList(
                    albums       = vm.albums,
                    onAlbumClick = { push(Screen.AlbumDetail(it)) },
                    vm           = vm
                )
                currentScreen is Screen.AlbumDetail -> SongsList(
                    songs           = currentScreen.album.songs,
                    showTrackNumber = true,
                    currentSongId   = vm.currentSong?.id,
                    onSongClick     = { song ->
                        playSong(song, currentScreen.album.songs)
                    },
                    vm              = vm,
                    scrollKey       = "album_${currentScreen.album.title}"
                )
                currentScreen == Screen.Songs -> SongsList(
                    songs         = vm.songs,
                    currentSongId = vm.currentSong?.id,
                    onSongClick   = { song -> playSong(song, vm.songs) },
                    vm            = vm
                )
                currentScreen is Screen.NowPlaying -> NowPlayingScreen(vm = vm)
            }
        }

        // ── Bottom bar ────────────────────────────────────────────
        if (isNowPlaying) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { vm.toggleShuffle() }
                ) {
                    Icon(Icons.Outlined.Shuffle, "Shuffle", tint = LightOSWhite,
                        modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(24.dp).height(1.5.dp)
                            .background(if (vm.isShuffling) LightOSWhite else Color.Transparent)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Bluetooth,
                        contentDescription = "Bluetooth",
                        tint               = LightOSWhite,
                        modifier           = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(24.dp).height(1.5.dp)
                            .background(if (isBluetoothConnected) LightOSWhite else Color.Transparent)
                    )
                }


                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { vm.toggleLoop() }
                ) {
                    Icon(Icons.Outlined.Repeat, "Repeat", tint = LightOSWhite,
                        modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(24.dp).height(1.5.dp)
                            .background(if (vm.isLooping) LightOSWhite else Color.Transparent)
                    )
                }
            }
        } else {
            LightBottomBar(
                shuffleOn       = vm.isShuffling,
                onShuffleToggle = {
                    if (vm.currentSong == null) {
                        if (vm.songs.isNotEmpty()) {
                            if (!vm.isShuffling) vm.toggleShuffle()
                            val song = vm.songs.random()
                            vm.playSong(song, vm.songs)
                            push(Screen.NowPlaying)
                        }
                    } else {
                        vm.toggleShuffle()
                    }
                },
                onSearchOpen = {
                    if (currentScreen != Screen.Home) {
                        savedScreenStack = screenStack          // remember where we came from
                        screenStack      = listOf(Screen.Home)  // reset to Home so search UI renders
                    }
                    searchActive     = true
                    searchJustOpened = true
                }

            )

        }
    }

    // Volume overlay — floats above everything
    AnimatedVisibility(
        visible  = vm.volumeVisible,
        enter    = fadeIn(animationSpec = tween(120)),
        exit     = fadeOut(animationSpec = tween(400)),
        modifier = Modifier.fillMaxSize()
    ) {
        VolumeOverlay(level = vm.volumeLevel)
    }
}
}

// ── Screens ───────────────────────────────────────────────────────

@Composable
private fun HomeMenu(
    onNavigate: (Screen) -> Unit,
    currentSong: Song?,
    onNowPlaying: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        listOf(
            stringResource(R.string.nav_artists) to Screen.Artists,
            stringResource(R.string.nav_albums)  to Screen.Albums,
            stringResource(R.string.nav_songs)   to Screen.Songs
        ).forEach { (label, screen) ->
            Text(
                text     = label,
                style    = MaterialTheme.typography.displaySmall,
                color    = LightOSWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { onNavigate(screen) }
                    .padding(horizontal = 24.dp, vertical = 5.dp)
            )
        }
        if (currentSong != null) {
            Spacer(modifier = Modifier.height(50.dp))
            Text(
                text     = stringResource(R.string.nav_now_playing),
                style    = MaterialTheme.typography.displaySmall,
                color    = LightOSWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { onNowPlaying() }
                    .padding(horizontal = 24.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun ArtistsList(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    vm: MusicViewModel
) {
    val scrollKey = "artists"
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex        = vm.getScrollIndex(scrollKey),
        initialFirstVisibleItemScrollOffset = vm.getScrollOffset(scrollKey)
    )
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        vm.saveScroll(scrollKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            items(artists, key = { it.name }) { artist ->
                MusicListItem(
                    title    = artist.name,
                    subtitle = pluralStringResource(
                        R.plurals.artist_album_count,
                        artist.albums.size,
                        artist.albums.size
                    ),
                    onClick  = { onArtistClick(artist) }
                )
            }
        }
        LightScrollbar(state = state)
    }
}

@Composable
private fun AlbumsList(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    vm: MusicViewModel,
    scrollKey: String = "albums"
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex        = vm.getScrollIndex(scrollKey),
        initialFirstVisibleItemScrollOffset = vm.getScrollOffset(scrollKey)
    )
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        vm.saveScroll(scrollKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            items(albums, key = { it.title + it.artist }) { album ->
                MusicListItem(
                    title    = album.title,
                    subtitle = album.artist,
                    onClick  = { onAlbumClick(album) }
                )
            }
        }
        LightScrollbar(state = state)
    }
}

@Composable
private fun SongsList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    showTrackNumber: Boolean = false,
    currentSongId: Long? = null,
    vm: MusicViewModel,
    scrollKey: String = "songs"
) {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex        = vm.getScrollIndex(scrollKey),
        initialFirstVisibleItemScrollOffset = vm.getScrollOffset(scrollKey)
    )
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        vm.saveScroll(scrollKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            items(songs, key = { it.id }) { song ->
                MusicListItem(
                    title              = song.title,
                    subtitle           = song.artist,
                    prefix             = if (showTrackNumber && song.track > 0) "${song.track}" else null,
                    isCurrentlyPlaying = song.id == currentSongId,
                    onClick            = { onSongClick(song) }
                )
            }
        }
        LightScrollbar(state = state)
    }
}

@Composable
private fun SearchResults(
    query: String,
    songs: List<Song>,
    currentSongId: Long? = null,
    onSongClick: (Song) -> Unit
) {
    val q            = query.trim().lowercase()
    val state        = rememberLazyListState()
    val matchedSongs = remember(q, songs) {
        songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            if (q.isEmpty()) return@LazyColumn
            if (matchedSongs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.search_no_results, query),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = LightOSDim,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                return@LazyColumn
            }
            items(matchedSongs) { song ->
                MusicListItem(
                    title              = song.title,
                    subtitle           = song.artist,
                    isCurrentlyPlaying = song.id == currentSongId,
                    onClick            = { onSongClick(song) }
                )
            }
        }
        LightScrollbar(state = state)
    }
}

// ── Helpers ───────────────────────────────────────────────────────

@Composable
private fun PermissionMessage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.permission_required),
            style    = MaterialTheme.typography.bodyMedium,
            color    = LightOSDim,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun LightScrollbar(state: LazyListState) {
    val scope = rememberCoroutineScope()

    val totalItems by remember {
        derivedStateOf { state.layoutInfo.totalItemsCount }
    }
    val visibleCount by remember {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }
    val thumbFraction by remember {
        derivedStateOf {
            if (totalItems == 0) return@derivedStateOf 1f
            (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
        }
    }
    val scrollFraction by remember {
        derivedStateOf {
            val maxScroll = (totalItems - visibleCount).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / maxScroll).coerceIn(0f, 1f)
        }
    }

    if (thumbFraction >= 1f) {
        Spacer(modifier = Modifier.width(16.dp))
        return
    }

    var onThumb          by remember { mutableStateOf(false) }
    var thumbGrabOffset  by remember { mutableStateOf(0f) }
    // Snapshot visibleCount at drag start — prevents maxIdx changing
    // mid-drag as partial items enter/leave visibleItemsInfo
    var snapVisibleCount by remember { mutableStateOf(0) }

    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .padding(end = 10.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        snapVisibleCount = visibleCount   // ← lock in at drag start
                        val h          = size.height.toFloat()
                        val minThumbPx = 28.dp.toPx()
                        val thumbPx    = (h * thumbFraction).coerceAtLeast(minThumbPx)
                        val thumbTop   = ((h - thumbPx) * scrollFraction)
                            .coerceIn(0f, h - thumbPx)
                        onThumb = offset.y >= thumbTop && offset.y <= thumbTop + thumbPx
                        if (onThumb) thumbGrabOffset = offset.y - thumbTop
                    },
                    onDragEnd    = { onThumb = false },
                    onDragCancel = { onThumb = false },
                    onDrag = { change, _ ->
                        if (!onThumb) return@detectDragGestures
                        change.consume()
                        val h          = size.height.toFloat()
                        val minThumbPx = 28.dp.toPx()
                        val thumbPx    = (h * thumbFraction).coerceAtLeast(minThumbPx)
                        val maxTravel  = (h - thumbPx).coerceAtLeast(1f)

                        val newThumbTop = (change.position.y - thumbGrabOffset)
                            .coerceIn(0f, maxTravel)
                        val newFraction = newThumbTop / maxTravel

                        // +1 ensures last item isn't hidden behind bottom bar —
                        // snapped count prevents mid-drag maxIdx changes
                        val maxIdx = (totalItems - snapVisibleCount + 1)
                            .coerceIn(0, totalItems - 1)
                        val target = (newFraction * maxIdx)
                            .roundToInt()
                            .coerceIn(0, totalItems - 1)
                        scope.launch { state.scrollToItem(target) }
                    }
                )
            }
    ) {
        val cx         = size.width / 2f
        val thinPx     = 1.5.dp.toPx()
        val thickPx    = 5.dp.toPx()
        val h          = size.height
        val minThumbPx = 28.dp.toPx()
        val thumbPx    = (h * thumbFraction).coerceAtLeast(minThumbPx)
        val thumbTop   = ((h - thumbPx) * scrollFraction).coerceIn(0f, h - thumbPx)

        drawRect(Color.White, Offset(cx - thinPx  / 2f, 0f),      Size(thinPx,  h))
        drawRect(Color.White, Offset(cx - thickPx / 2f, thumbTop), Size(thickPx, thumbPx))
    }
}



@Composable
private fun VolumeOverlay(level: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightOSBlack)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-48).dp)       // slightly above center, matching LP3
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = stringResource(R.string.volume),
                style = MaterialTheme.typography.displaySmall,
                color = LightOSWhite
            )
            Spacer(modifier = Modifier.height(36.dp))
            // Same thick/thin bar as NowPlaying progress bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val cy        = size.height / 2f
                val thickPx   = 6.dp.toPx()
                val thinPx    = 1.5.dp.toPx()
                val progressX = size.width * level.coerceIn(0f, 1f)

                // Thin — remaining volume
                drawRect(
                    color   = Color.White,
                    topLeft = Offset(progressX, cy - thinPx / 2f),
                    size    = Size((size.width - progressX).coerceAtLeast(0f), thinPx)
                )
                // Thick — current volume level
                if (progressX > 0f) {
                    drawRect(
                        color   = Color.White,
                        topLeft = Offset(0f, cy - thickPx / 2f),
                        size    = Size(progressX, thickPx)
                    )
                }
            }
        }
    }
}

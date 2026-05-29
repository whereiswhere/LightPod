package com.lightpod.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightpod.R
import com.lightpod.data.Album
import com.lightpod.data.Artist
import com.lightpod.data.Song
import com.lightpod.ui.MusicViewModel
import com.lightpod.ui.components.LightBottomBar
import com.lightpod.ui.components.LightTopBar
import com.lightpod.ui.components.MusicListItem
import com.lightpod.ui.theme.LightOSBlack
import com.lightpod.ui.theme.LightOSDim
import com.lightpod.ui.theme.LightOSWhite
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Navigation ────────────────────────────────────────────────────
sealed class Screen {
    object Home                                     : Screen()
    object Artists                                  : Screen()
    data class ArtistDetail(val artist: Artist)     : Screen()
    object Albums                                   : Screen()
    data class AlbumDetail(val album: Album)        : Screen()
    object Songs                                    : Screen()
    object NowPlaying                               : Screen()
}

private fun AudioManager.isAnyBluetoothConnected() =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO  ||
        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET    ||
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

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
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
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) vm.loadMusic()
        else permissionLauncher.launch(permission)
    }

    var lyricsVisible by remember { mutableStateOf(false) }

    var screenStack     by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val currentScreen    = screenStack.last()
    var searchActive     by remember { mutableStateOf(false) }
    var searchQuery      by remember { mutableStateOf("") }
    var searchJustOpened by remember { mutableStateOf(false) }
    var savedScreenStack by remember { mutableStateOf<List<Screen>?>(null) }

    fun push(screen: Screen) { screenStack = screenStack + screen }
    fun back() {
        when {
            screenStack.size > 1 -> screenStack = screenStack.dropLast(1)
            searchActive -> {
                searchActive = false
                searchQuery  = ""
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
        if (vm.currentSong == null && currentScreen is Screen.NowPlaying) back()
        lyricsVisible = false
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
            // ── Top bar ───────────────────────────────────────────
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

            // ── Content ───────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    !hasPermission -> PermissionMessage()
                    isSearchOnHome -> SearchResults(
                        query         = searchQuery,
                        songs         = vm.songs,
                        currentSongId = vm.currentSong?.id,
                        onSongClick   = { song -> playSong(song, vm.songs) }
                    )
                    currentScreen == Screen.Home -> HomeMenu(
                        onNavigate   = { push(it) },
                        currentSong  = vm.currentSong,
                        onNowPlaying = { push(Screen.NowPlaying) }
                    )
                    vm.isLoading -> { /* blank only for list screens while data loads */ }
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
                        onSongClick     = { song -> playSong(song, currentScreen.album.songs) },
                        vm              = vm,
                        scrollKey       = "album_${currentScreen.album.title}"
                    )
                    currentScreen == Screen.Songs -> SongsList(
                        songs         = vm.songs,
                        currentSongId = vm.currentSong?.id,
                        onSongClick   = { song -> playSong(song, vm.songs) },
                        vm            = vm
                    )
                    currentScreen is Screen.NowPlaying -> NowPlayingScreen(
                        vm           = vm,
                        onShowLyrics = { lyricsVisible = true }
                    )
                }
            }

            // ── Bottom bar ────────────────────────────────────────
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
                            savedScreenStack = screenStack
                            screenStack      = listOf(Screen.Home)
                        }
                        searchActive     = true
                        searchJustOpened = true
                    }
                )
            }
        }

        // ── Lyrics overlay ────────────────────────────────────────
        if (lyricsVisible && vm.currentLyrics != null) {
            LyricsOverlay(
                lyrics    = vm.currentLyrics ?: "",
                title     = vm.currentSong?.title ?: "",
                onDismiss = { lyricsVisible = false }
            )
        }

        // ── Volume overlay ────────────────────────────────────────
        if (vm.volumeVisible) {
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
                        text     = stringResource(R.string.search_no_results, query),
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
            text     = stringResource(R.string.permission_required),
            style    = MaterialTheme.typography.bodyMedium,
            color    = LightOSDim,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun LightScrollbar(state: LazyListState) {
    val scope = rememberCoroutineScope()

    val thumbFraction by remember {
        derivedStateOf {
            val info    = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (info.totalItemsCount == 0 || visible.isEmpty()) return@derivedStateOf 1f
            val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val avgSize   = visible.sumOf { it.size }.toFloat() / visible.size
            val totalH    = avgSize * info.totalItemsCount
            (viewportH / totalH).coerceIn(0.08f, 1f)
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            val info    = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (info.totalItemsCount == 0 || visible.isEmpty()) return@derivedStateOf 0f
            val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val avgSize   = visible.sumOf { it.size }.toFloat() / visible.size
            val totalH    = avgSize * info.totalItemsCount
            val maxScroll = (totalH - viewportH).coerceAtLeast(1f)
            val first     = visible.first()
            val scrolled  = first.index * avgSize - first.offset
            (scrolled / maxScroll).coerceIn(0f, 1f)
        }
    }

    if (thumbFraction >= 1f) {
        Spacer(modifier = Modifier.width(16.dp))
        return
    }

    var onThumb         by remember { mutableStateOf(false) }
    var thumbGrabOffset by remember { mutableStateOf(0f) }
    var snapAvgSize     by remember { mutableStateOf(0f) }
    var snapViewportH   by remember { mutableStateOf(0f) }
    var snapTotalH      by remember { mutableStateOf(0f) }

    Canvas(
        modifier = Modifier
            .fillMaxHeight()
            .width(16.dp)
            .padding(end = 10.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val info    = state.layoutInfo
                        val visible = info.visibleItemsInfo
                        if (visible.isEmpty()) return@detectDragGestures

                        snapViewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                        snapAvgSize   = visible.sumOf { it.size }.toFloat() / visible.size
                        snapTotalH    = snapAvgSize * info.totalItemsCount

                        val h             = size.height.toFloat()
                        val minThumbPx    = 28.dp.toPx()
                        val snapThumbFrac = (snapViewportH / snapTotalH).coerceIn(0.08f, 1f)
                        val thumbPx       = (h * snapThumbFrac).coerceAtLeast(minThumbPx)
                        val maxScroll     = (snapTotalH - snapViewportH).coerceAtLeast(1f)
                        val first         = visible.first()
                        val scrolled      = first.index * snapAvgSize - first.offset
                        val snapScrollFrac = (scrolled / maxScroll).coerceIn(0f, 1f)
                        val thumbTop      = ((h - thumbPx) * snapScrollFrac).coerceIn(0f, h - thumbPx)

                        onThumb = offset.y >= thumbTop && offset.y <= thumbTop + thumbPx
                        if (onThumb) thumbGrabOffset = offset.y - thumbTop
                    },
                    onDragEnd    = { onThumb = false },
                    onDragCancel = { onThumb = false },
                    onDrag = { change, _ ->
                        if (!onThumb) return@detectDragGestures
                        change.consume()
                        if (snapAvgSize <= 0f || snapTotalH <= snapViewportH) return@detectDragGestures

                        val h             = size.height.toFloat()
                        val minThumbPx    = 28.dp.toPx()
                        val snapThumbFrac = (snapViewportH / snapTotalH).coerceIn(0.08f, 1f)
                        val thumbPx       = (h * snapThumbFrac).coerceAtLeast(minThumbPx)
                        val maxTravel     = (h - thumbPx).coerceAtLeast(1f)

                        val newThumbTop  = (change.position.y - thumbGrabOffset).coerceIn(0f, maxTravel)
                        val newFraction  = newThumbTop / maxTravel
                        val maxScrollPx  = snapTotalH - snapViewportH
                        val targetPx     = (newFraction * maxScrollPx).coerceIn(0f, maxScrollPx)
                        val targetIndex  = (targetPx / snapAvgSize)
                            .toInt().coerceIn(0, state.layoutInfo.totalItemsCount - 1)
                        val targetOffset = (targetPx - targetIndex * snapAvgSize)
                            .toInt().coerceAtLeast(0)

                        scope.launch { state.scrollToItem(targetIndex, targetOffset) }
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
                .offset(y = (-48).dp)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = stringResource(R.string.volume),
                style = MaterialTheme.typography.displaySmall,
                color = LightOSWhite
            )
            Spacer(modifier = Modifier.height(36.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val cy        = size.height / 2f
                val thickPx   = 6.dp.toPx()
                val thinPx    = 1.5.dp.toPx()
                val progressX = size.width * level.coerceIn(0f, 1f)

                drawRect(
                    color   = Color.White,
                    topLeft = Offset(progressX, cy - thinPx / 2f),
                    size    = Size((size.width - progressX).coerceAtLeast(0f), thinPx)
                )
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

@Composable
private fun LyricsOverlay(
    lyrics: String,
    title: String,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightOSBlack)
            .systemBarsPadding()
    ) {
        LightTopBar(
            title  = title,
            onBack = onDismiss
        )
        Text(
            text     = lyrics,
            style    = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp),
            color    = LightOSWhite,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(
                    start  = 24.dp,
                    end    = 24.dp,
                    top    = 8.dp,
                    bottom = 24.dp
                )
        )
    }
}

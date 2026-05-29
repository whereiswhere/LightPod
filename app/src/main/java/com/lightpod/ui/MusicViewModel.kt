package com.lightpod.ui

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lightpod.PlaybackService
import com.lightpod.data.Album
import com.lightpod.data.Artist
import com.lightpod.data.MusicRepository
import com.lightpod.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    var songs     by mutableStateOf<List<Song>>(emptyList())
    var artists   by mutableStateOf<List<Artist>>(emptyList())
    var albums    by mutableStateOf<List<Album>>(emptyList())
    var isLoading by mutableStateOf(true)
    private var hasLoadedOnce = false

    var currentLyrics by mutableStateOf<String?>(null); private set
    private var lyricsFetchJob: Job? = null

    // ── Scroll persistence ────────────────────────────────────────
    private val scrollPositions = mutableStateMapOf<String, Pair<Int, Int>>()
    fun getScrollIndex(key: String)  = scrollPositions[key]?.first  ?: 0
    fun getScrollOffset(key: String) = scrollPositions[key]?.second ?: 0
    fun saveScroll(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index to offset
    }

    private var reloadJob: Job? = null

    // ── Playback ──────────────────────────────────────────────────
    private var mediaController: MediaController? = null
    val player: Player? get() = mediaController

    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying   by mutableStateOf(false)      ; private set
    var isShuffling by mutableStateOf(false)      ; private set
    var isLooping   by mutableStateOf(false)      ; private set
    private var _queue = listOf<Song>()

    // ── Volume overlay ────────────────────────────────────────────
    var volumeVisible by mutableStateOf(false); private set
    var volumeLevel   by mutableStateOf(0f)   ; private set
    private var volumeHideJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = mediaController?.currentMediaItemIndex ?: return
            if (index in _queue.indices) {
                currentSong = _queue[index]
                fetchLyrics(_queue[index])
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            isShuffling = shuffleModeEnabled
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            isLooping = repeatMode == Player.REPEAT_MODE_ONE
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                currentSong   = null
                isPlaying     = false
                currentLyrics = null
            }
        }
    }

    // ── MediaStore observer ───────────────────────────────────────
    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch {
                delay(1000)
                loadMusic()
            }
        }
    }

    init {
        val token = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        val future = MediaController.Builder(application, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                isPlaying   = controller.isPlaying
                isShuffling = controller.shuffleModeEnabled
                isLooping   = controller.repeatMode == Player.REPEAT_MODE_ONE
                if (controller.playbackState == Player.STATE_ENDED) {
                    currentSong = null
                } else {
                    restorePlaybackState()
                }
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(application))

        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun loadMusic() {
        if (!hasLoadedOnce) isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            repository.scanMusicFolder()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val (a, b, s) = repository.loadAll()
            withContext(Dispatchers.Main) {
                artists       = a
                albums        = b
                songs         = s
                isLoading     = false
                hasLoadedOnce = true
                restorePlaybackState()
            }
        }
    }

    private fun restorePlaybackState() {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        if (controller.playbackState == Player.STATE_ENDED) {
            currentSong = null
            return
        }

        if (_queue.isEmpty() && controller.mediaItemCount > 0) {
            _queue = (0 until controller.mediaItemCount).mapNotNull { i ->
                try {
                    val id = controller.getMediaItemAt(i).mediaId
                    songs.find { it.id.toString() == id }
                } catch (_: Exception) { null }
            }
        }

        if (currentSong == null) {
            val id   = controller.currentMediaItem?.mediaId ?: return
            val song = songs.find { it.id.toString() == id } ?: return
            currentSong = song
            fetchLyrics(song)
        }
    }

    private fun fetchLyrics(song: Song) {
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = getApplication<Application>()
                    .contentResolver
                    .getType(song.uri) ?: "audio/mpeg"
                val ext = when (mimeType) {
                    "audio/mpeg", "audio/mp3"         -> "mp3"
                    "audio/mp4", "audio/m4a",
                    "audio/aac", "audio/x-aac"        -> "m4a"
                    "audio/flac", "audio/x-flac"      -> "flac"
                    "audio/ogg", "audio/x-ogg"        -> "ogg"
                    "audio/opus"                       -> "opus"
                    "audio/x-wav", "audio/wav",
                    "audio/wave"                       -> "wav"
                    "audio/x-ms-wma"                   -> "wma"
                    "audio/aiff", "audio/x-aiff"      -> "aiff"
                    else                               -> "mp3"
                }

                val tmp = java.io.File(
                    getApplication<Application>().cacheDir,
                    "${song.id}.$ext"
                )
                getApplication<Application>()
                    .contentResolver
                    .openInputStream(song.uri)
                    ?.use { input -> tmp.outputStream().use { input.copyTo(it) } }

                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tmp)
                val lyrics = audioFile.tag?.let { tag ->
                    tag.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)
                        ?.takeIf { it.isNotBlank() }
                    ?:
                    try {
                        tag.getFields("TXXX")
                            ?.asSequence()
                            ?.mapNotNull { field ->
                                val frame = field as? org.jaudiotagger.tag.id3.AbstractID3v2Frame
                                val body  = frame?.body as? org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
                                if (body?.description == "USLT") {
                                    body.firstTextValue?.takeIf { it.isNotBlank() }
                                } else null
                            }
                            ?.firstOrNull()
                    } catch (_: Exception) { null }
                }
                withContext(Dispatchers.Main) { currentLyrics = lyrics }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { currentLyrics = null }
            }
        }
    }

    fun playSong(song: Song, queue: List<Song>) {
        val controller = mediaController ?: return
        _queue      = queue
        currentSong = song
        fetchLyrics(song)
        val index = queue.indexOf(song).coerceAtLeast(0)
        controller.setMediaItems(
            queue.map { s ->
                MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())
                    .build()
            },
            index, 0L
        )
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val c = mediaController ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() {
        val c = mediaController ?: return
        if (c.repeatMode != Player.REPEAT_MODE_OFF) c.repeatMode = Player.REPEAT_MODE_OFF
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    fun skipPrev() {
        val c = mediaController ?: return
        if (c.repeatMode != Player.REPEAT_MODE_OFF) c.repeatMode = Player.REPEAT_MODE_OFF
        if (c.currentPosition > 3000L) c.seekTo(0L)
        else if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { mediaController?.seekTo(positionMs) }

    fun toggleShuffle() {
        val c = mediaController ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun toggleLoop() {
        val c = mediaController ?: return
        c.repeatMode = if (c.repeatMode == Player.REPEAT_MODE_OFF)
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun showVolume(current: Int, max: Int) {
        volumeLevel   = if (max > 0) current.toFloat() / max.toFloat() else 0f
        volumeVisible = true
        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(1500)
            volumeVisible = false
        }
    }

    override fun onCleared() {
        lyricsFetchJob?.cancel()
        getApplication<Application>().cacheDir
            .listFiles()
            ?.forEach { it.delete() }
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        getApplication<Application>().contentResolver
            .unregisterContentObserver(contentObserver)
        super.onCleared()
    }
}

package com.lightmusic.ui.screens

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
import com.lightmusic.PlaybackService
import com.lightmusic.data.Album
import com.lightmusic.data.Artist
import com.lightmusic.data.MusicRepository
import com.lightmusic.data.Song
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

    // ── Scroll persistence: index + pixel offset ──────────────────
    private val scrollPositions = mutableStateMapOf<String, Pair<Int, Int>>()
    fun getScrollIndex(key: String)  = scrollPositions[key]?.first  ?: 0
    fun getScrollOffset(key: String) = scrollPositions[key]?.second ?: 0
    fun saveScroll(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index to offset
    }

    private var reloadJob: Job? = null

    // ── Playback via MediaController → PlaybackService ────────────
    private var mediaController: MediaController? = null
    val player: Player? get() = mediaController

    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying   by mutableStateOf(false)      ; private set
    var isShuffling by mutableStateOf(false)      ; private set
    var isLooping   by mutableStateOf(false)      ; private set
    private var _queue = listOf<Song>()

    // ── Volume overlay ────────────────────────────────────────────────
    var volumeVisible by mutableStateOf(false); private set
    var volumeLevel   by mutableStateOf(0f);    private set
    private var volumeHideJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = mediaController?.currentMediaItemIndex ?: return
            if (index in _queue.indices) currentSong = _queue[index]
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            isShuffling = shuffleModeEnabled
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            isLooping = repeatMode == Player.REPEAT_MODE_ONE
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // Queue exhausted naturally — clear current song so
                // "Now Playing" button and underline indicators disappear
                currentSong = null
                isPlaying   = false
            }
        }
    }

    // ── MediaStore observer (stored so it can be unregistered) ────
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
        // Connect to PlaybackService
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
                // If queue already ended before we connected, clear rather than restore
                if (controller.playbackState == Player.STATE_ENDED) {
                    currentSong = null
                } else {
                    restorePlaybackState()
                }
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(application))


        // Watch for new audio files
        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun loadMusic() {
        if (!hasLoadedOnce) isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val (a, b, s) = repository.loadAll()
            withContext(Dispatchers.Main) {
                artists       = a
                albums        = b
                songs         = s
                isLoading     = false
                hasLoadedOnce = true
                // Attempt to restore current song now that songs are available
                restorePlaybackState()
            }
        }
    }

    /**
     * After process death: the service keeps playing but the ViewModel is new.
     * This rebuilds _queue and currentSong from the controller's media items,
     * matched by the mediaId we embedded when calling playSong().
     */
     private fun restorePlaybackState() {
         val controller = mediaController ?: return
         if (songs.isEmpty()) return

         // If queue naturally ended, clear current song rather than restoring it
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
             val id = controller.currentMediaItem?.mediaId ?: return
             currentSong = songs.find { it.id.toString() == id }
         }
     }


    fun playSong(song: Song, queue: List<Song>) {
        val controller = mediaController ?: return
        _queue      = queue
        currentSong = song
        val index   = queue.indexOf(song).coerceAtLeast(0)
        controller.setMediaItems(
            queue.map { s ->
                MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())  // used for state restoration
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
        // Turn off repeat when manually skipping
        if (c.repeatMode != Player.REPEAT_MODE_OFF) {
            c.repeatMode = Player.REPEAT_MODE_OFF
        }
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    fun skipPrev() {
        val c = mediaController ?: return
        // Turn off repeat when manually skipping
        if (c.repeatMode != Player.REPEAT_MODE_OFF) {
            c.repeatMode = Player.REPEAT_MODE_OFF
        }
        if (c.currentPosition > 3000L) c.seekTo(0L)
        else if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

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
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
        super.onCleared()
    }
}

package com.lightmusic

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.lightmusic.ui.screens.HomeScreen
import com.lightmusic.ui.screens.MusicViewModel
import com.lightmusic.ui.theme.LightMusicTheme

class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var vm: MusicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioManager = getSystemService(AudioManager::class.java)
        vm = ViewModelProvider(this)[MusicViewModel::class.java]

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                vm.loadMusic()
            }
        })

        setContent {
            LightMusicTheme {
                HomeScreen(vm = vm)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        0
                    )
                    notifyVolume()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        0
                    )
                    notifyVolume()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun notifyVolume() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        vm.showVolume(current, max)
    }
}

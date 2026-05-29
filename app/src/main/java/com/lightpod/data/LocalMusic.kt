package com.lightpod.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val duration: Long,
    val track: Int,
    val uri: Uri
) {
    val durationFormatted: String get() {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

data class Album(
    val title: String,
    val artist: String,
    val songs: List<Song>
)

data class Artist(
    val name: String,
    val albums: List<Album>
)

package com.lightpod.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore

class MusicRepository(private val context: Context) {

    fun loadAll(): Triple<List<Artist>, List<Album>, List<Song>> {
        val raw = querySongs()

        // ── Artists ───────────────────────────────────────────────
        val artistToSongs = mutableMapOf<String, MutableList<Song>>()
        for (song in raw) {
            for (individualArtist in song.artist.splitArtists()) {
                artistToSongs
                    .getOrPut(individualArtist) { mutableListOf() }
                    .add(song)
            }
        }
        val artists = artistToSongs.entries
            .map { (artistName, artistSongs) ->
                Artist(
                    name   = artistName,
                    albums = artistSongs
                        .groupBy { it.album }
                        .map { (albumName, albumSongs) ->
                            Album(
                                title  = albumName,
                                artist = albumSongs
                                    .firstOrNull { it.albumArtist.isNotBlank() }
                                    ?.albumArtist ?: albumSongs.first().artist,
                                songs  = albumSongs.sortedBy { it.track }
                            )
                        }
                        .sortedBy { it.title }
                )
            }
            .sortedBy { it.name }

        // ── Albums ────────────────────────────────────────────────
        val albums = raw
            .groupBy { it.album }
            .map { (albumName, albumSongs) ->
                Album(
                    title  = albumName,
                    artist = albumSongs
                        .firstOrNull { it.albumArtist.isNotBlank() }
                        ?.albumArtist ?: albumSongs.first().artist,
                    songs  = albumSongs.sortedBy { it.track }
                )
            }
            .sortedBy { it.title }

        val songs = raw.sortedBy { it.title }

        return Triple(artists, albums, songs)
    }

    fun scanMusicFolder() {
        val musicDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        )
        val audioExtensions = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "mp4",
            "wma", "aiff", "aif"
        )
        val filesToScan = try {
            musicDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                .map    { it.absolutePath }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
        if (filesToScan.isEmpty()) return

        MediaScannerConnection.scanFile(
            context,
            filesToScan.toTypedArray(),
            null,
            null
        )
    }

    private fun querySongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        data class RawEntry(val song: Song, val inMusicFolder: Boolean)
        val raw = mutableListOf<RawEntry>()

        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol          = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val albumCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val pathCol        = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: ""

                val fromDashboard   = path.startsWith("Download/Persisted/Music/", ignoreCase = true)
                val fromMusicFolder = path.startsWith("Music/", ignoreCase = true) ||
                                      path.equals("Music", ignoreCase = true)
                if (!fromDashboard && !fromMusicFolder) continue

                val rawTrack    = cursor.getInt(trackCol)
                val trackNumber = if (rawTrack > 1000) rawTrack % 1000 else rawTrack
                val id          = cursor.getLong(idCol)

                raw.add(
                    RawEntry(
                        song = Song(
                            id          = id,
                            title       = cursor.getString(titleCol)       ?: "Unknown",
                            artist      = cursor.getString(artistCol)      ?: "Unknown Artist",
                            albumArtist = cursor.getString(albumArtistCol) ?: "",
                            album       = cursor.getString(albumCol)       ?: "Unknown Album",
                            duration    = cursor.getLong(durationCol),
                            track       = trackNumber,
                            uri         = ContentUris.withAppendedId(collection, id)
                        ),
                        inMusicFolder = fromMusicFolder
                    )
                )
            }
        }

        // Deduplicate by (title, artist) — prefer Music folder over Dashboard
        val seen = LinkedHashMap<Pair<String, String>, RawEntry>()
        for (entry in raw) {
            val key      = entry.song.title.trim().lowercase() to
                           entry.song.artist.trim().lowercase()
            val existing = seen[key]
            if (existing == null || (entry.inMusicFolder && !existing.inMusicFolder)) {
                seen[key] = entry
            }
        }

        return seen.values.map { it.song }
    }
}

private fun String.splitArtists(): List<String> =
    split(
        " & ",
        " feat. ",
        " ft. ",
        " Feat. ",
        " Ft. ",
        " featuring ",
        " Featuring ",
        " vs. ",
        " vs ",
        " and "
    )
    .map    { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

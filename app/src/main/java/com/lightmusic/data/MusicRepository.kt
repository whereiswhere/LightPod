package com.lightmusic.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MusicRepository(private val context: Context) {

    fun loadAll(): Triple<List<Artist>, List<Album>, List<Song>> {
        val raw = querySongs()

        // ── Artists — split by MusicBrainz join phrases ───────────
        // Each individual artist gets their own entry.
        // Artist "b" who performed one song in album "a" gets their
        // own Artist entry, with Album "a" containing only their songs.
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
                                // Always show the album artist as subtitle,
                                // not the individual artist being viewed
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


        // ── Albums — unchanged, uses albumArtist for global list ───
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

    private fun querySongs(): List<Song> {
        val songs      = mutableListOf<Song>()
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
                if (path.contains("Recordings", ignoreCase = true) ||
                    path.contains("Notes",      ignoreCase = true) ||
                    path.contains("Ringtones",  ignoreCase = true) ||
                    path.contains("Alarms",     ignoreCase = true)
                ) continue

                val rawTrack    = cursor.getInt(trackCol)
                val trackNumber = if (rawTrack > 1000) rawTrack % 1000 else rawTrack
                val id          = cursor.getLong(idCol)

                songs.add(
                    Song(
                        id          = id,
                        title       = cursor.getString(titleCol)       ?: "Unknown",
                        artist      = cursor.getString(artistCol)      ?: "Unknown Artist",
                        albumArtist = cursor.getString(albumArtistCol) ?: "",
                        album       = cursor.getString(albumCol)       ?: "Unknown Album",
                        duration    = cursor.getLong(durationCol),
                        track       = trackNumber,
                        uri         = ContentUris.withAppendedId(collection, id)
                    )
                )
            }
        }
        return songs
    }
}

// MusicBrainz Picard standard join phrases between separate artists.
// "Simon & Garfunkel" is ONE artist in MusicBrainz (single entity),
// so it won't be split — only truly separate collaborating artists
// joined by these phrases will be split.
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
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

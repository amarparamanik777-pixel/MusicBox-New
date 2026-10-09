package com.musicbox.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Reads the songs that Android already knows about (MediaStore). Nothing is uploaded anywhere. */
object MediaScanner {
    fun scan(context: Context, settings: AppSettings): List<Song> {
        val out = ArrayList<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf((settings.minDurationSec * 1000L).toString())
        val excluded = settings.excludedWords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = c.getString(iData) ?: ""
                val lowerPath = path.lowercase()
                if (excluded.any { lowerPath.contains(it) }) continue
                val name = c.getString(iName) ?: "audio_$id"
                val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                out.add(
                    Song(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        title = title,
                        artistTag = artist,
                        album = album,
                        durationMs = c.getLong(iDur),
                        dateAddedSec = c.getLong(iAdded),
                        fileName = name,
                        path = path,
                    )
                )
            }
        }
        return out
    }
}

package com.musicbox.app.player

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.LruCache
import java.io.File
import java.io.FileNotFoundException

/** Cover art that is embedded inside the audio files, cached on disk and in memory. */
object ArtCache {
    private val small = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }
    private val large = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    /** A picture that is already in memory, or null. Never blocks, so it is safe to call while drawing. */
    fun cached(id: Long, big: Boolean): Bitmap? = (if (big) large else small).get(id)

    /** The extracted picture file for a song, or null when the song has no embedded art. */
    fun file(ctx: Context, id: Long): File? {
        val dir = File(ctx.cacheDir, "art")
        val f = File(dir, "$id.jpg")
        if (f.exists()) return f
        // Only one thread extracts at a time, so two list rows never decode the same song twice.
        synchronized(this) {
            return extract(ctx, id, dir, f)
        }
    }

    private fun extract(ctx: Context, id: Long, dir: File, f: File): File? {
        dir.mkdirs()
        if (f.exists()) return f
        val none = File(dir, "$id.none")
        if (none.exists()) return null
        var bytes: ByteArray? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
            bytes = r.embeddedPicture
        } catch (e: Exception) {
            bytes = null
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        if (bytes == null) {
            try {
                none.createNewFile()
            } catch (e: Exception) {
                // ignore
            }
            return null
        }
        return try {
            // Embedded pictures are often 1-3 MB. Keep a screen-sized copy so the cache stays small.
            val shrunk = shrink(bytes)
            val tmp = File(dir, "$id.tmp")
            tmp.writeBytes(shrunk ?: bytes)
            if (tmp.renameTo(f)) f else {
                f.writeBytes(shrunk ?: bytes)
                tmp.delete()
                f
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Re-encodes a big picture as a JPEG of at most about 1200 pixels. Null means "keep the original". */
    private fun shrink(bytes: ByteArray): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0 || (longest <= 1200 && bytes.size < 400_000)) return null
            var sample = 1
            while (longest / (sample * 2) >= 1200) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bmp.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    fun bitmap(ctx: Context, id: Long, big: Boolean): Bitmap? {
        val cache = if (big) large else small
        val hit = cache.get(id)
        if (hit != null) return hit
        val f = file(ctx, id) ?: return null
        val bmp = decode(f, if (big) 1080 else 256) ?: return null
        cache.put(id, bmp)
        return bmp
    }

    /** First song in the list that has artwork. */
    fun firstBitmap(ctx: Context, ids: List<Long>, big: Boolean): Pair<Long, Bitmap>? {
        for (id in ids) {
            val b = bitmap(ctx, id, big)
            if (b != null) return id to b
        }
        return null
    }

    /** Average colour of a picture, as an ARGB int. */
    fun averageColor(b: Bitmap): Int = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)

    private fun decode(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeFile(file.path, opts)
    }
}

/** Lets the notification / lock screen load the cover art through a content:// address. */
class ArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("bad id")
        val ctx = context ?: throw FileNotFoundException("no context")
        val f = ArtCache.file(ctx, id) ?: throw FileNotFoundException("no art")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun uriFor(ctx: Context, id: Long): Uri = Uri.parse("content://${ctx.packageName}.art/song/$id")
    }
}

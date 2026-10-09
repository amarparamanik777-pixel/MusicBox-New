package com.musicbox.app.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicbox.app.data.ResolvedSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val currentId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queueIds: List<Long> = emptyList(),
    val index: Int = -1,
    val sleepEndsAt: Long = 0L,
    val speed: Float = 1f,
)

/** The app's remote control for the background player service. */
class PlayerController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val onStart: (Long) -> Unit,
    private val onCounted: (Long) -> Unit,
) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var countedFor = -1L
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null

    val state = MutableStateFlow(PlayerState())
    val position = MutableStateFlow(0L)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            countedFor = -1L
            mediaItem?.mediaId?.toLongOrNull()?.let { onStart(it) }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                null
            }
            if (c != null) {
                controller = c
                c.addListener(listener)
                sync(c)
                val todo = pending.toList()
                pending.clear()
                todo.forEach { it(c) }
                startTicker()
            } else {
                future = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending.add(block)
            connect()
        }
    }

    private fun sync(p: Player) {
        val item = p.currentMediaItem
        val md = item?.mediaMetadata
        val ids = ArrayList<Long>(p.mediaItemCount)
        for (i in 0 until p.mediaItemCount) ids.add(p.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L)
        val dur = p.duration
        state.update { old ->
            old.copy(
                currentId = item?.mediaId?.toLongOrNull(),
                title = md?.title?.toString() ?: "",
                artist = md?.artist?.toString() ?: "",
                album = md?.albumTitle?.toString() ?: "",
                isPlaying = p.isPlaying,
                durationMs = if (dur == C.TIME_UNSET) 0L else dur,
                shuffle = p.shuffleModeEnabled,
                repeat = p.repeatMode,
                queueIds = ids,
                index = p.currentMediaItemIndex,
                speed = p.playbackParameters.speed,
            )
        }
    }

    private fun startTicker() {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            while (true) {
                // Poll often while music plays (smooth progress bar), rarely while paused (saves battery).
                delay(if (state.value.isPlaying) 250 else 1000)
                val c = controller ?: continue
                val pos = c.currentPosition
                position.value = pos
                val s = state.value
                val id = s.currentId
                if (c.isPlaying && id != null && countedFor != id) {
                    val threshold = if (s.durationMs > 0) minOf(30_000L, s.durationMs / 2) else 30_000L
                    if (pos >= threshold) {
                        countedFor = id
                        onCounted(id)
                    }
                }
            }
        }
    }

    private fun toItem(r: ResolvedSong): MediaItem =
        MediaItem.Builder()
            .setMediaId(r.id.toString())
            .setUri(r.song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(r.title)
                    .setArtist(r.artistLabel)
                    .setAlbumTitle(r.song.album)
                    .setArtworkUri(ArtProvider.uriFor(app, r.id))
                    .build()
            )
            .build()

    /** Plays the songs in exactly this order, starting at [startIndex]. */
    fun playQueue(songs: List<ResolvedSong>, startIndex: Int) {
        if (songs.isEmpty()) return
        val items = songs.map { toItem(it) }
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: ResolvedSong) {
        val item = toItem(song)
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(item)
                c.prepare()
                c.play()
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, item)
            }
        }
    }

    fun addToQueue(song: ResolvedSong) {
        val item = toItem(song)
        withController { c ->
            val wasEmpty = c.mediaItemCount == 0
            c.addMediaItem(item)
            if (wasEmpty) {
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController {
        it.seekToDefaultPosition(index)
        it.play()
    }

    fun moveQueueItem(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }
    fun removeQueueItem(index: Int) = withController { it.removeMediaItem(index) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /** Pauses after [minutes]. The last few seconds fade the volume out, then the volume is restored. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        controller?.volume = 1f
        if (minutes <= 0) {
            state.update { it.copy(sleepEndsAt = 0L) }
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        state.update { it.copy(sleepEndsAt = end) }
        sleepJob = scope.launch {
            try {
                val fadeMs = 8_000L
                delay((minutes * 60_000L - fadeMs).coerceAtLeast(0L))
                val steps = 16
                for (i in 1..steps) {
                    controller?.volume = 1f - i / steps.toFloat()
                    delay(fadeMs / steps)
                }
                controller?.pause()
                state.update { it.copy(sleepEndsAt = 0L) }
            } finally {
                // Also runs when the timer is cancelled in the middle of a fade.
                controller?.volume = 1f
            }
        }
    }
}

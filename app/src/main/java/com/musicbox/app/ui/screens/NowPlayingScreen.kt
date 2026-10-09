@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.musicbox.app.player.ArtCache
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.components.DragDropState
import com.musicbox.app.ui.components.DragHandle
import com.musicbox.app.ui.components.SongArt
import com.musicbox.app.ui.components.bounceClick
import com.musicbox.app.ui.components.fmtDuration
import com.musicbox.app.ui.components.rememberSpinAngle
import com.musicbox.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------- mini player

@Composable
fun MiniPlayer(vm: MainViewModel) {
    val ps by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    // The cover turns like a record while music plays and stops when it is paused.
    val spin = rememberSpinAngle(ps.isPlaying)
    val rawProgress = if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(rawProgress, tween(260, easing = LinearEasing), label = "miniProgress")

    AnimatedVisibility(
        visible = ps.currentId != null && !vm.nowPlayingOpen,
        enter = expandVertically(tween(300)) + fadeIn(tween(240)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp)).background(Brand.Card).clickable { vm.nowPlayingOpen = true },
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SongArt(ps.currentId, Modifier.size(44.dp).graphicsLayer { rotationZ = spin.value }, corner = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ps.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ps.artist, color = Brand.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.player.previous() }) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White) }
                IconButton(onClick = { vm.player.togglePlay() }) {
                    Crossfade(targetState = ps.isPlaying, animationSpec = tween(160), label = "miniPlayPause") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White) }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Brand.Dim.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Brand.logo))
            }
        }
    }
}

// ---------------------------------------------------------------------------- now playing

private enum class Panel { ART, LYRICS }

@Composable
fun NowPlayingScreen(vm: MainViewModel) {
    val ps by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    val index by vm.repo.index.collectAsState()
    val ctx = LocalContext.current
    val id = ps.currentId
    val song = if (id != null) index.byId[id] else null

    var panel by remember { mutableStateOf(Panel.ART) }
    var showQueue by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    val bg by produceState<Bitmap?>(initialValue = null, id) {
        value = if (id == null) null else withContext(Dispatchers.IO) { ArtCache.bitmap(ctx, id, true) }
    }
    val tintTarget = remember(bg) { bg?.let { Color(ArtCache.averageColor(it)) } ?: Brand.Blue }
    // The glow behind the player slowly changes colour when the song changes.
    val tint by animateColorAsState(tintTarget, tween(700), label = "tint")
    // Playing: the cover is full size. Paused: it shrinks a little, like a record that stopped.
    val artScale by animateFloatAsState(
        targetValue = if (ps.isPlaying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 180f),
        label = "artScale",
    )

    Box(
        Modifier.fillMaxSize().background(Brand.Bg)
            // This screen covers the others, so it must also catch taps; otherwise they reach the list underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Crossfade(targetState = bg, animationSpec = tween(600), label = "nowBackground") { b ->
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(36.dp).graphicsLayer { alpha = 0.55f },
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), Brand.Bg.copy(alpha = 0.92f), Brand.Bg))
            )
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.nowPlayingOpen = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Text("Now Playing", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White) }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Speed: " + speedLabel(ps.speed)) },
                            leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                            // Stays open so you can tap again to step through the speeds.
                            onClick = { vm.player.setSpeed(nextSpeed(ps.speed)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep timer") },
                            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                            onClick = {
                                moreOpen = false
                                vm.dialog = Dlg.Sleep
                            },
                        )
                        if (song != null) {
                            DropdownMenuItem(
                                text = { Text("Edit info / move to artist") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    moreOpen = false
                                    vm.dialog = Dlg.EditSong(song.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add to folder") },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    moreOpen = false
                                    vm.dialog = Dlg.AddToFolder(listOf(song.id))
                                },
                            )
                        }
                    }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(panel) {
                    // Swipe the cover left or right to change song.
                    if (panel == Panel.ART) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -140f) vm.player.next() else if (total > 140f) vm.player.previous()
                            },
                        ) { change, amount ->
                            change.consume()
                            total += amount
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (panel == Panel.ART) {
                    SongArt(
                        id,
                        Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                            shadowElevation = 24f
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                        big = true,
                        corner = 28.dp,
                    )
                } else if (id != null) {
                    LyricsPanel(vm, id, pos)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ps.title.ifBlank { "Nothing playing" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(ps.artist, color = Brand.Cyan, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ps.album.isNotBlank()) Text(ps.album, color = Brand.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val fav = song?.state?.favorite == true
                IconButton(onClick = { if (id != null) vm.repo.toggleFavorite(id) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (fav) Brand.Pink else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            val progress = if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
            WaveSeek(
                seed = id ?: 0L,
                progress = progress,
                onSeek = { f -> if (ps.durationMs > 0) vm.player.seekTo((f * ps.durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                playing = ps.isPlaying,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtDuration(pos), color = Brand.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(fmtDuration(ps.durationMs), color = Brand.Dim, fontSize = 12.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.player.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (ps.shuffle) Brand.Blue else Brand.Dim, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { vm.player.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(76.dp).bounceClick { vm.player.togglePlay() }.clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Brand.Blue, Brand.Violet))),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = ps.isPlaying, animationSpec = tween(180), label = "playPause") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (ps.repeat == Player.REPEAT_MODE_OFF) Brand.Dim else Brand.Blue,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Lyrics", Icons.Rounded.Subject, panel == Panel.LYRICS, Modifier.weight(1f)) {
                    panel = if (panel == Panel.LYRICS) Panel.ART else Panel.LYRICS
                }
                Pill("Queue", Icons.Rounded.QueueMusic, false, Modifier.weight(1f)) { showQueue = true }
                Pill("Audio", Icons.Rounded.Equalizer, false, Modifier.weight(1f)) { showEq = true }
                val minutesLeft = ((ps.sleepEndsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
                Pill(
                    if (ps.sleepEndsAt > 0) "$minutesLeft min left" else "Sleep",
                    Icons.Rounded.Timer,
                    ps.sleepEndsAt > 0,
                    Modifier.weight(1f),
                ) { vm.dialog = Dlg.Sleep }
            }

            val nextId = ps.queueIds.getOrNull(ps.index + 1)
            val next = if (nextId != null) index.byId[nextId] else null
            if (next != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Card.copy(alpha = 0.7f))
                        .clickable { vm.player.next() }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArt(next.id, Modifier.size(40.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Up next", color = Brand.Dim, fontSize = 11.sp)
                        Text(next.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmtDuration(next.song.durationMs), color = Brand.Dim, fontSize = 12.sp)
                }
            }
        }
    }

    if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
    if (showEq) EqualizerSheet(vm, onDismiss = { showEq = false })
}

private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun nextSpeed(current: Float): Float {
    var best = 0
    for (i in speeds.indices) {
        if (kotlin.math.abs(speeds[i] - current) < kotlin.math.abs(speeds[best] - current)) best = i
    }
    return speeds[(best + 1) % speeds.size]
}

private fun speedLabel(v: Float): String = if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).border(1.dp, if (active) Brand.Blue else Brand.Dim.copy(alpha = 0.4f), RoundedCornerShape(50))
            .background(if (active) Brand.Blue.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Brand.Blue else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (active) Brand.Blue else Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------- wave seek bar

/**
 * A bar-style progress control. The bars are decorative (seeded by the song) and you can tap or drag to seek.
 * While music plays the bars around the playhead gently bounce.
 */
@Composable
fun WaveSeek(
    seed: Long,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val bars = remember(seed) {
        val r = java.util.Random(seed)
        List(60) { 0.25f + r.nextFloat() * 0.75f }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragP by remember { mutableFloatStateOf(0f) }
    val onSeekNow by rememberUpdatedState(onSeek)
    val smooth by animateFloatAsState(progress, tween(260, easing = LinearEasing), label = "seek")
    val shown = if (dragging) dragP else smooth
    val pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase")

    Canvas(
        modifier.height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> onSeekNow((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragP = (o.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeekNow(dragP)
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    dragP = (change.position.x / size.width).coerceIn(0f, 1f)
                    change.consume()
                }
            },
    ) {
        val n = bars.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val head = shown * n
        val phase = if (playing) pulse.value else 0f
        for (i in 0 until n) {
            var h = size.height * bars[i]
            val distance = kotlin.math.abs(i + 0.5f - head)
            if (playing && distance < 4f) {
                val wobble = kotlin.math.sin(phase * 2f * Math.PI.toFloat() + i * 0.8f)
                h *= 1f + 0.18f * (1f - distance / 4f) * wobble
            }
            h = h.coerceIn(bw, size.height)
            val played = (i + 0.5f) / n <= shown
            drawRoundRect(
                color = when {
                    distance < 1f && played -> Brand.Cyan
                    played -> Brand.Blue
                    else -> Color.White.copy(alpha = 0.25f)
                },
                topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------- queue

private data class QueueEntry(val uid: Long, val songId: Long)

@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val ps by vm.player.state.collectAsState()
    val index by vm.repo.index.collectAsState()
    val scope = rememberCoroutineScope()
    val entries = remember {
        val l = mutableStateListOf<QueueEntry>()
        vm.player.state.value.queueIds.forEachIndexed { i, id -> l.add(QueueEntry(i.toLong(), id)) }
        l
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (ps.index - 2).coerceAtLeast(0))
    val dd = remember(listState) {
        DragDropState(
            listState,
            scope,
            onMove = { from, to ->
                entries.add(to, entries.removeAt(from))
                vm.player.moveQueueItem(from, to)
            },
            onEnd = {},
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Surface,
    ) {
        Text(
            "Queue (${entries.size})",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = listState) {
            itemsIndexed(entries, key = { _, e -> e.uid }) { i, e ->
                val s = index.byId[e.songId]
                val current = i == ps.index
                val dragging = dd.dragging == i
                val rowModifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dd.offset }.background(Brand.Card)
                } else {
                    Modifier
                }
                Row(
                    rowModifier.fillMaxWidth().clickable { vm.player.skipTo(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArt(e.songId, Modifier.size(42.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s?.title ?: "Unknown song",
                            color = if (current) Brand.Blue else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(s?.artistLabel ?: "", color = Brand.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DragHandle(dd, i)
                    IconButton(onClick = {
                        entries.removeAt(i)
                        vm.player.removeQueueItem(i)
                    }) { Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Brand.Dim) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------- lyrics

private data class LyricLine(val timeMs: Long, val text: String)

private val lrcStamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")

private fun parseLrc(text: String): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    for (line in text.lines()) {
        val stamps = lrcStamp.findAll(line).toList()
        if (stamps.isEmpty()) continue
        val body = lrcStamp.replace(line, "").trim()
        for (m in stamps) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
            out.add(LyricLine(minutes * 60_000L + seconds * 1000L + ms, body))
        }
    }
    return out.sortedBy { it.timeMs }
}

@Composable
private fun LyricsPanel(vm: MainViewModel, songId: Long, positionMs: Long) {
    var text by remember(songId) { mutableStateOf(vm.repo.getLyrics(songId)) }
    var editing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = vm.repo.readText(uri)
            if (t != null) {
                text = t
                vm.repo.saveLyrics(songId, t)
            }
        }
    }
    val lines = remember(text) { parseLrc(text) }
    val synced = lines.size >= 2

    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .lrc / .txt") }
            OutlinedButton(onClick = { editing = true }) { Text(if (text.isBlank()) "Paste" else "Edit") }
        }
        Spacer(Modifier.height(8.dp))
        if (text.isBlank()) {
            Text(
                "No lyrics saved for this song yet. Import an .lrc file (timed lyrics) or a .txt file, or paste the words.",
                color = Brand.Dim,
                fontSize = 14.sp,
            )
        } else if (synced) {
            val active = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
            val state = rememberLazyListState()
            LaunchedEffect(active) { state.animateScrollToItem((active - 2).coerceAtLeast(0)) }
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                itemsIndexed(lines) { i, l ->
                    Text(
                        l.text.ifBlank { "\u266A" },
                        color = if (i == active) Color.White else Brand.Dim,
                        fontSize = if (i == active) 22.sp else 18.sp,
                        fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(text, color = Color.White, fontSize = 17.sp, lineHeight = 26.sp)
            }
        }
    }

    if (editing) {
        var draft by remember { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Brand.Card,
            title = { Text("Lyrics") },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    text = draft
                    vm.repo.saveLyrics(songId, draft)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

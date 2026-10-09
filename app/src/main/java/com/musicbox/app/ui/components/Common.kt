@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.organize.Place
import com.musicbox.app.player.ArtCache
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.theme.Brand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun fmtDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** Cover art of one song, or a coloured placeholder. */
@Composable
fun SongArt(
    songId: Long?,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    corner: Dp = 12.dp,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(
        initialValue = if (songId == null) null else ArtCache.cached(songId, big),
        songId,
        big,
    ) {
        value = if (songId == null) {
            null
        } else {
            ArtCache.cached(songId, big) ?: withContext(Dispatchers.IO) { ArtCache.bitmap(ctx, songId, big) }
        }
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brand.gradientFor(songId ?: 0L))) {
        Crossfade(targetState = bmp, animationSpec = tween(250), label = "art") { b ->
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxSize(0.45f),
                    )
                }
            }
        }
    }
}

/** A click that squeezes the item a little while the finger is down, then springs back. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * Fades and slides its content in once. With [animate] false the content simply appears,
 * which is what we want when a list row scrolls back into view.
 */
@Composable
fun FadeSlideIn(delayMs: Int = 0, animate: Boolean = true, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(delayMs.toLong())
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 10 },
    ) {
        // AnimatedVisibility stacks several children on top of each other, so keep them in a column.
        Column { content() }
    }
}

/**
 * A rotation angle (degrees) that keeps turning while [spinning] is true and holds still otherwise.
 * Read `.value` inside graphicsLayer so the screen is not recomposed on every frame.
 */
@Composable
fun rememberSpinAngle(spinning: Boolean, periodMs: Int = 14_000): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                val start = angle.value % 360f
                angle.snapTo(start)
                angle.animateTo(start + 360f, tween(periodMs, easing = LinearEasing))
            }
        }
    }
    return angle
}

/** Artist picture: your own photo, else cover art of one of their songs, else initials. */
@Composable
fun ArtistArt(
    name: String,
    photo: String?,
    coverIds: List<Long>,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    shape: Shape = CircleShape,
    initialsSize: Int = 22,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, photo, coverIds.firstOrNull(), big) {
        value = withContext(Dispatchers.IO) {
            val own = if (photo != null) BitmapFactory.decodeFile(photo) else null
            own ?: ArtCache.firstBitmap(ctx, coverIds.take(4), big)?.second
        }
    }
    Box(modifier.clip(shape).background(Brand.gradientFor(name)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val letters = name.split(' ', '-', '.').filter { it.isNotEmpty() }.take(2)
                .joinToString("") { it.first().uppercase() }
            Text(letters, color = Color.White, fontSize = initialsSize.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                "See all",
                color = Brand.Blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(10.dp),
            )
        }
    }
}

@Composable
fun SongRow(
    song: ResolvedSong,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showHint: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    note: String? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) Brand.Card else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        SongArt(song.id, Modifier.size(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.title,
                    color = if (playing) Brand.Blue else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.dupCount > 1) {
                    Spacer(Modifier.width(6.dp))
                    DupBadge(song.dupCount)
                }
            }
            val who = if (showHint && song.hint != null) "Maybe: ${song.hint}" else song.artistLabel
            Text(
                who + " \u00B7 " + fmtDuration(song.song.durationMs),
                color = Brand.Dim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note != null) {
                Text(note, color = Brand.Dim.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Small pink tag shown next to a song that exists more than once in the library. */
@Composable
fun DupBadge(count: Int) {
    Text(
        "DUP \u00D7$count",
        color = Brand.Pink,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brand.Pink.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun SongMenu(vm: MainViewModel, song: ResolvedSong, removeFromFolder: String? = null, source: Place? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Brand.Dim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuEntry("Play next", Icons.Rounded.SkipNext) {
                open = false
                vm.player.playNext(song)
            }
            MenuEntry("Add to queue", Icons.Rounded.QueueMusic) {
                open = false
                vm.player.addToQueue(song)
            }
            MenuEntry(
                if (song.state.favorite) "Remove from favorites" else "Add to favorites",
                if (song.state.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            ) {
                open = false
                vm.repo.toggleFavorite(song.id)
            }
            MenuEntry("Edit info / move to artist", Icons.Rounded.Edit) {
                open = false
                vm.dialog = Dlg.EditSong(song.id)
            }
            MenuEntry("Add to folder", Icons.Rounded.CreateNewFolder) {
                open = false
                vm.dialog = Dlg.AddToFolder(listOf(song.id))
            }
            MenuEntry("Copy to...", Icons.Rounded.ContentCopy) {
                open = false
                vm.dialog = Dlg.Transfer(listOf(song.id), false, source)
            }
            if (source != null && source.canLeave) {
                MenuEntry("Move to...", Icons.Rounded.DriveFileMove) {
                    open = false
                    vm.dialog = Dlg.Transfer(listOf(song.id), true, source)
                }
            }
            if (removeFromFolder != null) {
                MenuEntry("Remove from this folder", Icons.Rounded.Delete) {
                    open = false
                    vm.repo.removeFromFolder(removeFromFolder, song.id)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Square song card used in the horizontal rows on the Home screen. */
@Composable
fun SongCard(song: ResolvedSong, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).bounceClick(onClick)) {
        Box {
            SongArt(song.id, Modifier.size(132.dp), corner = 14.dp)
            Box(
                Modifier.align(Alignment.BottomStart).padding(8.dp).size(30.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            color = if (playing) Brand.Blue else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(song.artistLabel, color = Brand.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Drag-and-drop re-ordering for a LazyColumn. Rows call start/drag/end from their drag handle. */
class DragDropState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onEnd: () -> Unit,
) {
    var dragging by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(index: Int) {
        dragging = index
        offset = 0f
    }

    fun drag(delta: Float) {
        val from = dragging ?: return
        offset += delta
        val info = list.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        val center = me.offset + me.size / 2f + offset
        val target = info.visibleItemsInfo.firstOrNull {
            it.index != from && center >= it.offset && center <= it.offset + it.size
        }
        if (target != null) {
            onMove(from, target.index)
            offset += (me.offset - target.offset).toFloat()
            dragging = target.index
        }
        if (center < info.viewportStartOffset + 120) {
            scope.launch { list.scrollBy(-24f) }
        } else if (center > info.viewportEndOffset - 120) {
            scope.launch { list.scrollBy(24f) }
        }
    }

    fun end() {
        dragging = null
        offset = 0f
        onEnd()
    }
}

@Composable
fun DragHandle(dd: DragDropState, index: Int) {
    val current by rememberUpdatedState(index)
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Drag to re-order",
        tint = Brand.Dim,
        modifier = Modifier.size(44.dp).padding(10.dp).pointerInput(dd) {
            detectDragGestures(
                onDragStart = { dd.start(current) },
                onDragEnd = { dd.end() },
                onDragCancel = { dd.end() },
            ) { change, amount ->
                change.consume()
                dd.drag(amount.y)
            }
        },
    )
}

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Brand.Dim, fontSize = 14.sp)
    }
}

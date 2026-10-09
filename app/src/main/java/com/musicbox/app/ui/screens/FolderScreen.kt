@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Folders
import com.musicbox.app.organize.Place
import com.musicbox.app.organize.SortMode
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.Route
import com.musicbox.app.ui.components.ArtistArt
import com.musicbox.app.ui.components.DragDropState
import com.musicbox.app.ui.components.DragHandle
import com.musicbox.app.ui.components.EmptyNote
import com.musicbox.app.ui.components.ScreenTitle
import com.musicbox.app.ui.components.SongMenu
import com.musicbox.app.ui.components.SongRow
import com.musicbox.app.ui.components.TextPromptDialog
import com.musicbox.app.ui.components.fmtDuration
import com.musicbox.app.ui.theme.Brand

/** Shows the songs of one folder: an artist, a mood, favourites, a folder you made, ... */
@Composable
fun FolderScreen(vm: MainViewModel, kind: FolderKind, key: String, title: String, embedded: Boolean = false) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val ps by vm.player.state.collectAsState()
    val scope = rememberCoroutineScope()

    val base = remember(index, data, kind, key) { Folders.songs(index, data, kind, key) }
    val manualKey = Folders.manualKey(kind, key)
    var sortName by rememberSaveable(kind, key) { mutableStateOf(SortMode.DEFAULT.name) }
    val mode = SortMode.valueOf(sortName)
    val shown = remember(base, mode) { Folders.sort(base, mode) }
    val canDrag = manualKey != null && mode == SortMode.DEFAULT
    val items = remember(shown) { mutableStateListOf<ResolvedSong>().also { it.addAll(shown) } }
    val listState = rememberLazyListState()
    val dd = remember(listState, items, manualKey) {
        DragDropState(
            listState,
            scope,
            onMove = { from, to -> items.add(to, items.removeAt(from)) },
            onEnd = { if (manualKey != null) vm.repo.setManualOrder(manualKey, items.map { it.id }) },
        )
    }

    var showShuffle by remember { mutableStateOf(false) }
    var seqMode by remember { mutableStateOf(false) }
    val seq = remember { mutableStateListOf<Long>() }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    val artist = if (kind == FolderKind.ARTIST) data.artists.firstOrNull { it.id == key } else null
    val folder = if (kind == FolderKind.CUSTOM) data.folders.firstOrNull { it.id == key } else null
    val heading = when {
        artist != null -> artist.name
        folder != null -> folder.name
        title.isNotBlank() -> title
        kind == FolderKind.FAVORITES -> "Favorites"
        else -> "Songs"
    }

    val playRandom = {
        if (items.isNotEmpty()) vm.player.playQueue(items.toList().shuffled(), 0)
    }
    val playMost = {
        if (items.isNotEmpty()) vm.player.playQueue(items.sortedByDescending { it.state.plays }, 0)
    }
    val playSequence = {
        val byId = items.associateBy { it.id }
        val picked = seq.mapNotNull { byId[it] }
        val rest = items.filter { it.id !in seq }.shuffled()
        vm.player.playQueue(picked + rest, 0)
        seqMode = false
        seq.clear()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            title = heading,
            onBack = if (embedded) null else ({ vm.pop() }),
            actions = {
                if (kind == FolderKind.UNIDENTIFIED) {
                    IconButton(onClick = { vm.open(Route.AiOrganizer) }) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI Organize", tint = Brand.Cyan)
                    }
                }
                if (folder != null) {
                    IconButton(onClick = { renameOpen = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = Color.White) }
                    IconButton(onClick = { deleteOpen = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White) }
                }
            },
        )

        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtistArt(
                heading,
                artist?.photo,
                shown.take(4).map { it.id },
                Modifier.size(96.dp),
                big = false,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                val total = items.sumOf { it.song.durationMs }
                Text("${items.size} songs \u00B7 ${fmtDuration(total)}", color = Brand.Dim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (items.isNotEmpty()) vm.player.playQueue(items.toList(), 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = { showShuffle = true }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (m in SortMode.values()) {
                val label = when (m) {
                    SortMode.DEFAULT -> if (manualKey != null) "My order" else "Default"
                    SortMode.AZ -> "A-Z"
                    SortMode.NEWEST -> "Newest"
                    SortMode.MOST -> "Most played"
                }
                FilterChip(selected = m == mode, onClick = { sortName = m.name }, label = { Text(label) })
            }
        }
        if (canDrag && items.size > 1) {
            Text(
                "Hold the \u2261 handle and drag to put songs in your own order.",
                color = Brand.Dim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (items.isEmpty()) {
            EmptyNote(
                when (kind) {
                    FolderKind.FAVORITES -> "Tap the heart on a song to add it here."
                    FolderKind.CUSTOM -> "Empty folder. Use a song's menu > Add to folder."
                    else -> "No songs here yet."
                }
            )
        }

        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(items, key = { _, s -> s.id }) { i, s ->
                val dragging = dd.dragging == i
                val rowModifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dd.offset }.background(Brand.Card)
                } else {
                    Modifier
                }
                val lead: (@Composable () -> Unit)? = if (seqMode) {
                    { SeqBadge(seq.indexOf(s.id) + 1) }
                } else {
                    null
                }
                SongRow(
                    song = s,
                    playing = s.id == ps.currentId,
                    showHint = kind == FolderKind.UNIDENTIFIED,
                    modifier = rowModifier,
                    leading = lead,
                    onClick = {
                        if (seqMode) {
                            if (seq.contains(s.id)) seq.remove(s.id) else seq.add(s.id)
                        } else {
                            vm.player.playQueue(items.toList(), i)
                        }
                    },
                    trailing = {
                        if (!seqMode) {
                            if (canDrag) DragHandle(dd, i)
                            SongMenu(vm, s, removeFromFolder = if (kind == FolderKind.CUSTOM) key else null, source = Place.of(kind, key))
                        }
                    },
                )
            }
        }

        if (seqMode) {
            Row(
                Modifier.fillMaxWidth().background(Brand.Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tap songs in the order you want (${seq.size} picked). The rest plays in random order.",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    seqMode = false
                    seq.clear()
                }) { Text("Cancel") }
                Button(onClick = playSequence, enabled = seq.isNotEmpty()) { Text("Play") }
            }
        }
    }

    if (showShuffle) {
        ModalBottomSheet(
            onDismissRequest = { showShuffle = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Brand.Surface,
        ) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text("Shuffle", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                ShuffleOption(Icons.Rounded.Shuffle, "Random", "Every song here in a random order") {
                    showShuffle = false
                    playRandom()
                }
                ShuffleOption(Icons.Rounded.Whatshot, "Most played", "Start with the songs you play most in this folder") {
                    showShuffle = false
                    playMost()
                }
                ShuffleOption(Icons.Rounded.QueueMusic, "My sequence", "Pick songs 1, 2, 3... then random for the rest, no repeats") {
                    showShuffle = false
                    seq.clear()
                    seqMode = true
                }
            }
        }
    }

    if (renameOpen && folder != null) {
        TextPromptDialog("Rename folder", "Folder name", folder.name, "Save", { renameOpen = false }) { vm.repo.renameFolder(folder.id, it) }
    }
    if (deleteOpen && folder != null) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = Brand.Card,
            title = { Text("Delete folder?") },
            text = { Text("The folder \"${folder.name}\" is removed. Your song files are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    vm.repo.deleteFolder(folder.id)
                    vm.pop()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeqBadge(n: Int) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(if (n > 0) Brand.Blue else Color.Transparent)
            .border(2.dp, if (n > 0) Brand.Blue else Brand.Dim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (n > 0) Text(n.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShuffleOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brand.Card), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Brand.Cyan)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Brand.Dim, fontSize = 12.sp)
        }
    }
}

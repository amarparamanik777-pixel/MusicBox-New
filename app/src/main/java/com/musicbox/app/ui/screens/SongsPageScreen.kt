@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Folders
import com.musicbox.app.organize.Organizer
import com.musicbox.app.organize.Place
import com.musicbox.app.organize.PlaceKind
import com.musicbox.app.organize.Text as TextUtil
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.components.DupBadge
import com.musicbox.app.ui.components.EmptyNote
import com.musicbox.app.ui.components.ScreenTitle
import com.musicbox.app.ui.components.SongMenu
import com.musicbox.app.ui.components.SongRow
import com.musicbox.app.ui.theme.Brand

enum class SongsPage { ALL, NEW }

/**
 * Two pages share this screen:
 *  - ALL: every song on the phone, with search, duplicate marking and multi-select.
 *  - NEW: songs added in the last two weeks that you have not sorted yet.
 * Long-press a song to start selecting, then copy (or, on the New page, move) the selection into
 * folders, artists, moods or favourites.
 */
@Composable
fun SongsPageScreen(vm: MainViewModel, page: SongsPage) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val ps by vm.player.state.collectAsState()
    val isNew = page == SongsPage.NEW

    var q by rememberSaveable(page) { mutableStateOf("") }
    var onlyDup by rememberSaveable(page) { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()

    val base = remember(index, data, page) {
        if (isNew) Folders.songs(index, data, FolderKind.NEW_SONGS, "") else index.all
    }
    val dupSongs = remember(base) { base.count { it.dupCount > 1 } }
    val words = remember(q) { TextUtil.spaced(q).split(' ').filter { it.isNotEmpty() } }
    val shown = remember(base, words, onlyDup) {
        val filtered = base.filter { s -> (!onlyDup || s.dupCount > 1) && words.all { s.searchKey.contains(it) } }
        // Copies of the same song sit next to each other so they are easy to compare.
        if (onlyDup) filtered.sortedBy { Organizer.dupKey(it) ?: "" } else filtered
    }
    val source = if (isNew) Place(PlaceKind.NEW) else null

    fun toggle(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color.White) }
                Text("${selected.size} selected", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    selected.clear()
                    selected.addAll(shown.map { it.id })
                }) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all", tint = Color.White) }
                IconButton(onClick = {
                    vm.dialog = Dlg.Transfer(selected.toList(), false, source) { selected.clear() }
                }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy to", tint = Brand.Cyan) }
                if (isNew) {
                    IconButton(onClick = {
                        vm.dialog = Dlg.Transfer(selected.toList(), true, source) { selected.clear() }
                    }) { Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move to", tint = Brand.Cyan) }
                }
            }
        } else {
            ScreenTitle(
                title = if (isNew) "New Songs" else "All Songs",
                onBack = { vm.pop() },
                actions = {
                    if (isNew && base.isNotEmpty()) {
                        IconButton(onClick = { vm.repo.markAllNewSeen() }) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "Mark all as seen", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { vm.dialog = Dlg.NewFolder() }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder", tint = Color.White)
                    }
                },
            )
        }

        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text(if (isNew) "Search new songs..." else "Search all songs...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = !onlyDup, onClick = { onlyDup = false }, label = { Text("All (${base.size})") })
            FilterChip(
                selected = onlyDup,
                onClick = { onlyDup = true },
                label = { Text("Duplicates ($dupSongs)") },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }

        if (isNew) {
            Text(
                "Songs added in the last ${Folders.NEW_DAYS} days. Move a song to a folder, or tap \u2713 at the top, and it leaves this list. Long-press to select several.",
                color = Brand.Dim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        } else if (dupSongs > 0 && !onlyDup) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brand.Pink.copy(alpha = 0.12f)).clickable { onlyDup = true }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Brand.Pink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "$dupSongs songs appear more than once. Tap to review them.",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                DupBadge(2)
            }
        } else if (!selecting && shown.isNotEmpty()) {
            Text(
                "Long-press a song to select several, then copy them into folders.",
                color = Brand.Dim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (shown.isEmpty()) {
            EmptyNote(
                when {
                    onlyDup -> "No duplicates found. Every song is unique."
                    words.isNotEmpty() -> "Nothing found."
                    isNew -> "No new songs. Songs you add to your phone appear here."
                    else -> "No songs yet. Go to Home and tap rescan."
                }
            )
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                val lead: (@Composable () -> Unit)? = if (selecting) {
                    { SelectDot(s.id in selected) }
                } else {
                    null
                }
                SongRow(
                    song = s,
                    playing = s.id == ps.currentId,
                    selected = s.id in selected,
                    note = if (onlyDup) s.song.path else null,
                    leading = lead,
                    onClick = {
                        if (selecting) toggle(s.id) else vm.player.playQueue(shown, i)
                    },
                    onLongClick = { toggle(s.id) },
                    trailing = { if (!selecting) SongMenu(vm, s, source = source) },
                )
            }
        }
    }
}

@Composable
private fun SelectDot(on: Boolean) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (on) Brand.Blue else Color.Transparent)
            .border(2.dp, if (on) Brand.Blue else Brand.Dim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

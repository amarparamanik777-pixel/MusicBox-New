@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.musicbox.app.data.Bucket
import com.musicbox.app.organize.Place
import com.musicbox.app.organize.PlaceKind
import com.musicbox.app.organize.Text as TextUtil
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.theme.Brand

@Composable
fun GlobalDialogs(vm: MainViewModel) {
    when (val d = vm.dialog) {
        is Dlg.EditSong -> EditSongDialog(vm, d.id) { vm.dialog = null }
        is Dlg.AddToFolder -> AddToFolderDialog(vm, d.ids) { vm.dialog = null }
        is Dlg.NewFolder -> NewFolderDialog(vm, d.thenAdd) { vm.dialog = null }
        is Dlg.Transfer -> TransferDialog(vm, d) { vm.dialog = null }
        Dlg.Sleep -> SleepDialog(vm) { vm.dialog = null }
        null -> {}
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text)
                onDismiss()
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NewFolderDialog(vm: MainViewModel, thenAdd: List<Long>, onDismiss: () -> Unit) {
    TextPromptDialog("New folder", "Folder name", "", "Create", onDismiss) { name ->
        vm.repo.createFolder(name, thenAdd)
    }
}

@Composable
fun AddToFolderDialog(vm: MainViewModel, ids: List<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val data by vm.repo.userData.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text("Add to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (data.folders.isEmpty()) Text("You have no folders yet.", color = Brand.Dim)
                for (f in data.folders) {
                    TextButton(onClick = {
                        // Same result as before, plus a message if the song was already in the folder.
                        val r = vm.repo.transfer(ids, Place(PlaceKind.CUSTOM, f.id), null, false)
                        Toast.makeText(ctx, r.message(f.name), Toast.LENGTH_LONG).show()
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("${f.name}  (${f.songIds.size})") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                vm.dialog = Dlg.NewFolder(ids)
            }) { Text("New folder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SleepDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text("Sleep timer") },
        text = {
            Column {
                for (m in listOf(15, 30, 45, 60, 90)) {
                    TextButton(onClick = {
                        vm.player.setSleepTimer(m)
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("$m minutes") }
                }
                TextButton(onClick = {
                    vm.player.setSleepTimer(0)
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) { Text("Turn off") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun EditSongDialog(vm: MainViewModel, id: Long, onDismiss: () -> Unit) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val song = index.byId[id] ?: return
    val startArtist = if (song.bucket == Bucket.UNIDENTIFIED) (song.hint ?: "") else song.artistLabel
    var title by remember(id) { mutableStateOf(song.title) }
    var artist by remember(id) { mutableStateOf(startArtist) }
    var moods by remember(id) { mutableStateOf(song.moods.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text("Edit song") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(song.song.fileName, color = Brand.Dim, fontSize = 11.sp, maxLines = 2)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist (folder)") },
                    supportingText = { Text("Known artist = their folder. Unknown name = Others.") },
                    singleLine = true,
                )
                Text("Mood", color = Brand.Dim, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in data.moods) {
                        FilterChip(
                            selected = m.id in moods,
                            onClick = { moods = if (m.id in moods) moods - m.id else moods + m.id },
                            label = { Text(m.emoji + " " + m.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.repo.editSong(
                    id,
                    if (title.trim() == song.title) null else title,
                    if (artist.trim() == startArtist.trim()) null else artist,
                    if (moods == song.moods.toSet()) null else moods.toList(),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

// ---------------------------------------------------------------------------- copy / move

@Composable
fun TransferDialog(vm: MainViewModel, d: Dlg.Transfer, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val data by vm.repo.userData.collectAsState()
    val index by vm.repo.index.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val single = if (d.ids.size == 1) index.byId[d.ids[0]] else null
    val verb = if (d.move) "Move" else "Copy"

    fun finish(dest: Place, name: String) {
        val r = vm.repo.transfer(d.ids, dest, d.source, d.move)
        Toast.makeText(ctx, r.message(name), Toast.LENGTH_LONG).show()
        d.onDone?.invoke()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text("$verb to...") },
        text = {
            Column {
                Text(
                    single?.title ?: "${d.ids.size} songs",
                    color = Brand.Dim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Folders", "Artists", "Moods").forEachIndexed { i, name ->
                        FilterChip(selected = tab == i, onClick = { tab = i; query = "" }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> {
                            val fav = Place(PlaceKind.FAVORITES)
                            if (d.source != fav) {
                                DestRow("Favorites", null, single != null && vm.repo.isIn(single.id, fav), Icons.Rounded.Favorite) {
                                    finish(fav, "Favorites")
                                }
                            }
                            for (f in data.folders) {
                                val p = Place(PlaceKind.CUSTOM, f.id)
                                if (d.source == p) continue
                                DestRow(f.name, "${f.songIds.size} songs", single != null && vm.repo.isIn(single.id, p), Icons.Rounded.Folder) {
                                    finish(p, f.name)
                                }
                            }
                            if (data.folders.isEmpty()) Text("No folders yet. Tap New folder to make one.", color = Brand.Dim, fontSize = 13.sp)
                        }
                        1 -> {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text("Find an artist") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val words = TextUtil.spaced(query).split(' ').filter { it.isNotEmpty() }
                            val list = data.artists
                                .filter { a -> val n = TextUtil.spaced(a.name); words.all { n.contains(it) } }
                                .sortedBy { it.name.lowercase() }
                                .take(60)
                            for (a in list) {
                                val p = Place(PlaceKind.ARTIST, a.id)
                                if (d.source == p) continue
                                DestRow(a.name, "${index.artistSongs[a.id]?.size ?: 0} songs", single != null && vm.repo.isIn(single.id, p), Icons.Rounded.Person) {
                                    finish(p, a.name)
                                }
                            }
                        }
                        else -> {
                            for (m in data.moods) {
                                val p = Place(PlaceKind.MOOD, m.id)
                                if (d.source == p) continue
                                DestRow(m.emoji + " " + m.name, "${index.moodSongs[m.id]?.size ?: 0} songs", single != null && vm.repo.isIn(single.id, p), Icons.Rounded.Mood) {
                                    finish(p, m.name)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (creating) {
        TextPromptDialog("New folder", "Folder name", "", if (d.move) "Create & move" else "Create & copy", { creating = false }) { name ->
            val clean = name.trim().ifBlank { "New folder" }
            val id = vm.repo.createFolder(clean)
            finish(Place(PlaceKind.CUSTOM, id), clean)
        }
    }
}

@Composable
private fun DestRow(label: String, sub: String?, present: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Brand.Dim, fontSize = 11.sp)
        }
        if (present) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Already here", color = Brand.Cyan, fontSize = 11.sp)
        }
    }
}

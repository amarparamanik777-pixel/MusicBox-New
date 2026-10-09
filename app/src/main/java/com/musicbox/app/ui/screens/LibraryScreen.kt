@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Folders
import com.musicbox.app.organize.Text as TextUtil
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.Route
import com.musicbox.app.ui.components.ArtistArt
import com.musicbox.app.ui.components.EmptyNote
import com.musicbox.app.ui.components.ScreenTitle
import com.musicbox.app.ui.components.SongArt
import com.musicbox.app.ui.components.SongMenu
import com.musicbox.app.ui.components.SongRow
import com.musicbox.app.ui.theme.Brand

private val libraryTabs = listOf("Songs", "Artists", "Albums", "Folders", "Moods")

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val ps by vm.player.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        val newCount = remember(index, data) { Folders.songs(index, data, FolderKind.NEW_SONGS, "").size }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { vm.openFolder(FolderKind.ALL, "", "All Songs") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("All songs (${index.all.size})", maxLines = 1)
            }
            OutlinedButton(onClick = { vm.openFolder(FolderKind.NEW_SONGS, "", "New Songs") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New ($newCount)", maxLines = 1)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            libraryTabs.forEachIndexed { i, name ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> {
                if (index.all.isEmpty()) EmptyNote("No songs yet. Go to Home and tap rescan.")
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(index.all, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            s,
                            s.id == ps.currentId,
                            onClick = { vm.player.playQueue(index.all, i) },
                            trailing = { SongMenu(vm, s) },
                        )
                    }
                }
            }
            1 -> {
                val artists = data.artists.filter { (index.artistSongs[it.id]?.size ?: 0) > 0 }
                    .sortedBy { it.name.lowercase() }
                val otherCount = index.others.values.sumOf { it.songs.size }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(artists, key = { it.id }) { a ->
                        val songs = index.artistSongs[a.id].orEmpty()
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.openFolder(FolderKind.ARTIST, a.id, a.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtistArt(a.name, a.photo, songs.take(4).map { it.id }, Modifier.fillMaxWidth().aspectRatio(1f))
                            Spacer(Modifier.height(6.dp))
                            Text(a.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text("${songs.size} songs", color = Brand.Dim, fontSize = 11.sp)
                        }
                    }
                    if (otherCount > 0) {
                        item {
                            SpecialTile("Others", "$otherCount songs", Icons.Rounded.Person) { vm.open(Route.Others) }
                        }
                    }
                    if (index.unidentified.isNotEmpty()) {
                        item {
                            SpecialTile("Unidentified", "${index.unidentified.size} songs", Icons.Rounded.AutoAwesome) {
                                vm.openFolder(FolderKind.UNIDENTIFIED, "", "Unidentified")
                            }
                        }
                    }
                }
            }
            2 -> {
                val albums = index.albums.entries.sortedBy { it.key.lowercase() }
                if (albums.isEmpty()) EmptyNote("No album names found in your song tags.")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(albums, key = { it.key }) { e ->
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { vm.openFolder(FolderKind.ALBUM, e.key, e.key) }) {
                            SongArt(e.value.first().id, Modifier.fillMaxWidth().aspectRatio(1f), big = false, corner = 14.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(e.key, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.value.size} songs", color = Brand.Dim, fontSize = 11.sp)
                        }
                    }
                }
            }
            3 -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    TextButton(onClick = { vm.dialog = Dlg.NewFolder() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New folder")
                    }
                    if (data.folders.isEmpty()) EmptyNote("Make your own folders and put any songs in them.")
                    for (f in data.folders) {
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openFolder(FolderKind.CUSTOM, f.id, f.name) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("${f.songIds.size} songs", color = Brand.Dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.moods, key = { it.id }) { m ->
                        MoodCard(m, index.moodSongs[m.id]?.size ?: 0, Modifier.fillMaxWidth()) {
                            vm.openFolder(FolderKind.MOOD, m.id, m.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(50)).background(Brand.logo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Brand.Dim, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------- Others

@Composable
fun OthersScreen(vm: MainViewModel) {
    val index by vm.repo.index.collectAsState()
    val groups = index.others.entries.toList()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Others", onBack = { vm.pop() })
        Text(
            "Songs by artists that are not in your artist list. Tap + to give an artist their own folder on the Home screen.",
            color = Brand.Dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (groups.isEmpty()) EmptyNote("Nothing here. Every song has a known artist.")
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(groups, key = { _, e -> e.key }) { _, e ->
                val g = e.value
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openFolder(FolderKind.OTHERS_GROUP, e.key, g.name) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtistArt(g.name, null, g.songs.take(4).map { it.id }, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${g.songs.size} songs", color = Brand.Dim, fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.repo.addArtist(g.name, emptyList()) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add as artist", tint = Brand.Blue)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Search

@Composable
fun SearchScreen(vm: MainViewModel) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val ps by vm.player.state.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val words = remember(q) { TextUtil.spaced(q).split(' ').filter { it.isNotEmpty() } }

    val results = remember(index, words) {
        if (words.isEmpty()) {
            emptyList()
        } else {
            // searchKey is prepared once when the library is organised, so typing stays smooth.
            index.all.filter { s -> words.all { s.searchKey.contains(it) } }.take(200)
        }
    }
    val artistHits = remember(index, data, words) {
        if (words.isEmpty()) emptyList() else data.artists.filter { a ->
            (index.artistSongs[a.id]?.size ?: 0) > 0 && TextUtil.spaced(a.name).let { n -> words.all { n.contains(it) } }
        }.take(12)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search")
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text("Songs, artists, albums...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (artistHits.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(artistHits, key = { _, a -> a.id }) { _, a ->
                    Column(
                        Modifier.width(76.dp).clickable { vm.openFolder(FolderKind.ARTIST, a.id, a.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtistArt(a.name, a.photo, index.artistSongs[a.id].orEmpty().take(4).map { it.id }, Modifier.size(64.dp))
                        Text(a.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (words.isNotEmpty() && results.isEmpty() && artistHits.isEmpty()) EmptyNote("Nothing found.")
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s,
                    s.id == ps.currentId,
                    onClick = { vm.player.playQueue(results, i) },
                    trailing = { SongMenu(vm, s) },
                )
            }
        }
    }
}

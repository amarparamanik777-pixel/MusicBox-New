@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.musicbox.app.R
import com.musicbox.app.data.HomeSection
import com.musicbox.app.data.LibraryIndex
import com.musicbox.app.data.MoodDef
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.data.SectionType
import com.musicbox.app.data.UserData
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Folders
import com.musicbox.app.ui.AppTab
import com.musicbox.app.ui.Dlg
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.Route
import com.musicbox.app.ui.components.ArtistArt
import com.musicbox.app.ui.components.FadeSlideIn
import com.musicbox.app.ui.components.SectionHeader
import com.musicbox.app.ui.components.SongArt
import com.musicbox.app.ui.components.SongCard
import com.musicbox.app.ui.components.bounceClick
import com.musicbox.app.ui.theme.Brand
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, listState: LazyListState = rememberLazyListState()) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val scanning by vm.repo.scanning.collectAsState()
    val scanned by vm.repo.scanned.collectAsState()
    val ps by vm.player.state.collectAsState()
    val visible = data.sections.filter { it.visible }
    // Sections that already played their entrance animation do not play it again when scrolled back into view.
    val introduced = vm.introducedSections

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item { HomeTopBar(vm, scanning) }
        if (index.all.isEmpty()) {
            // Before the first scan finishes we show "Scanning", not "No songs found".
            item { EmptyLibrary(scanning || !scanned) { vm.repo.rescan() } }
        } else {
            itemsIndexed(visible, key = { _, sec -> sec.id }) { i, sec ->
                val first = remember(sec.id) { introduced.add(sec.id) }
                FadeSlideIn(delayMs = minOf(i, 6) * 70, animate = first) {
                    SectionBlock(vm, sec, index, data, ps.currentId)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(vm: MainViewModel, scanning: Boolean) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Box", color = Brand.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text("Feel The Music", color = Brand.Dim, fontSize = 12.sp)
        }
        IconButton(onClick = { vm.selectTab(AppTab.SEARCH) }) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
        }
        if (scanning) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Brand.Blue, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = { vm.repo.rescan() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan", tint = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Brand.Dim, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (scanning) "Scanning your music..." else "No songs found yet",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Copy songs to your phone, then tap rescan. Tiny sounds, ringtones and WhatsApp audio are skipped.",
            color = Brand.Dim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (!scanning) Button(onClick = onRescan) { Text("Rescan") }
    }
}

@Composable
private fun SectionBlock(vm: MainViewModel, sec: HomeSection, index: LibraryIndex, data: UserData, playingId: Long?) {
    when (sec.type) {
        SectionType.ARTISTS -> ArtistHero(vm, index, data)
        SectionType.NEW_SONGS -> SongRowBlock(vm, sec.title, FolderKind.NEW_SONGS, "", index, data, playingId)
        SectionType.RECENT -> SongRowBlock(vm, sec.title, FolderKind.RECENT, "", index, data, playingId)
        SectionType.MOST_PLAYED -> RankedGrid(vm, sec.title, index, data, playingId)
        SectionType.FAVORITES -> SongRowBlock(vm, sec.title, FolderKind.FAVORITES, "", index, data, playingId)
        SectionType.MOODS -> MoodsBlock(vm, sec.title, index, data)
        SectionType.MOOD -> SongRowBlock(vm, sec.title, FolderKind.MOOD, sec.param, index, data, playingId)
        SectionType.FOLDER -> SongRowBlock(vm, sec.title, FolderKind.CUSTOM, sec.param, index, data, playingId)
        SectionType.FOLDERS -> FoldersBlock(vm, sec.title, data)
        SectionType.RECENT_ADDED -> SongRowBlock(vm, sec.title, FolderKind.RECENT_ADDED, "", index, data, playingId)
        SectionType.FORGOTTEN -> SongRowBlock(vm, sec.title, FolderKind.FORGOTTEN, "", index, data, playingId)
        SectionType.NEVER_PLAYED -> SongRowBlock(vm, sec.title, FolderKind.NEVER_PLAYED, "", index, data, playingId)
        SectionType.LONG -> SongRowBlock(vm, sec.title, FolderKind.LONG, "", index, data, playingId)
    }
}

// ------------------------------------------------------------------ artists hero

private data class HeroPage(
    val kind: Int,
    val id: String,
    val title: String,
    val count: Int,
    val coverIds: List<Long>,
    val photo: String?,
)

@Composable
private fun ArtistHero(vm: MainViewModel, index: LibraryIndex, data: UserData) {
    val pages = remember(index, data) {
        val list = ArrayList<HeroPage>()
        for (a in data.artists) {
            val s = index.artistSongs[a.id].orEmpty()
            if (s.isNotEmpty()) {
                val covers = s.sortedByDescending { it.state.plays }.take(4).map { it.id }
                list.add(HeroPage(0, a.id, a.name, s.size, covers, a.photo))
            }
        }
        val other = index.others.values.flatMap { it.songs }
        if (other.isNotEmpty()) list.add(HeroPage(1, "", "Others", other.size, other.take(4).map { it.id }, null))
        if (index.unidentified.isNotEmpty()) {
            list.add(HeroPage(2, "", "Unidentified", index.unidentified.size, index.unidentified.take(4).map { it.id }, null))
        }
        list
    }
    if (pages.isEmpty()) return

    val pager = rememberPagerState(pageCount = { pages.size })
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pages.size, dragged) {
        if (dragged || pages.size < 2) return@LaunchedEffect
        while (true) {
            delay(4000)
            val next = (pager.currentPage + 1) % pages.size
            if (next == 0) pager.scrollToPage(0) else pager.animateScrollToPage(next)
        }
    }

    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { i ->
        val p = pages[i]
        HeroCard(
            p,
            // The page in the middle is full size; its neighbours are a little smaller and fainter.
            modifier = Modifier.graphicsLayer {
                val distance = Math.abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                val scale = lerp(1f, 0.92f, distance)
                scaleX = scale
                scaleY = scale
                alpha = lerp(1f, 0.6f, distance)
            },
            onOpen = {
                when (p.kind) {
                    0 -> vm.openFolder(FolderKind.ARTIST, p.id, p.title)
                    1 -> vm.open(Route.Others)
                    else -> vm.openFolder(FolderKind.UNIDENTIFIED, "", "Unidentified")
                }
            },
            onPlay = {
                val songs = when (p.kind) {
                    0 -> Folders.songs(index, data, FolderKind.ARTIST, p.id)
                    1 -> index.others.values.flatMap { it.songs }
                    else -> index.unidentified
                }
                vm.player.playQueue(songs, 0)
            },
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pages.size <= 9) {
            for (i in pages.indices) {
                val dot by animateDpAsState(
                    targetValue = if (i == pager.currentPage) 9.dp else 6.dp,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "dot",
                )
                Box(
                    Modifier.padding(horizontal = 3.dp).size(dot).clip(CircleShape)
                        .background(if (i == pager.currentPage) Brand.Blue else Brand.Dim.copy(alpha = 0.4f))
                )
            }
        } else {
            Text("${pager.currentPage + 1} / ${pages.size}", color = Brand.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HeroCard(p: HeroPage, modifier: Modifier, onOpen: () -> Unit, onPlay: () -> Unit) {
    Box(modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).bounceClick(onOpen)) {
        ArtistArt(
            p.title,
            p.photo,
            p.coverIds,
            Modifier.fillMaxSize(),
            big = true,
            shape = RoundedCornerShape(0.dp),
            initialsSize = 72,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.15f)))
            )
        )
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp)) {
            Text(
                if (p.kind == 0) "ARTIST" else "FOLDER",
                color = Brand.Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                p.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            Text("${p.count} songs", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp).clip(CircleShape).background(Brand.Blue)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

// ------------------------------------------------------------------ horizontal song rows

@Composable
private fun SongRowBlock(
    vm: MainViewModel,
    title: String,
    kind: FolderKind,
    key: String,
    index: LibraryIndex,
    data: UserData,
    playingId: Long?,
) {
    val songs = remember(index, data, kind, key) { Folders.songs(index, data, kind, key) }
    if (songs.isEmpty()) return
    val queue = remember(songs) { songs.take(300) }
    SectionHeader(title) { vm.openFolder(kind, key, title) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(queue.take(15), key = { it.id }) { s ->
            SongCard(s, s.id == playingId, onClick = { vm.player.playQueue(queue, queue.indexOf(s)) })
        }
    }
}

// ------------------------------------------------------------------ most played (numbered grid)

@Composable
private fun RankedGrid(vm: MainViewModel, title: String, index: LibraryIndex, data: UserData, playingId: Long?) {
    val songs = remember(index, data) { Folders.songs(index, data, FolderKind.MOST_PLAYED, "") }
    SectionHeader(title) { vm.openFolder(FolderKind.MOST_PLAYED, "", title) }
    if (songs.isEmpty()) {
        Text(
            "Play some songs and your most played tracks will show up here, ranked 1, 2, 3...",
            color = Brand.Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    val top = remember(songs) { songs.take(30) }
    val columns = remember(top) { top.chunked(3) }
    val width = LocalConfiguration.current.screenWidthDp.dp - 56.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(columns) { ci, col ->
            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                col.forEachIndexed { ri, s ->
                    val rank = ci * 3 + ri + 1
                    RankRow(rank, s, s.id == playingId) { vm.player.playQueue(top, rank - 1) }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, song: ResolvedSong, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Card.copy(alpha = 0.6f))
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SongArt(song.id, Modifier.size(54.dp), corner = 10.dp)
            Box(
                Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            rank.toString(),
            color = if (rank <= 3) Brand.Cyan else Brand.Dim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (playing) Brand.Blue else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artistLabel + " \u00B7 " + song.state.plays + " plays",
                color = Brand.Dim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ moods and folders

@Composable
private fun MoodsBlock(vm: MainViewModel, title: String, index: LibraryIndex, data: UserData) {
    val moods = data.moods.filter { (index.moodSongs[it.id]?.size ?: 0) > 0 }
    SectionHeader(title)
    if (moods.isEmpty()) {
        Text(
            "No moods yet. Add (sad) or (romantic) to a file name, use Edit info on a song, or try the AI Organizer.",
            color = Brand.Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(moods, key = { it.id }) { m ->
            MoodCard(m, index.moodSongs[m.id]?.size ?: 0) { vm.openFolder(FolderKind.MOOD, m.id, m.name) }
        }
    }
}

@Composable
fun MoodCard(m: MoodDef, count: Int, modifier: Modifier = Modifier.width(150.dp), onClick: () -> Unit) {
    Box(
        modifier.height(96.dp).clip(RoundedCornerShape(18.dp))
            .background(Brand.moodBrush(m.colorA, m.colorB)).clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(m.emoji, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(m.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$count songs", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun FoldersBlock(vm: MainViewModel, title: String, data: UserData) {
    SectionHeader(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(data.folders, key = { it.id }) { f ->
            Column(
                Modifier.width(120.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Card)
                    .clickable { vm.openFolder(FolderKind.CUSTOM, f.id, f.name) }.padding(12.dp),
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(f.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${f.songIds.size} songs", color = Brand.Dim, fontSize = 11.sp)
            }
        }
        item {
            Column(
                Modifier.width(120.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Brand.Dim.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { vm.dialog = Dlg.NewFolder() }.padding(12.dp),
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = Brand.Blue, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("New folder", color = Brand.Blue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

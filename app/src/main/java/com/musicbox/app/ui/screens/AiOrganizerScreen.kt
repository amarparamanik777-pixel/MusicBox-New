@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.Route
import com.musicbox.app.ui.components.ScreenTitle
import com.musicbox.app.ui.theme.Brand

@Composable
fun AiOrganizerScreen(vm: MainViewModel) {
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    var scope by rememberSaveable { mutableStateOf(0) }

    val candidates = remember(index, scope) {
        when (scope) {
            0 -> index.unidentified
            1 -> index.all.filter { it.moods.isEmpty() }.take(300)
            else -> index.all.take(300)
        }
    }
    val hints = remember(index) { index.unidentified.filter { it.hint != null } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("AI Organizer", onBack = { vm.pop() }) }
        item {
            Text(
                "Finds the real title and artist of songs MusicBox could not place, and tags moods and situations. Nothing changes until you tick the guesses you trust and press Apply.",
                color = Brand.Dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (hints.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Card).padding(14.dp)) {
                    Text("Quick guesses from file names (works offline)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    for (s in hints.take(5)) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Artist: ${s.hint}", color = Brand.Cyan, fontSize = 12.sp)
                            }
                            TextButton(onClick = { vm.repo.editSong(s.id, null, s.hint, null) }) { Text("Use") }
                        }
                    }
                    if (hints.size > 5) Text("and ${hints.size - 5} more...", color = Brand.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { for (s in hints) vm.repo.editSong(s.id, null, s.hint, null) }) {
                        Text("Accept all ${hints.size}")
                    }
                }
            }
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = scope == 0, onClick = { scope = 0 }, label = { Text("Unidentified (${index.unidentified.size})") })
                FilterChip(selected = scope == 1, onClick = { scope = 1 }, label = { Text("Songs without mood") })
                FilterChip(selected = scope == 2, onClick = { scope = 2 }, label = { Text("Any songs (first 300)") })
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { vm.runAi(candidates) },
                    enabled = !vm.aiBusy && candidates.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Blue),
                ) {
                    Text(if (vm.aiBusy) "Thinking... ${vm.aiProgress}" else "Ask AI about ${candidates.size} songs")
                }
                if (data.settings.ai.apiKey.isBlank()) {
                    TextButton(onClick = { vm.open(Route.AiSettings) }) { Text("Set up your AI key first") }
                }
                val err = vm.aiError
                if (err != null) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (vm.aiResults.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vm.aiResults.size} guesses", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        vm.aiChecked.clear()
                        vm.aiChecked.addAll(vm.aiResults.map { it.songId })
                    }) { Text("Tick all") }
                    TextButton(onClick = { vm.aiChecked.clear() }) { Text("None") }
                }
            }
            items(vm.aiResults.toList(), key = { it.songId }) { r ->
                val song = index.byId[r.songId]
                val checked = r.songId in vm.aiChecked
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = {
                        if (it) vm.aiChecked.add(r.songId) else vm.aiChecked.remove(r.songId)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(
                            (song?.title ?: "Song") + if (r.title != null && r.title != song?.title) "  \u2192  ${r.title}" else "",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val bits = ArrayList<String>()
                        if (r.artist != null) bits.add("Artist: ${r.artist}")
                        if (r.moods.isNotEmpty()) bits.add(r.moods.joinToString(", "))
                        if (r.situations.isNotEmpty()) bits.add(r.situations.joinToString(", "))
                        bits.add("${(r.confidence * 100).toInt()}% sure")
                        Text(bits.joinToString(" \u00B7 "), color = Brand.Dim, fontSize = 12.sp)
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.applyAi() },
                    enabled = vm.aiChecked.isNotEmpty(),
                    modifier = Modifier.padding(16.dp),
                ) { Text("Apply ${vm.aiChecked.size} selected") }
            }
        }
    }
}

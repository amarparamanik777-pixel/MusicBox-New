@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.AiSettings
import com.musicbox.app.data.ArtistDef
import com.musicbox.app.data.SectionType
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.Route
import com.musicbox.app.ui.components.ArtistArt
import com.musicbox.app.ui.components.ScreenTitle
import com.musicbox.app.ui.theme.Brand

// ---------------------------------------------------------------------------- small building blocks

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Brand.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Card),
        content = content,
    )
}

@Composable
private fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Blue)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Brand.Dim, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Brand.Dim)
    }
}

// ---------------------------------------------------------------------------- settings home

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val index by vm.repo.index.collectAsState()
    val data by vm.repo.userData.collectAsState()
    val scanning by vm.repo.scanning.collectAsState()
    var excluded by remember(data.settings.excludedWords) { mutableStateOf(data.settings.excludedWords.joinToString(", ")) }
    var minSec by remember(data.settings.minDurationSec) { mutableStateOf(data.settings.minDurationSec.toString()) }
    var showEq by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = vm.repo.writeText(uri, vm.repo.exportJson())
            Toast.makeText(ctx, if (ok) "Backup saved" else "Could not save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = vm.repo.readText(uri)
            val ok = text != null && vm.repo.importJson(text)
            Toast.makeText(ctx, if (ok) "Backup restored" else "That file is not a MusicBox backup", Toast.LENGTH_SHORT).show()
        }
    }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle("Settings")

        Group("LIBRARY") {
            val withArtist = index.all.count { it.artistIds.isNotEmpty() }
            Text(
                "${index.all.size} songs \u00B7 $withArtist in artist folders \u00B7 ${index.others.values.sumOf { it.songs.size }} in Others \u00B7 ${index.unidentified.size} unidentified",
                color = Brand.Dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.repo.rescan() }, enabled = !scanning) { Text(if (scanning) "Scanning..." else "Rescan music") }
            }
            OutlinedTextField(
                value = excluded,
                onValueChange = { excluded = it },
                label = { Text("Skip folders containing (comma separated)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = minSec,
                onValueChange = { minSec = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Ignore sounds shorter than (seconds)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            TextButton(
                onClick = {
                    val words = excluded.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    vm.repo.updateSettings { it.copy(excludedWords = words, minDurationSec = minSec.toIntOrNull() ?: 30) }
                    vm.repo.rescan()
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Save and rescan") }
        }

        Group("PERSONALISE") {
            Item(Icons.Rounded.Person, "Artists", "${data.artists.size} artists \u00B7 names, nicknames, photos, order") { vm.open(Route.ArtistsManager) }
            Item(Icons.Rounded.Mood, "Moods", "${data.moods.size} moods and their keywords") { vm.open(Route.MoodsManager) }
            Item(Icons.Rounded.Tune, "Home layout", "Move sections up and down, hide or add sections") { vm.open(Route.HomeLayout) }
        }

        Group("SMART TOOLS") {
            Item(Icons.Rounded.AutoAwesome, "AI Organizer", "${index.unidentified.size} unidentified songs") { vm.open(Route.AiOrganizer) }
            Item(Icons.Rounded.Settings, "AI settings", if (data.settings.ai.apiKey.isBlank()) "Not set up yet" else "Ready") { vm.open(Route.AiSettings) }
        }

        Group("AUDIO") {
            Item(Icons.Rounded.Equalizer, "Equalizer and bass", "Start a song first, then adjust") { showEq = true }
        }

        Group("BACKUP") {
            Item(Icons.Rounded.Save, "Export backup", "Play counts, favourites, folders, layout") { exporter.launch("musicbox-backup.json") }
            Item(Icons.Rounded.Restore, "Restore backup", "Load a file you exported before") { importer.launch(arrayOf("*/*")) }
        }

        Group("ABOUT") {
            Text(
                "MusicBox $version\nPlays only the songs on your phone. No account, no ads, no tracking. The internet permission is used only by the optional AI Organizer, and only file names and tags are sent.",
                color = Brand.Dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    if (showEq) EqualizerSheet(vm) { showEq = false }
}

// ---------------------------------------------------------------------------- AI settings

@Composable
fun AiSettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val data by vm.repo.userData.collectAsState()
    var ai by remember { mutableStateOf(data.settings.ai) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle("AI settings", onBack = { vm.pop() })
        Text(
            "The AI Organizer sends song file names and tags (never your audio) to the service you pick, and shows you its guesses before anything changes. Your key stays on this phone.",
            color = Brand.Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ai.provider == "anthropic",
                onClick = { ai = ai.copy(provider = "anthropic", model = "claude-haiku-4-5-20251001") },
                label = { Text("Claude (Anthropic)") },
            )
            FilterChip(
                selected = ai.provider == "openai",
                onClick = { ai = ai.copy(provider = "openai", model = "gpt-4o-mini") },
                label = { Text("OpenAI-compatible") },
            )
        }
        OutlinedTextField(
            value = ai.apiKey,
            onValueChange = { ai = ai.copy(apiKey = it.trim()) },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = ai.model,
            onValueChange = { ai = ai.copy(model = it.trim()) },
            label = { Text("Model name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (ai.provider == "openai") {
            OutlinedTextField(
                value = ai.baseUrl,
                onValueChange = { ai = ai.copy(baseUrl = it.trim()) },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text("Quick presets (model names change over time, edit if needed):", color = Brand.Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = { ai = ai.copy(baseUrl = "https://api.openai.com/v1", model = "gpt-4o-mini") }, label = { Text("OpenAI") })
                AssistChip(onClick = { ai = ai.copy(baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai", model = "gemini-2.0-flash") }, label = { Text("Gemini") })
                AssistChip(onClick = { ai = ai.copy(baseUrl = "https://api.groq.com/openai/v1", model = "llama-3.3-70b-versatile") }, label = { Text("Groq") })
                AssistChip(onClick = { ai = ai.copy(baseUrl = "https://openrouter.ai/api/v1", model = "openrouter/auto") }, label = { Text("OpenRouter") })
            }
        } else {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Let Claude search the web", color = Color.White, fontSize = 15.sp)
                    Text("Better at identifying unknown songs. Needs web search enabled on your Anthropic account.", color = Brand.Dim, fontSize = 12.sp)
                }
                Switch(checked = ai.webSearch, onCheckedChange = { ai = ai.copy(webSearch = it) })
            }
        }
        Button(
            onClick = {
                vm.repo.updateSettings { it.copy(ai = ai) }
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("Save") }
    }
}

// ---------------------------------------------------------------------------- artists manager

@Composable
fun ArtistsManagerScreen(vm: MainViewModel) {
    val data by vm.repo.userData.collectAsState()
    val index by vm.repo.index.collectAsState()
    var editing by remember { mutableStateOf<ArtistDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ArtistDef?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = photoFor
        if (uri != null && id != null) vm.repo.setArtistPhoto(id, uri)
        photoFor = null
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Artists", onBack = { vm.pop() }, actions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add artist", tint = Color.White) }
        })
        Text(
            "The order here is the order of the Home carousel. Nicknames help the organiser recognise file names.",
            color = Brand.Dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(data.artists, key = { it.id }) { a ->
                val songs = index.artistSongs[a.id].orEmpty()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtistArt(a.name, a.photo, songs.take(4).map { it.id }, Modifier.size(46.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable { editing = a }) {
                        Text(a.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${songs.size} songs", color = Brand.Dim, fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.repo.moveArtist(a.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", tint = Brand.Dim) }
                    IconButton(onClick = { vm.repo.moveArtist(a.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", tint = Brand.Dim) }
                    IconButton(onClick = { editing = a }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Brand.Blue) }
                    IconButton(onClick = { deleting = a }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Brand.Dim) }
                }
            }
        }
    }

    if (adding) ArtistDialog("Add artist", null, onDismiss = { adding = false }, onPhoto = {}) { name, aliases ->
        vm.repo.addArtist(name, aliases)
    }
    val e = editing
    if (e != null) {
        ArtistDialog("Edit artist", e, onDismiss = { editing = null }, onPhoto = {
            photoFor = e.id
            photoPicker.launch("image/*")
        }) { name, aliases -> vm.repo.updateArtist(e.id, name, aliases) }
    }
    val d = deleting
    if (d != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Brand.Card,
            title = { Text("Remove ${d.name}?") },
            text = { Text("Their songs move to Others. Your files are not touched.") },
            confirmButton = { TextButton(onClick = { vm.repo.removeArtist(d.id); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ArtistDialog(
    title: String,
    artist: ArtistDef?,
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(artist?.name ?: "") }
    var aliases by remember { mutableStateOf(artist?.aliases?.joinToString(", ") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.Card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Nicknames (comma separated)") },
                    supportingText = { Text("e.g. Arijit, Arjit Singh") },
                )
                if (artist != null) OutlinedButton(onClick = onPhoto) { Text("Choose photo") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------- moods manager

@Composable
fun MoodsManagerScreen(vm: MainViewModel) {
    val data by vm.repo.userData.collectAsState()
    val index by vm.repo.index.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Moods", onBack = { vm.pop() }, actions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add mood", tint = Color.White) }
        })
        Text(
            "A song joins a mood when its file name has the word in brackets, like (sad), when its title contains a keyword, or when you or the AI choose it.",
            color = Brand.Dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(data.moods, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${index.moodSongs[m.id]?.size ?: 0} songs \u00B7 " + m.keywords.take(6).joinToString(", "),
                            color = Brand.Dim,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.repo.removeMood(m.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Brand.Dim) }
                }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("\uD83C\uDFB5") }
        var words by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Brand.Card,
            title = { Text("New mood") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Emoji") }, singleLine = true)
                    OutlinedTextField(value = words, onValueChange = { words = it }, label = { Text("Keywords (comma separated)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.repo.addMood(name, emoji, words.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() })
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------- home layout

@Composable
fun HomeLayoutScreen(vm: MainViewModel) {
    val data by vm.repo.userData.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Home layout", onBack = { vm.pop() }, actions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add section", tint = Color.White) }
        })
        LazyColumn(Modifier.fillMaxSize()) {
            items(data.sections, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = if (s.visible) Color.White else Brand.Dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(sectionLabel(s.type), color = Brand.Dim, fontSize = 11.sp)
                    }
                    IconButton(onClick = { vm.repo.moveSection(s.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", tint = Brand.Dim) }
                    IconButton(onClick = { vm.repo.moveSection(s.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", tint = Brand.Dim) }
                    Switch(checked = s.visible, onCheckedChange = { vm.repo.setSectionVisible(s.id, it) })
                    IconButton(onClick = { vm.repo.removeSection(s.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Brand.Dim) }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Brand.Card,
            title = { Text("Add a section") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val builtIns = listOf(
                        Triple(SectionType.MOST_PLAYED, "Most Played", ""),
                        Triple(SectionType.FAVORITES, "Favorite Songs", ""),
                        Triple(SectionType.RECENT, "Recently Played", ""),
                        Triple(SectionType.NEW_SONGS, "New Songs", ""),
                        Triple(SectionType.RECENT_ADDED, "Recently Added", ""),
                        Triple(SectionType.FORGOTTEN, "Forgotten Songs", ""),
                        Triple(SectionType.NEVER_PLAYED, "Hidden Gems", ""),
                        Triple(SectionType.LONG, "Long Listens", ""),
                        Triple(SectionType.MOODS, "Moods", ""),
                        Triple(SectionType.FOLDERS, "My Folders", ""),
                    )
                    for ((type, title, param) in builtIns) {
                        TextButton(onClick = { vm.repo.addSection(type, title, param); adding = false }, modifier = Modifier.fillMaxWidth()) { Text(title) }
                    }
                    Text("A single mood", color = Brand.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    for (m in data.moods) {
                        TextButton(onClick = { vm.repo.addSection(SectionType.MOOD, m.name, m.id); adding = false }, modifier = Modifier.fillMaxWidth()) { Text(m.emoji + " " + m.name) }
                    }
                    if (data.folders.isNotEmpty()) Text("One of your folders", color = Brand.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    for (f in data.folders) {
                        TextButton(onClick = { vm.repo.addSection(SectionType.FOLDER, f.name, f.id); adding = false }, modifier = Modifier.fillMaxWidth()) { Text(f.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Close") } },
        )
    }
}

private fun sectionLabel(type: String): String = when (type) {
    SectionType.ARTISTS -> "Auto-sliding artist carousel"
    SectionType.RECENT -> "Row of recently played songs"
    SectionType.MOST_PLAYED -> "Numbered grid, ranked by plays"
    SectionType.FAVORITES -> "Songs you liked"
    SectionType.MOODS -> "Mood cards"
    SectionType.MOOD -> "One mood"
    SectionType.FOLDER -> "One of your folders"
    SectionType.FOLDERS -> "Your folders"
    SectionType.NEW_SONGS -> "Songs you have not sorted yet"
    SectionType.RECENT_ADDED -> "Newest files"
    SectionType.FORGOTTEN -> "Played a lot, not lately"
    SectionType.NEVER_PLAYED -> "Never played yet"
    SectionType.LONG -> "Mashups and long tracks"
    else -> type
}

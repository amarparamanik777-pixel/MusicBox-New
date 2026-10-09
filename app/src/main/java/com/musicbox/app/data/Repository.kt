package com.musicbox.app.data

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.musicbox.app.ai.AiSuggestion
import com.musicbox.app.organize.ArtistMatcher
import com.musicbox.app.organize.Folders
import com.musicbox.app.organize.Organizer
import com.musicbox.app.organize.Place
import com.musicbox.app.organize.PlaceKind
import com.musicbox.app.organize.TransferResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The single place that knows about the library and everything the user changed. */
class Repository(private val context: Context, private val scope: CoroutineScope) {
    private val store = UserDataStore(context, scope)
    val userData: StateFlow<UserData> = store.data

    private val songsFlow = MutableStateFlow<List<Song>>(emptyList())
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _scanned = MutableStateFlow(false)
    val scanned: StateFlow<Boolean> = _scanned.asStateFlow()

    private val organizeInput = store.data.map { it }.distinctUntilChanged { a, b ->
        a.artists == b.artists && a.moods == b.moods && a.songState == b.songState
    }

    val index: StateFlow<LibraryIndex> = combine(songsFlow, organizeInput) { s, u -> s to u }
        .conflate()
        .map { (s, u) -> withContext(Dispatchers.Default) { Organizer.build(s, u) } }
        .stateIn(scope, SharingStarted.Eagerly, LibraryIndex.EMPTY)

    private var observerJob: Job? = null
    private var rescanQueued = false

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    observerJob?.cancel()
                    observerJob = scope.launch {
                        delay(2500)
                        if (_scanned.value) rescan()
                    }
                }
            },
        )
    }

    // ---------------------------------------------------------------- notices (duplicates found after a scan)

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Short messages for the screen, e.g. "this song is already in your library". */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var knownIds: Set<Long>? = null

    init {
        scope.launch {
            index.collect { ix ->
                if (ix.all.isEmpty()) return@collect
                val before = knownIds
                knownIds = ix.byId.keys
                if (before == null) return@collect // the first scan is the starting point, not "new"
                val fresh = ix.all.filter { it.id !in before && it.dupCount > 1 }
                when {
                    fresh.isEmpty() -> {}
                    fresh.size <= 2 -> for (r in fresh) {
                        _notices.tryEmit("\"${r.title}\" is already in your library (${r.dupCount} copies)")
                    }
                    else -> _notices.tryEmit("${fresh.size} of the new songs are already in your library")
                }
            }
        }
    }

    // ---------------------------------------------------------------- scanning

    fun flush() = store.flush()

    fun rescan() {
        if (_scanning.value) {
            // Something changed while we were scanning: scan once more when this one is done.
            rescanQueued = true
            return
        }
        scope.launch {
            _scanning.value = true
            val settings = store.data.value.settings
            val list = withContext(Dispatchers.IO) {
                try {
                    MediaScanner.scan(context, settings)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            songsFlow.value = list
            _scanned.value = true
            _scanning.value = false
            if (rescanQueued) {
                rescanQueued = false
                rescan()
            }
        }
    }

    // ---------------------------------------------------------------- listening stats

    fun markStarted(id: Long) = editState(id) { it.copy(lastPlayed = System.currentTimeMillis()) }

    fun markCounted(id: Long) = editState(id) {
        it.copy(plays = it.plays + 1, lastPlayed = System.currentTimeMillis())
    }

    fun toggleFavorite(id: Long) = editState(id) {
        val now = !it.favorite
        it.copy(favorite = now, favoriteAt = if (now) System.currentTimeMillis() else 0L)
    }

    private fun editState(id: Long, change: (SongState) -> SongState) {
        store.update { u ->
            val key = id.toString()
            u.copy(songState = u.songState + (key to change(u.songState[key] ?: SongState())))
        }
    }

    // ---------------------------------------------------------------- editing songs

    /** Null means "leave unchanged". Blank artist clears the manual artist. */
    fun editSong(id: Long, title: String?, artistText: String?, moods: List<String>?) {
        store.update { u ->
            val key = id.toString()
            var st = u.songState[key] ?: SongState()
            if (title != null) st = st.copy(titleOverride = title.trim().ifBlank { null })
            if (artistText != null) {
                val t = artistText.trim()
                st = if (t.isEmpty()) {
                    st.copy(artistIds = null, otherArtist = null)
                } else {
                    val ids = ArtistMatcher(u.artists).matchStrict(t)
                    if (ids != null) st.copy(artistIds = ids, otherArtist = null)
                    else st.copy(artistIds = emptyList(), otherArtist = t)
                }
            }
            if (moods != null) st = st.copy(moodsOverride = moods)
            u.copy(songState = u.songState + (key to st))
        }
    }

    fun applyAi(list: List<AiSuggestion>) {
        if (list.isEmpty()) return
        store.update { u ->
            val matcher = ArtistMatcher(u.artists)
            val states = u.songState.toMutableMap()
            for (s in list) {
                val key = s.songId.toString()
                var st = states[key] ?: SongState()
                if (s.title != null) st = st.copy(titleOverride = s.title)
                val artist = s.artist
                if (artist != null) {
                    val ids = matcher.matchStrict(artist)
                    st = if (ids != null) st.copy(artistIds = ids, otherArtist = null)
                    else st.copy(artistIds = emptyList(), otherArtist = artist)
                }
                st = st.copy(
                    aiMoods = (st.aiMoods + s.moods).distinct(),
                    situations = (st.situations + s.situations).distinct(),
                )
                states[key] = st
            }
            u.copy(songState = states)
        }
    }

    // ---------------------------------------------------------------- orders and folders

    fun setManualOrder(key: String, ids: List<Long>) {
        store.update { u ->
            if (key.startsWith("folder:")) {
                val fid = key.removePrefix("folder:")
                u.copy(folders = u.folders.map { if (it.id == fid) it.copy(songIds = ids) else it })
            } else {
                u.copy(manualOrder = u.manualOrder + (key to ids))
            }
        }
    }

    fun createFolder(name: String, add: List<Long> = emptyList()): String {
        val id = "f" + System.currentTimeMillis().toString(36)
        store.update { u -> u.copy(folders = u.folders + CustomFolder(id, name.trim().ifBlank { "New folder" }, add)) }
        return id
    }

    fun renameFolder(id: String, name: String) = store.update { u ->
        u.copy(folders = u.folders.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    fun deleteFolder(id: String) = store.update { u ->
        u.copy(
            folders = u.folders.filter { it.id != id },
            sections = u.sections.filter { !(it.type == SectionType.FOLDER && it.param == id) },
        )
    }

    fun addToFolder(id: String, songIds: List<Long>) = store.update { u ->
        u.copy(folders = u.folders.map {
            if (it.id == id) it.copy(songIds = (it.songIds + songIds).distinct()) else it
        })
    }

    fun removeFromFolder(id: String, songId: Long) = store.update { u ->
        u.copy(folders = u.folders.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it })
    }

    // ---------------------------------------------------------------- copy / move between places

    /** True if the song is already in that folder, artist, mood or favourites. */
    fun isIn(songId: Long, place: Place): Boolean {
        val rs = index.value.byId[songId] ?: return false
        return contains(store.data.value, rs, place)
    }

    /**
     * Puts songs into `dest`. With `move` they also leave `source`. A song can live in many places,
     * so copying never removes anything. Songs that were already in `dest` are reported, not added twice.
     */
    fun transfer(ids: List<Long>, dest: Place, source: Place?, move: Boolean): TransferResult {
        val ix = index.value
        var added = 0
        val already = ArrayList<String>()
        store.update { u ->
            added = 0
            already.clear()
            var d = u
            for (id in ids.distinct()) {
                val rs = ix.byId[id] ?: continue
                if (contains(d, rs, dest)) {
                    already.add(rs.title)
                } else {
                    d = put(d, rs, dest)
                    added++
                }
                if (move && source != null && source != dest) d = take(d, rs, source)
            }
            d
        }
        return TransferResult(added, already.toList(), move)
    }

    fun markNewSeen(ids: List<Long>) = store.update { u -> u.copy(seenNew = (u.seenNew + ids).distinct()) }

    fun markAllNewSeen() {
        val ids = Folders.songs(index.value, store.data.value, com.musicbox.app.organize.FolderKind.NEW_SONGS, "").map { it.id }
        if (ids.isNotEmpty()) markNewSeen(ids)
    }

    private fun artistIdsNow(d: UserData, rs: ResolvedSong): List<String> =
        d.songState[rs.id.toString()]?.artistIds ?: rs.artistIds

    private fun moodsNow(d: UserData, rs: ResolvedSong): List<String> =
        d.songState[rs.id.toString()]?.moodsOverride ?: rs.moods

    private fun withState(d: UserData, id: Long, change: (SongState) -> SongState): UserData {
        val key = id.toString()
        return d.copy(songState = d.songState + (key to change(d.songState[key] ?: SongState())))
    }

    private fun contains(d: UserData, rs: ResolvedSong, place: Place): Boolean = when (place.kind) {
        PlaceKind.CUSTOM -> d.folders.firstOrNull { it.id == place.key }?.songIds?.contains(rs.id) == true
        PlaceKind.ARTIST -> place.key in artistIdsNow(d, rs)
        PlaceKind.MOOD -> place.key in moodsNow(d, rs)
        PlaceKind.FAVORITES -> d.songState[rs.id.toString()]?.favorite == true
        PlaceKind.NEW, PlaceKind.OTHER -> false
    }

    private fun put(d: UserData, rs: ResolvedSong, place: Place): UserData = when (place.kind) {
        PlaceKind.CUSTOM -> d.copy(folders = d.folders.map {
            if (it.id == place.key) it.copy(songIds = (it.songIds + rs.id).distinct()) else it
        })
        PlaceKind.ARTIST -> {
            val ids = (artistIdsNow(d, rs) + place.key).distinct()
            withState(d, rs.id) { it.copy(artistIds = ids, otherArtist = null) }
        }
        PlaceKind.MOOD -> {
            val moods = (moodsNow(d, rs) + place.key).distinct()
            withState(d, rs.id) { it.copy(moodsOverride = moods) }
        }
        PlaceKind.FAVORITES -> withState(d, rs.id) {
            it.copy(favorite = true, favoriteAt = if (it.favorite) it.favoriteAt else System.currentTimeMillis())
        }
        PlaceKind.NEW, PlaceKind.OTHER -> d
    }

    private fun take(d: UserData, rs: ResolvedSong, place: Place): UserData = when (place.kind) {
        PlaceKind.CUSTOM -> d.copy(folders = d.folders.map {
            if (it.id == place.key) it.copy(songIds = it.songIds - rs.id) else it
        })
        PlaceKind.ARTIST -> {
            val ids = artistIdsNow(d, rs) - place.key
            withState(d, rs.id) { it.copy(artistIds = ids) }
        }
        PlaceKind.MOOD -> {
            val moods = moodsNow(d, rs) - place.key
            withState(d, rs.id) { it.copy(moodsOverride = moods) }
        }
        PlaceKind.FAVORITES -> withState(d, rs.id) { it.copy(favorite = false, favoriteAt = 0L) }
        PlaceKind.NEW -> d.copy(seenNew = (d.seenNew + rs.id).distinct())
        PlaceKind.OTHER -> d
    }

    // ---------------------------------------------------------------- home sections

    fun moveSection(id: String, delta: Int) = store.update { u ->
        val list = u.sections.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i >= 0 && j in list.indices) {
            val s = list.removeAt(i)
            list.add(j, s)
        }
        u.copy(sections = list)
    }

    fun setSectionVisible(id: String, visible: Boolean) = store.update { u ->
        u.copy(sections = u.sections.map { if (it.id == id) it.copy(visible = visible) else it })
    }

    fun addSection(type: String, title: String, param: String = "") = store.update { u ->
        val id = "s" + System.currentTimeMillis().toString(36)
        u.copy(sections = u.sections + HomeSection(id, type, title, true, param))
    }

    fun removeSection(id: String) = store.update { u -> u.copy(sections = u.sections.filter { it.id != id }) }

    // ---------------------------------------------------------------- artists

    fun addArtist(name: String, aliases: List<String>) = store.update { u ->
        val clean = name.trim()
        if (clean.isEmpty()) return@update u
        var id = DefaultData.slug(clean)
        while (u.artists.any { it.id == id }) id += "x"
        u.copy(artists = u.artists + ArtistDef(id, clean, aliases))
    }

    fun updateArtist(id: String, name: String, aliases: List<String>) = store.update { u ->
        u.copy(artists = u.artists.map {
            if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, aliases = aliases) else it
        })
    }

    fun removeArtist(id: String) = store.update { u -> u.copy(artists = u.artists.filter { it.id != id }) }

    fun moveArtist(id: String, delta: Int) = store.update { u ->
        val list = u.artists.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i >= 0 && j in list.indices) {
            val a = list.removeAt(i)
            list.add(j, a)
        }
        u.copy(artists = list)
    }

    fun setArtistPhoto(artistId: String, uri: Uri?) {
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "artists")
                dir.mkdirs()
                var newPath: String? = null
                if (uri != null) {
                    val bounds = BitmapFactory.Options()
                    bounds.inJustDecodeBounds = true
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 700) sample *= 2
                    val opts = BitmapFactory.Options()
                    opts.inSampleSize = sample
                    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?: return@launch
                    val f = File(dir, "$artistId-${System.currentTimeMillis()}.jpg")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    newPath = f.absolutePath
                }
                val old = store.data.value.artists.firstOrNull { it.id == artistId }?.photo
                store.update { u -> u.copy(artists = u.artists.map { if (it.id == artistId) it.copy(photo = newPath) else it }) }
                if (old != null) File(old).delete()
            } catch (e: Exception) {
                // ignore: the old photo stays
            }
        }
    }

    // ---------------------------------------------------------------- moods

    fun addMood(name: String, emoji: String, keywords: List<String>) = store.update { u ->
        val clean = name.trim()
        if (clean.isEmpty()) return@update u
        var id = DefaultData.slug(clean)
        while (u.moods.any { it.id == id }) id += "x"
        u.copy(moods = u.moods + MoodDef(id, clean, emoji.ifBlank { "🎵" }, keywords))
    }

    fun removeMood(id: String) = store.update { u ->
        u.copy(
            moods = u.moods.filter { it.id != id },
            sections = u.sections.filter { !(it.type == SectionType.MOOD && it.param == id) },
        )
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(change: (AppSettings) -> AppSettings) = store.update { u -> u.copy(settings = change(u.settings)) }

    // ---------------------------------------------------------------- lyrics

    private fun lyricsFile(id: Long): File {
        val dir = File(context.filesDir, "lyrics")
        dir.mkdirs()
        return File(dir, "$id.txt")
    }

    fun getLyrics(id: Long): String = try {
        val f = lyricsFile(id)
        if (f.exists()) f.readText() else ""
    } catch (e: Exception) {
        ""
    }

    fun saveLyrics(id: Long, text: String) {
        try {
            if (text.isBlank()) lyricsFile(id).delete() else lyricsFile(id).writeText(text)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- backup

    fun exportJson(): String = store.exportJson()

    fun importJson(text: String): Boolean {
        val d = store.parse(text) ?: return false
        val keepAi = store.data.value.settings.ai
        store.replace(d.copy(settings = d.settings.copy(ai = keepAi)))
        return true
    }

    fun writeText(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        true
    } catch (e: Exception) {
        false
    }
}

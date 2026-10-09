package com.musicbox.app.organize

import com.musicbox.app.data.Bucket
import com.musicbox.app.data.LibraryIndex
import com.musicbox.app.data.OtherGroup
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.data.Song
import com.musicbox.app.data.SongState
import com.musicbox.app.data.UserData

/**
 * Decides where every song lives:
 *  - a known artist folder (from the tag, the file name or the parent folder),
 *  - "Others" (artist known from the tag but not in your artist list),
 *  - "Unidentified" (nothing usable found).
 */
object Organizer {
    private val genericFolders = setOf(
        "music", "download", "downloads", "songs", "audio", "audios", "sounds", "telegram",
        "bluetooth", "media", "documents", "sdcard", "emulated", "storage", "new folder", "mp3", "my music", "0",
    )

    fun build(songs: List<Song>, data: UserData): LibraryIndex {
        val matcher = ArtistMatcher(data.artists)
        val mm = MoodMatcher(data.moods)
        val artistNames = data.artists.associate { it.id to it.name }
        val moodIds = data.moods.map { it.id }.toSet()
        val base = songs.map { resolve(it, data, matcher, mm, artistNames, moodIds) }
            .sortedBy { it.title.lowercase() }

        // Songs that appear more than once (same title and artist) get a count so the UI can mark them.
        val counts = HashMap<String, Int>()
        for (r in base) {
            val k = dupKey(r) ?: continue
            counts[k] = (counts[k] ?: 0) + 1
        }
        val resolved = base.map { r ->
            val k = dupKey(r)
            val n = if (k == null) 1 else (counts[k] ?: 1)
            if (n > 1) r.copy(dupCount = n) else r
        }
        val duplicateGroups = resolved.filter { it.dupCount > 1 }.groupBy { dupKey(it) ?: "" }.values.toList()

        val artistSongs = HashMap<String, MutableList<ResolvedSong>>()
        val others = LinkedHashMap<String, MutableList<ResolvedSong>>()
        val otherNames = HashMap<String, String>()
        val unidentified = ArrayList<ResolvedSong>()
        val moodSongs = HashMap<String, MutableList<ResolvedSong>>()
        val albums = HashMap<String, MutableList<ResolvedSong>>()

        for (r in resolved) {
            when (r.bucket) {
                Bucket.ARTIST -> for (id in r.artistIds) artistSongs.getOrPut(id) { ArrayList() }.add(r)
                Bucket.OTHER -> {
                    val name = r.otherArtist ?: continue
                    val k = Text.key(name)
                    otherNames.putIfAbsent(k, name)
                    others.getOrPut(k) { ArrayList() }.add(r)
                }
                Bucket.UNIDENTIFIED -> unidentified.add(r)
            }
            for (m in r.moods) moodSongs.getOrPut(m) { ArrayList() }.add(r)
            val album = r.song.album
            if (album != null) albums.getOrPut(album) { ArrayList() }.add(r)
        }

        val otherGroups = LinkedHashMap<String, OtherGroup>()
        for ((k, list) in others.entries.sortedBy { (otherNames[it.key] ?: it.key).lowercase() }) {
            otherGroups[k] = OtherGroup(otherNames[k] ?: k, list)
        }
        return LibraryIndex(
            all = resolved,
            byId = resolved.associateBy { it.id },
            artistSongs = artistSongs,
            others = otherGroups,
            unidentified = unidentified,
            moodSongs = moodSongs,
            albums = albums,
            duplicates = duplicateGroups,
        )
    }

    /** Two songs with the same key are the same song stored twice. Null when there is no usable title. */
    fun dupKey(r: ResolvedSong): String? {
        val t = Text.key(r.title)
        if (t.isEmpty()) return null
        return t + "|" + Text.key(r.artistLabel)
    }

    private fun resolve(
        song: Song,
        data: UserData,
        matcher: ArtistMatcher,
        mm: MoodMatcher,
        artistNames: Map<String, String>,
        moodIds: Set<String>,
    ): ResolvedSong {
        val st = data.songState[song.id.toString()] ?: SongState()
        val base = Text.baseName(song.fileName)
        val parsed = FilenameParser.parse(base, matcher, mm)
        val tag = song.artistTag?.takeIf { usableTag(it) }
        val tagIds = if (tag != null) matcher.matchLoose(tag) else emptyList()

        var ids: List<String> = (parsed.artistIds + tagIds).distinct()
        if (ids.isEmpty()) folderGuess(song.path, matcher)?.let { ids = listOf(it) }

        var other: String? = null
        val manual = st.artistIds
        if (manual != null) {
            ids = manual.filter { id -> artistNames.containsKey(id) }
            if (ids.isEmpty()) other = st.otherArtist
        } else if (ids.isEmpty()) {
            other = st.otherArtist ?: tag?.let { matcher.splitNames(it).firstOrNull() }
        }

        val tagTitle = song.title
        val useTagTitle = tag != null && tagTitle.isNotBlank() && Text.key(tagTitle) != Text.key(base)
        var title = if (useTagTitle) Text.titleCase(FilenameParser.clean(tagTitle)) else parsed.title
        val titleMoods = if (useTagTitle) mm.detect(title) else parsed.titleMoods
        val override = st.titleOverride
        if (override != null && override.isNotBlank()) title = override

        val auto = (parsed.groupMoods + titleMoods + st.aiMoods).distinct()
        val moods = (st.moodsOverride ?: auto).filter { it in moodIds }

        val bucket = when {
            ids.isNotEmpty() -> Bucket.ARTIST
            other != null -> Bucket.OTHER
            else -> Bucket.UNIDENTIFIED
        }
        val label = when {
            ids.isNotEmpty() -> ids.joinToString(", ") { artistNames[it] ?: it }
            other != null -> other
            else -> "Unknown artist"
        }
        return ResolvedSong(
            song = song,
            title = title,
            artistIds = ids,
            artistLabel = label,
            otherArtist = other,
            moods = moods,
            hint = if (bucket == Bucket.UNIDENTIFIED) parsed.hint else null,
            state = st,
            bucket = bucket,
            searchKey = Text.spaced("$title $label ${song.album ?: ""} ${song.fileName}"),
        )
    }

    private fun usableTag(t: String): Boolean {
        val s = t.trim()
        if (s.length < 2) return false
        val l = s.lowercase()
        if (l == "unknown" || l == "unknown artist" || l == "<unknown>" || l == "various artists" ||
            l == "artist" || l == "va" || l == "n/a"
        ) return false
        return !FilenameParser.junkSite.containsMatchIn(s)
    }

    private fun folderGuess(path: String, matcher: ArtistMatcher): String? {
        val parts = path.split('/')
        for (back in 2..3) {
            if (parts.size < back + 1) break
            val folder = parts[parts.size - back]
            if (folder.lowercase() in genericFolders) continue
            matcher.match(folder)?.let { return it }
        }
        return null
    }
}

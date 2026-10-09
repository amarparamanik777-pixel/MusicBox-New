package com.musicbox.app.organize

import com.musicbox.app.data.LibraryIndex
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.data.UserData

enum class FolderKind {
    ARTIST, OTHERS_GROUP, UNIDENTIFIED, MOOD, CUSTOM, ALBUM, FAVORITES,
    MOST_PLAYED, RECENT, RECENT_ADDED, FORGOTTEN, NEVER_PLAYED, LONG, ALL, NEW_SONGS,
}

enum class SortMode { DEFAULT, AZ, NEWEST, MOST }

/** Turns a folder (artist, mood, favourites ...) into an ordered list of songs. */
object Folders {
    private const val DAY = 86_400_000L

    /** Songs added to the phone within this many days count as "new". */
    const val NEW_DAYS = 14

    fun songs(ix: LibraryIndex, d: UserData, kind: FolderKind, key: String): List<ResolvedSong> = when (kind) {
        FolderKind.ARTIST -> orderManual(ix.artistSongs[key].orEmpty(), d.manualOrder["artist:$key"])
        FolderKind.OTHERS_GROUP -> ix.others[key]?.songs.orEmpty()
        FolderKind.UNIDENTIFIED -> ix.unidentified
        FolderKind.MOOD -> ix.moodSongs[key].orEmpty()
        FolderKind.CUSTOM -> d.folders.firstOrNull { it.id == key }?.songIds.orEmpty().mapNotNull { ix.byId[it] }
        FolderKind.ALBUM -> ix.albums[key].orEmpty()
        FolderKind.FAVORITES -> ix.all.filter { it.state.favorite }.sortedByDescending { it.state.favoriteAt }
        FolderKind.MOST_PLAYED -> ix.all.filter { it.state.plays > 0 }.sortedByDescending { it.state.plays }
        FolderKind.RECENT -> ix.all.filter { it.state.lastPlayed > 0 }.sortedByDescending { it.state.lastPlayed }
        FolderKind.RECENT_ADDED -> ix.all.sortedByDescending { it.song.dateAddedSec }
        FolderKind.FORGOTTEN -> {
            val cutoff = System.currentTimeMillis() - 21 * DAY
            ix.all.filter { it.state.plays > 0 && it.state.lastPlayed > 0 && it.state.lastPlayed < cutoff }
                .sortedByDescending { it.state.plays }
        }
        FolderKind.NEVER_PLAYED -> ix.all.filter { it.state.plays == 0 }
        FolderKind.LONG -> ix.all.filter { it.song.durationMs >= 8 * 60_000L }.sortedByDescending { it.song.durationMs }
        FolderKind.ALL -> ix.all
        FolderKind.NEW_SONGS -> {
            val seen = d.seenNew.toHashSet()
            val cutoffSec = System.currentTimeMillis() / 1000L - NEW_DAYS * 86_400L
            ix.all.filter { it.song.dateAddedSec >= cutoffSec && it.id !in seen }
                .sortedByDescending { it.song.dateAddedSec }
        }
    }

    /** Key under which a hand-made order is saved, or null if this folder cannot be re-ordered. */
    fun manualKey(kind: FolderKind, key: String): String? = when (kind) {
        FolderKind.ARTIST -> "artist:$key"
        FolderKind.CUSTOM -> "folder:$key"
        else -> null
    }

    fun sort(list: List<ResolvedSong>, mode: SortMode): List<ResolvedSong> = when (mode) {
        SortMode.DEFAULT -> list
        SortMode.AZ -> list.sortedBy { it.title.lowercase() }
        SortMode.NEWEST -> list.sortedByDescending { it.song.dateAddedSec }
        SortMode.MOST -> list.sortedByDescending { it.state.plays }
    }

    /** Hand-made order first, then new songs A-Z at the end. */
    fun orderManual(list: List<ResolvedSong>, order: List<Long>?): List<ResolvedSong> {
        val sorted = list.sortedBy { it.title.lowercase() }
        if (order.isNullOrEmpty()) return sorted
        val byId = sorted.associateBy { it.id }
        val used = HashSet<Long>()
        val out = ArrayList<ResolvedSong>(sorted.size)
        for (id in order) {
            val s = byId[id]
            if (s != null && used.add(id)) out.add(s)
        }
        for (s in sorted) if (s.id !in used) out.add(s)
        return out
    }
}

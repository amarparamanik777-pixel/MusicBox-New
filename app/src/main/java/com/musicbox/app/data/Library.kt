package com.musicbox.app.data

enum class Bucket { ARTIST, OTHER, UNIDENTIFIED }

/** A song after the organiser decided its title, artist(s) and moods. */
data class ResolvedSong(
    val song: Song,
    val title: String,
    val artistIds: List<String>,
    val artistLabel: String,
    val otherArtist: String?,
    val moods: List<String>,
    val hint: String?,
    val state: SongState,
    val bucket: Bucket,
    /** Lower-case words of title, artist, album and file name. Built once so searching is fast. */
    val searchKey: String = "",
    /** How many songs in the library share this title and artist (1 = unique). */
    val dupCount: Int = 1,
) {
    val id: Long get() = song.id
}

data class OtherGroup(val name: String, val songs: List<ResolvedSong>)

class LibraryIndex(
    val all: List<ResolvedSong>,
    val byId: Map<Long, ResolvedSong>,
    val artistSongs: Map<String, List<ResolvedSong>>,
    val others: Map<String, OtherGroup>,
    val unidentified: List<ResolvedSong>,
    val moodSongs: Map<String, List<ResolvedSong>>,
    val albums: Map<String, List<ResolvedSong>>,
    /** Groups of songs that appear more than once (same title and artist). */
    val duplicates: List<List<ResolvedSong>> = emptyList(),
) {
    companion object {
        val EMPTY = LibraryIndex(
            emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(),
        )
    }
}

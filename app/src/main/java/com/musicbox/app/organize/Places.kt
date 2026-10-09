package com.musicbox.app.organize

/** The kinds of "place" a song can belong to. A song can be in several places at once. */
enum class PlaceKind { CUSTOM, ARTIST, MOOD, FAVORITES, NEW, OTHER }

/**
 * A folder or section a song can be copied to or moved from.
 * CUSTOM = a folder you made, ARTIST = an artist folder, MOOD = a mood, FAVORITES = the heart list,
 * NEW = the "New Songs" list (source only), OTHER = Others / Unidentified (source only).
 */
data class Place(val kind: PlaceKind, val key: String = "") {
    /** Songs can be put here. */
    val isDestination: Boolean
        get() = kind == PlaceKind.CUSTOM || kind == PlaceKind.ARTIST || kind == PlaceKind.MOOD || kind == PlaceKind.FAVORITES

    /** "Move" makes sense when leaving here. (Others/Unidentified empty themselves once a song gets an artist.) */
    val canLeave: Boolean
        get() = kind != PlaceKind.OTHER

    companion object {
        fun of(kind: FolderKind, key: String): Place? = when (kind) {
            FolderKind.CUSTOM -> Place(PlaceKind.CUSTOM, key)
            FolderKind.ARTIST -> Place(PlaceKind.ARTIST, key)
            FolderKind.MOOD -> Place(PlaceKind.MOOD, key)
            FolderKind.FAVORITES -> Place(PlaceKind.FAVORITES)
            FolderKind.NEW_SONGS -> Place(PlaceKind.NEW)
            FolderKind.OTHERS_GROUP, FolderKind.UNIDENTIFIED -> Place(PlaceKind.OTHER)
            else -> null
        }
    }
}

/** What happened when songs were copied or moved. `already` holds titles that were already in the destination. */
data class TransferResult(val added: Int, val already: List<String>, val move: Boolean) {
    fun message(destName: String): String {
        val verb = if (move) "Moved" else "Copied"
        return when {
            added == 0 && already.size == 1 -> "\"${already[0]}\" is already in $destName"
            added == 0 && already.isEmpty() -> "Nothing to ${if (move) "move" else "copy"}"
            added == 0 -> "All ${already.size} songs are already in $destName"
            already.isEmpty() -> "$verb $added ${if (added == 1) "song" else "songs"} to $destName"
            else -> "$verb $added to $destName. ${already.size} already there"
        }
    }
}

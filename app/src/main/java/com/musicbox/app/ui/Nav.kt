package com.musicbox.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Place

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    FAVORITES("Favorites", Icons.Rounded.Favorite),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

sealed interface Route {
    data class Folder(val kind: FolderKind, val key: String = "", val title: String = "") : Route
    object Others : Route
    object AiOrganizer : Route
    object AiSettings : Route
    object ArtistsManager : Route
    object MoodsManager : Route
    object HomeLayout : Route
}

sealed interface Dlg {
    data class EditSong(val id: Long) : Dlg
    data class AddToFolder(val ids: List<Long>) : Dlg
    data class NewFolder(val thenAdd: List<Long> = emptyList()) : Dlg
    /** Copy (or move) songs into a folder, artist, mood or favourites. `source` is where they come from. */
    data class Transfer(
        val ids: List<Long>,
        val move: Boolean,
        val source: Place?,
        val onDone: (() -> Unit)? = null,
    ) : Dlg
    object Sleep : Dlg
}

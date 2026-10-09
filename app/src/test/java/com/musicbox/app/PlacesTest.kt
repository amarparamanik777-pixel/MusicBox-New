package com.musicbox.app

import com.musicbox.app.organize.FolderKind
import com.musicbox.app.organize.Place
import com.musicbox.app.organize.PlaceKind
import com.musicbox.app.organize.TransferResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    @Test
    fun foldersMapToPlaces() {
        assertEquals(Place(PlaceKind.CUSTOM, "f1"), Place.of(FolderKind.CUSTOM, "f1"))
        assertEquals(Place(PlaceKind.ARTIST, "arijit"), Place.of(FolderKind.ARTIST, "arijit"))
        assertEquals(Place(PlaceKind.NEW), Place.of(FolderKind.NEW_SONGS, ""))
        assertNull(Place.of(FolderKind.MOST_PLAYED, ""))
    }

    @Test
    fun onlyRealFoldersAreDestinations() {
        assertTrue(Place(PlaceKind.CUSTOM, "f1").isDestination)
        assertTrue(Place(PlaceKind.FAVORITES).isDestination)
        assertFalse(Place(PlaceKind.NEW).isDestination)
        assertFalse(Place(PlaceKind.OTHER).isDestination)
        assertFalse(Place(PlaceKind.OTHER).canLeave)
    }

    @Test
    fun messagesTellAboutSongsThatAreAlreadyThere() {
        assertEquals("Copied 2 songs to Gym", TransferResult(2, emptyList(), false).message("Gym"))
        assertEquals("Moved 1 song to Gym", TransferResult(1, emptyList(), true).message("Gym"))
        assertEquals("\"Tum Hi Ho\" is already in Gym", TransferResult(0, listOf("Tum Hi Ho"), false).message("Gym"))
        assertEquals("All 3 songs are already in Gym", TransferResult(0, listOf("a", "b", "c"), false).message("Gym"))
        assertEquals("Copied 2 to Gym. 1 already there", TransferResult(2, listOf("a"), false).message("Gym"))
    }
}

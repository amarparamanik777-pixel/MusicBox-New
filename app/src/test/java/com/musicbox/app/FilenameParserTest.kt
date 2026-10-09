package com.musicbox.app

import com.musicbox.app.data.DefaultData
import com.musicbox.app.organize.ArtistMatcher
import com.musicbox.app.organize.FilenameParser
import com.musicbox.app.organize.MoodMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameParserTest {
    private val matcher = ArtistMatcher(DefaultData.artists())
    private val moods = MoodMatcher(DefaultData.moods())
    private fun parse(name: String) = FilenameParser.parse(name, matcher, moods)

    @Test
    fun titleByArtist() {
        val r = parse("tum hi ho by Arijit Singh")
        assertEquals("Tum Hi Ho", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
    }

    @Test
    fun artistAndMoodInBrackets() {
        val r = parse("tum hi ho (Arijit Singh) (romantic)")
        assertEquals("Tum Hi Ho", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
        assertEquals(listOf("romantic"), r.groupMoods)
    }

    @Test
    fun artistDashTitleAndTitleDashArtist() {
        assertEquals(listOf("arijit-singh"), parse("Arijit Singh - Tum Hi Ho").artistIds)
        val r = parse("Tum Hi Ho - Arijit Singh")
        assertEquals("Tum Hi Ho", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
    }

    @Test
    fun threePartNameKeepsFirstPartAsTitle() {
        val r = parse("Kesariya - Brahmastra - Arijit Singh")
        assertEquals("Kesariya", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
    }

    @Test
    fun junkIsRemoved() {
        val r = parse("01. Tum Hi Ho - Arijit Singh [PagalWorld.com] 320kbps")
        assertEquals("Tum Hi Ho", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
    }

    @Test
    fun artistWithoutSeparator() {
        val r = parse("Arijit Singh Tum Hi Ho")
        assertEquals("Tum Hi Ho", r.title)
        assertEquals(listOf("arijit-singh"), r.artistIds)
    }

    @Test
    fun initialsAndTypos() {
        assertEquals(listOf("a-r-rahman"), parse("AR Rahman - Jai Ho").artistIds)
        assertEquals(listOf("a-r-rahman"), parse("A R Rahman - Jai Ho").artistIds)
        assertEquals(listOf("arijit-singh"), parse("Jai Ho by Arjit Singh").artistIds)
    }

    @Test
    fun unknownSongStaysUnidentified() {
        val r = parse("Tum Hi Ho")
        assertTrue(r.artistIds.isEmpty())
        assertNull(r.hint)
    }

    @Test
    fun titleContainingByIsNotSplit() {
        val r = parse("Stand By Me")
        assertEquals("Stand By Me", r.title)
        assertTrue(r.artistIds.isEmpty())
    }

    @Test
    fun twoArtistsAreBothFound() {
        val r = parse("Lata Mangeshkar & Kishore Kumar - Pyar Hua")
        assertEquals(listOf("lata-mangeshkar", "kishore-kumar"), r.artistIds)
    }

    @Test
    fun similarNamesAreNotConfused() {
        assertEquals(listOf("dev-arijit"), parse("Dev Arijit - Song One").artistIds)
        assertEquals(listOf("vishal-shekhar"), parse("Vishal & Shekhar - Dhoom").artistIds)
    }

    @Test
    fun moodsFromTitleWords() {
        assertTrue(parse("Hanuman Chalisa").titleMoods.contains("devotional"))
        assertTrue(parse("Taylor Swift - Love Story").titleMoods.contains("romantic"))
    }
}

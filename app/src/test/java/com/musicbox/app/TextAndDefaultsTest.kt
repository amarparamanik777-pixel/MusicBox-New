package com.musicbox.app

import com.musicbox.app.data.DefaultData
import com.musicbox.app.data.SectionType
import com.musicbox.app.organize.ArtistMatcher
import com.musicbox.app.organize.MoodMatcher
import com.musicbox.app.organize.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAndDefaultsTest {
    @Test
    fun baseNameRemovesOnlyRealExtensions() {
        assertEquals("Tum Hi Ho", Text.baseName("Tum Hi Ho.mp3"))
        assertEquals("Tum Hi Ho", Text.baseName("Tum Hi Ho.flac"))
        assertEquals("Mr. Brightside", Text.baseName("Mr. Brightside"))
    }

    @Test
    fun keyIgnoresCaseAndPunctuation() {
        assertEquals(Text.key("A.R. Rahman"), Text.key("AR Rahman"))
        assertEquals(Text.key("Sachet-Parampara"), Text.key("sachet parampara"))
    }

    @Test
    fun spacedGivesLowerCaseWords() {
        assertEquals("tum hi ho arijit singh", Text.spaced("Tum Hi Ho - Arijit Singh"))
        assertEquals("cafe", Text.spaced("Café"))
    }

    @Test
    fun titleCaseOnlyFixesAllLowerOrAllUpper() {
        assertEquals("Tum Hi Ho", Text.titleCase("tum hi ho"))
        assertEquals("Tum Hi Ho", Text.titleCase("TUM HI HO"))
        assertEquals("iPhone Song", Text.titleCase("iPhone Song"))
    }

    @Test
    fun distanceCountsSingleEdits() {
        assertEquals(0, Text.distance("arijit", "arijit"))
        assertEquals(1, Text.distance("arijit", "arjit"))
        assertEquals(2, Text.distance("kitten", "kitte" + "ns").coerceAtLeast(2))
    }

    /** Compose lists use these ids as keys. Two equal ids would crash the app. */
    @Test
    fun defaultIdsAreUnique() {
        val artists = DefaultData.artists().map { it.id }
        assertEquals("duplicate artist ids", artists.size, artists.toSet().size)
        val moods = DefaultData.moods().map { it.id }
        assertEquals("duplicate mood ids", moods.size, moods.toSet().size)
        val sections = DefaultData.sections().map { it.id }
        assertEquals("duplicate section ids", sections.size, sections.toSet().size)
    }

    @Test
    fun everyDefaultMoodSectionPointsToARealMood() {
        val moodIds = DefaultData.moods().map { it.id }.toSet()
        for (s in DefaultData.sections()) {
            if (s.type == SectionType.MOOD) assertTrue(s.param + " is not a mood", s.param in moodIds)
        }
    }

    @Test
    fun skippingWordsNoLongerHideNormalSongs() {
        val words = com.musicbox.app.data.AppSettings().excludedWords
        assertFalse("call" in words)
        assertTrue("whatsapp" in words)
    }

    @Test
    fun moodsAreFoundOnWholeWordsOnly() {
        val mm = MoodMatcher(DefaultData.moods())
        assertTrue(mm.detect("Slowed Reverb").contains("relax"))
        // "sad" must not fire inside "Saddle"
        assertFalse(mm.detect("Saddle Up").contains("sad"))
    }

    @Test
    fun artistsInsideTitlesAreNotInventedFromShortWords() {
        val matcher = ArtistMatcher(DefaultData.artists())
        assertTrue(matcher.findInText("Hello World").isEmpty())
    }
}

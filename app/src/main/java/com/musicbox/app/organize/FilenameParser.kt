package com.musicbox.app.organize

data class Parsed(
    val title: String,
    val artistIds: List<String>,
    val hint: String?,
    val groupMoods: List<String>,
    val titleMoods: List<String>,
)

/**
 * Understands file names such as
 *   "Tum Hi Ho by Arijit Singh"
 *   "tum hi ho (Arijit Singh) (romantic)"
 *   "Arijit Singh - Tum Hi Ho"
 *   "Kesariya - Brahmastra - Arijit Singh"
 */
object FilenameParser {
    val junkSite = Regex("""(?i)\b(?:www\.)?[a-z0-9\-]{2,}\.(?:com|in|net|org|pk|co|info|me|cc|ws|to)\b""")
    private val kbps = Regex("""(?i)\b\d{2,3}\s?kbps\b""")
    private val leadTrack = Regex("""^\s*\(?\d{1,3}\)?\s*[.\-_)\]]\s*""")
    private val group = Regex("""[(\[{]([^()\[\]{}]*)[)\]}]""")
    private val feat = Regex("""(?i)^(?:feat\.?|ft\.?|featuring|by)\s+""")
    private val byRule = Regex("""(?i)^(.*\S)\s+by\s+(\S.*)$""")
    private val dash = Regex("""\s+[-–—|:~]+\s+|\s*\|\s*""")
    private val spaces = Regex("""\s+""")
    private val edges = charArrayOf('-', '–', '—', '|', ':', '~', ' ')

    /** Removes underscores, web-site names, "320kbps" and leading track numbers. */
    fun clean(raw: String): String {
        var s = raw.replace('_', ' ')
        s = junkSite.replace(s, " ")
        s = kbps.replace(s, " ")
        s = leadTrack.replace(s, "")
        return spaces.replace(s, " ").trim()
    }

    fun parse(base: String, matcher: ArtistMatcher, mm: MoodMatcher): Parsed {
        val s = clean(base)
        val groups = ArrayList<String>()
        val rest = group.replace(s) { m ->
            groups.add(m.groupValues[1].trim())
            " "
        }

        val artistIds = LinkedHashSet<String>()
        val groupMoods = LinkedHashSet<String>()
        for (g in groups) {
            if (g.isEmpty()) continue
            val low = g.lowercase()
            if (low.startsWith("from ") || low.startsWith("film") || low.startsWith("movie") || low.startsWith("ost")) continue
            val ids = matcher.matchStrict(feat.replace(g, ""))
            if (ids != null) artistIds.addAll(ids) else groupMoods.addAll(mm.detect(g))
        }

        val text = spaces.replace(rest, " ").trim().trim(*edges)
        var title = text
        var hint: String? = null
        var found = false

        val by = byRule.find(text)
        if (by != null) {
            val left = by.groupValues[1].trim()
            val right = by.groupValues[2].trim()
            val ids = matcher.matchStrict(right)
            if (ids != null) {
                artistIds.addAll(ids)
                title = left
                found = true
            } else if (right.length >= 3 && right.split(' ').size <= 4) {
                hint = right
            }
        }

        if (!found) {
            val parts = text.split(dash).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val artistParts = ArrayList<Int>()
                for ((i, p) in parts.withIndex()) {
                    val ids = matcher.matchStrict(p)
                    if (ids != null) {
                        artistIds.addAll(ids)
                        artistParts.add(i)
                    }
                }
                if (artistParts.isNotEmpty()) {
                    val others = parts.filterIndexed { i, _ -> i !in artistParts }
                    title = others.firstOrNull() ?: parts.first()
                    found = true
                } else if (artistIds.isNotEmpty()) {
                    title = parts.first()
                } else if (hint == null) {
                    hint = parts.first()
                }
            }
        }

        if (!found && artistIds.isEmpty()) {
            val hits = matcher.findInText(text)
            if (hits.isNotEmpty()) {
                var t = text
                for ((id, phrase) in hits) {
                    artistIds.add(id)
                    t = removePhrase(t, phrase)
                }
                t = t.trim().trim(*edges)
                title = if (t.isNotEmpty()) t else text
                hint = null
            }
        }

        title = spaces.replace(title, " ").trim().trim(*edges)
        title = if (title.isBlank()) Text.titleCase(s) else Text.titleCase(title)
        return Parsed(title, artistIds.toList(), hint, groupMoods.toList(), mm.detect(title))
    }

    private fun removePhrase(original: String, phrase: String): String {
        val words = original.split(spaces).filter { it.isNotEmpty() }
        val ptoks = phrase.split(' ')
        val norm = words.map { Text.spaced(it) }
        var i = 0
        while (i + ptoks.size <= words.size) {
            var ok = true
            for (j in ptoks.indices) {
                if (norm[i + j] != ptoks[j]) {
                    ok = false
                    break
                }
            }
            if (ok) return (words.take(i) + words.drop(i + ptoks.size)).joinToString(" ")
            i++
        }
        return original
    }
}

package com.musicbox.app.organize

import com.musicbox.app.data.ArtistDef
import com.musicbox.app.data.MoodDef

/** Finds known artists inside names, tags and free text. */
class ArtistMatcher(artists: List<ArtistDef>) {
    private val exact = HashMap<String, String>()
    private val fuzzy = ArrayList<Pair<String, String>>()
    private val free = ArrayList<Triple<String, String, Int>>()

    init {
        for (a in artists) {
            val variants = LinkedHashSet<String>()
            variants.add(a.name)
            variants.addAll(a.aliases)
            for (v in variants.toList()) variants.add(mergeInitials(Text.spaced(v)))
            for (v in variants) {
                val k = Text.key(v)
                if (k.length < 2) continue
                if (!exact.containsKey(k)) {
                    exact[k] = a.id
                    if (k.length >= 6) fuzzy.add(k to a.id)
                }
                val sp = Text.spaced(v)
                if (sp.length >= 6) free.add(Triple(" $sp ", a.id, sp.length))
            }
        }
        free.sortByDescending { it.third }
    }

    private fun mergeInitials(s: String): String {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (t in s.split(' ')) {
            if (t.length == 1) {
                sb.append(t)
            } else {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                out.add(t)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out.joinToString(" ")
    }

    /** One name -> artist id. Allows a small typo for longer names. */
    fun match(part: String): String? {
        val k = Text.key(part)
        if (k.length < 2) return null
        exact[k]?.let { return it }
        if (k.length < 6) return null
        val limit = if (k.length >= 12) 2 else 1
        var best: String? = null
        var bestD = limit + 1
        for ((key, id) in fuzzy) {
            if (kotlin.math.abs(key.length - k.length) > limit) continue
            val d = Text.distance(k, key)
            if (d < bestD) {
                bestD = d
                best = id
            }
        }
        return best
    }

    fun splitNames(text: String): List<String> =
        text.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    /** Every name in the text must be a known artist, otherwise null. */
    fun matchStrict(text: String): List<String>? {
        exact[Text.key(text)]?.let { return listOf(it) }
        val parts = splitNames(text)
        if (parts.isEmpty()) return null
        val ids = LinkedHashSet<String>()
        for (p in parts) {
            val id = match(p) ?: return null
            ids.add(id)
        }
        return ids.toList()
    }

    /** Known artists among the names in the text; unknown names are ignored. */
    fun matchLoose(text: String): List<String> {
        exact[Text.key(text)]?.let { return listOf(it) }
        val ids = LinkedHashSet<String>()
        for (p in splitNames(text)) match(p)?.let { ids.add(it) }
        return ids.toList()
    }

    /** Looks for artist names anywhere inside free text. Returns (artist id, matched words). */
    fun findInText(text: String): List<Pair<String, String>> {
        var hay = " " + Text.spaced(text) + " "
        val res = ArrayList<Pair<String, String>>()
        for ((pat, id, _) in free) {
            if (hay.contains(pat)) {
                res.add(id to pat.trim())
                hay = hay.replace(pat, " ")
            }
        }
        return res
    }

    companion object {
        private val SPLIT = Regex(
            """\s*(?:,|;|/|&|\+|\s+x\s+|\s+and\s+|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+|\s+with\s+|\s+vs\.?\s+)\s*""",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** Detects moods from words such as "romantic", "sad", "bhajan". */
class MoodMatcher(moods: List<MoodDef>) {
    private val table: List<Pair<String, List<String>>> = moods.map { m ->
        m.id to (m.keywords + listOf(m.name, m.id)).map { Text.spaced(it) }.filter { it.isNotEmpty() }.distinct()
    }

    fun detect(text: String): List<String> {
        val t = " " + Text.spaced(text) + " "
        val out = ArrayList<String>()
        for ((id, words) in table) {
            if (words.any { t.contains(" $it ") }) out.add(id)
        }
        return out
    }
}

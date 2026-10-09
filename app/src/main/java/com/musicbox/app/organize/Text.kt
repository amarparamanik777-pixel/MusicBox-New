package com.musicbox.app.organize

import java.text.Normalizer

/** Small text helpers used by the organiser. Pure Kotlin so they can be unit-tested. */
object Text {
    private val marks = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")

    fun strip(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")

    /** Letters and digits only, lower case: "A.R. Rahman" and "AR Rahman" give the same key. */
    fun key(s: String): String = strip(s).lowercase().filter { it.isLetterOrDigit() }

    /** Lower case words separated by single spaces. */
    fun spaced(s: String): String = nonAlnum.replace(strip(s).lowercase(), " ").trim()

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    /** "tum hi ho" -> "Tum Hi Ho". Mixed-case text is left alone. */
    fun titleCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return s
        val allLower = letters.all { it.isLowerCase() }
        val allUpper = letters.length > 3 && letters.all { it.isUpperCase() }
        if (!allLower && !allUpper) return s
        return s.split(' ').joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { c -> c.titlecase() }
        }
    }

    /** File name without its extension ("Song feat. X.mp3" -> "Song feat. X"). */
    fun baseName(fileName: String): String {
        val i = fileName.lastIndexOf('.')
        return if (i > 0 && fileName.length - i <= 6) fileName.substring(0, i) else fileName
    }
}

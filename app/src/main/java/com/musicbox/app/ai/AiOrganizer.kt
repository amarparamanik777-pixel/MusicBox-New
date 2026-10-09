package com.musicbox.app.ai

import com.musicbox.app.data.AiSettings
import com.musicbox.app.data.MoodDef
import com.musicbox.app.data.ResolvedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AiSuggestion(
    val songId: Long,
    val title: String?,
    val artist: String?,
    val moods: List<String>,
    val situations: List<String>,
    val confidence: Double,
)

/**
 * Optional online helper. It sends only file names and tags (never audio) to the AI service
 * you configured, and returns suggestions that you review before anything is changed.
 */
object AiOrganizer {
    suspend fun suggest(
        ai: AiSettings,
        songs: List<ResolvedSong>,
        moods: List<MoodDef>,
        artistNames: List<String>,
        onProgress: (Int, Int) -> Unit,
    ): List<AiSuggestion> = withContext(Dispatchers.IO) {
        val out = ArrayList<AiSuggestion>()
        val chunks = songs.chunked(15)
        for ((i, chunk) in chunks.withIndex()) {
            onProgress(i, chunks.size)
            out.addAll(askBatch(ai, chunk, moods, artistNames))
        }
        onProgress(chunks.size, chunks.size)
        out
    }

    private fun askBatch(
        ai: AiSettings,
        chunk: List<ResolvedSong>,
        moods: List<MoodDef>,
        artistNames: List<String>,
    ): List<AiSuggestion> {
        val system = buildString {
            append("You are a careful music librarian for a personal offline music player. ")
            append("The library is mostly Hindi/Bollywood, Punjabi, Bengali, devotional and international music. ")
            append("You receive a JSON list of audio files (id, fileName, tagTitle, tagArtist, folder). ")
            append("For each file work out the real song title and the main artist, and classify the mood and the listening situations. ")
            append("Never invent facts. If you are not sure, use null for title/artist and a low confidence. ")
            append("Allowed moods (use these ids only): ")
            append(moods.joinToString(", ") { it.id })
            append(". Prefer these artist spellings when they apply: ")
            append(artistNames.joinToString(", "))
            append(". Reply with ONLY a JSON array and no other text, in the form ")
            append("[{\"id\":123,\"title\":\"Tum Hi Ho\"|null,\"artist\":\"Arijit Singh\"|null,")
            append("\"moods\":[\"romantic\"],\"situations\":[\"late night drive\"],\"confidence\":0.9}]")
        }
        val items = JSONArray()
        for (r in chunk) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("fileName", r.song.fileName)
            o.put("tagTitle", r.song.title)
            o.put("tagArtist", r.song.artistTag ?: JSONObject.NULL)
            o.put("folder", folderOf(r.song.path))
            items.put(o)
        }
        val text = if (ai.provider == "anthropic") callAnthropic(ai, system, items.toString())
        else callOpenAi(ai, system, items.toString())
        return parse(text, chunk, moods)
    }

    private fun folderOf(path: String): String {
        val parts = path.split('/')
        return if (parts.size >= 2) parts[parts.size - 2] else ""
    }

    private fun callAnthropic(ai: AiSettings, system: String, user: String): String {
        val body = JSONObject()
        body.put("model", ai.model)
        body.put("max_tokens", 4096)
        body.put("system", system)
        body.put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        if (ai.webSearch) {
            val tool = JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 5)
            body.put("tools", JSONArray().put(tool))
        }
        val headers = mapOf(
            "x-api-key" to ai.apiKey,
            "anthropic-version" to "2023-06-01",
            "content-type" to "application/json",
        )
        val resp = post("https://api.anthropic.com/v1/messages", headers, body.toString())
        val content = JSONObject(resp).optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") sb.append(block.optString("text")).append('\n')
        }
        return sb.toString()
    }

    private fun callOpenAi(ai: AiSettings, system: String, user: String): String {
        val body = JSONObject()
        body.put("model", ai.model)
        body.put("temperature", 0.2)
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        msgs.put(JSONObject().put("role", "user").put("content", user))
        body.put("messages", msgs)
        val base = ai.baseUrl.trim().trimEnd('/')
        val headers = mapOf(
            "Authorization" to "Bearer ${ai.apiKey}",
            "Content-Type" to "application/json",
        )
        val resp = post("$base/chat/completions", headers, body.toString())
        return JSONObject(resp).optJSONArray("choices")
            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("AI service said HTTP $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String, chunk: List<ResolvedSong>, moods: List<MoodDef>): List<AiSuggestion> {
        var arr: JSONArray? = null
        var start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        var tries = 0
        while (arr == null && start >= 0 && start < end && tries < 6) {
            arr = try {
                JSONArray(text.substring(start, end + 1))
            } catch (e: Exception) {
                null
            }
            if (arr == null) start = text.indexOf('[', start + 1)
            tries++
        }
        if (arr == null) return emptyList()
        val valid = chunk.map { it.id }.toSet()
        val out = ArrayList<AiSuggestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            if (id !in valid) continue
            val moodIds = strings(o.optJSONArray("moods")).mapNotNull { n ->
                moods.firstOrNull { it.id.equals(n, true) || it.name.equals(n, true) }?.id
            }.distinct()
            out.add(
                AiSuggestion(
                    songId = id,
                    title = str(o, "title"),
                    artist = str(o, "artist"),
                    moods = moodIds,
                    situations = strings(o.optJSONArray("situations")).take(4),
                    confidence = o.optDouble("confidence", 0.5),
                )
            )
        }
        return out
    }

    private fun str(o: JSONObject, key: String): String? {
        if (o.isNull(key)) return null
        val s = o.optString(key).trim()
        return if (s.isEmpty() || s.equals("null", true) || s.equals("unknown", true)) null else s
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = a.optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}

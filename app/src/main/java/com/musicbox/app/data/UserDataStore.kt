package com.musicbox.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** Keeps all user data in one small JSON file inside the app's private storage. */
class UserDataStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "userdata.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val state = MutableStateFlow(seed(load()))
    val data: StateFlow<UserData> = state.asStateFlow()
    private var saveJob: Job? = null
    @Volatile private var dirty = false

    private fun load(): UserData = try {
        if (file.exists()) json.decodeFromString(UserData.serializer(), file.readText()) else UserData()
    } catch (e: Exception) {
        // Never throw the user's data away silently: keep the unreadable file next to the new one.
        try {
            file.renameTo(File(file.parentFile, "userdata.json.bad"))
        } catch (ignored: Exception) {
            // ignore
        }
        UserData()
    }

    private fun seed(u: UserData): UserData {
        var d = u
        if (d.version < 2) {
            // 0.1.0 skipped every path containing "call", which hid songs such as "Call Me Maybe".
            val words = d.settings.excludedWords.filter { it.trim().lowercase() != "call" }
            d = d.copy(version = 2, settings = d.settings.copy(excludedWords = words))
        }
        if (d.version < 3) {
            // 0.2: the "New Songs" section appears on Home for existing installs too (right after Artists).
            if (d.sections.isNotEmpty() && d.sections.none { it.type == SectionType.NEW_SONGS }) {
                val list = d.sections.toMutableList()
                list.add(minOf(1, list.size), HomeSection("newsongs", SectionType.NEW_SONGS, "New Songs"))
                d = d.copy(sections = list)
            }
            d = d.copy(version = 3)
        }
        if (d.artists.isEmpty()) d = d.copy(artists = DefaultData.artists())
        if (d.moods.isEmpty()) d = d.copy(moods = DefaultData.moods())
        if (d.sections.isEmpty()) d = d.copy(sections = DefaultData.sections())
        return d
    }

    fun update(transform: (UserData) -> UserData) {
        state.update(transform)
        scheduleSave()
    }

    fun replace(d: UserData) {
        state.value = seed(d)
        scheduleSave()
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(500)
            writeNow(state.value)
        }
    }

    /** Writes pending changes right now. Called when the app goes to the background. */
    fun flush() {
        if (!dirty) return
        saveJob?.cancel()
        writeNow(state.value)
    }

    @Synchronized
    private fun writeNow(d: UserData) {
        dirty = false
        try {
            val tmp = File(file.parentFile, "userdata.json.tmp")
            tmp.writeText(json.encodeToString(UserData.serializer(), d))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // Saving is best effort; the next change will try again.
            dirty = true
        }
    }

    /** Backup text. The AI key is never exported. */
    fun exportJson(): String {
        val d = state.value
        val clean = d.copy(settings = d.settings.copy(ai = d.settings.ai.copy(apiKey = "")))
        return json.encodeToString(UserData.serializer(), clean)
    }

    fun parse(text: String): UserData? = try {
        json.decodeFromString(UserData.serializer(), text)
    } catch (e: Exception) {
        null
    }
}

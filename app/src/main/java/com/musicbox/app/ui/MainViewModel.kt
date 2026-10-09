package com.musicbox.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.MusicBoxApp
import com.musicbox.app.ai.AiOrganizer
import com.musicbox.app.ai.AiSuggestion
import com.musicbox.app.data.Repository
import com.musicbox.app.data.ResolvedSong
import com.musicbox.app.organize.FolderKind
import com.musicbox.app.player.PlayerController
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val musicApp = app as MusicBoxApp
    val repo: Repository = musicApp.repo
    val player: PlayerController = musicApp.player

    val stack = mutableStateListOf<Route>()
    var tab by mutableStateOf(AppTab.HOME)
    var nowPlayingOpen by mutableStateOf(false)
    var dialog by mutableStateOf<Dlg?>(null)

    /** Home sections that already played their entrance animation (plain set, not UI state). */
    val introducedSections = HashSet<String>()

    // AI organizer state (kept here so it survives screen rotation)
    var aiBusy by mutableStateOf(false)
    var aiProgress by mutableStateOf("")
    var aiError by mutableStateOf<String?>(null)
    val aiResults = mutableStateListOf<AiSuggestion>()
    val aiChecked = mutableStateListOf<Long>()

    fun open(r: Route) {
        stack.add(r)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun selectTab(t: AppTab) {
        stack.clear()
        tab = t
    }

    fun openFolder(kind: FolderKind, key: String = "", title: String = "") {
        open(Route.Folder(kind, key, title))
    }

    fun runAi(songs: List<ResolvedSong>) {
        val d = repo.userData.value
        if (d.settings.ai.apiKey.isBlank()) {
            aiError = "Add your AI key first (Settings > AI settings)."
            return
        }
        if (songs.isEmpty()) {
            aiError = "There is nothing to organise."
            return
        }
        aiBusy = true
        aiError = null
        aiResults.clear()
        aiChecked.clear()
        viewModelScope.launch {
            try {
                val res = AiOrganizer.suggest(d.settings.ai, songs, d.moods, d.artists.map { it.name }) { done, total ->
                    aiProgress = "$done / $total"
                }
                aiResults.addAll(res)
                aiChecked.addAll(
                    res.filter { it.confidence >= 0.7 && (it.artist != null || it.moods.isNotEmpty()) }
                        .map { it.songId }
                )
                if (res.isEmpty()) aiError = "The AI returned nothing usable. Check the model name and try again."
            } catch (e: Exception) {
                aiError = e.message ?: "The AI request failed."
            }
            aiBusy = false
        }
    }

    fun applyAi() {
        val chosen = aiResults.filter { it.songId in aiChecked }
        repo.applyAi(chosen)
        aiResults.removeAll(chosen)
        aiChecked.clear()
    }
}

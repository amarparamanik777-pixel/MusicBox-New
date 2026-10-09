package com.musicbox.app

import android.app.Application
import com.musicbox.app.data.Repository
import com.musicbox.app.player.AudioFx
import com.musicbox.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MusicBoxApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repo: Repository
        private set
    lateinit var player: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, appScope)
        player = PlayerController(
            app = this,
            scope = appScope,
            onStart = { repo.markStarted(it) },
            onCounted = { repo.markCounted(it) },
        )
        appScope.launch {
            repo.userData.map { it.settings.audio }.distinctUntilChanged().collect { AudioFx.applySettings(it) }
        }
    }
}

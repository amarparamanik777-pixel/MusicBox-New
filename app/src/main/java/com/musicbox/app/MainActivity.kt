package com.musicbox.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musicbox.app.ui.AppRoot
import com.musicbox.app.ui.theme.MusicBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        (application as MusicBoxApp).player.connect()
        setContent {
            MusicBoxTheme {
                AppRoot()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Play counts and favourites are saved a moment after a change; make sure nothing is lost.
        (application as MusicBoxApp).repo.flush()
    }
}

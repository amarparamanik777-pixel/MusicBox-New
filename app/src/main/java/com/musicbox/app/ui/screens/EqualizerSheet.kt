@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.musicbox.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.AudioSettings
import com.musicbox.app.player.AudioFx
import com.musicbox.app.ui.MainViewModel
import com.musicbox.app.ui.theme.Brand

@Composable
fun EqualizerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val data by vm.repo.userData.collectAsState()
    val fx by AudioFx.info.collectAsState()
    val s = data.settings.audio

    fun save(n: AudioSettings) = vm.repo.updateSettings { it.copy(audio = n) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Surface,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Audio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = s.eqEnabled, onCheckedChange = { save(s.copy(eqEnabled = it)) })
            }
            if (!fx.ready) {
                Text(
                    "Start playing a song first. The equalizer attaches to the player.",
                    color = Brand.Dim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                val levels = List(fx.bands) { s.bands.getOrNull(it) ?: 0 }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    for ((name, shape) in presets) {
                        AssistChip(onClick = {
                            val lv = List(fx.bands) { b ->
                                val t = if (fx.bands > 1) b / (fx.bands - 1f) else 0f
                                (shape(t) * (if (shape(t) >= 0f) fx.max else -fx.min)).toInt().coerceIn(fx.min, fx.max)
                            }
                            save(s.copy(eqEnabled = true, bands = lv))
                        }, label = { Text(name) })
                    }
                }
                for (b in 0 until fx.bands) {
                    val hz = fx.centersHz.getOrNull(b) ?: 0
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", color = Brand.Dim, fontSize = 12.sp)
                    Slider(
                        value = levels[b].toFloat(),
                        onValueChange = { v ->
                            val nb = levels.toMutableList()
                            nb[b] = v.toInt()
                            save(s.copy(eqEnabled = true, bands = nb))
                        },
                        valueRange = fx.min.toFloat()..fx.max.toFloat(),
                    )
                }
            }
            if (fx.ready && fx.bassOk) {
                Text("Bass boost", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = s.bass.toFloat(), onValueChange = { save(s.copy(bass = it.toInt())) }, valueRange = 0f..1000f)
            }
            if (fx.ready && fx.loudOk) {
                Text("Loudness boost", color = Color.White, fontSize = 14.sp)
                Slider(value = s.loudness.toFloat(), onValueChange = { save(s.copy(loudness = it.toInt())) }, valueRange = 0f..1000f)
            }
            TextButton(onClick = { save(AudioSettings()) }) { Text("Reset") }
        }
    }
}

/** Preset curves from 0.0 (lowest band) to 1.0 (highest band); the result is a fraction of the band range. */
private val presets: List<Pair<String, (Float) -> Float>> = listOf(
    "Flat" to { _: Float -> 0f },
    "Bass" to { t: Float -> if (t < 0.5f) 0.8f * (1f - t / 0.5f) else 0f },
    "Treble" to { t: Float -> if (t > 0.5f) 0.8f * ((t - 0.5f) / 0.5f) else 0f },
    "Vocal" to { t: Float -> (0.7f * (1f - kotlin.math.abs(t - 0.55f) * 2.4f)).coerceAtLeast(-0.1f) },
    "Party" to { t: Float -> 0.6f * kotlin.math.abs(t - 0.5f) * 2f },
)

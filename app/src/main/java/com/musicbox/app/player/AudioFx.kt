package com.musicbox.app.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.musicbox.app.data.AudioSettings
import kotlinx.coroutines.flow.MutableStateFlow

data class FxInfo(
    val ready: Boolean = false,
    val bands: Int = 0,
    val min: Int = 0,
    val max: Int = 0,
    val centersHz: List<Int> = emptyList(),
    val bassOk: Boolean = false,
    val loudOk: Boolean = false,
)

/** Equalizer, bass boost and loudness, attached to the player's audio session. */
object AudioFx {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var session = 0
    private var current = AudioSettings()
    val info = MutableStateFlow(FxInfo())

    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == session) return
        release()
        session = sessionId
        var bands = 0
        var min = 0
        var max = 0
        var centers: List<Int> = emptyList()
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            bands = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            min = range[0].toInt()
            max = range[1].toInt()
            centers = (0 until bands).map { e.getCenterFreq(it.toShort()) / 1000 }
        } catch (ex: Exception) {
            eq = null
        }
        try {
            bass = BassBoost(0, sessionId)
        } catch (ex: Exception) {
            bass = null
        }
        try {
            loud = LoudnessEnhancer(sessionId)
        } catch (ex: Exception) {
            loud = null
        }
        info.value = FxInfo(eq != null, bands, min, max, centers, bass != null, loud != null)
        applySettings(current)
    }

    fun applySettings(s: AudioSettings) {
        current = s
        // Some phones do not support every effect, so each one has its own try block.
        try {
            val e = eq
            if (e != null) {
                e.setEnabled(s.eqEnabled)
                val i = info.value
                for (b in 0 until i.bands) {
                    val lvl = (s.bands.getOrNull(b) ?: 0).coerceIn(i.min, i.max)
                    e.setBandLevel(b.toShort(), lvl.toShort())
                }
            }
        } catch (ex: Exception) {
            // ignore
        }
        try {
            val bb = bass
            if (bb != null) {
                bb.setEnabled(s.bass > 0)
                if (bb.strengthSupported) bb.setStrength(s.bass.coerceIn(0, 1000).toShort())
            }
        } catch (ex: Exception) {
            // ignore
        }
        try {
            val le = loud
            if (le != null) {
                le.setEnabled(s.loudness > 0)
                le.setTargetGain(s.loudness.coerceIn(0, 1500))
            }
        } catch (ex: Exception) {
            // ignore
        }
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) { /* ignore */ }
        try { bass?.release() } catch (e: Exception) { /* ignore */ }
        try { loud?.release() } catch (e: Exception) { /* ignore */ }
        eq = null
        bass = null
        loud = null
        session = 0
        info.value = FxInfo()
    }
}

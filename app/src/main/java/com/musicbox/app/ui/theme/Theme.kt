package com.musicbox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Colours taken from the MusicBox logo: pink, violet, blue and cyan on deep navy. */
object Brand {
    val Bg = Color(0xFF060B1A)
    val BgTop = Color(0xFF0B1636)
    val Surface = Color(0xFF0D1530)
    val Card = Color(0xFF111C3D)
    val Blue = Color(0xFF2F7BFF)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF3D81)
    val Cyan = Color(0xFF22D3EE)
    val Dim = Color(0xFF93A4C8)

    val logo: Brush = Brush.linearGradient(listOf(Pink, Violet, Blue, Cyan))
    val screen: Brush = Brush.verticalGradient(listOf(BgTop, Bg))

    private val pairs = listOf(
        Color(0xFF1D4ED8) to Color(0xFF7C3AED),
        Color(0xFFDB2777) to Color(0xFF7C3AED),
        Color(0xFF0891B2) to Color(0xFF2563EB),
        Color(0xFFEA580C) to Color(0xFFBE185D),
        Color(0xFF059669) to Color(0xFF0E7490),
        Color(0xFF7C3AED) to Color(0xFF1D4ED8),
        Color(0xFFD97706) to Color(0xFFDC2626),
        Color(0xFF4F46E5) to Color(0xFF0EA5E9),
    )

    fun gradientFor(seed: Long): Brush {
        val p = pairs[(seed.hashCode() and 0x7fffffff) % pairs.size]
        return Brush.linearGradient(listOf(p.first, p.second))
    }

    fun gradientFor(name: String): Brush = gradientFor(name.hashCode().toLong())

    fun moodBrush(a: Long, b: Long): Brush = Brush.linearGradient(listOf(Color(a), Color(b)))
}

@Composable
fun MusicBoxTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Brand.Blue,
        onPrimary = Color.White,
        secondary = Brand.Violet,
        onSecondary = Color.White,
        tertiary = Brand.Cyan,
        background = Brand.Bg,
        onBackground = Color.White,
        surface = Brand.Surface,
        onSurface = Color.White,
        surfaceVariant = Brand.Card,
        onSurfaceVariant = Brand.Dim,
        outline = Color(0xFF2A3A66),
        outlineVariant = Color(0xFF1B2850),
        error = Color(0xFFFF5C7A),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

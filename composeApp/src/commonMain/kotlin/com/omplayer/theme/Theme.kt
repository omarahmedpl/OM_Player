package com.omplayer.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Ported 1:1 from prototype/tokens.css. Never hard-code colours in screens; read OmTheme.colors. */
@Immutable
data class OmColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val primary: Color,
    val primaryHover: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val scrim: Color = Color(0xB806060A),
    val glass: Color = Color(0x9E16161E),
    val onVideo: Color = Color.White,
    val isDark: Boolean,
)

val DarkColors = OmColors(
    bg = Color(0xFF0A0A0E), surface1 = Color(0xFF121218), surface2 = Color(0xFF1A1A22), surface3 = Color(0xFF24242E),
    hairline = Color(0x14FFFFFF), hairlineStrong = Color(0x24FFFFFF),
    text = Color(0xFFF4F4F6), text2 = Color(0xFFB4B4BE), text3 = Color(0xFF8A8A96),
    primary = Color(0xFFE11D48), primaryHover = Color(0xFFF43F5E), primarySoft = Color(0x24F43F5E), onPrimary = Color.White,
    accent = Color(0xFF60A5FA), success = Color(0xFF34D399), warning = Color(0xFFFBBF24), danger = Color(0xFFF87171),
    isDark = true,
)

val LightColors = OmColors(
    bg = Color(0xFFF7F7F9), surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF0F0F4), surface3 = Color(0xFFE6E6EC),
    hairline = Color(0x14000000), hairlineStrong = Color(0x24000000),
    text = Color(0xFF111116), text2 = Color(0xFF4A4A55), text3 = Color(0xFF5E5E69),
    primary = Color(0xFFBE123C), primaryHover = Color(0xFF9F1239), primarySoft = Color(0x1AE11D48), onPrimary = Color.White,
    accent = Color(0xFF2563EB), success = Color(0xFF047857), warning = Color(0xFFB45309), danger = Color(0xFFDC2626),
    glass = Color(0xB8FFFFFF),
    isDark = false,
)

/** Motion tokens (Emil Kowalski curves). UI motion stays under 300 ms; sheets ≤ 380 ms. */
object OmMotion {
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    const val Press = 140
    const val Fast = 180
    const val Base = 240
    const val Sheet = 380
    const val PressScale = 0.97f
}

object OmSpace {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 20.dp
    val s6 = 24.dp; val s8 = 32.dp; val s10 = 40.dp; val s12 = 48.dp
    val tap = 44.dp
}

object OmRadius {
    val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 22.dp
}

enum class ThemeMode { Dark, Light, System }

val LocalOmColors = staticCompositionLocalOf { DarkColors }

object OmTheme {
    val colors: OmColors @Composable get() = LocalOmColors.current
}

@Composable
fun OmTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    OmScheme(if (dark) DarkColors else LightColors, content)
}

/** Forces a colour set for a subtree, e.g. the player always stays dark (like .scheme-dark in the prototype). */
@Composable
fun OmScheme(colors: OmColors, content: @Composable () -> Unit) {
    val m3 = if (colors.isDark) darkColorScheme(
        primary = colors.primary, onPrimary = colors.onPrimary, background = colors.bg, onBackground = colors.text,
        surface = colors.surface1, onSurface = colors.text, surfaceVariant = colors.surface2, onSurfaceVariant = colors.text2,
        outline = colors.hairlineStrong, error = colors.danger,
    ) else lightColorScheme(
        primary = colors.primary, onPrimary = colors.onPrimary, background = colors.bg, onBackground = colors.text,
        surface = colors.surface1, onSurface = colors.text, surfaceVariant = colors.surface2, onSurfaceVariant = colors.text2,
        outline = colors.hairlineStrong, error = colors.danger,
    )
    CompositionLocalProvider(LocalOmColors provides colors) {
        MaterialTheme(colorScheme = m3, content = content)
    }
}

package com.rgservice.carlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rgservice.carlauncher.R

/** Paleta del diseño: pocos colores, un acento y uno de alerta. */
@Immutable
data class CarColors(
    val ground: Color,
    val card: Color,
    val card2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val warn: Color,
    val track: Color,
    val tick: Color,
    val pill: Color,
    val glow: Color,
    val isDark: Boolean,
)

// Colores tomados del diseño de referencia: azul marino profundo con acento azul vivo
val DarkColors = CarColors(
    ground = Color(0xFF070B14),
    card = Color(0xFF0F1624),
    card2 = Color(0xFF172133),
    line = Color(0xFF1F2B3F),
    text = Color(0xFFF2F5FA),
    muted = Color(0xFF8E9AAF),
    accent = Color(0xFF2F7CFF),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFFF7A59),
    track = Color(0xFF1E2839),
    tick = Color(0xFF2E3A4F),
    pill = Color(0x292F7CFF),
    glow = Color(0x332F7CFF),
    isDark = true,
)

val LightColors = CarColors(
    ground = Color(0xFFE3E8EF),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF1F4F8),
    line = Color(0xFFDCE2EA),
    text = Color(0xFF0F1520),
    muted = Color(0xFF536074),   // bien legible a pleno sol
    accent = Color(0xFF1F6BF0),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFC2361A),
    track = Color(0xFFE2E7EE),
    tick = Color(0xFFC5CDD8),
    pill = Color(0x1A1F6BF0),
    glow = Color(0x261F6BF0),
    isDark = false,
)

/** Colores de los círculos de los accesos rápidos (como en el diseño). */
object TileTint {
    val music = Color(0xFF2F7CFF)
    val radio = Color(0xFFE2A04F)
    val phone = Color(0xFF3FC487)
    val bluetooth = Color(0xFF8C7BFF)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val LocalCarColors = staticCompositionLocalOf { DarkColors }

/** Acceso corto a los colores: CarTheme.colors.accent */
object CarTheme {
    val colors: CarColors
        @Composable get() = LocalCarColors.current
}

@Composable
fun CarLauncherTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCarColors provides if (dark) DarkColors else LightColors) {
        content()
    }
}

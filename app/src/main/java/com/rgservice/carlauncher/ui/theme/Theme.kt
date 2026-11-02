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
    // Inicio "luces encendidas / apagadas" (guía de rediseño)
    val bgCenter: Color = Color(0xFF0B2A5C),   // fondo: centro del degradado (detrás del auto)
    val bgEdge: Color = Color(0xFF06122A),     // fondo: bordes
    val panel: Color = Color(0xD90E1A30),      // panel grande (85 %)
    val panelLine: Color = Color(0xFF1C2C48),
    val mainCard: Color = Color(0xFF13213A),   // tarjeta de la app principal
    val tileMusic: Color = Color(0xFF1E7BFF),
    val tileRadio: Color = Color(0xFFFF8A4C),
    val tileEq: Color = Color(0xFF2EC4B6),
)

// Colores de la guía de rediseño: oscuro = luces encendidas (noche), claro = luces apagadas (día)
val DarkColors = CarColors(
    ground = Color(0xFF06122A),
    card = Color(0xFF13213A),
    card2 = Color(0xFF182945),
    line = Color(0xFF1C2C48),
    text = Color(0xFFF3F6FA),
    muted = Color(0xFF8FA3BF),
    accent = Color(0xFF1E7BFF),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFFF7A59),
    track = Color(0xFF1E2C46),
    tick = Color(0xFF2C3D5C),
    pill = Color(0x2E1E7BFF),
    glow = Color(0x401E7BFF),
    isDark = true,
)

val LightColors = CarColors(
    ground = Color(0xFFDCE8F6),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFF1F5FA),
    line = Color(0xFFD5E0EE),
    text = Color(0xFF0D1B2E),
    muted = Color(0xFF5A6B82),
    accent = Color(0xFF1565E0),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFC2361A),
    track = Color(0xFFE1E8F1),
    tick = Color(0xFFC3CEDC),
    pill = Color(0x1A1565E0),
    glow = Color(0x261565E0),
    isDark = false,
    bgCenter = Color(0xFFF5F8FC),
    bgEdge = Color(0xFFDCE8F6),
    panel = Color(0xBFFFFFFF),
    panelLine = Color(0xFFD5E0EE),
    mainCard = Color(0xFF0F1B2E),
    tileMusic = Color(0xFF1565E0),
    tileRadio = Color(0xFFE2622A),
    tileEq = Color(0xFF13998C),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** Tipografía de la pantalla de inicio (igual a la del diseño de referencia). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
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

package com.rgservice.carlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.rgservice.carlauncher.ui.theme.CarTheme

/** Proporción del dibujo del auto (ancho / alto). */
const val CAR_RATIO = 316f / 178f

/**
 * Fondo de "estudio" detrás del auto, como en el diseño: pared con un reflector arriba,
 * la línea del horizonte donde empieza el piso, piso brillante con líneas en perspectiva,
 * destellos de luz y la sombra del auto. Solo dibujo: no cambia ninguna función.
 *
 * [stage] = lugar del auto dentro de la tarjeta (null = sin auto: solo el degradé de fondo).
 */
@Composable
fun StudioBackdrop(stage: Rect?, modifier: Modifier) {
    val c = CarTheme.colors
    val dark = c.isDark
    val accent = c.accent
    Canvas(modifier) {
        // 1) Fondo: pared un poco más clara en el medio, piso oscuro abajo
        val car = stage?.let { carBox(it) }
        val horizon = car?.let { it.top + it.height * 0.60f } ?: (size.height * 0.55f)
        val hFrac = (horizon / size.height).coerceIn(0.2f, 0.9f)
        drawRect(
            Brush.verticalGradient(
                0f to (if (dark) Color(0xFF0D1626) else Color(0xFFFDFEFF)),
                hFrac to (if (dark) Color(0xFF17243B) else Color(0xFFEFF3F8)),
                (hFrac + 0.02f).coerceAtMost(1f) to (if (dark) Color(0xFF0B1220) else Color(0xFFE6EBF2)),
                1f to (if (dark) Color(0xFF060A12) else Color(0xFFDDE3EB)),
            ),
        )
        if (car == null) return@Canvas
        val cx = car.center.x

        // 2) Reflector de arriba: cono de luz sobre la pared
        drawCircle(
            Brush.radialGradient(
                listOf(if (dark) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.95f), Color.Transparent),
                center = Offset(cx, car.top - car.height * 0.05f), radius = car.width * 0.75f,
            ),
            radius = car.width * 0.75f, center = Offset(cx, car.top - car.height * 0.05f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.6f), Color.Transparent),
                center = Offset(cx, car.top + car.height * 0.2f), radius = car.width * 0.45f,
            ),
            radius = car.width * 0.45f, center = Offset(cx, car.top + car.height * 0.2f),
        )

        // 3) Piso: líneas en perspectiva hacia el centro del horizonte y líneas horizontales
        val floorBottom = size.height
        val lineCol = if (dark) Color.White.copy(alpha = 0.05f) else Color(0xFF1F2B3F).copy(alpha = 0.05f)
        val spread = size.width * 1.6f
        for (i in -6..6) {
            val xBottom = cx + spread * i / 6f
            drawLine(lineCol, Offset(cx + (xBottom - cx) * 0.08f, horizon), Offset(xBottom, floorBottom), strokeWidth = 1.2f)
        }
        var y = horizon + 6f
        var step = 8f
        while (y < floorBottom) {
            drawLine(lineCol, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            step *= 1.45f
            y += step
        }

        // 4) Línea del horizonte con brillo (más fuerte en el centro)
        val glowH = car.height * 0.16f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, accent.copy(alpha = if (dark) 0.16f else 0.08f), Color.Transparent),
                startY = horizon - glowH, endY = horizon + glowH,
            ),
            topLeft = Offset(0f, horizon - glowH), size = Size(size.width, glowH * 2),
        )
        drawLine(
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    accent.copy(alpha = if (dark) 0.45f else 0.25f),
                    (if (dark) Color.White else accent).copy(alpha = if (dark) 0.55f else 0.35f),
                    accent.copy(alpha = if (dark) 0.45f else 0.25f),
                    Color.Transparent,
                ),
            ),
            Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 2f,
        )

        // 5) Destellos del piso mojado: franjas de luz que bajan desde el auto
        val wheels = car.top + car.height * 0.90f
        val streakH = (floorBottom - wheels) * 0.85f
        val streakCol = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.9f)
        listOf(-0.34f to 0.020f, -0.12f to 0.035f, 0.10f to 0.03f, 0.33f to 0.022f, 0.46f to 0.012f, -0.46f to 0.012f).forEach { (dx, wf) ->
            val x = cx + car.width * dx
            val w = car.width * wf
            drawRect(
                Brush.verticalGradient(listOf(streakCol, Color.Transparent), startY = wheels, endY = wheels + streakH),
                topLeft = Offset(x - w / 2f, wheels), size = Size(w, streakH),
            )
        }

        // 6) Sombra debajo del auto y luz de color del piso
        oval(Offset(cx, wheels), car.width * 0.52f, car.height * 0.10f,
            Color.Black.copy(alpha = if (dark) 0.55f else 0.20f))
        oval(Offset(cx, wheels + car.height * 0.04f), car.width * 0.62f, car.height * 0.16f,
            accent.copy(alpha = if (dark) 0.30f else 0.14f))
    }
}

/** Dónde queda el dibujo del auto dentro de su lugar (centrado, con su proporción). */
fun carBox(stage: Rect): Rect {
    val w = minOf(stage.width, stage.height * CAR_RATIO)
    val h = w / CAR_RATIO
    return Rect(stage.center.x - w / 2f, stage.center.y - h / 2f, stage.center.x + w / 2f, stage.center.y + h / 2f)
}

/** Óvalo con degradé radial (luz o sombra suave). */
private fun DrawScope.oval(center: Offset, rx: Float, ry: Float, color: Color) {
    if (rx <= 0f || ry <= 0f) return
    scale(1f, ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, rx), rx, center)
    }
}

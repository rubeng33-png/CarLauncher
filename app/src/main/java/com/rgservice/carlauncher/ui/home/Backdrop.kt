package com.rgservice.carlauncher.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.rgservice.carlauncher.ui.theme.CarTheme
import kotlin.random.Random

/** Proporción del dibujo del auto (ancho / alto). */
const val CAR_RATIO = 316f / 178f

/**
 * Fondo "Ciudad nocturna" detrás del auto (boceto E):
 *  - oscuro: cielo de noche, luna violeta, siluetas de edificios con ventanas encendidas,
 *    luces desenfocadas, horizonte anaranjado y calle mojada con reflejos de colores;
 *  - claro: cielo celeste, sol del atardecer, edificios claros y la calle brillante.
 * Solo dibujo: no cambia ninguna función. La ciudad se calcula una vez por tamaño (drawWithCache).
 *
 * [stage] = lugar del auto dentro de la tarjeta (null = sin auto: solo el cielo y la calle).
 */
@Composable
fun StudioBackdrop(stage: Rect?, modifier: Modifier) {
    val dark = CarTheme.colors.isDark
    Spacer(
        modifier.drawWithCache {
            val city = City.build(size, stage, dark)
            onDrawBehind { drawCity(city, dark) }
        },
    )
}

/* ------------------------------------------------------------------ geometría */

/** Dónde queda el dibujo del auto dentro de su lugar (centrado, con su proporción). */
fun carBox(stage: Rect): Rect {
    val w = minOf(stage.width, stage.height * CAR_RATIO)
    val h = w / CAR_RATIO
    return Rect(stage.center.x - w / 2f, stage.center.y - h / 2f, stage.center.x + w / 2f, stage.center.y + h / 2f)
}

private class Bokeh(val c: Offset, val r: Float, val color: Color, val alpha: Float)

/** Todo lo que se dibuja, calculado una sola vez (posiciones fijas, siempre la misma ciudad). */
private class City(
    val horizon: Float,
    val car: Rect?,
    val buildings: List<Rect>,
    val windows: List<Rect>,
    val bokeh: List<Bokeh>,
    val unit: Float,
) {
    companion object {
        fun build(size: Size, stage: Rect?, dark: Boolean): City {
            val car = stage?.let { carBox(it) }
            // 1 "unidad" = 1 px de la pantalla de 1024x600 (la tarjeta grande mide 564 de alto)
            val u = size.height / 564f
            val horizon = car?.let { it.top + it.height * 0.62f } ?: (size.height * 0.62f)
            val cx = car?.center?.x ?: (size.width / 2f)
            val carW = car?.width ?: (size.width * 0.8f)
            val carH = car?.height ?: (size.height * 0.5f)
            val rnd = Random(11)

            // Edificios: más bajos detrás del centro del auto
            val buildings = ArrayList<Rect>()
            val windows = ArrayList<Rect>()
            var x = -10f * u
            while (x < size.width + 10f * u) {
                val bw = (18 + rnd.nextInt(30)) * u
                var bh = carH * (0.12f + rnd.nextFloat() * 0.38f)
                if (kotlin.math.abs(x - cx) < carW * 0.18f) bh *= 0.6f
                val b = Rect(x, horizon - bh, x + bw, horizon)
                buildings += b
                // Ventanas encendidas (cuadraditos de 2-3 px)
                var wy = b.top + 6 * u
                while (wy < horizon - 4 * u) {
                    var wx = b.left + 4 * u
                    while (wx < b.right - 3 * u) {
                        if (rnd.nextFloat() < 0.28f) windows += Rect(wx, wy, wx + 2.4f * u, wy + 2.4f * u)
                        wx += 6 * u
                    }
                    wy += 7 * u
                }
                x += bw + rnd.nextInt(4) * u
            }

            // Luces desenfocadas cerca del horizonte
            val colors = listOf(Color(0xFFFF7A59), Color(0xFFFFD27A), Color(0xFF3FD0FF), Color(0xFF8C6BFF))
            val bokeh = List(28) { k ->
                Bokeh(
                    Offset(rnd.nextFloat() * size.width, horizon - carH * 0.45f + rnd.nextFloat() * carH * 0.5f),
                    (6 + rnd.nextFloat() * 12) * u,
                    colors[k % 4],
                    (0.25f + rnd.nextFloat() * 0.30f) * (if (dark) 1f else 0.6f),
                )
            }
            return City(horizon, car, buildings, windows, bokeh, u)
        }
    }
}

/* ------------------------------------------------------------------ dibujo */

private fun DrawScope.drawCity(city: City, dark: Boolean) {
    val h = city.horizon
    val hz = (h / size.height).coerceIn(0.2f, 0.95f)
    val u = city.unit
    val car = city.car
    val cx = car?.center?.x ?: (size.width / 2f)
    val carW = car?.width ?: (size.width * 0.8f)
    val carH = car?.height ?: (size.height * 0.5f)
    val glow = if (dark) BlendMode.Screen else BlendMode.SrcOver

    // Cielo y calle
    drawRect(
        Brush.verticalGradient(
            0f to (if (dark) Color(0xFF060914) else Color(0xFFCFE3FF)),
            hz * 0.7f to (if (dark) Color(0xFF14183A) else Color(0xFFE9F1FF)),
            hz to (if (dark) Color(0xFF2A2650) else Color(0xFFFFE9D2)),
            (hz + 0.005f) to (if (dark) Color(0xFF0B0D1C) else Color(0xFFEEF1F6)),
            1f to (if (dark) Color(0xFF03040A) else Color(0xFFD9DFE8)),
        ),
    )

    // Luna (oscuro) o sol del atardecer (claro)
    softCircle(
        Offset(cx + carW * 0.25f, h - carH * 0.32f), carW * 0.30f,
        if (dark) Color(0xFF7A6CFF).copy(alpha = 0.45f) else Color(0xFFFFB46B).copy(alpha = 0.70f), glow,
    )

    // Siluetas de edificios
    val bCol = if (dark) Color(0xFF0A0D1C).copy(alpha = 0.95f) else Color(0xFF9FB3D1).copy(alpha = 0.55f)
    city.buildings.forEach { drawRect(bCol, it.topLeft, it.size) }

    // Ventanas encendidas
    val wCol = if (dark) Color(0xFFFFD27A).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.6f)
    city.windows.forEach { drawRect(wCol, it.topLeft, it.size, blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver) }

    // Luces desenfocadas
    city.bokeh.forEach { softCircle(it.c, it.r, it.color.copy(alpha = it.alpha), glow) }

    // Brillo anaranjado del horizonte
    softOval(Offset(cx, h), size.width * 0.8f, carH * 0.08f,
        (if (dark) Color(0xFFFF8A5B) else Color(0xFFFFB46B)).copy(alpha = if (dark) 0.45f else 0.40f), glow)

    // Calle mojada: reflejos de colores que bajan desde el horizonte
    val floorH = size.height - h
    listOf(
        Triple(-0.38f, 8f, Color(0xFFFFD27A)), Triple(-0.20f, 5f, Color(0xFFFF7A59)),
        Triple(0.05f, 10f, Color(0xFF3FD0FF)), Triple(0.25f, 6f, Color(0xFFFFD27A)),
        Triple(0.40f, 9f, Color(0xFF8C6BFF)),
    ).forEach { (dx, wpx, col) ->
        val x = cx + carW * dx
        val w = wpx * u * 1.6f
        drawRect(
            Brush.verticalGradient(
                listOf(col.copy(alpha = if (dark) 0.50f else 0.32f), Color.Transparent),
                startY = h + 2 * u, endY = h + floorH * 0.85f,
            ),
            topLeft = Offset(x - w / 2f, h + 2 * u), size = Size(w, floorH * 0.85f), blendMode = glow,
        )
    }

    // Sombra debajo del auto
    if (car != null) {
        val wheels = car.top + car.height * 0.90f
        softOval(Offset(cx, wheels), car.width * 0.55f, car.height * 0.09f,
            Color.Black.copy(alpha = if (dark) 0.70f else 0.28f), BlendMode.SrcOver)
    }
}

/** Círculo con degradé radial (luz suave). */
private fun DrawScope.softCircle(center: Offset, r: Float, color: Color, mode: BlendMode) {
    if (r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, r), r, center, blendMode = mode)
}

/** Óvalo con degradé radial (luz o sombra suave). */
private fun DrawScope.softOval(center: Offset, rx: Float, ry: Float, color: Color, mode: BlendMode) {
    if (rx <= 0f || ry <= 0f) return
    scale(1f, ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, rx), rx, center, blendMode = mode)
    }
}

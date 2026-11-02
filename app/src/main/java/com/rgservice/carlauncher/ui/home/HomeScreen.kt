package com.rgservice.carlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgservice.carlauncher.data.CarPhoto
import com.rgservice.carlauncher.data.Gauge
import androidx.compose.ui.graphics.asImageBitmap
import com.rgservice.carlauncher.ui.components.ArcGauge
import com.rgservice.carlauncher.ui.components.cardBrush
import com.rgservice.carlauncher.ui.theme.TileTint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rgservice.carlauncher.R
import com.rgservice.carlauncher.core.HoloVideoView
import com.rgservice.carlauncher.data.HomeState
import com.rgservice.carlauncher.data.apps.Slot
import com.rgservice.carlauncher.ui.pages.RightActions
import com.rgservice.carlauncher.ui.pages.RightCard
import com.rgservice.carlauncher.ui.pages.RightData
import com.rgservice.carlauncher.data.ThemeMode
import com.rgservice.carlauncher.data.Weather
import com.rgservice.carlauncher.ui.components.CarCard
import com.rgservice.carlauncher.ui.components.Dock
import com.rgservice.carlauncher.ui.components.DockItem
import com.rgservice.carlauncher.ui.components.Hairline
import com.rgservice.carlauncher.ui.components.LineIcon
import com.rgservice.carlauncher.ui.components.MaintGauge
import com.rgservice.carlauncher.ui.components.Txt
import com.rgservice.carlauncher.ui.components.fmt1
import com.rgservice.carlauncher.ui.components.fmtKm
import com.rgservice.carlauncher.ui.theme.CarTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Acciones que dispara la pantalla de inicio (las resuelve MainActivity). */
class HomeActions(
    val onCycleTheme: () -> Unit,
    val onSettings: () -> Unit,
    val onApps: () -> Unit,
    val onMaint: () -> Unit,
    val onToggleStats: () -> Unit,
    val onComingSoon: (String) -> Unit,
    val onGauge: (String) -> Unit,
    val onOdometer: () -> Unit,
    val onName: () -> Unit,
    val onLimit: () -> Unit,
    val onTrip: () -> Unit,
    val onSlot: (Slot) -> Unit,
    val onSlotLong: (Slot) -> Unit,
    val onBluetooth: () -> Unit,
    val onCarImage: () -> Unit = {},
)

/**
 * Pantalla de inicio (diseño "tarjeta grande + columna de accesos").
 *  - Izquierda: hora y fecha, saludo y clima, modo noche, el auto con el mantenimiento al costado,
 *    velocidad y barra de velocidad con los datos del viaje.
 *  - Derecha: app de mapas, accesos rápidos (Música, Radio, Teléfono, Bluetooth), todas las apps
 *    y ajustes, y la tarjeta Música / Mapa / Accesos.
 * Se adapta al alto disponible (Normal / Grande / Muy grande).
 */
@Composable
fun HomeScreen(state: HomeState, right: RightData, rightActions: RightActions, actions: HomeActions) {
    val c = CarTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .background(Brush.radialGradient(listOf(c.glow, Color.Transparent), radius = 1500f)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
            val compact = maxHeight < 620.dp          // Grande
            val veryCompact = maxHeight < 560.dp      // Muy grande
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroCard(state, actions, compact, veryCompact, Modifier.weight(61f).fillMaxHeight())
                RightColumn(state, right, rightActions, actions, compact, veryCompact, Modifier.weight(39f).fillMaxHeight())
            }
        }
    }
}

/* ------------------------------------------------------------------ Tarjeta grande (izquierda) */

@Composable
private fun HeroCard(s: HomeState, actions: HomeActions, compact: Boolean, veryCompact: Boolean, modifier: Modifier) {
    // Lugar del auto dentro de la tarjeta, para dibujar el fondo de estudio detrás
    var stage by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    CarCard(modifier, radius = 30.dp, padding = PaddingValues(0.dp), brush = cardBrush()) {
        StudioBackdrop(
            if (veryCompact) null else stage?.translate(-origin),
            Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot() },
        )
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            // Arriba: hora y fecha a la izquierda; saludo, clima y modo noche a la derecha
            Row(Modifier.fillMaxWidth()) {
                Clock(onLongPress = actions.onToggleStats, size = if (compact) 64 else 78, modifier = Modifier.weight(1f))
                Greeting(s, actions, compact)
            }
            // Centro: el auto grande, a todo el ancho (como en el diseño).
            // En "Muy grande" no entra el auto: ahí va el mantenimiento.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 6.dp else 10.dp)
                    .onGloballyPositioned { stage = it.boundsInRoot() },
                contentAlignment = Alignment.Center,
            ) {
                if (veryCompact) {
                    MaintPanel(s, actions, compact, Modifier.fillMaxSize())
                } else {
                    CarStage()
                    // Mantener apretado el auto: elegir la imagen (foto de la galería o modelo 3D)
                    Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onLongPress = { actions.onCarImage() }) })
                }
            }
            // Abajo: velocidad y barra de velocidad con el viaje
            Row(Modifier.fillMaxWidth().height(if (compact) 104.dp else 118.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpeedTile(s, actions, Modifier.width(if (compact) 206.dp else 232.dp).fillMaxHeight())
                SpeedBar(s, actions, compact, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Fondo de las "piezas" dentro de la tarjeta grande. */
@Composable
private fun tileBg(): Color = CarTheme.colors.let { if (it.isDark) Color(0x590A1220) else Color(0xFFF1F4F8) }

@Composable
private fun Greeting(s: HomeState, actions: HomeActions, compact: Boolean) {
    val c = CarTheme.colors
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 330.dp)) {
        Txt(greeting().uppercase(Locale("es")), 14, c.muted, FontWeight.Medium, letterSpacing = 3f)
        Txt(
            s.name.uppercase(Locale("es")), if (compact) 30 else 36, weight = FontWeight.SemiBold, letterSpacing = 1.5f,
            modifier = Modifier.clickable(onClick = actions.onName),
        )
        s.weather?.let { w ->
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(22.dp).height(2.dp).background(c.accent))
                LineIcon(weatherIcon(w.code, s.dark), 18.dp, c.accent, Modifier.padding(start = 8.dp))
                Txt(
                    if (w.updatedAgo != null) "${w.tempC}° · ${w.description} · ${w.updatedAgo}" else "${w.tempC}° · ${w.description}",
                    15, c.muted, modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (!compact) Txt(
                "Máx ${w.maxC}° · Mín ${w.minC}° · ${w.humidity}% · ${w.windKmh} km/h", 13, c.muted, tabular = true,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ThemeChip(s, actions.onCycleTheme, Modifier.padding(top = 8.dp))
    }
}

/** Botón de modo noche (en el diseño: "Luces encendidas"). */
@Composable
private fun ThemeChip(s: HomeState, onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.pill)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(if (s.theme == ThemeMode.Day) R.drawable.ic_sun else R.drawable.ic_moon, 18.dp, c.accent)
        Txt(
            when (s.theme) { ThemeMode.Auto -> "Noche auto"; ThemeMode.Night -> "Noche"; ThemeMode.Day -> "Día" },
            15, c.text, FontWeight.Medium, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** El auto sobre un "piso" iluminado, como en el diseño. */
@Composable
private fun CarStage() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * CAR_RATIO)
        CarPicture(Modifier.width(w).aspectRatio(CAR_RATIO), reflectionGap = w / CAR_RATIO)
    }
}

/**
 * Imagen del auto: con foto propia se muestra la foto (de día y de noche).
 * Sin foto: holograma en video en tema oscuro (lo decodifica el hardware) e imagen fija en tema claro.
 */
@Composable
private fun CarPicture(modifier: Modifier, reflectionGap: Dp) {
    val c = CarTheme.colors
    val photo by CarPhoto.photo.collectAsStateWithLifecycle()
    val bmp = photo
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Box(contentAlignment = Alignment.Center) {
            Reflection(reflectionGap, modifier) { m -> Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = m) }
            Image(img, contentDescription = "Tu auto", contentScale = ContentScale.Fit, modifier = modifier)
        }
    } else if (c.isDark) {
        AndroidView(
            factory = { ctx -> HoloVideoView(ctx).apply { play(0, R.raw.holo_home) } },
            modifier = modifier,
            onRelease = { it.stop() },
        )
    } else {
        val painter = painterResource(R.drawable.auto3d)
        Box(contentAlignment = Alignment.Center) {
            Reflection(reflectionGap, modifier) { m ->
                Image(painter, contentDescription = null, contentScale = ContentScale.Fit, modifier = m.padding(horizontal = 8.dp, vertical = 10.dp))
            }
            Image(
                painter = painter,
                contentDescription = "Tu auto",
                contentScale = ContentScale.Fit,
                modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/** Reflejo del auto en el piso brillante: la imagen dada vuelta, que se desvanece hacia abajo. */
@Composable
private fun Reflection(height: Dp, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    val c = CarTheme.colors
    content(
        modifier
            .offset(y = height * 0.86f)
            .graphicsLayer {
                scaleY = -1f
                alpha = if (c.isDark) 0.30f else 0.22f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Dibujado antes de dar vuelta: abajo (las ruedas) visible, arriba transparente
                drawRect(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/* ------------------------------------------------------------------ Mantenimiento (al costado del auto) */

@Composable
private fun MaintPanel(s: HomeState, actions: HomeActions, compact: Boolean, modifier: Modifier) {
    val c = CarTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(tileBg()).border(1.dp, c.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Txt("Mantenimiento", 15, c.muted, FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        Txt(
            if (s.odoKm != null) "Tablero ${s.odoKm.fmtKm()} km" else "Cargar km del tablero", 14,
            if (s.odoKm != null) c.muted else c.accent, tabular = true,
            modifier = Modifier.clickable(onClick = actions.onOdometer).padding(bottom = 4.dp),
        )
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
            MaintRow(s.oil, if (compact) 78.dp else 92.dp) { actions.onGauge(s.oil.id) }
            Hairline(Modifier.fillMaxWidth().height(1.dp))
            MaintRow(s.gear, if (compact) 78.dp else 92.dp) { actions.onGauge(s.gear.id) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = actions.onMaint).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            val col = if (s.maintWarnings > 0) c.warn else c.accent
            Txt(if (s.maintWarnings > 0) "Ver todo · ${s.maintWarnings} ⚠" else "Ver todo", 15, col, FontWeight.SemiBold)
            LineIcon(R.drawable.ic_chevron, 16.dp, col, Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun MaintRow(g: Gauge, ring: Dp, small: Boolean = false, onClick: () -> Unit) {
    val c = CarTheme.colors
    val color = if (g.warn) c.warn else c.accent
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        ArcGauge(fraction = g.fraction, color = color, size = ring, warn = g.warn) {
            Txt(
                if (g.configured) g.remainingKm.fmtKm() else "—",
                size = (ring.value * 0.2f).toInt().coerceAtLeast(14),
                color = if (g.warn) c.warn else c.text,
                weight = FontWeight.SemiBold,
                tabular = true,
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Txt(g.name, if (small) 16 else 17, weight = FontWeight.SemiBold)
            if (!small) Txt(if (g.configured) "km restantes" else "", 13, c.muted)
            val sub = when {
                !g.configured -> "Tocá para configurar"
                g.remainingKm == 0 -> "¡Cambiar ahora!"
                g.warn -> "Cambiar pronto"
                g.nextAtKm != null -> "Próx.: ${g.nextAtKm.fmtKm()} km"
                else -> "Cada ${g.intervalKm.fmtKm()} km"
            }
            Txt(sub, 13, if (g.warn) c.warn else c.muted, tabular = true)
        }
    }
}

/* ------------------------------------------------------------------ Velocidad y viaje (abajo) */

@Composable
private fun SpeedTile(s: HomeState, actions: HomeActions, modifier: Modifier) {
    val c = CarTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(tileBg()).border(1.dp, c.line, RoundedCornerShape(22.dp))
            .clickable(onClick = actions.onLimit).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(s.speedKmh?.toString() ?: "--", 58, if (s.overLimit) c.warn else c.text, FontWeight.SemiBold, tabular = true, lineHeight = 1f)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Txt("km/h", 17, c.muted)
            GpsLabel(s.gpsOk)
        }
        if (s.speedLimit > 0) {
            Box(
                Modifier.size(46.dp).border(3.dp, if (s.overLimit) c.warn else c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Txt(s.speedLimit.toString(), 17, if (s.overLimit) c.warn else c.muted, FontWeight.Bold, tabular = true)
            }
        }
    }
}

/** "GPS" con el punto que late cuando hay señal; "Sin GPS" bajo techo. */
@Composable
private fun GpsLabel(ok: Boolean) {
    val c = CarTheme.colors
    val t = rememberInfiniteTransition(label = "gps")
    val a by t.animateFloat(0.5f, 0f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "ripple")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            if (ok) Box(Modifier.size(16.dp).alpha(a).background(c.accent, CircleShape))
            Box(Modifier.size(7.dp).alpha(if (ok) 1f else 0.4f).background(c.accent, CircleShape))
        }
        Txt(if (ok) "GPS" else "Sin GPS", 14, if (ok) c.accent else c.muted, FontWeight.Medium, modifier = Modifier.padding(start = 3.dp))
    }
}

private const val BAR_MAX = 160f

@Composable
private fun SpeedBar(s: HomeState, actions: HomeActions, compact: Boolean, modifier: Modifier) {
    val c = CarTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(tileBg()).border(1.dp, c.line, RoundedCornerShape(22.dp))
            .clickable(onClick = actions.onTrip).padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            val frac = ((s.speedKmh ?: 0) / BAR_MAX).coerceIn(0f, 1f)
            val anim by androidx.compose.animation.core.animateFloatAsState(frac, tween(600), label = "bar")
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.track))
                    Box(
                        Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.55f), if (s.overLimit) c.warn else c.accent))),
                    )
                    if (s.speedLimit > 0) {
                        val lf = (s.speedLimit / BAR_MAX).coerceIn(0f, 1f)
                        Box(Modifier.padding(start = (w * lf - 1.5.dp).coerceAtLeast(0.dp)).width(3.dp).fillMaxHeight().background(c.warn))
                    }
                }
                // Escala 0 · 60 · 100 · 160
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(18.dp)) {
                    listOf(0, 60, 100, 160).forEach { v ->
                        val x = w * (v / BAR_MAX)
                        val off = when (v) { 0 -> 0.dp; 160 -> (x - 26.dp); else -> (x - 12.dp) }
                        Txt(v.toString(), 13, c.muted, tabular = true, modifier = Modifier.padding(start = off.coerceAtLeast(0.dp)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("${s.trip.km.fmt1()} km", "Viaje", Modifier.weight(1f))
            Stat(minutesText(s.trip.minutes), "Tiempo", Modifier.weight(1f))
            Stat("${s.trip.avgKmh} km/h", "Promedio", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Txt(value, 19, weight = FontWeight.SemiBold, tabular = true)
        Txt(label, 13, CarTheme.colors.muted)
    }
}

@Composable
private fun Clock(onLongPress: () -> Unit, size: Int, modifier: Modifier) {
    val c = CarTheme.colors
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    val t = rememberInfiniteTransition(label = "colon")
    val colon by t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "c")
    val hh = SimpleDateFormat("HH", Locale.US).format(now)
    val mm = SimpleDateFormat("mm", Locale.US).format(now)
    val date = SimpleDateFormat("EEEE · d MMM", Locale("es")).format(now).replace(".", "").uppercase(Locale("es"))
    Column(modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(hh, size, weight = FontWeight.SemiBold, tabular = true, lineHeight = 1f)
            Txt(":", size, weight = FontWeight.SemiBold, modifier = Modifier.alpha(colon).padding(horizontal = 2.dp).padding(bottom = (size / 9).dp), lineHeight = 1f)
            Txt(mm, size, weight = FontWeight.SemiBold, tabular = true, lineHeight = 1f)
        }
        Txt(date, 16, c.muted, FontWeight.Medium, letterSpacing = 1.5f)
    }
}

/* ------------------------------------------------------------------ Columna derecha */

@Composable
private fun RightColumn(
    s: HomeState, right: RightData, rightActions: RightActions, actions: HomeActions,
    compact: Boolean, veryCompact: Boolean, modifier: Modifier,
) {
    val slots by right.apps.slots.collectAsStateWithLifecycle()
    val gap = if (compact) 12.dp else 14.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        NavAppCard(slots[Slot.Nav], right, actions, Modifier.fillMaxWidth().height(if (veryCompact) 78.dp else if (compact) 86.dp else 96.dp))
        Row(
            Modifier.fillMaxWidth().height(if (veryCompact) 92.dp else if (compact) 100.dp else 112.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickTile(R.drawable.ic_music, Slot.Music.label, TileTint.music, Modifier.weight(1f),
                onLong = { actions.onSlotLong(Slot.Music) }) { actions.onSlot(Slot.Music) }
            QuickTile(R.drawable.ic_radio, Slot.Radio.label, TileTint.radio, Modifier.weight(1f),
                onLong = { actions.onSlotLong(Slot.Radio) }) { actions.onSlot(Slot.Radio) }
            QuickTile(R.drawable.ic_phone, Slot.Phone.label, TileTint.phone, Modifier.weight(1f),
                onLong = { actions.onSlotLong(Slot.Phone) }) { actions.onSlot(Slot.Phone) }
            QuickTile(R.drawable.ic_bt, "Bluetooth", TileTint.bluetooth, Modifier.weight(1f), onLong = null, onClick = actions.onBluetooth)
        }
        Row(
            Modifier.fillMaxWidth().height(if (veryCompact) 52.dp else 60.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AllAppsRow(actions.onApps, Modifier.weight(1f).fillMaxHeight())
            SquareButton(R.drawable.ic_settings, "Ajustes", actions.onSettings, Modifier.aspectRatio(1f).fillMaxHeight())
        }
        if (!veryCompact) MaintCard(s, actions, compact, Modifier.fillMaxWidth().height(if (compact) 104.dp else 118.dp))
        RightCard(right, rightActions, small = compact, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Mantenimiento en la columna derecha: km del tablero, los dos medidores y "Ver todo". */
@Composable
private fun MaintCard(s: HomeState, actions: HomeActions, compact: Boolean, modifier: Modifier) {
    val c = CarTheme.colors
    CarCard(modifier, radius = 24.dp, padding = PaddingValues(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), brush = cardBrush()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    if (s.odoKm != null) "Mantenimiento · ${s.odoKm.fmtKm()} km" else "Mantenimiento · cargar km",
                    15, if (s.odoKm != null) c.muted else c.accent, FontWeight.Medium, tabular = true,
                    modifier = Modifier.weight(1f).clickable(onClick = actions.onOdometer).padding(vertical = 4.dp),
                )
                val col = if (s.maintWarnings > 0) c.warn else c.accent
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = actions.onMaint).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(if (s.maintWarnings > 0) "Ver todo · ${s.maintWarnings} ⚠" else "Ver todo", 15, col, FontWeight.SemiBold)
                    LineIcon(R.drawable.ic_chevron, 16.dp, col)
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MaintRow(s.oil, if (compact) 64.dp else 74.dp, small = true) { actions.onGauge(s.oil.id) } }
                Hairline(Modifier.width(1.dp).fillMaxHeight(0.7f))
                Box(Modifier.weight(1f).padding(start = 10.dp)) { MaintRow(s.gear, if (compact) 64.dp else 74.dp, small = true) { actions.onGauge(s.gear.id) } }
            }
        }
    }
}

/** Tarjeta de la app de mapas elegida (en el diseño: "ZLink5"). Tocar abre; mantener cambia la app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavAppCard(pkg: String?, right: RightData, actions: HomeActions, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val installed = pkg != null && right.apps.installed(pkg)
    val icon = remember(pkg, installed) { if (installed) right.apps.icon(pkg!!) else null }
    val label = remember(pkg, installed) { if (installed) right.apps.label(pkg!!) else Slot.Nav.label }
    Row(
        modifier.clip(shape).background(cardBrush(), shape).border(1.dp, c.line, shape)
            .combinedClickable(onClick = { actions.onSlot(Slot.Nav) }, onLongClick = { actions.onSlotLong(Slot.Nav) })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight(0.66f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.68f))
            else LineIcon(R.drawable.ic_nav, 28.dp, c.onAccent)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Txt(label, 23, weight = FontWeight.Bold)
            Txt(if (installed) "Mapas · navegación" else "Tocá para elegir la app de mapas", 15, c.muted)
        }
        LineIcon(R.drawable.ic_chevron, 22.dp, c.muted)
    }
}

/** Acceso rápido cuadrado con ícono en un círculo de color (BT Music / Radio / … en el diseño). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickTile(icon: Int, label: String, tint: Color, modifier: Modifier, onLong: (() -> Unit)?, onClick: () -> Unit) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(cardBrush(), shape).border(1.dp, c.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.fillMaxHeight(0.52f).aspectRatio(1f).clip(CircleShape).background(tint.copy(alpha = if (c.isDark) 0.18f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(icon, 24.dp, tint) }
        Txt(label, 15, c.text, FontWeight.Medium, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp))
    }
}

@Composable
private fun AllAppsRow(onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.clip(shape).background(cardBrush(), shape).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(R.drawable.ic_apps, 22.dp, c.accent)
        Txt("Todas las apps", 18, c.text, FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
        LineIcon(R.drawable.ic_chevron, 20.dp, c.muted)
    }
}

@Composable
private fun SquareButton(icon: Int, desc: String, onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(cardBrush(), shape).border(1.dp, c.line, shape)
            .semantics { contentDescription = desc }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, 24.dp, c.text) }
}

/* ------------------------------------------------------------------ utilidades */

private fun weatherIcon(code: Int, night: Boolean) = when {
    code == 0 -> if (night) R.drawable.ic_moon else R.drawable.ic_sun
    code <= 2 -> R.drawable.ic_cloud_sun
    code == 3 -> R.drawable.ic_cloud
    code == 45 || code == 48 -> R.drawable.ic_fog
    code in 51..67 || code in 80..82 -> R.drawable.ic_rain
    code in 71..77 -> R.drawable.ic_snow
    code >= 95 -> R.drawable.ic_storm
    else -> R.drawable.ic_cloud_sun
}

private fun greeting(): String {
    val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when { hr < 12 -> "Buenos días"; hr < 19 -> "Buenas tardes"; else -> "Buenas noches" }
}

private fun minutesText(m: Int) = if (m >= 60) "${m / 60} h ${String.format(Locale.US, "%02d", m % 60)}" else "$m min"

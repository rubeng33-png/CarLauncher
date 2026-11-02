package com.rgservice.carlauncher.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgservice.carlauncher.R
import com.rgservice.carlauncher.core.HoloVideoView
import com.rgservice.carlauncher.data.HomeState
import com.rgservice.carlauncher.data.ThemeMode
import com.rgservice.carlauncher.data.Weather
import com.rgservice.carlauncher.data.apps.Slot
import com.rgservice.carlauncher.data.car.CarLook
import com.rgservice.carlauncher.ui.components.LineIcon
import com.rgservice.carlauncher.ui.components.Txt
import com.rgservice.carlauncher.ui.components.fmt1
import com.rgservice.carlauncher.ui.components.fmtKm
import com.rgservice.carlauncher.ui.pages.MapPage
import com.rgservice.carlauncher.ui.pages.MiniPlayer
import com.rgservice.carlauncher.ui.pages.NavBanner
import com.rgservice.carlauncher.ui.pages.RightActions
import com.rgservice.carlauncher.ui.pages.RightData
import com.rgservice.carlauncher.ui.theme.CarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** Mantener apretado el auto: elegir la foto (Ajustes › Mi auto). */
    val onCarImage: () -> Unit = {},
)

/**
 * Pantalla de inicio "luces encendidas / apagadas" (guía de rediseño):
 * un panel grande sobre un fondo degradado, con la zona héroe (~70 %) y la columna derecha (~30 %).
 *  - Héroe: hora y fecha (+ clima), marca/modelo/año, luces (= modo noche), mantenimiento,
 *    el auto (o el mapa, deslizando) y abajo velocidad + barra + viaje.
 *  - Columna: app principal, BT Music / Radio / Ecualizador, Todas las apps + Ajustes y el mini reproductor.
 * Tamaños Grande y Muy grande: sin reflejo ni chip de clima, auto más chico.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    right: RightData,
    rightActions: RightActions,
    actions: HomeActions,
    car: CarLook = CarLook(),
) {
    val c = CarTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.bgEdge)
            .drawBehind {
                // Degradado radial: centro detrás del auto
                drawRect(
                    Brush.radialGradient(
                        listOf(c.bgCenter, c.bgEdge),
                        center = Offset(size.width * 0.36f, size.height * 0.58f),
                        radius = size.width * 0.62f,
                    ),
                )
            }
            .padding(14.dp),
    ) {
        val panelShape = RoundedCornerShape(28.dp)
        Box(
            Modifier.fillMaxSize().clip(panelShape).background(c.panel, panelShape)
                .border(1.dp, c.panelLine, panelShape).padding(22.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxHeight < 600.dp          // Grande
                val veryCompact = maxHeight < 540.dp      // Muy grande
                val colW = maxOf(300.dp, maxWidth * 0.30f)
                Row(Modifier.fillMaxSize()) {
                    Hero(state, right, rightActions, actions, car, compact, veryCompact, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(16.dp))
                    RightColumn(state, right, rightActions, actions, compact, veryCompact, Modifier.width(colW).fillMaxHeight())
                }
            }
        }
    }
}

/* ================================================================== Zona héroe */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(
    s: HomeState, right: RightData, rightActions: RightActions, actions: HomeActions, car: CarLook,
    compact: Boolean, veryCompact: Boolean, modifier: Modifier,
) {
    val c = CarTheme.colors
    Column(modifier) {
        // ---- Arriba: hora y fecha (izquierda); auto, luces y mantenimiento (derecha)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Clock(actions.onToggleStats, if (veryCompact) 62 else if (compact) 72 else 88)
                if (!compact) s.weather?.let { WeatherChip(it, s.dark, Modifier.padding(top = 10.dp)) }
            }
            CarInfo(s, car, actions, compact)
        }

        // ---- Centro: el auto o el mapa (deslizar); banner de navegación arriba en cualquier página
        val pager = rememberPagerState(initialPage = if (right.initialPage == 1) 1 else 0) { 2 }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager.currentPage) { rightActions.onPage(pager.currentPage) }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 6.dp else 10.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) CarStage(car, s.dark, reflection = !compact, actions)
                else Box(Modifier.fillMaxSize().padding(top = 4.dp)) { MapPage(right, rightActions) }
            }
            right.nav?.let { n ->
                NavBanner(n, Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp)) { rightActions.onNavBanner(n) }
            }
        }
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            for (i in 0..1) {
                val active = i == pager.currentPage
                val w by animateDpAsState(if (active) 20.dp else 7.dp, label = "dotw")
                val col by animateColorAsState(if (active) c.accent else c.tick, label = "dotc")
                Box(
                    Modifier.size(30.dp, 14.dp).clickable { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(w, 7.dp).clip(CircleShape).background(col)) }
            }
        }

        // ---- Abajo: velocidad (izquierda) y barra de velocidad + viaje (derecha)
        Row(
            Modifier.fillMaxWidth().height(if (compact) 88.dp else 104.dp).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedBlock(s, actions, compact, Modifier.fillMaxHeight())
            SpeedBar(s, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun Clock(onLongPress: () -> Unit, size: Int) {
    val c = CarTheme.colors
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    val t = rememberInfiniteTransition(label = "colon")
    val colon by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "c")
    val hh = SimpleDateFormat("HH", Locale.US).format(now)
    val mm = SimpleDateFormat("mm", Locale.US).format(now)
    val date = SimpleDateFormat("EEEE · d MMM", Locale("es")).format(now).replace(".", "").uppercase(Locale("es"))
    Column(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(hh, size, weight = FontWeight.Medium, tabular = true, lineHeight = 1f)
            Txt(":", size, weight = FontWeight.Medium, lineHeight = 1f,
                modifier = Modifier.alpha(colon).padding(horizontal = 2.dp).padding(bottom = (size / 9).dp))
            Txt(mm, size, weight = FontWeight.Medium, tabular = true, lineHeight = 1f)
        }
        Txt(date, 18, c.accent, FontWeight.SemiBold, letterSpacing = 1.5f, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Chip de clima debajo de la fecha. */
@Composable
private fun WeatherChip(w: Weather, dark: Boolean, modifier: Modifier) {
    val c = CarTheme.colors
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.pill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(weatherIcon(w.code, dark), 18.dp, c.accent)
        Txt(
            if (w.updatedAgo != null) "${w.tempC}° ${w.description} · ${w.updatedAgo}" else "${w.tempC}° ${w.description}",
            15, c.text, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Marca / modelo / año, luces (= modo noche, tocar cambia) y la píldora de mantenimiento. */
@Composable
private fun CarInfo(s: HomeState, car: CarLook, actions: HomeActions, compact: Boolean) {
    val c = CarTheme.colors
    val p = car.profile
    val es = Locale("es")
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 360.dp)) {
        if (p.brand.isNotBlank()) Txt(p.brand.uppercase(es), 12, c.muted, FontWeight.Medium, letterSpacing = 4f)
        if (p.model.isNotBlank()) Txt(p.model.uppercase(es), if (compact) 24 else 28, weight = FontWeight.SemiBold, letterSpacing = 3f)
        if (p.year.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.width(18.dp).height(2.dp).background(c.accent))
            Txt(p.year, 13, c.muted, tabular = true, modifier = Modifier.padding(start = 6.dp))
        }
        Row(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = actions.onCycleTheme)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(R.drawable.ic_headlight, 18.dp, if (s.dark) c.tileEq else c.muted)
            Txt(
                (if (s.dark) "Luces encendidas" else "Luces apagadas") + if (s.theme != ThemeMode.Auto) " · fijo" else "",
                13, c.muted, modifier = Modifier.padding(start = 6.dp),
            )
        }
        MaintPill(s, actions.onMaint, Modifier.padding(top = 6.dp))
    }
}

/** "Aceite: faltan 1.200 km" (+ "· 2 ⚠" si hay avisos). Tocar abre Mantenimiento. */
@Composable
private fun MaintPill(s: HomeState, onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    val warn = s.maintWarnings > 0
    val col = if (warn) c.warn else c.text
    val text = when {
        !s.oil.configured -> "Mantenimiento: tocá para configurar"
        s.oil.remainingKm == 0 -> "Aceite: ¡cambiar ahora!"
        else -> "Aceite: faltan ${s.oil.remainingKm.fmtKm()} km"
    } + if (warn) " · ${s.maintWarnings} ⚠" else ""
    Row(
        modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (warn) c.warn.copy(alpha = 0.14f) else c.pill)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(R.drawable.ic_wrench, 16.dp, if (warn) c.warn else c.accent)
        Txt(text, 14, col, FontWeight.Medium, tabular = true, modifier = Modifier.padding(start = 6.dp))
    }
}

/* ------------------------------------------------------------------ El auto */

/**
 * El auto centrado y grande. Tocar = Mantenimiento (visor holográfico); mantener = elegir la foto.
 * Oscuro: brillo azul en el piso y reflejo suave (solo tamaño Normal). Claro: sombra suave.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarStage(car: CarLook, dark: Boolean, reflection: Boolean, actions: HomeActions) {
    val c = CarTheme.colors
    val factory = ImageBitmap.imageResource(R.drawable.car_factory)
    val holo = dark && car.profile.useHologram
    val img = if (dark) car.night ?: car.day ?: factory else car.day ?: factory
    BoxWithConstraints(
        Modifier.fillMaxSize().combinedClickable(onClick = actions.onMaint, onLongClick = actions.onCarImage),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = if (holo) 16f / 9f else img.width.toFloat() / img.height.coerceAtLeast(1)
        val w = minOf(maxWidth, maxHeight * 0.80f * aspect)
        val h = w / aspect
        // Brillo azul (oscuro) o sombra suave (claro) debajo de las ruedas
        Box(
            Modifier.size(w * 1.25f, h * 0.5f).offset(y = h * 0.42f).drawBehind {
                softOval(
                    center, size.width / 2f, size.height / 2f,
                    if (dark) c.accent.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.16f),
                    if (dark) BlendMode.Screen else BlendMode.SrcOver,
                )
            },
        )
        if (holo) {
            AndroidView(
                factory = { ctx -> HoloVideoView(ctx).apply { blendWithBackground(); play(0, R.raw.holo_home) } },
                modifier = Modifier.size(w, h),
                onRelease = { it.stop() },
            )
        } else {
            val fade = remember(img) { !hasTransparency(img) }   // foto con fondo: se funde en los bordes
            if (dark && reflection) {
                Image(
                    img, contentDescription = null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(w, h).graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = -0.45f
                        alpha = 0.25f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }.drawWithContent {
                        drawContent()
                        if (fade) edgeMask()
                        drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black), blendMode = BlendMode.DstIn)
                    },
                )
            }
            Image(
                img, contentDescription = "Tu auto", contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(w, h).then(
                    if (fade) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent { drawContent(); edgeMask() }
                    else Modifier,
                ),
            )
        }
    }
}

/** ¿La imagen tiene fondo transparente? (PNG recortado). Mira las cuatro esquinas. */
private fun hasTransparency(img: ImageBitmap): Boolean = try {
    val b = img.asAndroidBitmap()
    b.hasAlpha() && listOf(0 to 0, b.width - 1 to 0, 0 to b.height - 1, b.width - 1 to b.height - 1)
        .any { (x, y) -> (b.getPixel(x, y) ushr 24) < 200 }
} catch (e: Exception) { false }

/** Máscara ovalada que desvanece los bordes de una foto con fondo, para que se funda con el panel. */
private fun DrawScope.edgeMask() {
    val rx = size.width / 2f
    scale(1f, size.height / size.width, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to Color.Black, 0.62f to Color.Black, 1f to Color.Transparent, center = center, radius = rx * 1.02f),
            radius = rx * 1.02f, center = center, blendMode = BlendMode.DstIn,
        )
    }
}

/** Óvalo con degradé radial (luz o sombra suave). */
private fun DrawScope.softOval(center: Offset, rx: Float, ry: Float, color: Color, mode: BlendMode) {
    if (rx <= 0f || ry <= 0f) return
    scale(1f, ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, rx), rx, center, blendMode = mode)
    }
}

/* ------------------------------------------------------------------ Velocidad y viaje */

@Composable
private fun SpeedBlock(s: HomeState, actions: HomeActions, compact: Boolean, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.clip(shape)
            .background(if (c.isDark) c.card.copy(alpha = 0.55f) else Color.White, shape)
            .border(1.dp, c.line, shape)
            .clickable(onClick = actions.onLimit).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            s.speedKmh?.toString() ?: "--", if (compact) 64 else 76,
            if (s.overLimit) c.warn else c.accent, FontWeight.SemiBold, tabular = true, lineHeight = 1f,
        )
        Column(Modifier.padding(start = 8.dp)) {
            Txt("km/h", 17, c.text, FontWeight.Medium)
            GpsLabel(s.gpsOk)
        }
        if (s.speedLimit > 0) {
            Box(
                Modifier.padding(start = 12.dp).size(40.dp).border(3.dp, if (s.overLimit) c.warn else c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(s.speedLimit.toString(), 15, if (s.overLimit) c.warn else c.muted, FontWeight.Bold, tabular = true) }
        }
    }
}

/** "GPS" en acento con el punto que late si hay señal; "Sin GPS" apagado si no. */
@Composable
private fun GpsLabel(ok: Boolean) {
    val c = CarTheme.colors
    val t = rememberInfiniteTransition(label = "gps")
    val a by t.animateFloat(0.5f, 0f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "ripple")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            if (ok) Box(Modifier.size(14.dp).alpha(a).background(c.accent, CircleShape))
            Box(Modifier.size(6.dp).alpha(if (ok) 1f else 0.4f).background(c.accent, CircleShape))
        }
        Txt(if (ok) "GPS" else "Sin GPS", 13, if (ok) c.accent else c.muted, FontWeight.Medium, modifier = Modifier.padding(start = 3.dp))
    }
}

private const val BAR_MAX = 160f

/** Barra horizontal 0 · 60 · 100 · 160 (naranja si pasa el aviso) y debajo la línea del viaje. */
@Composable
private fun SpeedBar(s: HomeState, actions: HomeActions, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape)
            .background(if (c.isDark) c.card.copy(alpha = 0.55f) else Color.White, shape)
            .border(1.dp, c.line, shape)
            .clickable(onClick = actions.onTrip).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            val frac = ((s.speedKmh ?: 0) / BAR_MAX).coerceIn(0f, 1f)
            val anim by animateFloatAsState(frac, tween(600), label = "bar")
            val fill = if (s.overLimit) c.warn else c.accent
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c.track))
                    Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(fill))
                    // marcas de la escala
                    listOf(60, 100).forEach { v ->
                        Box(Modifier.padding(start = w * (v / BAR_MAX)).width(1.5.dp).fillMaxHeight().background(c.tick))
                    }
                    if (s.speedLimit > 0) {
                        val lf = (s.speedLimit / BAR_MAX).coerceIn(0f, 1f)
                        Box(Modifier.padding(start = (w * lf - 1.5.dp).coerceAtLeast(0.dp)).width(3.dp).fillMaxHeight().background(c.warn))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(17.dp)) {
                    listOf(0, 60, 100, 160).forEach { v ->
                        val x = w * (v / BAR_MAX)
                        val off = when (v) { 0 -> 0.dp; 160 -> (x - 26.dp); else -> (x - 11.dp) }
                        Txt(v.toString(), 13, c.muted, tabular = true, modifier = Modifier.padding(start = off.coerceAtLeast(0.dp)))
                    }
                }
            }
        }
        Txt(
            "Viaje ${s.trip.km.fmt1()} km · ${minutesText(s.trip.minutes)} · ${s.trip.avgKmh} km/h",
            14, c.muted, tabular = true,
        )
    }
}

/* ================================================================== Columna derecha */

@Composable
private fun RightColumn(
    s: HomeState, right: RightData, rightActions: RightActions, actions: HomeActions,
    compact: Boolean, veryCompact: Boolean, modifier: Modifier,
) {
    val c = CarTheme.colors
    val slots by right.apps.slots.collectAsStateWithLifecycle()
    val gap = if (compact) 10.dp else 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        MainAppCard(slots[Slot.Main], right, actions, Modifier.fillMaxWidth().height(if (veryCompact) 80.dp else if (compact) 92.dp else 110.dp))
        Row(Modifier.fillMaxWidth().height(if (veryCompact) 86.dp else if (compact) 98.dp else 112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_music, "BT Music", c.tileMusic, Modifier.weight(1f), { actions.onSlotLong(Slot.Music) }) { actions.onSlot(Slot.Music) }
            Tile(R.drawable.ic_radio, "Radio", c.tileRadio, Modifier.weight(1f), { actions.onSlotLong(Slot.Radio) }) { actions.onSlot(Slot.Radio) }
            Tile(R.drawable.ic_eq, "Ecualizador", c.tileEq, Modifier.weight(1f), { actions.onSlotLong(Slot.Eq) }) { actions.onSlot(Slot.Eq) }
        }
        Row(Modifier.fillMaxWidth().height(if (veryCompact) 50.dp else 58.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AllAppsRow(actions.onApps, Modifier.weight(1f).fillMaxHeight())
            GearButton(actions.onSettings, Modifier.aspectRatio(1f).fillMaxHeight())
        }
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(shape).background(c.card, shape).border(1.dp, c.line, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { MiniPlayer(right.media, rightActions, Modifier.fillMaxSize()) }
    }
}

/** Tarjeta de la app principal (en la imagen: ZLink5). Oscura también en el tema claro. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainAppCard(pkg: String?, right: RightData, actions: HomeActions, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val installed = pkg != null && right.apps.installed(pkg)
    val icon = remember(pkg, installed) { if (installed) right.apps.icon(pkg!!) else null }
    val label = remember(pkg, installed) { if (installed) right.apps.label(pkg!!) else "App principal" }
    val sub = when {
        !installed -> "Tocá para elegir la app"
        label.contains("zlink", ignoreCase = true) -> "CarPlay · Android Auto"
        else -> "App principal"
    }
    Row(
        modifier.clip(shape).background(c.mainCard, shape).border(1.dp, c.panelLine, shape)
            .combinedClickable(onClick = { actions.onSlot(Slot.Main) }, onLongClick = { actions.onSlotLong(Slot.Main) })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight(0.6f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A8DFF), Color(0xFF1565E0)))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.66f))
            else LineIcon(R.drawable.ic_phone, 28.dp, Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Txt(label, 22, Color(0xFFF3F6FA), FontWeight.Bold)
            Txt(sub, 14, Color(0xFF8FA3BF))
        }
        LineIcon(R.drawable.ic_chevron, 22.dp, Color(0xFF8FA3BF))
    }
}

/** Mosaico: ícono en un círculo tenue del mismo color y el texto debajo. Mantener = elegir la app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(icon: Int, label: String, tint: Color, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(c.card, shape).border(1.dp, c.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.fillMaxHeight(0.52f).aspectRatio(1f).clip(CircleShape).background(tint.copy(alpha = if (c.isDark) 0.20f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(icon, 24.dp, tint) }
        Txt(label, 14, c.text, FontWeight.Medium, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp))
    }
}

@Composable
private fun AllAppsRow(onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.clip(shape).background(c.card, shape).border(1.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(R.drawable.ic_apps, 22.dp, c.accent)
        Txt("Todas las apps", 17, c.text, FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
        LineIcon(R.drawable.ic_chevron, 20.dp, c.muted)
    }
}

@Composable
private fun GearButton(onClick: () -> Unit, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(c.card, shape).border(1.dp, c.line, shape)
            .semantics { contentDescription = "Ajustes" }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(R.drawable.ic_settings, 22.dp, c.text) }
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

private fun minutesText(m: Int) = if (m >= 60) "${m / 60} h ${String.format(Locale.US, "%02d", m % 60)}" else "$m min"

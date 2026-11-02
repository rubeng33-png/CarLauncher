package com.rgservice.carlauncher.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
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
import com.rgservice.carlauncher.data.Media
import com.rgservice.carlauncher.data.ThemeMode
import com.rgservice.carlauncher.data.Weather
import com.rgservice.carlauncher.data.apps.Slot
import com.rgservice.carlauncher.data.car.CarLook
import com.rgservice.carlauncher.data.media.MediaRepository
import com.rgservice.carlauncher.ui.components.LineIcon
import com.rgservice.carlauncher.ui.components.Txt
import com.rgservice.carlauncher.ui.components.fmt1
import com.rgservice.carlauncher.ui.components.fmtKm
import com.rgservice.carlauncher.ui.pages.MapPage
import com.rgservice.carlauncher.ui.pages.NavBanner
import com.rgservice.carlauncher.ui.pages.PermissionButton
import com.rgservice.carlauncher.ui.pages.RightActions
import com.rgservice.carlauncher.ui.pages.RightData
import com.rgservice.carlauncher.ui.pages.mmss
import com.rgservice.carlauncher.ui.theme.CarTheme
import com.rgservice.carlauncher.ui.theme.Inter
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

/* ------------------------------------------------------------------ colores del diseño */

/** Colores propios del inicio, copiados de la imagen de referencia (luces encendidas / apagadas). */
private class HomePalette(val dark: Boolean) {
    val screen = if (dark) Color(0xFF0B1321) else Color(0xFFDDE5EF)
    val screenGlow = if (dark) Color(0xFF16243A) else Color(0xFFF4F7FB)
    val heroLine = if (dark) Color(0xFF253247) else Color(0xFFD3DCE8)
    val text = if (dark) Color(0xFFF5F7FA) else Color(0xFF101A2A)
    val date = if (dark) Color(0xFFD2D9E3) else Color(0xFF3E4B5E)
    val brand = if (dark) Color(0xFF8E9BB0) else Color(0xFF6B7A90)
    val model = if (dark) Color(0xFFE8EDF3) else Color(0xFF1A2638)
    val year = if (dark) Color(0xFFC0CAD8) else Color(0xFF4A586C)
    val yearLine = if (dark) Color(0xFF6F8FD0) else Color(0xFF2F6FE0)
    val lightsIcon = if (dark) Color(0xFF45D07F) else Color(0xFF6B7A90)
    val lightsText = if (dark) Color(0xFFDCE3EC) else Color(0xFF4A586C)
    val boxFill = if (dark) Color(0xB3182436) else Color(0xEBFFFFFF)
    val boxLine = if (dark) Color(0xFF33435C) else Color(0xFFD6DFEA)
    val speed = if (dark) Color(0xFFDDE6FF) else Color(0xFF1A66E6)
    val gps = if (dark) Color(0xFF8EA5C6) else Color(0xFF6B7A90)
    val barTrack = if (dark) Color(0xFF3A4A63) else Color(0xFFDDE4EE)
    val barFill = Color(0xFF2F7BF2)
    val tick = if (dark) Color(0xCCFFFFFF) else Color(0xFF8B99AD)
    val barLabel = if (dark) Color(0xFFC9D2DE) else Color(0xFF5A6B82)
    val cardTop = if (dark) Color(0xFF1C2536) else Color(0xFFFFFFFF)
    val cardBottom = if (dark) Color(0xFF151D2B) else Color(0xFFF3F6FA)
    val cardLine = if (dark) Color(0xFF2B3648) else Color(0xFFDCE3EC)
    val muted = if (dark) Color(0xFF9AA9C0) else Color(0xFF64738A)
    val chevron = if (dark) Color(0xFFDCE3EC) else Color(0xFF3E4B5E)
    // La tarjeta de la app principal es oscura en los dos temas (como en la imagen)
    val mainTop = if (dark) Color(0xFF1A2436) else Color(0xFF13233D)
    val mainBottom = if (dark) Color(0xFF1C2D48) else Color(0xFF0C1830)
    val mainLine = if (dark) Color(0xFF2E3E58) else Color(0xFF203352)
    val musicCircle = if (dark) Color(0xFF22385E) else Color(0xFFDDE8FB)
    val musicIcon = if (dark) Color(0xFF4C8DF0) else Color(0xFF2F6FE0)
    val radioCircle = if (dark) Color(0xFF4A3526) else Color(0xFFFCE9D9)
    val radioIcon = if (dark) Color(0xFFF2A54A) else Color(0xFFE2862A)
    val eqCircle = if (dark) Color(0xFF1C4A40) else Color(0xFFD9F3EC)
    val eqIcon = if (dark) Color(0xFF47D6B0) else Color(0xFF1FA889)
    val gridDots = Color(0xFF3A7BE0)
    val artBox = if (dark) Color(0xFF243147) else Color(0xFFE3ECF9)
    val play = Color(0xFF1E6FF0)
    val controls = if (dark) Color(0xFFCBD3DE) else Color(0xFF3E4B5E)
}

/** Texto con la tipografía del diseño (Inter). */
@Composable
private fun T(
    text: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
    spacing: Float = 0f, tabular: Boolean = false, align: TextAlign? = null, lineHeight: Float = 1.15f,
) = Txt(text, size, color, weight, modifier, align = align, tabular = tabular, lineHeight = lineHeight, letterSpacing = spacing, family = Inter)

/**
 * Pantalla de inicio: copia del diseño de referencia ("luces encendidas" de noche y
 * "luces apagadas" de día). Zona del auto (~62 %) y columna derecha (~38 %).
 * Todo lo editable viene de Ajustes › Mi auto (foto, marca, modelo, año, holograma).
 */
@Composable
fun HomeScreen(
    state: HomeState,
    right: RightData,
    rightActions: RightActions,
    actions: HomeActions,
    car: CarLook = CarLook(),
) {
    val p = remember(state.dark) { HomePalette(state.dark) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(p.screen)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(p.screenGlow, p.screen),
                        center = Offset(size.width * 0.35f, size.height * 0.45f),
                        radius = size.width * 0.7f,
                    ),
                )
            }
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
    ) {
        val compact = maxHeight < 600.dp          // Grande
        val veryCompact = maxHeight < 540.dp      // Muy grande
        Row(Modifier.fillMaxSize()) {
            Hero(state, right, rightActions, actions, car, p, compact, veryCompact, Modifier.weight(62.4f).fillMaxHeight())
            Spacer(Modifier.width(16.dp))
            RightColumn(right, rightActions, actions, p, Modifier.weight(37.6f).fillMaxHeight())
        }
    }
}

/* ================================================================== Zona del auto */

/** Lugar del auto dentro de la zona (fracciones medidas en la imagen de referencia). */
private const val CAR_L = 0.061f
private const val CAR_R = 0.936f
private const val CAR_T = 0.271f
private const val CAR_B = 0.743f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hero(
    s: HomeState, right: RightData, rightActions: RightActions, actions: HomeActions, car: CarLook,
    p: HomePalette, compact: Boolean, veryCompact: Boolean, modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier.clip(shape).border(1.dp, p.heroLine, shape)) {
        val W = maxWidth
        val H = maxHeight
        // Fondo de estudio (de la imagen): noche con luces / día claro
        val studio = ImageBitmap.imageResource(if (s.dark) R.drawable.studio_night else R.drawable.studio_day)
        Image(studio, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

        // Centro: el auto o el mapa (deslizar de costado)
        val pager = rememberPagerState(initialPage = if (right.initialPage == 1) 1 else 0) { 2 }
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager.currentPage) { rightActions.onPage(pager.currentPage) }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                CarLayer(car, s.dark, reflection = !compact, actions, W, H)
            } else {
                Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = if (compact) 120.dp else 150.dp, bottom = if (compact) 120.dp else 140.dp)) {
                    MapPage(right, rightActions)
                }
            }
        }

        // Arriba a la izquierda: hora, fecha y (si entra) el clima
        Column(Modifier.padding(start = 34.dp, top = 24.dp)) {
            Clock(actions.onToggleStats, if (veryCompact) 64 else if (compact) 74 else 85, p)
            if (!compact) s.weather?.let { WeatherLine(it, s.dark, p, Modifier.padding(top = 8.dp)) }
        }
        // Arriba a la derecha: marca, modelo, año, luces y mantenimiento
        CarInfo(s, car, actions, p, compact, Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 30.dp))

        // Bolitas Auto / Mapa (arriba al centro)
        Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0..1) {
                val active = i == pager.currentPage
                val w by animateDpAsState(if (active) 18.dp else 6.dp, label = "dotw")
                val col by animateColorAsState(if (active) p.barFill else p.tick.copy(alpha = 0.45f), label = "dotc")
                Box(
                    Modifier.size(28.dp, 18.dp).clickable { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(w, 6.dp).clip(CircleShape).background(col)) }
            }
        }

        // Giro de Maps / Waze (en cualquier página)
        right.nav?.let { n ->
            NavBanner(
                n, Modifier.align(Alignment.TopCenter).padding(top = H * CAR_T - 10.dp).widthIn(max = 460.dp).padding(horizontal = 16.dp),
            ) { rightActions.onNavBanner(n) }
        }

        // Abajo: velocidad, barra de velocidad y viaje
        val bottomSpeed = if (compact) 30.dp else 48.dp
        SpeedBox(
            s, actions, p,
            Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = bottomSpeed)
                .width(W * 0.329f).height(if (compact) 88.dp else 100.dp),
        )
        SpeedBar(
            s, actions, p,
            Modifier.align(Alignment.BottomStart).padding(start = W * 0.478f, bottom = bottomSpeed + 9.dp)
                .width(W * 0.493f).height(66.dp),
        )
        T(
            "Viaje ${s.trip.km.fmt1()} km · ${minutesText(s.trip.minutes)} · ${s.trip.avgKmh} km/h", 14, p.gps,
            tabular = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = W * 0.478f + 6.dp, bottom = bottomSpeed - 26.dp)
                .clickable(onClick = actions.onTrip),
        )
    }
}

@Composable
private fun Clock(onLongPress: () -> Unit, size: Int, p: HomePalette) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000L) }
    }
    val t = rememberInfiniteTransition(label = "colon")
    val colon by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "c")
    val hh = SimpleDateFormat("HH", Locale.US).format(now)
    val mm = SimpleDateFormat("mm", Locale.US).format(now)
    val date = SimpleDateFormat("EEEE · d MMM", Locale("es")).format(now).replace(".", "").uppercase(Locale("es"))
    Column(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(hh, size, p.text, FontWeight.SemiBold, tabular = true, lineHeight = 1f)
            T(":", size, p.text, FontWeight.SemiBold, Modifier.alpha(colon).padding(bottom = (size / 10).dp), lineHeight = 1f)
            T(mm, size, p.text, FontWeight.SemiBold, tabular = true, lineHeight = 1f)
        }
        T(date, if (size < 80) 18 else 21, p.date, FontWeight.Medium, spacing = 0.6f)
    }
}

@Composable
private fun WeatherLine(w: Weather, dark: Boolean, p: HomePalette, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LineIcon(weatherIcon(w.code, dark), 18.dp, p.gps)
        T(
            if (w.updatedAgo != null) "${w.tempC}° ${w.description} · ${w.updatedAgo}" else "${w.tempC}° ${w.description}",
            15, p.gps, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** TOYOTA / COROLLA / ── 2001 / Luces encendidas (tocar cambia el modo noche) / mantenimiento. */
@Composable
private fun CarInfo(s: HomeState, car: CarLook, actions: HomeActions, p: HomePalette, compact: Boolean, modifier: Modifier) {
    val pr = car.profile
    val es = Locale("es")
    Column(modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.End) {
        if (pr.brand.isNotBlank()) T(pr.brand.uppercase(es), 16, p.brand, spacing = 5.5f)
        if (pr.model.isNotBlank()) T(pr.model.uppercase(es), if (compact) 31 else 37, p.model, spacing = 2.5f, modifier = Modifier.padding(top = 2.dp))
        if (pr.year.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.width(32.dp).height(1.5.dp).background(p.yearLine))
            T(pr.year, 16, p.year, tabular = true, modifier = Modifier.padding(start = 10.dp))
        }
        Row(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = actions.onCycleTheme)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(R.drawable.ic_headlight, 22.dp, p.lightsIcon)
            T(
                (if (s.dark) "Luces encendidas" else "Luces apagadas") + if (s.theme != ThemeMode.Auto) " · fijo" else "",
                18, p.lightsText, modifier = Modifier.padding(start = 8.dp),
            )
        }
        MaintLine(s, actions.onMaint, p, Modifier.padding(top = 4.dp))
    }
}

/** "Aceite: faltan 3.200 km" (naranja con "· 1 ⚠" si hay avisos). Tocar abre Mantenimiento. */
@Composable
private fun MaintLine(s: HomeState, onClick: () -> Unit, p: HomePalette, modifier: Modifier) {
    val c = CarTheme.colors
    val warn = s.maintWarnings > 0
    val col = if (warn) c.warn else p.gps
    val text = when {
        !s.oil.configured -> "Mantenimiento: configurar"
        s.oil.remainingKm == 0 -> "Aceite: ¡cambiar ahora!"
        else -> "Aceite: faltan ${s.oil.remainingKm.fmtKm()} km"
    } + if (warn) " · ${s.maintWarnings} ⚠" else ""
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(R.drawable.ic_wrench, 16.dp, col)
        T(text, 14, col, FontWeight.Medium, tabular = true, modifier = Modifier.padding(start = 6.dp))
    }
}

/* ------------------------------------------------------------------ El auto */

/**
 * El auto en el lugar de la imagen. Tocar = Mantenimiento; mantener = elegir la foto.
 * Foto de fábrica: el Corolla recortado de la imagen. Foto propia: misma posición, centrada abajo.
 * De noche, opción de holograma.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarLayer(car: CarLook, dark: Boolean, reflection: Boolean, actions: HomeActions, W: Dp, H: Dp) {
    val factory = ImageBitmap.imageResource(R.drawable.car_factory)
    val holo = dark && car.profile.useHologram
    val custom = if (dark) car.night ?: car.day else car.day
    val img = custom ?: factory
    val boxW = W * (CAR_R - CAR_L)
    val boxH = H * (CAR_B - CAR_T)
    val aspect = if (holo) 16f / 9f else img.width.toFloat() / img.height.coerceAtLeast(1)
    val w = minOf(boxW, boxH * aspect)
    val h = w / aspect
    val x = W * CAR_L + (boxW - w) / 2
    val y = H * CAR_B - h            // apoyado en el piso, como en la imagen
    Box(Modifier.fillMaxSize()) {
        // Sombra en el piso (y en el día un poco más marcada)
        Box(
            Modifier.offset(x - w * 0.05f, y + h * 0.80f).size(w * 1.1f, h * 0.32f).drawBehind {
                softOval(center, size.width / 2f, size.height / 2f, Color.Black.copy(alpha = if (dark) 0.45f else 0.18f), BlendMode.SrcOver)
            },
        )
        if (holo) {
            AndroidView(
                factory = { ctx -> HoloVideoView(ctx).apply { blendWithBackground(); play(0, R.raw.holo_home) } },
                modifier = Modifier.offset(x, y).size(w, h),
                onRelease = { it.stop() },
            )
        } else {
            // Foto propia con fondo: bordes difuminados. Reflejo solo para fotos propias de noche
            // (el fondo de fábrica ya trae los reflejos del piso). Se preparan una sola vez por imagen.
            val shown = remember(img) { if (hasTransparency(img)) img else featherEdges(img) }
            if (custom != null && dark && reflection) {
                val refl = remember(shown) { reflectionOf(shown) }
                Image(refl, contentDescription = null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.offset(x, y + h - h * 0.03f).size(w, h * 0.45f))
            }
            Image(shown, contentDescription = "Tu auto", contentScale = ContentScale.FillBounds, modifier = Modifier.offset(x, y).size(w, h))
        }
        Box(Modifier.offset(x, y).size(w, h).combinedClickable(onClick = actions.onMaint, onLongClick = actions.onCarImage))
    }
}

/** ¿La imagen tiene fondo transparente? (PNG recortado). Mira las cuatro esquinas. */
private fun hasTransparency(img: ImageBitmap): Boolean = try {
    val b = img.asAndroidBitmap()
    b.hasAlpha() && listOf(0 to 0, b.width - 1 to 0, 0 to b.height - 1, b.width - 1 to b.height - 1)
        .any { (x, y) -> (b.getPixel(x, y) ushr 24) < 200 }
} catch (e: Exception) { false }

/** Copia de la foto con los cuatro bordes desvanecidos (para que se funda con el fondo). */
private fun featherEdges(src: ImageBitmap): ImageBitmap = try {
    val b = src.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    b.setHasAlpha(true)   // un JPG viene marcado "sin transparencia": sin esto los bordes se ven blancos
    val cv = android.graphics.Canvas(b)
    val w = b.width.toFloat(); val h = b.height.toFloat()
    val black = android.graphics.Color.BLACK
    val pt = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
    pt.shader = android.graphics.LinearGradient(0f, 0f, w, 0f, intArrayOf(0, black, black, 0), floatArrayOf(0f, 0.16f, 0.84f, 1f), android.graphics.Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, w, h, pt)
    pt.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, intArrayOf(0, black, black, 0), floatArrayOf(0f, 0.18f, 0.80f, 1f), android.graphics.Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, w, h, pt)
    b.asImageBitmap()
} catch (e: Throwable) { src }

/** Reflejo en el piso: la imagen dada vuelta, achatada (45 %), al 25 % y desvanecida hacia abajo. */
private fun reflectionOf(src: ImageBitmap): ImageBitmap = try {
    val s = src.asAndroidBitmap()
    val rh = (s.height * 0.45f).toInt().coerceAtLeast(1)
    val out = android.graphics.Bitmap.createBitmap(s.width, rh, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out)
    cv.save()
    cv.translate(0f, rh.toFloat())
    cv.scale(1f, -rh.toFloat() / s.height)
    cv.drawBitmap(s, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = 64 })
    cv.restore()
    val pt = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        shader = android.graphics.LinearGradient(0f, 0f, 0f, rh.toFloat(), android.graphics.Color.BLACK, 0, android.graphics.Shader.TileMode.CLAMP)
    }
    cv.drawRect(0f, 0f, s.width.toFloat(), rh.toFloat(), pt)
    out.asImageBitmap()
} catch (e: Throwable) { src }

/** Óvalo con degradé radial (sombra suave). */
private fun DrawScope.softOval(center: Offset, rx: Float, ry: Float, color: Color, mode: BlendMode) {
    if (rx <= 0f || ry <= 0f) return
    scale(1f, ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, rx), rx, center, blendMode = mode)
    }
}

/* ------------------------------------------------------------------ Velocidad */

@Composable
private fun SpeedBox(s: HomeState, actions: HomeActions, p: HomePalette, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(p.boxFill, shape).border(1.dp, p.boxLine, shape)
            .clickable(onClick = actions.onLimit).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(s.speedKmh?.toString() ?: "--", 67, if (s.overLimit) c.warn else p.speed, FontWeight.Bold, tabular = true, lineHeight = 1f)
        Column(Modifier.padding(start = 22.dp)) {
            T("km/h", 21, p.text, FontWeight.SemiBold)
            GpsLabel(s.gpsOk, p)
        }
    }
}

/** "GPS" con el punto que late si hay señal; "Sin GPS" apagado si no. */
@Composable
private fun GpsLabel(ok: Boolean, p: HomePalette) {
    val t = rememberInfiniteTransition(label = "gps")
    val a by t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "blink")
    T(if (ok) "GPS" else "Sin GPS", 16, p.gps, modifier = Modifier.alpha(if (ok) a else 0.6f))
}

private const val BAR_MAX = 160f

/** Barra 0 · 60 · 100 · 160 con marcas; naranja si pasa el aviso. Tocar = elegir el aviso. */
@Composable
private fun SpeedBar(s: HomeState, actions: HomeActions, p: HomePalette, modifier: Modifier) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier.clip(shape).background(p.boxFill, shape).border(1.dp, p.boxLine, shape)
            .clickable(onClick = actions.onLimit).padding(horizontal = 19.dp, vertical = 10.dp),
    ) {
        val w = maxWidth
        val frac = ((s.speedKmh ?: 0) / BAR_MAX).coerceIn(0f, 1f)
        val anim by animateFloatAsState(frac, tween(600), label = "bar")
        val fill = if (s.overLimit) c.warn else p.barFill
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(14.dp)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.barTrack))
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(anim).height(7.dp).clip(RoundedCornerShape(4.dp)).background(fill))
                listOf(60, 100, 160).forEach { v ->
                    val x = (w * (v / BAR_MAX) - if (v == 160) 6.dp else 0.dp)
                    Box(Modifier.offset(x = x).width(1.5.dp).fillMaxHeight().background(p.tick))
                }
                if (s.speedLimit > 0) {
                    val lf = (s.speedLimit / BAR_MAX).coerceIn(0f, 1f)
                    Box(Modifier.offset(x = (w * lf - 1.5.dp).coerceAtLeast(0.dp)).width(3.dp).fillMaxHeight().padding(vertical = 2.dp).background(c.warn.copy(alpha = 0.85f)))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(20.dp)) {
                listOf(0, 60, 100, 160).forEach { v ->
                    val x = w * (v / BAR_MAX)
                    val off = when (v) { 0 -> 0.dp; 160 -> (x - 30.dp); else -> (x - 11.dp) }
                    T(v.toString(), 16, p.barLabel, tabular = true, modifier = Modifier.offset(x = off.coerceAtLeast(0.dp)))
                }
            }
        }
    }
}

/* ================================================================== Columna derecha */

@Composable
private fun RightColumn(right: RightData, rightActions: RightActions, actions: HomeActions, p: HomePalette, modifier: Modifier) {
    val slots by right.apps.slots.collectAsStateWithLifecycle()
    // Alturas proporcionales a la imagen: 118 · 164 · 72 · 101 (con 14 de separación)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MainAppCard(slots[Slot.Main], right, actions, p, Modifier.fillMaxWidth().weight(118f))
        Row(Modifier.fillMaxWidth().weight(164f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tile(R.drawable.ic_music, "BT Music", p.musicCircle, p.musicIcon, p, Modifier.weight(1f), { actions.onSlotLong(Slot.Music) }) { actions.onSlot(Slot.Music) }
            Tile(R.drawable.ic_radio, "Radio", p.radioCircle, p.radioIcon, p, Modifier.weight(1f), { actions.onSlotLong(Slot.Radio) }) { actions.onSlot(Slot.Radio) }
            Tile(R.drawable.ic_eq, "Ecualizador", p.eqCircle, p.eqIcon, p, Modifier.weight(1f), { actions.onSlotLong(Slot.Eq) }) { actions.onSlot(Slot.Eq) }
        }
        Row(Modifier.fillMaxWidth().weight(72f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AllAppsRow(actions.onApps, p, Modifier.weight(1f).fillMaxHeight())
            GearButton(actions.onSettings, p, Modifier.fillMaxHeight().aspectRatio(1f))
        }
        Player(right.media, rightActions, p, Modifier.fillMaxWidth().weight(112f))
    }
}

@Composable
private fun cardModifier(p: HomePalette, shape: RoundedCornerShape) =
    Modifier.clip(shape).background(Brush.verticalGradient(listOf(p.cardTop, p.cardBottom)), shape).border(1.dp, p.cardLine, shape)

/** Tarjeta de la app principal (ZLink5). Tocar abre; mantener = elegir otra app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainAppCard(pkg: String?, right: RightData, actions: HomeActions, p: HomePalette, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val installed = pkg != null && right.apps.installed(pkg)
    val icon = remember(pkg, installed) { if (installed) right.apps.icon(pkg!!) else null }
    val label = remember(pkg, installed) { if (installed) right.apps.label(pkg!!) else "ZLink5" }
    val sub = when {
        !installed -> "Tocá para elegir la app"
        label.contains("zlink", ignoreCase = true) -> "CarPlay · Android Auto"
        else -> "App principal"
    }
    Row(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(p.mainTop, p.mainBottom), start = Offset.Zero, end = Offset.Infinite), shape)
            .border(1.dp, p.mainLine, shape)
            .combinedClickable(onClick = { actions.onSlot(Slot.Main) }, onLongClick = { actions.onSlotLong(Slot.Main) })
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight(0.64f).aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF1666F0)))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.62f))
            else LineIcon(R.drawable.ic_smartphone, 46.dp, Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            T(label, 32, Color(0xFFF5F7FA), FontWeight.SemiBold)
            T(sub, 19, Color(0xFF9AA9C0), modifier = Modifier.padding(top = 2.dp))
        }
        LineIcon(R.drawable.ic_chevron, 28.dp, Color(0xFFDCE3EC))
    }
}

/** Mosaico: ícono en un círculo del mismo color y el texto debajo. Mantener = elegir la app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(icon: Int, label: String, circle: Color, tint: Color, p: HomePalette, modifier: Modifier, onLong: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        modifier.fillMaxHeight().then(cardModifier(p, shape)).combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        val d = minOf(maxHeight * 0.38f, maxWidth * 0.62f)
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.43f - d / 2).size(d)
                .clip(if (p.dark) CircleShape else RoundedCornerShape(d * 0.3f)).background(circle),
            contentAlignment = Alignment.Center,
        ) { LineIcon(icon, d * 0.46f, tint) }
        T(
            label, 19, p.text, FontWeight.Medium, align = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = maxHeight * 0.68f).fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AllAppsRow(onClick: () -> Unit, p: HomePalette, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.then(cardModifier(p, shape)).clickable(onClick = onClick).padding(start = 26.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val st = size.width / 3f
            for (i in 0..2) for (j in 0..2) drawCircle(p.gridDots, st * 0.22f, Offset(st * (i + 0.5f), st * (j + 0.5f)))
        }
        T("Todas las apps", 23, p.text, FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 22.dp))
        LineIcon(R.drawable.ic_chevron, 26.dp, p.chevron)
    }
}

@Composable
private fun GearButton(onClick: () -> Unit, p: HomePalette, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.then(cardModifier(p, shape)).semantics { contentDescription = "Ajustes" }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(R.drawable.ic_settings, 26.dp, p.chevron) }
}

/**
 * Mini reproductor: carátula (tocar = abrir la app que suena; mantener = diagnóstico),
 * título, "artista - fuente", progreso con tiempos y ⏮ ⏯ ⏭. Sin permiso: botón para permitir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Player(m: Media, a: RightActions, p: HomePalette, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m) {
        while (true) {
            pos = MediaRepository.positionNow(m)
            if (!m.playing) break
            delay(500)
        }
    }
    BoxWithConstraints(modifier.then(cardModifier(p, shape)).padding(horizontal = 18.dp, vertical = 12.dp)) {
        val art = minOf(maxHeight, 76.dp)
        val play = minOf(maxHeight, 70.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(art).clip(RoundedCornerShape(12.dp)).background(p.artBox)
                    .combinedClickable(onClick = a.onOpenMusic, onLongClick = a.onMusicDiag),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = m.art
                if (bmp != null) {
                    val img = remember(bmp) { bmp.asImageBitmap() }
                    Image(img, contentDescription = "Carátula", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else LineIcon(R.drawable.ic_music, art * 0.46f, Color(0xFF3D86F0))
            }
            if (!m.hasPermission) {
                Box(Modifier.weight(1f).padding(start = 14.dp)) { PermissionButton(a) }
            } else {
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 10.dp)) {
                    T(m.title, 18, p.text, FontWeight.SemiBold)
                    T(listOf(m.artist, m.source).filter { it.isNotBlank() }.joinToString(" - "), 14, p.muted, modifier = Modifier.padding(top = 2.dp))
                    val frac = if (m.durationMs > 0) (pos.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.barTrack)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.barFill))
                    }
                    if (m.durationMs > 0) T("${mmss(pos, 2)} / ${mmss(m.durationMs, 2)}", 13, p.muted, tabular = true, modifier = Modifier.padding(top = 6.dp))
                }
                LineIcon(R.drawable.ic_prev, 28.dp, p.controls, Modifier.clip(CircleShape).clickable(onClick = a.onPrevious).padding(6.dp))
                Box(
                    Modifier.padding(horizontal = 8.dp).size(play).clip(CircleShape).background(p.play).clickable(onClick = a.onPlayPause),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play, play * 0.42f, Color.White) }
                LineIcon(R.drawable.ic_next, 28.dp, p.controls, Modifier.clip(CircleShape).clickable(onClick = a.onNext).padding(6.dp))
            }
        }
    }
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

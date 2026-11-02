package com.rgservice.carlauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rgservice.carlauncher.data.HomeState
import com.rgservice.carlauncher.data.apps.AppEntry
import com.rgservice.carlauncher.data.apps.AppsRepository
import com.rgservice.carlauncher.data.apps.Slot
import com.rgservice.carlauncher.data.location.LiveGps
import com.rgservice.carlauncher.data.maint.Maintenance
import com.rgservice.carlauncher.ui.components.CarCard
import com.rgservice.carlauncher.ui.components.Hairline
import com.rgservice.carlauncher.ui.components.Txt
import com.rgservice.carlauncher.ui.components.fmtKm
import com.rgservice.carlauncher.ui.theme.Barlow
import com.rgservice.carlauncher.ui.theme.CarTheme
import kotlinx.coroutines.delay

/** Ventanas que se abren desde el inicio. */
sealed interface HomeDialog {
    /** Cargar/corregir el km del tablero. [then] = ítem a abrir después. */
    data class Odometer(val then: String? = null) : HomeDialog
    data class GaugeEdit(val id: String) : HomeDialog
    data class NextKm(val id: String) : HomeDialog
    data object Name : HomeDialog
    data object Limit : HomeDialog
    data object TripReset : HomeDialog
    /** Elegir una app: para un botón de la barra, para agregar o reemplazar un acceso. */
    data class PickApp(val title: String, val slot: Slot? = null, val replaceFav: String? = null) : HomeDialog
    data class FavMenu(val pkg: String) : HomeDialog
    data class Destination(val key: String, val name: String) : HomeDialog
    data class DestinationText(val key: String, val name: String) : HomeDialog
}

@Composable
fun HomeDialogs(dialog: HomeDialog?, s: HomeState, vm: HomeViewModel, open: (HomeDialog?) -> Unit) {
    val maint = vm.repo.maint
    val close = { open(null) }
    when (dialog) {
        null -> Unit

        is HomeDialog.Odometer -> NumberDialog(
            title = "Km del tablero",
            help = "Mirá el cuentakilómetros del auto y escribí el número tal cual. " +
                "Desde ahí el GPS le va sumando lo que manejes.",
            initial = maint.odo(),
            suffix = "km",
            onDismiss = { if (dialog.then != null && maint.hasOdo()) open(HomeDialog.GaugeEdit(dialog.then)) else close() },
            onSave = { km ->
                maint.setOdo(km)
                open(dialog.then?.let { HomeDialog.GaugeEdit(it) })
            },
        )

        is HomeDialog.GaugeEdit -> {
            // Se lee directo de lo guardado: recién cargado puede no haber llegado todavía al estado
            val odo = maint.odo()
            if (odo == null) {
                LaunchedEffect(dialog) { open(HomeDialog.Odometer(then = dialog.id)) }
                return
            }
            val item = Maintenance.item(dialog.id)
            val interval = maint.interval(dialog.id)
            val next = maint.next(dialog.id)
            val rem = maint.remaining(dialog.id)
            CarDialog(item.name, onDismiss = close) {
                InfoRow("Tablero ahora", "${odo.fmtKm()} km", action = "Corregir") { open(HomeDialog.Odometer(then = dialog.id)) }
                InfoRow(
                    "Próximo cambio",
                    if (next != null) "${next.fmtKm()} km" else "sin cargar",
                    action = "Cambiar",
                    highlight = rem != null && rem <= Maintenance.WARN_KM,
                ) { open(HomeDialog.NextKm(dialog.id)) }
                if (rem != null) {
                    Txt(
                        if (rem == 0) "¡Ya toca cambiarlo!" else "Faltan ${rem.fmtKm()} km",
                        20, if (rem <= Maintenance.WARN_KM) CarTheme.colors.warn else CarTheme.colors.accent,
                        FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
                Txt("Se cambia cada", 16, CarTheme.colors.muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.presets.forEach { p ->
                        Chip("${p.fmtKm()} km", p == interval, Modifier.weight(1f)) { maint.setInterval(dialog.id, p) }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton("Cerrar", primary = false, Modifier.weight(1f), onClick = close)
                    DialogButton("Lo cambié hoy", primary = true, Modifier.weight(1.4f)) {
                        maint.changedNow(dialog.id)
                        close()
                    }
                }
                Txt(
                    "“Lo cambié hoy” deja el próximo en ${(odo + interval).fmtKm()} km",
                    15, CarTheme.colors.muted, align = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        is HomeDialog.NextKm -> {
            val back = { open(HomeDialog.GaugeEdit(dialog.id)) }
            NumberDialog(
                title = "Próximo cambio",
                help = "¿A cuántos km del tablero toca el próximo cambio de ${Maintenance.item(dialog.id).name.lowercase()}? " +
                    "Es el número del sticker del taller.",
                initial = maint.next(dialog.id) ?: maint.odo()?.let { it + maint.interval(dialog.id) },
                suffix = "km",
                onDismiss = back,
                onSave = { km -> maint.setNext(dialog.id, km); back() },
            )
        }

        HomeDialog.Name -> TextDialog(
            title = "¿Cómo te llamás?",
            initial = if (s.name == "conductor") "" else s.name,
            onDismiss = close,
            onSave = { vm.repo.setName(it); close() },
        )

        HomeDialog.Limit -> CarDialog("Aviso de velocidad", onDismiss = close) {
            Txt("El número se pone naranja si lo pasás.", 17, CarTheme.colors.muted, modifier = Modifier.padding(bottom = 14.dp))
            val opts = listOf(0, 40, 60, 80, 90, 100, 110, 120)
            opts.chunked(4).forEach { row ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { v ->
                        Chip(if (v == 0) "Sin aviso" else "$v", v == s.speedLimit, Modifier.weight(1f)) {
                            vm.repo.setLimit(v); close()
                        }
                    }
                }
            }
        }

        is HomeDialog.PickApp -> AppPicker(dialog.title, vm.apps, onDismiss = close) { pkg ->
            when {
                dialog.slot != null -> { vm.apps.setSlot(dialog.slot, pkg); vm.apps.launch(pkg) }
                dialog.replaceFav != null -> vm.apps.replaceFav(dialog.replaceFav, pkg)
                else -> vm.apps.addFav(pkg)
            }
            close()
        }

        is HomeDialog.FavMenu -> CarDialog(vm.apps.label(dialog.pkg), onDismiss = close) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Quitar", primary = false, Modifier.weight(1f)) { vm.apps.removeFav(dialog.pkg); close() }
                DialogButton("Cambiar por otra", primary = true, Modifier.weight(1.4f)) {
                    open(HomeDialog.PickApp("Cambiar por…", replaceFav = dialog.pkg))
                }
            }
        }

        is HomeDialog.Destination -> {
            val cur = vm.apps.dest.value[dialog.key]
            CarDialog(dialog.name, onDismiss = close) {
                Txt(cur ?: "Todavía sin destino. Elegí cómo guardarlo:", 18, CarTheme.colors.muted, maxLines = 2,
                    modifier = Modifier.padding(bottom = 18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton("Escribir dirección", primary = false, Modifier.weight(1f)) {
                        open(HomeDialog.DestinationText(dialog.key, dialog.name))
                    }
                    DialogButton("Estoy acá ahora", primary = true, Modifier.weight(1f)) {
                        vm.apps.setDest(dialog.key, String.format(java.util.Locale.US, "%.6f,%.6f", LiveGps.fix.value?.lat ?: vm.lastLat(), LiveGps.fix.value?.lon ?: vm.lastLon()))
                        close()
                    }
                }
                if (cur != null) {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DialogButton("Borrar", primary = false, Modifier.weight(1f)) { vm.apps.setDest(dialog.key, null); close() }
                        DialogButton("Ir ahora", primary = false, Modifier.weight(1f)) { vm.apps.navigateTo(cur); close() }
                    }
                }
            }
        }

        is HomeDialog.DestinationText -> TextDialog(
            title = "Dirección de ${dialog.name}",
            initial = vm.apps.dest.value[dialog.key]?.takeIf { !it.first().isDigit() && !it.startsWith("-") } ?: "",
            maxLen = 80,
            onDismiss = close,
            onSave = { vm.apps.setDest(dialog.key, it); close() },
        )

        HomeDialog.TripReset -> CarDialog("Viaje", onDismiss = close) {
            Txt(
                "El viaje empieza solo de nuevo después de 20 min parado. ¿Lo ponés en cero ahora?",
                18, CarTheme.colors.muted, maxLines = 3,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Cancelar", primary = false, Modifier.weight(1f), onClick = close)
                DialogButton("Poner en cero", primary = true, Modifier.weight(1f)) { vm.repo.resetTrip(); close() }
            }
        }
    }
}

/* ------------------------------------------------------------------ piezas */

@Composable
private fun CarDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CarCard(Modifier.width(560.dp), padding = PaddingValues(28.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Txt(title, 28, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, action: String, highlight: Boolean = false, onAction: () -> Unit) {
    val c = CarTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, 18, c.muted, modifier = Modifier.weight(1f))
        Txt(value, 22, if (highlight) c.warn else c.text, FontWeight.SemiBold, tabular = true)
        Txt(action, 18, c.accent, FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(10.dp))
    }
    Hairline(Modifier.fillMaxWidth().height(1.dp))
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(52.dp).clip(shape)
            .background(if (selected) c.pill else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 17, if (selected) c.accent else c.text, FontWeight.SemiBold, tabular = true) }
}

@Composable
private fun DialogButton(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = CarTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.height(60.dp).clip(shape)
            .background(if (primary) c.accent else c.card2)
            .border(1.dp, if (primary) Color.Transparent else c.line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 19, if (primary) c.onAccent else c.text, FontWeight.SemiBold) }
}

@Composable
private fun NumberDialog(title: String, help: String, initial: Int?, suffix: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val start = initial?.toString() ?: ""
    var v by remember { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val num = v.text.toIntOrNull()
    val save = { if (num != null) onSave(num) }
    CarDialog(title, onDismiss) {
        Txt(help, 17, CarTheme.colors.muted, maxLines = 3, modifier = Modifier.padding(bottom = 16.dp))
        Field(v, { nv -> v = nv.copy(text = nv.text.filter(Char::isDigit).take(7)) }, KeyboardType.Number, suffix, onDone = save)
        Txt(num?.let { "${it.fmtKm()} $suffix" } ?: " ", 16, CarTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogButton("Cancelar", primary = false, Modifier.weight(1f), onClick = onDismiss)
            DialogButton("Guardar", primary = true, Modifier.weight(1f), onClick = save)
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, maxLen: Int = 20, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val save = { onSave(v.text) }
    CarDialog(title, onDismiss) {
        Field(v, { nv -> v = nv.copy(text = nv.text.take(maxLen)) }, KeyboardType.Text, null, onDone = save)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DialogButton("Cancelar", primary = false, Modifier.weight(1f), onClick = onDismiss)
            DialogButton("Guardar", primary = true, Modifier.weight(1f), onClick = save)
        }
    }
}

@Composable
private fun Field(
    v: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    type: KeyboardType,
    suffix: String?,
    onDone: () -> Unit,
) {
    val c = CarTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); try { focus.requestFocus() } catch (e: Exception) { } }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(shape).background(c.card2).border(1.dp, c.accent, shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = v,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        if (suffix != null) Txt(suffix, 22, c.muted)
    }
}

/** Lista de apps instaladas para elegir una. */
@Composable
private fun AppPicker(title: String, apps: AppsRepository, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = CarTheme.colors
    var list by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) { list = apps.all() }
    CarDialog(title, onDismiss) {
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            val l = list
            if (l == null) {
                Txt("Buscando apps…", 18, c.muted, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(l, key = { it.pkg }) { e ->
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(e.pkg) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = remember(e.pkg) { apps.icon(e.pkg) }
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
                            else Spacer(Modifier.size(44.dp))
                            Txt(e.label, 20, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DialogButton("Cancelar", primary = false, Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

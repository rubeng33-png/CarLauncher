package com.rgservice.carlauncher

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgservice.carlauncher.core.DesignScale
import com.rgservice.carlauncher.data.ThemeMode
import com.rgservice.carlauncher.data.apps.Slot
import com.rgservice.carlauncher.data.location.LiveGps
import com.rgservice.carlauncher.data.location.TripService
import com.rgservice.carlauncher.data.media.LiveNav
import com.rgservice.carlauncher.ui.pages.RightActions
import com.rgservice.carlauncher.ui.pages.RightData
import com.rgservice.carlauncher.ui.components.PerfMeter
import com.rgservice.carlauncher.ui.home.HomeActions
import com.rgservice.carlauncher.ui.home.HomeDialog
import com.rgservice.carlauncher.ui.home.HomeDialogs
import com.rgservice.carlauncher.ui.home.HomeScreen
import com.rgservice.carlauncher.ui.home.HomeViewModel
import com.rgservice.carlauncher.ui.theme.CarLauncherTheme

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()
    private val dialog = mutableStateOf<HomeDialog?>(null)

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) TripService.start(this)
        else toast("Sin permiso de ubicación no se puede medir velocidad ni km")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = HomeActions(
            onCycleTheme = {
                val m = vm.cycleTheme()
                toast(when (m) {
                    ThemeMode.Auto -> "Modo noche automático (según el sol)"
                    ThemeMode.Night -> "Modo noche siempre"
                    ThemeMode.Day -> "Modo día siempre"
                })
            },
            onNextSize = {
                vm.nextSize()
                toast("Tamaño: ${vm.size.value.label}")
            },
            onToggleStats = vm::toggleStats,
            onComingSoon = { what -> toast("$what: llega en la próxima etapa") },
            onGauge = { id -> dialog.value = HomeDialog.GaugeEdit(id) },
            onOdometer = { dialog.value = HomeDialog.Odometer() },
            onName = { dialog.value = HomeDialog.Name },
            onLimit = { dialog.value = HomeDialog.Limit },
            onTrip = { dialog.value = HomeDialog.TripReset },
            onSlot = ::openSlot,
            onSlotLong = { slot -> dialog.value = HomeDialog.PickApp("App para ${slot.label}", slot = slot) },
            onBluetooth = {
                if (!vm.apps.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) toast("No se pudo abrir Bluetooth")
            },
        )
        val rightActions = RightActions(
            onPage = vm::savePage,
            onPlayPause = { vm.music.playPause() },
            onNext = { vm.music.next() },
            onPrevious = { vm.music.previous() },
            onOpenMusic = {
                val pkg = vm.music.openPlayingApp()
                if (!vm.apps.launch(pkg)) openSlot(Slot.Music)
            },
            onMusicPermission = {
                toast("Activá \"Car Launcher 3\" en la lista")
                vm.apps.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onOpenMaps = { openSlot(Slot.Nav) },
            onNavBanner = { n ->
                val ok = try { n.intent?.send(); n.intent != null } catch (e: Exception) { false }
                if (!ok) vm.apps.launch(n.pkg)
            },
            onDest = { key, name ->
                val d = vm.apps.dest.value[key]
                if (d == null) dialog.value = HomeDialog.Destination(key, name)
                else if (!vm.apps.navigateTo(d)) toast("No hay app de mapas para navegar")
            },
            onDestLong = { key, name -> dialog.value = HomeDialog.Destination(key, name) },
            onZoom = vm::saveZoom,
            onFav = { pkg -> if (!vm.apps.launch(pkg)) toast("No se pudo abrir") },
            onFavLong = { pkg -> dialog.value = HomeDialog.FavMenu(pkg) },
            onFavAdd = { dialog.value = HomeDialog.PickApp("Agregar a accesos") },
        )
        val initialPage = vm.initialPage
        val initialZoom = vm.mapZoom
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val size by vm.size.collectAsStateWithLifecycle()
            val stats by vm.showStats.collectAsStateWithLifecycle()
            val open by dialog
            val media by vm.media.collectAsStateWithLifecycle()
            val nav by LiveNav.nav.collectAsStateWithLifecycle()
            val fix by LiveGps.fix.collectAsStateWithLifecycle()
            val favs by vm.apps.favs.collectAsStateWithLifecycle()
            val dest by vm.apps.dest.collectAsStateWithLifecycle()
            val right = RightData(
                media = media, nav = nav, fix = fix?.takeIf { state.gpsOk },
                fallbackLat = vm.lastLat(), fallbackLon = vm.lastLon(),
                favs = favs, dest = dest, apps = vm.apps,
                initialPage = initialPage, initialZoom = initialZoom,
            )
            CarLauncherTheme(dark = state.dark) {
                DesignScale(size) {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(state, right, rightActions, actions)
                        if (stats) PerfMeter(Modifier.align(Alignment.TopCenter).padding(top = 4.dp), onClick = vm::toggleStats)
                    }
                    HomeDialogs(open, state, vm) { dialog.value = it }
                }
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            TripService.start(this)
        } else {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Abre la app del botón; si todavía no se eligió (o ya no está), pide elegirla. */
    private fun openSlot(slot: Slot) {
        val pkg = vm.apps.slots.value[slot]
        if (!vm.apps.launch(pkg)) dialog.value = HomeDialog.PickApp("App para ${slot.label}", slot = slot)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher no se cierra con Atrás; sí cierra una ventana abierta
        if (dialog.value != null) dialog.value = null
    }
}

package com.rgservice.carlauncher13

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder

/**
 * Servicio en primer plano:
 *  - lee el GPS aunque estés en Maps u otra app (velocidad, viaje, odómetro),
 *  - descuenta los km de todos los ítems de mantenimiento,
 *  - guarda la ubicación para el clima y el modo noche por sol,
 *  - abre el launcher al encender la pantalla (opción "Abrir al dar contacto").
 */
class TripService : Service(), LocationListener {

    companion object {
        private const val NEW_TRIP_AFTER_MS = 20 * 60 * 1000L // 20 min detenido = viaje nuevo
        private var lastOpen = 0L

        /** Trae el launcher al frente (si la opción está activada). */
        fun openLauncher(c: Context, fromIgnition: Boolean) {
            val sp = c.getSharedPreferences("cl", Context.MODE_PRIVATE)
            if (!sp.getBoolean("ignOpen", true)) return
            val now = System.currentTimeMillis()
            if (now - lastOpen < 10_000) return
            lastOpen = now
            try {
                c.startActivity(Intent(c, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("ign", fromIgnition))
            } catch (e: Exception) { }
        }
    }

    private lateinit var sp: SharedPreferences
    private var last: Location? = null
    private var gpsOn = false
    private var lastLatSave = 0L

    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private var wakeTask: Runnable? = null

    /**
     * Al sacar el contacto (pantalla apagada) se suelta el GPS para que la radio pueda entrar
     * en reposo tranquila. Al volver a dar contacto se espera a que la radio termine de
     * despertar antes de reanudar el GPS y abrir el launcher.
     */
    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    wakeTask?.let { h.removeCallbacks(it) }; wakeTask = null
                    stopGps()
                }
                Intent.ACTION_SCREEN_ON -> {
                    wakeTask?.let { h.removeCallbacks(it) }
                    val r = Runnable { startGps(); openLauncher(c, true) }
                    wakeTask = r
                    h.postDelayed(r, 3000)
                }
            }
        }
    }

    private fun stopGps() {
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (e: Exception) { }
        gpsOn = false
        last = null
        Live.speedKmh = -1f
    }

    override fun onCreate() {
        super.onCreate()
        sp = getSharedPreferences("cl", Context.MODE_PRIVATE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("trip", "Recorrido", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "trip")
            .setContentTitle("Car Launcher")
            .setContentText("Registrando recorrido por GPS")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, n)

        try {
            registerReceiver(screenRx, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            })
        } catch (e: Exception) { }
        startGps()
    }

    private fun startGps() {
        if (gpsOn) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
            gpsOn = true
        } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // por si el permiso se dio después (solo con la pantalla prendida)
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isInteractive) startGps()
        return START_STICKY
    }

    override fun onDestroy() {
        wakeTask?.let { h.removeCallbacks(it) }
        try { unregisterReceiver(screenRx) } catch (e: Exception) { }
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (e: Exception) { }
        Live.speedKmh = -1f
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLocationChanged(l: Location) {
        if (l.hasAccuracy() && l.accuracy > 40f) return // descarta lecturas imprecisas
        val prev = last
        last = l
        val now = System.currentTimeMillis()
        val ed = sp.edit()
        var dirty = false
        // La ubicación (para clima y modo noche) se guarda como mucho una vez por minuto
        if (now - lastLatSave > 60_000L) {
            lastLatSave = now
            ed.putString("lat", l.latitude.toString()).putString("lon", l.longitude.toString())
            dirty = true
        }

        var speed = if (l.hasSpeed()) l.speed else 0f // m/s
        if (prev != null) {
            val d = prev.distanceTo(l)
            val dtMs = l.time - prev.time
            if (!l.hasSpeed() && dtMs > 0) speed = d / (dtMs / 1000f)
            // Solo cuenta si realmente se mueve (> ~5 km/h) y sin saltos raros
            if (speed > 1.5f && d < 500f) {
                // ---- Viaje ----
                val lastMove = sp.getLong("lastMove", 0L)
                var tripM = sp.getFloat("tripM", 0f)
                var tripMove = sp.getLong("tripMoveMs", 0L)
                if (now - lastMove > NEW_TRIP_AFTER_MS) {
                    tripM = 0f; tripMove = 0L
                    ed.putLong("tripStart", now)
                }
                tripM += d
                tripMove += dtMs.coerceIn(0L, 10_000L)
                ed.putFloat("tripM", tripM).putLong("tripMoveMs", tripMove).putLong("lastMove", now)
                ed.putFloat("odoM", sp.getFloat("odoM", 0f) + d)
                // Odómetro del tablero (si ya lo cargaste)
                val odo = sp.getFloat("odoKm", -1f)
                if (odo >= 0f) ed.putFloat("odoKm", odo + d / 1000f)

                // ---- Mantenimiento: descuenta km enteros a todos los ítems configurados ----
                var acc = sp.getFloat("accM", 0f) + d
                val km = (acc / 1000f).toInt()
                if (km > 0) {
                    acc -= km * 1000f
                    // formato viejo (km restantes) solo para ítems que todavía no tienen "próximo cambio"
                    for (k in Maint.allKmIds(sp)) {
                        if (sp.getInt("next_$k", -1) >= 0 && odo >= 0f) continue
                        val v = sp.getInt(k, -1)
                        if (v >= 0) ed.putInt(k, maxOf(0, v - km))
                    }
                }
                ed.putFloat("accM", acc)
                dirty = true
            }
        }
        Live.speedKmh = speed * 3.6f
        Live.lat = l.latitude; Live.lon = l.longitude
        if (l.hasBearing() && speed > 1.5f) Live.bearing = l.bearing
        Live.fixAt = now
        if (dirty) ed.apply()
    }

    // Necesarios para compatibilidad con versiones donde la interfaz no trae métodos por defecto
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}

/** Al prender la radio: arranca el servicio de GPS y (si está activado) abre el launcher. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // Al arrancar la radio, el launcher ya se abre solo por ser la pantalla de inicio.
        try { c.startForegroundService(Intent(c, TripService::class.java)) } catch (e: Exception) { }
    }
}

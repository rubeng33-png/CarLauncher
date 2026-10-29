package com.rgservice.carlauncher13

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        // Ubicación por defecto (Capiatá) hasta que el GPS dé la real.
        const val LAT = -25.35
        const val LON = -57.45
        val SLOTS = listOf(
            Slot("pkgNav", "Mapas", R.drawable.ic_nav, "com.google.android.apps.maps"),
            Slot("pkgMusic", "Música", R.drawable.ic_music, null),
            Slot("pkgRadio", "Radio", R.drawable.ic_radio, null),
            Slot("pkgPhone", "Teléfono", R.drawable.ic_phone, null)
        )
    }

    class Slot(val key: String, val label: String, val icon: Int, val def: String?)

    lateinit var sp: SharedPreferences
    lateinit var pal: Pal
    var dark = false
    private val h = Handler(Looper.getMainLooper())

    // ---- vistas del inicio ----
    private lateinit var homeView: View
    private lateinit var screenHost: FrameLayout
    private lateinit var splash: View
    private lateinit var tvHello: TextView
    private lateinit var tvGreet: TextView
    private lateinit var tvGps: TextView
    private lateinit var gpsRipple: View
    private lateinit var gpsDot: View
    private lateinit var ivCar: SheenImageView
    private lateinit var flare1: View
    private lateinit var flare2: View
    private lateinit var speedBox: View
    private lateinit var tvSpeed: TextView
    private lateinit var tvLimit: TextView
    private lateinit var tvTripKm: TextView
    private lateinit var tvTripTime: TextView
    private lateinit var tvTripAvg: TextView
    private lateinit var driveBox: View
    private lateinit var navBox: View
    private lateinit var tvNavApp: TextView
    private lateinit var navSpeedPill: View
    private lateinit var tvNavSpeed: TextView
    private lateinit var ivTurn: ImageView
    private lateinit var tvNavDist: TextView
    private lateinit var tvNavManeuver: TextView
    private lateinit var tvNavStreet: TextView
    private lateinit var navNextRow: View
    private lateinit var tvNavNext: TextView
    private lateinit var navRoute: ProgressLine
    private lateinit var navStats: LinearLayout
    private lateinit var ivWx: ImageView
    private lateinit var tvTemp: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvRange: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvWind: TextView
    private lateinit var btnSeeAll: TextView
    private lateinit var gvOil: GaugeView
    private lateinit var gvGear: GaugeView
    private lateinit var tvSource: TextView
    private lateinit var ivArt: ImageView
    private lateinit var eq: EqualizerView
    private lateinit var tvTrack: TextView
    private lateinit var tvArtist: TextView
    private lateinit var pbMusic: ProgressLine
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView
    private lateinit var btnPlay: ImageView
    private lateinit var playRing: View
    private var dockNav: View? = null
    private var dockNight: View? = null

    // ---- estado ----
    var currentScreen: String? = null
    var maintSel: String? = null      // ítem elegido en Mantenimiento ("km:oil", "date:ins"...)
    private var askedPerm = false
    private var wxFromGps = false
    private var lastLight: Boolean? = null
    private var lastSunDark: Boolean? = null
    private var lastNav: NavInfo? = null
    private var lastArt: Bitmap? = null
    private var artIsFallback = false
    private var playing = false
    private var gaugesDrawn = false

    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        runOnUiThread {
            if (key == "lat" && !wxFromGps) { wxFromGps = true; Thread { fetchWeather() }.start() }
            if (key != null && key != "lat" && key != "lon" && key != "accM" && key != "lastMove") refreshTexts(false)
        }
    }

    /* =========================================================================================
     *  Ciclo de vida
     * ========================================================================================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        sp = getSharedPreferences("cl", MODE_PRIVATE)
        migrate()
        dark = sp.getBoolean("dark", true)
        setTheme(if (dark) R.style.Theme_Car_Dark else R.style.Theme_Car_Light)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<ScaleLayout>(R.id.scaler).designH = designHeight()
        pal = Pal(this)
        bindHome()
        applyScale()
        setupPages()
        showCachedWeather()
        sp.registerOnSharedPreferenceChangeListener(spListener)

        val restore = savedInstanceState?.getString("screen")
        if (restore != null) showScreen(restore, false)
        if (savedInstanceState == null && sp.getBoolean("splash", true)) showSplash() else introAnim(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("screen", currentScreen)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Tocar Inicio estando en el launcher vuelve a la pantalla principal
        if (currentScreen != null) closeScreen()
        if (intent?.getBooleanExtra("ign", false) == true && sp.getBoolean("splash", true)) showSplash()
    }

    override fun onResume() {
        super.onResume()
        refreshTexts(false)
        ensureTrip()
        loadCar()
        h.removeCallbacks(weatherLoop); weatherLoop.run()
        h.removeCallbacks(uiLoop); uiLoop.run()
        if (currentScreen == "settings") reloadScreen() // por si se dio un permiso en Android
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(weatherLoop)
        h.removeCallbacks(uiLoop)
        findViewById<HoloVideoView>(R.id.holoHome).stop()
    }

    override fun onDestroy() {
        sp.unregisterOnSharedPreferenceChangeListener(spListener)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen != null) closeScreen() // un launcher no sale con Atrás
    }

    /** Pasa ajustes de la versión anterior al formato nuevo. */
    private fun migrate() {
        if (!sp.contains("nightMode")) {
            sp.edit().putInt("nightMode", if (sp.getBoolean("autoLights", false)) 1 else 0).apply()
        }
    }

    fun tv(id: Int): TextView = findViewById(id)

    /* =========================================================================================
     *  Armado del inicio
     * ========================================================================================= */

    private fun bindHome() {
        homeView = findViewById(R.id.home)
        screenHost = findViewById(R.id.screenHost)
        splash = findViewById(R.id.splash)

        tvHello = tv(R.id.tvHello); tvGreet = tv(R.id.tvGreet); tvGps = tv(R.id.tvGps)
        gpsRipple = findViewById(R.id.gpsRipple); gpsDot = findViewById(R.id.gpsDot)
        ivCar = findViewById(R.id.ivCar); flare1 = findViewById(R.id.flare1); flare2 = findViewById(R.id.flare2)
        speedBox = findViewById(R.id.speedBox); tvSpeed = tv(R.id.tvSpeed); tvLimit = tv(R.id.tvLimit)
        tvTripKm = tv(R.id.tvTripKm); tvTripTime = tv(R.id.tvTripTime); tvTripAvg = tv(R.id.tvTripAvg)
        driveBox = findViewById(R.id.driveBox); navBox = findViewById(R.id.navBox)
        tvNavApp = tv(R.id.tvNavApp); navSpeedPill = findViewById(R.id.navSpeedPill); tvNavSpeed = tv(R.id.tvNavSpeed)
        ivTurn = findViewById(R.id.ivTurn); tvNavDist = tv(R.id.tvNavDist); tvNavManeuver = tv(R.id.tvNavManeuver)
        tvNavStreet = tv(R.id.tvNavStreet); navNextRow = findViewById(R.id.navNextRow); tvNavNext = tv(R.id.tvNavNext)
        navRoute = findViewById(R.id.navRoute); navStats = findViewById(R.id.navStats)
        ivWx = findViewById(R.id.ivWx); tvTemp = tv(R.id.tvTemp); tvDesc = tv(R.id.tvDesc); tvRange = tv(R.id.tvRange)
        tvHum = tv(R.id.tvHum); tvWind = tv(R.id.tvWind)
        btnSeeAll = tv(R.id.btnSeeAll)
        gvOil = findViewById(R.id.gvOil); gvGear = findViewById(R.id.gvGear)
        tvSource = tv(R.id.tvSource); ivArt = findViewById(R.id.ivArt); eq = findViewById(R.id.eq)
        tvTrack = tv(R.id.tvTrack); tvArtist = tv(R.id.tvArtist)
        pbMusic = findViewById(R.id.pbMusic); tvPos = tv(R.id.tvPos); tvDur = tv(R.id.tvDur)
        btnPlay = findViewById(R.id.btnPlay); playRing = findViewById(R.id.playRing)

        // Fondos y colores que no se pueden poner en XML
        homeView.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.5f, 0.3f)
            gradientRadius = dp(620f)
            colors = intArrayOf(pal.glow, withAlpha(pal.glow, 0))
        }
        findViewById<View>(R.id.carGlow).background = radialGlow(pal.glow, dp(145f))
        val flareColor = withAlpha(pal.accent, if (dark) 0xB3 else 0x59)
        flare1.background = radialGlow(flareColor, dp(17f))
        flare2.background = radialGlow(flareColor, dp(17f))
        findViewById<RoadView>(R.id.road).setLineColor(pal.line)
        gvOil.setColors(pal.track, pal.tick); gvGear.setColors(pal.track, pal.tick)
        pbMusic.setColors(pal.track, pal.accent)
        navRoute.dot = true; navRoute.barHeightDp = 6f; navRoute.setColors(pal.track, pal.accent)
        eq.setBarColor(0xFFFFFFFF.toInt())
        ivArt.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(24f)) }
        }
        ivArt.clipToOutline = true
        tvTrack.isSelected = true
        iconEnd(btnSeeAll, R.drawable.ic_chevron, pal.accent)
        iconStart(tvHum, R.drawable.ic_drop, pal.muted, 16)
        iconStart(tvWind, R.drawable.ic_wind, pal.muted, 16)

        // Animaciones permanentes
        Anim.bob(findViewById(R.id.carFloat), dp(1.5f), 1500)
        Anim.glow(findViewById(R.id.carGlow), 4000)
        Anim.glow(flare1, 2400); Anim.glow(flare2, 2400)
        Anim.blink(findViewById(R.id.tvColon))
        Anim.bob(ivWx, dp(3f), 2000)

        // Toques
        tvHello.setOnLongClickListener { askText("Tu nombre", "name"); true }
        findViewById<View>(R.id.carBox).setOnLongClickListener { carMenu(); true }
        findViewById<View>(R.id.gOil).setOnClickListener { kmDialog(Maint.BASE_KM[0]) { refreshTexts(false) } }
        findViewById<View>(R.id.gGear).setOnClickListener { kmDialog(Maint.BASE_KM[1]) { refreshTexts(false) } }
        btnSeeAll.setOnClickListener { showScreen("maint", true) }
        tvTrack.setOnClickListener {
            if (!listenerEnabled()) openNotifAccess()
            else controller()?.packageName?.let { launch(it) }
        }
        findViewById<View>(R.id.btnPrev).setOnClickListener { prev() }
        btnPlay.setOnClickListener { playPause() }
        findViewById<View>(R.id.btnNext).setOnClickListener { next() }
        findViewById<View>(R.id.btnBackToMap).setOnClickListener { backToMap() }
        navBox.setOnClickListener { backToMap() }
        splash.setOnClickListener { hideSplash() }

        buildDock()
    }

    fun iconStart(t: TextView, res: Int, color: Int, sizeDp: Int = 22) {
        val d = getDrawable(res)?.mutate() ?: return
        d.setTint(color); d.setBounds(0, 0, dpi(sizeDp), dpi(sizeDp))
        t.setCompoundDrawablesRelative(d, null, null, null); t.compoundDrawablePadding = dpi(8)
    }

    fun iconEnd(t: TextView, res: Int, color: Int, sizeDp: Int = 18) {
        val d = getDrawable(res)?.mutate() ?: return
        d.setTint(color); d.setBounds(0, 0, dpi(sizeDp), dpi(sizeDp))
        t.setCompoundDrawablesRelative(null, null, d, null); t.compoundDrawablePadding = dpi(4)
    }

    private fun buildDock() {
        val dock = findViewById<LinearLayout>(R.id.dock)
        dock.removeAllViews()
        fun item(iconRes: Int, label: String, click: () -> Unit, long: (() -> Unit)?): FrameLayout {
            // Ícono arriba y texto abajo, los dos centrados en el mismo eje
            val b = FrameLayout(this)
            b.layoutParams = lp(0, MATCH, 1f).apply { setMargins(dpi(4), dpi(6), dpi(4), dpi(6)) }
            b.isClickable = true; b.isFocusable = true
            b.contentDescription = label
            b.addView(icon(iconRes, 28, pal.text).apply {
                layoutParams = FrameLayout.LayoutParams(dpi(28), dpi(28), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpi(8) }
            })
            b.addView(text(label, 15f, pal.muted).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dpi(6) }
            })
            b.setOnClickListener { click() }
            if (long != null) b.setOnLongClickListener { long(); true }
            dock.addView(b)
            return b
        }
        for (s in SLOTS) {
            val v = item(s.icon, s.label, { openSlot(s) }, { pickApp { p -> setSlot(s, p) } })
            if (s.key == "pkgNav") dockNav = v
        }
        item(R.drawable.ic_bt, "Bluetooth", { safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, null)
        dockNight = item(R.drawable.ic_moon, "Modo noche", { toggleNightManual() }, { showScreen("settings", true) })
        dock.addView(divider(pal.line, true).apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(dpi(6), dpi(18), dpi(6), dpi(18))
        })
        item(R.drawable.ic_apps, "Apps", { showScreen("apps", true) }, null)
        item(R.drawable.ic_settings, "Ajustes", { showScreen("settings", true) }, null)
        setDockActive(dockNight, dark)
    }

    private fun setDockActive(v: View?, on: Boolean) {
        val b = v as? ViewGroup ?: return
        b.background = if (on) rounded(pal.pill, 20f) else null
        val c = if (on) pal.accent else pal.text
        (b.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(c)
        (b.getChildAt(1) as? TextView)?.setTextColor(if (on) pal.accent else pal.muted)
    }

    /* =========================================================================================
     *  Bienvenida y entrada
     * ========================================================================================= */

    private val splashSeq = ArrayList<Runnable>()
    private fun later(ms: Long, r: () -> Unit) { val x = Runnable(r); splashSeq.add(x); h.postDelayed(x, ms) }

    /** Bienvenida: el holograma se arma, saludo tipeado y chequeo rápido del auto. */
    private fun showSplash() {
        splashSeq.forEach { h.removeCallbacks(it) }; splashSeq.clear()
        splash.visibility = View.VISIBLE
        splash.alpha = 1f
        findViewById<HoloVideoView>(R.id.holoHome).stop()
        val name = sp.getString("name", "conductor") ?: "conductor"
        val hello = "Hola, $name"
        val tvHello = findViewById<TextView>(R.id.splashHello)
        val tvSub = findViewById<TextView>(R.id.splashSub)
        val kicker = findViewById<View>(R.id.splashKicker)
        val checks = findViewById<LinearLayout>(R.id.splashChecks)
        val bar = findViewById<ProgressLine>(R.id.splashBar)
        val accent = 0xFF6FB1FF.toInt(); val warn = 0xFFFF7A59.toInt()
        bar.setColors(0xFF272D36.toInt(), accent); bar.fraction = 0f
        tvSub.text = "${greeting()} · ¡Buen viaje!"

        // Chequeo rápido con datos reales
        checks.removeAllViews()
        val oil = Maint.rem(sp, Maint.BASE_KM[0])
        val tires = Maint.rem(sp, Maint.BASE_KM[3])
        val docs = Maint.dateItems(sp).count { val d = Maint.date(sp, it); d > 0 && Maint.daysUntil(d) <= Maint.WARN_DAYS }
        val items = listOf(
            if (oil in 0..Maint.WARN_KM) "Aceite de motor: cambiar pronto" to true else "Aceite de motor en orden" to false,
            if (tires in 0..Maint.WARN_KM) "Neumáticos: rotación pendiente" to true else "Neumáticos en orden" to false,
            if (docs > 0) "Documentos: $docs por vencer" to true else "Documentos al día" to false
        )
        val rows = items.map { (txt, bad) ->
            val r = hbox()
            r.layoutParams = lp(WRAP, dpi(34))
            val dot = View(this).apply { background = oval(if (bad) warn else accent); layoutParams = lp(dpi(10), dpi(10)) }
            r.addView(dot)
            r.addView(text(txt, 19f, if (bad) warn else 0xFFC9D0D8.toInt(), Fonts.medium(this)).apply { setPadding(dpi(12), 0, 0, 0) })
            r.alpha = 0f
            checks.addView(r)
            r
        }

        val video = findViewById<HoloVideoView>(R.id.splashVideo)
        if (!Anim.enabled(splash)) {
            tvHello.text = hello; rows.forEach { it.alpha = 1f }; bar.fraction = 1f
            video.play(R.raw.holo_intro, 0, null)
            later(2500) { hideSplash() }
            return
        }
        video.play(R.raw.holo_intro, 0, null)
        tvHello.text = ""
        kicker.alpha = 0f; tvSub.alpha = 0f
        later(1100) { kicker.animate().alpha(1f).setDuration(500).start() }
        // saludo tipeado con cursor
        hello.forEachIndexed { i, _ ->
            later(1400L + i * 55L) { tvHello.text = hello.substring(0, i + 1) + if (i < hello.length - 1) "▍" else "" }
        }
        val afterType = 1400L + hello.length * 55L
        later(afterType + 100) { Anim.rise(tvSub, 0, dp(14f)) }
        rows.forEachIndexed { i, r ->
            later(afterType + 450 + i * 260L) {
                r.translationX = -dp(16f)
                r.animate().alpha(1f).translationX(0f).setDuration(420).setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }
        later(1200) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3400
                addUpdateListener { bar.fraction = it.animatedValue as Float }
                start()
            }
        }
        later(5000) { hideSplash() }
    }

    private fun hideSplash() {
        if (splash.visibility != View.VISIBLE) return
        splashSeq.forEach { h.removeCallbacks(it) }; splashSeq.clear()
        splash.animate().alpha(0f).setDuration(450).withEndAction {
            findViewById<HoloVideoView>(R.id.splashVideo).stop()
            splash.visibility = View.GONE
            loadCar()
            introAnim(true)
        }.start()
    }

    private fun introAnim(animate: Boolean) {
        if (animate) {
            val dy = dp(18f)
            Anim.rise(findViewById(R.id.cardCar), 0, dy)
            Anim.rise(findViewById(R.id.clockBox), 100, dy)
            Anim.rise(findViewById(R.id.cardWeather), 150, dy)
            Anim.rise(findViewById(R.id.cardMaint), 200, dy)
            Anim.rise(findViewById(R.id.cardMedia), 300, dy)
            Anim.rise(findViewById(R.id.dock), 400, dy)
        }
        gaugesDrawn = false
        refreshTexts(true)
    }

    /* =========================================================================================
     *  Pantallas secundarias
     * ========================================================================================= */

    fun showScreen(name: String, animate: Boolean) {
        val v: View = when (name) {
            "maint" -> MaintScreen(this).build()
            "apps" -> AppsScreen(this).build()
            "settings" -> SettingsScreen(this).build()
            else -> return
        }
        screenHost.removeAllViews()
        screenHost.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        screenHost.visibility = View.VISIBLE
        homeView.visibility = View.INVISIBLE
        currentScreen = name
        findViewById<HoloVideoView>(R.id.holoHome).stop() // un solo video a la vez
        if (animate && Anim.enabled(v)) {
            v.alpha = 0f; v.translationY = dp(18f)
            v.animate().alpha(1f).translationY(0f).setDuration(350).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    fun closeScreen() {
        screenHost.removeAllViews()
        screenHost.visibility = View.GONE
        homeView.visibility = View.VISIBLE
        currentScreen = null
        refreshTexts(false)
        loadCar()
    }

    /** Vuelve a armar la pantalla abierta (después de cambiar algo en un diálogo). */
    fun reloadScreen() { currentScreen?.let { showScreen(it, false) } }

    /* =========================================================================================
     *  Textos y medidores
     * ========================================================================================= */

    fun greeting(): String {
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when { hr < 12 -> "Buenos días"; hr < 19 -> "Buenas tardes"; else -> "Buenas noches" }
    }

    fun refreshTexts(animateGauges: Boolean) {
        tvHello.text = "Hola, " + sp.getString("name", "conductor")
        tvGreet.text = greeting()
        updateDate()
        val anim = animateGauges || !gaugesDrawn
        renderGauge(Maint.BASE_KM[0], gvOil, tv(R.id.tvOilKm), tv(R.id.tvOilSub), findViewById(R.id.gOilRing), anim)
        renderGauge(Maint.BASE_KM[1], gvGear, tv(R.id.tvGearKm), tv(R.id.tvGearSub), findViewById(R.id.gGearRing), anim)
        gaugesDrawn = true
        val n = Maint.warnings(sp, setOf("oil", "gear"))
        if (n > 0) {
            btnSeeAll.text = if (n == 1) "1 aviso" else "$n avisos"
            btnSeeAll.setTextColor(pal.warn); iconEnd(btnSeeAll, R.drawable.ic_chevron, pal.warn)
        } else {
            btnSeeAll.text = "Ver todo"
            btnSeeAll.setTextColor(pal.accent); iconEnd(btnSeeAll, R.drawable.ic_chevron, pal.accent)
        }
        updateTrip()
    }

    private fun renderGauge(item: KmItem, g: GaugeView, km: TextView, sub: TextView, ring: View, animate: Boolean) {
        val rem = Maint.rem(sp, item)
        val iv = Maint.interval(sp, item)
        if (rem < 0) {
            g.setValue(0f, pal.accent, false)
            km.text = "—"; km.setTextColor(pal.text)
            sub.text = "Toca para configurar"; sub.setTextColor(pal.muted)
            Anim.stopPulse(ring)
            return
        }
        val warn = Maint.isWarn(sp, item)
        val color = if (warn) pal.warn else pal.accent
        g.setValue(if (iv > 0) rem.toFloat() / iv else 0f, color, animate)
        km.text = fmtKm(rem); km.setTextColor(if (warn) pal.warn else pal.text)
        val nx = Maint.next(sp, item)
        sub.text = when {
            rem == 0 -> "¡Cambiar ahora!"
            warn -> "Cambiar pronto"
            nx >= 0 && Maint.hasOdo(sp) -> "Próximo: ${fmtKm(nx)} km"
            else -> "Cada ${fmtKm(iv)} km"
        }
        sub.setTextColor(if (warn) pal.warn else pal.muted)
        if (warn) Anim.pulse(ring) else Anim.stopPulse(ring)
    }

    private val dateFmt = java.text.SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es"))

    /** Fecha siempre en español, aunque la radio esté en otro idioma. */
    private fun updateDate() {
        val s = dateFmt.format(java.util.Date())
        val t = tv(R.id.tvDate)
        if (t.text.toString() != s) t.text = s
    }

    private fun updateTrip() {
        val m = sp.getFloat("tripM", 0f)
        val start = sp.getLong("tripStart", 0L)
        val lastMove = sp.getLong("lastMove", 0L)
        val moveMs = sp.getLong("tripMoveMs", 0L)
        tvTripKm.text = fmtDec(m / 1000.0) + " km"
        val end = if (System.currentTimeMillis() - lastMove > 20 * 60 * 1000L) lastMove else System.currentTimeMillis()
        val mins = if (start > 0) ((end - start) / 60000L).coerceAtLeast(0) else 0
        tvTripTime.text = if (mins >= 60) "${mins / 60} h ${String.format(Locale.US, "%02d", mins % 60)}" else "$mins min"
        val avg = if (moveMs > 60_000) (m / 1000.0) / (moveMs / 3_600_000.0) else 0.0
        tvTripAvg.text = "${Math.round(avg)} km/h"
    }

    /* =========================================================================================
     *  Bucle de la interfaz (cada 1 s): velocidad, navegación, música, modo noche
     * ========================================================================================= */

    private val uiLoop = object : Runnable {
        override fun run() {
            try {
                checkNight()
                updateSpeed()
                updateNav()
                updateMedia()
                updateMapPage()
                if (SystemClock.elapsedRealtime() / 1000 % 15 == 0L) { updateTrip(); updateDate(); tvGreet.text = greeting() }
            } catch (e: Exception) { }
            h.postDelayed(this, 1000)
        }
    }

    private fun updateSpeed() {
        val fresh = System.currentTimeMillis() - Live.fixAt < 8000 && Live.speedKmh >= 0f
        val s = if (fresh) Math.round(Live.speedKmh) else 0
        val limit = sp.getInt("limit", 80)
        val over = fresh && limit > 0 && s > limit
        tvSpeed.text = if (fresh || Live.fixAt > 0) s.toString() else "--"
        tvSpeed.setTextColor(if (over) pal.warn else pal.text)
        tvNavSpeed.text = tvSpeed.text
        tvNavSpeed.setTextColor(if (over) pal.warn else pal.text)
        if (limit > 0) {
            tvLimit.visibility = View.VISIBLE
            tvLimit.text = limit.toString()
            tvLimit.setTextColor(if (over) pal.warn else pal.muted)
            tvLimit.background = oval(0, if (over) pal.warn else pal.line, 4f)
        } else tvLimit.visibility = View.INVISIBLE
        if (over) { Anim.pulse(speedBox, 0.45f, 600); Anim.pulse(navSpeedPill, 0.45f, 600) }
        else { Anim.stopPulse(speedBox); Anim.stopPulse(navSpeedPill) }

        if (fresh) {
            tvGps.text = "GPS"; gpsDot.alpha = 1f; Anim.ripple(gpsRipple, 2.6f, 2000)
        } else {
            tvGps.text = if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) "Buscando GPS" else "Sin GPS"
            gpsDot.alpha = 0.4f; Anim.stopRipple(gpsRipple)
        }
    }

    private fun updateNav() {
        val n = Live.nav
        setDockActive(dockNav, n != null)
        if (n == null) {
            if (navBox.visibility == View.VISIBLE) { navBox.visibility = View.GONE; driveBox.visibility = View.VISIBLE }
            lastNav = null
            return
        }
        if (navBox.visibility != View.VISIBLE) { navBox.visibility = View.VISIBLE; driveBox.visibility = View.GONE }
        if (n === lastNav) return
        lastNav = n
        tvNavApp.text = "Navegando · ${n.appName}"
        tvNavDist.text = n.dist
        tvNavManeuver.text = n.maneuver
        tvNavManeuver.visibility = if (n.maneuver.isEmpty()) View.GONE else View.VISIBLE
        tvNavStreet.text = n.street
        tvNavNext.text = n.next
        navNextRow.visibility = if (n.next.isEmpty()) View.GONE else View.VISIBLE
        if (n.icon != null) ivTurn.setImageBitmap(n.icon) else ivTurn.setImageResource(R.drawable.ic_turn)
        ivTurn.imageTintList = ColorStateList.valueOf(pal.accent)

        val rem = n.remainingKm; val start = n.startKm
        if (rem != null && start != null && start > 0.05) {
            navRoute.visibility = View.VISIBLE
            navRoute.fraction = (1.0 - rem / start).toFloat()
        } else navRoute.visibility = View.GONE

        navStats.removeAllViews()
        for (c in n.chunks) {
            val col = vbox()
            col.layoutParams = lp(0, WRAP, 1f)
            col.addView(text(c, 22f, pal.text, Fonts.semibold(this)).apply { isSingleLine = true })
            val label = when {
                c.contains(':') || c.contains("ETA", true) || c.contains("llegada", true) -> "Llegada"
                Regex("""\b(min|h|hr|hs)\b""", RegexOption.IGNORE_CASE).containsMatchIn(c) -> "Restante"
                MediaListener.parseKm(c) != null -> "Distancia"
                else -> ""
            }
            if (label.isNotEmpty()) col.addView(text(label, 15f, pal.muted))
            navStats.addView(col)
        }
    }

    private fun backToMap() {
        val n = Live.nav
        try {
            if (n?.intent != null) {
                val opts = cardOptions()
                if (opts != null) n.intent.send(this, 0, null, null, null, null, opts) else n.intent.send()
                return
            }
        } catch (e: Exception) { }
        openMapApp()
    }

    /* ---------------------------------------------------------------------------------------
     *  Maps dentro de la tarjeta
     *  Android no deja meter otra app adentro de un launcher, pero sí abrirla como ventana
     *  ("forma libre") con el tamaño y la posición exactos de la tarjeta del mapa. El launcher
     *  sigue visible y usable alrededor. Necesita que la radio tenga activadas las ventanas de
     *  forma libre (Opciones de desarrollador › "Habilitar ventanas de forma libre").
     * --------------------------------------------------------------------------------------- */

    fun mapInCard() = sp.getBoolean("mapInCard", true)

    fun freeformSupported(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) != 0

    /** Rectángulo de la tarjeta del mapa en coordenadas de pantalla (ya escalado). */
    private fun mapCardRect(): Rect? {
        val v = findViewById<View>(R.id.cardMedia) ?: return null
        val r = Rect()
        if (!v.isShown || !v.getGlobalVisibleRect(r) || r.width() < dpi(120)) return null
        val win = IntArray(2)
        window.decorView.getLocationOnScreen(win)
        r.offset(win[0], win[1])
        return r
    }

    private fun cardOptions(): Bundle? {
        if (!mapInCard() || !freeformSupported()) return null
        val r = mapCardRect() ?: return null
        val o = ActivityOptions.makeBasic().setLaunchBounds(r)
        try {
            // WINDOWING_MODE_FREEFORM = 5 (API oculta, la usan los launchers con ventanas)
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5)
        } catch (e: Throwable) { }
        return o.toBundle()
    }

    /** Abre la app de mapas: en la tarjeta si se puede; si no, a pantalla completa. */
    private fun startMap(i: Intent): Boolean {
        if (mapInCard() && !freeformSupported()) askFreeform()
        return try {
            val opts = cardOptions()
            if (opts != null) startActivity(ownTask(i), opts) else startActivity(ownTask(i))
            true
        } catch (e: Exception) { false }
    }

    private fun openMapApp() {
        val pkg = slotPkg(SLOTS[0])
        val i = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (i == null || !startMap(i)) openSlot(SLOTS[0])
    }

    /** Se muestra una sola vez si la radio no tiene activadas las ventanas de forma libre. */
    private fun askFreeform() {
        if (sp.getBoolean("ffAsked", false)) return
        sp.edit().putBoolean("ffAsked", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Maps dentro de la tarjeta")
            .setMessage("Para que Maps se abra en la tarjeta del mapa sin tapar el launcher, la radio " +
                "tiene que permitir ventanas.\n\n" +
                "1. Ajustes de Android › Opciones de desarrollador\n" +
                "2. Activá \"Habilitar ventanas de forma libre\" (y si aparece, \"Forzar actividades redimensionables\")\n" +
                "3. Reiniciá la radio\n\n" +
                "Mientras tanto Maps se abre a pantalla completa. Podés desactivar esto en Ajustes › Destinos.")
            .setPositiveButton("Abrir opciones") { _, _ ->
                safeStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    /* =========================================================================================
     *  Modo noche: por puesta del sol, por luces del auto o manual
     * ========================================================================================= */

    fun lat(): Double = sp.getString("lat", null)?.toDoubleOrNull() ?: LAT
    fun lon(): Double = sp.getString("lon", null)?.toDoubleOrNull() ?: LON

    private fun checkNight() {
        when (sp.getInt("nightMode", 0)) {
            0 -> {
                val t = Sun.times(lat(), lon()) ?: return
                val now = System.currentTimeMillis()
                val want = now < t.first || now >= t.second
                if (want != lastSunDark) { lastSunDark = want; if (want != dark) applyDark(want) }
            }
            1 -> checkLights()
            else -> { }
        }
    }

    fun applyDark(d: Boolean) {
        if (d == dark) return
        sp.edit().putBoolean("dark", d).commit()
        recreate()
    }

    private fun toggleNightManual() {
        if (sp.getInt("nightMode", 0) != 2) {
            sp.edit().putInt("nightMode", 2).apply()
            Toast.makeText(this, "Modo noche manual. El automático se elige en Ajustes.", Toast.LENGTH_LONG).show()
        }
        sp.edit().putBoolean("dark", !dark).commit()
        recreate()
    }

    /** Lee un ajuste del sistema guardado como "system:nombre", "global:nombre" o "secure:nombre". */
    private fun readSetting(k: String): String? {
        val t = k.substringBefore(":"); val n = k.substringAfter(":")
        return try {
            when (t) {
                "system" -> Settings.System.getString(contentResolver, n)
                "global" -> Settings.Global.getString(contentResolver, n)
                else -> Settings.Secure.getString(contentResolver, n)
            }
        } catch (e: Exception) { null }
    }

    /** Si la señal guardada indica luces encendidas -> oscuro, apagadas -> claro. */
    private fun checkLights() {
        val key = sp.getString("lightKey", null) ?: return
        val cur = readSetting(key) ?: return
        val a = sp.getString("lightA", "") ?: ""   // luces apagadas
        val b = sp.getString("lightB", "") ?: ""   // luces encendidas
        val want: Boolean? = when {
            cur == b -> true
            cur == a -> false
            else -> {
                val c = cur.toDoubleOrNull(); val x = a.toDoubleOrNull(); val y = b.toDoubleOrNull()
                if (c != null && x != null && y != null) Math.abs(c - y) < Math.abs(c - x) else null
            }
        }
        if (want != null && want != lastLight) { lastLight = want; if (want != dark) applyDark(want) }
    }

    private fun snapshot(): Map<String, String> {
        val m = HashMap<String, String>()
        val tables = listOf("system" to Settings.System.CONTENT_URI,
            "global" to Settings.Global.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI)
        for ((p, uri) in tables) {
            try {
                contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) m["$p:${c.getString(0)}"] = c.getString(1) ?: ""
                }
            } catch (e: Exception) { }
        }
        return m
    }

    /** Asistente para detectar qué ajuste del sistema cambia al prender las luces del auto. */
    fun detectLights(onDone: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Paso 1 de 2")
            .setMessage("Con las luces del auto APAGADAS, tocá Listo.")
            .setPositiveButton("Listo") { _, _ ->
                val a = snapshot()
                AlertDialog.Builder(this).setTitle("Paso 2 de 2")
                    .setMessage("Ahora PRENDÉ las luces del auto, esperá 3 segundos y tocá Listo.")
                    .setPositiveButton("Listo") { _, _ -> showDiff(a, snapshot(), onDone) }
                    .setNegativeButton("Cancelar", null).show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showDiff(a: Map<String, String>, b: Map<String, String>, onDone: () -> Unit) {
        val noise = listOf("time", "elapsed", "uptime", "last", "count", "date", "wake", "boot")
        val diffs = b.filter { (k, v) -> a[k] != null && a[k] != v && noise.none { k.lowercase().contains(it) } }
            .toList().take(30)
        if (diffs.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Sin cambios")
                .setMessage("Al prender las luces no cambió ningún ajuste del sistema: la radio baja la pantalla por hardware. Usá el modo automático por la puesta del sol o el manual.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = diffs.map { (k, v) -> "$k: ${a[k]} → $v" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Elegí el que cambió con las luces")
            .setItems(labels) { _, i ->
                val (k, v) = diffs[i]
                sp.edit().putString("lightKey", k).putString("lightA", a[k]).putString("lightB", v)
                    .putInt("nightMode", 1).apply()
                lastLight = null
                Toast.makeText(this, "Listo: modo noche con las luces del auto", Toast.LENGTH_LONG).show()
                onDone()
            }.show()
    }

    /* =========================================================================================
     *  Foto del auto
     * ========================================================================================= */

    // También podés copiar la imagen a: Android/data/com.rgservice.carlauncher13/files/auto.png
    fun carFile() = java.io.File(getExternalFilesDir(null), "auto.png")

    /**
     * Imagen del auto en el inicio:
     *  - foto propia (si la elegiste),
     *  - holograma animado en tema oscuro,
     *  - modelo 3D fijo en tema claro.
     */
    fun loadCar() {
        val holo = findViewById<HoloVideoView>(R.id.holoHome)
        val carFloat = findViewById<View>(R.id.carFloat)
        val glow = findViewById<View>(R.id.carGlow)
        val road = findViewById<View>(R.id.road)
        val f = carFile()
        if (f.exists()) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (o.outWidth / s > 1000) s *= 2          // evita cargar fotos enormes
            val bm = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            if (bm != null) {
                holo.stop(); holo.visibility = View.GONE
                carFloat.visibility = View.VISIBLE; glow.visibility = View.VISIBLE; road.visibility = View.VISIBLE
                ivCar.setImageBitmap(bm); ivCar.sheenOn = false
                flare1.visibility = View.GONE; flare2.visibility = View.GONE
                return
            }
        }
        if (dark) {
            carFloat.visibility = View.GONE; glow.visibility = View.GONE; road.visibility = View.GONE
            holo.visibility = View.VISIBLE
            if (splash.visibility != View.VISIBLE && currentScreen == null) holo.play(0, R.raw.holo_home) else holo.stop()
            return
        }
        holo.stop(); holo.visibility = View.GONE
        carFloat.visibility = View.VISIBLE; glow.visibility = View.VISIBLE; road.visibility = View.VISIBLE
        ivCar.setImageResource(R.drawable.auto3d); ivCar.sheenOn = true
        flare1.visibility = View.VISIBLE; flare2.visibility = View.VISIBLE
    }

    fun carMenu() {
        val hasPhoto = carFile().exists()
        val opts = if (hasPhoto) arrayOf("Elegir otra foto", "Volver al modelo 3D") else arrayOf("Elegir una foto de mi auto")
        AlertDialog.Builder(this).setTitle("Imagen del auto").setItems(opts) { _, w ->
            if (w == 0) pickCarPhoto() else { carFile().delete(); loadCar(); reloadScreen() }
        }.show()
    }

    private fun pickCarPhoto() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            }, 7)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Sin selector de archivos. Copiá la foto como auto.png a: ${carFile().parent}",
                Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req == 7 && res == RESULT_OK && uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { i -> carFile().outputStream().use { o -> i.copyTo(o) } }
                loadCar(); reloadScreen()
            } catch (e: Exception) { Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show() }
        }
    }

    /* =========================================================================================
     *  GPS / recorrido
     * ========================================================================================= */

    private fun ensureTrip() {
        // El servicio corre siempre (también abre el launcher al dar contacto); el GPS solo con permiso.
        try { startForegroundService(Intent(this, TripService::class.java)) } catch (e: Exception) { }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && !askedPerm) {
            askedPerm = true
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        ensureTrip()
    }

    /* =========================================================================================
     *  Diálogos
     * ========================================================================================= */

    fun askText(title: String, key: String) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT; setText(sp.getString(key, "")) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ ->
                val s = et.text.toString().trim()
                if (s.isNotEmpty()) { sp.edit().putString(key, s).apply(); refreshTexts(false); reloadScreen() }
            }.setNegativeButton("Cancelar", null).show()
    }

    fun askInt(title: String, onValue: (Int) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> et.text.toString().trim().toIntOrNull()?.let(onValue) }
            .setNegativeButton("Cancelar", null).show()
    }

    fun askString(title: String, hint: String, onValue: (String) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; this.hint = hint }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> val s = et.text.toString().trim(); if (s.isNotEmpty()) onValue(s) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun setInterval(item: KmItem, v: Int) {
        val ed = sp.edit().putInt(item.id + "Int", v)
        if (Maint.rem(sp, item) < 0) ed.putInt(item.id, v)   // primera vez: arranca completo
        ed.apply()
    }

    /* ---------- Kilometraje con el tablero ---------- */

    /** Si todavía no se cargó el km del tablero, lo pide primero. */
    fun ensureOdo(then: () -> Unit) {
        if (Maint.hasOdo(sp)) { then(); return }
        askNumber("¿Cuántos km marca el tablero?", "Ej.: 182350", null) { km ->
            Maint.setOdo(sp, km); refreshTexts(false); then()
        }
    }

    /** Corrige el km del tablero (todos los ítems se recalculan solos). */
    fun updateOdo(onDone: () -> Unit) {
        askNumber("¿Cuántos km marca el tablero ahora?", "Ej.: 182350", if (Maint.hasOdo(sp)) Maint.odo(sp) else null) { km ->
            Maint.setOdo(sp, km); refreshTexts(false); onDone()
        }
    }

    fun askNumber(title: String, hint: String, initial: Int?, onValue: (Int) -> Unit) {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; this.hint = hint
            if (initial != null) { setText(initial.toString()); setSelection(text.length) }
        }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> et.text.toString().replace(".", "").trim().toIntOrNull()?.let(onValue) }
            .setNegativeButton("Cancelar", null).show()
    }

    fun kmDone(item: KmItem, onDone: () -> Unit) = ensureOdo {
        val odo = Maint.odo(sp); val iv = Maint.interval(sp, item)
        AlertDialog.Builder(this).setTitle("${item.name}: ¿lo cambiaste hoy?")
            .setMessage("Se registra a los ${fmtKm(odo)} km del tablero.\nPróximo cambio: ${fmtKm(odo + iv)} km.")
            .setPositiveButton("Sí, registrar") { _, _ -> Maint.changedAt(sp, item, odo, System.currentTimeMillis()); refreshTexts(false); onDone() }
            .setNegativeButton("Cancelar", null).show()
    }

    fun kmNextAt(item: KmItem, onDone: () -> Unit) = ensureOdo {
        askNumber("${item.name}: próximo cambio a los… km", "El número de la etiqueta del taller", Maint.next(sp, item).takeIf { it >= 0 }) { km ->
            Maint.setNext(sp, item, km); refreshTexts(false); onDone()
        }
    }

    fun kmChangedAt(item: KmItem, onDone: () -> Unit) = ensureOdo {
        askNumber("${item.name}: ¿a cuántos km del tablero lo cambiaste?", "Ej.: 178000", Maint.last(sp, item).takeIf { it >= 0 }) { km ->
            Maint.changedAt(sp, item, km, Maint.lastDate(sp, item).takeIf { it > 0 } ?: -1L); refreshTexts(false); onDone()
        }
    }

    fun kmInterval(item: KmItem, onDone: () -> Unit) {
        val labels = item.presets.map { "Cada ${fmtKm(it)} km" } + "Otro valor…"
        AlertDialog.Builder(this).setTitle("${item.name}: cambio cada…")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < item.presets.size) { Maint.setIntervalKm(sp, item, item.presets[i]); refreshTexts(false); onDone() }
                else askNumber("Intervalo en km", "Ej.: 5000", null) { Maint.setIntervalKm(sp, item, it); refreshTexts(false); onDone() }
            }.show()
    }

    /** Aviso por tiempo (además de los km): lo que llegue primero. */
    fun kmMonths(item: KmItem, onDone: () -> Unit) {
        val opts = arrayOf("Sin aviso por tiempo", "Cada 3 meses", "Cada 6 meses", "Cada 12 meses")
        val vals = intArrayOf(0, 3, 6, 12)
        AlertDialog.Builder(this).setTitle("${item.name}: aviso por tiempo").setItems(opts) { _, i ->
            sp.edit().putInt("mon_" + item.id, vals[i]).apply()
            if (vals[i] > 0 && Maint.lastDate(sp, item) <= 0) {
                val cal = java.util.Calendar.getInstance()
                android.app.DatePickerDialog(this, { _, y, m, d ->
                    val c = java.util.Calendar.getInstance(); c.clear(); c.set(y, m, d, 12, 0, 0)
                    sp.edit().putLong("lastd_" + item.id, c.timeInMillis).apply(); refreshTexts(false); onDone()
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                    setTitle("¿Cuándo fue el último cambio?")
                }.show()
            } else { refreshTexts(false); onDone() }
        }.show()
    }

    /** Menú de un ítem por km (desde el inicio o el botón "Más"). */
    fun kmDialog(item: KmItem, onDone: () -> Unit) {
        val iv = Maint.interval(sp, item)
        val mon = Maint.months(sp, item)
        val opts = arrayListOf(
            "✔ Lo cambié hoy",
            "Próximo cambio a los… km (etiqueta del taller)",
            "Lo cambié a los… km",
            "Intervalo: cada ${fmtKm(iv)} km",
            if (mon > 0) "Aviso por tiempo: cada $mon meses" else "Aviso por tiempo: no",
            "Actualizar km del tablero")
        if (item.custom) opts.add("Eliminar")
        AlertDialog.Builder(this).setTitle(item.name).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> kmDone(item, onDone)
                1 -> kmNextAt(item, onDone)
                2 -> kmChangedAt(item, onDone)
                3 -> kmInterval(item, onDone)
                4 -> kmMonths(item, onDone)
                5 -> updateOdo(onDone)
                6 -> { Maint.removeCustom(sp, "customKm", item.id); onDone() }
            }
        }.show()
    }

    /* =========================================================================================
     *  Apps
     * ========================================================================================= */

    fun launchableApps(): List<ResolveInfo> {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        return pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    fun pickApp(onPick: (String) -> Unit) {
        val pm = packageManager
        val apps = launchableApps()
        val gv = GridView(this).apply { numColumns = 6; setPadding(dpi(8), dpi(8), dpi(8), dpi(8)) }
        gv.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val ri = apps[i]
                return TextView(this@MainActivity).apply {
                    text = ri.loadLabel(pm); isSingleLine = true; gravity = Gravity.CENTER
                    val d = ri.loadIcon(pm); d.setBounds(0, 0, dpi(56), dpi(56))
                    setCompoundDrawables(null, d, null, null)
                    setPadding(4, 12, 4, 12)
                }
            }
        }
        val dlg = AlertDialog.Builder(this).setView(gv).create()
        gv.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            dlg.dismiss(); onPick(apps[i].activityInfo.packageName)
        }
        dlg.show()
    }

    fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) Toast.makeText(this, "App no disponible: $pkg", Toast.LENGTH_SHORT).show()
        else safeStart(i)
    }

    /**
     * Las apps se abren en su propia tarea, como hace cualquier launcher. Sin esto Google Maps
     * arrancaba dentro de la tarea del launcher: al pasar a la navegación el launcher volvía a
     * quedar arriba y Maps se achicaba a la ventanita flotante.
     */
    private fun ownTask(i: Intent): Intent = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun safeStart(i: Intent) {
        try { startActivity(ownTask(i)) } catch (e: Exception) { Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show() }
    }

    fun slotPkg(s: Slot): String? = sp.getString(s.key, s.def)

    fun setSlot(s: Slot, pkg: String) {
        sp.edit().putString(s.key, pkg).apply()
        Toast.makeText(this, "${appLabel(pkg)} fijada en ${s.label}", Toast.LENGTH_SHORT).show()
        reloadScreen()
    }

    fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /** Toque: abre la app guardada (o te deja elegirla la primera vez). */
    fun openSlot(s: Slot) {
        val pkg = slotPkg(s)
        if (pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null) { launch(pkg); return }
        if (s.key == "pkgPhone") {
            try { startActivity(ownTask(Intent(Intent.ACTION_DIAL))); return } catch (e: Exception) { }
        }
        pickApp { p -> sp.edit().putString(s.key, p).apply(); launch(p) }
    }

    /* =========================================================================================
     *  Música
     * ========================================================================================= */

    fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    fun openNotifAccess() = safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun controller(): MediaController? = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: list.firstOrNull()
    } catch (e: SecurityException) { null }

    /* ---------------------------------------------------------------------------------------
     *  Control de música
     *  Algunas radios publican una sesión de música "vacía" (sin título ni estado, típico de la
     *  app de Bluetooth) que ignora play/pausa/siguiente. En ese caso se usan las teclas
     *  multimedia del sistema, igual que los botones del volante.
     *  Ajustes › Música › Control: 0 = Automático, 1 = Sesión de música, 2 = Teclas multimedia
     * --------------------------------------------------------------------------------------- */

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun mediaMode() = sp.getInt("mediaMode", 0)
    private fun keyPkgs(): Set<String> = sp.getStringSet("mediaKeyPkgs", emptySet()) ?: emptySet()
    private fun rememberKeys(pkg: String) {
        sp.edit().putStringSet("mediaKeyPkgs", keyPkgs() + pkg).apply()
    }

    private fun titleOf(c: MediaController): String? =
        c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }

    /** true si la sesión informa bien lo que pasa y vale la pena mandarle órdenes. */
    private fun sessionWorks(c: MediaController): Boolean {
        when (mediaMode()) { 1 -> return true; 2 -> return false }
        if (c.packageName in keyPkgs()) return false
        val st = c.playbackState?.state ?: return false
        if (st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_ERROR) return false
        if (titleOf(c) == null) return false
        // Suena música pero la sesión dice que no está reproduciendo: no es la que manda
        if (audio().isMusicActive && st != PlaybackState.STATE_PLAYING && st != PlaybackState.STATE_BUFFERING) return false
        return true
    }

    private fun key(code: Int) {
        val am = audio()
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun refreshMediaSoon() {
        h.postDelayed({ try { updateMedia() } catch (e: Exception) { } }, 350)
        h.postDelayed({ try { updateMedia() } catch (e: Exception) { } }, 1200)
    }

    private fun prev() = skip(false)
    private fun next() = skip(true)

    private fun skip(forward: Boolean) {
        val code = if (forward) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS
        val c = controller()
        if (c == null || !sessionWorks(c)) { key(code); refreshMediaSoon(); return }
        val before = titleOf(c)
        if (forward) c.transportControls.skipToNext() else c.transportControls.skipToPrevious()
        refreshMediaSoon()
        // Si en 1,5 s no cambió el tema, la sesión no obedece: se usa la tecla y se recuerda
        if (mediaMode() == 0) h.postDelayed({
            try {
                val now = controller()
                if (now != null && now.packageName == c.packageName && titleOf(now) == before) {
                    rememberKeys(c.packageName); key(code); refreshMediaSoon()
                }
            } catch (e: Exception) { }
        }, 1500)
    }

    private fun playPause() {
        val c = controller()
        if (c == null || !sessionWorks(c)) {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            playing = !playing; renderPlay()
            refreshMediaSoon(); return
        }
        val wasPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (wasPlaying) c.transportControls.pause() else c.transportControls.play()
        playing = !wasPlaying; renderPlay()
        refreshMediaSoon()
        // Si en 1 s el estado no cambió, la sesión no obedece: se usa la tecla y se recuerda
        if (mediaMode() == 0) h.postDelayed({
            try {
                val now = controller()
                val isPlaying = now?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (now != null && now.packageName == c.packageName && isPlaying == wasPlaying) {
                    rememberKeys(c.packageName); key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); refreshMediaSoon()
                }
            } catch (e: Exception) { }
        }, 1000)
    }

    private fun renderPlay() {
        eq.playing = playing
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (playing) Anim.ripple(playRing) else Anim.stopRipple(playRing)
    }

    private fun mmss(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "${s / 60}:${String.format(Locale.US, "%02d", s % 60)}"
    }

    private fun fallbackArt(): LayerDrawable {
        val base = GradientDrawable().apply { setColor(pal.art3); cornerRadius = dp(24f) }
        val a = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; setGradientCenter(0.25f, 0.2f)
            gradientRadius = dp(150f); colors = intArrayOf(pal.art1, withAlpha(pal.art1, 0)); cornerRadius = dp(24f)
        }
        val b = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; setGradientCenter(0.85f, 0.9f)
            gradientRadius = dp(150f); colors = intArrayOf(pal.art2, withAlpha(pal.art2, 0)); cornerRadius = dp(24f)
        }
        return LayerDrawable(arrayOf(base, a, b))
    }

    private fun updateMedia() {
        val c = controller()
        var art: Bitmap? = null
        val perm = listenerEnabled()
        findViewById<View>(R.id.btnMusicPerm).visibility = if (perm) View.GONE else View.VISIBLE
        if (c == null) {
            if (page == 0) tvSource.text = "Música"
            tvTrack.text = if (perm) "Sin reproducción" else "Sin permiso de música"
            tvArtist.text = if (perm) "Poné música en el celular o en la radio" else ""
            playing = audio().isMusicActive
            pbMusic.fraction = 0f; tvPos.text = ""; tvDur.text = ""
        } else {
            if (page == 0) tvSource.text = appLabel(c.packageName)
            val md = c.metadata
            val title = titleOf(c) ?: (if (audio().isMusicActive) "Reproduciendo" else "Música")
            if (tvTrack.text.toString() != title) tvTrack.text = title
            tvArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val ps = c.playbackState
            playing = if (sessionWorks(c)) ps?.state == PlaybackState.STATE_PLAYING else audio().isMusicActive
            val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (ps != null && dur > 0) {
                var pos = ps.position
                if (playing && ps.lastPositionUpdateTime > 0) {
                    pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
                }
                pos = pos.coerceIn(0L, dur)
                pbMusic.fraction = pos.toFloat() / dur
                tvPos.text = mmss(pos); tvDur.text = mmss(dur)
            } else { pbMusic.fraction = 0f; tvPos.text = ""; tvDur.text = "" }
        }
        if (art != null) {
            if (art !== lastArt) { ivArt.setImageBitmap(art); lastArt = art; artIsFallback = false }
        } else if (!artIsFallback) {
            ivArt.setImageDrawable(fallbackArt()); lastArt = null; artIsFallback = true
        }
        renderPlay()
    }

    /* =========================================================================================
     *  Clima (Open-Meteo, sin API key)
     * ========================================================================================= */

    private val weatherLoop = object : Runnable {
        override fun run() {
            Thread { fetchWeather() }.start()
            h.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun fetchWeather() {
        try {
            val u = "https://api.open-meteo.com/v1/forecast?latitude=${lat()}&longitude=${lon()}" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val cur = j.getJSONObject("current")
            val d = j.getJSONObject("daily")
            val code = cur.getInt("weather_code")
            val temp = Math.round(cur.getDouble("temperature_2m")).toString() + "°"
            val desc = weatherDesc(code)
            val range = "Máx ${Math.round(d.getJSONArray("temperature_2m_max").getDouble(0))}° · " +
                "Mín ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(0))}°"
            val hum = "${cur.getInt("relative_humidity_2m")}%"
            val wind = "${Math.round(cur.getDouble("wind_speed_10m"))} km/h"
            sp.edit().putString("wxLine", "$temp $desc")
                .putString("wxCache", JSONObject().put("temp", temp).put("desc", desc).put("range", range)
                    .put("hum", hum).put("wind", wind).put("code", code).put("at", System.currentTimeMillis()).toString())
                .apply()
            runOnUiThread {
                tvTemp.text = temp; tvDesc.text = desc; tvRange.text = range
                tvHum.text = hum; tvWind.text = wind
                ivWx.setImageResource(weatherIcon(code))
                tv(R.id.tvWxInline).text = "$temp · $desc"
            }
        } catch (e: Exception) {
            runOnUiThread { showCachedWeather() }
        }
    }

    private fun weatherDesc(c: Int) = when {
        c == 0 -> "Despejado"
        c <= 2 -> "Parcialmente nublado"
        c == 3 -> "Nublado"
        c == 45 || c == 48 -> "Niebla"
        c in 51..67 -> "Lluvia"
        c in 71..77 -> "Nieve"
        c in 80..82 -> "Chubascos"
        c >= 95 -> "Tormenta"
        else -> "Variable"
    }

    private fun weatherIcon(c: Int) = when {
        c == 0 -> if (dark) R.drawable.ic_moon else R.drawable.ic_sun
        c <= 2 -> R.drawable.ic_cloud_sun
        c == 3 -> R.drawable.ic_cloud
        c == 45 || c == 48 -> R.drawable.ic_fog
        c in 51..67 || c in 80..82 -> R.drawable.ic_rain
        c in 71..77 -> R.drawable.ic_snow
        c >= 95 -> R.drawable.ic_storm
        else -> R.drawable.ic_cloud_sun
    }

    /** Permiso para abrir el launcher sobre otras apps al dar contacto (algunas radios lo exigen). */
    fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) return
        AlertDialog.Builder(this).setTitle("Permitir abrir al dar contacto")
            .setMessage("Para que el launcher se abra solo al encender la radio, Android pide el permiso \"Mostrar sobre otras apps\". Activalo para Car Launcher en la pantalla que se abre.")
            .setPositiveButton("Abrir ajuste") { _, _ ->
                safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }.setNegativeButton("Ahora no", null).show()
    }

    /* =========================================================================================
     *  Clima sin conexión: muestra el último que se descargó
     * ========================================================================================= */

    private fun showCachedWeather() {
        val s = sp.getString("wxCache", null) ?: run { if (tvTemp.text == "--°") tvDesc.text = "Sin conexión"; return }
        try {
            val o = JSONObject(s)
            val mins = (System.currentTimeMillis() - o.getLong("at")) / 60000
            val ago = when { mins < 60 -> "hace $mins min"; mins < 48 * 60 -> "hace ${mins / 60} h"; else -> "hace ${mins / 1440} días" }
            tvTemp.text = o.getString("temp")
            tvDesc.text = "${o.getString("desc")} · $ago"
            tvRange.text = o.getString("range")
            tvHum.text = o.getString("hum"); tvWind.text = o.getString("wind")
            ivWx.setImageResource(weatherIcon(o.getInt("code")))
            tv(R.id.tvWxInline).text = "${o.getString("temp")} · ${o.getString("desc")}"
        } catch (e: Exception) { }
    }

    /* =========================================================================================
     *  Tamaño de pantalla (Ajustes › Pantalla › Tamaño)
     * ========================================================================================= */

    fun scaleLevel(): Int = sp.getInt("scale", 0).coerceIn(0, 2)
    private fun designHeight(): Float = when (scaleLevel()) { 1 -> 640f; 2 -> 580f; else -> 720f }

    /** Reacomoda el inicio según el tamaño elegido para que todo entre en la pantalla. */
    private fun applyScale() {
        val lvl = scaleLevel()
        // Grande y Muy grande: el clima pasa a la línea de la fecha
        findViewById<View>(R.id.cardWeather).visibility = if (lvl >= 1) View.GONE else View.VISIBLE
        tv(R.id.tvWxInline).visibility = if (lvl >= 1) View.VISIBLE else View.GONE
        if (lvl == 2) {
            // Muy grande: sin dibujo del auto, medidores y carátula más chicos
            findViewById<View>(R.id.carBox).visibility = View.GONE
            findViewById<View>(R.id.road).visibility = View.GONE
            for (id in intArrayOf(R.id.gOilRing, R.id.gGearRing)) {
                val v = findViewById<View>(id); v.layoutParams = v.layoutParams.apply { width = dpi(124); height = dpi(124) }
            }
            tv(R.id.tvOilKm).textSize = 24f; tv(R.id.tvGearKm).textSize = 24f
            val art = findViewById<View>(R.id.artBox); art.layoutParams = art.layoutParams.apply { width = dpi(130); height = dpi(130) }
        }
        // El cuadro del auto se adapta al ancho real de la columna (16:9)
        val card = findViewById<View>(R.id.cardCar)
        card.post {
            val inner = card.width - card.paddingLeft - card.paddingRight
            val box = findViewById<View>(R.id.carBox)
            if (inner > 0 && box.visibility != View.GONE) {
                val w = Math.min(inner, dpi(316))
                box.layoutParams = box.layoutParams.apply { width = w; height = w * 178 / 316 }
                val fl = findViewById<View>(R.id.carFloat)
                fl.layoutParams = fl.layoutParams.apply { width = w * 290 / 316; height = w * 141 / 316 }
                for (id in intArrayOf(R.id.flare1, R.id.flare2)) {
                    val f = findViewById<View>(id)
                    (f.layoutParams as FrameLayout.LayoutParams).apply {
                        marginStart = marginStart * w / 316; topMargin = topMargin * w / 316
                    }
                }
                findViewById<View>(R.id.road).layoutParams = findViewById<View>(R.id.road).layoutParams.apply { width = w * 290 / 316 }
            }
        }
    }

    /* =========================================================================================
     *  Tarjeta derecha con páginas: 0 música · 1 mapa · 2 accesos
     * ========================================================================================= */

    var page = 0
    private val pageTitles = arrayOf("Música", "Mapa", "Accesos")
    private val pageIcons = intArrayOf(R.drawable.ic_music, R.drawable.ic_mappin, R.drawable.ic_apps)

    private fun setupPages() {
        val dots = findViewById<LinearLayout>(R.id.pageDots)
        dots.removeAllViews()
        for (i in 0..2) {
            val hit = FrameLayout(this)
            hit.layoutParams = lp(dpi(30), MATCH)
            hit.contentDescription = "Página ${pageTitles[i]}"
            hit.addView(View(this), FrameLayout.LayoutParams(dpi(8), dpi(8), Gravity.CENTER))
            hit.setOnClickListener { showPage(i, true) }
            dots.addView(hit)
        }
        findViewById<SwipeFrame>(R.id.pages).onSwipe = { dir -> showPage((page + dir + 3) % 3, true) }
        findViewById<View>(R.id.btnMusicPerm).setOnClickListener { openNotifAccess() }

        // Mapa
        val map = findViewById<MapTileView>(R.id.mapView)
        map.darkMap = dark; map.accent = pal.accent
        map.zoom = sp.getInt("mapZoom", 16)
        map.setOnClickListener { openSlot(SLOTS[0]) }
        findViewById<View>(R.id.btnZoomIn).setOnClickListener { map.zoom = map.zoom + 1; sp.edit().putInt("mapZoom", map.zoom).apply() }
        findViewById<View>(R.id.btnZoomOut).setOnClickListener { map.zoom = map.zoom - 1; sp.edit().putInt("mapZoom", map.zoom).apply() }
        findViewById<View>(R.id.mapFrame).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(22f)) }
            }
            clipToOutline = true
        }
        val btns = findViewById<LinearLayout>(R.id.mapButtons)
        btns.removeAllViews()
        fun mapBtn(label: String, icon: Int, onClick: () -> Unit, onLong: (() -> Unit)?) {
            val b = hbox(Gravity.CENTER)
            b.layoutParams = lp(0, MATCH, 1f).apply { marginEnd = dpi(8) }
            b.background = rounded(withAlpha(pal.card, 0xF2), 28f, pal.line, 1f)
            b.isClickable = true
            b.addView(icon(icon, 20, pal.accent))
            b.addView(text(label, 17f, pal.text, Fonts.semibold(this)).apply { setPadding(dpi(8), 0, 0, 0); isSingleLine = true })
            b.setOnClickListener { onClick() }
            if (onLong != null) b.setOnLongClickListener { onLong(); true }
            btns.addView(b)
        }
        mapBtn("Casa", R.drawable.ic_mappin, { goTo("destHome", "Casa") }, { editDestination("destHome", "Casa") })
        mapBtn("Trabajo", R.drawable.ic_mappin, { goTo("destWork", "Trabajo") }, { editDestination("destWork", "Trabajo") })
        mapBtn("Buscar", R.drawable.ic_search, { openMapApp() }, null)

        buildAppsPage()
        showPage(sp.getInt("page", 0).coerceIn(0, 2), false)
    }

    fun showPage(i: Int, animate: Boolean) {
        val pages = arrayOf<View>(findViewById(R.id.pageMusic), findViewById(R.id.pageMap), findViewById(R.id.pageApps))
        val old = page
        page = i
        sp.edit().putInt("page", i).apply()
        tvSource.text = pageTitles[i]
        findViewById<ImageView>(R.id.ivPageIcon).setImageResource(pageIcons[i])
        val dots = findViewById<LinearLayout>(R.id.pageDots)
        for (k in 0 until dots.childCount) {
            val d = (dots.getChildAt(k) as FrameLayout).getChildAt(0)
            val on = k == i
            d.layoutParams = (d.layoutParams as FrameLayout.LayoutParams).apply { width = dpi(if (on) 20 else 8) }
            d.background = rounded(if (on) pal.accent else withAlpha(pal.muted, 0x66), 4f)
        }
        for ((k, v) in pages.withIndex()) {
            if (k == i) {
                v.visibility = View.VISIBLE
                if (animate && old != i && Anim.enabled(v)) {
                    val dir = if ((i - old + 3) % 3 == 1) 1 else -1
                    v.alpha = 0f; v.translationX = dp(40f) * dir
                    v.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
                }
            } else v.visibility = View.GONE
        }
        if (i == 1) updateMapPage()
        if (i == 2) buildAppsPage()
    }

    private fun updateMapPage() {
        if (page != 1) return
        val map = findViewById<MapTileView>(R.id.mapView)
        val fresh = System.currentTimeMillis() - Live.fixAt < 15000 && !Live.lat.isNaN()
        if (fresh) map.setPosition(Live.lat, Live.lon, Live.bearing, true)
        else map.setPosition(lat(), lon(), 0f, false)
    }

    /** Abre Google Maps (o la app de mapas elegida) navegando al destino guardado. */
    private fun goTo(key: String, name: String) {
        val dest = sp.getString(key, null)
        if (dest.isNullOrBlank()) { editDestination(key, name); return }
        val navPkg = slotPkg(SLOTS[0])
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(dest))
        val i = Intent(Intent.ACTION_VIEW, uri)
        if (navPkg == "com.google.android.apps.maps" && packageManager.getLaunchIntentForPackage(navPkg) != null) i.setPackage(navPkg)
        if (!startMap(i)) startMap(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dest))))
    }

    /** Guarda Casa / Trabajo: dirección escrita o tu ubicación actual. */
    fun editDestination(key: String, name: String) {
        val cur = sp.getString(key, null)
        val opts = arrayListOf("Escribir dirección", "Usar mi ubicación actual")
        if (!cur.isNullOrBlank()) opts.add("Borrar")
        AlertDialog.Builder(this).setTitle(if (cur.isNullOrBlank()) "$name: elegir destino" else "$name: $cur")
            .setItems(opts.toTypedArray()) { _, w ->
                when (w) {
                    0 -> askString("Dirección de $name", "Ej.: Av. Mcal. López 1234, Asunción") { s ->
                        sp.edit().putString(key, s).apply(); reloadScreen()
                    }
                    1 -> {
                        val la = if (!Live.lat.isNaN()) Live.lat else lat(); val lo = if (!Live.lon.isNaN()) Live.lon else lon()
                        sp.edit().putString(key, String.format(Locale.US, "%.6f,%.6f", la, lo)).apply()
                        Toast.makeText(this, "$name guardado con tu ubicación actual", Toast.LENGTH_SHORT).show()
                        reloadScreen()
                    }
                    2 -> { sp.edit().remove(key).apply(); reloadScreen() }
                }
            }.show()
    }

    /** Accesos: hasta 6 apps favoritas grandes. Tocá "+" para agregar, mantené para cambiar o quitar. */
    private fun favs(): MutableList<String> {
        val s = sp.getString("favs", null)
        if (s == null) {
            val def = SLOTS.mapNotNull { slotPkg(it) }.filter { packageManager.getLaunchIntentForPackage(it) != null }.distinct().take(6)
            return def.toMutableList()
        }
        return s.split(",").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveFavs(l: List<String>) { sp.edit().putString("favs", l.joinToString(",")).apply() }

    private fun buildAppsPage() {
        val pageApps = findViewById<LinearLayout>(R.id.pageApps)
        pageApps.removeAllViews()
        val list = favs()
        val cells = list.take(6).map { it as String? }.toMutableList()
        if (cells.size < 6) cells.add(null)
        val cols = if (scaleLevel() == 2) 2 else 3
        var row: LinearLayout? = null
        for ((idx, pkg) in cells.withIndex()) {
            if (idx % cols == 0) {
                row = hbox(Gravity.CENTER)
                row.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
                pageApps.addView(row)
            }
            val cell = vbox(Gravity.CENTER)
            cell.layoutParams = lp(0, MATCH, 1f)
            cell.isClickable = true
            val box = FrameLayout(this)
            box.layoutParams = lp(dpi(64), dpi(64))
            if (pkg != null) {
                box.addView(ImageView(this).apply {
                    try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { }
                }, FrameLayout.LayoutParams(MATCH, MATCH))
            } else {
                box.background = rounded(pal.pill, 20f)
                box.addView(icon(R.drawable.ic_plus, 28, pal.accent).apply {
                    layoutParams = FrameLayout.LayoutParams(dpi(28), dpi(28), Gravity.CENTER)
                })
            }
            cell.addView(box)
            cell.addView(text(if (pkg != null) appLabel(pkg) else "Agregar", 16f, if (pkg != null) pal.text else pal.accent).apply {
                gravity = Gravity.CENTER; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(6) }
            })
            if (pkg != null) {
                cell.setOnClickListener { launch(pkg) }
                cell.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle(appLabel(pkg)).setItems(arrayOf("Cambiar por otra app", "Quitar de accesos")) { _, w ->
                        val l = favs()
                        if (w == 0) pickApp { np -> val i = l.indexOf(pkg); if (i >= 0) l[i] = np; saveFavs(l); buildAppsPage() }
                        else { l.remove(pkg); saveFavs(l); buildAppsPage() }
                    }.show(); true
                }
            } else {
                cell.setOnClickListener { pickApp { np -> val l = favs(); if (np !in l) l.add(np); saveFavs(l); buildAppsPage() } }
            }
            row?.addView(cell)
        }
        // completa la última fila
        val rest = (cols - cells.size % cols) % cols
        repeat(rest) { row?.addView(View(this).apply { layoutParams = lp(0, MATCH, 1f) }) }
    }
}

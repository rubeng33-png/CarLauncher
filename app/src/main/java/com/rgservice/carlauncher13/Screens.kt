package com.rgservice.carlauncher13

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* =============================================================================================
 *  Base común: encabezado con botón volver + título
 * ============================================================================================= */

abstract class BaseScreen(protected val a: MainActivity) {
    protected val p: Pal = a.pal
    protected val sp = a.sp

    protected fun root(): LinearLayout = a.vbox().apply {
        setPadding(a.dpi(28), a.dpi(28), a.dpi(28), a.dpi(28))
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
    }

    protected fun header(title: String, subtitle: String? = null): LinearLayout {
        val hdr = a.hbox()
        hdr.layoutParams = lp(MATCH, a.dpi(64))
        hdr.addView(a.backButton(p) { a.closeScreen() })
        val t = a.vbox()
        t.layoutParams = lp(WRAP, WRAP).apply { marginStart = a.dpi(18) }
        t.addView(a.text(title, 34f, p.text, Fonts.bold(a)).apply { includeFontPadding = false })
        if (subtitle != null) t.addView(a.text(subtitle, 16f, p.muted).apply { setPadding(0, a.dpi(2), 0, 0) })
        hdr.addView(t)
        return hdr
    }

    protected fun card(radius: Float = 26f, stroke: Int = p.line): LinearLayout = a.vbox().apply {
        background = a.rounded(p.card, radius, stroke, 1f)
    }

    protected fun section(title: String, top: Int = 14): TextView =
        a.text(title, 16f, p.muted, Fonts.medium(a)).apply {
            layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dpi(top); bottomMargin = a.dpi(4) }
        }

    /** Fila tocable: ícono (opcional) · texto · valor · flecha. */
    protected fun row(iconRes: Int?, label: String, value: String, divider: Boolean, onClick: () -> Unit): View {
        val r = a.hbox()
        r.layoutParams = lp(MATCH, a.dpi(60))
        r.isClickable = true; r.isFocusable = true
        r.setOnClickListener { onClick() }
        if (divider) r.background = topLine()
        if (iconRes != null) r.addView(a.icon(iconRes, 24, p.accent).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = a.dpi(14)
        })
        r.addView(a.text(label, 19f, p.text).apply { layoutParams = lp(0, WRAP, 1f); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        if (value.isNotEmpty()) r.addView(a.text(value, 17f, p.muted).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; maxWidth = a.dpi(220)
        })
        r.addView(a.icon(R.drawable.ic_chevron, 20, p.muted).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = a.dpi(8)
        })
        return r
    }

    /** Línea fina arriba de una fila (separador). */
    protected fun topLine(): Drawable {
        val line = android.graphics.drawable.ColorDrawable(p.line)
        return android.graphics.drawable.LayerDrawable(arrayOf(line)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerHeight(0, a.dpi(1))
            setLayerGravity(0, Gravity.TOP or Gravity.FILL_HORIZONTAL)
        }
    }

    abstract fun build(): View
}

/* =============================================================================================
 *  Mantenimiento: visor holográfico + ítems por km + vencimientos por fecha
 * ============================================================================================= */

class MaintScreen(a: MainActivity) : BaseScreen(a) {

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("es"))

    // Colores fijos del visor (siempre oscuro, como el holograma)
    private val vText = 0xFFF3F5F7.toInt()
    private val vMuted = 0xFF9BA5B1.toInt()
    private val vAmber = 0xFFFFB547.toInt()
    private val vWarn = 0xFFFF7A59.toInt()
    private val vGround = 0xFF0B0D10.toInt()

    /** Pieza -> (video de entrada, video en bucle) */
    private val clips = mapOf(
        "oil" to Pair(R.raw.holo_oil_in, R.raw.holo_oil_loop),
        "gear" to Pair(R.raw.holo_gear_in, R.raw.holo_gear_loop),
        "air" to Pair(R.raw.holo_air_in, R.raw.holo_air_loop),
        "tires" to Pair(R.raw.holo_tires_in, R.raw.holo_tires_loop),
        "brakes" to Pair(R.raw.holo_brakes_in, R.raw.holo_brakes_loop),
        "belt" to Pair(R.raw.holo_belt_in, R.raw.holo_belt_loop)
    )

    private lateinit var video: HoloVideoView
    private lateinit var capBox: LinearLayout
    private lateinit var capTitle: TextView
    private lateinit var capSub: TextView
    private lateinit var capBig: TextView
    private lateinit var capBar: ProgressLine
    private lateinit var actions: LinearLayout
    private val rowViews = HashMap<String, View>()

    override fun build(): View {
        val root = root()
        val odo = (sp.getFloat("odoM", 0f) / 1000f).toInt()
        val odoTxt = if (Maint.hasOdo(sp)) "Tu auto: ${fmtKm(Maint.odo(sp))} km (tablero)" else "Cargá los km del tablero para empezar"
        val hdr = header("Mantenimiento", odoTxt)
        hdr.addView(a.spacer())
        val add = a.hbox(Gravity.CENTER)
        add.layoutParams = lp(WRAP, a.dpi(56))
        add.setPadding(a.dpi(22), 0, a.dpi(22), 0)
        add.background = a.rounded(p.card, 28f, p.line, 1f)
        add.isClickable = true
        add.addView(a.icon(R.drawable.ic_plus, 22, p.accent))
        add.addView(a.text("Agregar", 19f, p.accent, Fonts.semibold(a)).apply { setPadding(a.dpi(10), 0, 0, 0) })
        add.setOnClickListener { addDialog() }
        hdr.addView(add)
        val upd = a.hbox(Gravity.CENTER)
        upd.layoutParams = lp(WRAP, a.dpi(56)).apply { marginStart = a.dpi(12) }
        upd.setPadding(a.dpi(22), 0, a.dpi(22), 0)
        upd.background = if (Maint.hasOdo(sp)) a.rounded(p.card, 28f, p.line, 1f) else a.rounded(p.accent, 28f)
        upd.isClickable = true
        upd.addView(a.icon(R.drawable.ic_gauge, 22, if (Maint.hasOdo(sp)) p.accent else p.onAccent))
        upd.addView(a.text(if (Maint.hasOdo(sp)) "Km del tablero" else "Cargar km del tablero", 19f,
            if (Maint.hasOdo(sp)) p.accent else p.onAccent, Fonts.semibold(a)).apply { setPadding(a.dpi(10), 0, 0, 0) })
        upd.setOnClickListener { a.updateOdo { a.reloadScreen() } }
        hdr.addView(upd)
        root.addView(hdr)

        val body = a.hbox(Gravity.TOP)
        body.layoutParams = lp(MATCH, 0, 1f).apply { topMargin = a.dpi(24) }

        // ---------------- Visor holográfico ----------------
        val left = a.vbox()
        left.layoutParams = lp(0, MATCH, 1f)
        val viewer = FrameLayout(a)
        viewer.layoutParams = lp(MATCH, a.dpi(450))
        viewer.post {
            val hh = viewer.width * 9 / 16
            if (hh > 0 && hh != viewer.height) viewer.layoutParams = viewer.layoutParams.apply { height = hh }
        }
        viewer.background = a.rounded(vGround, 26f, p.line, 1f)
        viewer.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, a.dp(26f)) }
        }
        viewer.clipToOutline = true
        video = HoloVideoView(a)
        viewer.addView(video, FrameLayout.LayoutParams(MATCH, MATCH))
        val shade = View(a)
        shade.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xE00B0D10.toInt(), 0x8C0B0D10.toInt(), 0x000B0D10)
        )
        viewer.addView(shade, FrameLayout.LayoutParams(a.dpi(440), MATCH))
        capBox = a.vbox()
        capBox.setPadding(a.dpi(32), a.dpi(30), 0, 0)
        capTitle = a.text("", 30f, vText, Fonts.semibold(a))
        capSub = a.text("", 18f, vMuted)
        capBig = a.text("", 44f, vAmber, Fonts.semibold(a)).apply { fontFeatureSettings = "tnum"; setPadding(0, a.dpi(8), 0, 0) }
        capBar = ProgressLine(a).apply { layoutParams = lp(a.dpi(220), a.dpi(4)).apply { topMargin = a.dpi(10) } }
        capBox.addView(capTitle); capBox.addView(capSub); capBox.addView(capBig); capBox.addView(capBar)
        viewer.addView(capBox, FrameLayout.LayoutParams(a.dpi(420), WRAP))
        left.addView(viewer)

        actions = a.hbox()
        actions.layoutParams = lp(MATCH, a.dpi(60)).apply { topMargin = a.dpi(16) }
        left.addView(actions)
        body.addView(left)

        // ---------------- Lista de ítems ----------------
        val list = card()
        list.layoutParams = lp(a.dpi(if (a.scaleLevel() == 0) 400 else 340), MATCH).apply { marginStart = a.dpi(24) }
        val sv = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val inner = a.vbox()
        inner.setPadding(a.dpi(14), a.dpi(10), a.dpi(14), a.dpi(14))
        sv.addView(inner, ViewGroup.LayoutParams(MATCH, WRAP))
        list.addView(sv, LinearLayout.LayoutParams(MATCH, MATCH))
        inner.addView(section("Por kilómetros", 6).apply { setPadding(a.dpi(10), 0, 0, 0) })
        for (it in Maint.kmItems(sp)) inner.addView(kmRow(it))
        inner.addView(section("Vencimientos", 16).apply { setPadding(a.dpi(10), 0, 0, 0) })
        for (d in Maint.dateItems(sp)) inner.addView(dateRow(d))
        body.addView(list)
        root.addView(body)

        // Selección inicial: la última elegida, si no el primer ítem en alerta, si no el aceite
        val firstWarn = Maint.kmItems(sp).firstOrNull { Maint.isWarn(sp, it) }?.let { "km:" + it.id }
        select(a.maintSel?.takeIf { rowViews.containsKey(it) } ?: firstWarn ?: "km:oil", false)
        return root
    }

    /* ---------- Filas ---------- */

    private fun kmRow(item: KmItem): View {
        val rem = Maint.rem(sp, item); val iv = Maint.interval(sp, item)
        val set = rem >= 0; val warn = Maint.isWarn(sp, item)
        val r = a.hbox()
        r.layoutParams = lp(MATCH, a.dpi(64))
        r.setPadding(a.dpi(12), 0, a.dpi(12), 0)
        r.isClickable = true
        val t = a.vbox()
        t.layoutParams = lp(0, WRAP, 1f)
        t.addView(a.text(item.name, 18f, p.text, Fonts.semibold(a)).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        t.addView(ProgressLine(a).apply {
            layoutParams = lp(MATCH, a.dpi(4)).apply { topMargin = a.dpi(7); marginEnd = a.dpi(16) }
            setColors(p.track, if (warn) p.warn else p.accent)
            fraction = if (set && iv > 0) rem.toFloat() / iv else 0f
        })
        r.addView(t)
        r.addView(a.text(if (set) "${fmtKm(rem)} km" else "Configurar", 17f, if (warn) p.warn else if (set) p.text else p.accent, Fonts.semibold(a)).apply {
            fontFeatureSettings = "tnum"
        })
        if (warn) Anim.pulse(r.getChildAt(1))
        val key = "km:" + item.id
        r.setOnClickListener { select(key, true) }
        rowViews[key] = r
        return r
    }

    private fun dateRow(d: DateItem): View {
        val ms = Maint.date(sp, d)
        val days = if (ms > 0) Maint.daysUntil(ms) else Int.MAX_VALUE
        val warn = ms > 0 && days <= Maint.WARN_DAYS
        val color = if (warn) p.warn else p.accent
        val r = a.hbox()
        r.layoutParams = lp(MATCH, a.dpi(64))
        r.setPadding(a.dpi(12), 0, a.dpi(12), 0)
        r.isClickable = true
        r.addView(a.icon(R.drawable.ic_calendar, 22, color))
        val t = a.vbox()
        t.layoutParams = lp(0, WRAP, 1f).apply { marginStart = a.dpi(12) }
        t.addView(a.text(d.name, 18f, p.text, Fonts.semibold(a)).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        t.addView(a.text(if (ms > 0) dateFmt.format(Date(ms)).replace(".", "") else "Sin fecha", 14f, p.muted))
        r.addView(t)
        val pill = a.text(if (ms > 0) Maint.leftText(days) else "Configurar", 15f, color, Fonts.semibold(a))
        pill.gravity = Gravity.CENTER
        pill.setPadding(a.dpi(12), 0, a.dpi(12), 0)
        pill.layoutParams = lp(WRAP, a.dpi(30))
        pill.background = a.rounded(withAlpha(color, 0x1F), 15f)
        r.addView(pill)
        if (warn) Anim.pulse(pill)
        val key = "date:" + d.id
        r.setOnClickListener { select(key, true) }
        rowViews[key] = r
        return r
    }

    /* ---------- Selección: mueve el holograma a la pieza y actualiza el texto ---------- */

    private fun select(key: String, animate: Boolean) {
        a.maintSel = key
        for ((k, v) in rowViews) v.background = if (k == key) a.rounded(p.pill, 16f) else null
        actions.removeAllViews()

        val (kind, id) = key.split(":", limit = 2).let { Pair(it[0], it.getOrElse(1) { "" }) }
        if (kind == "km") {
            val item = Maint.kmItems(sp).firstOrNull { it.id == id } ?: return
            val rem = Maint.rem(sp, item); val iv = Maint.interval(sp, item)
            val set = rem >= 0; val warn = Maint.isWarn(sp, item)
            capTitle.text = item.name
            val nx = Maint.next(sp, item)
            val due = Maint.dueDate(sp, item)
            val base = if (nx >= 0 && Maint.hasOdo(sp)) "Próximo a los ${fmtKm(nx)} km" else (if (id == "tires") "Rotación cada ${fmtKm(iv)} km" else "Cambio cada ${fmtKm(iv)} km")
            capSub.text = if (due > 0) base + " · o el " + dateFmt.format(Date(due)).replace(".", "") else base
            capBig.text = if (set) "${fmtKm(rem)} km" else "Sin configurar"
            capBig.setTextColor(if (warn) vWarn else vAmber)
            capBar.setColors(0xFF272D36.toInt(), if (warn) vWarn else vAmber)
            capBar.fraction = if (set && iv > 0) rem.toFloat() / iv else 0f
            capBar.visibility = if (set) View.VISIBLE else View.GONE
            val clip = clips[id]
            if (clip != null) video.play(clip.first, clip.second) else video.play(0, R.raw.holo_overview)

            val done = { a.reloadScreen() }
            actions.addView(actionBtn("✔  Lo cambié hoy", true) { a.kmDone(item, done) })
            actions.addView(actionBtn("Etiqueta del taller", false) { a.kmNextAt(item, done) })
            actions.addView(actionBtn("Más…", false) { a.kmDialog(item, done) })
        } else {
            val d = Maint.dateItems(sp).firstOrNull { it.id == id } ?: return
            val ms = Maint.date(sp, d)
            val days = if (ms > 0) Maint.daysUntil(ms) else 0
            val warn = ms > 0 && days <= Maint.WARN_DAYS
            capTitle.text = d.name
            capSub.text = if (ms > 0) "Vence el " + dateFmt.format(Date(ms)).replace(".", "") else "Elegí la fecha de vencimiento"
            capBig.text = if (ms > 0) Maint.leftText(days).replaceFirstChar { it.uppercase() } else "Sin fecha"
            capBig.setTextColor(if (warn) vWarn else vAmber)
            capBar.visibility = View.GONE
            video.play(0, R.raw.holo_overview)

            actions.addView(actionBtn(if (ms > 0) "Cambiar fecha" else "Elegir fecha", true) {
                pickDate(ms) { v -> sp.edit().putLong("d_" + d.id, v).apply(); a.reloadScreen() }
            })
            if (ms > 0) actions.addView(actionBtn("Quitar fecha", false) { sp.edit().putLong("d_" + d.id, -1L).apply(); a.reloadScreen() })
            if (d.custom) actions.addView(actionBtn("Eliminar", false) { Maint.removeCustom(sp, "customDates", d.id); a.maintSel = null; a.reloadScreen() })
        }

        // El texto aparece cuando la cámara llega a la pieza
        if (animate && Anim.enabled(capBox)) {
            capBox.animate().cancel()
            capBox.alpha = 0f; capBox.translationX = -a.dp(16f)
            capBox.animate().alpha(1f).translationX(0f).setStartDelay(900).setDuration(500)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        } else { capBox.alpha = 1f; capBox.translationX = 0f }
    }

    private fun actionBtn(label: String, primary: Boolean, onClick: () -> Unit): View {
        val b = a.text(label, 18f, if (primary) p.onAccent else p.text, Fonts.semibold(a))
        b.gravity = Gravity.CENTER
        b.setPadding(a.dpi(24), 0, a.dpi(24), 0)
        b.layoutParams = lp(WRAP, MATCH).apply { marginEnd = a.dpi(12) }
        b.background = if (primary) a.rounded(p.accent, 30f) else a.rounded(p.card, 30f, p.line, 1f)
        b.isClickable = true
        b.setOnClickListener { onClick() }
        return b
    }

    private fun pickDate(initial: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (initial > 0) cal.timeInMillis = initial
        DatePickerDialog(a, { _, y, m, d ->
            val c = Calendar.getInstance(); c.clear(); c.set(y, m, d, 12, 0, 0)
            onPick(c.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun addDialog() {
        val opts = arrayOf("Por kilómetros (ej.: filtro de combustible)", "Por fecha (ej.: patente, matafuego)")
        AlertDialog.Builder(a).setTitle("Agregar").setItems(opts) { _, w ->
            if (w == 0) {
                a.askString("Nombre", "Ej.: Filtro de combustible") { name ->
                    a.askInt("¿Cada cuántos km?") { km -> if (km > 0) { Maint.addKm(sp, name, km); a.reloadScreen() } }
                }
            } else {
                a.askString("Nombre", "Ej.: Matafuego") { name ->
                    pickDate(-1L) { ms -> Maint.addDate(sp, name, ms); a.reloadScreen() }
                }
            }
        }.show()
    }
}

/* =============================================================================================
 *  Todas las apps: buscador, apps fijadas en la barra y grilla
 * ============================================================================================= */

class AppsScreen(a: MainActivity) : BaseScreen(a) {

    private class Entry(val pkg: String, val label: String, val icon: Drawable)

    private var all: List<Entry> = emptyList()
    private var shown: List<Entry> = emptyList()
    private lateinit var adapter: BaseAdapter
    private lateinit var countView: TextView

    override fun build(): View {
        val root = root()
        val hdr = header("Apps")

        // Buscador
        val search = a.hbox()
        search.layoutParams = lp(0, a.dpi(56), 1f).apply { marginStart = a.dpi(36); marginEnd = a.dpi(24) }
        search.setPadding(a.dpi(20), 0, a.dpi(20), 0)
        search.background = a.rounded(p.card, 28f, p.line, 1f)
        search.addView(a.icon(R.drawable.ic_search, 22, p.muted))
        val et = EditText(a).apply {
            hint = "Buscar app"
            setHintTextColor(p.muted); setTextColor(p.text)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 19f)
            typeface = Fonts.regular(a)
            background = null; isSingleLine = true
            layoutParams = lp(0, MATCH, 1f).apply { marginStart = a.dpi(12) }
        }
        search.addView(et)
        hdr.addView(search)
        if (a.scaleLevel() == 0) {
            hdr.addView(a.icon(R.drawable.ic_pin, 18, p.muted))
            hdr.addView(a.text("Mantené presionada una app para fijarla", 15f, p.muted).apply { setPadding(a.dpi(8), 0, 0, 0) })
        }
        root.addView(hdr)

        // Fijadas en la barra
        root.addView(a.text("En la barra", 17f, p.muted, Fonts.medium(a)).apply {
            layoutParams = lp(WRAP, WRAP).apply { topMargin = a.dpi(22) }
        })
        val pinned = a.hbox()
        pinned.layoutParams = lp(MATCH, a.dpi(72)).apply { topMargin = a.dpi(12) }
        for (s in MainActivity.SLOTS) pinned.addView(slotCard(s))
        root.addView(pinned)

        // Todas
        countView = a.text("Todas", 17f, p.muted, Fonts.medium(a))
        countView.layoutParams = lp(WRAP, WRAP).apply { topMargin = a.dpi(22) }
        root.addView(countView)

        val grid = GridView(a).apply {
            numColumns = when (a.scaleLevel()) { 0 -> 8; 1 -> 7; else -> 6 }
            verticalSpacing = a.dpi(8)
            horizontalSpacing = a.dpi(12)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            selector = android.graphics.drawable.ColorDrawable(0)
            layoutParams = lp(MATCH, 0, 1f).apply { topMargin = a.dpi(12) }
        }
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup?): View = tile(shown[i])
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ -> a.launch(shown[i].pkg) }
        grid.setOnItemLongClickListener { _, _, i, _ -> pinDialog(shown[i]); true }
        root.addView(grid)

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filter(s?.toString().orEmpty()) }
        })

        // Cargar la lista de apps sin trabar la pantalla
        Thread {
            val pm = a.packageManager
            val list = try {
                a.launchableApps().map { Entry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                    .distinctBy { it.pkg }
            } catch (e: Exception) { emptyList() }
            a.runOnUiThread { all = list; filter(et.text.toString()) }
        }.start()
        return root
    }

    private fun filter(q: String) {
        val t = q.trim().lowercase()
        shown = if (t.isEmpty()) all else all.filter { it.label.lowercase().contains(t) }
        countView.text = "Todas · ${shown.size}"
        adapter.notifyDataSetChanged()
    }

    private fun pinnedPkgs(): Set<String> = MainActivity.SLOTS.mapNotNull { a.slotPkg(it) }.toSet()

    private fun tile(e: Entry): View {
        val v = a.vbox(Gravity.CENTER)
        v.layoutParams = AbsListView.LayoutParams(MATCH, a.dpi(120))
        val box = FrameLayout(a)
        box.layoutParams = lp(a.dpi(72), a.dpi(72))
        box.addView(ImageView(a).apply { setImageDrawable(e.icon); setPadding(a.dpi(4), a.dpi(4), a.dpi(4), a.dpi(4)) },
            FrameLayout.LayoutParams(MATCH, MATCH))
        if (e.pkg in pinnedPkgs()) {
            val badge = FrameLayout(a)
            badge.background = a.oval(p.accent)
            badge.addView(a.icon(R.drawable.ic_pin, 14, p.onAccent).apply {
                layoutParams = FrameLayout.LayoutParams(a.dpi(14), a.dpi(14), Gravity.CENTER)
            })
            box.addView(badge, FrameLayout.LayoutParams(a.dpi(24), a.dpi(24), Gravity.TOP or Gravity.END))
        }
        v.addView(box)
        v.addView(a.text(e.label, 16f, p.text).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dpi(8) }
        })
        return v
    }

    private fun slotCard(s: MainActivity.Slot): View {
        val pkg = a.slotPkg(s)
        val has = pkg != null && a.packageManager.getLaunchIntentForPackage(pkg) != null
        val c = a.hbox()
        c.layoutParams = lp(a.dpi(220), MATCH).apply { marginEnd = a.dpi(14) }
        c.setPadding(a.dpi(14), 0, a.dpi(14), 0)
        c.background = a.rounded(p.card, 22f, p.line, 1f)
        c.isClickable = true
        val iconBox = FrameLayout(a)
        iconBox.layoutParams = lp(a.dpi(46), a.dpi(46))
        if (has) {
            iconBox.addView(ImageView(a).apply { setImageDrawable(a.packageManager.getApplicationIcon(pkg!!)) }, FrameLayout.LayoutParams(MATCH, MATCH))
        } else {
            iconBox.background = a.rounded(p.pill, 14f)
            iconBox.addView(a.icon(s.icon, 22, p.accent).apply { layoutParams = FrameLayout.LayoutParams(a.dpi(22), a.dpi(22), Gravity.CENTER) })
        }
        c.addView(iconBox)
        val t = a.vbox()
        t.layoutParams = lp(0, WRAP, 1f).apply { marginStart = a.dpi(14) }
        t.addView(a.text(if (has) a.appLabel(pkg!!) else "Elegir app", 18f, if (has) p.text else p.accent, Fonts.semibold(a)).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        })
        t.addView(a.text(s.label, 14f, p.muted))
        c.addView(t)
        c.setOnClickListener { a.openSlot(s) }
        c.setOnLongClickListener { a.pickApp { pk -> a.setSlot(s, pk) }; true }
        return c
    }

    private fun pinDialog(e: Entry) {
        val labels = MainActivity.SLOTS.map { "Fijar en ${it.label}" }.toTypedArray()
        AlertDialog.Builder(a).setTitle(e.label).setItems(labels) { _, i ->
            a.setSlot(MainActivity.SLOTS[i], e.pkg)
        }.show()
    }
}

/* =============================================================================================
 *  Ajustes del launcher
 * ============================================================================================= */

class SettingsScreen(a: MainActivity) : BaseScreen(a) {

    override fun build(): View {
        val root = root()
        root.addView(header("Ajustes"))
        val body = a.hbox(Gravity.TOP)
        body.layoutParams = lp(MATCH, 0, 1f).apply { topMargin = a.dpi(22) }

        val (leftCard, left) = column(0)
        val (rightCard, right) = column(24)
        body.addView(leftCard)
        body.addView(rightCard)
        root.addView(body)

        // ---------------- Columna izquierda ----------------
        left.addView(section("Perfil", 8))
        left.addView(row(R.drawable.ic_user, "Tu nombre", sp.getString("name", "conductor") ?: "", false) { a.askText("Tu nombre", "name") })
        left.addView(row(R.drawable.ic_image, "Imagen del auto", if (a.carFile().exists()) "Foto propia" else "Modelo 3D", true) { a.carMenu() })

        left.addView(section("Pantalla"))
        val nmTitle = a.hbox()
        nmTitle.layoutParams = lp(MATCH, a.dpi(40))
        nmTitle.addView(a.icon(R.drawable.ic_moon, 24, p.accent))
        nmTitle.addView(a.text("Modo noche", 19f, p.text).apply { setPadding(a.dpi(14), 0, 0, 0) })
        left.addView(nmTitle)
        left.addView(nightSegment())
        left.addView(nightHint())

        val szTitle = a.hbox()
        szTitle.layoutParams = lp(MATCH, a.dpi(40))
        szTitle.background = topLine()
        szTitle.addView(a.icon(R.drawable.ic_android, 24, p.accent))
        szTitle.addView(a.text("Tamaño de pantalla", 19f, p.text).apply { setPadding(a.dpi(14), 0, 0, 0) })
        left.addView(szTitle)
        left.addView(segment(listOf("Normal", "Grande", "Muy grande"), a.scaleLevel()) { i ->
            sp.edit().putInt("scale", i).commit()
            a.recreate()
        })
        left.addView(a.text("Grande y Muy grande agrandan textos y botones para pantallas chicas.", 15f, p.muted).apply {
            layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dpi(8); bottomMargin = a.dpi(12) }
        })

        left.addView(toggleRow("Abrir al dar contacto", "Muestra el launcher al encender la radio", "ignOpen", true) { on ->
            if (on) a.requestOverlay()
        })
        left.addView(toggleRow("Animación de bienvenida", "Auto y saludo durante 2 segundos", "splash", true, null))

        // ---------------- Columna derecha ----------------
        right.addView(section("Conducción", 8))
        right.addView(speedRow())

        right.addView(section("Apps predeterminadas"))
        MainActivity.SLOTS.forEachIndexed { i, s ->
            val pkg = a.slotPkg(s)
            val label = if (pkg != null && a.packageManager.getLaunchIntentForPackage(pkg) != null) a.appLabel(pkg) else "Elegir"
            right.addView(row(null, s.label, label, i > 0) { a.pickApp { pk -> a.setSlot(s, pk) } })
        }

        right.addView(section("Permisos"))
        right.addView(row(R.drawable.ic_music, "Música y navegación", if (a.listenerEnabled()) "Activado" else "Activar", false) { a.openNotifAccess() })
        val gps = a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        right.addView(row(R.drawable.ic_mappin, "Ubicación (GPS)", if (gps) "Activado" else "Activar", true) {
            a.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName)))
        })

        right.addView(section("Música"))
        right.addView(a.text("Control de los botones play, pausa, anterior y siguiente", 15f, p.muted).apply {
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = a.dpi(2) }
        })
        right.addView(segment(listOf("Automático", "Sesión", "Teclas"), sp.getInt("mediaMode", 0)) { i ->
            sp.edit().putInt("mediaMode", i).remove("mediaKeyPkgs").apply()
            a.reloadScreen()
        })
        right.addView(a.text("Si los botones no responden con la música Bluetooth, elegí \"Teclas\".", 15f, p.muted).apply {
            layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dpi(8); bottomMargin = a.dpi(4) }
        })

        right.addView(section("Destinos"))
        right.addView(toggleRow("Maps dentro de la tarjeta",
            if (a.freeformSupported()) "Abre Maps en la tarjeta del mapa, sin tapar el launcher"
            else "Requiere \"ventanas de forma libre\" en Opciones de desarrollador", "mapInCard", true) { on ->
            if (on) a.sp.edit().remove("ffAsked").apply()
        })
        right.addView(row(R.drawable.ic_mappin, "Casa", sp.getString("destHome", null) ?: "Elegir", false) { a.editDestination("destHome", "Casa") })
        right.addView(row(R.drawable.ic_mappin, "Trabajo", sp.getString("destWork", null) ?: "Elegir", true) { a.editDestination("destWork", "Trabajo") })

        right.addView(section("Más"))
        right.addView(row(R.drawable.ic_gauge, "Km del tablero", if (Maint.hasOdo(sp)) "${fmtKm(Maint.odo(sp))} km" else "Cargar", false) { a.updateOdo { a.reloadScreen() } })
        right.addView(row(R.drawable.ic_wrench, "Mantenimiento y vencimientos", "", true) { a.showScreen("maint", true) })
        right.addView(row(R.drawable.ic_android, "Ajustes de Android", "", true) { a.safeStart(Intent(Settings.ACTION_SETTINGS)) })
        return root
    }

    /** Tarjeta con scroll. Devuelve (tarjeta, contenedor interno donde se agregan las filas). */
    private fun column(marginStartDp: Int): Pair<LinearLayout, LinearLayout> {
        val c = card()
        c.layoutParams = lp(0, MATCH, 1f).apply { marginStart = a.dpi(marginStartDp) }
        val sv = ScrollView(a).apply { isVerticalScrollBarEnabled = false }
        val inner = a.vbox()
        inner.setPadding(a.dpi(24), a.dpi(14), a.dpi(24), a.dpi(14))
        sv.addView(inner, ViewGroup.LayoutParams(MATCH, WRAP))
        c.addView(sv, LinearLayout.LayoutParams(MATCH, MATCH))
        return Pair(c, inner)
    }

    /** Selector de opciones tipo "pastilla" (el elegido en color). */
    private fun segment(labels: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val seg = a.hbox()
        seg.layoutParams = lp(MATCH, a.dpi(52)).apply { topMargin = a.dpi(4) }
        seg.setPadding(a.dpi(4), a.dpi(4), a.dpi(4), a.dpi(4))
        seg.background = a.rounded(p.seg, 16f, p.line, 1f)
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val b = a.text(l, 17f, if (on) p.onAccent else p.text, Fonts.semibold(a))
            b.gravity = Gravity.CENTER
            b.isSingleLine = true
            b.layoutParams = lp(0, MATCH, 1f).apply { if (i > 0) marginStart = a.dpi(4) }
            if (on) b.background = a.rounded(p.accent, 12f)
            b.isClickable = true
            b.setOnClickListener { if (!on) onPick(i) }
            seg.addView(b)
        }
        return seg
    }

    private fun nightSegment(): View {
        val mode = sp.getInt("nightMode", 0)
        val seg = a.hbox()
        seg.layoutParams = lp(MATCH, a.dpi(52)).apply { topMargin = a.dpi(4) }
        seg.setPadding(a.dpi(4), a.dpi(4), a.dpi(4), a.dpi(4))
        seg.background = a.rounded(p.seg, 16f, p.line, 1f)
        val labels = listOf("Automático (sol)", "Luces del auto", "Manual")
        labels.forEachIndexed { i, l ->
            val on = i == mode
            val b = a.text(l, 17f, if (on) p.onAccent else p.text, Fonts.semibold(a))
            b.gravity = Gravity.CENTER
            b.layoutParams = lp(0, MATCH, 1f).apply { if (i > 0) marginStart = a.dpi(4) }
            if (on) b.background = a.rounded(p.accent, 12f)
            b.isClickable = true
            b.contentDescription = "Modo noche: $l"
            b.setOnClickListener {
                if (i == 1 && (sp.getString("lightKey", null) == null || mode == 1)) {
                    a.detectLights { a.reloadScreen() }
                } else {
                    sp.edit().putInt("nightMode", i).apply()
                    a.reloadScreen()
                }
            }
            seg.addView(b)
        }
        return seg
    }

    private fun nightHint(): View {
        val mode = sp.getInt("nightMode", 0)
        val txt = when (mode) {
            0 -> {
                val t = Sun.times(a.lat(), a.lon())
                if (t == null) "Según la salida y puesta del sol en tu ubicación"
                else {
                    val f = SimpleDateFormat("HH:mm", Locale.getDefault())
                    "Hoy oscurece a las ${f.format(Date(t.second))} y aclara a las ${f.format(Date(t.first))}, según tu ubicación GPS"
                }
            }
            1 -> "Cambia al encender las luces del auto. Tocá de nuevo para volver a detectarlas."
            else -> "Lo cambiás con el botón Modo noche de la barra"
        }
        val r = a.hbox(Gravity.TOP)
        r.layoutParams = lp(MATCH, WRAP).apply { topMargin = a.dpi(10); bottomMargin = a.dpi(12) }
        r.addView(a.icon(R.drawable.ic_sunset, 18, p.muted))
        r.addView(a.text(txt, 15f, p.muted).apply { setPadding(a.dpi(8), 0, 0, 0) })
        return r
    }

    private fun toggleRow(label: String, hint: String, key: String, def: Boolean, onChange: ((Boolean) -> Unit)?): View {
        val r = a.hbox()
        r.layoutParams = lp(MATCH, a.dpi(64))
        r.background = topLine()
        val t = a.vbox()
        t.layoutParams = lp(0, WRAP, 1f)
        t.addView(a.text(label, 19f, p.text))
        t.addView(a.text(hint, 14f, p.muted))
        r.addView(t)

        val sw = FrameLayout(a)
        sw.layoutParams = lp(a.dpi(60), a.dpi(34))
        sw.contentDescription = label
        val knob = View(a)
        sw.addView(knob, FrameLayout.LayoutParams(a.dpi(28), a.dpi(28), Gravity.CENTER_VERTICAL))
        fun render(on: Boolean) {
            sw.background = a.rounded(if (on) p.accent else withAlpha(p.muted, 0x55), 17f)
            knob.background = a.oval(if (on) p.onAccent else p.card)
            knob.translationX = a.dp(if (on) 29f else 3f)
        }
        render(sp.getBoolean(key, def))
        val click = View.OnClickListener {
            val on = !sp.getBoolean(key, def)
            sp.edit().putBoolean(key, on).apply()
            render(on)
            onChange?.invoke(on)
        }
        sw.setOnClickListener(click)
        r.setOnClickListener(click)
        r.addView(sw)
        return r
    }

    private fun speedRow(): View {
        val r = a.hbox()
        r.layoutParams = lp(MATCH, a.dpi(72))
        r.addView(a.icon(R.drawable.ic_gauge, 24, p.accent))
        val t = a.vbox()
        t.layoutParams = lp(0, WRAP, 1f).apply { marginStart = a.dpi(14) }
        t.addView(a.text("Aviso de velocidad", 19f, p.text))
        val hint = a.text("", 14f, p.muted)
        t.addView(hint)
        r.addView(t)

        val value = a.text("", 22f, p.text, Fonts.bold(a)).apply {
            gravity = Gravity.CENTER; minWidth = a.dpi(92); fontFeatureSettings = "tnum"
        }
        fun render() {
            val l = sp.getInt("limit", 80)
            value.text = if (l > 0) "$l km/h" else "No"
            hint.text = if (l > 0) "El velocímetro se pone naranja al pasarlo" else "Desactivado"
        }
        fun btn(sym: String, desc: String, f: () -> Unit) = a.text(sym, 24f, p.text).apply {
            gravity = Gravity.CENTER
            layoutParams = lp(a.dpi(48), a.dpi(48))
            background = a.oval(0, p.line, 1f)
            contentDescription = desc
            setOnClickListener { f(); render() }
        }
        r.addView(btn("−", "Bajar límite") {
            val l = sp.getInt("limit", 80)
            sp.edit().putInt("limit", if (l <= 20) 0 else l - 10).apply()
        })
        r.addView(value)
        r.addView(btn("+", "Subir límite") {
            val l = sp.getInt("limit", 80)
            sp.edit().putInt("limit", if (l == 0) 20 else minOf(160, l + 10)).apply()
        })
        render()
        return r
    }
}

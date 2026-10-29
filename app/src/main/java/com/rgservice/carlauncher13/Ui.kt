package com.rgservice.carlauncher13

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/* ---------- Medidas y formatos ---------- */

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Int): Int = Math.round(v * resources.displayMetrics.density)

/** 3420 -> "3.420" */
fun fmtKm(n: Int): String = String.format(Locale.US, "%,d", n).replace(',', '.')

/** 12.43 -> "12,4" */
fun fmtDec(x: Double): String = String.format(Locale.US, "%.1f", x).replace('.', ',')

fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/* ---------- Colores del tema actual (claro u oscuro) ---------- */

class Pal(ctx: Context) {
    private val tv = android.util.TypedValue()
    private val theme = ctx.theme
    private fun c(attr: Int, def: Int): Int =
        if (theme.resolveAttribute(attr, tv, true)) tv.data else def

    val ground = c(R.attr.cGround, Color.BLACK)
    val card = c(R.attr.cCard, Color.DKGRAY)
    val card2 = c(R.attr.cCard2, Color.DKGRAY)
    val line = c(R.attr.cLine, Color.GRAY)
    val text = c(R.attr.cText, Color.WHITE)
    val muted = c(R.attr.cMuted, Color.LTGRAY)
    val accent = c(R.attr.cAccent, Color.BLUE)
    val onAccent = c(R.attr.cOnAccent, Color.BLACK)
    val warn = c(R.attr.cWarn, Color.RED)
    val track = c(R.attr.cTrack, Color.DKGRAY)
    val tick = c(R.attr.cTick, Color.GRAY)
    val pill = c(R.attr.cPill, Color.DKGRAY)
    val glow = c(R.attr.cGlow, Color.TRANSPARENT)
    val seg = c(R.attr.cSeg, Color.BLACK)
    val art1 = c(R.attr.cArt1, Color.BLUE)
    val art2 = c(R.attr.cArt2, Color.BLUE)
    val art3 = c(R.attr.cArt3, Color.BLACK)
}

/* ---------- Tipografías ---------- */

object Fonts {
    private val cache = HashMap<Int, Typeface>()
    fun get(ctx: Context, res: Int): Typeface =
        cache.getOrPut(res) { try { ctx.resources.getFont(res) } catch (e: Exception) { Typeface.DEFAULT } }
    fun regular(c: Context) = get(c, R.font.barlow_regular)
    fun medium(c: Context) = get(c, R.font.barlow_medium)
    fun semibold(c: Context) = get(c, R.font.barlow_semibold)
    fun bold(c: Context) = get(c, R.font.barlow_bold)
}

/* ---------- Fondos ---------- */

fun Context.rounded(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 0f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp)
        setColor(fill)
        if (strokeDp > 0f) setStroke(Math.max(1, Math.round(dp(strokeDp))), stroke)
    }

fun Context.oval(fill: Int, stroke: Int = 0, strokeDp: Float = 0f): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (strokeDp > 0f) setStroke(Math.max(1, Math.round(dp(strokeDp))), stroke)
    }

/** Resplandor radial (centro de color -> transparente). */
fun radialGlow(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = radiusPx
        colors = intArrayOf(color, withAlpha(color, 0))
    }

/* ---------- Constructores de vistas (para las pantallas armadas en código) ---------- */

fun Context.text(s: CharSequence, sizeDp: Float, color: Int, font: Typeface? = null): TextView =
    TextView(this).apply {
        text = s
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        setTextColor(color)
        typeface = font ?: Fonts.regular(this@text)
        includeFontPadding = true
    }

fun Context.icon(res: Int, sizeDp: Int, tint: Int): ImageView =
    ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dpi(sizeDp), dpi(sizeDp))
    }

fun Context.hbox(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; this.gravity = gravity }

fun Context.vbox(gravity: Int = Gravity.NO_GRAVITY): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; this.gravity = gravity }

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun View.margins(ctx: Context, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): View {
    val p = layoutParams as? ViewGroup.MarginLayoutParams ?: LinearLayout.LayoutParams(WRAP, WRAP)
    p.setMargins(ctx.dpi(l), ctx.dpi(t), ctx.dpi(r), ctx.dpi(b))
    layoutParams = p
    return this
}

fun Context.spacer(weight: Float = 1f): View = View(this).apply { layoutParams = lp(0, 0, weight) }

fun Context.vspace(weight: Float = 1f): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, weight) }

fun Context.divider(color: Int, vertical: Boolean = false): View = View(this).apply {
    setBackgroundColor(color)
    layoutParams = if (vertical) LinearLayout.LayoutParams(dpi(1), MATCH) else LinearLayout.LayoutParams(MATCH, dpi(1))
}

/** Botón redondo "volver" de las pantallas secundarias. */
fun Context.backButton(pal: Pal, onClick: () -> Unit): FrameLayout =
    FrameLayout(this).apply {
        background = oval(pal.card, pal.line, 1f)
        layoutParams = LinearLayout.LayoutParams(dpi(60), dpi(60))
        contentDescription = "Volver al inicio"
        isClickable = true
        setOnClickListener { onClick() }
        addView(icon(R.drawable.ic_back, 26, pal.text).apply {
            layoutParams = FrameLayout.LayoutParams(dpi(26), dpi(26), Gravity.CENTER)
        })
    }

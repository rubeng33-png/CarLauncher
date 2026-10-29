package com.rgservice.carlauncher13

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Contenedor que dibuja su hijo como si la pantalla midiera 720 dp de alto
 * y lo escala para llenar la pantalla real (1024x600, 1280x720, 1920x720...).
 * Así el diseño se ve igual en cualquier radio.
 */
class ScaleLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    /** Alto de diseño: 720 = Normal, 640 = Grande, 580 = Muy grande (más chico = todo más grande). */
    var designH = 720f
        set(v) { field = v; requestLayout() }
    private val minDesignW = 1024f
    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        if (w == 0 || h == 0) return
        val d = resources.displayMetrics.density
        scale = h / (designH * d)
        if (designH >= 700f && w / scale < minDesignW * d) scale = w / (minDesignW * d)
        val cw = Math.round(w / scale)
        val ch = Math.round(h / scale)
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.layout(0, 0, c.measuredWidth, c.measuredHeight)
            c.pivotX = 0f
            c.pivotY = 0f
            c.scaleX = scale
            c.scaleY = scale
        }
    }
}

/** Medidor de arco de 270° con marcas, como en el diseño. */
class GaugeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pArc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val rect = RectF()
    private var target = 0f
    private var shown = 0f
    private var color = 0xFF6FB1FF.toInt()
    private var anim: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // necesario para el brillo (shadowLayer)
    }

    fun setColors(track: Int, tick: Int) {
        pTrack.color = track; pTick.color = tick; invalidate()
    }

    /** fraction 0..1. Si animate=true el arco se dibuja desde 0. */
    fun setValue(fraction: Float, arcColor: Int, animate: Boolean) {
        val f = fraction.coerceIn(0f, 1f)
        val changed = f != target || arcColor != color
        target = f; color = arcColor
        if (!animate) {
            if (changed) { anim?.cancel(); shown = f; invalidate() }
            return
        }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, f).apply {
            duration = 1400
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val k = s / 176f
        val cx = width / 2f; val cy = height / 2f
        // Marcas: 28 rayitas cada 10° sobre un radio de 80
        pTick.strokeWidth = 1.7f * k
        for (i in 0..27) {
            val a = Math.toRadians(135.0 + i * 10.0)
            val r1 = 77.5f * k; val r2 = 82.5f * k
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), pTick)
        }
        val r = 66f * k
        rect.set(cx - r, cy - r, cx + r, cy + r)
        pTrack.strokeWidth = 10f * k
        c.drawArc(rect, 135f, 270f, false, pTrack)
        if (shown > 0.002f) {
            pArc.strokeWidth = 10f * k
            pArc.color = color
            pArc.setShadowLayer(7f * k, 0f, 0f, withAlpha(color, 150))
            c.drawArc(rect, 135f, 270f * shown, false, pArc)
        }
    }
}

/** Barras de ecualizador animadas mientras suena música. */
class EqualizerView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val speeds = floatArrayOf(0.91f, 1.11f, 0.77f, 1.0f)
    private val phases = floatArrayOf(0.2f, 0.6f, 0.1f, 0.8f)
    private var t = 0f
    private val rect = RectF()
    private val anim = ValueAnimator.ofFloat(0f, 1000f).apply {
        duration = 1_000_000L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    var playing = false
        set(v) {
            if (field == v) return
            field = v
            if (v && isAttachedToWindow) anim.start() else anim.cancel()
            invalidate()
        }

    fun setBarColor(c: Int) { p.color = c; invalidate() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (playing) anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val n = 4
        val bw = context.dp(4f)
        val gap = (width - n * bw) / (n - 1).toFloat()
        val h = height.toFloat()
        for (i in 0 until n) {
            val f = if (playing) 0.22f + 0.78f * (0.5f - 0.5f * cos(2 * PI * (t * speeds[i] + phases[i]))).toFloat() else 0.22f
            val bh = max(bw, h * f)
            val x = i * (bw + gap)
            rect.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f)
            c.drawRoundRect(rect, bw / 2f, bw / 2f, p)
        }
    }
}

/** Línea de "ruta" punteada que se desplaza debajo del auto. */
class RoadView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    fun setLineColor(c: Int) { p.color = c; invalidate() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val k = context.dp(1f)
        p.strokeWidth = 2f * k
        p.pathEffect = DashPathEffect(floatArrayOf(18f * k, 30f * k), phase * 48f * k)
        val y = height / 2f
        c.drawLine(p.strokeWidth, y, width - p.strokeWidth, y, p)
    }
}

/** Imagen con un reflejo de luz que la cruza cada pocos segundos (solo sobre la silueta). */
class SheenImageView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : ImageView(ctx, attrs) {
    var sheenOn = true
        set(v) { field = v; invalidate() }
    var period = 6000L
    private var t = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private var anim: ValueAnimator? = null

    private fun startAnim() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = period
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val nt = it.animatedValue as Float
                val visible = nt < 0.55f || t < 0.55f
                t = nt
                if (visible) invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startAnim() }
    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!sheenOn || t >= 0.55f || drawable == null) return
        val p = t / 0.55f
        val e = (p * p * (3 - 2 * p)) // suavizado
        val w = width.toFloat(); val h = height.toFloat()
        val cx = 1.3f * w + (-0.3f * w - 1.3f * w) * e
        val half = 0.21f * w
        paint.shader = LinearGradient(cx - half, 0f, cx + half, 0.11f * w,
            intArrayOf(0x00FFFFFF, 0x52FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val save = c.saveLayer(0f, 0f, w, h, null)
        super.onDraw(c)
        c.drawRect(0f, 0f, w, h, paint)
        c.restoreToCount(save)
    }
}

/** Barra fina de progreso (música, ruta, carga). Con dot=true dibuja un punto al final. */
class ProgressLine @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var dot = false
    var barHeightDp = 0f // 0 = alto de la vista

    fun setColors(track: Int, fill: Int) {
        pTrack.color = track; pFill.color = fill; pHalo.color = withAlpha(fill, 60); invalidate()
    }

    override fun onDraw(c: Canvas) {
        val bh = if (barHeightDp > 0f) context.dp(barHeightDp) else height.toFloat()
        val top = (height - bh) / 2f
        val r = bh / 2f
        val inset = if (dot) context.dp(8f) else 0f
        rect.set(inset, top, width - inset, top + bh)
        c.drawRoundRect(rect, r, r, pTrack)
        val fx = inset + (width - 2 * inset) * fraction
        if (fraction > 0f) {
            rect.set(inset, top, max(inset + bh, fx), top + bh)
            c.drawRoundRect(rect, r, r, pFill)
        }
        if (dot) {
            val cy = height / 2f
            c.drawCircle(fx, cy, context.dp(12f), pHalo)
            c.drawCircle(fx, cy, context.dp(8f), pFill)
        }
    }
}

/**
 * Reproduce los videos del holograma (res/raw) sin sonido.
 * play(entrada, bucle): reproduce la entrada una vez y después el bucle sin fin.
 * play(0, bucle): solo el bucle. play(video, 0, onEnd): una sola vez y avisa al terminar.
 */
class HoloVideoView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    android.view.TextureView(ctx, attrs), android.view.TextureView.SurfaceTextureListener {

    private var surface: android.view.Surface? = null
    private var mp: android.media.MediaPlayer? = null
    private var inRes = 0
    private var loopRes = 0
    private var onEnd: (() -> Unit)? = null
    private var playingRes = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun play(entry: Int, loop: Int, end: (() -> Unit)? = null) {
        if (entry == 0 && loop != 0 && loop == playingRes && mp != null) return // ya está en ese bucle
        inRes = entry; loopRes = loop; onEnd = end
        if (surface != null) startSequence()
    }

    fun stop() {
        inRes = 0; loopRes = 0; onEnd = null
        releasePlayer()
    }

    private fun startSequence() {
        if (inRes != 0) {
            playRes(inRes, false) {
                if (loopRes != 0) playRes(loopRes, true, null) else onEnd?.invoke()
            }
        } else if (loopRes != 0) playRes(loopRes, true, null)
    }

    private fun playRes(res: Int, looping: Boolean, done: (() -> Unit)?) {
        val s = surface ?: return
        releasePlayer()
        val m = android.media.MediaPlayer()
        try {
            val afd = resources.openRawResourceFd(res)
            m.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            m.setSurface(s)
            m.isLooping = looping
            m.setVolume(0f, 0f)
            m.setOnPreparedListener { it.start() }
            m.setOnCompletionListener { if (!looping) done?.invoke() }
            m.setOnErrorListener { _, _, _ -> done?.invoke(); true }
            m.prepareAsync()
            mp = m
            playingRes = res
        } catch (e: Exception) {
            m.release()
            done?.invoke()
        }
    }

    private fun releasePlayer() {
        try { mp?.setOnCompletionListener(null); mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        playingRes = 0
    }

    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
        surface = android.view.Surface(st)
        if (inRes != 0 || loopRes != 0) startSequence()
    }

    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}

    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}

/**
 * Mapa chico con tu posición (calles de OpenStreetMap).
 * Las calles se descargan solo cuando hay internet y quedan guardadas en la radio,
 * así después se ven sin conexión. La posición sale de la antena GPS.
 */
class MapTileView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var zoom = 16
        set(v) { field = v.coerceIn(12, 18); invalidate() }
    var darkMap = false
        set(v) { field = v; tilePaint.colorFilter = if (v) darkFilter else null; invalidate() }
    var accent = 0xFF6FB1FF.toInt()

    private var lat = -25.35
    private var lon = -57.45
    private var bearing = 0f
    private var hasFix = false
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(48)
    private val inFlight = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val exec = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gridPaint = Paint().apply { color = 0x22888888; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
    private val textBg = Paint().apply { color = 0x99FFFFFF.toInt() }
    private val rect = RectF()
    private val path = android.graphics.Path()
    private val darkFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
        -0.85f, 0f, 0f, 0f, 225f,
        0f, -0.85f, 0f, 0f, 230f,
        0f, 0f, -0.80f, 0f, 245f,
        0f, 0f, 0f, 1f, 0f))

    fun setPosition(la: Double, lo: Double, br: Float, fix: Boolean) {
        if (Math.abs(la - lat) < 1e-6 && Math.abs(lo - lon) < 1e-6 && br == bearing && fix == hasFix) return
        lat = la; lon = lo; bearing = br; hasFix = fix
        invalidate()
    }

    private fun tileFile(z: Int, x: Int, y: Int) = java.io.File(context.filesDir, "tiles/$z/$x/$y.png")

    private fun request(z: Int, x: Int, y: Int) {
        val key = "$z/$x/$y"
        if (key in inFlight) return
        val f = failedAt[key]
        if (f != null && System.currentTimeMillis() - f < 60_000) return
        inFlight.add(key)
        exec.execute {
            var bmp: android.graphics.Bitmap? = null
            try {
                val file = tileFile(z, x, y)
                if (!file.exists()) {
                    val c = java.net.URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as java.net.HttpURLConnection
                    c.setRequestProperty("User-Agent", "CarLauncher13/2.2 (launcher personal de auto)")
                    c.connectTimeout = 6000; c.readTimeout = 8000
                    if (c.responseCode == 200) {
                        file.parentFile?.mkdirs()
                        val tmp = java.io.File(file.path + ".tmp")
                        c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                        tmp.renameTo(file)
                    }
                    c.disconnect()
                }
                if (file.exists()) bmp = android.graphics.BitmapFactory.decodeFile(file.path)
            } catch (e: Exception) { }
            post {
                inFlight.remove(key)
                if (bmp != null) { cache.put(key, bmp); invalidate() } else failedAt[key] = System.currentTimeMillis()
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val n = 1 shl zoom
        val t = context.dp(256f) * 0.85f                     // tamaño de cada cuadro en pantalla
        val latR = Math.toRadians(lat)
        val wx = (lon + 180.0) / 360.0 * n
        val wy = (1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n
        val cx = w / 2f; val cy = h * 0.55f
        val x0 = Math.floor(wx - cx / t).toInt(); val x1 = Math.floor(wx + (w - cx) / t).toInt()
        val y0 = Math.floor(wy - cy / t).toInt(); val y1 = Math.floor(wy + (h - cy) / t).toInt()
        for (tx in x0..x1) for (ty in y0..y1) {
            if (ty < 0 || ty >= n) continue
            val xx = ((tx % n) + n) % n
            val left = cx + ((tx - wx) * t).toFloat(); val top = cy + ((ty - wy) * t).toFloat()
            rect.set(left, top, left + t, top + t)
            val key = "$zoom/$xx/$ty"
            val b = cache.get(key)
            if (b != null) c.drawBitmap(b, null, rect, tilePaint)
            else { c.drawRect(rect, gridPaint); request(zoom, xx, ty) }
        }
        // Posición
        val k = context.dp(1f)
        dotPaint.color = withAlpha(accent, 0x40)
        c.drawCircle(cx, cy, 22f * k, dotPaint)
        if (hasFix) {
            c.save(); c.rotate(bearing, cx, cy)
            path.reset()
            path.moveTo(cx, cy - 14f * k); path.lineTo(cx + 10f * k, cy + 10f * k)
            path.lineTo(cx, cy + 5f * k); path.lineTo(cx - 10f * k, cy + 10f * k); path.close()
            dotPaint.color = accent; c.drawPath(path, dotPaint)
            ringPaint.color = 0xFFFFFFFF.toInt(); ringPaint.strokeWidth = 2f * k; c.drawPath(path, ringPaint)
            c.restore()
        } else {
            dotPaint.color = 0xFF9BA5B1.toInt(); c.drawCircle(cx, cy, 8f * k, dotPaint)
        }
        // Atribución obligatoria
        textPaint.textSize = 11f * k
        val s = "© OpenStreetMap"
        val tw = textPaint.measureText(s)
        c.drawRect(w - tw - 10f * k, h - 16f * k, w, h, textBg)
        c.drawText(s, w - tw - 5f * k, h - 4f * k, textPaint)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow() }
}

/** Contenedor que detecta deslizar a los costados (para pasar de página) sin bloquear los toques. */
class SwipeFrame @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    var onSwipe: ((Int) -> Unit)? = null   // -1 = anterior, +1 = siguiente
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop * 2

    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragging = false }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) dragging = true
            }
        }
        return dragging
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                if (Math.abs(dx) > slop * 2) onSwipe?.invoke(if (dx < 0) 1 else -1)
                dragging = false
            }
            android.view.MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}

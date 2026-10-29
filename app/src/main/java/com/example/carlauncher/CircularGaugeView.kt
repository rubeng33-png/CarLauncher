package com.example.carlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CircularGaugeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var progress = 0f
    private var maxProgress = 10000f
    private var remainingText = ""
    private var isWarning = false

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f * d }
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f * d }
    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 20f * sp }
    private val paintSubtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 9f * sp }

    private val rect = RectF()
    private val startAngle = 135f
    private val sweepAngle = 270f

    fun setData(remaining: Float, max: Float, warning: Boolean) {
        this.maxProgress = max
        this.progress = remaining
        this.isWarning = warning
        this.remainingText = "%,d".format(remaining.toInt())

        ValueAnimator.ofFloat(0f, remaining).apply {
            duration = 1000
            addUpdateListener { this@CircularGaugeView.progress = it.animatedValue as Float; invalidate() }
            start()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (min(width, height) / 2f - 12f * d).coerceAtLeast(20f * d)
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        paintBg.color = ContextCompat.getColor(context, R.color.gauge_bg)
        canvas.drawArc(rect, startAngle, sweepAngle, false, paintBg)

        val percentage = (progress / maxProgress).coerceIn(0f, 1f)
        val sweep = sweepAngle * percentage
        val color = ContextCompat.getColor(context, if (isWarning) R.color.warning_orange else R.color.accent_blue)

        paintProgress.color = color
        canvas.drawArc(rect, startAngle, sweep, false, paintProgress)

        paintDot.color = color
        val endAngle = Math.toRadians((startAngle + sweep).toDouble())
        val dotX = centerX + radius * cos(endAngle).toFloat()
        val dotY = centerY + radius * sin(endAngle).toFloat()
        canvas.drawCircle(dotX, dotY, 5f * d, paintDot)

        paintText.color = ContextCompat.getColor(context, if (isWarning) R.color.warning_orange else R.color.text_primary)
        canvas.drawText(remainingText, centerX, centerY + 4f * d, paintText)

        paintSubtitle.color = ContextCompat.getColor(context, R.color.text_secondary)
        canvas.drawText("km restantes", centerX, centerY + 18f * d, paintSubtitle)
    }
}

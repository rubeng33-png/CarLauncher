package com.rgservice.carlauncher13

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/** Animaciones reutilizables. Cada vista guarda su animación en un tag para poder cortarla. */
object Anim {
    private val KEY = R.id.anim_tag

    /** Respeta la opción del sistema "quitar animaciones". */
    fun enabled(v: View): Boolean = try {
        Settings.Global.getFloat(v.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (e: Exception) { true }

    fun stop(v: View) {
        (v.getTag(KEY) as? Animator)?.cancel()
        v.setTag(KEY, null)
    }

    private fun keep(v: View, a: Animator) { stop(v); v.setTag(KEY, a); a.start() }

    /** Opacidad que sube y baja (alertas). */
    fun pulse(v: View, min: Float = 0.45f, halfMs: Long = 900) {
        if (v.getTag(KEY) != null) return
        if (!enabled(v)) return
        keep(v, ObjectAnimator.ofFloat(v, View.ALPHA, 1f, min).apply {
            duration = halfMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        })
    }

    fun stopPulse(v: View) { stop(v); v.alpha = 1f }

    /** Vaivén vertical suave (auto flotando, ícono del clima). */
    fun bob(v: View, dyPx: Float, halfMs: Long) {
        if (!enabled(v)) return
        keep(v, ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -dyPx).apply {
            duration = halfMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        })
    }

    /** Parpadeo de los dos puntos del reloj. */
    fun blink(v: View) {
        if (!enabled(v)) return
        keep(v, ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { v.alpha = if ((it.animatedValue as Float) < 0.5f) 1f else 0.2f }
        })
    }

    /** Onda que se expande y desvanece (GPS, botón play). */
    fun ripple(v: View, toScale: Float = 1.45f, ms: Long = 2400) {
        if (v.getTag(KEY) != null) return
        if (!enabled(v)) { v.alpha = 0f; return }
        val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, toScale).apply { repeatCount = ValueAnimator.INFINITE }
        val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, toScale).apply { repeatCount = ValueAnimator.INFINITE }
        val a = ObjectAnimator.ofFloat(v, View.ALPHA, 0.5f, 0f).apply { repeatCount = ValueAnimator.INFINITE }
        keep(v, AnimatorSet().apply {
            playTogether(sx, sy, a)
            duration = ms
            interpolator = DecelerateInterpolator()
        })
    }

    fun stopRipple(v: View) { stop(v); v.alpha = 0f; v.scaleX = 1f; v.scaleY = 1f }

    /** Brillo de faros / resplandor. */
    fun glow(v: View, ms: Long) {
        if (!enabled(v)) return
        keep(v, ObjectAnimator.ofFloat(v, View.ALPHA, 0.5f, 1f).apply {
            duration = ms / 2
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        })
    }

    /** Entrada desde abajo con fundido. */
    fun rise(v: View, delayMs: Long, dyPx: Float) {
        if (!enabled(v)) { v.alpha = 1f; v.translationY = 0f; return }
        v.alpha = 0f
        v.translationY = dyPx
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(800)
            .setInterpolator(DecelerateInterpolator(2.2f)).start()
    }
}

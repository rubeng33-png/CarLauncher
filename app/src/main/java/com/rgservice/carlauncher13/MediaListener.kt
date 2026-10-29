package com.rgservice.carlauncher13

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Con "Acceso a notificaciones" activado sirve para dos cosas:
 *  1) que Android nos deje leer y controlar la música (MediaSession),
 *  2) leer la notificación de navegación de Maps / Waze y mostrar el próximo giro en el inicio.
 */
class MediaListener : NotificationListenerService() {

    companion object {
        val NAV_PKGS = setOf(
            "com.google.android.apps.maps", "com.waze", "com.here.app.maps",
            "net.osmand", "net.osmand.plus", "com.sygic.aura", "ru.yandex.yandexnavi"
        )
        private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
        private var startKm: Double? = null

        /** "12,4 km" -> 12.4 ; "800 m" -> 0.8 */
        fun parseKm(s: String): Double? {
            val m = DIST.find(s) ?: return null
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            return when (m.groupValues[2].lowercase()) {
                "km" -> v; "m" -> v / 1000.0; "mi" -> v * 1.609; "ft" -> v * 0.0003048; "yd" -> v * 0.0009144
                else -> null
            }
        }
    }

    override fun onListenerConnected() {
        try { activeNotifications?.forEach { handle(it) } } catch (e: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try { handle(sbn) } catch (e: Exception) { }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == Live.nav?.pkg) { Live.nav = null; startKm = null }
    }

    override fun onListenerDisconnected() { Live.nav = null }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in NAV_PKGS) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))?.toString()?.trim().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        // Distancia al próximo giro: se busca primero en el título, después en el texto
        var dist = ""; var maneuver = title; var street = text
        val inTitle = DIST.find(title)
        if (inTitle != null) {
            dist = inTitle.value
            maneuver = title.removeRange(inTitle.range).trim(' ', '·', '-', '–', ',', '•')
        } else {
            val inText = DIST.find(text)
            if (inText != null && inText.range.first == 0) {
                dist = inText.value
                street = text.removeRange(inText.range).trim(' ', '·', '-', '–', ',', '•')
            }
        }
        if (dist.isEmpty()) { dist = title; maneuver = "" }

        val chunks = sub.split('·', '•', '|').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        val remaining = chunks.firstNotNullOfOrNull { c -> if (c.contains(':')) null else parseKm(c) }
        if (Live.nav?.pkg != sbn.packageName) startKm = null
        if (remaining != null && (startKm == null || remaining > startKm!!)) startKm = remaining

        val next = if (big.isNotEmpty() && big != text) big.lines().lastOrNull()?.trim().orEmpty() else ""

        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) { "Navegación" }

        Live.nav = NavInfo(
            pkg = sbn.packageName,
            appName = appName,
            dist = dist,
            maneuver = maneuver,
            street = street,
            next = if (next == street || next == maneuver) "" else next,
            chunks = chunks,
            icon = try { n.getLargeIcon()?.loadDrawable(this)?.let { toBitmap(it) } } catch (e: Exception) { null },
            intent = n.contentIntent,
            remainingKm = remaining,
            startKm = startKm
        )
    }

    private fun toBitmap(d: android.graphics.drawable.Drawable): Bitmap? {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }
}

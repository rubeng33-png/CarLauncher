package com.rgservice.carlauncher13

import android.app.PendingIntent
import android.content.SharedPreferences
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/* ======================= Datos en vivo (GPS y navegación) ======================= */

/** Datos que el servicio y el lector de notificaciones comparten con la pantalla (mismo proceso). */
object Live {
    @Volatile var speedKmh: Float = -1f
    @Volatile var fixAt: Long = 0L
    @Volatile var nav: NavInfo? = null
    @Volatile var lat: Double = Double.NaN
    @Volatile var lon: Double = Double.NaN
    @Volatile var bearing: Float = 0f
}

data class NavInfo(
    val pkg: String,
    val appName: String,
    val dist: String,
    val maneuver: String,
    val street: String,
    val next: String,
    val chunks: List<String>,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    val remainingKm: Double?,
    val startKm: Double?
)

/* ======================= Mantenimiento ======================= */

class KmItem(val id: String, val name: String, val def: Int, val presets: IntArray, val custom: Boolean)
class DateItem(val id: String, val name: String, val custom: Boolean)

/**
 * Cada ítem por km guarda: <id> = km restantes (-1 = sin configurar) y <id>Int = intervalo.
 * "oil" y "gear" usan las mismas claves que la versión anterior, así no se pierde nada.
 * Cada vencimiento guarda: d_<id> = fecha en milisegundos (-1 = sin fecha).
 */
object Maint {
    val BASE_KM = listOf(
        KmItem("oil", "Aceite motor", 5000, intArrayOf(3000, 5000, 7500, 10000), false),
        KmItem("gear", "Aceite caja", 40000, intArrayOf(20000, 30000, 40000, 60000), false),
        KmItem("air", "Filtro de aire", 15000, intArrayOf(10000, 15000, 20000, 30000), false),
        KmItem("tires", "Rotación de neumáticos", 10000, intArrayOf(5000, 8000, 10000, 15000), false),
        KmItem("brakes", "Pastillas de freno", 20000, intArrayOf(15000, 20000, 30000, 40000), false),
        KmItem("belt", "Correa de distribución", 60000, intArrayOf(40000, 50000, 60000, 100000), false)
    )
    val BASE_DATES = listOf(
        DateItem("ins", "Seguro del auto", false),
        DateItem("hab", "Habilitación", false),
        DateItem("rev", "Revisión técnica", false),
        DateItem("lic", "Licencia de conducir", false)
    )
    const val WARN_KM = 500
    const val WARN_DAYS = 30

    private fun arr(sp: SharedPreferences, key: String): JSONArray =
        try { JSONArray(sp.getString(key, "[]")) } catch (e: Exception) { JSONArray() }

    fun kmItems(sp: SharedPreferences): List<KmItem> {
        val out = ArrayList(BASE_KM)
        val a = arr(sp, "customKm")
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val def = o.optInt("def", 10000)
            out.add(KmItem(o.optString("id"), o.optString("name"), def, intArrayOf(def / 2, def, def * 2), true))
        }
        return out
    }

    fun dateItems(sp: SharedPreferences): List<DateItem> {
        val out = ArrayList(BASE_DATES)
        val a = arr(sp, "customDates")
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(DateItem(o.optString("id"), o.optString("name"), true))
        }
        return out
    }

    /* ---------- Odómetro del tablero ----------
     * odoKm = km que marca el tablero (lo cargás vos y el GPS le va sumando). -1 = sin cargar.
     * next_<id> = km del tablero en que toca el próximo cambio.
     * last_<id> = km del tablero del último cambio (si se conoce).
     * mon_<id>  = aviso por tiempo en meses (0 = no) · lastd_<id> = fecha del último cambio.
     * Si un ítem todavía no tiene next_, se usa el formato viejo (<id> = km restantes).
     */
    fun hasOdo(sp: SharedPreferences) = sp.getFloat("odoKm", -1f) >= 0f
    fun odo(sp: SharedPreferences): Int = Math.round(sp.getFloat("odoKm", -1f))
    fun next(sp: SharedPreferences, it: KmItem) = sp.getInt("next_" + it.id, -1)
    fun last(sp: SharedPreferences, it: KmItem) = sp.getInt("last_" + it.id, -1)
    fun months(sp: SharedPreferences, it: KmItem) = sp.getInt("mon_" + it.id, 0)
    fun lastDate(sp: SharedPreferences, it: KmItem) = sp.getLong("lastd_" + it.id, -1L)

    fun rem(sp: SharedPreferences, it: KmItem): Int {
        val n = next(sp, it)
        if (n >= 0 && hasOdo(sp)) return Math.max(0, n - odo(sp))
        return sp.getInt(it.id, -1)
    }
    fun interval(sp: SharedPreferences, it: KmItem) = sp.getInt(it.id + "Int", it.def)

    /** Fecha límite por tiempo (o -1). */
    fun dueDate(sp: SharedPreferences, it: KmItem): Long {
        val m = months(sp, it); val d = lastDate(sp, it)
        if (m <= 0 || d <= 0) return -1L
        return Calendar.getInstance().apply { timeInMillis = d; add(Calendar.MONTH, m) }.timeInMillis
    }

    /** ¿Está en alerta por km o por tiempo? */
    fun isWarn(sp: SharedPreferences, it: KmItem): Boolean {
        val r = rem(sp, it)
        if (r in 0..WARN_KM) return true
        val due = dueDate(sp, it)
        return due > 0 && daysUntil(due) <= WARN_DAYS
    }

    /** Carga el km del tablero. La primera vez pasa los ítems del formato viejo al nuevo. */
    fun setOdo(sp: SharedPreferences, km: Int) {
        val ed = sp.edit()
        if (!hasOdo(sp)) {
            for (it in kmItems(sp)) {
                val legacy = sp.getInt(it.id, -1)
                if (next(sp, it) < 0 && legacy >= 0) ed.putInt("next_" + it.id, km + legacy)
            }
        }
        ed.putFloat("odoKm", km.toFloat()).apply()
    }

    fun setNext(sp: SharedPreferences, it: KmItem, nextKm: Int) {
        sp.edit().putInt("next_" + it.id, nextKm).remove("last_" + it.id).putInt(it.id, Math.max(0, nextKm - odo(sp))).apply()
    }

    fun changedAt(sp: SharedPreferences, it: KmItem, atKm: Int, date: Long) {
        val n = atKm + interval(sp, it)
        sp.edit().putInt("last_" + it.id, atKm).putInt("next_" + it.id, n).putLong("lastd_" + it.id, date)
            .putInt(it.id, Math.max(0, n - odo(sp))).apply()
    }

    fun setIntervalKm(sp: SharedPreferences, it: KmItem, v: Int) {
        val ed = sp.edit().putInt(it.id + "Int", v)
        val l = last(sp, it)
        if (l >= 0) ed.putInt("next_" + it.id, l + v)
        else if (next(sp, it) < 0 && hasOdo(sp)) ed.putInt("next_" + it.id, odo(sp) + v)
        ed.apply()
    }
    fun date(sp: SharedPreferences, it: DateItem) = sp.getLong("d_" + it.id, -1L)

    fun addKm(sp: SharedPreferences, name: String, interval: Int) {
        val a = arr(sp, "customKm")
        val id = "c" + System.currentTimeMillis()
        a.put(JSONObject().put("id", id).put("name", name).put("def", interval))
        val ed = sp.edit().putString("customKm", a.toString()).putInt(id + "Int", interval).putInt(id, interval)
        if (hasOdo(sp)) ed.putInt("next_$id", odo(sp) + interval).putInt("last_$id", odo(sp))
        ed.apply()
    }

    fun addDate(sp: SharedPreferences, name: String, millis: Long) {
        val a = arr(sp, "customDates")
        val id = "c" + System.currentTimeMillis()
        a.put(JSONObject().put("id", id).put("name", name))
        sp.edit().putString("customDates", a.toString()).putLong("d_$id", millis).apply()
    }

    fun removeCustom(sp: SharedPreferences, listKey: String, id: String) {
        val a = arr(sp, listKey)
        val b = JSONArray()
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("id") != id) b.put(o) }
        sp.edit().putString(listKey, b.toString()).remove(id).remove(id + "Int").remove("d_$id")
            .remove("next_$id").remove("last_$id").remove("mon_$id").remove("lastd_$id").apply()
    }

    /** Ids de todos los ítems por km (el servicio de GPS descuenta km a todos). */
    fun allKmIds(sp: SharedPreferences): List<String> = kmItems(sp).map { it.id }

    /** Días hasta la fecha (negativo = vencido). */
    fun daysUntil(millis: Long): Int {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((day - today) / 86_400_000.0).toInt()
    }

    fun leftText(days: Int): String = when {
        days < 0 -> "Vencido"
        days == 0 -> "Hoy"
        days == 1 -> "Mañana"
        days < 60 -> "en $days días"
        days < 365 -> "en ${days / 30} meses"
        days < 730 -> "en 1 año"
        else -> "en ${days / 365} años"
    }

    /** Cantidad de ítems en alerta (para avisar en la pantalla de inicio). */
    fun warnings(sp: SharedPreferences, skipIds: Set<String> = emptySet()): Int {
        var n = 0
        for (it in kmItems(sp)) { if (it.id in skipIds) continue; if (isWarn(sp, it)) n++ }
        for (it in dateItems(sp)) { val d = date(sp, it); if (d > 0 && daysUntil(d) <= WARN_DAYS) n++ }
        return n
    }
}

/* ======================= Salida y puesta del sol ======================= */

/** Cálculo NOAA simplificado. Devuelve (amanecer, atardecer) en milisegundos para el día de 'cal'. */
object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(v: Double, m: Double): Double { var x = v % m; if (x < 0) x += m; return x }

    private fun utcHours(dayOfYear: Int, lat: Double, lon: Double, rising: Boolean): Double? {
        val lngHour = lon / 15.0
        val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val tt = h + ra - 0.06571 * t - 6.622
        return norm(tt - lngHour, 24.0)
    }

    fun times(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Pair<Long, Long>? {
        val doy = cal.get(Calendar.DAY_OF_YEAR)
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
        val noon = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun fix(ms: Long): Long { var x = ms; while (x - noon > 12 * 3_600_000L) x -= 86_400_000L; while (noon - x > 12 * 3_600_000L) x += 86_400_000L; return x }
        val r = utcHours(doy, lat, lon, true) ?: return null
        val s = utcHours(doy, lat, lon, false) ?: return null
        return Pair(fix(utc + (r * 3_600_000).toLong()), fix(utc + (s * 3_600_000).toLong()))
    }
}

package com.rgservice.carlauncher.data.maint

import android.content.SharedPreferences
import com.rgservice.carlauncher.data.Gauge

/**
 * Mantenimiento por el km del tablero (lo que se lee en el auto, sin cuentas):
 *  - odoKm        = km que marca el tablero (lo cargás una vez; el GPS le va sumando),
 *  - next_<id>    = km del tablero en que toca el próximo cambio,
 *  - int_<id>     = cada cuántos km se cambia,
 *  - last_<id>    = km del tablero del último cambio (si se conoce).
 * Faltan = próximo − tablero.
 */
class Maintenance(private val sp: SharedPreferences) {

    data class Item(val id: String, val name: String, val defInterval: Int, val presets: List<Int>)

    companion object {
        const val WARN_KM = 500
        val ITEMS = listOf(
            Item("oil", "Aceite motor", 5000, listOf(3000, 5000, 7500, 10000)),
            Item("gear", "Aceite caja", 40000, listOf(20000, 30000, 40000, 60000)),
            Item("air", "Filtro de aire", 15000, listOf(10000, 15000, 20000, 30000)),
            Item("tires", "Rotación de neumáticos", 10000, listOf(5000, 8000, 10000, 15000)),
            Item("brakes", "Pastillas de freno", 20000, listOf(15000, 20000, 30000, 40000)),
            Item("belt", "Correa de distribución", 60000, listOf(40000, 50000, 60000, 100000)),
        )
        fun item(id: String) = ITEMS.first { it.id == id }
    }

    fun hasOdo() = sp.getFloat("odoKm", -1f) >= 0f
    fun odo(): Int? = sp.getFloat("odoKm", -1f).takeIf { it >= 0f }?.let { Math.round(it) }
    fun next(id: String): Int? = sp.getInt("next_$id", -1).takeIf { it >= 0 }
    fun last(id: String): Int? = sp.getInt("last_$id", -1).takeIf { it >= 0 }
    fun interval(id: String): Int = sp.getInt("int_$id", item(id).defInterval)

    /** Corrige el km del tablero (los "próximo cambio" no se mueven: son km fijos del tablero). */
    fun setOdo(km: Int) { sp.edit().putFloat("odoKm", km.toFloat()).apply() }

    /** "Lo cambié hoy": próximo = tablero + intervalo. */
    fun changedNow(id: String) {
        val o = odo() ?: return
        sp.edit().putInt("last_$id", o).putInt("next_$id", o + interval(id)).apply()
    }

    /** "El próximo cambio es a los X km" (lo que dice el sticker del taller). */
    fun setNext(id: String, km: Int) { sp.edit().putInt("next_$id", km).remove("last_$id").apply() }

    /** Cambia el intervalo. Si se sabe cuándo fue el último cambio, recalcula el próximo. */
    fun setInterval(id: String, km: Int) {
        val ed = sp.edit().putInt("int_$id", km)
        last(id)?.let { ed.putInt("next_$id", it + km) }
        ed.apply()
    }

    fun remaining(id: String): Int? {
        val o = odo() ?: return null
        val n = next(id) ?: return null
        return (n - o).coerceAtLeast(0)
    }

    fun gauge(id: String): Gauge {
        val it = item(id)
        return Gauge(id, it.name, remaining(id) ?: -1, interval(id), next(id))
    }
}

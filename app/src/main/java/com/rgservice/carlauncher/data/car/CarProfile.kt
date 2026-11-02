package com.rgservice.carlauncher.data.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rgservice.carlauncher.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Datos del auto que se muestran en el inicio (Ajustes › Mi auto). */
data class CarProfile(
    val brand: String = "Toyota",
    val model: String = "New Corolla",
    val year: String = "2001",
    val hasDayPhoto: Boolean = false,
    val hasNightPhoto: Boolean = false,
    val useHologram: Boolean = false,
    val version: Long = 0,
)

/** Lo que dibuja el inicio: el perfil y las fotos ya leídas (null = imagen de fábrica / la de día). */
data class CarLook(
    val profile: CarProfile = CarProfile(),
    val day: ImageBitmap? = null,
    val night: ImageBitmap? = null,
)

/**
 * Perfil y fotos del auto. Las fotos elegidas se copian a filesDir (car_day.png / car_night.png):
 * lado mayor ≤ 1400 px, derechas según el EXIF y en PNG (conserva la transparencia).
 * Las imágenes se leen una sola vez por versión, en segundo plano; la pantalla no lee archivos.
 */
object CarRepository {
    private const val MAX_SIDE = 1400
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _look = MutableStateFlow(CarLook())
    val look: StateFlow<CarLook> = _look.asStateFlow()
    private var started = false

    private fun dayFile(c: Context) = File(c.filesDir, "car_day.png")
    private fun nightFile(c: Context) = File(c.filesDir, "car_night.png")

    /** Carga perfil y fotos (una vez). Pasa la foto de la versión anterior si existía. */
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        scope.launch {
            migrateOldPhoto(app)
            reload(app)
        }
    }

    private fun profile(c: Context): CarProfile {
        val sp = Prefs.of(c)
        return CarProfile(
            brand = sp.getString("car_brand", null) ?: "Toyota",
            model = sp.getString("car_model", null) ?: "New Corolla",
            year = sp.getString("car_year", null) ?: "2001",
            hasDayPhoto = dayFile(c).exists(),
            hasNightPhoto = nightFile(c).exists(),
            useHologram = sp.getBoolean("car_holo", false),
            version = sp.getLong("car_photo_ver", 0),
        )
    }

    private suspend fun reload(c: Context) = withContext(Dispatchers.IO) {
        val p = profile(c)
        _look.value = CarLook(
            profile = p,
            day = if (p.hasDayPhoto) decode(dayFile(c)) else null,
            night = if (p.hasNightPhoto) decode(nightFile(c)) else null,
        )
    }

    private fun bump(c: Context) {
        val sp = Prefs.of(c)
        sp.edit().putLong("car_photo_ver", sp.getLong("car_photo_ver", 0) + 1).apply()
    }

    /** Marca / modelo / año. Vacío = no se muestra. */
    fun setText(ctx: Context, key: String, value: String) {
        val c = ctx.applicationContext
        Prefs.of(c).edit().putString(key, value.trim()).apply()
        _look.value = _look.value.copy(profile = profile(c).copy(version = _look.value.profile.version))
    }

    fun setHologram(ctx: Context, on: Boolean) {
        val c = ctx.applicationContext
        Prefs.of(c).edit().putBoolean("car_holo", on).apply()
        _look.value = _look.value.copy(profile = _look.value.profile.copy(useHologram = on))
    }

    /** Copia la foto elegida. false si no se pudo leer como imagen. */
    suspend fun savePhoto(ctx: Context, uri: Uri, night: Boolean): Boolean = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        try {
            val bmp = readScaled(c, uri) ?: return@withContext false
            val f = if (night) nightFile(c) else dayFile(c)
            val tmp = File(c.filesDir, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            bump(c)
            reload(c)
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** Día: vuelve a la imagen de fábrica. Noche: deja de usar foto propia (se usa la de día). */
    suspend fun clearPhoto(ctx: Context, night: Boolean) = withContext(Dispatchers.IO) {
        val c = ctx.applicationContext
        try { (if (night) nightFile(c) else dayFile(c)).delete() } catch (e: Exception) { }
        bump(c)
        reload(c)
    }

    /* ---------------------------------------------------------------- imágenes */

    private fun readScaled(c: Context, uri: Uri): Bitmap? {
        val cr = c.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rot = try {
            cr.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0f
        } catch (e: Exception) { 0f }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        return bmp
    }

    private fun rotationOf(e: ExifInterface): Float =
        when (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

    private fun decode(f: File): ImageBitmap? = try {
        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    } catch (e: Throwable) { null }

    /** La versión anterior guardaba la foto como "auto_foto" / "auto.jpg": se pasa a car_day.png. */
    private fun migrateOldPhoto(c: Context) {
        val day = dayFile(c)
        if (day.exists()) return
        val old = listOf(File(c.filesDir, "auto_foto"), File(c.filesDir, "auto.jpg")).firstOrNull { it.exists() } ?: return
        try {
            val uri = Uri.fromFile(old)
            val bmp = readScaled(c, uri)
            if (bmp != null) day.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            old.delete()
            bump(c)
        } catch (e: Throwable) { }
    }

    /** Solo para las capturas de prueba. */
    internal fun setForTest(l: CarLook) { _look.value = l; started = true }
}

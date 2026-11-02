package com.rgservice.carlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foto propia del auto (elegida de la galería). Se guarda una copia exacta dentro de la app
 * (mismas dimensiones que el original) para no depender del archivo original ni de permisos.
 * Sin foto: holograma en modo noche y modelo 3D en modo día.
 */
object CarPhoto {
    /** Solo para mostrar: arriba de esto se lee más liviano (la pantalla es de 1024x600). */
    private const val SHOW_MAX_SIDE = 2560

    private val _photo = MutableStateFlow<Bitmap?>(null)
    val photo: StateFlow<Bitmap?> = _photo.asStateFlow()
    private var loaded = false

    /** Copia exacta de la foto elegida (mismo tamaño y calidad que el original). */
    private fun file(ctx: Context) = File(ctx.filesDir, "auto_foto")

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        val f = file(ctx)
        if (f.exists()) _photo.value = decode(f)
    }

    /** Guarda la foto elegida tal cual (sin achicarla ni recomprimirla). false si no es una imagen. */
    suspend fun save(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val tmp = File(ctx.filesDir, "auto_foto.tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext false
            val bmp = decode(tmp)
            if (bmp == null) { tmp.delete(); return@withContext false }
            val f = file(ctx)
            f.delete()
            if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            _photo.value = bmp
            loaded = true
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** Lee la foto para mostrarla, derecha según el EXIF (las del celular suelen venir acostadas). */
    private fun decode(f: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > SHOW_MAX_SIDE) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            val rotation = try {
                when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }
            if (bmp != null && rotation != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            else bmp
        }
    } catch (e: Throwable) { null }

    /** Solo para las capturas de prueba. */
    internal fun setForTest(b: Bitmap?) { _photo.value = b; loaded = true }

    /** Vuelve al modelo 3D / holograma. */
    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        try { file(ctx).delete() } catch (e: Exception) { }
        try { File(ctx.filesDir, "auto.jpg").delete() } catch (e: Exception) { }
        _photo.value = null
    }
}

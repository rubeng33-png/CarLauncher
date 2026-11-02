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
 * Foto propia del auto (elegida de la galería). Se guarda una copia dentro de la app,
 * achicada y ya derecha, para no depender del archivo original ni de permisos.
 * Sin foto: holograma en modo noche y modelo 3D en modo día.
 */
object CarPhoto {
    private const val MAX_SIDE = 1280

    private val _photo = MutableStateFlow<Bitmap?>(null)
    val photo: StateFlow<Bitmap?> = _photo.asStateFlow()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "auto.jpg")

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        val f = file(ctx)
        if (f.exists()) _photo.value = try { BitmapFactory.decodeFile(f.absolutePath) } catch (e: Throwable) { null }
    }

    /** Copia la imagen elegida. Devuelve false si no se pudo leer. */
    suspend fun save(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            var bmp = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false

            // Las fotos del celular suelen venir "acostadas": se enderezan según el EXIF
            val rotation = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) { 0f }
            if (rotation != 0f) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            }
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)

            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            _photo.value = bmp
            loaded = true
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** Solo para las capturas de prueba. */
    internal fun setForTest(b: Bitmap?) { _photo.value = b; loaded = true }

    /** Vuelve al modelo 3D / holograma. */
    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        try { file(ctx).delete() } catch (e: Exception) { }
        _photo.value = null
    }
}

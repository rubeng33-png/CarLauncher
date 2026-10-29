package com.example.carlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoLoader {
    private var cachedUri: String? = null
    private var cachedBitmap: Bitmap? = null

    suspend fun load(context: Context, uri: Uri): Bitmap? {
        val uriStr = uri.toString()
        cachedBitmap?.let { if (uriStr == cachedUri) return it }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth == -1) return@withContext context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1280 && bounds.outHeight / (sample * 2) >= 960) sample *= 2

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (e: Exception) { null }
        }
        if (bitmap != null) { cachedUri = uriStr; cachedBitmap = bitmap }
        return bitmap
    }

    fun clearCache() { cachedUri = null; cachedBitmap = null }
}

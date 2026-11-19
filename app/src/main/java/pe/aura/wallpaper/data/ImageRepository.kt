package pe.aura.wallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/**
 * Copia las imágenes elegidas al almacenamiento interno de la app (reescaladas),
 * así no dependemos de permisos persistentes sobre la galería.
 */
class ImageRepository(private val context: Context) {

    private val dir get() = File(context.filesDir, "images").apply { mkdirs() }

    fun all(): List<File> =
        dir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    fun import(uris: List<Uri>): Int {
        var count = 0
        uris.forEachIndexed { i, uri ->
            runCatching {
                val bmp = decodeScaled(uri, MAX_SIDE)
                val out = File(dir, "${System.currentTimeMillis()}_$i.jpg")
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                count++
            }
        }
        return count
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder respeta la orientación EXIF.
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("No se pudo leer la imagen")
    }

    companion object {
        private const val MAX_SIDE = 2800
    }
}

package pe.aura.wallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.time.LocalDate
import kotlin.math.max

/**
 * Copia las imágenes elegidas al almacenamiento interno de la app (reescaladas): borrar la
 * foto original de la galería no la quita del fondo. El orden de rotación vive en
 * images/order.json (ver [ImageOrder]), independiente del nombre de los archivos.
 */
class ImageRepository(private val baseDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val dir get() = File(baseDir, "images").apply { mkdirs() }
    private val orderFile get() = File(dir, "order.json")

    private fun files(): List<File> = dir.listFiles { f -> f.extension == "jpg" }?.toList() ?: emptyList()

    /** Orden actual, conciliado con los archivos que realmente existen. */
    fun order(): ImageOrder {
        val stored = if (orderFile.exists()) runCatching { ImageOrder.parse(orderFile.readText()) }.getOrNull() else null
        return (stored ?: ImageOrder()).reconcile(files().map { it.name })
    }

    /** Imágenes en orden de rotación. */
    fun all(): List<File> = order().order.map { File(dir, it) }

    fun forDate(date: LocalDate): File? = order().pick(date)?.let { File(dir, it) }

    /** Agrega al final, sin tocar las existentes. Devuelve cuántas se importaron. */
    fun import(context: Context, uris: List<Uri>): Int {
        val added = mutableListOf<String>()
        uris.forEachIndexed { i, uri ->
            runCatching {
                val bmp = decodeScaled(context, uri, MAX_SIDE)
                val out = File(dir, "${System.currentTimeMillis()}_$i.jpg")
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
                added += out.name
            }
        }
        save(order().add(added))
        return added.size
    }

    fun delete(name: String) {
        File(dir, name).delete()
        save(order().remove(name))
    }

    fun move(name: String, delta: Int) = save(order().move(name, delta))

    fun pinToday(name: String, today: LocalDate = LocalDate.now()) = save(order().pin(name, today))

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun save(order: ImageOrder) {
        orderFile.writeText(order.toJson())
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap {
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

        /** Miniatura pequeña para la cuadrícula de la app. */
        fun thumbnail(file: File, targetPx: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}

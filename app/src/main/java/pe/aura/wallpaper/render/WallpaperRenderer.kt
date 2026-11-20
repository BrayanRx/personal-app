package pe.aura.wallpaper.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import pe.aura.wallpaper.R
import pe.aura.wallpaper.data.Activity
import pe.aura.wallpaper.data.DailyContent
import pe.aura.wallpaper.data.Style
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class RenderOptions(
    val showClock: Boolean = false,
    val use24h: Boolean = true,
    val style: Style = Style.MINIMAL,
    val showAgenda: Boolean = true,
)

/**
 * Dibuja el fondo completo en un Canvas. Las medidas son proporcionales al ancho,
 * así el mismo código sirve para la pantalla real y para la vista previa reducida.
 *
 * Distribución vertical (pensada para convivir con el bloqueo del sistema):
 *  - arriba: reloj del sistema (o el nuestro si showClock)
 *  - centro: la imagen respira
 *  - abajo: agenda de hoy + frase, terminando en BOTTOM para no chocar con
 *    el indicador de carga, los accesos directos ni las notificaciones en vivo.
 */
class WallpaperRenderer(private val context: Context) {

    private val thin: Typeface = font(R.font.jost_extralight, "sans-serif-thin")
    private val light: Typeface = font(R.font.jost_light, "sans-serif-light")
    private val regular: Typeface = font(R.font.jost_regular, "sans-serif")

    private val accent = Color.rgb(242, 160, 107)

    private var sceneKey: String? = null
    private var scene: Bitmap? = null
    private var blurred: Bitmap? = null
    private var scrimKey = ""
    private var topScrim: Paint? = null
    private var bottomScrim: Paint? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun invalidate() {
        sceneKey = null
    }

    /** Renderiza a un Bitmap (fondo de bloqueo estático y vista previa). */
    fun render(width: Int, height: Int, content: DailyContent, now: LocalDateTime, opts: RenderOptions): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), width, height, content, now, opts)
        return bmp
    }

    fun draw(canvas: Canvas, w: Int, h: Int, content: DailyContent, now: LocalDateTime, opts: RenderOptions) {
        prepareScene(content.image, w, h)
        canvas.drawBitmap(scene!!, 0f, 0f, bitmapPaint)
        drawScrims(canvas, w, h)

        val glass = opts.style == Style.GLASS
        val top = if (opts.showClock) drawClock(canvas, w, h, now, opts) else h * TOP_FREE
        val bottom = h * BOTTOM

        // La frase se ancla abajo; la agenda ocupa el espacio que queda encima.
        val quoteTop = content.quote?.let {
            if (glass) drawQuoteGlass(canvas, w, bottom, it.text, it.author)
            else drawQuoteMinimal(canvas, w, bottom, it.text, it.author)
        } ?: bottom

        if (opts.showAgenda && content.activities.isNotEmpty()) {
            val gap = w * if (glass) 0.03f else 0.07f
            drawAgenda(canvas, w, top + w * 0.04f, quoteTop - gap, content.activities, now, glass)
        }
    }

    // ---------- Reloj propio (opcional) ----------

    /** Devuelve el borde inferior ocupado. */
    private fun drawClock(c: Canvas, w: Int, h: Int, now: LocalDateTime, o: RenderOptions): Float {
        val cx = w / 2f
        val clock = textPaint(thin, w * 0.25f, 0.02f, alpha = 245, shadow = true)
        val baseline = h * 0.25f
        drawCentered(c, time(now, o.use24h), cx, baseline, clock)
        val date = textPaint(light, w * 0.030f, 0.42f, alpha = 215, shadow = true)
        val dateText = now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", ES)).uppercase(ES)
        drawCentered(c, dateText, cx, baseline + w * 0.085f, date)
        return baseline + w * 0.10f
    }

    // ---------- Frase ----------

    /** Estilo "SILENCE": centrado, sin tarjeta. Devuelve el borde superior. */
    private fun drawQuoteMinimal(c: Canvas, w: Int, bottom: Float, text: String, author: String?): Float {
        val cx = w / 2f
        val body = textPaint(light, w * 0.044f, 0.02f, alpha = 240, shadow = true)
        val layout = staticLayout(text, body, (w * 0.76f).toInt(), Layout.Alignment.ALIGN_CENTER)
        val authorPaint = textPaint(regular, w * 0.024f, 0.38f, alpha = 190, shadow = true)
        val authorText = author?.uppercase(ES)

        val dividerGap = w * 0.05f
        val authorGap = w * 0.05f
        val blockH = dividerGap + layout.height + (if (authorText != null) authorGap + authorPaint.textSize else 0f)
        val top = bottom - blockH
        var y = top

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = dp(1.5f); alpha = 230 }
        c.drawLine(cx - w * 0.035f, y, cx + w * 0.035f, y, line)
        y += dividerGap

        c.save()
        c.translate(cx - layout.width / 2f, y)
        layout.draw(c)
        c.restore()
        y += layout.height

        if (authorText != null) drawCentered(c, authorText, cx, y + authorGap + authorPaint.textSize * 0.3f, authorPaint)
        return top
    }

    /** Tarjeta de cristal compacta. Devuelve el borde superior. */
    private fun drawQuoteGlass(c: Canvas, w: Int, bottom: Float, text: String, author: String?): Float {
        val m = w * MARGIN
        val p = w * PAD
        val cardW = w - 2 * m
        val body = textPaint(light, w * 0.040f, 0.01f, alpha = 245)
        val layout = staticLayout(text, body, (cardW - 2 * p).toInt(), Layout.Alignment.ALIGN_NORMAL)
        val mark = textPaint(regular, w * 0.075f, 0f, alpha = 255).apply { color = accent }
        val authorPaint = textPaint(regular, w * 0.023f, 0.30f, alpha = 185)
        val authorText = author?.uppercase(ES)

        val markH = w * 0.045f
        val authorBlock = if (authorText != null) w * 0.035f + authorPaint.textSize else 0f
        val cardH = p + markH + layout.height + authorBlock + p * 0.9f
        val card = RectF(m, bottom - cardH, m + cardW, bottom)
        glassCard(c, card, w)

        c.drawText("“", card.left + p - w * 0.006f, card.top + p + markH * 1.25f, mark)
        c.save()
        c.translate(card.left + p, card.top + p + markH)
        layout.draw(c)
        c.restore()
        if (authorText != null) {
            c.drawText(authorText, card.left + p, card.top + p + markH + layout.height + w * 0.035f + authorPaint.textSize * 0.6f, authorPaint)
        }
        return card.top
    }

    // ---------- Agenda de hoy ----------

    /**
     * Dibuja la agenda anclada a [bottom], usando como máximo el espacio hasta [top].
     * Si no caben todas, muestra desde la actividad en curso y añade "+N".
     */
    private fun drawAgenda(
        c: Canvas, w: Int, top: Float, bottom: Float,
        items: List<Activity>, now: LocalDateTime, glass: Boolean,
    ) {
        val p = w * PAD
        val rowH = w * 0.068f
        val headerH = w * 0.065f
        val padV = if (glass) p * 0.8f else 0f
        val available = bottom - top - headerH - 2 * padV
        val fit = min(MAX_ROWS, (available / rowH).toInt())
        if (fit <= 0) return

        // Una actividad "ya pasó" si empezó hace más de una hora.
        val nowT = now.toLocalTime()
        val cutoff = if (nowT.hour == 0) LocalTime.MIN else nowT.minusMinutes(60)
        val firstActive = items.indexOfFirst { it.time == null || !it.time.isBefore(cutoff) }
            .let { if (it < 0) items.size else it }
        val start = max(0, min(firstActive, items.size - fit))
        val visible = items.drop(start).take(fit)
        val hidden = items.size - visible.size
        val nextIndex = items.indexOfFirst { it.time != null && !it.time.isBefore(nowT) }

        val blockH = 2 * padV + headerH + visible.size * rowH
        val m = w * MARGIN
        val box = if (glass) RectF(m, bottom - blockH, w - m, bottom)
        else RectF(w * 0.14f, bottom - blockH, w * 0.86f, bottom)
        if (glass) glassCard(c, box, w)

        val left = box.left + if (glass) p else 0f
        val right = box.right - if (glass) p else 0f
        val shadow = !glass

        // Encabezado: "HOY · VIERNES 20"            "+2"
        val header = textPaint(regular, w * 0.023f, 0.35f, alpha = 170, shadow = shadow)
        val headerText = "HOY · " + now.format(DateTimeFormatter.ofPattern("EEEE d", ES)).uppercase(ES)
        var y = box.top + padV + header.textSize
        c.drawText(headerText, left, y, header)
        if (hidden > 0) {
            val more = "+$hidden"
            c.drawText(more, right - header.measureText(more), y, header)
        }
        y = box.top + padV + headerH

        val timePaint = textPaint(regular, w * 0.029f, 0.04f, alpha = 210, shadow = shadow)
        val titlePaint = textPaint(light, w * 0.036f, 0.01f, alpha = 240, shadow = shadow)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val timeColW = w * 0.13f
        val dotX = left + timeColW + w * 0.012f
        val titleX = dotX + w * 0.035f

        visible.forEachIndexed { i, a ->
            val index = start + i
            val past = a.time != null && a.time.isBefore(cutoff) && index < firstActive
            val isNext = index == nextIndex
            val alphaMul = if (past) 0.45f else 1f
            val baseline = y + rowH * 0.66f

            timePaint.color = if (isNext) accent else Color.WHITE
            timePaint.alpha = ((if (isNext) 255 else 200) * alphaMul).toInt()
            c.drawText(a.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—", left, baseline, timePaint)

            dot.color = a.category.color
            dot.alpha = (255 * alphaMul).toInt()
            c.drawCircle(dotX, baseline - titlePaint.textSize * 0.32f, w * 0.008f, dot)

            titlePaint.alpha = (240 * alphaMul).toInt()
            val title = TextUtils.ellipsize(a.title, titlePaint, right - titleX, TextUtils.TruncateAt.END).toString()
            c.drawText(title, titleX, baseline, titlePaint)
            y += rowH
        }
    }

    // ---------- Cristal ----------

    private fun glassCard(c: Canvas, rect: RectF, w: Int) {
        val r = w * 0.05f
        val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        blurred?.let { c.drawBitmap(it, null, RectF(0f, 0f, w.toFloat(), scene!!.height.toFloat()), bitmapPaint) }
        c.restore()
        // Tinte oscuro suave para que el texto blanco se lea incluso sobre zonas claras.
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 18, 20, 28) })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(1f); color = Color.WHITE; alpha = 60
        })
    }

    // ---------- Fondo ----------

    private fun prepareScene(file: File?, w: Int, h: Int) {
        val key = "${file?.path}:${file?.lastModified()}:$w:$h"
        if (key == sceneKey && scene != null) return
        scene?.recycle(); blurred?.recycle()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val src = file?.let { decodeForSize(it, w, h) }
        if (src != null) {
            // Center-crop
            val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
            }
            canvas.drawBitmap(src, m, bitmapPaint)
            src.recycle()
        } else {
            drawDefaultScene(canvas, w, h)
        }
        scene = out

        // Desenfoque: se reduce a 1/6, se aplica un desenfoque de caja de 3 pasadas
        // (aproxima un gaussiano) y al ampliarlo con filtrado bilineal queda suave, sin píxeles.
        val small = Bitmap.createScaledBitmap(out, max(1, w / 6), max(1, h / 6), true)
        blurred = Blur.apply(small, radius = max(2, w / 6 / 22))
        small.recycle()
        sceneKey = key
    }

    private fun decodeForSize(file: File, w: Int, h: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Fondo por defecto (amanecer de montaña) mientras no haya imágenes importadas. */
    private fun drawDefaultScene(c: Canvas, w: Int, h: Int) {
        val sky = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.rgb(36, 40, 64), Color.rgb(110, 86, 120), Color.rgb(232, 146, 98), Color.rgb(30, 30, 40)),
                floatArrayOf(0f, 0.38f, 0.55f, 0.8f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sky)
        fun ridge(color: Int, pts: List<Pair<Float, Float>>) {
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                pts.forEach { (x, y) -> lineTo(x * w, y * h) }
                lineTo(w.toFloat(), h.toFloat()); close()
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        ridge(Color.rgb(74, 62, 84), listOf(0f to 0.62f, 0.25f to 0.55f, 0.52f to 0.36f, 0.62f to 0.45f, 0.8f to 0.52f, 1f to 0.58f))
        ridge(Color.rgb(44, 40, 54), listOf(0f to 0.70f, 0.3f to 0.62f, 0.55f to 0.68f, 0.85f to 0.6f, 1f to 0.66f))
        ridge(Color.rgb(24, 24, 32), listOf(0f to 0.80f, 0.4f to 0.74f, 0.7f to 0.79f, 1f to 0.75f))
    }

    private fun drawScrims(c: Canvas, w: Int, h: Int) {
        val key = "$w:$h"
        if (key != scrimKey) {
            topScrim = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h * 0.42f, Color.argb(110, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            bottomScrim = Paint().apply {
                shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP)
            }
            scrimKey = key
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.42f, topScrim!!)
        c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), bottomScrim!!)
    }

    // ---------- Utilidades ----------

    private fun time(now: LocalDateTime, use24h: Boolean): String =
        now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))

    private fun textPaint(tf: Typeface, size: Float, spacing: Float, alpha: Int, shadow: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tf
            textSize = size
            letterSpacing = spacing
            color = Color.WHITE
            this.alpha = alpha
            if (shadow) setShadowLayer(dp(10f), 0f, dp(1f), Color.argb(90, 0, 0, 0))
        }

    /** Centra compensando el espaciado extra que letterSpacing deja tras el último carácter. */
    private fun drawCentered(c: Canvas, text: String, cx: Float, baseline: Float, paint: Paint) {
        val width = paint.measureText(text) - paint.letterSpacing * paint.textSize
        c.drawText(text, cx - width / 2f, baseline, paint)
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()

    companion object {
        /** Fecha siempre en español, aunque el teléfono esté en otro idioma. */
        private val ES: Locale = Locale.forLanguageTag("es-PE")

        /** Debajo de esto empieza la zona libre (bajo el reloj del sistema). */
        private const val TOP_FREE = 0.39f

        /** Borde inferior del contenido: libre el indicador de carga y la barra de accesos. */
        private const val BOTTOM = 0.80f

        private const val MARGIN = 0.07f
        private const val PAD = 0.05f
        private const val MAX_ROWS = 6
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    private fun font(res: Int, fallback: String): Typeface =
        runCatching { ResourcesCompat.getFont(context, res) }.getOrNull()
            ?: Typeface.create(fallback, Typeface.NORMAL)
}

/** Desenfoque de caja separable en 3 pasadas (≈ gaussiano). Pensado para bitmaps pequeños. */
internal object Blur {
    fun apply(src: Bitmap, radius: Int, passes: Int = 3): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            boxBlur(a, b, w, h, radius, horizontal = true)
            boxBlur(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Media móvil por fila (o columna) con bordes repetidos. */
    internal fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + k else k * w + line
            }
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[idx(i)]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[idx(i + r + 1)]
                val rem = src[idx(i - r)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}

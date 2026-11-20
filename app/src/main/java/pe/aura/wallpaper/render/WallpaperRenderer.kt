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
import androidx.core.content.res.ResourcesCompat
import pe.aura.wallpaper.R
import pe.aura.wallpaper.data.DailyContent
import pe.aura.wallpaper.data.Style
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

data class RenderOptions(
    val showClock: Boolean = true,
    val use24h: Boolean = true,
    val style: Style = Style.MINIMAL,
)

/**
 * Dibuja el fondo completo en un Canvas. Las medidas son proporcionales al ancho,
 * así el mismo código sirve para la pantalla real y para la vista previa reducida.
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
        when (opts.style) {
            Style.MINIMAL -> drawMinimal(canvas, w, h, content, now, opts)
            Style.GLASS -> drawGlass(canvas, w, h, content, now, opts)
        }
    }

    // ---------- Estilo MINIMAL (tipo "SILENCE") ----------

    private fun drawMinimal(c: Canvas, w: Int, h: Int, content: DailyContent, now: LocalDateTime, o: RenderOptions) {
        val cx = w / 2f
        if (o.showClock) {
            val clock = textPaint(thin, w * 0.25f, 0.02f, alpha = 245, shadow = true)
            val baseline = h * 0.25f
            drawCentered(c, time(now, o.use24h), cx, baseline, clock)

            val date = textPaint(light, w * 0.030f, 0.42f, alpha = 215, shadow = true)
            val dateText = now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", ES))
                .uppercase(ES)
            drawCentered(c, dateText, cx, baseline + w * 0.085f, date)
        }

        val quote = content.quote ?: return
        val body = textPaint(light, w * 0.050f, 0.02f, alpha = 240, shadow = true)
        val layout = staticLayout(quote.text, body, (w * 0.76f).toInt(), Layout.Alignment.ALIGN_CENTER)
        val author = quote.author?.uppercase(ES)
        val authorPaint = textPaint(regular, w * 0.026f, 0.38f, alpha = 190, shadow = true)

        val dividerGap = w * 0.06f
        val authorGap = w * 0.055f
        val blockH = dividerGap + layout.height + (if (author != null) authorGap + authorPaint.textSize else 0f)
        var y = QUOTE_BOTTOM_MINIMAL * h - blockH

        // Línea fina de acento sobre la frase
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; strokeWidth = dp(1.5f); alpha = 230
        }
        c.drawLine(cx - w * 0.035f, y, cx + w * 0.035f, y, line)
        y += dividerGap

        c.save()
        c.translate(cx - layout.width / 2f, y)
        layout.draw(c)
        c.restore()
        y += layout.height

        if (author != null) drawCentered(c, author, cx, y + authorGap + authorPaint.textSize * 0.3f, authorPaint)
    }

    // ---------- Estilo GLASS (tarjetas de cristal esmerilado) ----------

    private fun drawGlass(c: Canvas, w: Int, h: Int, content: DailyContent, now: LocalDateTime, o: RenderOptions) {
        val m = w * 0.07f
        val p = w * 0.06f
        val radius = w * 0.06f

        if (o.showClock) {
            val clock = textPaint(thin, w * 0.20f, 0.0f, alpha = 245)
            val date = textPaint(light, w * 0.038f, 0.02f, alpha = 220)
            val top = h * 0.10f
            val clockBase = top + p + clock.textSize * 0.78f
            val dateBase = clockBase + w * 0.08f
            val lineY = dateBase + w * 0.06f
            val card = RectF(m, top, m + w * 0.64f, lineY + p * 0.8f)
            glassCard(c, card, radius, w, h)

            c.drawText(time(now, o.use24h), card.left + p * 0.85f, clockBase, clock)
            val dateText = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ES))
                .replaceFirstChar { it.titlecase(ES) }
            c.drawText(dateText, card.left + p, dateBase, date)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 200; strokeWidth = dp(1.5f) }
            c.drawLine(card.left + p, lineY, card.left + p + w * 0.09f, lineY, line)
        }

        val quote = content.quote ?: return
        val cardW = w - 2 * m
        val body = textPaint(light, w * 0.050f, 0.01f, alpha = 240)
        val layout = staticLayout(quote.text, body, (cardW - 2 * p).toInt(), Layout.Alignment.ALIGN_NORMAL)
        val mark = textPaint(regular, w * 0.11f, 0f, alpha = 255).apply { color = accent }
        val authorPaint = textPaint(regular, w * 0.026f, 0.30f, alpha = 185)
        val author = quote.author?.uppercase(ES)

        val markH = w * 0.07f
        val authorBlock = if (author != null) w * 0.05f + authorPaint.textSize else 0f
        val cardH = p + markH + layout.height + authorBlock + p
        val card = RectF(m, h * QUOTE_BOTTOM_GLASS - cardH, m + cardW, h * QUOTE_BOTTOM_GLASS)
        glassCard(c, card, radius, w, h)

        c.drawText("“", card.left + p - w * 0.008f, card.top + p + markH * 1.15f, mark)
        c.save()
        c.translate(card.left + p, card.top + p + markH)
        layout.draw(c)
        c.restore()
        if (author != null) {
            c.drawText(author, card.left + p, card.top + p + markH + layout.height + w * 0.05f + authorPaint.textSize * 0.5f, authorPaint)
        }
    }

    private fun glassCard(c: Canvas, rect: RectF, r: Float, w: Int, h: Int) {
        val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        blurred?.let { c.drawBitmap(it, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), bitmapPaint) }
        c.restore()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 26 })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(1f); color = Color.WHITE; alpha = 70
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

        // Desenfoque barato: reducir en dos pasos y ampliar con filtrado bilineal.
        val mid = Bitmap.createScaledBitmap(out, max(1, w / 8), max(1, h / 8), true)
        blurred = Bitmap.createScaledBitmap(mid, max(1, w / 28), max(1, h / 28), true)
        mid.recycle()
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
                shader = LinearGradient(0f, h * 0.5f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP)
            }
            scrimKey = key
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.42f, topScrim!!)
        c.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), bottomScrim!!)
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
            .setLineSpacing(0f, 1.22f)
            .setIncludePad(false)
            .build()

    companion object {
        /** Fecha siempre en español, aunque el teléfono esté en otro idioma. */
        private val ES: Locale = Locale.forLanguageTag("es-PE")

        /**
         * Borde inferior de la frase (fracción del alto). Deja libre la franja de abajo del
         * bloqueo: indicador de carga, accesos directos y notificaciones en vivo.
         */
        private const val QUOTE_BOTTOM_MINIMAL = 0.73f
        private const val QUOTE_BOTTOM_GLASS = 0.75f
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    private fun font(res: Int, fallback: String): Typeface =
        runCatching { ResourcesCompat.getFont(context, res) }.getOrNull()
            ?: Typeface.create(fallback, Typeface.NORMAL)
}

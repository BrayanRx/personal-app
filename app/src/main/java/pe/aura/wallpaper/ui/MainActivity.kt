package pe.aura.wallpaper.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aura.wallpaper.R
import pe.aura.wallpaper.data.DailySelector
import pe.aura.wallpaper.data.ImageRepository
import pe.aura.wallpaper.data.QuoteRepository
import pe.aura.wallpaper.data.Settings
import pe.aura.wallpaper.data.Style
import pe.aura.wallpaper.render.RenderOptions
import pe.aura.wallpaper.render.WallpaperRenderer
import pe.aura.wallpaper.wallpaper.AuraWallpaperService
import pe.aura.wallpaper.work.LockScreen
import java.time.LocalDateTime
import java.util.Locale

internal val Jost = FontFamily(
    Font(R.font.jost_extralight, FontWeight.ExtraLight),
    Font(R.font.jost_light, FontWeight.Light),
    Font(R.font.jost_regular, FontWeight.Normal),
)
internal val Accent = Color(0xFFF2A06B)
internal val ES: Locale = Locale.forLanguageTag("es-PE")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color(0xFF1B1210),
                    background = Color(0xFF0E0F12),
                    surface = Color(0xFF16171C),
                ),
            ) { AuraApp() }
        }
    }
}

@Composable
private fun AuraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    var version by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    // Cualquier cambio de datos: redibuja el fondo animado y, si está activo, regenera el bloqueo estático.
    fun dataChanged() {
        settings.bumpDataVersion()
        version++
        if (settings.lockAutoUpdate) scope.launch(Dispatchers.Default) { runCatching { LockScreen.apply(context) } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "A U R A",
            style = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 6.sp),
            color = Color.White,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Fondo", "Agenda").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label, style = body()) }
            }
        }
        when (tab) {
            0 -> FondoScreen(settings, version, ::dataChanged)
            else -> AgendaScreen(version, ::dataChanged)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColumnScope.FondoScreen(settings: Settings, version: Int, dataChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember { ImageRepository(context) }
    val quotes = remember { QuoteRepository(context) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var imageCount by remember { mutableIntStateOf(0) }
    var quoteCount by remember { mutableIntStateOf(0) }
    var customQuotes by remember { mutableStateOf(false) }

    var showClock by remember { mutableStateOf(settings.showClock) }
    var use24h by remember { mutableStateOf(settings.use24h) }
    var style by remember { mutableStateOf(settings.style) }
    var lockAuto by remember { mutableStateOf(settings.lockAutoUpdate) }
    var showAgenda by remember { mutableStateOf(settings.showAgenda) }

    LaunchedEffect(version, showClock, use24h, style, showAgenda) {
        val bmp = withContext(Dispatchers.Default) {
            imageCount = images.all().size
            quoteCount = quotes.all().size
            customQuotes = quotes.hasCustomQuotes()
            val (w, h) = LockScreen.screenSize(context)
            WallpaperRenderer(context).render(
                w / 2, h / 2, DailySelector.forDate(context), LocalDateTime.now(),
                RenderOptions(showClock, use24h, style, showAgenda),
            )
        }
        preview = bmp
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 60),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) { images.import(uris) }
            toast(context, "$n imágenes importadas")
            dataChanged()
        }
    }

    fun importQuotes(json: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { quotes.import(json) } }
            result.onSuccess { toast(context, "$it frases nuevas"); dataChanged() }
                .onFailure { toast(context, "JSON inválido: ${it.message}") }
        }
    }

    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.getOrNull()
        if (text == null) toast(context, "No se pudo leer el archivo") else importQuotes(text)
    }

    run {

        // Vista previa
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(preview?.let { it.width / it.height.toFloat() } ?: (9f / 19.5f))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1D1F26)),
        ) {
            preview?.let {
                Image(it.asImageBitmap(), contentDescription = "Vista previa", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }

        Section("Aplicar") {
            Button(
                onClick = { setLiveWallpaper(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Text("Usar como fondo animado (hora en vivo)", style = body()) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { runCatching { LockScreen.apply(context) }.isSuccess }
                        toast(context, if (ok) "Fondo de bloqueo aplicado" else "No se pudo aplicar")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            ) { Text("Aplicar solo a pantalla de bloqueo (estático)", style = body(), color = Color.White) }
            ToggleRow("Actualizar el bloqueo cada medianoche", lockAuto) {
                lockAuto = it
                settings.lockAutoUpdate = it
                if (it) LockScreen.schedule(context) else LockScreen.cancel(context)
            }
            Hint("El modo estático no dibuja reloj: tu teléfono ya muestra el suyo en el bloqueo.")
        }

        Section("Fondos · $imageCount") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                ) { Text("Importar imágenes", style = body()) }
                if (imageCount > 0) TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { images.clear() }; dataChanged() }
                }) { Text("Borrar todo", style = body(), color = Accent) }
            }
            Hint("Rotan una por día en el orden en que las importaste.")
        }

        Section("Frases · $quoteCount${if (customQuotes) "" else " (de ejemplo)"}") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                ) { Text("Archivo JSON", style = body()) }
                Button(
                    onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (text.isNullOrBlank()) toast(context, "El portapapeles está vacío") else importQuotes(text)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                ) { Text("Pegar JSON", style = body()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Prompt Aura", CLAUDE_PROMPT))
                    toast(context, "Prompt copiado: pégalo en Claude")
                }) { Text("Copiar prompt para Claude", style = body(), color = Accent) }
                if (customQuotes) TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { quotes.clear() }; dataChanged() }
                }) { Text("Borrar frases", style = body(), color = Color.White.copy(alpha = 0.6f)) }
            }
            Hint("Flujo: copia el prompt → pídeselo a Claude → copia su respuesta → \"Pegar JSON\".")
        }

        Section("Estilo") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Style.MINIMAL to "Minimal", Style.GLASS to "Cristal").forEachIndexed { i, (s, label) ->
                    SegmentedButton(
                        selected = style == s,
                        onClick = { style = s; settings.style = s },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label, style = body()) }
                }
            }
            ToggleRow("Mostrar hora en el fondo", showClock) { showClock = it; settings.showClock = it }
            ToggleRow("Mostrar agenda de hoy", showAgenda) { showAgenda = it; settings.showAgenda = it }
            ToggleRow("Formato 24 horas", use24h) { use24h = it; settings.use24h = it }
            Hint("Si en tu bloqueo ves dos relojes, desactiva \"Mostrar hora\" (el del sistema no se puede ocultar desde una app).")
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title.uppercase(),
            style = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 3.sp),
            color = Color.White.copy(alpha = 0.55f),
        )
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = body(), color = Color.White, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Hint(text: String) =
    Text(text, style = body().copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.45f))

internal fun body() = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 15.sp)

internal fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun setLiveWallpaper(context: Context) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, AuraWallpaperService::class.java),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            .onFailure { toast(context, "Tu teléfono no permite fondos animados") }
    }
}

private const val CLAUDE_PROMPT = """Genera 60 frases motivadoras en español, breves (máximo 18 palabras), sobre disciplina, crecimiento, calma y construir cosas que importan. Evita clichés repetidos.

Responde ÚNICAMENTE con JSON válido, sin texto adicional, con este formato exacto:
{
  "version": 1,
  "quotes": [
    { "text": "La frase aquí.", "author": "Autor o Anónimo" }
  ]
}

Opcional: añade "date": "AAAA-MM-DD" a una frase para que aparezca exactamente ese día."""

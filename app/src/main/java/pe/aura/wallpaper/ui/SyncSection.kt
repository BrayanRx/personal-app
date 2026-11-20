package pe.aura.wallpaper.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aura.wallpaper.data.Settings
import pe.aura.wallpaper.sync.SyncCore
import pe.aura.wallpaper.sync.VaultSync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sección "Sincronización": carpeta de Obsidian elegida, estado de la última lectura y acciones.
 * [syncVersion] cambia cada vez que cambia el estado guardado, para releerlo.
 */
@Composable
fun SyncSection(settings: Settings, syncVersion: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun syncNow(force: Boolean) {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { VaultSync.sync(context, force = force) }
            busy = false
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cr = context.contentResolver
        // Soltar la carpeta anterior, si había otra.
        settings.syncTreeUri?.takeIf { it != uri.toString() }?.let { old ->
            runCatching { cr.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { toast(context, "No se pudo guardar el permiso de la carpeta"); return@rememberLauncherForActivityResult }
        settings.syncTreeUri = uri.toString()
        settings.resetSyncSignatures()
        syncNow(force = true)
    }

    // Se relee en cada cambio de estado (syncVersion).
    val tree = remember(syncVersion) { settings.syncTreeUri }
    val lastTime = remember(syncVersion) { settings.syncLastTime }
    val activities = remember(syncVersion) { settings.syncActivityCount }
    val quotes = remember(syncVersion) { settings.syncQuoteCount }
    val error = remember(syncVersion) { settings.syncError }

    Section("Sincronización con Obsidian") {
        if (tree == null) {
            Hint(
                "Elige la carpeta de tu bóveda donde están las notas «${SyncCore.AGENDA_MARKER}…» y " +
                    "«${SyncCore.QUOTES_MARKER}…». Aura las leerá sola cada vez que Obsidian Sync las actualice.",
            )
            Button(onClick = { pickFolder.launch(null) }, colors = subtleButton()) { Text("Elegir carpeta", style = body()) }
            return@Section
        }

        Text(VaultSync.folderLabel(tree), style = body(), color = Color.White)
        if (lastTime > 0) {
            val whenText = DateTimeFormatter.ofPattern("d MMM, HH:mm", ES)
                .format(Instant.ofEpochMilli(lastTime).atZone(ZoneId.systemDefault()))
            val counts = buildList {
                if (activities >= 0) add("$activities actividades")
                if (quotes >= 0) add("$quotes frases")
            }.joinToString(" · ")
            Hint("Última revisión: $whenText" + if (counts.isNotEmpty()) "\nLeídas: $counts" else "")
        }
        if (error != null) {
            Text(error, style = body().copy(fontSize = 13.sp), color = Color(0xFFFF8A80))
            Hint("Tus datos actuales no se tocaron.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { syncNow(force = true) }, enabled = !busy, colors = subtleButton()) {
                Text(if (busy) "Sincronizando…" else "Sincronizar ahora", style = body())
            }
            TextButton(onClick = { pickFolder.launch(null) }) { Text("Cambiar", style = body(), color = Accent) }
            TextButton(onClick = {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                settings.clearSync()
            }) { Text("Quitar", style = body(), color = Color.White.copy(alpha = 0.6f)) }
        }
        Hint("Se revisa al abrir Aura, al encender la pantalla (máx. cada 15 min) y antes de generar el bloqueo. Solo se lee si la nota cambió.")
    }
}

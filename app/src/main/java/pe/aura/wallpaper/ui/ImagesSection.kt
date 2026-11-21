package pe.aura.wallpaper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aura.wallpaper.data.ImageRepository
import java.io.File
import java.time.LocalDate

private const val COLUMNS = 4

/**
 * Fondos uno por uno: cuadrícula en orden de rotación, marcando el de hoy.
 * Tocar una miniatura → Usar hoy / Mover antes / Mover después / Eliminar.
 */
@Composable
fun ImagesSection(images: ImageRepository, version: Int, onAdd: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val state by produceState(Pair(emptyList<File>(), null as String?), version) {
        value = withContext(Dispatchers.IO) { images.all() to images.order().pick(today) }
    }
    val (files, todayName) = state
    var selected by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun act(block: () -> Unit) {
        scope.launch { withContext(Dispatchers.IO) { block() }; onChanged() }
    }

    Section("Fondos · ${files.size}") {
        if (files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.chunked(COLUMNS).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEachIndexed { col, f ->
                            Thumbnail(
                                file = f,
                                position = row * COLUMNS + col + 1,
                                isToday = f.name == todayName,
                                modifier = Modifier.weight(1f),
                                onClick = { selected = f },
                            )
                        }
                        repeat(COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd, colors = subtleButton()) { Text("Agregar", style = body()) }
            if (files.isNotEmpty()) TextButton(onClick = { confirmClear = true }) {
                Text("Borrar todo", style = body(), color = Color.White.copy(alpha = 0.6f))
            }
        }
        Hint("Rotan una por día en este orden. Las imágenes se copian dentro de Aura: puedes borrar las originales de tu galería.")
    }

    selected?.let { f ->
        val index = files.indexOf(f)
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Color(0xFF1D1F26),
            title = { Text("Fondo ${index + 1} de ${files.size}", style = body().copy(fontSize = 20.sp), color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DialogAction("Usar hoy", f.name != todayName) { act { images.pinToday(f.name) }; selected = null }
                    DialogAction("Mover antes", index > 0) { act { images.move(f.name, -1) }; selected = null }
                    DialogAction("Mover después", index < files.lastIndex) { act { images.move(f.name, +1) }; selected = null }
                    DialogAction("Eliminar", true, danger = true) { confirmDelete = f; selected = null }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cerrar", color = Color.White.copy(alpha = 0.7f)) } },
        )
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF1D1F26),
            title = { Text("¿Eliminar este fondo?", style = body().copy(fontSize = 20.sp), color = Color.White) },
            text = { Hint("Se quita de Aura. La foto original de tu galería no se toca.") },
            confirmButton = {
                TextButton(onClick = { act { images.delete(f.name) }; confirmDelete = null }) { Text("Eliminar", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar", color = Color.White.copy(alpha = 0.7f)) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF1D1F26),
            title = { Text("¿Borrar todos los fondos?", style = body().copy(fontSize = 20.sp), color = Color.White) },
            text = { Hint("Se quitan ${files.size} imágenes de Aura. Las originales de tu galería no se tocan.") },
            confirmButton = {
                TextButton(onClick = { act { images.clear() }; confirmClear = false }) { Text("Borrar todo", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = Color.White.copy(alpha = 0.7f)) } },
        )
    }
}

@Composable
private fun Thumbnail(file: File, position: Int, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ImageRepository.thumbnail(file, 240)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .then(if (isToday) Modifier.border(2.dp, Accent, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        bitmap?.let { Image(it, contentDescription = "Fondo $position", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Text(
            if (isToday) "HOY" else "$position",
            fontSize = 11.sp,
            color = if (isToday) Color(0xFF1B1210) else Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isToday) Accent else Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun DialogAction(label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(
            label, style = body(),
            color = when {
                !enabled -> Color.White.copy(alpha = 0.25f)
                danger -> Color(0xFFFF8A80)
                else -> Color.White
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

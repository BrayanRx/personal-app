package pe.aura.wallpaper.ui

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aura.wallpaper.data.Activity
import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.Category
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun AgendaScreen(version: Int, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ActivityRepository(context) }

    // version fuerza la relectura tras cualquier cambio
    val all = remember(version) { repo.all() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<Activity?>(null) }

    fun importJson(text: String) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { repo.import(text) } }
                .onSuccess { toast(context, "$it actividades importadas"); onDataChanged() }
                .onFailure { toast(context, "JSON inválido: ${it.message}") }
        }
    }

    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
            .onSuccess { importJson(it) }
            .onFailure { toast(context, "No se pudo leer el archivo") }
    }

    Section("Calendario") {
        MonthCalendar(
            month = month,
            selected = selected,
            datedPlans = all.mapNotNull { it.date }.toSet(),
            onMonth = { month = it },
            onSelect = { selected = it },
        )
    }

    val dayItems = ActivityRepository.forDate(all, selected)
    Section(selected.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.titlecase(ES) }) {
        if (dayItems.isEmpty()) Hint("Nada programado. Día libre.")
        dayItems.forEach { a -> ActivityRow(a) { editing = a } }
        Button(
            onClick = { editing = Activity(title = "", date = selected) },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Añadir actividad", style = body()) }
    }

    Section("Importar rutina") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")) },
                colors = subtleButton(),
            ) { Text("Archivo JSON", style = body()) }
            Button(
                onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) toast(context, "El portapapeles está vacío") else importJson(text)
                },
                colors = subtleButton(),
            ) { Text("Pegar JSON", style = body()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Prompt rutina", ROUTINE_PROMPT))
                toast(context, "Prompt copiado: pégalo en Claude")
            }) { Text("Copiar prompt para Claude", style = body(), color = Accent) }
            if (all.isNotEmpty()) TextButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { repo.clear() }; onDataChanged() }
            }) { Text("Borrar todo", style = body(), color = Color.White.copy(alpha = 0.6f)) }
        }
        Hint("Claude te hará preguntas sobre tus horarios y te devolverá el JSON listo para pegar.")
    }

    editing?.let { current ->
        ActivityDialog(
            initial = current,
            isNew = all.none { it.id == current.id },
            onDismiss = { editing = null },
            onSave = { a ->
                editing = null
                scope.launch { withContext(Dispatchers.IO) { repo.upsert(a) }; onDataChanged() }
            },
            onDelete = {
                editing = null
                scope.launch { withContext(Dispatchers.IO) { repo.delete(current.id) }; onDataChanged() }
            },
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    datedPlans: Set<LocalDate>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹", fontSize = 22.sp, color = Color.White) }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)).replaceFirstChar { it.titlecase(ES) },
            style = body(), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text("›", fontSize = 22.sp, color = Color.White) }
    }
    Row {
        DAY_LETTERS.forEach {
            Text(it, style = body().copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1 // lunes = 0
    val cells = offset + month.lengthOfMonth()
    val weeks = (cells + 6) / 7
    for (week in 0 until weeks) {
        Row {
            for (col in 0 until 7) {
                val dayNum = week * 7 + col - offset + 1
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayNum)
                        val isSel = date == selected
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Accent else Color.Transparent)
                                .then(if (date == today && !isSel) Modifier.border(1.dp, Accent, CircleShape) else Modifier)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$dayNum", style = body().copy(fontSize = 14.sp),
                                color = if (isSel) Color(0xFF1B1210) else Color.White)
                            if (date in datedPlans) Box(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp)
                                    .clip(CircleShape).background(if (isSel) Color(0xFF1B1210) else Accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(a: Activity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a.time?.toString() ?: "—", style = body(), color = Color.White.copy(alpha = 0.75f), modifier = Modifier.width(54.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(a.category.color)))
        Spacer(Modifier.width(10.dp))
        Text(a.title, style = body(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            when {
                a.date != null -> "este día"
                a.days.isEmpty() -> "diario"
                else -> a.days.sorted().joinToString(" ") { DAY_LETTERS[it.value - 1] }
            },
            style = body().copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.45f),
        )
    }
}

private enum class Repeat(val label: String) { ONCE("Solo este día"), WEEKLY("Semanal"), DAILY("Diario") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDialog(
    initial: Activity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Activity) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    var time by remember { mutableStateOf(initial.time) }
    var repeat by remember {
        mutableStateOf(
            when {
                initial.date != null -> Repeat.ONCE
                initial.days.isEmpty() -> Repeat.DAILY
                else -> Repeat.WEEKLY
            },
        )
    }
    val baseDate = initial.date ?: LocalDate.now()
    var days by remember { mutableStateOf(initial.days.ifEmpty { setOf(baseDate.dayOfWeek) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1D1F26),
        title = { Text(if (isNew) "Nueva actividad" else "Editar actividad", style = body().copy(fontSize = 20.sp), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Qué vas a hacer") }, textStyle = body().copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(c.color))) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val t = time ?: LocalTime.of(8, 0)
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, t.hour, t.minute, true).show()
                    }) { Text("Hora: ${time?.toString() ?: "sin hora"}", style = body(), color = Accent) }
                    if (time != null) TextButton(onClick = { time = null }) {
                        Text("Quitar", style = body(), color = Color.White.copy(alpha = 0.5f))
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Repeat.entries.forEachIndexed { i, r ->
                        SegmentedButton(selected = repeat == r, onClick = { repeat = r },
                            shape = SegmentedButtonDefaults.itemShape(i, Repeat.entries.size)) {
                            Text(r.label, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                when (repeat) {
                    Repeat.ONCE -> Hint(baseDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", ES)))
                    Repeat.DAILY -> Hint("Se mostrará todos los días.")
                    Repeat.WEEKLY -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEachIndexed { i, d ->
                            val on = d in days
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (on) Accent else Color.White.copy(alpha = 0.08f))
                                    .clickable { days = if (on) days - d else days + d },
                                contentAlignment = Alignment.Center,
                            ) { Text(DAY_LETTERS[i], fontSize = 13.sp, color = if (on) Color(0xFF1B1210) else Color.White) }
                        }
                    }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar actividad", style = body(), color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !(repeat == Repeat.WEEKLY && days.isEmpty()),
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            category = category,
                            time = time,
                            date = if (repeat == Repeat.ONCE) baseDate else null,
                            days = if (repeat == Repeat.WEEKLY) days else emptySet(),
                        ),
                    )
                },
            ) { Text("Guardar", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(alpha = 0.7f)) } },
    )
}

@Composable
internal fun subtleButton() =
    ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)

private const val ROUTINE_PROMPT = """Ayúdame a armar mi rutina para la app Aura (agenda en mi fondo de pantalla).

Primero hazme preguntas breves (una sola tanda) sobre: horarios de comidas, entrenamiento, clases y estudio, y planes o eventos con fecha concreta. Cuando te responda, devuelve ÚNICAMENTE JSON válido, sin texto adicional, con este formato:

{
  "version": 1,
  "activities": [
    { "title": "Desayuno", "category": "comida", "time": "07:30", "days": ["lun","mar","mie","jue","vie"] },
    { "title": "Gimnasio: pierna", "category": "entrenamiento", "time": "18:00", "days": ["lun","jue"] },
    { "title": "Examen de Cálculo", "category": "estudio", "time": "10:00", "date": "2026-11-25" }
  ]
}

Reglas:
- category: comida | entrenamiento | estudio | plan | otro
- time: formato 24 h "HH:mm"
- Rutinas semanales: "days" con lun, mar, mie, jue, vie, sab, dom
- Eventos únicos: "date" en formato AAAA-MM-DD
- Sin "days" ni "date" = todos los días
- Títulos cortos (máx. 30 caracteres)"""

package pe.aura.wallpaper.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.Settings
import pe.aura.wallpaper.sleep.SleepAlarms
import pe.aura.wallpaper.sleep.SleepEntry
import pe.aura.wallpaper.sleep.SleepEvent
import pe.aura.wallpaper.sleep.SleepLogic
import pe.aura.wallpaper.sleep.SleepNotifier
import pe.aura.wallpaper.sleep.SleepRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val GoalColor = Color(0xFF7FD1AE)
private val ShortColor = Color(0xFFF2A06B)

@Composable
fun SleepScreen(settings: Settings, version: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SleepRepository(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(settings.sleepEnabled) }
    var pauseMusic by remember { mutableStateOf(settings.pauseMusic) }
    var showLastNight by remember { mutableStateOf(settings.showLastNight) }
    var editing by remember { mutableStateOf<SleepEntry?>(null) }

    val today = LocalDate.now()
    val entries = remember(version, refresh) { repo.all() }
    val week = remember(version, refresh) { repo.week(today) }
    // Se recalcula al volver a la app (version cambia con cada sincronización).
    val next = remember(version, refresh, enabled) {
        val agenda = ActivityRepository(context).agenda()
        val now = LocalDateTime.now()
        SleepEvent.values().associateWith { SleepLogic.next(agenda.activities, now, it, agenda.customDays) }
    }
    val canNotify = remember(refresh, enabled) { SleepNotifier.canNotify(context) }
    val batteryRestricted = remember(refresh, enabled) {
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    fun changed() {
        settings.bumpDataVersion()
        refresh++
    }

    fun enable() {
        enabled = true
        settings.sleepEnabled = true
        scope.launch(Dispatchers.IO) { SleepAlarms.reschedule(context) }
        refresh++
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context, "Sin permiso de notificaciones no podré avisarte.")
        enable()
    }

    Section("Recordatorios") {
        ToggleRow("Desconectar, dormir y despertar", enabled) { on ->
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 && !SleepNotifier.canNotify(context)) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enable()
                }
            } else {
                enabled = false
                settings.sleepEnabled = false
                scope.launch(Dispatchers.IO) { SleepAlarms.reschedule(context) }
            }
        }
        if (enabled) {
            val fmt = DateTimeFormatter.ofPattern("EEE HH:mm", ES)
            val lines = listOf(
                SleepEvent.DESCONECTAR to "Desconectar",
                SleepEvent.DORMIR to "Dormir",
                SleepEvent.DESPERTAR to "Despertar",
            ).map { (e, label) -> "$label: " + (next[e]?.format(fmt) ?: "no está en tu agenda") }
            Hint("Próximos — " + lines.joinToString(" · "))
            ToggleRow("Pausar música al desconectar", pauseMusic) { pauseMusic = it; settings.pauseMusic = it }
            if (!canNotify) {
                Warning("Las notificaciones están desactivadas para Aura.", "Activar") {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            if (batteryRestricted) {
                Warning("El ahorro de batería puede retrasar los recordatorios. En Batería elige «Sin restricciones».", "Ajustes") {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
        } else {
            Hint("Usa las horas de tu agenda: actividades que empiecen con «Desconectar», «Dormir» y «Despertar».")
        }
    }

    Section("Anoche y hoy") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.recordSleep(LocalDateTime.now()) }
                        toast(context, "Buenas noches. Registré tu hora de dormir.")
                        changed()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Text("Me voy a dormir", style = body()) }
            Button(
                onClick = {
                    scope.launch {
                        val e = withContext(Dispatchers.IO) { repo.recordWake(LocalDateTime.now()) }
                        toast(context, e.durationMin?.let { "Dormiste ${SleepLogic.format(it)}." } ?: "Registré tu hora de despertar.")
                        changed()
                    }
                },
                colors = subtleButton(),
            ) { Text("Desperté", style = body()) }
        }
    }

    Section("Últimos 7 días · meta ${SleepLogic.format(SleepLogic.GOAL_MIN)}") {
        WeekChart(week.nights)
        Text(
            if (week.recorded == 0) "Aún no hay noches registradas esta semana."
            else "Promedio ${SleepLogic.format(week.averageMin!!)} · meta cumplida ${week.met} de ${week.recorded} noches",
            style = body(), color = Color.White,
        )
        ToggleRow("Mostrar «Anoche» en el fondo", showLastNight) {
            showLastNight = it
            settings.showLastNight = it
        }
    }

    Section("Noches registradas") {
        if (entries.isEmpty()) Hint("Toca «Me voy a dormir» al acostarte y «Desperté» al levantarte.")
        entries.sortedByDescending { it.night }.take(14).forEach { e -> NightRow(e) { editing = e } }
        TextButton(onClick = { editing = SleepEntry(today.minusDays(1)) }) {
            Text("+ Agregar una noche", style = body(), color = Accent)
        }
    }

    editing?.let { e ->
        NightDialog(
            initial = e,
            isNew = entries.none { it.night == e.night },
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (updated.night != e.night) repo.delete(e.night)
                        repo.upsert(updated)
                    }
                    changed()
                }
            },
            onDelete = {
                editing = null
                scope.launch { withContext(Dispatchers.IO) { repo.delete(e.night) }; changed() }
            },
        )
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = body().copy(fontSize = 13.sp), color = Color(0xFFFFB4A8), modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action, color = Accent) }
    }
}

/** Barras de las 7 noches con una línea punteada en la meta. Sin librerías. */
@Composable
private fun WeekChart(nights: List<Pair<LocalDate, SleepEntry?>>) {
    val goal = SleepLogic.GOAL_MIN
    val maxMin = maxOf(goal + 90, nights.maxOfOrNull { it.second?.durationMin ?: 0 } ?: 0)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val slot = size.width / nights.size
        val barW = slot * 0.5f
        fun y(min: Int) = size.height * (1f - min.toFloat() / maxMin)
        nights.forEachIndexed { i, (_, e) ->
            val d = e?.durationMin ?: return@forEachIndexed
            val top = y(d)
            drawRoundRect(
                color = if (d >= goal) GoalColor else ShortColor,
                topLeft = Offset(i * slot + (slot - barW) / 2, top),
                size = Size(barW, size.height - top),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        val gy = y(goal)
        drawLine(
            Color.White.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        nights.forEach { (date, e) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    e?.durationMin?.let { "%d:%02d".format(it / 60, it % 60) } ?: "–",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE", ES)).take(2).replaceFirstChar { it.titlecase(ES) },
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NightRow(e: SleepEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.night.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)).replaceFirstChar { it.titlecase(ES) },
            style = body(), color = Color.White, modifier = Modifier.width(110.dp),
        )
        Text(
            "${e.sleep ?: "--:--"} → ${e.wake ?: "--:--"}",
            style = body(), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f),
        )
        val d = e.durationMin
        Text(
            d?.let { SleepLogic.format(it) } ?: "incompleta",
            style = body(), color = when {
                d == null -> Color.White.copy(alpha = 0.4f)
                e.metGoal -> GoalColor
                else -> ShortColor
            },
        )
    }
}

@Composable
private fun NightDialog(initial: SleepEntry, isNew: Boolean, onDismiss: () -> Unit, onSave: (SleepEntry) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var night by remember { mutableStateOf(initial.night) }
    var sleep by remember { mutableStateOf(initial.sleep) }
    var wake by remember { mutableStateOf(initial.wake) }

    fun pick(current: LocalTime?, default: LocalTime, set: (LocalTime) -> Unit) {
        val t = current ?: default
        TimePickerDialog(context, { _, h, m -> set(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1D1F26),
        title = { Text(if (isNew) "Agregar noche" else "Editar noche", style = body().copy(fontSize = 20.sp), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { night = night.minusDays(1) }) { Text("‹", fontSize = 20.sp, color = Color.White) }
                    Text(
                        "Noche del " + night.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)),
                        style = body(), color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { night = night.plusDays(1) }, enabled = night.isBefore(LocalDate.now())) {
                        Text("›", fontSize = 20.sp, color = Color.White)
                    }
                }
                TextButton(onClick = { pick(sleep, LocalTime.of(23, 15)) { sleep = it } }) {
                    Text("Me dormí: ${sleep ?: "elegir"}", style = body(), color = Accent)
                }
                TextButton(onClick = { pick(wake, LocalTime.of(7, 0)) { wake = it } }) {
                    Text("Desperté: ${wake ?: "elegir"}", style = body(), color = Accent)
                }
                val s = sleep
                val w = wake
                if (s != null && w != null) Hint("Duración: ${SleepLogic.format(SleepLogic.durationMin(s, w))}")
                if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar noche", style = body(), color = Color(0xFFFF8A80)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(night = night, sleep = sleep, wake = wake)) }) { Text("Guardar", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(alpha = 0.7f)) } },
    )
}

/**
 * Si anoche tocaba dormir (según la agenda) y no se registró la hora, al abrir la app
 * pregunta "¿A qué hora te dormiste anoche?". Solo una vez por noche.
 */
@Composable
fun MissedSleepPrompt(settings: Settings, version: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askNight by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(version) {
        askNight = withContext(Dispatchers.IO) {
            if (!settings.sleepEnabled) return@withContext null
            val now = LocalDateTime.now()
            // "Anoche" = la noche que empezó ayer; solo se pregunta a partir de las 04:00 de hoy.
            val night = now.toLocalDate().minusDays(1)
            if (settings.sleepAskedNight == night.toString()) return@withContext null
            if (SleepRepository(context).forNight(night)?.sleep != null) return@withContext null
            // ¿Había "Dormir" esa noche? (de las 12:00 de ese día a las 12:00 del siguiente)
            val agenda = ActivityRepository(context).agenda()
            val hadDormir = SleepLogic.next(agenda.activities, night.atTime(12, 0), SleepEvent.DORMIR, agenda.customDays)
                ?.isBefore(night.plusDays(1).atTime(12, 0)) == true
            night.takeIf { hadDormir && now.isAfter(night.plusDays(1).atTime(4, 0)) }
        }
    }

    val night = askNight ?: return
    fun done() {
        settings.sleepAskedNight = night.toString()
        askNight = null
    }
    AlertDialog(
        onDismissRequest = { done() },
        containerColor = Color(0xFF1D1F26),
        title = { Text("¿A qué hora te dormiste anoche?", style = body().copy(fontSize = 20.sp), color = Color.White) },
        text = { Hint("No registraste «Me voy a dormir». Elige la hora aproximada para completar tu semana.") },
        confirmButton = {
            TextButton(onClick = {
                TimePickerDialog(context, { _, h, m ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val repo = SleepRepository(context)
                            repo.upsert((repo.forNight(night) ?: SleepEntry(night)).copy(sleep = LocalTime.of(h, m)))
                        }
                        settings.bumpDataVersion()
                    }
                    done()
                }, 23, 15, true).show()
            }) { Text("Elegir hora", color = Accent) }
        },
        dismissButton = { TextButton(onClick = { done() }) { Text("No registrar", color = Color.White.copy(alpha = 0.7f)) } },
    )
}

# Aura

App Android que muestra cada día un **fondo de pantalla** y una **frase motivadora**, con la hora en una tipografía fina y elegante (Jost ExtraLight, estilo "S I L E N C E").

## Descargar

**[⬇ Descargar Aura (última versión)](https://github.com/BrayanRx/personal-app/releases/latest/download/Aura.apk)** · [Todas las versiones](https://github.com/BrayanRx/personal-app/releases)

Android 8.0 o superior. Al abrir el APK, permite "Instalar apps desconocidas" para tu navegador.
Las versiones están firmadas siempre con la misma clave, así que cada nueva se instala encima sin perder tus fotos, frases ni agenda.

## Qué hace
- **Fondo animado** (live wallpaper): hora en vivo, fecha, frase del día. Funciona en inicio y bloqueo (según el fabricante).
- **Modo estático solo para bloqueo**: genera la imagen (fondo + frase) y la fija con `WallpaperManager.FLAG_LOCK`; opcionalmente se regenera cada medianoche (WorkManager).
- **Fondos** importados desde la galería (copiados dentro de Aura), rotan uno por día; se pueden reordenar, eliminar o fijar para hoy.
- **Sueño**: recordatorios para desconectar, dormir y despertar según tu agenda, registro de cada noche y gráfico semanal contra la meta de 7 h 45. Pausa la música al desconectar.
- **Importar frases** desde archivo JSON o pegando desde el portapapeles; botón para copiar un prompt listo para Claude.
- Estilos **Minimal** (centrado) y **Cristal** (tarjetas con desenfoque real).
- **Agenda**: comidas, entrenamiento, estudios y planes. Rutinas semanales, diarias o de una fecha concreta, con calendario en la app; las de hoy se dibujan en el fondo (la siguiente resaltada, las pasadas atenuadas).

## Formato JSON de agenda
```json
{
  "activities": [
    { "title": "Desayuno", "category": "comida", "time": "07:30", "days": ["lun","mar","mie","jue","vie"] },
    { "title": "Examen de Cálculo", "category": "estudio", "time": "10:00", "date": "2026-11-25" }
  ]
}
```
`category`: comida | entrenamiento | estudio | plan | otro. Sin `days` ni `date` = todos los días.
### Días con agenda propia
Para días en que la rutina semanal no aplica (torneos, viajes, feriados):

```json
{
  "version": 1,
  "customDays": ["2026-11-22"],
  "activities": [
    { "title": "Torneo de Tetris", "category": "plan", "time": "12:00", "date": "2026-11-22" },
    { "title": "Gimnasio", "category": "entrenamiento", "time": "18:00", "days": ["lun","mie"], "skip": ["2026-11-25"] }
  ]
}
```

- **`customDays`** (raíz, opcional): en esas fechas solo se muestran las actividades con `date` igual a ese día; las recurrentes y diarias se ocultan. Sueño tampoco programa Dormir ni Despertar ese día, salvo que tenga los suyos con fecha.
- **`skip`** (por actividad, opcional): oculta esa actividad recurrente solo en las fechas indicadas.
- En la app: **mantén presionado un día** del calendario → "Solo agenda propia este día". Los días marcados desde la nota se gestionan en la nota; los de la app, en la app.
- Los planes con fecha de hace más de 30 días se ocultan del calendario (no se borran).

Cada actividad recibe un ID estable (hash de título, hora, días y fecha), así que importar o sincronizar dos veces lo mismo no duplica nada.

## Sincronización con Obsidian

Aura puede leer **sola** la agenda y las frases desde dos notas de tu bóveda (por ejemplo, sincronizada con Obsidian Sync). Editas la nota en la PC, llega al celular y Aura la aplica, sin copiar ni pegar.

1. En la app: **Fondo → Sincronización con Obsidian → Elegir carpeta** y selecciona la carpeta que contiene las dos notas (por ejemplo `Documents/obsidian/20 Áreas/Desarrollo personal/Aura`). Se elige una sola vez.
2. Aura busca en esa carpeta (sin subcarpetas):
   - la nota de **agenda**: un `.md` cuyo nombre contiene `Rutina diaria`;
   - la nota de **frases**: un `.md` cuyo nombre contiene `Frases para la app`.
3. De cada nota usa el primer bloque ` ```json `; si no hay, el primer bloque de código; si tampoco, el texto completo.

**Cuándo se revisa:** al abrir Aura, cuando el fondo animado se hace visible (como máximo cada 15 min) y antes de generar el bloqueo estático. Solo se vuelve a leer una nota si cambió (fecha de modificación o, si no hay, tamaño). "Sincronizar ahora" fuerza la lectura.

**Qué reemplaza:**
- Agenda: las actividades que vienen de la nota se **reemplazan** en cada sincronización; las creadas en la app se conservan. En la app, las de la nota aparecen marcadas con «nota».
- Frases: el banco completo se reemplaza por el de la nota.
- Si algo falla (permiso revocado, nota borrada, JSON inválido) **no se borra nada**: el error se muestra en la sección Sincronización y se reintenta en la próxima revisión.

Ejemplo de nota de agenda:

````markdown
# Rutina diaria — JSON para la app Aura

Cualquier texto antes o después del bloque se ignora.

```json
{ "version": 1, "activities": [
  { "title": "Desayuno", "category": "comida", "time": "07:30", "days": ["lun","mar","mie","jue","vie"] }
] }
```
````

Se lee la nota `.md` y no un `.json` aparte porque Obsidian Sync siempre sincroniza notas, mientras que "otros tipos de archivo" requieren activarse.

## Sueño

Pestaña **Sueño**. Activa "Desconectar, dormir y despertar" (en Android 13+ pide permiso de notificaciones).

- **Detección por título** en la agenda de cada día (sin distinguir mayúsculas ni tildes): actividades que empiezan con `Desconectar`, `Dormir` y `Despertar`. Si un día no tiene alguna, no se programa nada para ese evento.
- **Recordatorios** (canal "Sueño"):
  - a la hora de *Dormir*: "Hora de dormir. Meta: 7 h 45" con el botón **Me voy a dormir**;
  - a la hora de *Despertar*: "Buenos días" con el botón **Desperté**.
  - Los mismos botones están en la app. Si no registraste la hora de dormir, al abrir la app te pregunta "¿A qué hora te dormiste anoche?".
- **Registro** en `sleep.json`: una entrada por noche (`night`, `sleep`, `wake`, `goalMin`). La noche lleva la fecha del día en que te acostaste; la madrugada (antes de las 12:00) cuenta como la noche anterior, y la duración cruza la medianoche. Cada noche se puede editar o borrar.
- **Vista**: barras de los últimos 7 días con la meta (7 h 45), promedio y noches que cumplieron la meta. Opcional en el fondo: "ANOCHE 6 H 50" en la tarjeta de agenda.

### Pausar la música al desconectar
A la hora de *Desconectar*, Aura envía la tecla multimedia **Pausa** (`AudioManager.dispatchMediaKeyEvent`): funciona con Spotify y cualquier app de música, sin APIs de terceros. La notificación "Hora de desconectar" trae **5 min más**, que reanuda la música y la vuelve a pausar 5 minutos después (una sola vez). Se puede desactivar con "Pausar música al desconectar".

### Alarmas
Se usan alarmas inexactas (`setAndAllowWhileIdle`, ±unos minutos), así que no hace falta el permiso de alarmas exactas. Se reprograman al abrir la app, tras cada sincronización de la bóveda, tras cada aviso y al reiniciar el teléfono. Si el ahorro de batería las retrasa, la pestaña Sueño muestra un aviso con acceso directo a los ajustes de la app (Batería → Sin restricciones).

## Fondos

- Cuadrícula de miniaturas en el **orden de rotación**, con la de **hoy** marcada.
- Toca una miniatura para **Usar hoy** (solo por hoy), **Mover antes / después** o **Eliminar** (con confirmación). **Agregar** suma imágenes al final; **Borrar todo** pide confirmación.
- El orden se guarda en `images/order.json`.
- **Las imágenes se copian (reescaladas) dentro de Aura**: puedes borrar las originales de tu galería y el fondo no cambia.

## Formato JSON de frases
```json
{
  "version": 1,
  "quotes": [
    { "text": "La disciplina es el puente entre las metas y los logros.", "author": "Jim Rohn" },
    { "text": "Hoy se construye.", "date": "2026-12-01" }
  ]
}
```
También acepta `[ "frase", ... ]`, alias en español (`frases`, `frase`, `autor`, `fecha`) y respuestas envueltas en ```` ```json ````.
Las frases con `date` se muestran exactamente ese día; el resto rota. El import manual fusiona sin duplicar; la sincronización reemplaza el banco.

## Compilar
- Android Studio: abrir la carpeta y ejecutar `app`.
- CLI: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (se instala como "Aura Dev", aparte de la versión publicada).
- CI: cada push ejecuta tests y compila el APK de prueba (artefacto `aura-debug-apk`).

## Publicar una versión
1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Fusiona a `main`. CI compila el APK firmado y crea el Release `vX.Y.Z` automáticamente.

La firma usa los secrets `AURA_KEYSTORE_BASE64`, `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS` y `AURA_KEY_PASSWORD`. Si faltan, el Release se omite con un aviso.

## Limitaciones conocidas (Android, no de la app)
- Ninguna app puede ocultar el reloj del sistema en la pantalla de bloqueo. Si se ven dos relojes, desactiva "Mostrar hora".
- Algunas capas (MIUI/HyperOS, ciertas One UI) no muestran fondos animados en el bloqueo: usar el modo estático.

## Próximos pasos
- Marcar actividades como hechas y racha semanal.
- Clima (Open-Meteo, sin API key).

## Licencia
Código bajo licencia [MIT](LICENSE). La fuente Jost se distribuye bajo SIL Open Font License 1.1 (`licenses/Jost-OFL.txt`).

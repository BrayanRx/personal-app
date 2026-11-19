# Aura

App Android que muestra cada día un **fondo de pantalla** y una **frase motivadora**, con la hora en una tipografía fina y elegante (Jost ExtraLight, estilo "S I L E N C E").

## MVP actual
- **Fondo animado** (live wallpaper): hora en vivo, fecha, frase del día. Funciona en inicio y bloqueo (según el fabricante).
- **Modo estático solo para bloqueo**: genera la imagen (fondo + frase) y la fija con `WallpaperManager.FLAG_LOCK`; opcionalmente se regenera cada medianoche (WorkManager).
- **Importar imágenes** desde la galería (se copian reescaladas al almacenamiento interno). Rotan una por día.
- **Importar frases** desde archivo JSON o pegando desde el portapapeles; botón para copiar un prompt listo para Claude.
- Estilos **Minimal** (centrado) y **Cristal** (tarjetas esmeriladas).

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
Las frases con `date` se muestran exactamente ese día; el resto rota.

## Compilar
- Android Studio: abrir la carpeta y ejecutar `app`.
- CLI: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- CI: cada push compila el APK en GitHub Actions (artefacto `aura-debug-apk`).

## Limitaciones conocidas (Android, no de la app)
- Ninguna app puede ocultar el reloj del sistema en la pantalla de bloqueo. Si se ven dos relojes, desactiva "Mostrar hora".
- Algunas capas (MIUI/HyperOS, ciertas One UI) no muestran fondos animados en el bloqueo: usar el modo estático.

## Próximos pasos
- Clima (Open-Meteo, sin API key).
- Rutinas/actividades por día de la semana configurables.

Fuente Jost bajo SIL Open Font License (`licenses/Jost-OFL.txt`).

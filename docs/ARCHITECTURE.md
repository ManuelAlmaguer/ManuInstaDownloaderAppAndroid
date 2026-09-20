# Arquitectura de Manu ReelDrop

## Vista general

```
┌──────────────────────────────── App Android ────────────────────────────────┐
│                                                                             │
│  ui/            Compose: Home, Descargas, Biblioteca, Ajustes, Acerca de     │
│                 (temas: 12 paletas + claro/oscuro/automático + Material You) │
│                                                                             │
│  service/       DownloadEngine  →  cola, concurrencia, reintentos, SSE       │
│                 DownloadService →  servicio en primer plano + notificaciones │
│                 ResumeWorker    →  reanudar al volver la red / reiniciar     │
│                                                                             │
│  data/          ReelDropApi (OkHttp + SSE), SettingsRepository (DataStore),  │
│                 JobStore (JSON atómico), LibraryRepository, ServerRepository │
│                                                                             │
│  domain/        DownloadJob, JobStatus, Quality, LibraryItem, ServerMode…    │
│  util/          UrlUtils, NetworkInfo, LocalFolder (SAF), Permissions        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │ HTTP/HTTPS + token
                                    ▼
┌──────────────────────────── Servidor PHP (Termux) ──────────────────────────┐
│  api.php       router: health, job-create, jobs, events(SSE), cancel, …      │
│  lib/worker.php  proceso independiente que ejecuta yt-dlp y publica estado   │
│  lib/store.php   jobs en JSON atómico + detección de procesos muertos        │
│  lib/media.php   biblioteca, miniaturas (ffmpeg), streaming con rangos       │
│  downloads/      videos ·  data/ estado, metadatos y miniaturas              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Flujo de una descarga

1. La app valida el enlace (`UrlUtils`) y crea un `DownloadJob` local (uuid) que guarda en
   `download_queue.json` (escritura atómica con copia de seguridad).
2. `DownloadEngine` entra en la puerta de concurrencia (`DownloadGate`, límite configurable).
3. POST `api.php?action=job-create` → el servidor crea el trabajo y lanza `lib/worker.php`
   con `nohup` (desacoplado de la petición HTTP).
4. El worker ejecuta yt-dlp, parsea la plantilla de progreso y reescribe el JSON del trabajo
   cada ~350 ms.
5. La app sigue GET `api.php?action=events&id=…` (SSE). Si el stream falla, cambia
   automáticamente a GET `action=job` cada 900 ms.
6. Al terminar: el worker guarda metadatos + miniatura; la app actualiza el historial,
   muestra la notificación de resultado y (si está configurado) copia el video al teléfono o a
   la carpeta SAF elegida.

La app no ejecuta PHP ni crea un listener en el puerto 8080. `server/start.sh` es el proceso
que el usuario inicia en Termux; Android solo consume su API por la URL configurada.

## Robustez

| Fallo | Respuesta automática |
|---|---|
| Se corta el stream SSE | *Polling* cada 900 ms hasta el estado final. |
| Se cae la red | `NetworkMonitor` programa `ResumeWorker` cuando vuelve la conexión. |
| La app se cierra | La cola está en disco; al abrir se reanudan los trabajos pendientes. |
| Android mata el servicio | La notificación de progreso es un servicio en primer plano `dataSync`; al reiniciar, la cola se reanuda. |
| El servidor es antiguo | Se detecta el `404` de `job-create` y se usa `download-stream` (modo compatible). |
| El worker muere | `store.php` detecta que el PID no existe y marca el trabajo como `failed` con un motivo legible. |
| Error transitorio del servidor | Reintentos con backoff exponencial + jitter (hasta el límite configurado). |

## Persistencia

| Dato | Dónde | Formato |
|---|---|---|
| Cola e historial | `files/download_queue.json` (+ `.bak`) | JSON con escritura atómica |
| Ajustes | DataStore `reeldrop_settings` | Preferencias tipadas |
| Trabajos del servidor | `server/data/jobs/*.json` | Un archivo por trabajo |
| Metadatos y miniaturas | `server/data/meta`, `server/data/thumbs` | JSON + JPEG |
| Videos | `server/downloads` o la carpeta elegida por el usuario (SAF) | Archivo original |
| Temporales | `server/downloads/*.part`, `*.ytdl`, `*.tmp`, fragmentos e info JSON | Listados por `temporary` |

## Calidad

* Tests unitarios: formateo de bytes/velocidad/ETA y validación de enlaces de Instagram,
  YouTube y Facebook.
* Lint de Android configurado (no bloqueante) y de PHP (`php -l`) en los scripts.
* CI en GitHub Actions: compila `assembleDebug` + `assembleRelease`, ejecuta los tests y
  publica los APK como artefactos.

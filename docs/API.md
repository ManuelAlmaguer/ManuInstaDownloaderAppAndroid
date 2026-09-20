# API del servidor Manu ReelDrop

Base: `http://<host>:<puerto>/api.php`

Todas las respuestas son JSON (`{"ok":true,…}` / `{"ok":false,"error":"…"}`), excepto
`action=events`, que devuelve *server-sent events*.

## Autenticación

Si `config.php → api_token` no está vacío, hay que enviar el token en **todas** las acciones
excepto `health`:

```
X-Api-Token: <token>          (la app lo hace automáticamente)
?token=<token>                (cómodo desde el navegador)
```

Con token incorrecto: `401 {"ok":false,"error":"Token de API incorrecto o ausente."}`

---

## Trabajos

### GET `?action=health`

```json
{
  "ok": true,
  "app": "Manu ReelDrop Server",
  "version": "2.1.0",
  "ytdlp": "/data/data/com.termux/files/usr/bin/yt-dlp",
  "ytdlp_version": "2025.09.05",
  "ffmpeg": true,
  "tokenRequired": true,
  "free_space": 15569829888,
  "total_space": 16729894912,
  "active_jobs": 1,
  "library_count": 12,
  "downloads_dir": "/data/data/com.termux/files/home/.../server/downloads",
  "max_concurrent_jobs": 3,
  "time": 1789792527,
  "message": null
}
```

`message` avisa cuando falta yt-dlp. La app usa este endpoint para el indicador de estado, el
espacio libre y el diagnóstico.

### POST `?action=job-create`

Cuerpo: `{"url": "https://www.youtube.com/watch?v=…", "quality": "best|1080|720|480|audio"}`

Se aceptan hosts de Instagram, YouTube (`youtube.com`, `youtu.be`) y Facebook
(`facebook.com`, `fb.watch`). El servidor vuelve a validar la lista configurada en
`allowed_hosts` para evitar destinos arbitrarios.

Respuesta:

```json
{
  "ok": true,
  "job": {
    "id": "20260919-120501-a1b2c3d4",
    "url": "https://www.instagram.com/reel/…",
    "quality": "best",
    "status": "queued",
    "progress": 0,
    "speed": null,
    "speed_bps": 0,
    "eta": null,
    "eta_seconds": null,
    "downloaded_bytes": 0,
    "total_bytes": 0,
    "title": null,
    "filename": null,
    "error": null,
    "message": "En cola",
    "created_at": 1789792527
  }
}
```

Errores: `400` si la URL no pertenece a una plataforma permitida, `503` si falta yt-dlp, `500` si no se puede
arrancar el worker.

### GET `?action=job&id=<id>`

Devuelve el trabajo completo. Si el proceso murió, el servidor lo marca como `failed` con un
mensaje explicativo (por ejemplo, «se interrumpió ¿se cerró Termux?»).

### GET `?action=jobs&limit=50&status=downloading,failed`

Lista los últimos trabajos (filtro opcional por estado separado por comas).

### GET `?action=events&id=<id>` — progreso en vivo (SSE)

```
data: {"id":"…","status":"downloading","progress":42.5,"speed":"1.24MiB/s","speed_bps":1300000,
       "eta":"00:12","eta_seconds":12,"downloaded_bytes":4567890,"total_bytes":10485760,
       "message":"Descargando…","type":"progress"}

: ping 1789792537

data: {"id":"…","status":"completed","progress":100,"filename":"reel [ABC].mp4","type":"done"}
```

* El stream termina cuando el trabajo llega a `completed`, `failed` o `canceled`.
* `type` vale `progress`, `done` o `error` para que clientes simples no tengan que interpretar
  `status`.
* Los comentarios (`: ping`) mantienen viva la conexión a través de proxies.

### POST `?action=job-cancel` con `{"id": "…"}`

Mata el proceso de yt-dlp (y sus hijos) y marca el trabajo como `canceled`.

### POST `?action=job-retry` con `{"id": "…"}`

Crea un trabajo nuevo con la misma URL/calidad, hereda `attempts + 1` y lo arranca.

### POST `?action=job-delete` con `{"id": "…"}`

Borra el registro del trabajo (no toca el archivo descargado).

### POST `?action=job-cancel-all`

Cancela todos los trabajos activos. Devuelve `{"ok":true,"canceled":2}`.

---

## Biblioteca

### GET `?action=library&q=<texto>`

```json
{
  "ok": true,
  "items": [
    {
      "file": "Mi reel [Cx1y2z3].mp4",
      "size": 10485760,
      "mtime": 1789792527,
      "duration": 27.4,
      "width": 1080,
      "height": 1920,
      "title": "Mi reel",
      "author": "una_cuenta",
      "thumb": "api.php?action=thumb&file=Mi%20reel%20%5BCx1y2z3%5D.mp4",
      "url": "api.php?action=file&file=Mi%20reel%20%5BCx1y2z3%5D.mp4"
    }
  ]
}
```

Los metadatos salen de `data/meta/<archivo>.json` (los escribe el worker con la información de
yt-dlp y, si hace falta, de ffprobe).

### POST `?action=library-delete` con `{"file": "…"}`

Borra el video, su miniatura y su sidecar de metadatos.

### GET `?action=file&file=<nombre>&download=1`

Streaming del archivo con soporte de **HTTP Range** (`206 Partial Content`), lo que permite
reproducir con búsqueda y reanudar descargas. Con `download=1` añade `Content-Disposition`.

### GET `?action=thumb&file=<nombre>`

Devuelve un JPEG: usa el `data/thumbs/<archivo>.jpg` cacheado o lo genera con ffmpeg
(`-ss 1 s`, escalado a 480 px de ancho).

### POST `?action=cleanup`

Borra `.part`, `.ytdl`, `.tmp`, `.info.json` y, si `retention_days > 0`, los archivos más
antiguos. Devuelve `{"ok":true,"deleted":3,"bytes":15728640}`.

### GET `?action=temporary`

Lista los residuos que yt-dlp puede dejar mientras descarga. Solo incluye ficheros con nombre
seguro y extensiones `.part`, `.ytdl`, `.tmp`, `.part-Frag*` o `.info.json`:

```json
{
  "ok": true,
  "items": [
    {"file": "video.mp4.part", "size": 524288, "mtime": 1789792527, "kind": "Descarga parcial"}
  ]
}
```

### POST `?action=temporary-delete` con `{"file": "…"}`

Elimina un temporal individual después de comprobar que pertenece a la carpeta `downloads` y
que su nombre coincide con uno de los tipos permitidos.

---

## Compatibilidad con la API original

| Acción antigua | Comportamiento actual |
|---|---|
| POST `?action=download-stream` | SSE con `start`, `progress` (`percent`, `speed`, `eta`, `downloaded`, `total`), `log`, `done` y `error`. |
| GET `?action=list` | Igual que `library`. |
| POST `?action=delete` | Igual que `library-delete`. |
| POST `?action=download` | Crea un trabajo y devuelve su `id` (ya no bloquea la petición). |
| GET `?action=file&file=` | Igual que antes, ahora con rangos. |

Esto significa que puedes actualizar `server/` sin romper la web que ya tenías.

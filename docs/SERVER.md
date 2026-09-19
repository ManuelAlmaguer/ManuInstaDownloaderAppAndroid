# Servidor ReelDrop en Termux (guía detallada)

## Instalación rápida

```bash
pkg update -y && pkg install -y git
git clone https://github.com/ManuelAlmaguer/ManuInstaDownloaderAppAndroid.git
cd ManuInstaDownloaderAppAndroid/server
bash setup-termux.sh     # instala php, python, ffmpeg, yt-dlp y crea config.php
bash start.sh            # http://127.0.0.1:8080
```

Comprueba el estado:

```bash
curl "http://127.0.0.1:8080/api.php?action=health"
```

## Instalación manual (si prefieres controlar cada paso)

```bash
pkg install -y php python ffmpeg openssl
pip install -U yt-dlp
cd server
cp config.example.php config.php
# edita config.php y pon tu api_token (openssl rand -hex 16)
mkdir -p downloads data/jobs data/meta data/thumbs logs
php -S 0.0.0.0:8080 -t .
```

## Estructura de carpetas

```
server/
├── api.php            # API (punto de entrada de la app)
├── index.php          # panel web
├── config.php         # tu configuración (no se sube al repositorio)
├── lib/               # worker.php, store.php, ytdlp.php, media.php, util.php, http.php
├── assets/            # CSS/JS del panel
├── downloads/         # videos descargados (servidos a la app)
├── data/jobs/*.json   # estado de cada descarga (lo que la app lee en vivo)
├── data/meta/*.json   # metadatos: título, autor, duración, resolución
├── data/thumbs/*.jpg  # miniaturas de la biblioteca
└── logs/              # trazas de yt-dlp por trabajo
```

## Configuración (config.php)

| Clave | Por defecto | Notas |
|---|---|---|
| `api_token` | `''` | Obligatorio si el servidor es accesible por otros. |
| `max_concurrent_jobs` | `3` | Descargas simultáneas; en móviles conviene 2–3. |
| `downloads_dir` | `server/downloads` | Puede estar en la SD. |
| `retention_days` | `0` | Borra automáticamente lo antiguo al ejecutar `cleanup`. |
| `cookies_file` | `''` | Para contenido privado/restringido. |
| `stale_job_seconds` | `45` | Margen antes de dar por muerto un trabajo. |

## Arranque automático

### Termux:Boot

```bash
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/reeldrop <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cd ~/ManuInstaDownloaderAppAndroid/server && nohup bash start.sh > logs/boot.log 2>&1 &
SH
chmod +x ~/.termux/boot/reeldrop
```

### Mantenerlo vivo

* Ajustes de Android → Termux → Batería → **Sin restricciones**.
* En la app: **Ajustes → Permisos → Descargas en segundo plano** (exención de batería).
* Activa `termux-wake-lock` (lo hace el script de arranque).

## Exponerlo fuera del móvil

### Red local

```bash
bash start.sh                   # escucha en 0.0.0.0:8080
ifconfig | grep inet            # averigua la IP, p. ej. 192.168.1.40
```

En la app: `http://192.168.1.40:8080` + token.

### Internet con Cloudflare Tunnel (sin abrir puertos)

```bash
pkg install -y cloudflared
cloudflared tunnel --url http://127.0.0.1:8080
# https://algo-random.trycloudflare.com
```

### Internet con Tailscale (red privada)

```bash
pkg install -y tailscale
tailscaled &
tailscale up
tailscale ip -4     # usa http://100.x.y.z:8080 en la app
```

### Internet en un VPS

```bash
sudo apt install -y php-cli php-mbstring ffmpeg python3-pip
sudo pip3 install -U yt-dlp
sudo cp -r server /var/www/reeldrop
# Nginx + PHP-FPM, o Apache. Fuerza HTTPS con Certbot.
```

En todos los casos: `api_token` definido, HTTPS cuando salga de tu red y, si usas Apache, el
`.htaccess` incluido ya bloquea el acceso a `*.json`, `*.log` y `*.jks`.

## Diagnóstico

```bash
curl "http://127.0.0.1:8080/api.php?action=health"        # estado general
tail -f logs/*.log                                        # trazas de descargas
ls -la data/jobs/                                         # trabajos y su estado
yt-dlp --version                                          # versión instalada
```

Síntoma típico «la descarga se queda en procesando»: actualiza yt-dlp
(`pip install -U yt-dlp`) y, si Instagram pide sesión, usa `cookies_file`.

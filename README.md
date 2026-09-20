# Manu ReelDrop

**Descargador de vídeos para Android con tu propio servidor (Termux + yt-dlp).**

Manu ReelDrop es una app Android nativa (Kotlin + Jetpack Compose) que descarga vídeos de
Instagram, YouTube y Facebook usando **tu** servidor: puede ser Termux en el mismo móvil,
un PC de tu red local o un servidor publicado en Internet. La app nunca envía tus enlaces a
terceros: solo habla con la dirección que tú configures.

El nombre visible actual es **Manu ReelDrop**. Se mantiene el paquete Android y la ruta
`Movies/ReelDrop` para no romper instalaciones anteriores. El icono anterior se conserva en
`app/src/main/res/drawable/ic_launcher_legacy_*`.

---

## Índice

1. [Qué hay en el repositorio](#1-qué-hay-en-el-repositorio)
2. [Cómo funciona (arquitectura)](#2-cómo-funciona-arquitectura)
3. [Requisitos](#3-requisitos)
4. [Paso 1 · Instalar el servidor en Termux](#4-paso-1--instalar-el-servidor-en-termux)
5. [Paso 2 · Elegir dónde vive el servidor](#5-paso-2--elegir-dónde-vive-el-servidor)
6. [Paso 3 · Instalar la app](#6-paso-3--instalar-la-app)
7. [Paso 4 · Configurar la app](#7-paso-4--configurar-la-app)
8. [Funciones de la app](#8-funciones-de-la-app)
9. [Temas visuales](#9-temas-visuales)
10. [Permisos de Android](#10-permisos-de-android)
11. [Carpeta personalizada de descargas](#11-carpeta-personalizada-de-descargas)
12. [Notificaciones](#12-notificaciones)
13. [Retoques finos del servidor](#13-retoques-finos-del-servidor)
14. [API del servidor](#14-api-del-servidor)
15. [Compilar la app desde el código](#15-compilar-la-app-desde-el-código)
16. [Problemas frecuentes](#16-problemas-frecuentes)
17. [Estructura del proyecto](#17-estructura-del-proyecto)
18. [Aviso legal](#18-aviso-legal)

---

## 1. Qué hay en el repositorio

| Carpeta | Contenido |
|---|---|
| `app/` | Código fuente de la app Android (Kotlin, Jetpack Compose, Material 3). |
| `server/` | Servidor PHP + yt-dlp para Termux (cola de trabajos, progreso, biblioteca, miniaturas). |
| `docs/` | Documentación detallada: API, servidor y arquitectura. |
| `release/` | APK de referencia listos para instalar (`ReelDrop-v1.0.0.apk`). |
| `HANDOFF.md` | Estado del proyecto, decisiones y siguientes pasos. |

El servidor es compatible con el `api.php` original de la web: puedes actualizar la carpeta y
todo lo que ya tenías sigue funcionando.

La lámina [docs/screenshots/pestanas.png](docs/screenshots/pestanas.png) muestra la maqueta
visual actualizada de Descargar, Descargas, Biblioteca y Ajustes. Es una referencia visual
renderizada del proyecto; para capturas de ejecución real hace falta instalar la build en un
dispositivo o emulador Android.

---

## 2. Cómo funciona (arquitectura)

```
┌──────────────────────────┐        HTTP/HTTPS + token         ┌────────────────────────────┐
│  App ReelDrop (Android)  │ ────────────────────────────────► │  Servidor ReelDrop (PHP)   │
│                          │                                   │                            │
│  · pega el enlace        │  POST  action=job-create          │  · crea un trabajo         │
│  · cola + reintentos     │ ◄───────────────────────────────  │  · arranca yt-dlp          │
│  · progreso en vivo      │  GET   action=events (SSE)        │  · escribe estado en JSON  │
│  · notificaciones        │ ◄──── progreso, velocidad, ETA ── │  · guarda el video         │
│  · biblioteca y ajustes  │  GET   action=library / file      │  · sirve archivos (Range)  │
└──────────────────────────┘                                   └────────────────────────────┘
                                                                          │
                                                                          ▼
                                                            downloads/  (reels descargados)
```

* El servidor **descarga** (yt-dlp) y guarda el archivo.
* La app **manda enlaces, muestra progreso y gestiona** la cola, el historial y la biblioteca.
* La app **no levanta PHP ni abre el puerto 8080**: ese proceso se ejecuta en Termux (o en el
  servidor que elijas) y la app solo se conecta a la URL configurada.
* La comunicación usa *server-sent events* para el progreso en tiempo real y, si el stream se
  corta, la app cambia automáticamente a *polling*.
* Cada trabajo se guarda en un JSON del servidor, así que puedes cerrar la app, reiniciar el
  móvil o cambiar de red sin perder nada.

---

## 3. Requisitos

**En el móvil que hace de servidor**

* Termux instalado (recomendado desde [F-Droid](https://f-droid.org/packages/com.termux/)).
* yt-dlp, PHP >= 8.1, Python y ffmpeg (el instalador los pone por ti).
* ~200 MB libres para el servidor y espacio para los videos.

**En el móvil donde usas la app**

* Android 7.0 (API 24) o superior.
* Acceso al servidor: por loopback (`127.0.0.1`), por la red local o por Internet.

---

## 4. Paso 1 · Instalar el servidor en Termux

Abre Termux y ejecuta:

```bash
pkg update -y && pkg install -y git
git clone https://github.com/ManuelAlmaguer/ManuInstaDownloaderAppAndroid.git
cd ManuInstaDownloaderAppAndroid/server
bash setup-termux.sh
```

El instalador:

1. Instala `php`, `python`, `ffmpeg` y `openssl`.
2. Instala/actualiza **yt-dlp**.
3. Crea `downloads/`, `data/` y `logs/`.
4. Crea `config.php` con un **token de API aleatorio** y te lo muestra en pantalla.

Al terminar verás algo así:

```
== Todo listo ==
  Token de API generado: 3f9c1d0a7b6e4c8f9a2d5e7b1c4f8a0d
Arranca el servidor con:  ./start.sh
```

**Guarda ese token**: se pega en la app (Ajustes → Servidor → Token de API).

### Arrancar el servidor

```bash
./start.sh                 # puerto 8080 en todas las interfaces
PORT=9000 ./start.sh       # otro puerto
```

Comprueba que responde (desde el navegador del propio móvil):

```
http://127.0.0.1:8080/api.php?action=health
```

Deberías ver un JSON con `"ok":true`, la versión de yt-dlp, si hay ffmpeg y el espacio libre.

### Arranque automático al encender el móvil (opcional)

1. Instala **Termux:Boot** (F-Droid) y ábrelo una vez.
2. Crea el script de arranque:

```bash
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/reeldrop <<'SH'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cd ~/ManuInstaDownloaderAppAndroid/server && nohup ./start.sh > logs/boot.log 2>&1 &
SH
chmod +x ~/.termux/boot/reeldrop
```

3. Desactiva la optimización de batería de Termux (Ajustes de Android → Aplicaciones →
   Termux → Batería → *Sin restricciones*). También puedes usar el botón de la app:
   **Ajustes → Permisos → Descargas en segundo plano**.

---

## 5. Paso 2 · Elegir dónde vive el servidor

La app tiene un selector de modo en **Ajustes → Servidor**:

### A) Mismo móvil (por defecto, lo más simple)

Termux y la app en el mismo teléfono. Dirección:

```
http://127.0.0.1:8080
```

Funciona sin Wi-Fi ni datos, sin token (nadie más puede alcanzarlo) y es lo más rápido.

### B) Red local (PC, otro móvil, TV box…)

1. Arranca el servidor en el equipo servidor (`./start.sh` ya escucha en `0.0.0.0`).
2. Averigua su IP:

```bash
ifconfig 2>/dev/null | grep inet     # o: ip addr show wlan0
```

3. En la app: **Ajustes → Servidor → Red local** y escribe, por ejemplo:

```
http://192.168.1.40:8080
```

4. Pulsa **Guardar y probar**. El botón **Usar la IP de este teléfono** rellena la IP del
   propio móvil, y **Buscar** escanea la red local (loopback, tu IP y la subred) para
   encontrar el servidor solo.

Recomendado: define `api_token` en `config.php`, porque cualquiera en tu Wi-Fi podría usar el
servidor.

### C) Internet (VPS, túnel, servidor remoto)

Cualquier máquina con PHP y yt-dlp sirve. Lo importante es **HTTPS + token**:

**Opción rápida · Cloudflare Tunnel** (sin abrir puertos ni IP fija):

```bash
pkg install -y cloudflared
cloudflared tunnel --url http://127.0.0.1:8080
# Te da una URL https://algo-random.trycloudflare.com
```

**Opción Tailscale** (red privada entre tus dispositivos):

```bash
pkg install -y tailscale
tailscaled &
tailscale up
tailscale ip -4          # 100.x.y.z
```

**Opción VPS** (Apache/Nginx + PHP):

```bash
sudo apt install -y php-cli php-mbstring ffmpeg python3-pip
sudo pip3 install -U yt-dlp
# Copia la carpeta server/ a /var/www/reeldrop
```

En la app: **Ajustes → Servidor → Internet** y escribe por ejemplo
`https://reeldrop.tudominio.com`. Define siempre `api_token`; la app lo manda en cada
petición (`X-Api-Token`). Si usas `http://` en un host público, la app te avisa.

---

## 6. Paso 3 · Instalar la app

### Opción rápida: APK ya compilado

1. Descarga `release/ReelDrop-v1.0.0.apk` (también está en *Releases* del repositorio).
2. Ábrelo en el móvil y permite **instalar apps de origen desconocido** cuando lo pida.
3. Al abrir la app por primera vez te pedirá los permisos necesarios.

### Opción avanzada: compilar tú mismo

```bash
git clone https://github.com/ManuelAlmaguer/ManuInstaDownloaderAppAndroid.git
cd ManuInstaDownloaderAppAndroid
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # APK firmado (usa keystore.properties)
```

---

## 7. Paso 4 · Configurar la app

**Ajustes → Servidor**

* **¿Dónde está tu servidor?** Mismo móvil / Red local / Internet.
* **Dirección del servidor**: por ejemplo `http://127.0.0.1:8080`.
* **Token de API**: el que generó `setup-termux.sh`.
* **Guardar y probar**: guarda y muestra latencia, versión del servidor, versión de yt-dlp,
  ffmpeg y espacio libre.
* **Buscar**: prueba loopback, tu IP de Wi-Fi y barre la subred local en los puertos 8080/9000.

**Ajustes → Descargas**

* Calidad predeterminada (Máxima, 1080p, 720p, 480p, solo audio).
* Descargas simultáneas (1–5) y reintentos automáticos (0–5).
* Reintentar cuando falle, guardar al terminar, solo Wi-Fi, mantener pantalla activa.

---

## 8. Funciones de la app

* **Descargar por URL**: pega el enlace o comparte vídeos desde Instagram, YouTube o Facebook
  (*Compartir → Manu ReelDrop*).
* **Cola de descargas** con estados en vivo: en cola, descargando, procesando, reintentando,
  completado, error.
* **Progreso en tiempo real**: porcentaje, velocidad, tamaño descargado/total y tiempo
  restante, con anillo y barra de progreso.
* **Gestión de descargas**: cancelar, reintentar, quitar del historial, cancelar todas,
  limpiar finalizadas. El historial sobrevive a reinicios.
* **Reintentos automáticos** con espera progresiva (1,5 s → 3 s → 6 s…) más *jitter*, y
  recuperación al volver la conexión y al reiniciar el móvil.
* **Robustez**: si el stream de progreso se corta, cambia a *polling*; si el servidor es
  antiguo, usa el modo compatible; si falta yt-dlp, te dice el comando a ejecutar.
* **Biblioteca** con miniaturas, duración y resolución: reproducir dentro de la app (Media3),
  guardar en el teléfono, compartir, eliminar del servidor o buscar.
* **Carpeta personalizada** en el teléfono o la tarjeta SD (sección 11).
* **Notificaciones** de progreso y de resultado con acciones (sección 12).
* **Temas** (12 paletas, claro/oscuro/automático, Material You) — sección 9.
* **Biblioteca** con pestañas para el servidor, la carpeta del teléfono y los temporales del
  servidor; los temporales se pueden revisar y borrar individualmente.
* **Acerca de** con versión, autor, estado del servidor, privacidad y novedades.

---

## 9. Temas visuales

Disponibles en **Ajustes → Temas**, se aplican al instante:

| Tema | Estilo |
|---|---|
| **Neón Púrpura** (predeterminado) | El gradiente violeta → rosa → naranja de la web original. |
| **Medianoche AMOLED** | Negro puro, ideal para pantallas OLED. |
| **Océano** | Azules y turquesa. |
| **Atardecer** | Naranja, rojo y rosa. |
| **Bosque** | Verdes y lima. |
| **Chicle** | Rosa y lavanda. |
| **Cereza** | Carmesí, rosa y naranja. |
| **Ártico** | Azul hielo e índigo. |
| **Aurora** | Verde, cian y violeta. |
| **Grafito** | Gris azulado de alto contraste. |
| **Color dinámico** | Toma la paleta del fondo de pantalla (Android 12+). |
| **Sistema** | Gris neutro, se adapta a claro/oscuro. |

Cada tema incluye modo **Oscuro**, **Claro** y **Automático**, y el interruptor de
**color dinámico (Material You)**.

---

## 10. Permisos de Android

**Ajustes → Permisos de Android** muestra el estado de cada permiso y permite pedirlos de uno
en uno o todos a la vez:

| Permiso | Para qué sirve | Obligatorio |
|---|---|---|
| **Notificaciones** (Android 13+) | Progreso y avisos de fin/error. | Sí |
| **Videos y audio** | Leer los archivos guardados y mostrarlos en la biblioteca. | Recomendado |
| **Descargas en segundo plano** (exención de batería) | Que Android no suspenda descargas largas. | Recomendado |

También hay un botón **Abrir ajustes de Manu ReelDrop en Android**. En el primer arranque la app
pide los permisos pendientes una sola vez.

---

## 11. Carpeta personalizada de descargas

**Ajustes → Carpeta de descargas → Elegir carpeta** abre el selector del sistema: puedes
elegir cualquier carpeta del almacenamiento interno, la tarjeta SD o un proveedor de
documentos. La app guarda el permiso (SAF) y entonces:

* cada descarga terminada se **copia automáticamente** a esa carpeta (con progreso visible),
* la pestaña **Biblioteca → Carpeta del teléfono** lista esos videos para reproducirlos o
  compartirlos desde la app,
* puedes borrarlos con un toque.

La pestaña **Biblioteca → Temporales** muestra los `.part`, `.ytdl`, `.tmp`, fragmentos y
metadatos `.info.json` que yt-dlp haya dejado en el servidor. Puedes eliminar cada fichero
desde la app o usar la limpieza global.

Si no eliges carpeta, los videos se guardan en `Movies/ReelDrop` con el gestor de descargas de
Android (o simplemente se quedan en el servidor, en `server/downloads`).

---

## 12. Notificaciones

* **Progreso** (canal *Progreso de descargas*): una sola tarjeta para toda la cola, con
  porcentaje medio, velocidad total, tamaño, tiempo restante y botón *Cancelar todo*.
* **Resultado** (canal *Resultados de descarga*): una tarjeta reutilizable al terminar o
  fallar, con *Abrir* o *Reintentar*, sin apilar una notificación por cada evento.

El servicio en primer plano mantiene la cola viva con la app cerrada. Si no quieres
notificaciones, desactívalas en Ajustes o en el sistema.

---

## 13. Retoques finos del servidor

Todo se configura en `server/config.php` (si no existe, se usan los valores de
`config.example.php`):

| Clave | Para qué |
|---|---|
| `api_token` | Contraseña compartida entre app y servidor. Obligatoria en Internet. |
| `max_concurrent_jobs` | Descargas simultáneas en el servidor (por defecto 3). |
| `cookies_file` | `cookies.txt` para contenido privado o con edad restringida. |
| `downloads_dir` / `data_dir` / `logs_dir` | Rutas de trabajo. |
| `retention_days` | Borrar automáticamente lo antiguo (`0` = no borrar nada). |
| `ytdlp_path`, `ffmpeg_path`, `ffprobe_path` | Forzar rutas si no se detectan solas. |
| `default_quality` | Calidad cuando la app no la envía. |

Cookies de Instagram (solo si algún video lo necesita):

```bash
# Exporta cookies.txt desde tu navegador con una extensión tipo "Get cookies.txt"
cp /sdcard/Download/cookies.txt ~/ManuInstaDownloaderAppAndroid/server/cookies.txt
# y en config.php: 'cookies_file' => __DIR__ . '/cookies.txt',
```

---

## 14. API del servidor

Todas las acciones viven en `api.php` y devuelven JSON (o SSE para `events`). Si el servidor
tiene token, envía `X-Api-Token: <token>` o `?token=<token>`.

| Acción | Método | Descripción |
|---|---|---|
| `health` | GET | Estado, versión de yt-dlp/ffmpeg, espacio libre, trabajos activos. |
| `job-create` | POST `{url, quality}` | Crea la descarga y la arranca en segundo plano. |
| `job` | GET `?id=` | Estado de un trabajo. |
| `jobs` | GET `?limit=` | Últimos trabajos. |
| `events` | GET `?id=` | Progreso en vivo (SSE) hasta que termina. |
| `job-cancel` | POST `{id}` | Cancela y mata el proceso en el servidor. |
| `job-retry` | POST `{id}` | Reintenta con un trabajo nuevo. |
| `job-delete` | POST `{id}` | Borra el registro del trabajo. |
| `job-cancel-all` | POST | Cancela todos los activos. |
| `library` | GET `?q=` | Lista de videos con tamaño, duración, resolución y miniatura. |
| `library-delete` | POST `{file}` | Borra un video del servidor. |
| `file` | GET `?file=` | Streaming del video con soporte de rangos (buscar y reanudar). |
| `thumb` | GET `?file=` | Miniatura JPEG (se genera con ffmpeg si hace falta). |
| `temporary` | GET | Lista temporales del directorio `downloads` con tamaño, fecha y tipo. |
| `temporary-delete` | POST `{file}` | Borra un temporal validado individualmente. |
| `cleanup` | POST | Borra temporales (`.part`, `.ytdl`, `.tmp`, fragmentos y `.info.json`). |
| *Compatibilidad* | | `download-stream`, `list`, `delete` y `download` siguen funcionando como en la web original. |

Detalles completos en [`docs/API.md`](docs/API.md).

---

## 15. Compilar la app desde el código

```bash
# Requisitos: JDK 17+ y Android SDK 35
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug           # APK de pruebas
./gradlew assembleRelease         # APK firmado
./gradlew test                    # tests unitarios
```

El APK de release se firma con `keystore.properties` + `reeldrop-release.jks` incluidos en el
repositorio (uso personal). **Si publicas la app, genera tu propio keystore y guárdalo en
secreto**:

```bash
keytool -genkeypair -v -keystore mi-keystore.jks -alias reeldrop \
        -keyalg RSA -keysize 2048 -validity 10000
```

El flujo de GitHub Actions (`.github/workflows/android.yml`) compila la app en cada push y
publica los APK como artefactos.

---

## 16. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| «No se pudo conectar con el servidor» | El servidor no está arrancado o la IP/puerto no coinciden. En Termux: `./start.sh`; en la app: **Ajustes → Buscar**. |
| «Token de API incorrecto o ausente» | Copia el token de `config.php` en Ajustes → Servidor. |
| «yt-dlp no está instalado» | En Termux: `pkg install python && pip install -U yt-dlp`. |
| «No se pudo generar la miniatura» | Falta ffmpeg: `pkg install ffmpeg`. |
| La descarga se queda en «Procesando» | La plataforma pide cookies o cambió el formato: `pip install -U yt-dlp` y, si hace falta, usa `cookies_file`. |
| Fallos intermitentes de red | La app reintenta sola; puedes subir «Reintentos automáticos» en Ajustes. |
| El móvil mata la descarga larga | Da a Termux batería «Sin restricciones» y activa «Descargas en segundo plano» en la app. |
| No aparecen los videos en la galería | Usa **Biblioteca → Guardar** o configura una **carpeta personalizada**. |
| La app no ve el servidor en Internet | Usa `https://`, activa el token y comprueba que el túnel está abierto. |

---

## 17. Estructura del proyecto

```
ManuInstaDownloaderAppAndroid/
├── app/                            # App Android (Kotlin + Compose)
│   └── src/main/java/com/manu/reeldrop/
│       ├── core/                   # constantes, formateadores, contenedor de dependencias
│       ├── data/                   # API (OkHttp), DataStore, cola persistente, repositorios
│       ├── domain/                 # modelos (trabajos, biblioteca, temas, modos de servidor)
│       ├── service/                # motor de descargas, servicio en primer plano, notificaciones
│       ├── ui/                     # pantallas Compose, componentes y temas
│       └── util/                   # URLs, red, carpeta SAF, permisos
├── server/                         # Servidor PHP + yt-dlp
│   ├── api.php                     # API (trabajos, biblioteca, streaming)
│   ├── index.php                   # panel web
│   ├── lib/                        # worker, store, ytdlp, media, utilidades
│   ├── assets/                     # CSS y JS del panel web
│   ├── setup-termux.sh             # instalador para Termux
│   └── start.sh                    # arranque del servidor
├── docs/                           # API, servidor y arquitectura
├── release/                        # APK compilados
└── HANDOFF.md                      # estado y siguientes pasos del proyecto
```

---

## 18. Aviso legal

Manu ReelDrop es una herramienta **personal** para descargar contenido propio o con
permiso, o material de dominio público. Respeta los términos de uso de Instagram, YouTube,
Facebook y los
derechos de autor: no redistribuyas contenido ajeno.

El servidor se ejecuta en tu dispositivo o en tu servidor; la app no envía datos a terceros.
Si lo publicas en Internet, protege siempre el acceso con token (y HTTPS).

---

**Autor:** Manuel Almaguer Sosa · manu004@atomicmail.io
**Repositorio:** https://github.com/ManuelAlmaguer/ManuInstaDownloaderAppAndroid

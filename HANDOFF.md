# HANDOFF · Manu ReelDrop (ManuInstaDownloaderAppAndroid)

## Instrucción principal (leer siempre primero)

**Al terminar de crear o editar cualquier archivo hay que subir TODO a la rama `main`.**
Sin excepciones y sin dejarse nada:

1. Código de la app Android (`app/`, `gradle/`, `*.gradle.kts`).
2. Servidor (`server/`).
3. Documentación (`README.md`, `docs/`, `HANDOFF.md`).
4. **El APK compilado** (`release/ReelDrop-*.apk`) y los archivos de firma
   (`keystore.properties`, `reeldrop-release.jks`).
5. Flujos de CI (`.github/workflows/`).

Comando de referencia:

```bash
git add -A
git commit -m "Descripción corta del cambio"
git push origin main
```

Nunca dejes cambios importantes solo en local. Si el APK cambia, súbelo otra vez y publica una
release nueva (la siguiente es v1.2.0).

---

## 1. Qué es el proyecto

App Android (Kotlin + Jetpack Compose) para descargar vídeos de Instagram, YouTube y Facebook usando un servidor
propio (Termux + PHP + yt-dlp). Sustituye a la web original (`index.php` + `api.php`),
aprovechando su lógica y ampliándola con cola de trabajos, progreso en vivo, biblioteca,
notificaciones, temas y configuración avanzada.

* **Nombre visible:** Manu ReelDrop. Se conserva el paquete `com.manu.reeldrop` y la ruta
  `Movies/ReelDrop` para compatibilidad.
* **Paquete Android:** `com.manu.reeldrop` (debug: `com.manu.reeldrop.debug`).
* **Versión de código:** 1.2.0 (versionCode 3); los APK v1.2.0 se generan en CI y se conservan junto a las referencias 1.1.0 y 1.0.0.
* **Autor:** Manuel Almaguer Sosa · manu004@atomicmail.io.

---

## 2. Estado actual

| Área | Estado |
|---|---|
| Análisis de la web original (zip) | Hecho (index.php, api.php, style.css, app.js) |
| Proyecto Android completo | Hecho (≈40 archivos Kotlin, recursos, temas, tests) |
| Servidor PHP 2.2.0 con cola de trabajos y análisis dinámico | Hecho (api.php, lib/*, panel web, instalador) |
| Paquete de servidor para entregar | `release/ManuReelDrop-server-v2.2.0.zip`, sin `data/`, `downloads/` ni `logs/` |
| APK debug compilado y verificado | `release/ManuReelDrop-v1.2.0-debug.apk`, generado por CI |
| APK release firmado | `release/ManuReelDrop-v1.2.0.apk`, generado por CI; se conservan las referencias 1.1.0 y 1.0.0 |
| Validación técnica | `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, lint PHP y comprobación de opciones de calidad; CI Android correcto |
| README + docs + HANDOFF | Hecho |
| Publicado en `main` | Hecho (ver historial de commits) |

### Qué se ha implementado exactamente

* **App**: pantalla de descarga con validación de enlaces de Instagram, YouTube y Facebook,
  pegado desde el portapapeles y recepción por *share*; selector de calidad; cola de descargas
  con progreso, velocidad, ETA y tamaño; biblioteca con miniaturas, reproductor Media3,
  guardado en el dispositivo y borrado remoto; ajustes completos; pantalla *Acerca de*.
* **Selector dinámico de calidad**: primero analiza el enlace en el servidor, muestra las alturas
  realmente disponibles y conserva Mejor calidad como opción fallback; la descarga empieza solo
  después de elegir una opción.
* **Motor de descargas**: cola persistente (JSON atómico), concurrencia configurable, SSE con
  *fallback* a polling, reintentos con backoff exponencial + jitter, modo compatible con el
  `api.php` antiguo y auto-guardado en el dispositivo o en una carpeta SAF elegida por el
  usuario.
* **Notificaciones**: servicio en primer plano y una única tarjeta de progreso agrupada para toda
  la cola, con velocidad, tamaño, ETA y acción de cancelar; resultado reutilizable (abrir/reintentar)
  y canales independientes para evitar tarjetas duplicadas o acumuladas. Progreso, servicio inactivo,
  éxito y error usan jerarquía visual, estados etiquetados y detalles legibles; se descartan las
  líneas compactas heredadas del servidor.
* **Termux**: la tarjeta del servidor incluye un botón para abrir la aplicación Termux. Por las
  restricciones de Android, el botón abre la actividad de Termux pero no ejecuta start.sh en
  segundo plano.
* **Biblioteca**: pestañas para servidor, carpeta del teléfono y temporales; estos últimos se
  consultan y eliminan mediante `temporary`/`temporary-delete` sin bloquear el hilo visual.
  Toda eliminación o limpieza muestra confirmación antes de ejecutarse.
* **Temas**: 12 paletas + claro/oscuro/automático + Material You; el selector se muestra en
  cuadrícula vertical para que todas las paletas sean visibles sin depender del desplazamiento
  horizontal.
* **Red y preferencias**: el checkbox de token mantiene el campo oculto mientras no se usa;
  «Solo por Wi-Fi» deja las descargas en espera con datos móviles y las reanuda al recuperar Wi-Fi.
  El monitor comprueba el servidor cada 5 segundos conectado y redescubre cada 10 segundos si se
  pierde.
* **Permisos**: sección propia con estado, petición individual o masiva y acceso a los ajustes
  del sistema (incluida la exención de batería).
* **Servidor**: acepta hosts de Instagram, YouTube y Facebook mediante `allowed_hosts` (incluidos
  automáticamente al migrar configuraciones antiguas); acciones
  `health`, `analyze`, `job-create`, `job`, `jobs`,
  `events`, `job-cancel`, `job-retry`, `job-delete`,
  `job-cancel-all`, `library`, `library-delete`, `file` (con
  rangos HTTP), `thumb`, `temporary`, `temporary-delete`,
  `cleanup` y compatibilidad total con la API antigua.

---

## 3. Decisiones técnicas (y por qué)

| Decisión | Motivo |
|---|---|
| Kotlin + Compose + Material 3 | Estándar actual, UI moderna y temas fáciles. |
| Sin Hilt ni Room | Menos dependencias y compilaciones más rápidas; `ServiceLocator` y JSON atómico cubren el caso. |
| OkHttp + kotlinx.serialization | Cliente HTTP ligero y orientado a streaming SSE. |
| Cola en JSON con escritura atómica | Sobrevive a reinicios y a procesos matados por Android. |
| Servicio en primer plano `dataSync` | Requisito de Android 14+ para descargas en segundo plano. |
| El servidor descarga, la app orquesta | El trabajo pesado ocurre en Termux; la app consume poca batería. |
| R8 desactivado en release | Garantiza que el APK entregado se comporta como la build probada (activarlo más adelante). |
| Firma incluida en el repo | Comodidad personal; el README avisa de generar un keystore propio si se publica. |

---

## 4. Cómo construir y probar

```bash
# Dependencias: JDK 17+ y Android SDK 35 (platform-tools, platforms;android-35, build-tools;35.0.0)
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug        # APK de prueba
./gradlew assembleRelease      # APK firmado (usa keystore.properties)
./gradlew test                 # tests unitarios (Formatters, UrlUtils)
./gradlew lint                 # informe de lint
```

Servidor en Termux (la app no inicia este proceso ni abre el puerto):

```bash
cd server && cp config.example.php config.php && ./start.sh
curl "http://127.0.0.1:8080/api.php?action=health"
```

---

## 5. Pendientes y siguientes pasos (por orden sugerido)

1. **Probar en el móvil real**: instalar el APK 1.2.0 generado por CI, configurar el servidor y completar una
   descarga (progreso, notificación y biblioteca).
2. **Código QR de configuración**: generar en `start.sh`/panel web un QR con `base_url` +
   token para configurar la app sin escribir nada.
3. **Descarga con la app cerrada**: valorar WorkManager por descarga en lugar del servicio en
   primer plano.
4. **Activar R8** en release con reglas verificadas y medir el APK resultante.
5. **Asistente de primera ejecución** (onboarding) que guíe servidor + permisos + carpeta.
6. **Widget de Android** con últimas descargas y acceso rápido.
7. **Estadísticas** en *Acerca de*: total descargado, velocidad media, tiempo ahorrado.
8. **Tests de instrumentación** (Compose UI tests) para home, cola y biblioteca.
9. **Publicar el APK 1.2.0** generado por CI como nueva release de GitHub cuando se valide en un móvil.
10. **Repaso de textos**: ya hay `values` (inglés) y `values-es`; revisar traducciones nuevas.

---

## 6. Riesgos conocidos

* Instagram, YouTube y Facebook cambian a menudo: si falla, `pip install -U yt-dlp` y, si hace falta,
  `cookies_file`.
* La primera ejecución pide permisos; si el usuario los deniega, no habrá notificaciones hasta
  que los conceda en Ajustes → Permisos.
* Los APK del repositorio están firmados con una clave incluida en el proyecto: válido para uso
  personal, no para publicar en Play Store.
* Las IP locales cambian (DHCP): si el servidor está en otro equipo, revisa la dirección o usa
  **Buscar**.
* La lógica de **Solo por Wi-Fi** quedó validada en código, tests y compilación; falta alternar
  físicamente entre datos móviles y Wi-Fi en un teléfono Android para completar la prueba de radio.

---

## 7. Contacto rápido

Manuel Almaguer Sosa · manu004@atomicmail.io

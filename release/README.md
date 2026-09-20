# APK de Manu ReelDrop

Los APK v1.1.0 fueron generados y verificados por GitHub Actions. La versión 1.0.0 se conserva
como referencia para instalaciones anteriores.

| Archivo | Para qué |
|---|---|
| `ManuReelDrop-v1.1.0.apk` | Versión de release, firmada. Es la recomendada para instalar. |
| `ManuReelDrop-v1.1.0-debug.apk` | Build de depuración (más pesada). Útil para probar cambios. |
| `ReelDrop-v1.0.0.apk` | Release histórica conservada para instalaciones anteriores. |
| `ReelDrop-v1.0.0-debug.apk` | Debug histórico conservado para comparación. |

## Instalar en el móvil

1. Copia el APK al teléfono (o descárgalo desde la release de GitHub).
2. Ábrelo y acepta **instalar apps de origen desconocido** si Android lo pide.
3. Al abrir Manu ReelDrop por primera vez, concede los permisos que solicite
   (notificaciones, videos/audio y descargas en segundo plano).
4. Ve a **Ajustes → Servidor**, elige dónde está tu servidor y pega la dirección
   (`http://127.0.0.1:8080` si usas Termux en este mismo móvil) y el token de API.

## Firma

La release está firmada con `reeldrop-release.jks` (alias `reeldrop`) usando las credenciales de
`keystore.properties`, ambos incluidos en el repositorio para uso personal.

Si vas a publicar la app en una tienda o compartirla ampliamente, genera tu propio keystore y
guárdalo en secreto:

```bash
keytool -genkeypair -v -keystore mi-keystore.jks -alias reeldrop \
        -keyalg RSA -keysize 2048 -validity 10000
```

## Compilar uno nuevo

```bash
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk release/ManuReelDrop-v1.1.0.apk
git add -A && git commit -m "Publicar APK Manu ReelDrop v1.1.0" && git push origin main
```

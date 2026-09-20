#!/data/data/com.termux/files/usr/bin/bash
#
# Manu ReelDrop Server - instalador para Termux (Android)
#
#   bash setup-termux.sh
#
# Instala todo lo necesario, crea config.php y deja el servidor listo para arrancar.

set -e

echo "== Manu ReelDrop Server: instalando dependencias =="
pkg update -y || true
pkg install -y php python ffmpeg openssl termux-api || pkg install -y php python ffmpeg openssl

echo "== Instalando yt-dlp =="
if command -v pip >/dev/null 2>&1; then
  pip install -U yt-dlp
else
  pkg install -y python-pip && pip install -U yt-dlp
fi

echo "== Preparando carpetas =="
mkdir -p downloads data/jobs data/meta data/thumbs logs

if [ ! -f config.php ]; then
  cp config.example.php config.php
  TOKEN=$(openssl rand -hex 16)
  php -r '
    $file = "config.php";
    $content = file_get_contents($file);
    $content = preg_replace("/'"'"'api_token'"'"' => '"'"''"'"'/", "'"'"'api_token'"'"' => '"'"'" . $argv[1] . "'"'"'", $content, 1);
    file_put_contents($file, $content);
  ' "$TOKEN"
  echo
  echo "  Token de API generado: $TOKEN"
  echo "  (cópialo en la app: Ajustes -> Servidor -> Token de API)"
  echo
fi

chmod +x start.sh 2>/dev/null || true

echo "== Todo listo =="
echo "Arranca el servidor con:"
echo "    ./start.sh"
echo
echo "Y prueba desde el navegador del móvil:  http://127.0.0.1:8080/api.php?action=health"

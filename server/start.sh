#!/data/data/com.termux/files/usr/bin/bash
#
# Arranca el servidor Manu ReelDrop en Termux.
#
#   ./start.sh              -> escucha en todas las interfaces, puerto 8080
#   PORT=9000 ./start.sh    -> cambia el puerto
#
# Para que arranque solo al encender el móvil instala Termux:Boot y copia este script en
# ~/.termux/boot/ (ver docs/SERVER.md).

cd "$(dirname "$0")"

PORT="${PORT:-8080}"
HOST="${HOST:-0.0.0.0}"

echo "Manu ReelDrop Server escuchando en http://$HOST:$PORT"
echo "  · Este móvil:   http://127.0.0.1:$PORT/api.php?action=health"
echo "  · Red local:    http://$(ip route get 1 2>/dev/null | awk '{print $7; exit}'):$PORT"
echo "  · Detén con Ctrl+C"
echo

exec php -S "$HOST:$PORT" -t .

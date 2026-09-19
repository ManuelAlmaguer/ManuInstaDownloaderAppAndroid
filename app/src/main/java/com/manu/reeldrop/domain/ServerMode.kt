package com.manu.reeldrop.domain

/**
 * Where the user decided to run the ReelDrop server. It only changes the helper text,
 * the suggested address and the security warnings — the app always talks to one URL.
 */
enum class ServerMode(val id: String, val label: String, val hint: String, val defaultPort: Int) {
    SAME_PHONE(
        id = "same_phone",
        label = "Mismo móvil",
        hint = "Termux corre en este teléfono: la app se conecta por 127.0.0.1 (loopback).",
        defaultPort = 8080,
    ),
    LAN(
        id = "lan",
        label = "Red local",
        hint = "El servidor está en otro equipo de tu Wi-Fi (PC, otro móvil, TV box) o prefieres usar la IP del teléfono.",
        defaultPort = 8080,
    ),
    INTERNET(
        id = "internet",
        label = "Internet",
        hint = "El servidor se publica por Internet (VPS, Cloudflare Tunnel, Tailscale…). Usa https:// y activa el token.",
        defaultPort = 443,
    );

    companion object {
        fun fromId(id: String?): ServerMode = entries.firstOrNull { it.id == id } ?: SAME_PHONE
    }
}

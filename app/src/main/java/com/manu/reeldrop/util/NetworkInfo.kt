package com.manu.reeldrop.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Helpers to discover where the Termux server can live:
 *  * `127.0.0.1` when the server runs on this same phone,
 *  * the phone's Wi-Fi IP when the server runs on another device of the LAN,
 *  * any public host (VPS, tunnel) when the server is hosted on the Internet.
 */
object NetworkInfo {

    /** The phone's own IPv4 address on Wi-Fi/Ethernet, when it has one. */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** "192.168.1.50" -> "192.168.1" so we can scan the local subnet for the server. */
    fun subnetPrefix(ip: String?): String? {
        val value = ip ?: return null
        val parts = value.split('.')
        if (parts.size != 4) return null
        return parts.take(3).joinToString(".")
    }

    /** Addresses worth probing inside the LAN: the phone itself, the gateway and neighbours. */
    fun lanCandidates(ip: String?, maxNeighbours: Int = 48): List<String> {
        val prefix = subnetPrefix(ip) ?: return emptyList()
        val gateway = "$prefix.1"
        val neighbours = (2..maxNeighbours + 1).map { "$prefix.$it" }
        return (listOf(gateway) + neighbours).distinct()
    }

    fun isLoopbackHost(host: String?): Boolean {
        val value = host?.lowercase() ?: return false
        return value == "localhost" || value == "127.0.0.1" || value == "::1" || value == "10.0.2.2"
    }

    fun isPrivateHost(host: String?): Boolean {
        val value = host?.lowercase() ?: return false
        if (isLoopbackHost(value)) return true
        val parts = value.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }
}

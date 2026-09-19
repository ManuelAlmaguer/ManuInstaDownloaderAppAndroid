package com.manu.reeldrop.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watches for connectivity changes: when the phone gets a network again, queued
 * downloads are resumed automatically (and with the right Wi-Fi policy applied).
 */
class NetworkMonitor(private val context: Context, private val scope: CoroutineScope) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null || manager == null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { ResumeWorker.enqueue(context) }
            }
        }
        callback = networkCallback
        runCatching { manager.registerNetworkCallback(request, networkCallback) }
    }

    fun stop() {
        val networkCallback = callback ?: return
        runCatching { manager?.unregisterNetworkCallback(networkCallback) }
        callback = null
    }

    fun isOnline(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isOnWifi(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

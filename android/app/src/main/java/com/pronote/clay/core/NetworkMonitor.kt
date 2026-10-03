package com.pronote.clay.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NetworkMonitor — the Android ConnectivityManager equivalent.
 * The UI observes [online] to switch between the success and offline states.
 */
class NetworkMonitor(context: Context) {

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onLost(network: Network) {
            _online.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun start() {
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure { Logger.warn("Network callback registration failed") }
    }

    fun isOnline(): Boolean = _online.value

    private fun currentlyOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

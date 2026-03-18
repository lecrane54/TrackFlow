package com.trackflow.core.pipeline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.trackflow.core.logging.TrackFlowLogger

internal class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    var isOnline: Boolean = checkConnectivity()
        private set

    var onConnectivityChanged: ((Boolean) -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !isOnline
            isOnline = true
            if (wasOffline) {
                TrackFlowLogger.debug("Network restored")
                onConnectivityChanged?.invoke(true)
            }
        }

        override fun onLost(network: Network) {
            isOnline = checkConnectivity()
            if (!isOnline) {
                TrackFlowLogger.debug("Network lost")
                onConnectivityChanged?.invoke(false)
            }
        }
    }

    fun register() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to register network callback", e)
        }
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to unregister network callback", e)
        }
    }

    private fun checkConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to check connectivity", e)
            true // Assume online if we can't check
        }
    }
}

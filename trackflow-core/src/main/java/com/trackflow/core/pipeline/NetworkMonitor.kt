package com.trackflow.core.pipeline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.trackflow.core.logging.TrackFlowLogger

/**
 * Monitors device network connectivity and notifies observers of changes.
 *
 * Uses the Android [ConnectivityManager] to register a [NetworkCallback][ConnectivityManager.NetworkCallback]
 * that listens for network availability and loss events. The current connectivity
 * state is exposed via the [isOnline] property, and an optional [onConnectivityChanged]
 * callback is invoked whenever the state transitions.
 *
 * Usage:
 * 1. Call [register] to start listening for connectivity changes.
 * 2. Read [isOnline] at any time to check the current state.
 * 3. Set [onConnectivityChanged] to receive transition notifications.
 * 4. Call [unregister] to stop listening and release system resources.
 *
 * @param context The Android [Context] used to obtain the [ConnectivityManager] system service.
 */
internal class NetworkMonitor(context: Context) {

    /** The system connectivity manager used to register callbacks and query network state. */
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Whether the device currently has internet connectivity.
     *
     * This value is updated by the registered network callback and initialized
     * by [checkConnectivity] at construction time. Reads are volatile to ensure
     * visibility across threads.
     */
    @Volatile
    var isOnline: Boolean = checkConnectivity()
        private set

    /**
     * Optional callback invoked when connectivity state changes.
     *
     * The callback receives `true` when connectivity is restored and `false`
     * when connectivity is lost. It is only invoked on actual state transitions
     * (not on duplicate events).
     */
    var onConnectivityChanged: ((Boolean) -> Unit)? = null

    /**
     * Internal network callback that updates [isOnline] and notifies
     * [onConnectivityChanged] on connectivity transitions.
     */
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

    /**
     * Registers the network callback with the system [ConnectivityManager].
     *
     * The callback listens for networks with [NetworkCapabilities.NET_CAPABILITY_INTERNET].
     * If registration fails (e.g., due to a missing permission), the error is logged
     * and the monitor falls back to the last known state.
     */
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

    /**
     * Unregisters the network callback from the system [ConnectivityManager].
     *
     * Should be called when the monitor is no longer needed to avoid leaking
     * system resources. If unregistration fails, the error is logged.
     */
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to unregister network callback", e)
        }
    }

    /**
     * Performs a synchronous check of the current network connectivity.
     *
     * Queries the active network for [NetworkCapabilities.NET_CAPABILITY_INTERNET].
     * If the check fails due to an exception, it optimistically assumes online
     * status to avoid silently dropping events.
     *
     * @return `true` if the device has internet capability, `false` otherwise.
     */
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

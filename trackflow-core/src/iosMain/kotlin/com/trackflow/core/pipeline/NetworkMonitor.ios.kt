@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.trackflow.core.pipeline

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.platform.PlatformContext
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

internal actual class NetworkMonitor actual constructor(context: PlatformContext) {
    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create("com.trackflow.network", null)

    actual var isOnline: Boolean = true
    actual var onConnectivityChanged: ((Boolean) -> Unit)? = null

    actual fun register() {
        try {
            nw_path_monitor_set_update_handler(monitor) { path ->
                val wasOnline = isOnline
                isOnline = nw_path_get_status(path) == nw_path_status_satisfied
                if (isOnline != wasOnline) {
                    onConnectivityChanged?.invoke(isOnline)
                }
            }
            nw_path_monitor_set_queue(monitor, queue)
            nw_path_monitor_start(monitor)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to register network monitor", e)
        }
    }

    actual fun unregister() {
        try {
            nw_path_monitor_cancel(monitor)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to unregister network monitor", e)
        }
    }
}

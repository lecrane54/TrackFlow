package com.trackflow.core.pipeline

import com.trackflow.core.platform.PlatformContext

internal expect class NetworkMonitor(context: PlatformContext) {
    var isOnline: Boolean
    var onConnectivityChanged: ((Boolean) -> Unit)?
    fun register()
    fun unregister()
}

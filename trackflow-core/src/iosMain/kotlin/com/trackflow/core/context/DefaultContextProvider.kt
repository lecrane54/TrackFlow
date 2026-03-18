package com.trackflow.core.context

import com.trackflow.core.platform.PlatformContext
import platform.UIKit.UIDevice
import platform.Foundation.NSBundle

class DefaultContextProvider(private val context: PlatformContext) {
    fun context(): Map<String, Any?> {
        return mapOf(
            "platform" to "ios",
            "device_model" to UIDevice.currentDevice.model,
            "os_version" to UIDevice.currentDevice.systemVersion,
            "package_name" to (NSBundle.mainBundle.bundleIdentifier ?: "unknown")
        )
    }
}

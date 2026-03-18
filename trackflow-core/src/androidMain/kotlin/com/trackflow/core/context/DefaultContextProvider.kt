package com.trackflow.core.context

import android.os.Build
import com.trackflow.core.platform.PlatformContext

/**
 * Provides default device and application context metadata for analytics events.
 *
 * This provider gathers information about the running platform, device model,
 * OS version, and application package name. The resulting map is typically
 * merged into the `context` field of every [com.trackflow.core.payload.AnalyticsPayload].
 *
 * @param context The [PlatformContext] (Android Context) used to retrieve the application's package name.
 */
class DefaultContextProvider(private val context: PlatformContext) {

    /**
     * Builds and returns a map of contextual device and application metadata.
     *
     * The returned map contains the following keys:
     * - `"platform"` -- always `"android"`
     * - `"device_model"` -- the end-user-visible device model name (e.g., `"Pixel 7"`)
     * - `"os_version"` -- the Android version string (e.g., `"14"`)
     * - `"package_name"` -- the application's package name (e.g., `"com.example.app"`)
     *
     * @return A [Map] of context key-value pairs describing the current environment.
     */
    fun context(): Map<String, Any?> {
        return mapOf(
            "platform" to "android",
            "device_model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE,
            "package_name" to context.packageName
        )
    }
}

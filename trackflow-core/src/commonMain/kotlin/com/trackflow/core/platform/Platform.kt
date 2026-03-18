package com.trackflow.core.platform

internal expect fun generateUuid(): String

internal expect fun currentTimeMillis(): Long

/**
 * Returns `true` when the host application is running a debug build.
 *
 * On Android this checks `ApplicationInfo.FLAG_DEBUGGABLE`.
 * On iOS this checks whether the `DEBUG` preprocessor flag was set at compile time.
 *
 * Used to relax the provider limit during development so that multiple
 * providers can be tested without a license key.
 */
internal expect fun isDebugBuild(context: PlatformContext): Boolean

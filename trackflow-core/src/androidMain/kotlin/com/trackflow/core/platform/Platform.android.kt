package com.trackflow.core.platform

import android.content.pm.ApplicationInfo

internal actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun isDebugBuild(context: PlatformContext): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

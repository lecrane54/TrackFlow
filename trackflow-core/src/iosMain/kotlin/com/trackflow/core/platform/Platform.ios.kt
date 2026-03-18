@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.trackflow.core.platform

import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun generateUuid(): String = NSUUID().UUIDString()

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual fun isDebugBuild(context: PlatformContext): Boolean {
    // Kotlin/Native sets this flag when compiled in debug mode
    return Platform.isDebugBinary
}

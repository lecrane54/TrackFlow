package com.trackflow.core.platform

import platform.Foundation.NSUUID
import platform.Foundation.NSDate

internal actual fun generateUuid(): String = NSUUID().UUIDString()

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

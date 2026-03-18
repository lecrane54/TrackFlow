package com.trackflow.core.platform

internal actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

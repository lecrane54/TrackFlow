package com.trackflow.core.logging

internal actual fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val prefix = when (level) {
        LogLevel.ERROR -> "ERROR"
        LogLevel.WARN -> "WARN"
        LogLevel.DEBUG -> "DEBUG"
        LogLevel.VERBOSE -> "VERBOSE"
        LogLevel.NONE -> return
    }
    println("[$prefix] $tag: $message")
    throwable?.let { println(it.stackTraceToString()) }
}

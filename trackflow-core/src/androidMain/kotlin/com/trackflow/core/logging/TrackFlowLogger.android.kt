package com.trackflow.core.logging

import android.util.Log

internal actual fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LogLevel.ERROR -> Log.e(tag, message, throwable)
        LogLevel.WARN -> Log.w(tag, message)
        LogLevel.DEBUG -> Log.d(tag, message)
        LogLevel.VERBOSE -> Log.v(tag, message)
        LogLevel.NONE -> { }
    }
}

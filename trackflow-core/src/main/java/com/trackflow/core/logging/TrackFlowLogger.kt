package com.trackflow.core.logging

import android.util.Log

enum class LogLevel { NONE, ERROR, WARN, DEBUG, VERBOSE }

fun interface TrackFlowLogListener {
    fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

object TrackFlowLogger {

    private const val TAG = "TrackFlow"

    var level: LogLevel = LogLevel.ERROR
    var listener: TrackFlowLogListener? = null

    fun error(message: String, throwable: Throwable? = null) {
        if (level >= LogLevel.ERROR) {
            Log.e(TAG, message, throwable)
            listener?.onLog(LogLevel.ERROR, TAG, message, throwable)
        }
    }

    fun warn(message: String) {
        if (level >= LogLevel.WARN) {
            Log.w(TAG, message)
            listener?.onLog(LogLevel.WARN, TAG, message, null)
        }
    }

    fun debug(message: String) {
        if (level >= LogLevel.DEBUG) {
            Log.d(TAG, message)
            listener?.onLog(LogLevel.DEBUG, TAG, message, null)
        }
    }

    fun verbose(message: String) {
        if (level >= LogLevel.VERBOSE) {
            Log.v(TAG, message)
            listener?.onLog(LogLevel.VERBOSE, TAG, message, null)
        }
    }
}

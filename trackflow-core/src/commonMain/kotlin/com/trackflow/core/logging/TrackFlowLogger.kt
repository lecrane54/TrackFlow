package com.trackflow.core.logging

/**
 * Defines the verbosity levels for SDK logging.
 *
 * Levels are ordered from least verbose ([NONE]) to most verbose ([VERBOSE]).
 * A given level enables all messages at that level and below (e.g., [WARN]
 * enables both [WARN] and [ERROR] messages).
 */
enum class LogLevel {
    /** Disables all logging output. */
    NONE,
    /** Logs only error-level messages. */
    ERROR,
    /** Logs warning and error messages. */
    WARN,
    /** Logs debug, warning, and error messages. */
    DEBUG,
    /** Logs all messages including verbose diagnostic output. */
    VERBOSE
}

/**
 * Functional listener interface for receiving SDK log events.
 *
 * Implement this interface to route TrackFlow log messages to a custom
 * logging backend (e.g., Crashlytics, a file logger, or a remote logging service).
 */
fun interface TrackFlowLogListener {
    /**
     * Called when a log message is emitted by the SDK.
     *
     * @param level The severity level of the log message.
     * @param tag The log tag (typically `"TrackFlow"`).
     * @param message The human-readable log message.
     * @param throwable An optional [Throwable] associated with the message, or `null`.
     */
    fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/**
 * Platform-specific logging implementation.
 *
 * On Android this delegates to `android.util.Log`, on other platforms
 * it uses the platform's native logging facility.
 *
 * @param level The severity level of the log message.
 * @param tag The log tag.
 * @param message The human-readable log message.
 * @param throwable An optional [Throwable] associated with the message, or `null`.
 */
internal expect fun platformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)

/**
 * Centralized logging facility for the TrackFlow SDK.
 *
 * All internal SDK components route their log output through this singleton.
 * The current [level] controls which messages are emitted to the platform's
 * logging system. An optional [listener] can be attached to forward log messages to
 * external systems.
 *
 * By default, only [LogLevel.ERROR] messages are emitted.
 */
object TrackFlowLogger {

    /** The log tag used for all SDK log messages. */
    private const val TAG = "TrackFlow"

    /**
     * The minimum log level that will be emitted.
     *
     * Messages below this level are silently discarded. Defaults to [LogLevel.ERROR].
     */
    var level: LogLevel = LogLevel.ERROR

    /**
     * An optional listener that receives every log message passing the [level] filter.
     *
     * Set this to forward SDK logs to a custom logging backend. Set to `null` to disable.
     */
    var listener: TrackFlowLogListener? = null

    /**
     * Logs an error-level message.
     *
     * @param message The error description.
     * @param throwable An optional [Throwable] that caused the error.
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (level >= LogLevel.ERROR) {
            platformLog(LogLevel.ERROR, TAG, message, throwable)
            listener?.onLog(LogLevel.ERROR, TAG, message, throwable)
        }
    }

    /**
     * Logs a warning-level message.
     *
     * @param message The warning description.
     */
    fun warn(message: String) {
        if (level >= LogLevel.WARN) {
            platformLog(LogLevel.WARN, TAG, message, null)
            listener?.onLog(LogLevel.WARN, TAG, message, null)
        }
    }

    /**
     * Logs a debug-level message.
     *
     * @param message The debug information.
     */
    fun debug(message: String) {
        if (level >= LogLevel.DEBUG) {
            platformLog(LogLevel.DEBUG, TAG, message, null)
            listener?.onLog(LogLevel.DEBUG, TAG, message, null)
        }
    }

    /**
     * Logs a verbose-level message.
     *
     * @param message The verbose diagnostic information.
     */
    fun verbose(message: String) {
        if (level >= LogLevel.VERBOSE) {
            platformLog(LogLevel.VERBOSE, TAG, message, null)
            listener?.onLog(LogLevel.VERBOSE, TAG, message, null)
        }
    }
}

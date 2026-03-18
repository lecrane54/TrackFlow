package com.trackflow.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.trackflow.core.TrackFlow
import com.trackflow.core.logging.TrackFlowLogger

/**
 * Automatically tracks application lifecycle events using [ProcessLifecycleOwner].
 *
 * When enabled, tracks:
 * - `app_foregrounded` — when the app comes to the foreground
 * - `app_backgrounded` — when the app goes to the background
 *
 * The first foreground event is reported as `app_opened` instead.
 *
 * Enable via [TrackFlow.Builder]:
 * ```
 * TrackFlow.Builder(context)
 *     .enableLifecycleTracking()
 *     .build()
 * ```
 */
internal class LifecycleTracker : DefaultLifecycleObserver {

    private var isFirstLaunch = true

    /** Registers this observer with the process lifecycle. Must be called on the main thread. */
    fun register() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            TrackFlowLogger.debug("Lifecycle tracker registered")
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to register lifecycle tracker", e)
        }
    }

    /** Unregisters this observer from the process lifecycle. */
    fun unregister() {
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        } catch (e: Exception) {
            TrackFlowLogger.error("Failed to unregister lifecycle tracker", e)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isFirstLaunch) {
            isFirstLaunch = false
            TrackFlow.track("app_opened")
        } else {
            TrackFlow.track("app_foregrounded")
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        TrackFlow.track("app_backgrounded")
        TrackFlow.flush()
    }
}

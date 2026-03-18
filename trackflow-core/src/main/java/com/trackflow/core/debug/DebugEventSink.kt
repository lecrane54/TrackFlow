
package com.trackflow.core.debug

import com.trackflow.core.payload.AnalyticsPayload
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A thread-safe in-memory sink that captures analytics events for debugging and testing.
 *
 * Events recorded via [record] are stored in a [CopyOnWriteArrayList] so they can be
 * safely read from any thread. This is primarily useful during development, automated
 * testing, or when building debug-overlay UIs that display tracked events in real time.
 */
class DebugEventSink {

    /** Thread-safe backing list for all recorded analytics payloads. */
    private val events = CopyOnWriteArrayList<AnalyticsPayload>()

    /**
     * Records a single analytics payload into the debug sink.
     *
     * @param payload The [AnalyticsPayload] to store.
     */
    fun record(payload: AnalyticsPayload) {
        events.add(payload)
    }

    /**
     * Returns a snapshot of all recorded events as an immutable list.
     *
     * The returned list is a defensive copy; modifications to it will not affect
     * the internal event store.
     *
     * @return An immutable [List] of all [AnalyticsPayload] instances recorded so far.
     */
    fun events(): List<AnalyticsPayload> = events.toList()
}

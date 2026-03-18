package com.trackflow.core.debug

import com.trackflow.core.payload.AnalyticsPayload

/**
 * A thread-safe in-memory sink that captures analytics events for debugging and testing.
 *
 * Events recorded via [record] are stored in a synchronized list so they can be
 * safely read from any thread. This is primarily useful during development, automated
 * testing, or when building debug-overlay UIs that display tracked events in real time.
 */
class DebugEventSink(private val maxEvents: Int = 1000) {

    /** Thread-safe backing list for all recorded analytics payloads. Guarded by [lock]. */
    private val events = mutableListOf<AnalyticsPayload>()

    /** Synchronization lock for [events] mutations. */
    private val lock = Any()

    /**
     * Records a single analytics payload into the debug sink.
     * Evicts the oldest event if the max capacity is reached.
     *
     * @param payload The [AnalyticsPayload] to store.
     */
    fun record(payload: AnalyticsPayload) {
        synchronized(lock) {
            events.add(payload)
            while (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
    }

    /**
     * Returns a snapshot of all recorded events as an immutable list.
     *
     * @return An immutable [List] of all [AnalyticsPayload] instances recorded so far.
     */
    fun events(): List<AnalyticsPayload> {
        synchronized(lock) {
            return events.toList()
        }
    }

    /** Clears all recorded events. */
    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }
}

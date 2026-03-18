package com.trackflow.core.debug

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.trackflow.core.payload.AnalyticsPayload

/**
 * A thread-safe in-memory sink that captures analytics events for debugging and testing.
 *
 * Uses an [ArrayDeque] as a ring buffer for O(1) eviction of the oldest event
 * when the max capacity is reached.
 */
class DebugEventSink(private val maxEvents: Int = 1000) {

    private val events = ArrayDeque<AnalyticsPayload>(maxEvents)
    private val lock = SynchronizedObject()

    /**
     * Records a single analytics payload into the debug sink.
     * Evicts the oldest event if the max capacity is reached.
     */
    fun record(payload: AnalyticsPayload) {
        synchronized(lock) {
            if (events.size >= maxEvents) {
                events.removeFirst()
            }
            events.addLast(payload)
        }
    }

    /**
     * Returns a snapshot of all recorded events as an immutable list.
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

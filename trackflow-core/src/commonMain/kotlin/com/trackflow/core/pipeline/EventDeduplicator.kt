package com.trackflow.core.pipeline

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.currentTimeMillis

/**
 * Prevents duplicate events from being dispatched within a configurable time window.
 *
 * An event is considered a duplicate if another event with the same name, type,
 * and properties was enqueued within the last [windowMs] milliseconds.
 *
 * Uses a [LinkedHashMap] with insertion-order iteration so expired entries
 * can be cleaned up efficiently from the head without scanning the entire map.
 *
 * @param windowMs The deduplication window in milliseconds. Default is 1000ms.
 */
internal class EventDeduplicator(
    private val windowMs: Long = 1_000L
) {
    private val seen = LinkedHashMap<String, Long>()
    private val lock = SynchronizedObject()

    /**
     * Checks whether the given [payload] is a duplicate.
     *
     * @return `true` if this event should be dispatched (not a duplicate),
     *   `false` if it should be dropped.
     */
    fun shouldDispatch(payload: AnalyticsPayload): Boolean {
        val fingerprint = computeFingerprint(payload)
        val now = currentTimeMillis()

        synchronized(lock) {
            // Evict expired entries from the head (oldest-first in insertion order)
            val iter = seen.entries.iterator()
            while (iter.hasNext()) {
                if (now - iter.next().value > windowMs) iter.remove() else break
            }

            val lastSeen = seen[fingerprint]
            if (lastSeen != null && (now - lastSeen) < windowMs) {
                return false
            }

            // Remove-then-put to maintain insertion order for future eviction
            seen.remove(fingerprint)
            seen[fingerprint] = now
            return true
        }
    }

    /** Clears all deduplication state. */
    fun clear() {
        synchronized(lock) {
            seen.clear()
        }
    }

    private fun computeFingerprint(payload: AnalyticsPayload): String {
        return "${payload.eventName}|${payload.type}|${payload.properties.hashCode()}"
    }
}

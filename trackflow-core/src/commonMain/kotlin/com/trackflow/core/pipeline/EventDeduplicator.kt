package com.trackflow.core.pipeline

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.currentTimeMillis

/**
 * Prevents duplicate events from being dispatched within a configurable time window.
 *
 * An event is considered a duplicate if another event with the same name and properties
 * was enqueued within the last [windowMs] milliseconds.
 *
 * This protects against:
 * - Double-tap sending the same event twice
 * - Retry-induced duplicates
 * - Rapid recomposition triggering duplicate screen views
 *
 * @param windowMs The deduplication window in milliseconds. Events with identical
 *   fingerprints within this window are dropped. Default is 1000ms (1 second).
 */
internal class EventDeduplicator(
    private val windowMs: Long = 1_000L
) {
    /** Guarded by [lock]. Maps fingerprint to last-seen timestamp. */
    private val seen = mutableMapOf<String, Long>()

    /** Synchronization lock for [seen] mutations. */
    private val lock = Any()

    /**
     * Checks whether the given [payload] is a duplicate.
     *
     * @param payload The event to check.
     * @return `true` if this event should be dispatched (not a duplicate),
     *   `false` if it should be dropped.
     */
    fun shouldDispatch(payload: AnalyticsPayload): Boolean {
        val fingerprint = computeFingerprint(payload)
        val now = currentTimeMillis()

        synchronized(lock) {
            // Clean up expired entries periodically
            if (seen.size > 500) {
                val iterator = seen.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > windowMs) {
                        iterator.remove()
                    }
                }
            }

            val lastSeen = seen[fingerprint]
            if (lastSeen != null && (now - lastSeen) < windowMs) {
                return false
            }

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

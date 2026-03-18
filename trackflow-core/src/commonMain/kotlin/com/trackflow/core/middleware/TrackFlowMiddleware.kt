package com.trackflow.core.middleware

import com.trackflow.core.payload.AnalyticsPayload

/**
 * Middleware that can intercept, transform, or filter events
 * before they reach the provider pipeline.
 *
 * Return a modified [AnalyticsPayload] to transform the event,
 * or return null to drop the event entirely.
 *
 * Example — PII scrubber:
 * ```
 * class PiiScrubber : TrackFlowMiddleware {
 *     private val piiKeys = setOf("email", "phone", "ssn")
 *     override fun process(payload: AnalyticsPayload): AnalyticsPayload {
 *         return payload.copy(
 *             properties = payload.properties.filterKeys { it !in piiKeys }
 *         )
 *     }
 * }
 * ```
 *
 * Example — event sampler:
 * ```
 * class Sampler(private val rate: Double = 0.1) : TrackFlowMiddleware {
 *     override fun process(payload: AnalyticsPayload): AnalyticsPayload? {
 *         return if (Math.random() < rate) payload else null
 *     }
 * }
 * ```
 */
fun interface TrackFlowMiddleware {
    /**
     * Processes a single analytics payload before it is dispatched to providers.
     *
     * Implementations may return:
     * - A modified copy of [payload] to transform the event (e.g., scrub PII, enrich data).
     * - The original [payload] unchanged to pass the event through.
     * - `null` to drop the event entirely, preventing it from reaching any provider.
     *
     * @param payload The analytics payload to process.
     * @return The (possibly modified) payload, or `null` to discard the event.
     */
    fun process(payload: AnalyticsPayload): AnalyticsPayload?
}

/**
 * Runs a payload through a chain of middleware in order.
 * Returns null if any middleware drops the event.
 *
 * @receiver The ordered list of [TrackFlowMiddleware] to apply sequentially.
 * @param payload The initial [AnalyticsPayload] to process.
 * @return The final transformed payload, or `null` if any middleware in the chain returned `null`.
 */
internal fun List<TrackFlowMiddleware>.applyAll(payload: AnalyticsPayload): AnalyticsPayload? {
    var current: AnalyticsPayload? = payload
    for (middleware in this) {
        current = current?.let { middleware.process(it) } ?: return null
    }
    return current
}

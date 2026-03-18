package com.trackflow.core.payload

import com.trackflow.core.event.ProviderExtras

/**
 * The type of analytics event, determining how providers handle it.
 *
 * - [ACTION] — User actions (button clicks, purchases, etc.). Routed to `track()` / `trackAction()`.
 * - [STATE] — Page/screen views. Routed to `trackState()` / `trackState()`.
 */
enum class EventType { ACTION, STATE }

/**
 * Internal representation of an analytics event as it flows through the pipeline.
 *
 * Created by [com.trackflow.core.TrackFlow] from a [com.trackflow.core.event.TrackFlowEvent],
 * enriched with device context, session ID, user ID, and super properties.
 * This is what gets batched, persisted offline, and delivered to providers.
 *
 * @property eventName The event name (e.g., "product_viewed").
 * @property properties Merged map of super properties + event properties.
 * @property providerExtras Provider-specific extras, keyed by provider key.
 * @property context Device and session context (platform, device_model, os_version, session_id, user_id).
 * @property timestamp Unix timestamp in milliseconds when the event was created.
 * @property type Whether this is an [EventType.ACTION] or [EventType.STATE] event.
 */
data class AnalyticsPayload(
    val eventName: String,
    val properties: Map<String, Any?>,
    val providerExtras: Map<String, ProviderExtras>,
    val context: Map<String, Any?>,
    val timestamp: Long,
    val type: EventType = EventType.ACTION
)

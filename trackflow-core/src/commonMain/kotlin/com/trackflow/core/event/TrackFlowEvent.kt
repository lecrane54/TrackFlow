package com.trackflow.core.event

/**
 * Marker interface for provider-specific extra data.
 *
 * Implement this interface to attach provider-specific metadata to events
 * that goes beyond the standard properties map. Providers can check for
 * their own [ProviderExtras] subtype via the [TrackFlowEvent.providerExtras] map.
 */
interface ProviderExtras

/**
 * Base interface for all TrackFlow analytics events.
 *
 * Implement this interface to create strongly-typed event classes,
 * or use the convenience `TrackFlow.track(name, ...)` overloads for ad-hoc events.
 *
 * @see com.trackflow.core.event.ProductViewed
 * @see com.trackflow.core.event.PurchaseCompleted
 * @see com.trackflow.core.event.ButtonClicked
 */
interface TrackFlowEvent {

    /** The event name identifier (e.g., "product_viewed", "button_clicked"). */
    val name: String

    /** Key-value map of event properties. Values may be String, Number, Boolean, or null. */
    val properties: Map<String, Any?>

    /**
     * Optional provider-specific extras, keyed by provider key (e.g., "firebase", "adobe-analytics").
     *
     * Defaults to an empty map. Override this to pass provider-specific data
     * that doesn't belong in the generic [properties] map.
     */
    val providerExtras: Map<String, ProviderExtras>
        get() = emptyMap()
}

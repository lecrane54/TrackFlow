package com.trackflow.core.provider

import android.content.Context
import com.trackflow.core.payload.AnalyticsPayload

/**
 * A normalized event that has been mapped for a specific provider.
 *
 * Created by a [ProviderEventMapper] from an [AnalyticsPayload].
 * This is what gets passed to [AnalyticsProvider.track] and [AnalyticsProvider.trackState].
 *
 * @property name The provider-specific event name (may differ from the original event name).
 * @property properties The provider-specific properties map (may be remapped, prefixed, or transformed).
 */
data class ProviderEvent(
    val name: String,
    val properties: Map<String, Any?>
)

/**
 * Transforms [AnalyticsPayload] instances into provider-specific [ProviderEvent] instances.
 *
 * Each [AnalyticsProvider] has a mapper that handles event name transformation,
 * property key remapping/prefixing, and any provider-specific data formatting.
 *
 * Return `null` from any map method to skip (filter out) the event for that provider.
 */
interface ProviderEventMapper {

    /**
     * Maps an action event payload to a provider-specific event.
     *
     * @param payload The enriched analytics payload from the pipeline.
     * @return A [ProviderEvent] for this provider, or null to skip this event.
     */
    fun mapTrack(payload: AnalyticsPayload): ProviderEvent?

    /**
     * Maps a state/page-view event payload to a provider-specific event.
     *
     * Defaults to [mapTrack] if not overridden. Override this when the provider
     * needs different mapping logic for state events (e.g., Adobe's context data
     * excludes `a.action` for state events).
     *
     * @param payload The enriched analytics payload from the pipeline.
     * @return A [ProviderEvent] for this provider, or null to skip this event.
     */
    fun mapState(payload: AnalyticsPayload): ProviderEvent? = mapTrack(payload)
}

/**
 * Contract for analytics provider implementations.
 *
 * Each provider wraps a specific analytics SDK (Firebase, Adobe, Mixpanel, etc.)
 * and handles initialization, event tracking, user identity, and cleanup.
 *
 * Providers are registered via [com.trackflow.core.TrackFlow.Builder.addProvider].
 * All methods are called by the SDK internally and should never throw exceptions
 * (wrap all SDK calls in try/catch and log errors via [com.trackflow.core.logging.TrackFlowLogger]).
 */
interface AnalyticsProvider {

    /** Unique identifier for this provider (e.g., "firebase", "adobe-analytics", "mixpanel"). */
    val key: String

    /** The event mapper that transforms [AnalyticsPayload] into [ProviderEvent] for this provider. */
    val mapper: ProviderEventMapper

    /**
     * Called once during [com.trackflow.core.TrackFlow.initialize] to set up the underlying SDK.
     *
     * @param context The Android application context.
     */
    fun initialize(context: Context)

    /**
     * Sends an action event to the underlying analytics SDK.
     *
     * @param event The mapped provider event containing the name and properties.
     */
    fun track(event: ProviderEvent)

    /**
     * Sends a state/page-view event to the underlying analytics SDK.
     *
     * Defaults to calling [track]. Override when the provider distinguishes between
     * actions and page views (e.g., Adobe `trackState` vs `trackAction`).
     *
     * @param event The mapped provider event.
     */
    fun trackState(event: ProviderEvent) { track(event) }

    /**
     * Sets the user identity in the underlying analytics SDK.
     *
     * Called when [com.trackflow.core.TrackFlow.identify] is invoked.
     * Default is a no-op. Override to call provider-specific identify methods
     * (e.g., `FirebaseAnalytics.setUserId`, `MixpanelAPI.identify`).
     *
     * @param userId The unique user identifier.
     * @param traits User profile traits (e.g., email, plan, name).
     */
    fun identify(userId: String, traits: Map<String, Any?>) {}

    /**
     * Resets the user identity in the underlying analytics SDK.
     *
     * Called when [com.trackflow.core.TrackFlow.resetIdentity] is invoked (e.g., on logout).
     * Default is a no-op. Override to call provider-specific reset methods.
     */
    fun reset() {}
}

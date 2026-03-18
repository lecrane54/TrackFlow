package com.trackflow.provider.amplitude

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * iOS Amplitude provider.
 *
 * Stub implementation — requires bridging to the Amplitude Swift SDK.
 *
 * TODO: Bridge to Amplitude(configuration: Configuration(apiKey:)).track(eventType:eventProperties:)
 * via Kotlin/Native interop.
 *
 * @param apiKey The Amplitude project API key.
 * @param keyPrefix Optional prefix for all property keys.
 * @param keyMap Optional key remapping.
 */
class AmplitudeIosProvider(
    private val apiKey: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "amplitude"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix)
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        // TODO: Bridge to Amplitude(configuration: Configuration(apiKey: apiKey))
        TrackFlowLogger.debug("Amplitude iOS provider initialized (stub)")
    }

    override fun track(event: ProviderEvent) {
        // TODO: Bridge to amplitude.track(eventType: event.name, eventProperties: event.properties)
        TrackFlowLogger.debug("Amplitude iOS track: ${event.name} (stub)")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        // TODO: Bridge to amplitude.setUserId(userId: userId)
        // TODO: Bridge to Identify().set(property:value:) for each trait
        TrackFlowLogger.debug("Amplitude iOS identify: $userId (stub)")
    }

    override fun reset() {
        // TODO: Bridge to amplitude.reset()
        TrackFlowLogger.debug("Amplitude iOS reset (stub)")
    }
}

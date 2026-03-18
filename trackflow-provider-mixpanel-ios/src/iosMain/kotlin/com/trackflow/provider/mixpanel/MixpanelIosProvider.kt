package com.trackflow.provider.mixpanel

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * iOS Mixpanel provider.
 *
 * Stub implementation — requires bridging to the Mixpanel Swift SDK.
 *
 * TODO: Bridge to Mixpanel.mainInstance().track(event:properties:) via Kotlin/Native interop.
 *
 * @param token The Mixpanel project token.
 * @param keyPrefix Optional prefix for all property keys.
 * @param keyMap Optional key remapping.
 */
class MixpanelIosProvider(
    private val token: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "mixpanel"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix) + mapOf("source" to "trackflow")
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        // TODO: Bridge to Mixpanel.initialize(token: token, trackAutomaticEvents: false)
        TrackFlowLogger.debug("Mixpanel iOS provider initialized (stub)")
    }

    override fun track(event: ProviderEvent) {
        // TODO: Bridge to Mixpanel.mainInstance().track(event: event.name, properties: event.properties)
        TrackFlowLogger.debug("Mixpanel iOS track: ${event.name} (stub)")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        // TODO: Bridge to Mixpanel.mainInstance().identify(distinctId: userId)
        // TODO: Bridge to Mixpanel.mainInstance().people.set(properties: traits)
        TrackFlowLogger.debug("Mixpanel iOS identify: $userId (stub)")
    }

    override fun reset() {
        // TODO: Bridge to Mixpanel.mainInstance().reset()
        TrackFlowLogger.debug("Mixpanel iOS reset (stub)")
    }
}

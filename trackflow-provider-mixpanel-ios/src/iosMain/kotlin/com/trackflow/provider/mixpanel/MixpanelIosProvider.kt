@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.trackflow.provider.mixpanel

import cocoapods.Mixpanel.Mixpanel
import com.trackflow.core.TrackFlowIos
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.remapKeys

/** Register a Mixpanel provider with TrackFlow from Swift without cross-framework type issues. */
fun registerMixpanelProvider(token: String, keyMap: Map<String, String>? = null) {
    TrackFlowIos.addProvider(MixpanelIosProvider(token, keyMap))
}

/**
 * iOS Mixpanel provider.
 *
 * Uses the Mixpanel iOS SDK (Obj-C) via CocoaPods cinterop bindings.
 *
 * @param token The Mixpanel project token.
 * @param keyMap Optional key remapping.
 */
class MixpanelIosProvider(
    private val token: String,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "mixpanel"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap) + mapOf("source" to "trackflow")
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        try {
            Mixpanel.sharedInstanceWithToken(token)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Mixpanel iOS initialize error: ${e.message}")
        }
        TrackFlowLogger.debug("Mixpanel iOS provider initialized")
    }

    override fun track(event: ProviderEvent) {
        try {
            Mixpanel.sharedInstance()?.track(event.name, properties = event.properties as Map<Any?, *>)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Mixpanel iOS track error: ${e.message}")
        }
        TrackFlowLogger.debug("Mixpanel iOS track: ${event.name}")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            Mixpanel.sharedInstance()?.identify(userId)
            if (traits.isNotEmpty()) {
                Mixpanel.sharedInstance()?.people?.set(traits as Map<Any?, *>)
            }
        } catch (e: Exception) {
            TrackFlowLogger.debug("Mixpanel iOS identify error: ${e.message}")
        }
        TrackFlowLogger.debug("Mixpanel iOS identify: $userId")
    }

    override fun reset() {
        try {
            Mixpanel.sharedInstance()?.reset()
        } catch (e: Exception) {
            TrackFlowLogger.debug("Mixpanel iOS reset error: ${e.message}")
        }
        TrackFlowLogger.debug("Mixpanel iOS reset")
    }
}

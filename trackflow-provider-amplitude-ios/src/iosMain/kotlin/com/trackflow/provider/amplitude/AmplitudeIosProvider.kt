@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.trackflow.provider.amplitude

import cocoapods.Amplitude.AMPIdentify
import cocoapods.Amplitude.Amplitude
import platform.darwin.NSObject
import com.trackflow.core.TrackFlowIos
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.remapKeys

/** Register an Amplitude provider with TrackFlow from Swift without cross-framework type issues. */
fun registerAmplitudeProvider(apiKey: String, keyMap: Map<String, String>? = null) {
    TrackFlowIos.addProvider(AmplitudeIosProvider(apiKey, keyMap))
}

/**
 * iOS Amplitude provider.
 *
 * Uses the Amplitude iOS SDK (classic Obj-C) via CocoaPods cinterop bindings.
 *
 * @param apiKey The Amplitude project API key.
 * @param keyMap Optional key remapping.
 */
class AmplitudeIosProvider(
    private val apiKey: String,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "amplitude"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap)
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        try {
            Amplitude.instance().initializeApiKey(apiKey)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Amplitude iOS initialize error: ${e.message}")
        }
        TrackFlowLogger.debug("Amplitude iOS provider initialized")
    }

    override fun track(event: ProviderEvent) {
        try {
            Amplitude.instance().logEvent(event.name, withEventProperties = event.properties as Map<Any?, *>)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Amplitude iOS track error: ${e.message}")
        }
        TrackFlowLogger.debug("Amplitude iOS track: ${event.name}")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            Amplitude.instance().setUserId(userId, startNewSession = false)
            if (traits.isNotEmpty()) {
                val identify = AMPIdentify()
                traits.forEach { (key, value) ->
                    identify.set(key, value = value as? NSObject)
                }
                Amplitude.instance().identify(identify)
            }
        } catch (e: Exception) {
            TrackFlowLogger.debug("Amplitude iOS identify error: ${e.message}")
        }
        TrackFlowLogger.debug("Amplitude iOS identify: $userId")
    }

    override fun reset() {
        try {
            Amplitude.instance().setUserId(null, startNewSession = false)
            Amplitude.instance().regenerateDeviceId()
        } catch (e: Exception) {
            TrackFlowLogger.debug("Amplitude iOS reset error: ${e.message}")
        }
        TrackFlowLogger.debug("Amplitude iOS reset")
    }
}

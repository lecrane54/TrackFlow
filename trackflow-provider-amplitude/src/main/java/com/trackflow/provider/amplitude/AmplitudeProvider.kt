package com.trackflow.provider.amplitude

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.events.Identify
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * Amplitude analytics provider.
 *
 * Uses the Amplitude Kotlin SDK to send events, identify users,
 * and manage user properties.
 *
 * @param apiKey Your Amplitude project API key.
 * @param keyPrefix Optional prefix for all property keys.
 * @param keyMap Optional map to remap property keys (e.g., "product_id" to "Product ID").
 */
class AmplitudeProvider(
    private val apiKey: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "amplitude"
    private var amplitude: Amplitude? = null

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix)
            )
        }
    }

    override fun initialize(context: Context) {
        try {
            amplitude = Amplitude(
                Configuration(
                    apiKey = apiKey,
                    context = context.applicationContext
                )
            )
            TrackFlowLogger.debug("Amplitude provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude init failed", e)
        }
    }

    override fun track(event: ProviderEvent) {
        try {
            amplitude?.track(event.name, event.properties)
                ?: TrackFlowLogger.warn("Amplitude not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude track failed for ${event.name}", e)
        }
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            amplitude?.setUserId(userId)
            if (traits.isNotEmpty()) {
                val identify = Identify()
                traits.forEach { (key, value) ->
                    when (value) {
                        is String -> identify.set(key, value)
                        is Int -> identify.set(key, value)
                        is Long -> identify.set(key, value)
                        is Double -> identify.set(key, value)
                        is Float -> identify.set(key, value)
                        is Boolean -> identify.set(key, value)
                        null -> identify.unset(key)
                        else -> identify.set(key, value.toString())
                    }
                }
                amplitude?.identify(identify)
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude identify failed", e)
        }
    }

    override fun reset() {
        try {
            amplitude?.reset()
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude reset failed", e)
        }
    }
}

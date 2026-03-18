package com.trackflow.provider.mixpanel

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys
import org.json.JSONObject

class MixpanelProvider(
    private val token: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "mixpanel"
    private var mixpanel: MixpanelAPI? = null

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix) + mapOf("source" to "trackflow")
            )
        }
    }

    override fun initialize(context: Context) {
        try {
            mixpanel = MixpanelAPI.getInstance(context, token, false)
            TrackFlowLogger.debug("Mixpanel provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel init failed", e)
        }
    }

    override fun track(event: ProviderEvent) {
        try {
            val jsonProps = JSONObject().apply {
                event.properties.forEach { (key, value) ->
                    put(key, value ?: JSONObject.NULL)
                }
            }
            mixpanel?.track(event.name, jsonProps)
                ?: TrackFlowLogger.warn("Mixpanel not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel track failed for ${event.name}", e)
        }
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            mixpanel?.identify(userId)
            if (traits.isNotEmpty()) {
                val people = mixpanel?.people
                traits.forEach { (key, value) ->
                    people?.set(key, value)
                }
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel identify failed", e)
        }
    }

    override fun reset() {
        try {
            mixpanel?.reset()
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel reset failed", e)
        }
    }
}

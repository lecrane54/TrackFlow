package com.trackflow.provider.adobe.edge

import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Signal
import com.adobe.marketing.mobile.UserProfile
import com.adobe.marketing.mobile.edge.identity.Identity
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * Adobe Experience Platform Edge provider for Customer Journey Analytics (CJA).
 *
 * This provider sends events as XDM Experience Events via the Edge Network.
 * Use this for modern Adobe implementations where data flows through
 * XDM schemas and datastreams configured in Adobe Experience Platform.
 *
 * @param appId The Adobe Experience Platform environment file ID.
 * @param datasetId Optional dataset ID for routing events to a specific dataset.
 */
class AdobeEdgeProvider(
    private val appId: String,
    private val datasetId: String? = null,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "adobe-edge"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(payload.eventName, payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix))
        }
    }

    override fun initialize(context: Context) {
        try {
            val application = context.applicationContext as Application
            MobileCore.setApplication(application)
            MobileCore.registerExtensions(
                listOf(
                    Edge.EXTENSION,
                    Identity.EXTENSION,
                    Lifecycle.EXTENSION,
                    Signal.EXTENSION,
                    UserProfile.EXTENSION
                )
            ) {
                MobileCore.configureWithAppID(appId)
            }
            TrackFlowLogger.debug("Adobe Edge provider initialized with appId: $appId")
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Edge init failed", e)
        }
    }

    override fun track(event: ProviderEvent) {
        sendEdgeEvent(event, "analytics.action")
    }

    override fun trackState(event: ProviderEvent) {
        sendEdgeEvent(event, "web.webpagedetails.pageViews")
    }

    private fun sendEdgeEvent(event: ProviderEvent, eventType: String) {
        try {
            val xdmData = mutableMapOf<String, Any>(
                "eventType" to eventType,
                "name" to event.name
            )
            event.properties.forEach { (key, value) ->
                if (value != null) {
                    xdmData[key] = value
                }
            }

            val builder = ExperienceEvent.Builder()
                .setXdmSchema(xdmData)

            if (datasetId != null) {
                builder.setDatastreamIdOverride(datasetId)
            }

            Edge.sendEvent(builder.build(), null)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Edge send failed for ${event.name}", e)
        }
    }
}

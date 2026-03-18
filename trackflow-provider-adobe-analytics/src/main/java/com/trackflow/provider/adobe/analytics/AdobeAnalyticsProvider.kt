package com.trackflow.provider.adobe.analytics

import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.Analytics
import com.adobe.marketing.mobile.Identity
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Signal
import com.adobe.marketing.mobile.UserProfile
import com.adobe.marketing.mobile.edge.bridge.EdgeBridge
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * Adobe Analytics provider using the legacy Analytics extension with Edge Bridge.
 *
 * This provider uses [MobileCore.trackAction] and [MobileCore.trackState] under the hood,
 * with the Edge Bridge extension forwarding hits to the Edge Network for backward compatibility.
 *
 * Use this provider when migrating from legacy Adobe Analytics to Edge Network,
 * or when your Adobe report suites are configured with processing rules that
 * expect traditional context data keys.
 *
 * @param appId The Adobe Experience Platform environment file ID.
 */
class AdobeAnalyticsProvider(
    private val appId: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "adobe-analytics"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (key, value) ->
                contextData[key] = value?.toString()
            }
            contextData["a.action"] = payload.eventName
            return ProviderEvent(payload.eventName, contextData)
        }

        override fun mapState(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (key, value) ->
                contextData[key] = value?.toString()
            }
            return ProviderEvent(payload.eventName, contextData)
        }
    }

    override fun initialize(context: Context) {
        try {
            val application = context.applicationContext as Application
            MobileCore.setApplication(application)
            MobileCore.setLogLevel(LoggingMode.VERBOSE)
            MobileCore.registerExtensions(
                listOf(
                    Analytics.EXTENSION,
                    Identity.EXTENSION,
                    Lifecycle.EXTENSION,
                    Signal.EXTENSION,
                    UserProfile.EXTENSION,
                    EdgeBridge.EXTENSION
                )
            ) {
                MobileCore.configureWithAppID(appId)
            }
            TrackFlowLogger.debug("Adobe Analytics provider initialized with appId: $appId")
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics init failed", e)
        }
    }

    override fun track(event: ProviderEvent) {
        try {
            val contextData = event.properties
                .filterValues { it != null }
                .mapValues { it.value.toString() }

            MobileCore.trackAction(event.name, contextData)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics trackAction failed for ${event.name}", e)
        }
    }

    override fun trackState(event: ProviderEvent) {
        try {
            val contextData = event.properties
                .filterValues { it != null }
                .mapValues { it.value.toString() }

            MobileCore.trackState(event.name, contextData)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics trackState failed for ${event.name}", e)
        }
    }
}

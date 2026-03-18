package com.trackflow.provider.adobe.analytics

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * iOS Adobe Analytics provider (Legacy + Edge Bridge).
 *
 * Stub implementation — requires bridging to the AEP Swift SDK:
 * - AEPCore (MobileCore.track)
 * - AEPAnalytics
 * - AEPEdgeBridge
 * - AEPIdentity, AEPLifecycle, AEPSignal, AEPUserProfile
 *
 * TODO: Bridge to MobileCore.track(action:data:) and MobileCore.track(state:data:)
 * via Kotlin/Native interop or a Swift wrapper exposed as an ObjC framework.
 *
 * @param appId The Adobe Experience Platform environment file ID.
 * @param keyPrefix Optional prefix for all context data keys.
 * @param keyMap Optional key remapping (e.g., "product_id" to "eVar21").
 */
class AdobeAnalyticsIosProvider(
    private val appId: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "adobe-analytics"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (k, v) ->
                contextData[k] = v?.toString()
            }
            contextData["a.action"] = payload.eventName
            return ProviderEvent(payload.eventName, contextData)
        }

        override fun mapState(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (k, v) ->
                contextData[k] = v?.toString()
            }
            return ProviderEvent(payload.eventName, contextData)
        }
    }

    override fun initialize(context: PlatformContext) {
        // TODO: Bridge to:
        //   MobileCore.registerExtensions([Analytics.self, Identity.self, Lifecycle.self,
        //       Signal.self, UserProfile.self, EdgeBridge.self])
        //   MobileCore.configureWith(appId: appId)
        TrackFlowLogger.debug("Adobe Analytics iOS provider initialized with appId: $appId (stub)")
    }

    override fun track(event: ProviderEvent) {
        // TODO: Bridge to MobileCore.track(action: event.name, data: event.properties)
        TrackFlowLogger.debug("Adobe Analytics iOS trackAction: ${event.name} (stub)")
    }

    override fun trackState(event: ProviderEvent) {
        // TODO: Bridge to MobileCore.track(state: event.name, data: event.properties)
        TrackFlowLogger.debug("Adobe Analytics iOS trackState: ${event.name} (stub)")
    }
}

package com.trackflow.provider.adobe.edge

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * iOS Adobe Experience Platform Edge provider for CJA / XDM.
 *
 * Stub implementation — requires bridging to the AEP Swift SDK:
 * - AEPCore
 * - AEPEdge (Edge.sendEvent)
 * - AEPEdgeIdentity, AEPLifecycle, AEPSignal, AEPUserProfile
 *
 * TODO: Bridge to Edge.sendEvent(experienceEvent:) via Kotlin/Native interop.
 *
 * @param appId The Adobe Experience Platform environment file ID.
 * @param datasetId Optional dataset ID for routing events.
 * @param keyPrefix Optional prefix for all property keys.
 * @param keyMap Optional key remapping.
 */
class AdobeEdgeIosProvider(
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

    override fun initialize(context: PlatformContext) {
        // TODO: Bridge to:
        //   MobileCore.registerExtensions([Edge.self, AEPEdgeIdentity.Identity.self,
        //       Lifecycle.self, Signal.self, UserProfile.self])
        //   MobileCore.configureWith(appId: appId)
        TrackFlowLogger.debug("Adobe Edge iOS provider initialized with appId: $appId (stub)")
    }

    override fun track(event: ProviderEvent) {
        // TODO: Bridge to Edge.sendEvent with eventType "analytics.action"
        TrackFlowLogger.debug("Adobe Edge iOS track: ${event.name} (stub)")
    }

    override fun trackState(event: ProviderEvent) {
        // TODO: Bridge to Edge.sendEvent with eventType "web.webpagedetails.pageViews"
        TrackFlowLogger.debug("Adobe Edge iOS trackState: ${event.name} (stub)")
    }
}

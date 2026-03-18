package com.trackflow.provider.firebase

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * iOS Firebase Analytics provider.
 *
 * Stub implementation — requires bridging to the Firebase iOS SDK
 * via Swift interop or a CocoaPods dependency.
 *
 * TODO: Bridge to FIRAnalytics.logEvent(withName:parameters:) via Kotlin/Native interop.
 *
 * @param keyPrefix Optional prefix for all property keys.
 * @param keyMap Optional key remapping (e.g., "product_id" to "item_id").
 */
class FirebaseIosProvider(
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key = "firebase"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            val sanitizedName = payload.eventName
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .take(40)
            return ProviderEvent(
                name = sanitizedName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix)
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        // TODO: Firebase iOS SDK initializes via GoogleService-Info.plist automatically.
        // FirebaseApp.configure() should be called in the Swift AppDelegate.
        TrackFlowLogger.debug("Firebase iOS provider initialized (stub)")
    }

    override fun track(event: ProviderEvent) {
        // TODO: Bridge to FIRAnalytics.logEvent(withName: event.name, parameters: event.properties)
        TrackFlowLogger.debug("Firebase iOS track: ${event.name} (stub)")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        // TODO: Bridge to FIRAnalytics.setUserID(userId)
        // TODO: Bridge to FIRAnalytics.setUserProperty(value, forName: key) for each trait
        TrackFlowLogger.debug("Firebase iOS identify: $userId (stub)")
    }

    override fun reset() {
        // TODO: Bridge to FIRAnalytics.setUserID(nil)
        TrackFlowLogger.debug("Firebase iOS reset (stub)")
    }
}

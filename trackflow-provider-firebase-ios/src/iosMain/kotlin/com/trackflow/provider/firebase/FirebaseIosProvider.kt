@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.trackflow.provider.firebase

import cocoapods.FirebaseAnalytics.FIRAnalytics
import com.trackflow.core.TrackFlowIos
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.remapKeys

/** Register a Firebase provider with TrackFlow from Swift without cross-framework type issues. */
fun registerFirebaseProvider(keyMap: Map<String, String>? = null) {
    TrackFlowIos.addProvider(FirebaseIosProvider(keyMap))
}

/**
 * iOS Firebase Analytics provider.
 *
 * Uses the Firebase iOS SDK via CocoaPods cinterop bindings.
 *
 * @param keyMap Optional key remapping (e.g., "product_id" to "item_id").
 */
class FirebaseIosProvider(
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
                properties = payload.properties.remapKeys(keyMap)
            )
        }
    }

    override fun initialize(context: PlatformContext) {
        // Firebase iOS SDK initializes via GoogleService-Info.plist automatically.
        // FirebaseApp.configure() should be called in the Swift AppDelegate.
        TrackFlowLogger.debug("Firebase iOS provider initialized")
    }

    override fun track(event: ProviderEvent) {
        try {
            FIRAnalytics.logEventWithName(event.name, parameters = event.properties as Map<Any?, *>)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Firebase iOS track error: ${e.message}")
        }
        TrackFlowLogger.debug("Firebase iOS track: ${event.name}")
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            FIRAnalytics.setUserID(userId)
            traits.forEach { (key, value) ->
                FIRAnalytics.setUserPropertyString(value?.toString(), forName = key)
            }
        } catch (e: Exception) {
            TrackFlowLogger.debug("Firebase iOS identify error: ${e.message}")
        }
        TrackFlowLogger.debug("Firebase iOS identify: $userId")
    }

    override fun reset() {
        try {
            FIRAnalytics.setUserID(null)
        } catch (e: Exception) {
            TrackFlowLogger.debug("Firebase iOS reset error: ${e.message}")
        }
        TrackFlowLogger.debug("Firebase iOS reset")
    }
}

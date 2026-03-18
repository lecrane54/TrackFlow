package com.trackflow.provider.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

class FirebaseProvider(
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    override val key: String = "firebase"
    private var firebaseAnalytics: FirebaseAnalytics? = null

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

    override fun initialize(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            TrackFlowLogger.debug("Firebase provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase init failed", e)
        }
    }

    override fun track(event: ProviderEvent) {
        try {
            val bundle = Bundle().apply {
                event.properties.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        null -> { /* skip nulls */ }
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(event.name, bundle)
                ?: TrackFlowLogger.warn("Firebase not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase track failed for ${event.name}", e)
        }
    }

    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            firebaseAnalytics?.setUserId(userId)
            traits.forEach { (key, value) ->
                firebaseAnalytics?.setUserProperty(key, value?.toString())
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase identify failed", e)
        }
    }

    override fun reset() {
        try {
            firebaseAnalytics?.setUserId(null)
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase reset failed", e)
        }
    }
}

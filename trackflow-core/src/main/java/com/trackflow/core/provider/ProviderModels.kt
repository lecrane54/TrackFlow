
package com.trackflow.core.provider

import android.content.Context
import com.trackflow.core.payload.AnalyticsPayload

data class ProviderEvent(
    val name: String,
    val properties: Map<String, Any?>
)

interface ProviderEventMapper {
    fun mapTrack(payload: AnalyticsPayload): ProviderEvent?
    fun mapState(payload: AnalyticsPayload): ProviderEvent? = mapTrack(payload)
}

interface AnalyticsProvider {
    val key: String
    val mapper: ProviderEventMapper
    fun initialize(context: Context)
    fun track(event: ProviderEvent)
    fun trackState(event: ProviderEvent) { track(event) }
    fun identify(userId: String, traits: Map<String, Any?>) {}
    fun reset() {}
}

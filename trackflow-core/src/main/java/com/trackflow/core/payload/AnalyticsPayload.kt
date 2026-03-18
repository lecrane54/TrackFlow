
package com.trackflow.core.payload

import com.trackflow.core.event.ProviderExtras

enum class EventType { ACTION, STATE }

data class AnalyticsPayload(
    val eventName: String,
    val properties: Map<String, Any?>,
    val providerExtras: Map<String, ProviderExtras>,
    val context: Map<String, Any?>,
    val timestamp: Long,
    val type: EventType = EventType.ACTION
)

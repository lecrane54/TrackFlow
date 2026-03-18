
package com.trackflow.core.event

interface ProviderExtras

interface TrackFlowEvent {
    val name: String
    val properties: Map<String, Any?>
    val providerExtras: Map<String, ProviderExtras>
        get() = emptyMap()
}

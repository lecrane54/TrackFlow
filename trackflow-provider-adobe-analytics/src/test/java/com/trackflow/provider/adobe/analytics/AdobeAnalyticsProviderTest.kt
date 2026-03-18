package com.trackflow.provider.adobe.analytics

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.ProviderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdobeAnalyticsProviderTest {

    private val provider = AdobeAnalyticsProvider("test-app-id")

    private fun createPayload(
        name: String,
        properties: Map<String, Any?> = emptyMap()
    ): AnalyticsPayload {
        return AnalyticsPayload(
            eventName = name,
            properties = properties,
            providerExtras = emptyMap(),
            context = emptyMap(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `key is adobe-analytics`() {
        assertEquals("adobe-analytics", provider.key)
    }

    @Test
    fun `mapper adds action name to context data`() {
        val payload = createPayload("purchase_completed", mapOf("item" to "laptop"))
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals("purchase_completed", result!!.name)
        assertEquals("purchase_completed", result.properties["a.action"])
    }

    @Test
    fun `mapper flattens properties to strings`() {
        val payload = createPayload("test", mapOf(
            "count" to 42,
            "price" to 9.99,
            "active" to true
        ))
        val result = provider.mapper.mapTrack(payload)

        assertEquals("42", result!!.properties["count"])
        assertEquals("9.99", result.properties["price"])
        assertEquals("true", result.properties["active"])
    }

    @Test
    fun `mapper handles empty properties`() {
        val payload = createPayload("test", emptyMap())
        val result = provider.mapper.mapTrack(payload)

        assertEquals(1, result!!.properties.size) // only a.action
        assertEquals("test", result.properties["a.action"])
    }

    @Test
    fun `mapper preserves event name`() {
        val payload = createPayload("screen_view")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("screen_view", result!!.name)
    }

    @Test
    fun `track does not throw when not initialized`() {
        provider.track(ProviderEvent("test", mapOf("key" to "value")))
    }
}

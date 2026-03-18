package com.trackflow.provider.adobe.edge

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.ProviderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdobeEdgeProviderTest {

    private val provider = AdobeEdgeProvider("test-app-id")

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
    fun `key is adobe-edge`() {
        assertEquals("adobe-edge", provider.key)
    }

    @Test
    fun `mapper preserves event name`() {
        val payload = createPayload("commerce.productViews")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("commerce.productViews", result!!.name)
    }

    @Test
    fun `mapper passes through properties unchanged`() {
        val props = mapOf(
            "product_id" to "sku_123",
            "price" to 29.99,
            "quantity" to 1
        )
        val payload = createPayload("purchase", props)
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals("sku_123", result!!.properties["product_id"])
        assertEquals(29.99, result.properties["price"])
        assertEquals(1, result.properties["quantity"])
    }

    @Test
    fun `mapper handles empty properties`() {
        val payload = createPayload("test", emptyMap())
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals(emptyMap<String, Any?>(), result!!.properties)
    }

    @Test
    fun `track does not throw when not initialized`() {
        provider.track(ProviderEvent("test", mapOf("key" to "value")))
    }

    @Test
    fun `constructor with dataset id`() {
        val customProvider = AdobeEdgeProvider("app-id", "dataset-123")
        assertEquals("adobe-edge", customProvider.key)
    }

    @Test
    fun `constructor without dataset id`() {
        val customProvider = AdobeEdgeProvider("app-id")
        assertEquals("adobe-edge", customProvider.key)
    }
}

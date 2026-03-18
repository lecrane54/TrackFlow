package com.trackflow.provider.amplitude

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.ProviderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AmplitudeProviderTest {

    private val provider = AmplitudeProvider("test-api-key")

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
    fun `key is amplitude`() {
        assertEquals("amplitude", provider.key)
    }

    @Test
    fun `mapper preserves event name`() {
        val payload = createPayload("button_clicked")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("button_clicked", result!!.name)
    }

    @Test
    fun `mapper passes through properties`() {
        val props = mapOf("product_id" to "sku_123", "price" to 29.99)
        val payload = createPayload("purchase", props)
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals("sku_123", result!!.properties["product_id"])
        assertEquals(29.99, result.properties["price"])
    }

    @Test
    fun `mapper applies keyMap`() {
        val provider = AmplitudeProvider(
            apiKey = "test",
            keyMap = mapOf("product_id" to "Product ID")
        )
        val payload = createPayload("test", mapOf("product_id" to "sku_123", "color" to "red"))
        val result = provider.mapper.mapTrack(payload)

        assertEquals("sku_123", result!!.properties["Product ID"])
        assertEquals("red", result.properties["color"])
    }

    @Test
    fun `mapper applies keyPrefix`() {
        val provider = AmplitudeProvider(apiKey = "test", keyPrefix = "amp.")
        val payload = createPayload("test", mapOf("key" to "value"))
        val result = provider.mapper.mapTrack(payload)

        assertEquals("value", result!!.properties["amp.key"])
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
    fun `reset does not throw when not initialized`() {
        provider.reset()
    }
}

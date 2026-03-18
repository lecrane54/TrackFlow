package com.trackflow.provider.firebase

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.ProviderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FirebaseProviderTest {

    private val provider = FirebaseProvider()

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
    fun `key is firebase`() {
        assertEquals("firebase", provider.key)
    }

    @Test
    fun `mapper returns event with same properties`() {
        val payload = createPayload("test_event", mapOf("key" to "value"))
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals(mapOf("key" to "value"), result!!.properties)
    }

    @Test
    fun `mapper sanitizes event names with spaces`() {
        val payload = createPayload("my event name")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("my_event_name", result!!.name)
    }

    @Test
    fun `mapper sanitizes event names with hyphens`() {
        val payload = createPayload("my-event-name")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("my_event_name", result!!.name)
    }

    @Test
    fun `mapper sanitizes event names with special characters`() {
        val payload = createPayload("event.name@special!")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("event_name_special_", result!!.name)
    }

    @Test
    fun `mapper truncates event names longer than 40 chars`() {
        val longName = "a".repeat(50)
        val payload = createPayload(longName)
        val result = provider.mapper.mapTrack(payload)

        assertEquals(40, result!!.name.length)
    }

    @Test
    fun `mapper preserves short valid event names`() {
        val payload = createPayload("valid_event_123")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("valid_event_123", result!!.name)
    }

    @Test
    fun `track does not throw when firebase not initialized`() {
        // FirebaseAnalytics is null since initialize() was not called
        // Should not throw
        provider.track(ProviderEvent("test", mapOf("key" to "value")))
    }

    @Test
    fun `mapper handles empty properties`() {
        val payload = createPayload("test", emptyMap())
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals(emptyMap<String, Any?>(), result!!.properties)
    }
}

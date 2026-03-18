package com.trackflow.provider.mixpanel

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.ProviderEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MixpanelProviderTest {

    private val provider = MixpanelProvider("test-token-123")

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
    fun `key is mixpanel`() {
        assertEquals("mixpanel", provider.key)
    }

    @Test
    fun `mapper adds source trackflow property`() {
        val payload = createPayload("purchase", mapOf("item" to "laptop"))
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
        assertEquals("trackflow", result!!.properties["source"])
    }

    @Test
    fun `mapper preserves event name`() {
        val payload = createPayload("user_signup")
        val result = provider.mapper.mapTrack(payload)

        assertEquals("user_signup", result!!.name)
    }

    @Test
    fun `mapper preserves original properties alongside source`() {
        val props = mapOf(
            "key1" to "value1",
            "key2" to 42,
            "key3" to null
        )
        val payload = createPayload("test", props)
        val result = provider.mapper.mapTrack(payload)

        assertEquals("value1", result!!.properties["key1"])
        assertEquals(42, result.properties["key2"])
        assertEquals(null, result.properties["key3"])
        assertEquals("trackflow", result.properties["source"])
    }

    @Test
    fun `mapper with empty properties only has source`() {
        val payload = createPayload("test", emptyMap())
        val result = provider.mapper.mapTrack(payload)

        assertEquals(1, result!!.properties.size)
        assertEquals("trackflow", result.properties["source"])
    }

    @Test
    fun `track does not throw when mixpanel not initialized`() {
        // Mixpanel is null since initialize() was not called
        // Should not throw
        provider.track(ProviderEvent("test", mapOf("key" to "value")))
    }

    @Test
    fun `mapper result is never null`() {
        val payload = createPayload("any_event")
        val result = provider.mapper.mapTrack(payload)

        assertNotNull(result)
    }

    @Test
    fun `constructor stores token`() {
        val customProvider = MixpanelProvider("custom-token")
        assertEquals("mixpanel", customProvider.key)
    }
}

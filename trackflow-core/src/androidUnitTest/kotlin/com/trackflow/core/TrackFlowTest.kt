package com.trackflow.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.trackflow.core.event.TrackFlowEvent
import com.trackflow.core.logging.LogLevel
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.payload.AnalyticsPayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TrackFlowTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "trackflow_test_${System.nanoTime()}")
        tempDir.mkdirs()

        context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tempDir
        every { context.packageName } returns "com.test.app"
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk(relaxed = true)
    }

    private fun createTestProvider(
        providerKey: String = "test",
        onTrack: (ProviderEvent) -> Unit = {}
    ): AnalyticsProvider {
        return object : AnalyticsProvider {
            override val key = providerKey
            override val mapper = object : ProviderEventMapper {
                override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
                    return ProviderEvent(payload.eventName, payload.properties)
                }
            }

            override fun initialize(context: Context) {}
            override fun track(event: ProviderEvent) = onTrack(event)
        }
    }

    private fun createTestEvent(
        eventName: String = "test_event",
        props: Map<String, Any?> = mapOf("key" to "value")
    ): TrackFlowEvent {
        return object : TrackFlowEvent {
            override val name = eventName
            override val properties = props
        }
    }

    @Test
    fun `initialize sets up providers`() {
        val provider = mockk<AnalyticsProvider>(relaxed = true)
        every { provider.key } returns "mock"
        every { provider.mapper } returns mockk(relaxed = true)

        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(provider)
                .logLevel(LogLevel.NONE)
                .build()
        )

        verify { provider.initialize(context) }
        TrackFlow.shutdown()
    }

    @Test
    fun `track records event to debug sink`() {
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(createTestProvider())
                .logLevel(LogLevel.NONE)
                .build()
        )

        TrackFlow.track(createTestEvent("debug_test"))

        val debugEvents = TrackFlow.debugEvents()
        assertTrue(debugEvents.isNotEmpty())
        assertEquals("debug_test", debugEvents.last().eventName)
        TrackFlow.shutdown()
    }

    @Test
    fun `track creates payload with context`() {
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(createTestProvider())
                .logLevel(LogLevel.NONE)
                .build()
        )

        TrackFlow.track(createTestEvent("ctx_test"))

        val payload = TrackFlow.debugEvents().last()
        assertEquals("ctx_test", payload.eventName)
        assertEquals("android", payload.context["platform"])
        assertTrue(payload.context.containsKey("session_id"))
        assertTrue(payload.timestamp > 0)
        TrackFlow.shutdown()
    }

    @Test
    fun `builder supports fluent API`() {
        val builder = TrackFlow.Builder(context)
            .addProvider(createTestProvider())
            .batchSize(50)
            .flushInterval(10_000L)
            .logLevel(LogLevel.DEBUG)
            .build()

        assertEquals(50, builder.batchSize)
        assertEquals(10_000L, builder.flushIntervalMs)
        assertEquals(LogLevel.DEBUG, builder.logLevel)
    }

    @Test
    fun `multiple providers are all initialized`() {
        val provider1 = mockk<AnalyticsProvider>(relaxed = true)
        every { provider1.key } returns "p1"
        every { provider1.mapper } returns mockk(relaxed = true)

        val provider2 = mockk<AnalyticsProvider>(relaxed = true)
        every { provider2.key } returns "p2"
        every { provider2.mapper } returns mockk(relaxed = true)

        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(provider1)
                .addProvider(provider2)
                .logLevel(LogLevel.NONE)
                .build()
        )

        verify { provider1.initialize(context) }
        verify { provider2.initialize(context) }
        TrackFlow.shutdown()
    }

    @Test
    fun `provider initialization failure does not crash`() {
        val failingProvider = object : AnalyticsProvider {
            override val key = "failing"
            override val mapper = object : ProviderEventMapper {
                override fun mapTrack(payload: AnalyticsPayload): ProviderEvent? = null
            }
            override fun initialize(context: Context) {
                throw RuntimeException("Init failed!")
            }
            override fun track(event: ProviderEvent) {}
        }

        // Should not throw
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(failingProvider)
                .logLevel(LogLevel.NONE)
                .build()
        )
        TrackFlow.shutdown()
    }

    @Test
    fun `shutdown can be called safely`() {
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(createTestProvider())
                .logLevel(LogLevel.NONE)
                .build()
        )
        // Should not throw
        TrackFlow.shutdown()
    }
}

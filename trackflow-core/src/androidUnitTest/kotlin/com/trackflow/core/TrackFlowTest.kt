package com.trackflow.core

import android.content.Context
import com.trackflow.core.event.TrackFlowEvent
import com.trackflow.core.logging.LogLevel
import com.trackflow.core.platform.PlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.payload.AnalyticsPayload
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrackFlowTest {

    private lateinit var context: PlatformContext
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
        // Default to debug build — tests that need release behavior override this
        val debugAppInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_DEBUGGABLE }
        every { context.applicationInfo } returns debugAppInfo
    }

    /** Creates a mock context that reports as a release (non-debuggable) build. */
    private fun createReleaseContext(): PlatformContext {
        val releaseContext = mockk<Context>(relaxed = true)
        every { releaseContext.filesDir } returns tempDir
        every { releaseContext.packageName } returns "com.test.app"
        every { releaseContext.applicationContext } returns releaseContext
        every { releaseContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk(relaxed = true)
        val releaseAppInfo = ApplicationInfo().apply { flags = 0 }
        every { releaseContext.applicationInfo } returns releaseAppInfo
        return releaseContext
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

            override fun initialize(context: PlatformContext) {}
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
    fun `track creates payload with session`() {
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(createTestProvider())
                .logLevel(LogLevel.NONE)
                .build()
        )

        TrackFlow.track(createTestEvent("ctx_test"))

        val payload = TrackFlow.debugEvents().last()
        assertEquals("ctx_test", payload.eventName)
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
            override fun initialize(context: PlatformContext) {
                throw RuntimeException("Init failed!")
            }
            override fun track(event: ProviderEvent) {}
        }

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
        TrackFlow.shutdown()
    }

    @Test
    fun `free tier release build blocks paid providers`() {
        val releaseCtx = createReleaseContext()

        val freeProvider = mockk<AnalyticsProvider>(relaxed = true)
        every { freeProvider.key } returns "firebase"
        every { freeProvider.mapper } returns mockk(relaxed = true)

        val paidProvider = mockk<AnalyticsProvider>(relaxed = true)
        every { paidProvider.key } returns "adobe-analytics"
        every { paidProvider.mapper } returns mockk(relaxed = true)

        TrackFlow.initialize(
            TrackFlow.Builder(releaseCtx)
                .addProvider(freeProvider)
                .addProvider(paidProvider)
                .logLevel(LogLevel.NONE)
                .build()
        )

        // Free provider should be initialized, paid provider should be blocked
        verify { freeProvider.initialize(releaseCtx) }
        verify(exactly = 0) { paidProvider.initialize(any()) }
        TrackFlow.shutdown()
    }

    @Test
    fun `free tier allows multiple open-source providers`() {
        val releaseCtx = createReleaseContext()

        val firebase = mockk<AnalyticsProvider>(relaxed = true)
        every { firebase.key } returns "firebase"
        every { firebase.mapper } returns mockk(relaxed = true)

        val amplitude = mockk<AnalyticsProvider>(relaxed = true)
        every { amplitude.key } returns "amplitude"
        every { amplitude.mapper } returns mockk(relaxed = true)

        val mixpanel = mockk<AnalyticsProvider>(relaxed = true)
        every { mixpanel.key } returns "mixpanel"
        every { mixpanel.mapper } returns mockk(relaxed = true)

        TrackFlow.initialize(
            TrackFlow.Builder(releaseCtx)
                .addProvider(firebase)
                .addProvider(amplitude)
                .addProvider(mixpanel)
                .logLevel(LogLevel.NONE)
                .build()
        )

        // All three open-source providers should be initialized
        verify { firebase.initialize(releaseCtx) }
        verify { amplitude.initialize(releaseCtx) }
        verify { mixpanel.initialize(releaseCtx) }
        TrackFlow.shutdown()
    }

    @Test
    fun `free tier debug build allows paid providers with warning`() {
        val paidProvider = mockk<AnalyticsProvider>(relaxed = true)
        every { paidProvider.key } returns "adobe-edge"
        every { paidProvider.mapper } returns mockk(relaxed = true)

        // context is debug by default (set in @Before)
        TrackFlow.initialize(
            TrackFlow.Builder(context)
                .addProvider(paidProvider)
                .logLevel(LogLevel.NONE)
                .build()
        )

        // Paid provider should still be initialized in debug builds
        verify { paidProvider.initialize(context) }
        TrackFlow.shutdown()
    }

    @Test
    fun `pro license allows paid providers in release build`() {
        val releaseCtx = createReleaseContext()

        val paidProvider = mockk<AnalyticsProvider>(relaxed = true)
        every { paidProvider.key } returns "adobe-analytics"
        every { paidProvider.mapper } returns mockk(relaxed = true)

        TrackFlow.initialize(
            TrackFlow.Builder(releaseCtx)
                .addProvider(paidProvider)
                .licenseKey("tf_pro_test123")
                .logLevel(LogLevel.NONE)
                .build()
        )

        verify { paidProvider.initialize(releaseCtx) }
        TrackFlow.shutdown()
    }
}

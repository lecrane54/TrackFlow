package com.trackflow.core.pipeline

import android.content.Context
import com.trackflow.core.payload.AnalyticsPayload
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OfflineEventQueueTest {

    private lateinit var tempDir: File
    private lateinit var queue: OfflineEventQueue

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "trackflow_test_${System.nanoTime()}")
        tempDir.mkdirs()

        val context = mockk<Context>()
        every { context.filesDir } returns tempDir

        queue = OfflineEventQueue(context, maxPersistedEvents = 10)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun createPayload(name: String, timestamp: Long = System.currentTimeMillis()): AnalyticsPayload {
        return AnalyticsPayload(
            eventName = name,
            properties = mapOf("key" to "value", "count" to 42),
            providerExtras = emptyMap(),
            context = mapOf("platform" to "android"),
            timestamp = timestamp
        )
    }

    @Test
    fun `persist and drain round-trip`() {
        val events = listOf(
            createPayload("event_1"),
            createPayload("event_2"),
            createPayload("event_3")
        )
        queue.persist(events)

        val drained = queue.drain()
        assertEquals(3, drained.size)
        assertEquals("event_1", drained[0].eventName)
        assertEquals("event_2", drained[1].eventName)
        assertEquals("event_3", drained[2].eventName)
    }

    @Test
    fun `drain clears persisted events`() {
        queue.persist(listOf(createPayload("event_1")))
        queue.drain()

        val secondDrain = queue.drain()
        assertTrue(secondDrain.isEmpty())
    }

    @Test
    fun `persist respects max limit keeping newest`() {
        val events = (1..15).map { createPayload("event_$it", timestamp = it.toLong()) }
        queue.persist(events)

        val drained = queue.drain()
        assertEquals(10, drained.size)
        assertEquals("event_6", drained[0].eventName)
        assertEquals("event_15", drained[9].eventName)
    }

    @Test
    fun `empty drain returns empty list`() {
        val drained = queue.drain()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `properties preserved through serialization`() {
        val payload = AnalyticsPayload(
            eventName = "test",
            properties = mapOf(
                "string" to "hello",
                "int" to 42,
                "double" to 3.14,
                "boolean" to true,
                "null_val" to null
            ),
            providerExtras = emptyMap(),
            context = mapOf("session_id" to "abc-123"),
            timestamp = 1000L
        )
        queue.persist(listOf(payload))

        val drained = queue.drain()
        assertEquals(1, drained.size)
        val restored = drained[0]
        assertEquals("test", restored.eventName)
        assertEquals("hello", restored.properties["string"])
        assertEquals(42, restored.properties["int"])
        assertEquals(true, restored.properties["boolean"])
        assertEquals(null, restored.properties["null_val"])
        assertEquals(1000L, restored.timestamp)
        assertEquals("abc-123", restored.context["session_id"])
    }

    @Test
    fun `corrupted file handled gracefully`() {
        val file = File(tempDir, "trackflow_queue.json")
        file.writeText("this is not json!!!")

        val drained = queue.drain()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `size returns correct count`() {
        queue.persist(listOf(createPayload("e1"), createPayload("e2")))
        assertEquals(2, queue.size())
    }

    @Test
    fun `clear removes all events`() {
        queue.persist(listOf(createPayload("e1")))
        queue.clear()
        assertEquals(0, queue.size())
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `persist empty list does nothing`() {
        queue.persist(emptyList())
        assertEquals(0, queue.size())
    }

    @Test
    fun `multiple persists accumulate`() {
        queue.persist(listOf(createPayload("e1")))
        queue.persist(listOf(createPayload("e2")))
        queue.persist(listOf(createPayload("e3")))

        val drained = queue.drain()
        assertEquals(3, drained.size)
    }
}

package com.trackflow.core.debug

import com.trackflow.core.payload.AnalyticsPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DebugEventSinkTest {

    private val sink = DebugEventSink()

    private fun createPayload(name: String): AnalyticsPayload {
        return AnalyticsPayload(
            eventName = name,
            properties = mapOf("key" to "value"),
            providerExtras = emptyMap(),
            context = emptyMap(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `record adds to list`() {
        sink.record(createPayload("test_event"))
        assertEquals(1, sink.events().size)
        assertEquals("test_event", sink.events()[0].eventName)
    }

    @Test
    fun `events returns all recorded events`() {
        sink.record(createPayload("event_1"))
        sink.record(createPayload("event_2"))
        sink.record(createPayload("event_3"))
        assertEquals(3, sink.events().size)
    }

    @Test
    fun `events returns immutable copy`() {
        sink.record(createPayload("event_1"))
        val events = sink.events()
        sink.record(createPayload("event_2"))
        assertEquals(1, events.size)
        assertEquals(2, sink.events().size)
    }

    @Test
    fun `thread safety with concurrent writes`() {
        val threadCount = 10
        val eventsPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadIndex ->
            executor.submit {
                repeat(eventsPerThread) { eventIndex ->
                    sink.record(createPayload("thread_${threadIndex}_event_$eventIndex"))
                }
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(threadCount * eventsPerThread, sink.events().size)
    }

    @Test
    fun `empty sink returns empty list`() {
        assertTrue(sink.events().isEmpty())
    }
}

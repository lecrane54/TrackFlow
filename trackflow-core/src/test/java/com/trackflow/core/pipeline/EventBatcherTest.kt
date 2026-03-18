package com.trackflow.core.pipeline

import com.trackflow.core.payload.AnalyticsPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBatcherTest {

    private fun createPayload(name: String): AnalyticsPayload {
        return AnalyticsPayload(
            eventName = name,
            properties = emptyMap(),
            providerExtras = emptyMap(),
            context = emptyMap(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `flush when batch size reached`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 3,
            flushIntervalMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.enqueue(createPayload("event_1"))
        batcher.enqueue(createPayload("event_2"))
        batcher.enqueue(createPayload("event_3"))

        advanceUntilIdle()

        assertEquals(1, flushedBatches.size)
        assertEquals(3, flushedBatches[0].size)
    }

    @Test
    fun `no flush before batch size reached`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 5,
            flushIntervalMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.enqueue(createPayload("event_1"))
        batcher.enqueue(createPayload("event_2"))

        advanceUntilIdle()

        assertEquals(0, flushedBatches.size)
    }

    @Test
    fun `flush on timer expiry`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 100,
            flushIntervalMs = 5_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.enqueue(createPayload("event_1"))
        advanceTimeBy(5_001L)
        advanceUntilIdle()

        assertEquals(1, flushedBatches.size)
        assertEquals(1, flushedBatches[0].size)
    }

    @Test
    fun `manual flush drains buffer`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 100,
            flushIntervalMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.enqueue(createPayload("event_1"))
        batcher.enqueue(createPayload("event_2"))
        batcher.flush()

        advanceUntilIdle()

        assertEquals(1, flushedBatches.size)
        assertEquals(2, flushedBatches[0].size)
    }

    @Test
    fun `empty flush does not call onFlush`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 10,
            flushIntervalMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.flush()
        advanceUntilIdle()

        assertTrue(flushedBatches.isEmpty())
    }

    @Test
    fun `stop flushes remaining events`() = runTest {
        val flushedBatches = mutableListOf<List<AnalyticsPayload>>()
        val batcher = EventBatcher(
            maxBatchSize = 100,
            flushIntervalMs = 60_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            onFlush = { flushedBatches.add(it) }
        )
        batcher.start()

        batcher.enqueue(createPayload("event_1"))
        batcher.stop()

        advanceUntilIdle()

        assertEquals(1, flushedBatches.size)
        assertEquals("event_1", flushedBatches[0][0].eventName)
    }
}

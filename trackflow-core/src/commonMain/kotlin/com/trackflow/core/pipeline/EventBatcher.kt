package com.trackflow.core.pipeline

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.trackflow.core.payload.AnalyticsPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Buffers analytics events and flushes them in batches to reduce network overhead.
 *
 * Events are accumulated in an internal buffer and flushed to the provided [onFlush]
 * callback when either of the following conditions is met:
 * - The buffer size reaches [maxBatchSize].
 * - The periodic flush interval ([flushIntervalMs]) elapses.
 *
 * The batcher must be explicitly [started][start] before enqueueing events, and
 * should be [stopped][stop] when the SDK shuts down. Calling [stop] triggers a
 * final flush of any remaining buffered events before cancelling the internal
 * coroutine scope.
 *
 * All buffer operations are synchronized to ensure thread safety.
 *
 * @param maxBatchSize The maximum number of events to buffer before triggering an automatic flush. Defaults to `20`.
 * @param flushIntervalMs The time interval in milliseconds between periodic flushes. Defaults to `30,000` ms (30 seconds).
 * @param dispatcher The [CoroutineDispatcher] used for flush and timer coroutines. Defaults to [Dispatchers.Default].
 * @param onFlush A suspending callback invoked with the list of batched events when a flush occurs.
 */
internal class EventBatcher(
    private val maxBatchSize: Int = 20,
    private val flushIntervalMs: Long = 30_000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onFlush: suspend (List<AnalyticsPayload>) -> Unit
) {
    /** Synchronization lock guarding access to [buffer]. */
    private val lock = SynchronizedObject()

    /** Internal buffer holding events waiting to be flushed. */
    private val buffer = mutableListOf<AnalyticsPayload>()

    /** The coroutine scope used for flush and timer operations, created in [start]. */
    private var scope: CoroutineScope? = null

    /** The currently active periodic flush timer job. */
    private var timerJob: Job? = null

    /**
     * Starts the batcher by creating a coroutine scope and launching the periodic flush timer.
     *
     * Must be called before [enqueue]. Calling [start] multiple times without [stop]
     * will replace the coroutine scope.
     */
    fun start() {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        startTimer()
    }

    /**
     * Adds an analytics payload to the internal buffer.
     *
     * If the buffer size reaches [maxBatchSize] after this addition, an
     * immediate [flush] is triggered.
     *
     * @param payload The [AnalyticsPayload] to enqueue for batching.
     */
    fun enqueue(payload: AnalyticsPayload) {
        var shouldFlush = false
        synchronized(lock) {
            buffer.add(payload)
            shouldFlush = buffer.size >= maxBatchSize
        }
        if (shouldFlush) {
            flush()
        }
    }

    /**
     * Immediately flushes all buffered events by invoking the [onFlush] callback.
     *
     * The buffer is cleared under the lock, and the batch is dispatched
     * asynchronously via the internal coroutine scope. The periodic flush
     * timer is restarted after each flush.
     *
     * If the buffer is empty, no callback is invoked.
     */
    fun flush() {
        var batch = emptyList<AnalyticsPayload>()
        synchronized(lock) {
            batch = buffer.toList()
            buffer.clear()
        }
        if (batch.isNotEmpty()) {
            scope?.launch { onFlush(batch) }
        }
        restartTimer()
    }

    /**
     * Stops the batcher by performing a final flush and cancelling the coroutine scope.
     *
     * After calling [stop], the batcher will no longer process enqueued events
     * until [start] is called again.
     */
    fun stop() {
        var batch = emptyList<AnalyticsPayload>()
        synchronized(lock) {
            batch = buffer.toList()
            buffer.clear()
        }
        if (batch.isNotEmpty()) {
            runBlocking { onFlush(batch) }
        }
        timerJob?.cancel()
        scope?.cancel()
        scope = null
    }

    /**
     * Launches the periodic flush timer that triggers [flush] after [flushIntervalMs].
     */
    private fun startTimer() {
        timerJob = scope?.launch {
            delay(flushIntervalMs)
            flush()
        }
    }

    /**
     * Cancels the current timer job and starts a new one, effectively resetting
     * the flush interval countdown.
     */
    private fun restartTimer() {
        timerJob?.cancel()
        startTimer()
    }
}

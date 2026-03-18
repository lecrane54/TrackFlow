package com.trackflow.core.pipeline

import com.trackflow.core.payload.AnalyticsPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class EventBatcher(
    private val maxBatchSize: Int = 20,
    private val flushIntervalMs: Long = 30_000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFlush: suspend (List<AnalyticsPayload>) -> Unit
) {
    private val lock = Any()
    private val buffer = mutableListOf<AnalyticsPayload>()
    private var scope: CoroutineScope? = null
    private var timerJob: Job? = null

    fun start() {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        startTimer()
    }

    fun enqueue(payload: AnalyticsPayload) {
        val shouldFlush: Boolean
        synchronized(lock) {
            buffer.add(payload)
            shouldFlush = buffer.size >= maxBatchSize
        }
        if (shouldFlush) {
            flush()
        }
    }

    fun flush() {
        val batch: List<AnalyticsPayload>
        synchronized(lock) {
            batch = buffer.toList()
            buffer.clear()
        }
        if (batch.isNotEmpty()) {
            scope?.launch { onFlush(batch) }
        }
        restartTimer()
    }

    fun stop() {
        flush()
        scope?.cancel()
        scope = null
    }

    private fun startTimer() {
        timerJob = scope?.launch {
            delay(flushIntervalMs)
            flush()
        }
    }

    private fun restartTimer() {
        timerJob?.cancel()
        startTimer()
    }
}

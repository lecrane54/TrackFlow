package com.trackflow.core.pipeline

import kotlinx.coroutines.delay

internal class RetryPolicy(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    suspend fun <T> execute(block: suspend () -> T): Result<T> {
        var lastException: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastException = e
                val delayMs = (baseDelayMs * (1L shl attempt)).coerceAtMost(maxDelayMs)
                delay(delayMs)
            }
        }
        return Result.failure(lastException ?: RuntimeException("Retry exhausted"))
    }
}

package com.trackflow.core.pipeline

import kotlinx.coroutines.delay

/**
 * Configurable retry policy with exponential backoff for transient failures.
 *
 * When a suspending operation fails, this policy retries it up to [maxRetries] times,
 * waiting between attempts with an exponentially increasing delay. The delay doubles
 * with each attempt (2^attempt * [baseDelayMs]) and is capped at [maxDelayMs].
 *
 * @param maxRetries The maximum number of retry attempts before giving up. Defaults to `3`.
 * @param baseDelayMs The initial delay in milliseconds before the first retry. The delay
 *   doubles with each subsequent attempt. Defaults to `1,000` ms (1 second).
 * @param maxDelayMs The maximum delay in milliseconds between retries, preventing
 *   excessively long waits. Defaults to `30,000` ms (30 seconds).
 */
internal class RetryPolicy(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    /**
     * Executes the given suspending [block] with retry logic and exponential backoff.
     *
     * The block is attempted up to [maxRetries] times. On each failure, the policy
     * waits for an exponentially increasing duration before retrying. If all attempts
     * fail, the result wraps the last caught exception.
     *
     * @param T The return type of the operation.
     * @param block The suspending operation to execute and potentially retry.
     * @return A [Result] wrapping the successful value, or the last [Throwable] if all retries were exhausted.
     */
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

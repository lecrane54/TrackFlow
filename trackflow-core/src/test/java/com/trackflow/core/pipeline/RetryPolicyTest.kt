package com.trackflow.core.pipeline

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {

    @Test
    fun `success on first attempt returns immediately`() = runTest {
        val policy = RetryPolicy(maxRetries = 3)
        var callCount = 0

        val result = policy.execute {
            callCount++
            "success"
        }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun `retries on failure up to max`() = runTest {
        val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 1L, maxDelayMs = 10L)
        var callCount = 0

        val result = policy.execute<String> {
            callCount++
            throw RuntimeException("fail")
        }

        assertTrue(result.isFailure)
        assertEquals(3, callCount)
        assertEquals("fail", result.exceptionOrNull()?.message)
    }

    @Test
    fun `success on second attempt stops retrying`() = runTest {
        val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 1L, maxDelayMs = 10L)
        var callCount = 0

        val result = policy.execute {
            callCount++
            if (callCount < 2) throw RuntimeException("transient error")
            "recovered"
        }

        assertTrue(result.isSuccess)
        assertEquals("recovered", result.getOrNull())
        assertEquals(2, callCount)
    }

    @Test
    fun `returns failure after max retries exhausted`() = runTest {
        val policy = RetryPolicy(maxRetries = 1, baseDelayMs = 1L, maxDelayMs = 10L)
        var callCount = 0

        val result = policy.execute<String> {
            callCount++
            throw IllegalStateException("persistent error")
        }

        assertTrue(result.isFailure)
        assertEquals(1, callCount)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `single retry policy attempts exactly once`() = runTest {
        val policy = RetryPolicy(maxRetries = 1, baseDelayMs = 1L)
        var callCount = 0

        val result = policy.execute<String> {
            callCount++
            throw RuntimeException("error")
        }

        assertTrue(result.isFailure)
        assertEquals(1, callCount)
    }
}

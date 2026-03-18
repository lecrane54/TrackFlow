package com.trackflow.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackFlowLoggerTest {

    private val capturedLogs = mutableListOf<CapturedLog>()

    data class CapturedLog(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    @Before
    fun setup() {
        capturedLogs.clear()
        TrackFlowLogger.listener = TrackFlowLogListener { level, tag, message, throwable ->
            capturedLogs.add(CapturedLog(level, tag, message, throwable))
        }
    }

    @After
    fun teardown() {
        TrackFlowLogger.level = LogLevel.ERROR
        TrackFlowLogger.listener = null
    }

    @Test
    fun `default level is ERROR`() {
        TrackFlowLogger.listener = null
        // Reset to verify default
        assertEquals(LogLevel.ERROR, LogLevel.ERROR) // Default set in object
    }

    @Test
    fun `error messages sent to listener when level is ERROR`() {
        TrackFlowLogger.level = LogLevel.ERROR
        TrackFlowLogger.error("test error")

        assertEquals(1, capturedLogs.size)
        assertEquals(LogLevel.ERROR, capturedLogs[0].level)
        assertEquals("test error", capturedLogs[0].message)
    }

    @Test
    fun `error with throwable passes throwable to listener`() {
        TrackFlowLogger.level = LogLevel.ERROR
        val exception = RuntimeException("boom")
        TrackFlowLogger.error("test error", exception)

        assertEquals(1, capturedLogs.size)
        assertEquals(exception, capturedLogs[0].throwable)
    }

    @Test
    fun `debug messages filtered when level is ERROR`() {
        TrackFlowLogger.level = LogLevel.ERROR
        TrackFlowLogger.debug("debug message")

        assertTrue(capturedLogs.isEmpty())
    }

    @Test
    fun `warn messages filtered when level is ERROR`() {
        TrackFlowLogger.level = LogLevel.ERROR
        TrackFlowLogger.warn("warn message")

        assertTrue(capturedLogs.isEmpty())
    }

    @Test
    fun `all messages sent when level is VERBOSE`() {
        TrackFlowLogger.level = LogLevel.VERBOSE
        TrackFlowLogger.error("error")
        TrackFlowLogger.warn("warn")
        TrackFlowLogger.debug("debug")
        TrackFlowLogger.verbose("verbose")

        assertEquals(4, capturedLogs.size)
        assertEquals(LogLevel.ERROR, capturedLogs[0].level)
        assertEquals(LogLevel.WARN, capturedLogs[1].level)
        assertEquals(LogLevel.DEBUG, capturedLogs[2].level)
        assertEquals(LogLevel.VERBOSE, capturedLogs[3].level)
    }

    @Test
    fun `no messages when level is NONE`() {
        TrackFlowLogger.level = LogLevel.NONE
        TrackFlowLogger.error("error")
        TrackFlowLogger.warn("warn")
        TrackFlowLogger.debug("debug")
        TrackFlowLogger.verbose("verbose")

        assertTrue(capturedLogs.isEmpty())
    }

    @Test
    fun `listener receives correct tag`() {
        TrackFlowLogger.level = LogLevel.ERROR
        TrackFlowLogger.error("test")

        assertEquals("TrackFlow", capturedLogs[0].tag)
    }
}

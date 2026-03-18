package com.trackflow.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val sessionManager = SessionManager()

    @Test
    fun `session returns UUID format string`() {
        val session = sessionManager.session()
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue("Session ID should be UUID format", uuidRegex.matches(session))
    }

    @Test
    fun `session is stable across calls`() {
        val first = sessionManager.session()
        val second = sessionManager.session()
        assertEquals(first, second)
    }

    @Test
    fun `reset generates new session id`() {
        val before = sessionManager.session()
        sessionManager.reset()
        val after = sessionManager.session()
        assertNotEquals(before, after)
    }

    @Test
    fun `reset produces valid UUID`() {
        sessionManager.reset()
        val session = sessionManager.session()
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        assertTrue("Reset session ID should be UUID format", uuidRegex.matches(session))
    }
}

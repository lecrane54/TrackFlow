
package com.trackflow.core.session

import java.util.UUID

/**
 * Manages the analytics session lifecycle for the TrackFlow SDK.
 *
 * A session represents a continuous period of user activity. Each instance
 * generates a unique session identifier on creation and provides the ability
 * to reset it (e.g., when the user logs out or the app returns from a
 * prolonged background state).
 *
 * Session IDs are UUID v4 strings generated via [UUID.randomUUID].
 */
class SessionManager {

    /** The current session identifier, generated as a random UUID string. */
    private var sessionId = UUID.randomUUID().toString()

    /**
     * Returns the current session identifier.
     *
     * @return A UUID v4 string representing the active session.
     */
    fun session(): String = sessionId

    /**
     * Resets the session by generating a new random UUID.
     *
     * Call this when the user's session should be considered new, such as
     * after a logout, a prolonged period of inactivity, or an explicit
     * session-reset action by the host application.
     */
    fun reset() {
        sessionId = UUID.randomUUID().toString()
    }
}

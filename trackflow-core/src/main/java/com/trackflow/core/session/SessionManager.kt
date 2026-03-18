
package com.trackflow.core.session

import java.util.UUID

class SessionManager {

    private var sessionId = UUID.randomUUID().toString()

    fun session(): String = sessionId

    fun reset() {
        sessionId = UUID.randomUUID().toString()
    }
}

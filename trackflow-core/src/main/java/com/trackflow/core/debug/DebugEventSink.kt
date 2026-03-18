
package com.trackflow.core.debug

import com.trackflow.core.payload.AnalyticsPayload
import java.util.concurrent.CopyOnWriteArrayList

class DebugEventSink {

    private val events = CopyOnWriteArrayList<AnalyticsPayload>()

    fun record(payload: AnalyticsPayload) {
        events.add(payload)
    }

    fun events(): List<AnalyticsPayload> = events.toList()
}

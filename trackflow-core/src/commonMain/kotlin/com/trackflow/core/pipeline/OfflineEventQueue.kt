package com.trackflow.core.pipeline

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.PlatformContext

internal expect class OfflineEventQueue(context: PlatformContext, maxPersistedEvents: Int = 500) {
    fun persist(events: List<AnalyticsPayload>)
    fun drain(): List<AnalyticsPayload>
    fun size(): Int
    fun clear()
}

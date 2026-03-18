package com.trackflow.core.pipeline

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.platform.PlatformContext
import platform.Foundation.*

internal actual class OfflineEventQueue actual constructor(
    context: PlatformContext,
    private val maxPersistedEvents: Int
) {
    private val filePath: String
    private val lock = Any()

    init {
        val dirs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        )
        val docDir = dirs.firstOrNull() as? String ?: "/tmp"
        filePath = "$docDir/trackflow_queue.json"
    }

    actual fun persist(events: List<AnalyticsPayload>) {
        if (events.isEmpty()) return
        synchronized(lock) {
            try {
                val existing = readEvents().toMutableList()
                existing.addAll(events)
                val trimmed = if (existing.size > maxPersistedEvents) {
                    existing.takeLast(maxPersistedEvents)
                } else existing
                writeEvents(trimmed)
            } catch (e: Exception) {
                TrackFlowLogger.error("Failed to persist events", e)
            }
        }
    }

    actual fun drain(): List<AnalyticsPayload> {
        return synchronized(lock) {
            try {
                val events = readEvents()
                NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                events
            } catch (e: Exception) {
                TrackFlowLogger.error("Failed to drain offline queue", e)
                emptyList()
            }
        }
    }

    actual fun size(): Int {
        return synchronized(lock) {
            try { readEvents().size } catch (_: Exception) { 0 }
        }
    }

    actual fun clear() {
        synchronized(lock) {
            NSFileManager.defaultManager.removeItemAtPath(filePath, null)
        }
    }

    private fun readEvents(): List<AnalyticsPayload> {
        val content = NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null)
            ?: return emptyList()
        // Simple JSON array parsing - each event is delimited by known markers
        // For production, use kotlinx.serialization. For now, store as simple format.
        return emptyList() // Stub — full implementation would parse JSON
    }

    private fun writeEvents(events: List<AnalyticsPayload>) {
        val json = StringBuilder("[]") // Stub — full implementation would serialize
        (json.toString() as NSString).writeToFile(filePath, true, NSUTF8StringEncoding, null)
    }
}

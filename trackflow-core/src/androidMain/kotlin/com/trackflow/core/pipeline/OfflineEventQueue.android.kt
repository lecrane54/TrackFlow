package com.trackflow.core.pipeline

import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.platform.PlatformContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A file-backed queue that persists analytics events for offline or failed-delivery scenarios.
 *
 * Events are serialized to JSON and stored in a file within the application's internal
 * storage directory (`trackflow_queue.json`). The queue enforces a maximum capacity
 * ([maxPersistedEvents]) by discarding the oldest events when the limit is exceeded,
 * ensuring that the most recent events are always retained.
 *
 * All file operations are protected by a [ReentrantReadWriteLock] to guarantee
 * thread safety for concurrent reads and writes.
 *
 * @param context The [PlatformContext] (Android Context) used to resolve the internal files directory.
 * @param maxPersistedEvents The maximum number of events to persist on disk. When exceeded,
 *   the oldest events are discarded. Defaults to `500`.
 */
internal actual class OfflineEventQueue actual constructor(
    context: PlatformContext,
    private val maxPersistedEvents: Int
) {
    /** The JSON file used to persist queued events. */
    private val file = File(context.filesDir, "trackflow_queue.json")

    /** Read-write lock protecting all file I/O operations. */
    private val lock = ReentrantReadWriteLock()

    /**
     * Persists a list of analytics events to the offline queue file.
     *
     * New events are appended to any existing persisted events. If the total
     * count exceeds [maxPersistedEvents], the oldest events are trimmed so
     * that only the newest events are retained.
     *
     * @param events The list of [AnalyticsPayload] events to persist. If empty, this is a no-op.
     */
    actual fun persist(events: List<AnalyticsPayload>) {
        if (events.isEmpty()) return
        lock.write {
            try {
                val existing = readArray()
                for (event in events) {
                    existing.put(event.toJson())
                }
                // Enforce max limit — keep newest events
                val trimmed = if (existing.length() > maxPersistedEvents) {
                    val arr = JSONArray()
                    val start = existing.length() - maxPersistedEvents
                    for (i in start until existing.length()) {
                        arr.put(existing.getJSONObject(i))
                    }
                    arr
                } else {
                    existing
                }
                file.writeText(trimmed.toString())
            } catch (e: Exception) {
                TrackFlowLogger.error("Failed to persist events", e)
            }
        }
    }

    /**
     * Drains all persisted events from the queue, removing them from disk.
     *
     * Reads and deserializes every event from the backing file, deletes the file,
     * and returns the events. Corrupted individual entries are skipped with a
     * warning log rather than failing the entire drain operation.
     *
     * @return A list of [AnalyticsPayload] events that were persisted, or an empty list
     *   if the queue was empty or an error occurred.
     */
    actual fun drain(): List<AnalyticsPayload> {
        return lock.write {
            try {
                val array = readArray()
                val events = mutableListOf<AnalyticsPayload>()
                for (i in 0 until array.length()) {
                    try {
                        events.add(array.getJSONObject(i).toPayload())
                    } catch (e: Exception) {
                        TrackFlowLogger.warn("Skipping corrupted event at index $i")
                    }
                }
                file.delete()
                events
            } catch (e: Exception) {
                TrackFlowLogger.error("Failed to drain offline queue", e)
                emptyList()
            }
        }
    }

    /**
     * Returns the number of events currently persisted in the queue.
     *
     * @return The count of persisted events, or `0` if the file is empty or unreadable.
     */
    actual fun size(): Int {
        return lock.read {
            try {
                readArray().length()
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Deletes all persisted events by removing the backing file.
     */
    actual fun clear() {
        lock.write {
            file.delete()
        }
    }

    /**
     * Reads the persisted JSON array from the backing file.
     *
     * @return The [JSONArray] of persisted events, or an empty array if the file
     *   does not exist or contains invalid JSON.
     */
    private fun readArray(): JSONArray {
        return if (file.exists()) {
            try {
                JSONArray(file.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }

    /**
     * Serializes this [AnalyticsPayload] into a [JSONObject] for file persistence.
     *
     * @return A [JSONObject] representation of the payload.
     */
    private fun AnalyticsPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("eventName", eventName)
            put("properties", mapToJson(properties))
            put("context", mapToJson(context))
            put("timestamp", timestamp)
            put("type", type.name)
        }
    }

    /**
     * Converts a [Map] of string keys and mixed values into a [JSONObject].
     *
     * Handles `null`, [String], [Number], [Boolean], and nested [Map] values.
     * Other types are converted to their [toString] representation.
     *
     * @param map The map to convert.
     * @return A [JSONObject] representing the map contents.
     */
    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is String, is Number, is Boolean -> json.put(key, value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    json.put(key, mapToJson(value as Map<String, Any?>))
                }
                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    /**
     * Deserializes a [JSONObject] into an [AnalyticsPayload].
     *
     * Falls back to [EventType.ACTION] if the persisted type name is unrecognized.
     *
     * @return The deserialized [AnalyticsPayload].
     * @throws org.json.JSONException If required fields are missing from the JSON.
     */
    private fun JSONObject.toPayload(): AnalyticsPayload {
        val typeName = optString("type", EventType.ACTION.name)
        return AnalyticsPayload(
            eventName = getString("eventName"),
            properties = jsonToMap(getJSONObject("properties")),
            providerExtras = emptyMap(),
            context = jsonToMap(getJSONObject("context")),
            timestamp = getLong("timestamp"),
            type = try { EventType.valueOf(typeName) } catch (_: Exception) { EventType.ACTION }
        )
    }

    /**
     * Converts a [JSONObject] into a [Map] of string keys and nullable values.
     *
     * Handles [JSONObject.NULL] as Kotlin `null` and recursively converts nested
     * [JSONObject] values into maps.
     *
     * @param json The JSON object to convert.
     * @return A [Map] representing the JSON contents.
     */
    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = when {
                value == JSONObject.NULL -> null
                value is JSONObject -> jsonToMap(value)
                else -> value
            }
        }
        return map
    }
}

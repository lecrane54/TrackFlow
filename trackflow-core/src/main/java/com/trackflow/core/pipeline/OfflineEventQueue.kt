package com.trackflow.core.pipeline

import android.content.Context
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class OfflineEventQueue(
    context: Context,
    private val maxPersistedEvents: Int = 500
) {
    private val file = File(context.filesDir, "trackflow_queue.json")
    private val lock = ReentrantReadWriteLock()

    fun persist(events: List<AnalyticsPayload>) {
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

    fun drain(): List<AnalyticsPayload> {
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

    fun size(): Int {
        return lock.read {
            try {
                readArray().length()
            } catch (e: Exception) {
                0
            }
        }
    }

    fun clear() {
        lock.write {
            file.delete()
        }
    }

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

    private fun AnalyticsPayload.toJson(): JSONObject {
        return JSONObject().apply {
            put("eventName", eventName)
            put("properties", mapToJson(properties))
            put("context", mapToJson(context))
            put("timestamp", timestamp)
            put("type", type.name)
        }
    }

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

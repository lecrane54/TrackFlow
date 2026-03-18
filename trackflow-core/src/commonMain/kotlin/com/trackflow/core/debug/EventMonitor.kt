package com.trackflow.core.debug

import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.platform.currentTimeMillis
import com.trackflow.core.provider.ProviderEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single delivery attempt to a provider.
 *
 * @property payload The original analytics payload from the pipeline.
 * @property mappedEvent The provider-specific mapped event (post keyMap/prefix). Null for offline/dropped.
 * @property providerKey The provider that received (or failed to receive) this event.
 * @property status Whether delivery succeeded or failed.
 * @property error Error message if delivery failed, null on success.
 * @property timestampMs When the delivery was attempted.
 */
data class DeliveryRecord(
    val payload: AnalyticsPayload,
    val mappedEvent: ProviderEvent?,
    val providerKey: String,
    val status: DeliveryStatus,
    val error: String? = null,
    val timestampMs: Long = currentTimeMillis()
)

/** Status of a provider delivery attempt. */
enum class DeliveryStatus { DELIVERED, FAILED, QUEUED_OFFLINE, DROPPED_BY_MIDDLEWARE }

/**
 * Real-time event monitor that tracks delivery status across all providers.
 *
 * Exposes a [StateFlow] of [DeliveryRecord] entries that can be collected
 * by Compose UIs or any coroutine-based observer for live event inspection.
 */
class EventMonitor {

    private val maxRecords = 200

    private val _records = MutableStateFlow<List<DeliveryRecord>>(emptyList())

    /** Live stream of delivery records, newest first. */
    val records: StateFlow<List<DeliveryRecord>> = _records.asStateFlow()

    /** Records a successful delivery to a provider, including the mapped event. */
    internal fun recordDelivery(payload: AnalyticsPayload, providerKey: String, mapped: ProviderEvent) {
        append(DeliveryRecord(payload, mapped, providerKey, DeliveryStatus.DELIVERED))
    }

    /** Records a failed delivery to a provider, including the mapped event. */
    internal fun recordFailure(payload: AnalyticsPayload, providerKey: String, mapped: ProviderEvent?, error: String?) {
        append(DeliveryRecord(payload, mapped, providerKey, DeliveryStatus.FAILED, error))
    }

    /** Records that an event was queued offline. */
    internal fun recordOffline(payload: AnalyticsPayload) {
        append(DeliveryRecord(payload, null, "offline_queue", DeliveryStatus.QUEUED_OFFLINE))
    }

    /** Records that an event was dropped by middleware. */
    internal fun recordDropped(eventName: String) {
        val stub = AnalyticsPayload(
            eventName = eventName,
            properties = emptyMap(),
            providerExtras = emptyMap(),
            context = emptyMap(),
            timestamp = currentTimeMillis()
        )
        append(DeliveryRecord(stub, null, "middleware", DeliveryStatus.DROPPED_BY_MIDDLEWARE))
    }

    /** Returns all unique provider keys seen in the current records. */
    fun providerKeys(): Set<String> {
        return _records.value.map { it.providerKey }.toSet()
    }

    /** Returns all unique event names seen in the current records. */
    fun eventNames(): Set<String> {
        return _records.value.map { it.payload.eventName }.toSet()
    }

    /** Clears all recorded entries. */
    fun clear() {
        _records.value = emptyList()
    }

    /**
     * Exports all current records as a JSON string for sharing/debugging.
     *
     * Each record includes: timestamp, eventName, providerKey, status,
     * mapped properties (what the provider received), and error if any.
     */
    fun exportAsJson(): String {
        val sb = StringBuilder()
        sb.append("[\n")
        _records.value.forEachIndexed { index, record ->
            sb.append("  {\n")
            sb.append("    \"timestamp\": \"${formatIso(record.timestampMs)}\",\n")
            sb.append("    \"event\": \"${record.payload.eventName}\",\n")
            sb.append("    \"type\": \"${record.payload.type}\",\n")
            sb.append("    \"provider\": \"${record.providerKey}\",\n")
            sb.append("    \"status\": \"${record.status}\",\n")
            val props = record.mappedEvent?.properties ?: record.payload.properties
            sb.append("    \"properties\": {")
            if (props.isNotEmpty()) {
                sb.append("\n")
                props.entries.forEachIndexed { i, (k, v) ->
                    val escaped = v?.toString()?.replace("\"", "\\\"") ?: "null"
                    val valueStr = if (v is Number || v is Boolean) "$v" else "\"$escaped\""
                    sb.append("      \"$k\": $valueStr")
                    if (i < props.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append("    }")
            } else {
                sb.append("}")
            }
            val error = record.error
            if (error != null) {
                sb.append(",\n    \"error\": \"${error.replace("\"", "\\\"")}\"")
            }
            sb.append("\n  }")
            if (index < _records.value.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun append(record: DeliveryRecord) {
        val current = _records.value
        val updated = ArrayList<DeliveryRecord>(minOf(current.size + 1, maxRecords))
        updated.add(record)
        val copyCount = minOf(current.size, maxRecords - 1)
        for (i in 0 until copyCount) {
            updated.add(current[i])
        }
        _records.value = updated
    }

    /**
     * Formats a Unix timestamp in milliseconds to a simplified ISO 8601 string.
     * Uses manual calculation instead of java.text.SimpleDateFormat for KMP compatibility.
     */
    private fun formatIso(ms: Long): String {
        // Calculate date/time components from epoch millis (UTC)
        val totalSeconds = ms / 1000
        val millisPart = (ms % 1000).toInt()

        // Days since epoch (1970-01-01)
        var remainingDays = (totalSeconds / 86400).toInt()
        val timeOfDay = (totalSeconds % 86400).toInt()
        val hours = timeOfDay / 3600
        val minutes = (timeOfDay % 3600) / 60
        val seconds = timeOfDay % 60

        // Calculate year, month, day from days since epoch
        var year = 1970
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366 else 365
            if (remainingDays < daysInYear) break
            remainingDays -= daysInYear
            year++
        }

        val daysInMonths = if (isLeapYear(year)) {
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        } else {
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        }

        var month = 0
        while (month < 12 && remainingDays >= daysInMonths[month]) {
            remainingDays -= daysInMonths[month]
            month++
        }
        month += 1 // 1-indexed
        val day = remainingDays + 1 // 1-indexed

        return "${padZero(year, 4)}-${padZero(month, 2)}T${padZero(hours.toInt(), 2)}:" +
                "${padZero(minutes.toInt(), 2)}:${padZero(seconds.toInt(), 2)}.${padZero(millisPart, 3)}"
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun padZero(value: Int, length: Int): String {
        val s = value.toString()
        return if (s.length < length) "0".repeat(length - s.length) + s else s
    }
}

package com.trackflow.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackflow.core.TrackFlow
import com.trackflow.core.debug.DeliveryRecord
import com.trackflow.core.debug.DeliveryStatus
import com.trackflow.core.payload.EventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wraps your app content with a floating debug overlay for TrackFlow.
 *
 * Shows a small FAB in the bottom-right corner. Tapping it opens a full-screen
 * panel with filtering, search, and export capabilities.
 */
@Composable
fun TrackFlowDebugOverlay(
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val records by TrackFlow.eventMonitor().records.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            EventViewerPanel(
                records = records,
                onClose = { isExpanded = false },
                onClear = { TrackFlow.eventMonitor().clear() }
            )
        }

        if (!isExpanded) {
            FloatingActionButton(
                onClick = { isExpanded = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFF6200EE),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text("TF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${records.size}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun EventViewerPanel(
    records: List<DeliveryRecord>,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<DeliveryStatus?>(null) }
    var providerFilter by remember { mutableStateOf<String?>(null) }

    val filteredRecords by remember(records, searchQuery, statusFilter, providerFilter) {
        derivedStateOf {
            records.filter { record ->
                val matchesSearch = searchQuery.isBlank() ||
                    record.payload.eventName.contains(searchQuery, ignoreCase = true) ||
                    record.providerKey.contains(searchQuery, ignoreCase = true) ||
                    (record.mappedEvent?.properties ?: record.payload.properties).any { (k, v) ->
                        k.contains(searchQuery, ignoreCase = true) ||
                        v?.toString()?.contains(searchQuery, ignoreCase = true) == true
                    }
                val matchesStatus = statusFilter == null || record.status == statusFilter
                val matchesProvider = providerFilter == null || record.providerKey == providerFilter
                matchesSearch && matchesStatus && matchesProvider
            }
        }
    }

    val providerKeys = remember(records) {
        records.map { it.providerKey }.distinct().sorted()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header: title + stats + close ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF6200EE))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TrackFlow Debug",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                val delivered = records.count { it.status == DeliveryStatus.DELIVERED }
                val failed = records.count { it.status == DeliveryStatus.FAILED }
                Text(
                    text = "$delivered ok / $failed err",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }
            }

            // ── Search bar ──────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search events, providers, properties...", fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6200EE),
                    cursorColor = Color(0xFF6200EE)
                )
            )

            // ── Filter chips ────────────────────────────────
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip("All", selected = statusFilter == null && providerFilter == null) {
                    statusFilter = null
                    providerFilter = null
                }
                FilterChip("Delivered", selected = statusFilter == DeliveryStatus.DELIVERED, color = Green) {
                    statusFilter = if (statusFilter == DeliveryStatus.DELIVERED) null else DeliveryStatus.DELIVERED
                }
                FilterChip("Failed", selected = statusFilter == DeliveryStatus.FAILED, color = Red) {
                    statusFilter = if (statusFilter == DeliveryStatus.FAILED) null else DeliveryStatus.FAILED
                }
                FilterChip("Offline", selected = statusFilter == DeliveryStatus.QUEUED_OFFLINE, color = Orange) {
                    statusFilter = if (statusFilter == DeliveryStatus.QUEUED_OFFLINE) null else DeliveryStatus.QUEUED_OFFLINE
                }

                if (providerKeys.size > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    providerKeys.forEach { key ->
                        FilterChip(key, selected = providerFilter == key, color = Color(0xFF6200EE)) {
                            providerFilter = if (providerFilter == key) null else key
                        }
                    }
                }
            }

            // ── Results count ───────────────────────────────
            Text(
                text = "${filteredRecords.size} of ${records.size} events",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // ── Event list ──────────────────────────────────
            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (records.isEmpty()) "No events yet. Track something!"
                            else "No events match your filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        filteredRecords,
                        key = { "${it.timestampMs}_${it.providerKey}_${it.payload.eventName}_${it.hashCode()}" }
                    ) { record ->
                        DeliveryRecordCard(record)
                    }
                }
            }

            // ── Bottom action bar ───────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val json = TrackFlow.eventMonitor().exportAsJson()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                        putExtra(Intent.EXTRA_SUBJECT, "TrackFlow Debug Export")
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Events"))
                }) {
                    Text("Share", color = Purple)
                }
                TextButton(onClick = {
                    val json = TrackFlow.eventMonitor().exportAsJson()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TrackFlow Events", json))
                    Toast.makeText(context, "Copied ${records.size} events", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy JSON", color = Purple)
                }
                TextButton(onClick = onClear) {
                    Text("Clear All", color = Red)
                }
            }
        }
    }
}

// ── Colors ──────────────────────────────────────────────────

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val Orange = Color(0xFFFF9800)
private val Gray = Color(0xFF9E9E9E)
private val KeyColor = Color(0xFF78909C)
private val ValueColor = Color(0xFF263238)
private val Purple = Color(0xFF6200EE)

// ── Card ────────────────────────────────────────────────────

@Composable
private fun DeliveryRecordCard(record: DeliveryRecord) {
    val statusColor = when (record.status) {
        DeliveryStatus.DELIVERED -> Green
        DeliveryStatus.FAILED -> Red
        DeliveryStatus.QUEUED_OFFLINE -> Orange
        DeliveryStatus.DROPPED_BY_MIDDLEWARE -> Gray
    }

    val statusLabel = when (record.status) {
        DeliveryStatus.DELIVERED -> "DELIVERED"
        DeliveryStatus.FAILED -> "FAILED"
        DeliveryStatus.QUEUED_OFFLINE -> "OFFLINE"
        DeliveryStatus.DROPPED_BY_MIDDLEWARE -> "DROPPED"
    }

    val typeLabel = when (record.payload.type) {
        EventType.ACTION -> "ACTION"
        EventType.STATE -> "STATE"
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: event name + timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.payload.eventName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(record.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Tag(text = record.providerKey, color = Purple)
                Tag(text = statusLabel, color = statusColor)
                Tag(text = typeLabel, color = Color(0xFF455A64))
                record.mappedEvent?.let { mapped ->
                    if (mapped.name != record.payload.eventName) {
                        Tag(text = "mapped: ${mapped.name}", color = Color(0xFF00796B))
                    }
                }
            }

            // Row 3: context data
            val displayProps = record.mappedEvent?.properties ?: record.payload.properties
            if (displayProps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                val entriesToShow = if (expanded) displayProps.entries.toList()
                    else displayProps.entries.take(4).toList()

                entriesToShow.forEach { (key, value) ->
                    ContextDataRow(key = key, value = value?.toString() ?: "null")
                }

                if (!expanded && displayProps.size > 4) {
                    Text(
                        text = "+${displayProps.size - 4} more \u2022 tap to expand",
                        style = MaterialTheme.typography.labelSmall,
                        color = Purple,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Row 4: error
            val errorText = record.error
            if (errorText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Red,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Red.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                )
            }
        }
    }
}

// ── Shared Components ───────────────────────────────────────

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    color: Color = Color(0xFF455A64),
    onClick: () -> Unit
) {
    val bg = if (selected) color else color.copy(alpha = 0.08f)
    val fg = if (selected) Color.White else color

    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun ContextDataRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = KeyColor,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = ValueColor
        )
    }
}

private fun formatTime(timestampMs: Long): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
}

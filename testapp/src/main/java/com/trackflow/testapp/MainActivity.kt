package com.trackflow.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trackflow.core.TrackFlow
import com.trackflow.core.compose.TrackScreen
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.debug.TrackFlowDebugOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TrackFlowDebugOverlay {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TestScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun TestScreen() {
    TrackScreen("test_screen")

    var debugEvents by remember { mutableStateOf(TrackFlow.debugEvents()) }
    var eventCounter by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TrackFlow Test App",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            eventCounter++
            TrackFlow.track("app_launch",
                "click_count" to eventCounter,
                "screen" to "test_screen",
                "hm.filter.type" to "brand filter"
            )
            debugEvents = TrackFlow.debugEvents()
        }) {
            Text("Track Button Click ($eventCounter)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            TrackFlow.track("product_viewed",
                "product_id" to "sku_${(100..999).random()}",
                "price" to (9.99 + (0..90).random()),
                "currency" to "USD"
            )
            debugEvents = TrackFlow.debugEvents()
        }) {
            Text("Track Product View")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            TrackFlow.flush()
        }) {
            Text("Force Flush")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Debug Events (${debugEvents.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(debugEvents.reversed()) { event ->
                EventCard(event)
            }
        }
    }
}

@Composable
fun EventCard(payload: AnalyticsPayload) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = payload.eventName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = payload.properties.entries.joinToString(", ") { "${it.key}=${it.value}" },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "session: ${payload.context["session_id"]?.toString()?.take(50) ?: "?"}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

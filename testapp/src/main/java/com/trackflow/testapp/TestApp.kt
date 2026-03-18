package com.trackflow.testapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.trackflow.core.TrackFlow
import com.trackflow.core.logging.LogLevel
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.provider.amplitude.AmplitudeProvider

class TestApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Amplitude: remap to Amplitude's preferred naming conventions
        // e.g. track("product_viewed", "product_id" to "SKU-123", "screen" to "pdp", "price" to 29.99)
        //   → Amplitude receives: { "Product ID": "SKU-123", "screen": "pdp", "Revenue": 29.99 }
        val amplitudeKeyMap = mapOf(
            "product_id" to "Product ID",
            "product_name" to "Product Name",
            "category" to "Product Category",
            "price" to "Revenue",
            "quantity" to "Quantity",
            "search_query" to "Search Query",
            "search_results" to "Search Results Count",
        )

        TrackFlow.initialize(
            TrackFlow.Builder(applicationContext)
           //     .addProvider(LogcatProvider())
                .addProvider(
                    AmplitudeProvider(
                        BuildConfig.AMP_KEY,
                        keyMap = amplitudeKeyMap
                    )
                )
                .batchSize(5)
                .flushInterval(5_000L)
                .logLevel(LogLevel.VERBOSE)
                .build()
        )

    }
}

/**
 * A simple provider that logs events to Logcat for testing purposes.
 */
class LogcatProvider : AnalyticsProvider {

    override val key = "logcat"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties + mapOf(
                    "_context" to payload.context.toString(),
                    "_timestamp" to payload.timestamp
                )
            )
        }
    }

    override fun initialize(context: Context) {
        Log.d("LogcatProvider", "Initialized")
    }

    override fun track(event: ProviderEvent) {
        Log.d("LogcatProvider", "Event: ${event.name} | Props: ${event.properties}")
    }
}

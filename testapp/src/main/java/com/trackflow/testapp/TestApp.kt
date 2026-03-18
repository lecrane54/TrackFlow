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
import com.trackflow.provider.adobe.analytics.AdobeAnalyticsProvider

class TestApp : Application() {

    override fun onCreate() {
        super.onCreate()

        TrackFlow.initialize(
            TrackFlow.Builder(applicationContext)
                .addProvider(LogcatProvider())
                .addProvider(AdobeAnalyticsProvider(""))
                .batchSize(5)
                .flushInterval(10_000L)
                .logLevel(LogLevel.VERBOSE)
                .logListener { level, tag, message, _ ->
                    Log.d("TrackFlowListener", "[$level] $tag: $message")
                }
                .build()
        )

        Log.d("TestApp", "TrackFlow initialized")
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

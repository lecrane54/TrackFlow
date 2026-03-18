package com.trackflow.ios

import com.trackflow.core.TrackFlow
import com.trackflow.core.platform.IosPlatformContext
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.logging.LogLevel

/**
 * iOS convenience initializer for TrackFlow.
 *
 * Provides a Swift-friendly API that avoids cross-framework type issues
 * by keeping all type references within a single framework.
 *
 * Usage from Swift:
 * ```swift
 * TrackFlowIos.shared.initialize(
 *     providers: [
 *         FirebaseIosProvider(keyMap: nil),
 *         AmplitudeIosProvider(apiKey: "key", keyMap: nil)
 *     ],
 *     logLevel: .debug,
 *     batchSize: 10
 * )
 * ```
 */
object TrackFlowIos {

    fun initialize(
        providers: List<AnalyticsProvider>,
        logLevel: LogLevel = LogLevel.ERROR,
        batchSize: Int = 20,
        flushIntervalMs: Long = 30_000L,
        licenseKey: String? = null
    ) {
        val context = IosPlatformContext()
        val builder = TrackFlow.Builder(context).apply {
            providers.forEach { addProvider(it) }
            logLevel(logLevel)
            batchSize(batchSize)
            flushInterval(flushIntervalMs)
            licenseKey?.let { licenseKey(it) }
        }
        TrackFlow.initialize(builder.build())
    }
}

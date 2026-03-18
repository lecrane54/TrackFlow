package com.trackflow.core

import com.trackflow.core.logging.LogLevel
import com.trackflow.core.platform.IosPlatformContext
import com.trackflow.core.provider.AnalyticsProvider

/**
 * iOS convenience initializer for TrackFlow.
 *
 * Solves the cross-framework type mismatch in Swift by keeping all
 * initialization within a single framework (TrackFlowCore). Providers
 * register themselves via [addProvider], then [initialize] wires everything up.
 *
 * Usage from Swift (each provider framework re-exports this via TrackFlowCore types):
 * ```swift
 * import TrackFlowCore
 * import trackflow_provider_firebase_ios
 *
 * // In each provider framework, call the provider's register function:
 * TrackFlowIos.shared.addProvider(provider: FirebaseIosProvider(keyMap: nil))
 * TrackFlowIos.shared.addProvider(provider: AmplitudeIosProvider(apiKey: "key", keyMap: nil))
 * TrackFlowIos.shared.initialize(logLevel: .debug, batchSize: 10)
 * ```
 */
object TrackFlowIos {

    private val pendingProviders = mutableListOf<AnalyticsProvider>()

    /**
     * Register a provider to be included when [initialize] is called.
     * Call this from Swift for each provider you want to use.
     */
    fun addProvider(provider: AnalyticsProvider) {
        pendingProviders.add(provider)
    }

    /**
     * Initialize TrackFlow with all registered providers.
     */
    fun initialize(
        logLevel: LogLevel = LogLevel.ERROR,
        batchSize: Int = 20,
        flushIntervalMs: Long = 30_000L,
        licenseKey: String? = null
    ) {
        val context = IosPlatformContext()
        val builder = TrackFlow.Builder(context).apply {
            pendingProviders.forEach { addProvider(it) }
            logLevel(logLevel)
            batchSize(batchSize)
            flushInterval(flushIntervalMs)
            licenseKey?.let { licenseKey(it) }
        }
        TrackFlow.initialize(builder.build())
        pendingProviders.clear()
    }
}

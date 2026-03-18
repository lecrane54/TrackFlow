package com.trackflow.core

import android.content.Context
import com.trackflow.core.context.DefaultContextProvider
import com.trackflow.core.debug.DebugEventSink
import com.trackflow.core.debug.EventMonitor
import com.trackflow.core.event.TrackFlowEvent
import com.trackflow.core.identity.IdentityListener
import com.trackflow.core.identity.IdentityManager
import com.trackflow.core.lifecycle.LifecycleTracker
import com.trackflow.core.logging.LogLevel
import com.trackflow.core.logging.TrackFlowLogListener
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.middleware.TrackFlowMiddleware
import com.trackflow.core.middleware.applyAll
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.pipeline.EventBatcher
import com.trackflow.core.pipeline.EventDeduplicator
import com.trackflow.core.pipeline.EventDispatcher
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.session.SessionManager

/**
 * Main entry point for the TrackFlow analytics SDK.
 *
 * TrackFlow is a singleton that manages the complete analytics pipeline:
 * event ingestion, context enrichment, middleware processing, deduplication,
 * batching, and distribution to multiple analytics providers.
 *
 * Must be initialized via [initialize] before any tracking calls.
 */
object TrackFlow {

    private lateinit var providers: List<AnalyticsProvider>
    private lateinit var contextProvider: DefaultContextProvider
    private val sessionManager = SessionManager()
    private val debugSink = DebugEventSink()
    private val identityManager = IdentityManager()
    private val _eventMonitor = EventMonitor()

    private var batcher: EventBatcher? = null
    private var dispatcher: EventDispatcher? = null
    private var lifecycleTracker: LifecycleTracker? = null
    private var middlewares: List<TrackFlowMiddleware> = emptyList()

    private val superProperties = mutableMapOf<String, Any?>()
    private val superPropertiesLock = Any()

    @Volatile
    private var isInitialized = false

    /**
     * Initializes the TrackFlow SDK with the given [builder] configuration.
     *
     * Sets up providers, pipeline (batcher + dispatcher + deduplicator),
     * middleware, super properties, logging, identity, and optional lifecycle tracking.
     * Must be called once before any tracking calls, typically in `Application.onCreate()`.
     *
     * @param builder The configured [Builder] instance.
     */
    fun initialize(builder: Builder) {
        // Clean up previous initialization if re-initializing
        if (isInitialized) {
            shutdown()
        }

        TrackFlowLogger.level = builder.logLevel
        TrackFlowLogger.listener = builder.logListener
        middlewares = builder.middlewares.toList()

        providers = builder.providers.toList()
        contextProvider = DefaultContextProvider(builder.context)

        synchronized(superPropertiesLock) {
            superProperties.clear()
            superProperties.putAll(builder.superProperties)
        }

        identityManager.addListener(object : IdentityListener {
            override fun onIdentify(userId: String, traits: Map<String, Any?>) {
                providers.forEach { provider ->
                    try {
                        provider.identify(userId, traits)
                    } catch (e: Exception) {
                        TrackFlowLogger.error("Failed to identify on ${provider.key}", e)
                    }
                }
            }
            override fun onReset() {
                providers.forEach { provider ->
                    try {
                        provider.reset()
                    } catch (e: Exception) {
                        TrackFlowLogger.error("Failed to reset on ${provider.key}", e)
                    }
                }
            }
        })

        providers.forEach { provider ->
            try {
                provider.initialize(builder.context)
            } catch (e: Exception) {
                TrackFlowLogger.error("Failed to initialize provider: ${provider.key}", e)
            }
        }

        val deduplicator = if (builder.deduplicationWindowMs > 0) {
            EventDeduplicator(builder.deduplicationWindowMs)
        } else null

        val eventDispatcher = EventDispatcher(
            context = builder.context,
            providers = providers,
            deduplicator = deduplicator,
            eventMonitor = _eventMonitor
        )
        dispatcher = eventDispatcher
        eventDispatcher.start()

        val eventBatcher = EventBatcher(
            maxBatchSize = builder.batchSize,
            flushIntervalMs = builder.flushIntervalMs,
            onFlush = { batch -> eventDispatcher.dispatch(batch) }
        )
        batcher = eventBatcher
        eventBatcher.start()

        // Lifecycle auto-tracking
        if (builder.lifecycleTrackingEnabled) {
            val tracker = LifecycleTracker()
            lifecycleTracker = tracker
            tracker.register()
        }

        isInitialized = true
        TrackFlowLogger.debug("TrackFlow initialized with ${providers.size} providers")
    }

    // ── Track Actions ───────────────────────────────────────

    /** Tracks an action event using a [TrackFlowEvent] instance. */
    fun track(event: TrackFlowEvent) {
        enqueue(event, EventType.ACTION)
    }

    /** Tracks an action event with the given [name] and optional property pairs. */
    fun track(name: String, vararg properties: Pair<String, Any?>) {
        track(name, properties.toMap())
    }

    /** Tracks an action event with the given [name] and [properties] map. */
    fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        track(SimpleEvent(name, properties))
    }

    // ── Track State / Page Views ────────────────────────────

    /** Tracks a state/page-view event. Routed to provider-specific trackState methods. */
    fun trackState(event: TrackFlowEvent) {
        enqueue(event, EventType.STATE)
    }

    /** Tracks a state/page-view event with the given [name] and optional property pairs. */
    fun trackState(name: String, vararg properties: Pair<String, Any?>) {
        trackState(name, properties.toMap())
    }

    /** Tracks a state/page-view event with the given [name] and [properties] map. */
    fun trackState(name: String, properties: Map<String, Any?> = emptyMap()) {
        trackState(SimpleEvent(name, properties))
    }

    // ── Identity ────────────────────────────────────────────

    /** Identifies the current user. Propagates to all providers. */
    fun identify(userId: String, vararg traits: Pair<String, Any?>) {
        identify(userId, traits.toMap())
    }

    /** Identifies the current user with [userId] and [traits]. */
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        identityManager.identify(userId, traits)
        TrackFlowLogger.debug("Identified user: $userId")
    }

    /** Resets user identity and rotates the session. Call on logout. */
    fun resetIdentity() {
        identityManager.reset()
        sessionManager.reset()
        TrackFlowLogger.debug("Identity and session reset")
    }

    /** Returns the currently identified user ID, or null. */
    fun userId(): String? = identityManager.userId

    // ── Super Properties ────────────────────────────────────

    /** Sets super properties merged into every event. Event properties override on conflict. */
    fun setSuperProperties(vararg properties: Pair<String, Any?>) {
        synchronized(superPropertiesLock) { superProperties.putAll(properties) }
    }

    /** Sets super properties from a map. */
    fun setSuperProperties(properties: Map<String, Any?>) {
        synchronized(superPropertiesLock) { superProperties.putAll(properties) }
    }

    /** Removes a single super property by [key]. */
    fun removeSuperProperty(key: String) {
        synchronized(superPropertiesLock) { superProperties.remove(key) }
    }

    /** Clears all super properties. */
    fun clearSuperProperties() {
        synchronized(superPropertiesLock) { superProperties.clear() }
    }

    /** Returns a snapshot of current super properties. */
    fun superProperties(): Map<String, Any?> {
        synchronized(superPropertiesLock) { return superProperties.toMap() }
    }

    // ── Event Monitor ───────────────────────────────────────

    /**
     * Returns the live [EventMonitor] for observing event delivery in real time.
     *
     * Use with Compose:
     * ```
     * val records by TrackFlow.eventMonitor().records.collectAsState()
     * ```
     *
     * Or use the [com.trackflow.core.compose.TrackFlowDebugView] composable.
     */
    fun eventMonitor(): EventMonitor = _eventMonitor

    // ── Pipeline ────────────────────────────────────────────

    private fun enqueue(event: TrackFlowEvent, type: EventType) {
        if (!isInitialized) {
            TrackFlowLogger.error("TrackFlow called before initialize()")
            return
        }

        val mergedProperties = buildMap {
            synchronized(superPropertiesLock) { putAll(superProperties) }
            putAll(event.properties)
        }

        val contextMap = buildMap {
            putAll(contextProvider.context())
            put("session_id", sessionManager.session())
            identityManager.userId?.let { put("user_id", it) }
        }

        var payload: AnalyticsPayload? = AnalyticsPayload(
            eventName = event.name,
            properties = mergedProperties,
            providerExtras = event.providerExtras,
            context = contextMap,
            timestamp = System.currentTimeMillis(),
            type = type
        )

        payload = middlewares.applyAll(payload!!)
        if (payload == null) {
            TrackFlowLogger.debug("Event '${event.name}' dropped by middleware")
            _eventMonitor.recordDropped(event.name)
            return
        }

        debugSink.record(payload)
        batcher?.enqueue(payload)
    }

    // ── Lifecycle ───────────────────────────────────────────

    /** Forces an immediate flush of all buffered events. */
    fun flush() {
        if (!isInitialized) {
            TrackFlowLogger.error("TrackFlow.flush() called before initialize()")
            return
        }
        batcher?.flush()
    }

    /** Shuts down the pipeline. Flushes remaining events and stops all components. */
    fun shutdown() {
        if (!isInitialized) return
        lifecycleTracker?.unregister()
        lifecycleTracker = null
        batcher?.stop()
        dispatcher?.stop()
        isInitialized = false
        TrackFlowLogger.debug("TrackFlow shut down")
    }

    /** Returns all events processed through the pipeline for debugging. */
    fun debugEvents(): List<AnalyticsPayload> = debugSink.events()

    // ── Internal ────────────────────────────────────────────

    private class SimpleEvent(
        override val name: String,
        override val properties: Map<String, Any?>
    ) : TrackFlowEvent

    /**
     * Fluent builder for configuring and initializing [TrackFlow].
     *
     * @param context The Android application context.
     */
    class Builder(val context: Context) {

        internal val providers = mutableListOf<AnalyticsProvider>()
        internal val middlewares = mutableListOf<TrackFlowMiddleware>()
        internal val superProperties = mutableMapOf<String, Any?>()
        internal var batchSize: Int = 20
        internal var flushIntervalMs: Long = 30_000L
        internal var logLevel: LogLevel = LogLevel.ERROR
        internal var logListener: TrackFlowLogListener? = null
        internal var lifecycleTrackingEnabled: Boolean = false
        internal var deduplicationWindowMs: Long = 0L

        /** Registers an analytics provider. */
        fun addProvider(provider: AnalyticsProvider) = apply { providers += provider }

        /** Adds a middleware interceptor to the processing chain. */
        fun addMiddleware(middleware: TrackFlowMiddleware) = apply { middlewares += middleware }

        /** Sets initial super properties. */
        fun superProperties(vararg properties: Pair<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        /** Sets initial super properties from a map. */
        fun superProperties(properties: Map<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        /** Sets the batch size before auto-flush. Default 20. */
        fun batchSize(size: Int) = apply { batchSize = size }

        /** Sets the auto-flush interval in milliseconds. Default 30000ms. */
        fun flushInterval(ms: Long) = apply { flushIntervalMs = ms }

        /** Sets the internal log level. Default [LogLevel.ERROR]. */
        fun logLevel(level: LogLevel) = apply { logLevel = level }

        /** Sets a listener for external log capture. */
        fun logListener(listener: TrackFlowLogListener) = apply { logListener = listener }

        /**
         * Enables automatic lifecycle tracking via ProcessLifecycleOwner.
         *
         * Tracks: `app_opened` (first launch), `app_foregrounded`, `app_backgrounded`.
         * Auto-flushes on background.
         */
        fun enableLifecycleTracking() = apply { lifecycleTrackingEnabled = true }

        /**
         * Enables event deduplication within the given time window.
         *
         * Events with identical name, type, and properties within [windowMs]
         * are dropped. Protects against double-taps and retry-induced duplicates.
         *
         * @param windowMs Deduplication window in milliseconds. Default 1000ms.
         */
        fun enableDeduplication(windowMs: Long = 1_000L) = apply {
            deduplicationWindowMs = windowMs
        }

        /** Finalizes the builder. Pass to [TrackFlow.initialize]. */
        fun build(): Builder = this
    }
}

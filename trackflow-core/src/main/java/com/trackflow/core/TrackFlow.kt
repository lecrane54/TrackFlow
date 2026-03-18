package com.trackflow.core

import android.content.Context
import com.trackflow.core.context.DefaultContextProvider
import com.trackflow.core.debug.DebugEventSink
import com.trackflow.core.event.TrackFlowEvent
import com.trackflow.core.identity.IdentityListener
import com.trackflow.core.identity.IdentityManager
import com.trackflow.core.logging.LogLevel
import com.trackflow.core.logging.TrackFlowLogListener
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.middleware.TrackFlowMiddleware
import com.trackflow.core.middleware.applyAll
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.pipeline.EventBatcher
import com.trackflow.core.pipeline.EventDispatcher
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.session.SessionManager

/**
 * Main entry point for the TrackFlow analytics SDK.
 *
 * TrackFlow is a singleton that manages the complete analytics pipeline:
 * event ingestion, context enrichment, middleware processing, batching,
 * and distribution to multiple analytics providers.
 *
 * Must be initialized via [initialize] before any tracking calls.
 *
 * Usage:
 * ```
 * TrackFlow.initialize(
 *     TrackFlow.Builder(context)
 *         .addProvider(FirebaseProvider())
 *         .addProvider(MixpanelProvider("token"))
 *         .build()
 * )
 *
 * TrackFlow.track("button_clicked", "screen" to "home")
 * ```
 */
object TrackFlow {

    private lateinit var providers: List<AnalyticsProvider>
    private lateinit var contextProvider: DefaultContextProvider
    private val sessionManager = SessionManager()
    private val debugSink = DebugEventSink()
    private val identityManager = IdentityManager()

    private var batcher: EventBatcher? = null
    private var dispatcher: EventDispatcher? = null
    private var middlewares: List<TrackFlowMiddleware> = emptyList()

    private val superProperties = mutableMapOf<String, Any?>()
    private val superPropertiesLock = Any()

    @Volatile
    private var isInitialized = false

    /**
     * Initializes the TrackFlow SDK with the given [builder] configuration.
     *
     * This sets up all providers, the event pipeline (batcher + dispatcher),
     * middleware chain, super properties, logging, and identity listeners.
     * Must be called once before any [track], [trackState], or [identify] calls,
     * typically in `Application.onCreate()`.
     *
     * @param builder The configured [Builder] instance.
     */
    fun initialize(builder: Builder) {
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

        val eventDispatcher = EventDispatcher(
            context = builder.context,
            providers = providers
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

        isInitialized = true
        TrackFlowLogger.debug("TrackFlow initialized with ${providers.size} providers")
    }

    // ── Track Actions ───────────────────────────────────────

    /**
     * Tracks an action event using a [TrackFlowEvent] instance.
     *
     * Use this overload for typed events from the event catalog or custom implementations.
     *
     * @param event The event to track.
     */
    fun track(event: TrackFlowEvent) {
        enqueue(event, EventType.ACTION)
    }

    /**
     * Tracks an action event with the given [name] and optional property pairs.
     *
     * Convenience overload for inline event tracking.
     *
     * @param name The event name (e.g., "button_clicked").
     * @param properties Key-value pairs of event properties.
     */
    fun track(name: String, vararg properties: Pair<String, Any?>) {
        track(name, properties.toMap())
    }

    /**
     * Tracks an action event with the given [name] and [properties] map.
     *
     * @param name The event name (e.g., "purchase_completed").
     * @param properties Map of event properties. Defaults to empty.
     */
    fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        track(SimpleEvent(name, properties))
    }

    // ── Track State / Page Views ────────────────────────────

    /**
     * Tracks a state/page-view event using a [TrackFlowEvent] instance.
     *
     * State events are routed differently by providers that distinguish between
     * actions and page views (e.g., Adobe's `trackState` vs `trackAction`).
     *
     * @param event The state event to track.
     */
    fun trackState(event: TrackFlowEvent) {
        enqueue(event, EventType.STATE)
    }

    /**
     * Tracks a state/page-view event with the given [name] and optional property pairs.
     *
     * @param name The state/screen name (e.g., "home_screen").
     * @param properties Key-value pairs of event properties.
     */
    fun trackState(name: String, vararg properties: Pair<String, Any?>) {
        trackState(name, properties.toMap())
    }

    /**
     * Tracks a state/page-view event with the given [name] and [properties] map.
     *
     * @param name The state/screen name.
     * @param properties Map of event properties. Defaults to empty.
     */
    fun trackState(name: String, properties: Map<String, Any?> = emptyMap()) {
        trackState(SimpleEvent(name, properties))
    }

    // ── Identity ────────────────────────────────────────────

    /**
     * Identifies the current user with the given [userId] and optional trait pairs.
     *
     * Propagates to all providers that support user identification
     * (e.g., Firebase `setUserId`, Mixpanel `identify`, Amplitude `setUserId`).
     *
     * @param userId The unique user identifier.
     * @param traits Key-value pairs of user traits (e.g., "email", "plan").
     */
    fun identify(userId: String, vararg traits: Pair<String, Any?>) {
        identify(userId, traits.toMap())
    }

    /**
     * Identifies the current user with the given [userId] and [traits] map.
     *
     * @param userId The unique user identifier.
     * @param traits Map of user traits. Defaults to empty.
     */
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        identityManager.identify(userId, traits)
        TrackFlowLogger.debug("Identified user: $userId")
    }

    /**
     * Resets the current user identity and rotates the session.
     *
     * Call this on user logout. Propagates `reset()` to all providers
     * and generates a new session ID.
     */
    fun resetIdentity() {
        identityManager.reset()
        sessionManager.reset()
        TrackFlowLogger.debug("Identity and session reset")
    }

    /**
     * Returns the currently identified user ID, or null if no user is identified.
     *
     * @return The current user ID, or null.
     */
    fun userId(): String? = identityManager.userId

    // ── Super Properties ────────────────────────────────────

    /**
     * Sets global super properties that are automatically merged into every event.
     *
     * Event-level properties override super properties on key conflict.
     *
     * @param properties Key-value pairs to add as super properties.
     */
    fun setSuperProperties(vararg properties: Pair<String, Any?>) {
        synchronized(superPropertiesLock) {
            superProperties.putAll(properties)
        }
    }

    /**
     * Sets global super properties from a map.
     *
     * @param properties Map of properties to add.
     */
    fun setSuperProperties(properties: Map<String, Any?>) {
        synchronized(superPropertiesLock) {
            superProperties.putAll(properties)
        }
    }

    /**
     * Removes a single super property by [key].
     *
     * @param key The property key to remove.
     */
    fun removeSuperProperty(key: String) {
        synchronized(superPropertiesLock) {
            superProperties.remove(key)
        }
    }

    /**
     * Clears all super properties.
     */
    fun clearSuperProperties() {
        synchronized(superPropertiesLock) {
            superProperties.clear()
        }
    }

    /**
     * Returns a snapshot of the current super properties.
     *
     * @return An immutable copy of the super properties map.
     */
    fun superProperties(): Map<String, Any?> {
        synchronized(superPropertiesLock) {
            return superProperties.toMap()
        }
    }

    // ── Pipeline ────────────────────────────────────────────

    /**
     * Internal method that creates an [AnalyticsPayload] from the event,
     * merges super properties, enriches with context and identity,
     * runs middleware, records to the debug sink, and enqueues for batching.
     *
     * @param event The source event.
     * @param type Whether this is an ACTION or STATE event.
     */
    private fun enqueue(event: TrackFlowEvent, type: EventType) {
        if (!isInitialized) {
            TrackFlowLogger.error("TrackFlow called before initialize()")
            return
        }

        val mergedProperties = buildMap {
            synchronized(superPropertiesLock) {
                putAll(superProperties)
            }
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
            return
        }

        debugSink.record(payload)
        batcher?.enqueue(payload)
    }

    // ── Lifecycle ───────────────────────────────────────────

    /**
     * Forces an immediate flush of all buffered events to providers.
     *
     * Normally events are flushed automatically when the batch size is reached
     * or the flush interval timer expires. Call this to flush on demand
     * (e.g., before the app goes to background).
     */
    fun flush() {
        if (!isInitialized) {
            TrackFlowLogger.error("TrackFlow.flush() called before initialize()")
            return
        }
        batcher?.flush()
    }

    /**
     * Shuts down the TrackFlow pipeline.
     *
     * Flushes any remaining buffered events, stops the batcher timer,
     * stops the dispatcher, and unregisters the network monitor.
     * After shutdown, [track] and [trackState] calls will be no-ops.
     */
    fun shutdown() {
        if (!isInitialized) return
        batcher?.stop()
        dispatcher?.stop()
        isInitialized = false
        TrackFlowLogger.debug("TrackFlow shut down")
    }

    /**
     * Returns all events that have been processed through the pipeline.
     *
     * Useful for debugging, testing, and building event inspector tools.
     *
     * @return An immutable list of all recorded [AnalyticsPayload] instances.
     */
    fun debugEvents(): List<AnalyticsPayload> = debugSink.events()

    // ── Internal ────────────────────────────────────────────

    /** Internal event implementation for the string-based track() overloads. */
    private class SimpleEvent(
        override val name: String,
        override val properties: Map<String, Any?>
    ) : TrackFlowEvent

    /**
     * Fluent builder for configuring and initializing [TrackFlow].
     *
     * @param context The Android application context. Should be `applicationContext`
     *   to avoid memory leaks.
     */
    class Builder(val context: Context) {

        internal val providers = mutableListOf<AnalyticsProvider>()
        internal val middlewares = mutableListOf<TrackFlowMiddleware>()
        internal val superProperties = mutableMapOf<String, Any?>()
        internal var batchSize: Int = 20
        internal var flushIntervalMs: Long = 30_000L
        internal var logLevel: LogLevel = LogLevel.ERROR
        internal var logListener: TrackFlowLogListener? = null

        /**
         * Registers an analytics provider to receive events.
         *
         * @param provider The [AnalyticsProvider] implementation to add.
         * @return This builder for chaining.
         */
        fun addProvider(provider: AnalyticsProvider) = apply {
            providers += provider
        }

        /**
         * Adds a middleware interceptor to the processing chain.
         *
         * Middleware runs in the order added, before events reach the batcher.
         *
         * @param middleware The [TrackFlowMiddleware] to add.
         * @return This builder for chaining.
         */
        fun addMiddleware(middleware: TrackFlowMiddleware) = apply {
            middlewares += middleware
        }

        /**
         * Sets initial super properties that will be merged into every event.
         *
         * @param properties Key-value pairs to set as super properties.
         * @return This builder for chaining.
         */
        fun superProperties(vararg properties: Pair<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        /**
         * Sets initial super properties from a map.
         *
         * @param properties Map of super properties.
         * @return This builder for chaining.
         */
        fun superProperties(properties: Map<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        /**
         * Sets the maximum number of events per batch before auto-flush.
         *
         * @param size The batch size. Default is 20.
         * @return This builder for chaining.
         */
        fun batchSize(size: Int) = apply {
            batchSize = size
        }

        /**
         * Sets the timer-based auto-flush interval in milliseconds.
         *
         * @param ms The flush interval. Default is 30000ms (30 seconds).
         * @return This builder for chaining.
         */
        fun flushInterval(ms: Long) = apply {
            flushIntervalMs = ms
        }

        /**
         * Sets the internal log level for TrackFlow diagnostics.
         *
         * @param level The [LogLevel]. Default is [LogLevel.ERROR].
         * @return This builder for chaining.
         */
        fun logLevel(level: LogLevel) = apply {
            logLevel = level
        }

        /**
         * Sets an optional listener to capture TrackFlow log messages externally.
         *
         * @param listener The [TrackFlowLogListener] callback.
         * @return This builder for chaining.
         */
        fun logListener(listener: TrackFlowLogListener) = apply {
            logListener = listener
        }

        /**
         * Finalizes the builder configuration. Returns this builder instance
         * to be passed to [TrackFlow.initialize].
         *
         * @return This [Builder] instance.
         */
        fun build(): Builder = this
    }
}

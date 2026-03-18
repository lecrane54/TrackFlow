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

        // Wire identity manager to providers
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

    fun track(event: TrackFlowEvent) {
        enqueue(event, EventType.ACTION)
    }

    fun track(name: String, vararg properties: Pair<String, Any?>) {
        track(name, properties.toMap())
    }

    fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        track(SimpleEvent(name, properties))
    }

    // ── Track State / Page Views ────────────────────────────

    fun trackState(event: TrackFlowEvent) {
        enqueue(event, EventType.STATE)
    }

    fun trackState(name: String, vararg properties: Pair<String, Any?>) {
        trackState(name, properties.toMap())
    }

    fun trackState(name: String, properties: Map<String, Any?> = emptyMap()) {
        trackState(SimpleEvent(name, properties))
    }

    // ── Identity ────────────────────────────────────────────

    fun identify(userId: String, vararg traits: Pair<String, Any?>) {
        identify(userId, traits.toMap())
    }

    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        identityManager.identify(userId, traits)
        TrackFlowLogger.debug("Identified user: $userId")
    }

    fun resetIdentity() {
        identityManager.reset()
        sessionManager.reset()
        TrackFlowLogger.debug("Identity and session reset")
    }

    fun userId(): String? = identityManager.userId

    // ── Super Properties ────────────────────────────────────

    fun setSuperProperties(vararg properties: Pair<String, Any?>) {
        synchronized(superPropertiesLock) {
            superProperties.putAll(properties)
        }
    }

    fun setSuperProperties(properties: Map<String, Any?>) {
        synchronized(superPropertiesLock) {
            superProperties.putAll(properties)
        }
    }

    fun removeSuperProperty(key: String) {
        synchronized(superPropertiesLock) {
            superProperties.remove(key)
        }
    }

    fun clearSuperProperties() {
        synchronized(superPropertiesLock) {
            superProperties.clear()
        }
    }

    fun superProperties(): Map<String, Any?> {
        synchronized(superPropertiesLock) {
            return superProperties.toMap()
        }
    }

    // ── Pipeline ────────────────────────────────────────────

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

    fun flush() {
        if (!isInitialized) {
            TrackFlowLogger.error("TrackFlow.flush() called before initialize()")
            return
        }
        batcher?.flush()
    }

    fun shutdown() {
        if (!isInitialized) return
        batcher?.stop()
        dispatcher?.stop()
        isInitialized = false
        TrackFlowLogger.debug("TrackFlow shut down")
    }

    fun debugEvents(): List<AnalyticsPayload> = debugSink.events()

    // ── Internal ────────────────────────────────────────────

    private class SimpleEvent(
        override val name: String,
        override val properties: Map<String, Any?>
    ) : TrackFlowEvent

    class Builder(val context: Context) {

        internal val providers = mutableListOf<AnalyticsProvider>()
        internal val middlewares = mutableListOf<TrackFlowMiddleware>()
        internal val superProperties = mutableMapOf<String, Any?>()
        internal var batchSize: Int = 20
        internal var flushIntervalMs: Long = 30_000L
        internal var logLevel: LogLevel = LogLevel.ERROR
        internal var logListener: TrackFlowLogListener? = null

        fun addProvider(provider: AnalyticsProvider) = apply {
            providers += provider
        }

        fun addMiddleware(middleware: TrackFlowMiddleware) = apply {
            middlewares += middleware
        }

        fun superProperties(vararg properties: Pair<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        fun superProperties(properties: Map<String, Any?>) = apply {
            superProperties.putAll(properties)
        }

        fun batchSize(size: Int) = apply {
            batchSize = size
        }

        fun flushInterval(ms: Long) = apply {
            flushIntervalMs = ms
        }

        fun logLevel(level: LogLevel) = apply {
            logLevel = level
        }

        fun logListener(listener: TrackFlowLogListener) = apply {
            logListener = listener
        }

        fun build(): Builder = this
    }
}

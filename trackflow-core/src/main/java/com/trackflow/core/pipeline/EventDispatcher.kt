package com.trackflow.core.pipeline

import android.content.Context
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.provider.AnalyticsProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dispatches batches of analytics events to all registered [AnalyticsProvider] instances.
 *
 * The dispatcher handles online/offline awareness: when the device is online, events
 * are sent to each provider via their mapper and tracking methods with automatic
 * retry support. When offline, events are persisted to the [OfflineEventQueue] and
 * replayed once connectivity is restored.
 *
 * Events that fail delivery to at least one provider are also persisted for later retry.
 *
 * Lifecycle:
 * 1. Call [start] to register the network callback and drain any previously persisted events.
 * 2. Call [dispatch] to send event batches through the provider pipeline.
 * 3. Call [stop] to unregister callbacks and cancel the coroutine scope.
 *
 * @param context The Android [Context] used for network monitoring and offline queue persistence.
 * @param providers The list of [AnalyticsProvider] instances that receive dispatched events.
 * @param retryPolicy The [RetryPolicy] governing retry attempts for failed provider calls. Defaults to a standard policy.
 * @param dispatcher The [CoroutineDispatcher] for asynchronous operations. Defaults to [Dispatchers.IO].
 */
internal class EventDispatcher(
    context: Context,
    private val providers: List<AnalyticsProvider>,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** File-backed queue for persisting events when offline or when delivery fails. */
    private val offlineQueue = OfflineEventQueue(context)

    /** Monitors network connectivity and notifies on state changes. */
    private val networkMonitor = NetworkMonitor(context)

    /** The coroutine scope for async dispatch and offline-queue drain operations. */
    private var scope: CoroutineScope? = null

    /**
     * Starts the dispatcher by initializing the coroutine scope, registering the
     * network connectivity callback, and draining any events persisted from a
     * previous session.
     *
     * When connectivity is restored after being offline, the offline queue is
     * automatically drained and its events re-dispatched.
     */
    fun start() {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        networkMonitor.register()
        networkMonitor.onConnectivityChanged = { online ->
            if (online) {
                scope?.launch { drainOfflineQueue() }
            }
        }
        // Drain any events persisted from a previous session
        scope?.launch { drainOfflineQueue() }
    }

    /**
     * Dispatches a batch of analytics events to all registered providers.
     *
     * For each event in the [batch]:
     * - The event is mapped through the provider's mapper (using [mapState] for
     *   [EventType.STATE] events or [mapTrack] for all other types).
     * - If the mapper returns `null`, that provider is skipped for the event.
     * - The mapped event is sent to the provider via [RetryPolicy.execute].
     * - If any provider fails to accept the event (after retries), the event is
     *   persisted to the offline queue for later retry.
     *
     * If the device is currently offline, the entire batch is persisted immediately
     * without attempting delivery.
     *
     * @param batch The list of [AnalyticsPayload] events to dispatch.
     */
    suspend fun dispatch(batch: List<AnalyticsPayload>) {
        if (!networkMonitor.isOnline) {
            offlineQueue.persist(batch)
            TrackFlowLogger.debug("Offline: persisted ${batch.size} events")
            return
        }

        val failedEvents = mutableListOf<AnalyticsPayload>()

        for (payload in batch) {
            var anyProviderFailed = false
            for (provider in providers) {
                try {
                    val isState = payload.type == EventType.STATE
                    val mapped = if (isState) {
                        provider.mapper.mapState(payload)
                    } else {
                        provider.mapper.mapTrack(payload)
                    } ?: continue
                    val result = retryPolicy.execute {
                        if (isState) provider.trackState(mapped) else provider.track(mapped)
                    }
                    if (result.isFailure) {
                        TrackFlowLogger.error(
                            "Failed to deliver to ${provider.key}: ${result.exceptionOrNull()?.message}",
                            result.exceptionOrNull()
                        )
                        anyProviderFailed = true
                    }
                } catch (e: Exception) {
                    TrackFlowLogger.error("Unexpected error dispatching to ${provider.key}", e)
                    anyProviderFailed = true
                }
            }
            if (anyProviderFailed) {
                failedEvents.add(payload)
            }
        }

        if (failedEvents.isNotEmpty()) {
            offlineQueue.persist(failedEvents)
            TrackFlowLogger.debug("Persisted ${failedEvents.size} failed events for retry")
        }
    }

    /**
     * Stops the dispatcher by unregistering the network callback and cancelling the
     * coroutine scope.
     *
     * Any in-flight dispatch operations may be cancelled. Persisted offline events
     * will be retained and drained on the next [start].
     */
    fun stop() {
        networkMonitor.unregister()
        scope?.cancel()
        scope = null
    }

    /**
     * Reads all events from the offline queue and re-dispatches them.
     *
     * This is called automatically on [start] and whenever network connectivity
     * is restored.
     */
    private suspend fun drainOfflineQueue() {
        val events = offlineQueue.drain()
        if (events.isNotEmpty()) {
            TrackFlowLogger.debug("Draining ${events.size} offline events")
            dispatch(events)
        }
    }
}

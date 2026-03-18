package com.trackflow.core.pipeline

import android.content.Context
import com.trackflow.core.debug.EventMonitor
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.payload.EventType
import com.trackflow.core.provider.AnalyticsProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Dispatches batches of analytics events to all registered [AnalyticsProvider] instances.
 *
 * Handles online/offline awareness, retry with backoff, offline persistence,
 * event deduplication, and live delivery monitoring.
 *
 * @param context Android context for network monitoring and offline queue.
 * @param providers The list of providers that receive dispatched events.
 * @param retryPolicy Retry policy for failed provider calls.
 * @param deduplicator Optional event deduplicator.
 * @param eventMonitor Optional live event monitor for delivery tracking.
 * @param dispatcher Coroutine dispatcher for async operations.
 */
internal class EventDispatcher(
    context: Context,
    private val providers: List<AnalyticsProvider>,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val deduplicator: EventDeduplicator? = null,
    private val eventMonitor: EventMonitor? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val offlineQueue = OfflineEventQueue(context)
    private val networkMonitor = NetworkMonitor(context)
    private var scope: CoroutineScope? = null

    fun start() {
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        networkMonitor.register()
        networkMonitor.onConnectivityChanged = { online ->
            if (online) {
                scope?.launch { drainOfflineQueue() }
            }
        }
        scope?.launch { drainOfflineQueue() }
    }

    suspend fun dispatch(batch: List<AnalyticsPayload>) {
        if (!networkMonitor.isOnline) {
            offlineQueue.persist(batch)
            TrackFlowLogger.debug("Offline: persisted ${batch.size} events")
            batch.forEach { eventMonitor?.recordOffline(it) }
            return
        }

        val failedEvents = mutableListOf<AnalyticsPayload>()

        for (payload in batch) {
            // Deduplication check
            if (deduplicator != null && !deduplicator.shouldDispatch(payload)) {
                TrackFlowLogger.debug("Dedup: dropped duplicate '${payload.eventName}'")
                continue
            }

            // Dispatch to all providers in parallel
            val results = coroutineScope {
                providers.map { provider ->
                    async {
                        try {
                            val isState = payload.type == EventType.STATE
                            val mapped = if (isState) {
                                provider.mapper.mapState(payload)
                            } else {
                                provider.mapper.mapTrack(payload)
                            } ?: return@async true // skipped = not a failure

                            val result = retryPolicy.execute {
                                if (isState) provider.trackState(mapped) else provider.track(mapped)
                            }
                            if (result.isFailure) {
                                val errorMsg = result.exceptionOrNull()?.message
                                TrackFlowLogger.error(
                                    "Failed to deliver to ${provider.key}: $errorMsg",
                                    result.exceptionOrNull()
                                )
                                eventMonitor?.recordFailure(payload, provider.key, mapped, errorMsg)
                                false
                            } else {
                                eventMonitor?.recordDelivery(payload, provider.key, mapped)
                                true
                            }
                        } catch (e: Exception) {
                            TrackFlowLogger.error("Unexpected error dispatching to ${provider.key}", e)
                            eventMonitor?.recordFailure(payload, provider.key, null, e.message)
                            false
                        }
                    }
                }.awaitAll()
            }
            if (results.any { !it }) {
                failedEvents.add(payload)
            }
        }

        if (failedEvents.isNotEmpty()) {
            offlineQueue.persist(failedEvents)
            TrackFlowLogger.debug("Persisted ${failedEvents.size} failed events for retry")
        }
    }

    fun stop() {
        networkMonitor.unregister()
        scope?.cancel()
        scope = null
    }

    private suspend fun drainOfflineQueue() {
        val events = offlineQueue.drain()
        if (events.isNotEmpty()) {
            TrackFlowLogger.debug("Draining ${events.size} offline events")
            dispatch(events)
        }
    }
}

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

internal class EventDispatcher(
    context: Context,
    private val providers: List<AnalyticsProvider>,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
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
        // Drain any events persisted from a previous session
        scope?.launch { drainOfflineQueue() }
    }

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

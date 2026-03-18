package com.trackflow.provider.adobe.analytics

import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.Analytics
import com.adobe.marketing.mobile.Identity
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Signal
import com.adobe.marketing.mobile.UserProfile
import com.adobe.marketing.mobile.edge.bridge.EdgeBridge
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * Adobe Analytics provider using the legacy Analytics extension with Edge Bridge.
 *
 * This provider uses [MobileCore.trackAction] and [MobileCore.trackState] under the hood,
 * with the Edge Bridge extension forwarding hits to the Edge Network for backward compatibility.
 *
 * Use this provider when migrating from legacy Adobe Analytics to Edge Network,
 * or when your Adobe report suites are configured with processing rules that
 * expect traditional context data keys.
 *
 * During initialisation the following Adobe Mobile SDK extensions are registered:
 * - [Analytics] -- legacy analytics hit collection
 * - [Identity] -- visitor ID management
 * - [Lifecycle] -- application lifecycle metrics
 * - [Signal] -- postback / PII rules
 * - [UserProfile] -- client-side user profile attributes
 * - [EdgeBridge] -- bridges legacy `trackAction`/`trackState` calls to Edge Network
 *
 * @property appId The Adobe Experience Platform environment file ID used to configure
 *   the Mobile SDK via [MobileCore.configureWithAppID].
 * @property keyPrefix An optional prefix that is prepended to every context-data key
 *   before the event is sent to Adobe. Pass `null` (the default) to disable prefixing.
 * @property keyMap An optional mapping of original property keys to replacement keys.
 *   Remapping is applied before [keyPrefix]. Pass `null` (the default) to skip remapping.
 */
class AdobeAnalyticsProvider(
    private val appId: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    /**
     * Unique identifier for this provider, used by the TrackFlow router to
     * look up and reference the Adobe Analytics provider instance.
     */
    override val key = "adobe-analytics"

    /**
     * Event mapper that transforms a generic [AnalyticsPayload] into an
     * Adobe Analytics-compatible [ProviderEvent].
     *
     * Both [mapTrack] and [mapState] convert all property values to strings
     * (matching Adobe context-data requirements) and apply [keyMap] remapping
     * followed by [keyPrefix].
     */
    override val mapper = object : ProviderEventMapper {
        /**
         * Maps an [AnalyticsPayload] to a [ProviderEvent] suitable for
         * [MobileCore.trackAction].
         *
         * All property values are converted to strings via [Any.toString].
         * An additional `"a.action"` key is inserted into the context data with
         * the original event name, following Adobe's convention for action tracking.
         *
         * @param payload The incoming analytics payload.
         * @return A [ProviderEvent] whose [ProviderEvent.properties] map contains
         *   string context-data entries ready for [MobileCore.trackAction].
         */
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (key, value) ->
                contextData[key] = value?.toString()
            }
            contextData["a.action"] = payload.eventName
            return ProviderEvent(payload.eventName, contextData)
        }

        /**
         * Maps an [AnalyticsPayload] to a [ProviderEvent] suitable for
         * [MobileCore.trackState].
         *
         * All property values are converted to strings via [Any.toString].
         * Unlike [mapTrack], no `"a.action"` key is added because state tracking
         * represents a screen view rather than a discrete user action.
         *
         * @param payload The incoming analytics payload representing a screen or state view.
         * @return A [ProviderEvent] whose [ProviderEvent.properties] map contains
         *   string context-data entries ready for [MobileCore.trackState].
         */
        override fun mapState(payload: AnalyticsPayload): ProviderEvent {
            val contextData = mutableMapOf<String, Any?>()
            payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix).forEach { (key, value) ->
                contextData[key] = value?.toString()
            }
            return ProviderEvent(payload.eventName, contextData)
        }
    }

    /**
     * Initialises the Adobe Mobile SDK and registers all required extensions.
     *
     * The SDK is configured with [appId] after extensions are registered. Logging is
     * set to [LoggingMode.VERBOSE] to aid debugging during development. The application
     * context is extracted from [context] and passed to [MobileCore.setApplication].
     *
     * @param context The Android [Context] from which the [Application] instance is obtained.
     *   Must be an application-level context or a context whose [Context.getApplicationContext]
     *   returns an [Application].
     */
    override fun initialize(context: Context) {
        try {
            val application = context.applicationContext as Application
            MobileCore.setApplication(application)
            MobileCore.setLogLevel(LoggingMode.VERBOSE)
            MobileCore.registerExtensions(
                listOf(
                    Analytics.EXTENSION,
                    Identity.EXTENSION,
                    Lifecycle.EXTENSION,
                    Signal.EXTENSION,
                    UserProfile.EXTENSION,
                    EdgeBridge.EXTENSION
                )
            ) {
                MobileCore.configureWithAppID(appId)
            }
            TrackFlowLogger.debug("Adobe Analytics provider initialized with appId: $appId")
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics init failed", e)
        }
    }

    /**
     * Sends a tracked action event to Adobe Analytics via [MobileCore.trackAction].
     *
     * The [ProviderEvent.properties] are filtered to remove `null` values and all
     * remaining values are converted to strings, as required by the Adobe context-data API.
     *
     * @param event The [ProviderEvent] containing the action name and context-data properties.
     */
    override fun track(event: ProviderEvent) {
        try {
            val contextData = event.properties
                .filterValues { it != null }
                .mapValues { it.value.toString() }

            MobileCore.trackAction(event.name, contextData)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics trackAction failed for ${event.name}", e)
        }
    }

    /**
     * Sends a tracked state (screen view) event to Adobe Analytics via
     * [MobileCore.trackState].
     *
     * The [ProviderEvent.properties] are filtered to remove `null` values and all
     * remaining values are converted to strings, as required by the Adobe context-data API.
     *
     * @param event The [ProviderEvent] containing the state/screen name and context-data properties.
     */
    override fun trackState(event: ProviderEvent) {
        try {
            val contextData = event.properties
                .filterValues { it != null }
                .mapValues { it.value.toString() }

            MobileCore.trackState(event.name, contextData)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Analytics trackState failed for ${event.name}", e)
        }
    }
}

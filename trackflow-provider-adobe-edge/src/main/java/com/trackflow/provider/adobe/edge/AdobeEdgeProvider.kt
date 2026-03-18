package com.trackflow.provider.adobe.edge

import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Signal
import com.adobe.marketing.mobile.UserProfile
import com.adobe.marketing.mobile.edge.identity.Identity
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * Adobe Experience Platform Edge provider for Customer Journey Analytics (CJA).
 *
 * This provider sends events as XDM Experience Events via the Edge Network.
 * Use this for modern Adobe implementations where data flows through
 * XDM schemas and datastreams configured in Adobe Experience Platform.
 *
 * Unlike [com.trackflow.provider.adobe.analytics.AdobeAnalyticsProvider], which
 * relies on the legacy `trackAction`/`trackState` API, this provider constructs
 * [ExperienceEvent] instances with XDM data and dispatches them through
 * [Edge.sendEvent]. This is the recommended approach for new Adobe Experience
 * Platform integrations.
 *
 * During initialisation the following Adobe Mobile SDK extensions are registered:
 * - [Edge] -- Experience Platform Edge Network communication
 * - [Identity] -- Edge identity service (ECID management)
 * - [Lifecycle] -- application lifecycle metrics
 * - [Signal] -- postback / PII rules
 * - [UserProfile] -- client-side user profile attributes
 *
 * @property appId The Adobe Experience Platform environment file ID used to configure
 *   the Mobile SDK via [MobileCore.configureWithAppID].
 * @property datasetId An optional dataset ID for routing events to a specific dataset
 *   within the Adobe Experience Platform. When set, it overrides the default datastream
 *   dataset mapping via [ExperienceEvent.Builder.setDatastreamIdOverride]. Pass `null`
 *   (the default) to use the datastream's default routing.
 * @property keyPrefix An optional prefix that is prepended to every XDM property key
 *   before the event is sent. Pass `null` (the default) to disable prefixing.
 * @property keyMap An optional mapping of original property keys to replacement keys.
 *   Remapping is applied before [keyPrefix]. Pass `null` (the default) to skip remapping.
 */
class AdobeEdgeProvider(
    private val appId: String,
    private val datasetId: String? = null,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    /**
     * Unique identifier for this provider, used by the TrackFlow router to
     * look up and reference the Adobe Edge provider instance.
     */
    override val key = "adobe-edge"

    /**
     * Event mapper that transforms a generic [AnalyticsPayload] into an
     * Edge-compatible [ProviderEvent].
     *
     * The mapper applies [keyMap] remapping followed by [keyPrefix] to all
     * property keys. The event name is passed through unmodified.
     */
    override val mapper = object : ProviderEventMapper {
        /**
         * Maps an [AnalyticsPayload] to a [ProviderEvent] for Edge dispatch.
         *
         * Property keys are remapped via [keyMap] and then prefixed with [keyPrefix].
         * The event name is preserved as-is from the payload.
         *
         * @param payload The incoming analytics payload.
         * @return A [ProviderEvent] with transformed property keys ready for XDM serialisation.
         */
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(payload.eventName, payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix))
        }
    }

    /**
     * Initialises the Adobe Mobile SDK and registers all required Edge extensions.
     *
     * The SDK is configured with [appId] after extensions are registered. The application
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
            MobileCore.registerExtensions(
                listOf(
                    Edge.EXTENSION,
                    Identity.EXTENSION,
                    Lifecycle.EXTENSION,
                    Signal.EXTENSION,
                    UserProfile.EXTENSION
                )
            ) {
                MobileCore.configureWithAppID(appId)
            }
            TrackFlowLogger.debug("Adobe Edge provider initialized with appId: $appId")
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Edge init failed", e)
        }
    }

    /**
     * Sends a tracked action event to the Adobe Edge Network.
     *
     * The event is dispatched as an XDM Experience Event with the event type
     * `"analytics.action"`, which Adobe maps to an action hit.
     *
     * @param event The [ProviderEvent] containing the action name and properties.
     */
    override fun track(event: ProviderEvent) {
        sendEdgeEvent(event, "analytics.action")
    }

    /**
     * Sends a tracked state (screen view) event to the Adobe Edge Network.
     *
     * The event is dispatched as an XDM Experience Event with the event type
     * `"web.webpagedetails.pageViews"`, which Adobe maps to a page-view hit
     * in Customer Journey Analytics.
     *
     * @param event The [ProviderEvent] containing the state/screen name and properties.
     */
    override fun trackState(event: ProviderEvent) {
        sendEdgeEvent(event, "web.webpagedetails.pageViews")
    }

    /**
     * Constructs and sends an XDM [ExperienceEvent] to the Adobe Edge Network.
     *
     * The XDM schema map is populated with:
     * - `"eventType"` -- the [eventType] string that categorises the hit
     * - `"name"` -- the event name from [event]
     * - All non-null properties from [ProviderEvent.properties]
     *
     * If [datasetId] is configured, it is applied as a datastream ID override so
     * that the event is routed to the specified dataset instead of the datastream's
     * default target.
     *
     * @param event The [ProviderEvent] containing the event name and properties.
     * @param eventType The XDM event type string (e.g., `"analytics.action"` or
     *   `"web.webpagedetails.pageViews"`).
     */
    private fun sendEdgeEvent(event: ProviderEvent, eventType: String) {
        try {
            val xdmData = mutableMapOf<String, Any>(
                "eventType" to eventType,
                "name" to event.name
            )
            event.properties.forEach { (key, value) ->
                if (value != null) {
                    xdmData[key] = value
                }
            }

            val builder = ExperienceEvent.Builder()
                .setXdmSchema(xdmData)

            if (datasetId != null) {
                builder.setDatastreamIdOverride(datasetId)
            }

            Edge.sendEvent(builder.build(), null)
        } catch (e: Exception) {
            TrackFlowLogger.error("Adobe Edge send failed for ${event.name}", e)
        }
    }
}

package com.trackflow.provider.mixpanel

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys
import org.json.JSONObject

/**
 * TrackFlow analytics provider that wraps the Mixpanel Android SDK.
 *
 * This provider forwards tracked events to [MixpanelAPI] by serialising event
 * properties into a [JSONObject] and calling [MixpanelAPI.track]. User
 * identification is handled via [MixpanelAPI.identify], and user profile
 * attributes are managed through the [MixpanelAPI.People] interface.
 *
 * A `"source": "trackflow"` property is automatically appended to every tracked
 * event by the [mapper], allowing downstream analysis to distinguish events
 * originating from TrackFlow.
 *
 * Mixpanel is initialised with opt-out tracking disabled (third parameter `false`
 * in [MixpanelAPI.getInstance]), meaning users are tracked by default. Adjust
 * this behaviour via Mixpanel's own opt-out API if GDPR/privacy controls are needed.
 *
 * @property token The Mixpanel project token used to authenticate events.
 * @property keyPrefix An optional prefix that is prepended to every property key
 *   before the event is sent to Mixpanel. For example, a prefix of `"tf_"` would
 *   turn a key `"screen"` into `"tf_screen"`. Pass `null` (the default) to disable
 *   prefixing.
 * @property keyMap An optional mapping of original property keys to replacement keys.
 *   Remapping is applied before [keyPrefix]. Pass `null` (the default) to skip remapping.
 */
class MixpanelProvider(
    private val token: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    /**
     * Unique identifier for this provider, used by the TrackFlow router to
     * look up and reference the Mixpanel provider instance.
     */
    override val key = "mixpanel"

    /**
     * Lazily-initialised [MixpanelAPI] instance. This is `null` until
     * [initialize] is called successfully.
     */
    private var mixpanel: MixpanelAPI? = null

    /**
     * Event mapper that transforms a generic [AnalyticsPayload] into a
     * Mixpanel-compatible [ProviderEvent].
     *
     * The mapper applies [keyMap] remapping followed by [keyPrefix] to all
     * property keys, then appends a `"source" to "trackflow"` entry to the
     * properties map for attribution purposes.
     */
    override val mapper = object : ProviderEventMapper {
        /**
         * Maps an [AnalyticsPayload] to a Mixpanel-compatible [ProviderEvent].
         *
         * Property keys are remapped via [keyMap], prefixed with [keyPrefix], and
         * augmented with an additional `"source": "trackflow"` entry.
         *
         * @param payload The incoming analytics payload containing the raw event name and properties.
         * @return A [ProviderEvent] with transformed properties and the `"source"` attribution key.
         */
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix) + mapOf("source" to "trackflow")
            )
        }
    }

    /**
     * Initialises the Mixpanel SDK by obtaining a [MixpanelAPI] instance for
     * the configured [token].
     *
     * The SDK is initialised with automatic opt-out tracking disabled (`false`),
     * meaning users are tracked by default. This must be called before [track],
     * [identify], or [reset]. If initialisation fails, the error is logged and
     * subsequent calls to [track] will emit warnings rather than crash.
     *
     * @param context The Android [Context] used to create the [MixpanelAPI] instance.
     *   Typically the application context.
     */
    override fun initialize(context: Context) {
        try {
            mixpanel = MixpanelAPI.getInstance(context, token, false)
            TrackFlowLogger.debug("Mixpanel provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel init failed", e)
        }
    }

    /**
     * Sends a tracked event to Mixpanel.
     *
     * Event properties are serialised into a [JSONObject]. Null values are
     * represented as [JSONObject.NULL] to preserve the key in the Mixpanel payload.
     *
     * If [mixpanel] has not been initialised, a warning is logged and the event
     * is dropped.
     *
     * @param event The [ProviderEvent] containing the event name and properties to track.
     */
    override fun track(event: ProviderEvent) {
        try {
            val jsonProps = JSONObject().apply {
                event.properties.forEach { (key, value) ->
                    put(key, value ?: JSONObject.NULL)
                }
            }
            mixpanel?.track(event.name, jsonProps)
                ?: TrackFlowLogger.warn("Mixpanel not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel track failed for ${event.name}", e)
        }
    }

    /**
     * Associates the current device with a specific user and sets user profile
     * properties in Mixpanel.
     *
     * The [userId] is set via [MixpanelAPI.identify], which links the anonymous
     * device profile to the identified user. Each entry in [traits] is set on
     * the user's [MixpanelAPI.People] profile via [MixpanelAPI.People.set].
     *
     * If [traits] is empty, only the user identification is performed and no
     * profile properties are updated.
     *
     * @param userId The unique identifier for the user (typically a database ID or email).
     * @param traits A map of user profile attribute names to their values. Values may
     *   be any type supported by Mixpanel's People profile API.
     */
    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            mixpanel?.identify(userId)
            if (traits.isNotEmpty()) {
                val people = mixpanel?.people
                traits.forEach { (key, value) ->
                    people?.set(key, value)
                }
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel identify failed", e)
        }
    }

    /**
     * Resets the Mixpanel instance, clearing the current user's identity and
     * generating a new anonymous distinct ID.
     *
     * This is typically called when a user logs out, so that subsequent events
     * are no longer associated with the previous user. Internally delegates to
     * [MixpanelAPI.reset].
     */
    override fun reset() {
        try {
            mixpanel?.reset()
        } catch (e: Exception) {
            TrackFlowLogger.error("Mixpanel reset failed", e)
        }
    }
}

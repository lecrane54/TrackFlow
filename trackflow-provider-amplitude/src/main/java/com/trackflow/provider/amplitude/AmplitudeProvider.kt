package com.trackflow.provider.amplitude

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.events.Identify
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.prefixKeys
import com.trackflow.core.util.remapKeys

/**
 * TrackFlow analytics provider that wraps the Amplitude Kotlin Android SDK.
 *
 * This provider forwards tracked events to [Amplitude] using the modern Kotlin SDK.
 * Events are sent via [Amplitude.track], user identification is handled through
 * [Amplitude.setUserId] combined with [Amplitude.identify] for user properties,
 * and session reset is performed via [Amplitude.reset].
 *
 * The Amplitude SDK is initialised with a [Configuration] that receives the
 * project [apiKey] and the application context. No additional configuration
 * (such as server URL or flush policies) is applied by this provider; use
 * Amplitude's own configuration API if further customisation is needed.
 *
 * @property apiKey The Amplitude project API key used to authenticate and route events.
 * @property keyPrefix An optional prefix that is prepended to every property key
 *   before the event is sent to Amplitude. For example, a prefix of `"tf_"` would
 *   turn a key `"screen"` into `"tf_screen"`. Pass `null` (the default) to disable
 *   prefixing.
 * @property keyMap An optional mapping of original property keys to replacement keys.
 *   Remapping is applied before [keyPrefix]. For example,
 *   `mapOf("product_id" to "Product ID")` would rename the `"product_id"` key to
 *   `"Product ID"` in every event. Pass `null` (the default) to skip remapping.
 */
class AmplitudeProvider(
    private val apiKey: String,
    private val keyPrefix: String? = null,
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    /**
     * Unique identifier for this provider, used by the TrackFlow router to
     * look up and reference the Amplitude provider instance.
     */
    override val key = "amplitude"

    /**
     * Lazily-initialised [Amplitude] client instance. This is `null` until
     * [initialize] is called successfully.
     */
    private var amplitude: Amplitude? = null

    /**
     * Event mapper that transforms a generic [AnalyticsPayload] into an
     * Amplitude-compatible [ProviderEvent].
     *
     * The mapper applies [keyMap] remapping followed by [keyPrefix] to all
     * property keys. The event name is passed through unmodified.
     */
    override val mapper = object : ProviderEventMapper {
        /**
         * Maps an [AnalyticsPayload] to an Amplitude-compatible [ProviderEvent].
         *
         * Property keys are remapped via [keyMap] and then prefixed with [keyPrefix].
         * The event name is preserved as-is from the payload.
         *
         * @param payload The incoming analytics payload containing the raw event name and properties.
         * @return A [ProviderEvent] with transformed property keys ready for the Amplitude SDK.
         */
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(
                name = payload.eventName,
                properties = payload.properties.remapKeys(keyMap).prefixKeys(keyPrefix)
            )
        }
    }

    /**
     * Initialises the Amplitude SDK by creating an [Amplitude] client with the
     * configured [apiKey] and the application context derived from [context].
     *
     * This must be called before [track], [identify], or [reset]. If initialisation
     * fails, the error is logged and subsequent calls to [track] will emit warnings
     * rather than crash.
     *
     * @param context The Android [Context] whose [Context.getApplicationContext] is
     *   passed to the Amplitude [Configuration]. Typically the application context.
     */
    override fun initialize(context: Context) {
        try {
            amplitude = Amplitude(
                Configuration(
                    apiKey = apiKey,
                    context = context.applicationContext
                )
            )
            TrackFlowLogger.debug("Amplitude provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude init failed", e)
        }
    }

    /**
     * Sends a tracked event to Amplitude.
     *
     * The event name and property map are passed directly to [Amplitude.track].
     * The Amplitude SDK accepts `Map<String, Any?>` natively, so no additional
     * serialisation is required.
     *
     * If [amplitude] has not been initialised, a warning is logged and the event
     * is dropped.
     *
     * @param event The [ProviderEvent] containing the event name and properties to track.
     */
    override fun track(event: ProviderEvent) {
        try {
            amplitude?.track(event.name, event.properties)
                ?: TrackFlowLogger.warn("Amplitude not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude track failed for ${event.name}", e)
        }
    }

    /**
     * Associates the current device with a specific user and sets user properties
     * in Amplitude.
     *
     * The [userId] is set via [Amplitude.setUserId]. Each entry in [traits] is
     * added to an [Identify] object using type-aware overloads:
     * - [String], [Int], [Long], [Double], [Float], and [Boolean] values use their
     *   respective typed [Identify.set] overloads.
     * - `null` values trigger [Identify.unset], which removes the property from
     *   the user profile.
     * - All other types are converted to [String] via [Any.toString].
     *
     * The [Identify] object is sent to Amplitude only if [traits] is non-empty.
     *
     * @param userId The unique identifier for the user.
     * @param traits A map of user property names to their values. See above for
     *   type handling details.
     */
    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            amplitude?.setUserId(userId)
            if (traits.isNotEmpty()) {
                val identify = Identify()
                traits.forEach { (key, value) ->
                    when (value) {
                        is String -> identify.set(key, value)
                        is Int -> identify.set(key, value)
                        is Long -> identify.set(key, value)
                        is Double -> identify.set(key, value)
                        is Float -> identify.set(key, value)
                        is Boolean -> identify.set(key, value)
                        null -> identify.unset(key)
                        else -> identify.set(key, value.toString())
                    }
                }
                amplitude?.identify(identify)
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude identify failed", e)
        }
    }

    /**
     * Resets the Amplitude instance, clearing the current user ID and
     * generating a new anonymous device ID.
     *
     * This is typically called when a user logs out, so that subsequent events
     * are no longer associated with the previous user. Internally delegates to
     * [Amplitude.reset].
     */
    override fun reset() {
        try {
            amplitude?.reset()
        } catch (e: Exception) {
            TrackFlowLogger.error("Amplitude reset failed", e)
        }
    }
}

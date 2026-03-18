package com.trackflow.provider.firebase

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.annotation.RequiresPermission
import com.google.firebase.analytics.FirebaseAnalytics
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.payload.AnalyticsPayload
import com.trackflow.core.provider.AnalyticsProvider
import com.trackflow.core.provider.ProviderEvent
import com.trackflow.core.provider.ProviderEventMapper
import com.trackflow.core.util.remapKeys

/**
 * TrackFlow analytics provider that wraps the Google Firebase Analytics SDK.
 *
 * This provider forwards tracked events to [FirebaseAnalytics] by converting event
 * properties into an Android [Bundle] and calling [FirebaseAnalytics.logEvent]. User
 * identification is handled via [FirebaseAnalytics.setUserId] and
 * [FirebaseAnalytics.setUserProperty].
 *
 * Firebase imposes constraints on event names: they must contain only alphanumeric
 * characters and underscores, and must not exceed 40 characters. The [mapper]
 * automatically sanitises incoming event names to satisfy these requirements.
 *
 * Firebase Analytics is configured through the `google-services.json` file bundled
 * with the application, so no explicit app ID is required at construction time.
 *
 * @property keyMap An optional mapping of original property keys to replacement keys.
 *   For example, `mapOf("product_id" to "item_id")` would rename the `"product_id"` key
 *   to `"item_id"` in every event. Pass `null` (the default) to skip remapping.
 */
class FirebaseProvider(
    private val keyMap: Map<String, String>? = null
) : AnalyticsProvider {

    /**
     * Unique identifier for this provider, used by the TrackFlow router to
     * look up and reference the Firebase provider instance.
     */
    override val key: String = "firebase"

    /**
     * Lazily-initialised [FirebaseAnalytics] instance. This is `null` until
     * [initialize] is called successfully.
     */
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Event mapper that transforms a generic [AnalyticsPayload] into a
     * Firebase-compatible [ProviderEvent].
     *
     * The mapper performs the following transformations on every tracked event:
     * 1. Replaces characters that are invalid in Firebase event names (anything other
     *    than `[a-zA-Z0-9_]`) with underscores.
     * 2. Truncates the event name to a maximum of 40 characters.
     * 3. Applies [keyMap] remapping to property keys.
     */
    override val mapper = object : ProviderEventMapper {
        /**
         * Maps an [AnalyticsPayload] to a Firebase-compatible [ProviderEvent].
         *
         * @param payload The incoming analytics payload containing the raw event name and properties.
         * @return A [ProviderEvent] with a sanitised event name and transformed property keys.
         */
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            val sanitizedName = payload.eventName
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .take(40)
            return ProviderEvent(
                name = sanitizedName,
                properties = payload.properties.remapKeys(keyMap)
            )
        }
    }

    /**
     * Initialises the Firebase Analytics SDK by obtaining a [FirebaseAnalytics] instance
     * from the given [context].
     *
     * This must be called before [track], [identify], or [reset]. If initialisation
     * fails (for example, because `google-services.json` is missing), the error is
     * logged and subsequent calls to [track] will emit warnings rather than crash.
     *
     * @param context The Android [Context] used to obtain the [FirebaseAnalytics] singleton.
     *   Typically the application context.
     */
    @RequiresPermission(allOf = [Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.WAKE_LOCK])
    override fun initialize(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            TrackFlowLogger.debug("Firebase provider initialized")
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase init failed", e)
        }
    }

    /**
     * Sends a tracked event to Firebase Analytics.
     *
     * Event properties are packed into a [Bundle] with type-aware conversions:
     * - [String] values use [Bundle.putString]
     * - [Int] values use [Bundle.putInt]
     * - [Long] values use [Bundle.putLong]
     * - [Double] values use [Bundle.putDouble]
     * - [Float] values use [Bundle.putFloat]
     * - [Boolean] values use [Bundle.putBoolean]
     * - `null` values are silently skipped
     * - All other types are converted to [String] via [Any.toString]
     *
     * If [firebaseAnalytics] has not been initialised, a warning is logged and the
     * event is dropped.
     *
     * @param event The [ProviderEvent] containing the event name and properties to log.
     */
    override fun track(event: ProviderEvent) {
        try {
            val bundle = Bundle().apply {
                event.properties.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        null -> { /* skip nulls */ }
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(event.name, bundle)
                ?: TrackFlowLogger.warn("Firebase not initialized, dropping event: ${event.name}")
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase track failed for ${event.name}", e)
        }
    }

    /**
     * Associates the current device with a specific user and sets user properties
     * in Firebase Analytics.
     *
     * The [userId] is set via [FirebaseAnalytics.setUserId]. Each entry in [traits]
     * is set as a user property via [FirebaseAnalytics.setUserProperty], with values
     * converted to strings. Null trait values are passed through, which clears the
     * corresponding user property in Firebase.
     *
     * @param userId The unique identifier for the user.
     * @param traits A map of user attribute names to their values. Values are
     *   converted to [String] using [Any.toString]; `null` values clear the property.
     */
    override fun identify(userId: String, traits: Map<String, Any?>) {
        try {
            firebaseAnalytics?.setUserId(userId)
            traits.forEach { (key, value) ->
                firebaseAnalytics?.setUserProperty(key, value?.toString())
            }
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase identify failed", e)
        }
    }

    /**
     * Resets the current user identity in Firebase Analytics by setting the
     * user ID to `null`.
     *
     * This is typically called when a user logs out, so that subsequent events
     * are no longer associated with the previous user.
     */
    override fun reset() {
        try {
            firebaseAnalytics?.setUserId(null)
        } catch (e: Exception) {
            TrackFlowLogger.error("Firebase reset failed", e)
        }
    }
}

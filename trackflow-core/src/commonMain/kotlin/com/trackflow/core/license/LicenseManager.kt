package com.trackflow.core.license

import kotlin.concurrent.Volatile
import com.trackflow.core.logging.TrackFlowLogger
import com.trackflow.core.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Defines which features are available for a given license tier.
 */
enum class LicenseTier {
    /** No license key provided. Open-source providers only, premium features disabled. */
    FREE,

    /** Paid license. All providers and features enabled. */
    PRO,

    /** Enterprise license. All providers, features, and extended limits. */
    ENTERPRISE,

    /** License key is invalid or expired. Same as FREE. */
    INVALID
}

/**
 * Result of a license validation check.
 *
 * @property tier The resolved license tier.
 * @property expiresAtMs Expiration timestamp in millis, or null if no expiration.
 * @property features Set of feature flags enabled for this license.
 */
data class LicenseStatus(
    val tier: LicenseTier = LicenseTier.FREE,
    val expiresAtMs: Long? = null,
    val features: Set<String> = emptySet()
) {
    val isExpired: Boolean
        get() = expiresAtMs != null && currentTimeMillis() > expiresAtMs

    val effectiveTier: LicenseTier
        get() = if (isExpired) LicenseTier.FREE else tier
}

/**
 * Feature flags that can be gated by license tier.
 */
object Features {
    const val DEBUG_OVERLAY = "debug_overlay"
    const val EVENT_MONITOR = "event_monitor"
    const val MIDDLEWARE = "middleware"
    const val OFFLINE_QUEUE = "offline_queue"
    const val DEDUPLICATION = "deduplication"
    const val LIFECYCLE_TRACKING = "lifecycle_tracking"
    const val JSON_EXPORT = "json_export"
    const val PAID_PROVIDERS = "paid_providers"
}

/**
 * Manages license key validation and feature gating for TrackFlow.
 *
 * Open-core model:
 * - Free tier: Core SDK + open-source providers (firebase, amplitude, mixpanel).
 *   No limit on how many free providers you use.
 * - Pro tier: All providers (adds Adobe Analytics, Adobe Edge, and future paid providers)
 *   plus premium features (middleware, deduplication, event monitor, etc.).
 * - Enterprise tier: Everything in Pro + extended limits.
 *
 * Paid providers are identified by their provider key. Any provider whose key
 * is in [paidProviderKeys] requires a Pro or Enterprise license.
 */
internal class LicenseManager {

    @Volatile
    var status: LicenseStatus = LicenseStatus(LicenseTier.FREE)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Validates the given license key.
     *
     * If no key is provided, defaults to FREE tier.
     * Validation happens asynchronously — the SDK starts immediately with
     * cached/default permissions and upgrades when validation completes.
     *
     * @param key The license key string, or null for free tier.
     * @param onValidated Optional callback when validation completes.
     */
    fun validate(key: String?, onValidated: ((LicenseStatus) -> Unit)? = null) {
        if (key.isNullOrBlank()) {
            status = LicenseStatus(LicenseTier.FREE)
            TrackFlowLogger.debug("No license key — running in free tier (open-source providers only)")
            onValidated?.invoke(status)
            return
        }

        // Decode the license key locally first (offline-friendly)
        val decoded = decodeKey(key)
        status = decoded
        TrackFlowLogger.debug("License: ${decoded.effectiveTier} (${decoded.features.size} features)")
        onValidated?.invoke(status)

        // Optionally validate against remote API in the background
        scope.launch {
            try {
                val remote = validateRemote(key)
                if (remote != null) {
                    status = remote
                    TrackFlowLogger.debug("License validated remotely: ${remote.effectiveTier}")
                }
            } catch (e: Exception) {
                TrackFlowLogger.debug("Remote license check failed, using local: ${e.message}")
            }
        }
    }

    /**
     * Checks if a specific feature is enabled for the current license.
     */
    fun isFeatureEnabled(feature: String): Boolean {
        val effective = status.effectiveTier
        return when (effective) {
            LicenseTier.PRO, LicenseTier.ENTERPRISE -> true
            LicenseTier.FREE, LicenseTier.INVALID -> feature !in premiumFeatures
        }
    }

    /**
     * Returns whether a provider with the given [providerKey] is allowed
     * under the current license.
     *
     * Open-source providers (firebase, amplitude, mixpanel) are always allowed.
     * Paid providers (adobe-analytics, adobe-edge, etc.) require Pro or Enterprise.
     */
    fun isProviderAllowed(providerKey: String): Boolean {
        if (providerKey !in paidProviderKeys) return true
        return isFeatureEnabled(Features.PAID_PROVIDERS)
    }

    /**
     * Decodes a license key locally without network.
     *
     * Key format: tf_{tier}_{signature}
     * - tf_pro_xxxx → PRO tier
     * - tf_ent_xxxx → ENTERPRISE tier
     * - anything else → INVALID
     */
    private fun decodeKey(key: String): LicenseStatus {
        val parts = key.lowercase().split("_")
        if (parts.size < 2 || parts[0] != "tf") {
            return LicenseStatus(LicenseTier.INVALID)
        }

        val tier = when (parts.getOrNull(1)) {
            "pro" -> LicenseTier.PRO
            "ent", "enterprise" -> LicenseTier.ENTERPRISE
            else -> LicenseTier.INVALID
        }

        val features = when (tier) {
            LicenseTier.PRO, LicenseTier.ENTERPRISE -> setOf(
                Features.DEBUG_OVERLAY,
                Features.EVENT_MONITOR,
                Features.MIDDLEWARE,
                Features.OFFLINE_QUEUE,
                Features.DEDUPLICATION,
                Features.LIFECYCLE_TRACKING,
                Features.JSON_EXPORT,
                Features.PAID_PROVIDERS
            )
            else -> emptySet()
        }

        return LicenseStatus(
            tier = tier,
            features = features
        )
    }

    /**
     * Validates the license key against the remote TrackFlow API.
     *
     * TODO: Implement HTTP call to https://api.trackflow.dev/v1/license/validate
     * For now, returns null (skip remote validation).
     */
    private suspend fun validateRemote(key: String): LicenseStatus? {
        // TODO: Implement when the license API is built
        return null
    }

    companion object {
        /**
         * Provider keys that require a paid license.
         * Open-source providers: firebase, amplitude, mixpanel
         * Paid providers: adobe-analytics, adobe-edge (and future additions)
         */
        val paidProviderKeys = setOf(
            "adobe-analytics",
            "adobe-edge"
        )

        private val premiumFeatures = setOf(
            Features.DEBUG_OVERLAY,
            Features.EVENT_MONITOR,
            Features.MIDDLEWARE,
            Features.OFFLINE_QUEUE,
            Features.DEDUPLICATION,
            Features.LIFECYCLE_TRACKING,
            Features.JSON_EXPORT,
            Features.PAID_PROVIDERS
        )
    }
}

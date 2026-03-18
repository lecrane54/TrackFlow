package com.trackflow.core.event

/**
 * Pre-built typed event catalog for common analytics scenarios.
 *
 * All events implement [TrackFlowEvent] and accept an [extra] map
 * for additional ad-hoc properties beyond the typed fields.
 */

// ── Screen / Navigation ─────────────────────────────────────

/**
 * Tracks a screen or page view. Best used with [com.trackflow.core.TrackFlow.trackState].
 *
 * @property screenName The name of the screen being viewed.
 * @property screenClass Optional fully-qualified class name of the screen (e.g., Activity or Fragment).
 * @property extra Additional properties to include with this event.
 */
data class ScreenViewed(
    val screenName: String,
    val screenClass: String? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "screen_viewed"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("screen_name", screenName)
            screenClass?.let { put("screen_class", it) }
            putAll(extra)
        }
}

// ── E-Commerce ──────────────────────────────────────────────

/**
 * Tracks when a user views a product.
 *
 * @property productId The unique product identifier (e.g., SKU).
 * @property productName Optional display name of the product.
 * @property price Optional price of the product.
 * @property currency Optional ISO 4217 currency code (e.g., "USD").
 * @property category Optional product category.
 * @property extra Additional properties to include with this event.
 */
data class ProductViewed(
    val productId: String,
    val productName: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "product_viewed"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("product_id", productId)
            productName?.let { put("product_name", it) }
            price?.let { put("price", it) }
            currency?.let { put("currency", it) }
            category?.let { put("category", it) }
            putAll(extra)
        }
}

/**
 * Tracks when a user adds a product to their cart.
 *
 * @property productId The unique product identifier.
 * @property quantity Number of units added. Defaults to 1.
 * @property price Optional unit price.
 * @property currency Optional ISO 4217 currency code.
 * @property extra Additional properties to include with this event.
 */
data class ProductAddedToCart(
    val productId: String,
    val quantity: Int = 1,
    val price: Double? = null,
    val currency: String? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "product_added_to_cart"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("product_id", productId)
            put("quantity", quantity)
            price?.let { put("price", it) }
            currency?.let { put("currency", it) }
            putAll(extra)
        }
}

/**
 * Tracks a completed purchase/transaction.
 *
 * @property orderId The unique order or transaction identifier.
 * @property total The total purchase amount.
 * @property currency ISO 4217 currency code. Defaults to "USD".
 * @property items Optional total number of items in the order.
 * @property extra Additional properties to include with this event.
 */
data class PurchaseCompleted(
    val orderId: String,
    val total: Double,
    val currency: String = "USD",
    val items: Int? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "purchase_completed"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("order_id", orderId)
            put("total", total)
            put("currency", currency)
            items?.let { put("items", it) }
            putAll(extra)
        }
}

// ── User Engagement ─────────────────────────────────────────

/**
 * Tracks a button click or tap interaction.
 *
 * @property buttonName The identifier or label of the button.
 * @property screen Optional screen name where the button was clicked.
 * @property extra Additional properties to include with this event.
 */
data class ButtonClicked(
    val buttonName: String,
    val screen: String? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "button_clicked"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("button_name", buttonName)
            screen?.let { put("screen", it) }
            putAll(extra)
        }
}

/**
 * Tracks when a user performs a search.
 *
 * @property query The search query string.
 * @property resultCount Optional number of results returned.
 * @property extra Additional properties to include with this event.
 */
data class SearchPerformed(
    val query: String,
    val resultCount: Int? = null,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "search_performed"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("query", query)
            resultCount?.let { put("result_count", it) }
            putAll(extra)
        }
}

// ── Auth ─────────────────────────────────────────────────────

/**
 * Tracks a new user registration.
 *
 * @property method The sign-up method used (e.g., "email", "google", "apple").
 * @property extra Additional properties to include with this event.
 */
data class SignUp(
    val method: String,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "sign_up"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("method", method)
            putAll(extra)
        }
}

/**
 * Tracks a user login.
 *
 * @property method The login method used (e.g., "email", "google", "biometric").
 * @property extra Additional properties to include with this event.
 */
data class Login(
    val method: String,
    val extra: Map<String, Any?> = emptyMap()
) : TrackFlowEvent {
    override val name = "login"
    override val properties: Map<String, Any?>
        get() = buildMap {
            put("method", method)
            putAll(extra)
        }
}

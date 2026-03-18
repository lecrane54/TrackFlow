package com.trackflow.core.event

// ── Screen / Navigation ─────────────────────────────────────

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

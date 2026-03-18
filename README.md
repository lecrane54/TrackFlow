# TrackFlow SDK

A lightweight, multiplatform analytics SDK for **Android** and **iOS** (Kotlin Multiplatform). Track events once, route them to any combination of analytics providers through a single unified pipeline.

## Features

- **Single API** — `track()` and `trackState()` for all providers
- **Android + iOS** — Kotlin Multiplatform with native CocoaPods integration for iOS
- **5 built-in providers** — Firebase, Adobe Analytics, Adobe Edge/CJA, Mixpanel, Amplitude
- **Automatic batching** — configurable batch size and flush interval
- **Offline persistence** — events queued to disk when offline, replayed on reconnect
- **Retry with backoff** — exponential backoff on provider failures
- **Super properties** — global properties attached to every event
- **User identity** — `identify()`/`resetIdentity()` propagated to all providers
- **Middleware pipeline** — intercept, transform, or filter events before dispatch
- **Key remapping** — per-provider property key mapping (e.g., `product_id` → `eVar5` for Adobe, `item_id` for Firebase)
- **Typed event catalog** — pre-built data classes for common events (Android)
- **Jetpack Compose** — `TrackScreen()` composable for automatic screen tracking (Android)
- **Zero-crash guarantee** — all provider errors handled internally
- **Thread-safe** singleton architecture

## Installation

### Android

```kotlin
dependencies {
    // Core SDK (required)
    implementation("com.trackflow:trackflow-core:1.0.0")

    // Analytics providers — pick one or more
    implementation("com.trackflow:trackflow-provider-firebase:1.0.0")
    implementation("com.trackflow:trackflow-provider-adobe-analytics:1.0.0")
    implementation("com.trackflow:trackflow-provider-adobe-edge:1.0.0")
    implementation("com.trackflow:trackflow-provider-mixpanel:1.0.0")
    implementation("com.trackflow:trackflow-provider-amplitude:1.0.0")
}
```

### iOS (CocoaPods)

Add the TrackFlow pods to your `Podfile`:

```ruby
pod 'TrackFlowCore', :path => '../trackflow-core'
pod 'trackflow-provider-firebase-ios', :path => '../trackflow-provider-firebase-ios'
pod 'trackflow-provider-amplitude-ios', :path => '../trackflow-provider-amplitude-ios'
pod 'trackflow-provider-mixpanel-ios', :path => '../trackflow-provider-mixpanel-ios'
pod 'trackflow-provider-adobe-analytics-ios', :path => '../trackflow-provider-adobe-analytics-ios'
pod 'trackflow-provider-adobe-edge-ios', :path => '../trackflow-provider-adobe-edge-ios'
```

## Quick Start

### Android

#### Initialize

Call in your `Application.onCreate()`:

```kotlin
TrackFlow.initialize(
    TrackFlow.Builder(applicationContext)
        .addProvider(FirebaseProvider())
        .addProvider(MixpanelProvider("YOUR_TOKEN"))
        .addProvider(AmplitudeProvider("YOUR_API_KEY"))
        .build()
)
```

#### Track Events

```kotlin
// Simple — vararg pairs
TrackFlow.track("button_clicked",
    "button_name" to "checkout",
    "screen" to "cart"
)

// With a map
TrackFlow.track("purchase_completed", mapOf(
    "order_id" to "order_456",
    "total" to 99.99
))

// No properties
TrackFlow.track("app_opened")
```

#### Track Page/Screen Views

```kotlin
// Routed to MobileCore.trackState() for Adobe, logEvent() for Firebase, etc.
TrackFlow.trackState("home_screen",
    "tab" to "featured"
)
```

#### Compose Screen Tracking

```kotlin
@Composable
fun HomeScreen() {
    TrackScreen("home_screen")  // Automatically calls trackState()
    // ...
}
```

### iOS

#### Initialize

Register providers and initialize in your app's entry point:

```swift
import TrackFlowCore
import trackflow_provider_firebase_ios
import trackflow_provider_amplitude_ios
import trackflow_provider_adobe_analytics_ios

// Register providers (before calling initialize)
FirebaseIosProviderKt.registerFirebaseProvider(keyMap: nil)
AmplitudeIosProviderKt.registerAmplitudeProvider(apiKey: "YOUR_API_KEY", keyMap: nil)
AdobeAnalyticsIosProviderKt.registerAdobeAnalyticsProvider(appId: "YOUR_APP_ID", keyMap: nil)

// Initialize TrackFlow
TrackFlowIos.shared.initialize(logLevel: .debug, batchSize: 10, flushIntervalMs: 15_000, licenseKey: nil)
```

#### Track Events

```swift
// With properties
TrackFlow.shared.track(name: "purchase", properties_: [
    "product_id": "SKU-1234",
    "price": "29.99",
    "currency": "USD"
])

// No properties
TrackFlow.shared.track(name: "app_opened")
```

#### Track Page/Screen Views

```swift
TrackFlow.shared.trackState(name: "product_detail", properties_: [
    "screen": "ProductDetailVC",
    "source": "search"
])
```

#### Identity

```swift
TrackFlow.shared.identify(userId: "user_123", traits_: ["plan": "premium"])
TrackFlow.shared.resetIdentity()
```

That's it. Events flow through the pipeline to all configured providers.

---

## Typed Event Catalog

Pre-built data classes for common analytics events in `com.trackflow.core.event`:

```kotlin
// E-Commerce
TrackFlow.track(ProductViewed(productId = "sku_123", price = 29.99, currency = "USD"))
TrackFlow.track(ProductAddedToCart(productId = "sku_123", quantity = 2))
TrackFlow.track(PurchaseCompleted(orderId = "order_456", total = 99.99, items = 3))

// Engagement
TrackFlow.track(ButtonClicked(buttonName = "subscribe", screen = "settings"))
TrackFlow.track(SearchPerformed(query = "running shoes", resultCount = 42))

// Auth
TrackFlow.track(Login(method = "google"))
TrackFlow.track(SignUp(method = "email"))

// Screens
TrackFlow.trackState(ScreenViewed(screenName = "ProductDetail", screenClass = "ProductActivity"))
```

All typed events accept an `extra` map for additional ad-hoc properties:

```kotlin
TrackFlow.track(ProductViewed(
    productId = "sku_123",
    price = 29.99,
    extra = mapOf("campaign" to "summer_sale")
))
```

---

## User Identity

Propagates to all providers that support it (Firebase `setUserId`, Mixpanel `identify`, Amplitude `setUserId`, etc.):

```kotlin
// Identify a user with optional traits
TrackFlow.identify("user_123",
    "email" to "user@example.com",
    "plan" to "premium"
)

// Check current user
val userId = TrackFlow.userId()

// Reset on logout — clears identity and rotates session
TrackFlow.resetIdentity()
```

---

## Super Properties

Global properties automatically merged into every event. Event-level properties override on conflict.

```kotlin
// Set at init time
TrackFlow.Builder(context)
    .superProperties(
        "app_version" to "1.0.0",
        "environment" to "production"
    )
    .build()

// Set at runtime
TrackFlow.setSuperProperties("user_tier" to "premium")

// Remove a single property
TrackFlow.removeSuperProperty("user_tier")

// Clear all
TrackFlow.clearSuperProperties()

// Read current
val props = TrackFlow.superProperties()
```

---

## Middleware

Intercept, transform, or drop events before they reach providers. Middleware runs in order; return `null` to drop an event.

```kotlin
TrackFlow.Builder(context)
    // PII scrubber — remove sensitive keys
    .addMiddleware { payload ->
        payload.copy(
            properties = payload.properties.filterKeys {
                it !in setOf("email", "phone", "ssn")
            }
        )
    }
    // Event sampler — only send 10% of events
    .addMiddleware { payload ->
        if (Math.random() < 0.1) payload else null
    }
    // Property enricher — add computed fields
    .addMiddleware { payload ->
        payload.copy(
            properties = payload.properties + mapOf(
                "enriched_at" to System.currentTimeMillis()
            )
        )
    }
    .build()
```

---

## Key Remapping

Map app-level property names to provider-specific keys. Each provider can have its own `keyMap`, so you write one consistent tracking call and each provider receives keys in the format it expects.

### How it works

When you call:
```kotlin
TrackFlow.track("product_viewed",
    "product_id" to "SKU-123",
    "product_name" to "Running Shoes",
    "category" to "footwear",
    "price" to 29.99,
    "currency" to "USD"
)
```

Each provider transforms the keys independently based on its `keyMap`:

| Your key | Adobe Analytics | Amplitude | Firebase | Mixpanel | Adobe Edge (XDM) |
|----------|----------------|-----------|----------|----------|-------------------|
| `product_id` | `eVar5` | `Product ID` | `item_id` | `$product_id` | `productListItems.SKU` |
| `product_name` | `eVar6` | `Product Name` | `item_name` | `$product_name` | `productListItems.name` |
| `category` | `eVar7` | `Product Category` | `item_category` | *(pass-through)* | *(pass-through)* |
| `price` | `eVar12` | `Revenue` | `value` | `$amount` | `productListItems.priceTotal` |
| `currency` | *(pass-through)* | *(pass-through)* | *(pass-through)* | `$currency` | *(pass-through)* |

Keys not in the `keyMap` pass through unchanged.

### Android example

```kotlin
// Adobe Analytics: remap generic keys → eVars/props
val adobeKeyMap = mapOf(
    "screen" to "pageName",
    "screen_name" to "pageName",
    "product_id" to "eVar5",
    "product_name" to "eVar6",
    "category" to "eVar7",
    "price" to "eVar12",
    "quantity" to "eVar13",
    "search_query" to "eVar15",
    "search_results" to "eVar16",
    "error_type" to "eVar20",
    "error_code" to "eVar21",
    "filter_applied" to "prop16",
    "message" to "prop43",
    "click_count" to "prop2"
)

// Amplitude: remap to human-readable names
val amplitudeKeyMap = mapOf(
    "product_id" to "Product ID",
    "product_name" to "Product Name",
    "category" to "Product Category",
    "price" to "Revenue",
    "quantity" to "Quantity",
    "search_query" to "Search Query",
    "search_results" to "Search Results Count",
)

TrackFlow.initialize(
    TrackFlow.Builder(applicationContext)
        .addProvider(AdobeAnalyticsProvider(BuildConfig.ADOBE_KEY, keyMap = adobeKeyMap))
        .addProvider(AmplitudeProvider(BuildConfig.AMP_KEY, keyMap = amplitudeKeyMap))
        .build()
)
```

### iOS example

```swift
// Adobe Analytics: remap generic keys → eVars/props
let adobeKeyMap: [String: String] = [
    "screen": "pageName",
    "screen_name": "pageName",
    "product_id": "eVar5",
    "product_name": "eVar6",
    "category": "eVar7",
    "price": "eVar12",
    "quantity": "eVar13",
    "search_query": "eVar15",
    "search_results": "eVar16",
    "error_type": "eVar20",
    "error_code": "eVar21",
]

// Firebase: remap to GA4 parameter conventions
let firebaseKeyMap: [String: String] = [
    "product_id": "item_id",
    "product_name": "item_name",
    "category": "item_category",
    "price": "value",
    "search_query": "search_term",
]

// Mixpanel: remap to Mixpanel's $ conventions
let mixpanelKeyMap: [String: String] = [
    "product_id": "$product_id",
    "product_name": "$product_name",
    "price": "$amount",
    "currency": "$currency",
    "screen": "mp_page",
    "screen_name": "mp_page",
]

// Adobe Edge: remap to XDM schema paths
let adobeEdgeKeyMap: [String: String] = [
    "product_id": "productListItems.SKU",
    "product_name": "productListItems.name",
    "price": "productListItems.priceTotal",
    "screen": "web.webPageDetails.name",
    "search_query": "search.query",
    "search_results": "search.numberOfResults",
]

// Register each provider with its own key map
AdobeAnalyticsIosProviderKt.registerAdobeAnalyticsProvider(
    appId: adobeAppId, keyMap:adobeKeyMap
)
FirebaseIosProviderKt.registerFirebaseProvider(
    keyMap: firebaseKeyMap
)
MixpanelIosProviderKt.registerMixpanelProvider(
    token: mixpanelToken, keyMap:mixpanelKeyMap
)
AmplitudeIosProviderKt.registerAmplitudeProvider(
    apiKey: amplitudeKey, keyMap: amplitudeKeyMap
)
AdobeEdgeIosProviderKt.registerAdobeEdgeProvider(
    appId: adobeAppId, datasetId: nil, keyMap:adobeEdgeKeyMap
)
```

## Provider Reference

### Firebase

```kotlin
// Android
FirebaseProvider(keyMap = null)
```

```swift
// iOS
FirebaseIosProviderKt.registerFirebaseProvider(keyMap: nil)
```

- Calls `FirebaseAnalytics.logEvent()` (Android) / `Analytics.logEvent()` (iOS)
- Event names sanitized: non-alphanumeric replaced with `_`, max 40 chars
- `identify()` calls `setUserId()` + `setUserProperty()` for traits
- Android requires `google-services.json`; iOS requires `GoogleService-Info.plist`

### Adobe Analytics (Legacy + Edge Bridge)

```kotlin
// Android
AdobeAnalyticsProvider(appId = "your-environment-file-id", keyMap = null)
```

```swift
// iOS
AdobeAnalyticsIosProviderKt.registerAdobeAnalyticsProvider(appId: "your-id", keyMap: nil)
```

- `track()` calls `MobileCore.trackAction(name, contextData)`
- `trackState()` calls `MobileCore.trackState(name, contextData)`
- Properties flattened to String values for context data compatibility
- Registers: Analytics, Identity, Lifecycle, Signal, UserProfile, EdgeBridge

### Adobe Edge / CJA (XDM)

```kotlin
// Android
AdobeEdgeProvider(appId = "your-environment-file-id", datasetId = null, keyMap = null)
```

```swift
// iOS
AdobeEdgeIosProviderKt.registerAdobeEdgeProvider(appId: "your-id", datasetId: nil, keyMap: nil)
```

- `track()` sends XDM ExperienceEvent with `eventType: "analytics.action"`
- `trackState()` sends with `eventType: "web.webpagedetails.pageViews"`
- Registers: Edge, Identity (Edge), Lifecycle, Signal, UserProfile

### Mixpanel

```kotlin
// Android
MixpanelProvider(token = "your-project-token", keyMap = null)
```

```swift
// iOS
MixpanelIosProviderKt.registerMixpanelProvider(token: "your-token", keyMap: nil)
```

- Calls `MixpanelAPI.track(name, jsonObject)` / `Mixpanel.track(name, properties:)`
- Adds `"source": "trackflow"` to all events
- `identify()` calls `identify()` + `people.set()` for traits
- `reset()` calls `reset()`

### Amplitude

```kotlin
// Android
AmplitudeProvider(apiKey = "your-api-key", keyMap = null)
```

```swift
// iOS
AmplitudeIosProviderKt.registerAmplitudeProvider(apiKey: "your-key", keyMap: nil)
```

- Calls `Amplitude.track(name, properties)` / `Amplitude.instance().logEvent()`
- `identify()` calls `setUserId()` + `Identify.set()` for typed traits
- `reset()` clears userId + regenerates deviceId

---

## Configuration

### Builder Options

| Option | Default | Description |
|--------|---------|-------------|
| `addProvider()` | — | Register an analytics provider |
| `addMiddleware()` | — | Add a middleware interceptor |
| `superProperties()` | empty | Global properties for all events |
| `batchSize()` | 20 | Events per batch before auto-flush |
| `flushInterval()` | 30000ms | Timer-based auto-flush interval |
| `logLevel()` | ERROR | NONE, ERROR, WARN, DEBUG, VERBOSE |
| `logListener()` | null | Callback for external log capture |

### Full Example (Android)

```kotlin
val adobeKeyMap = mapOf(
    "screen" to "pageName", "product_id" to "eVar5",
    "product_name" to "eVar6", "price" to "eVar12",
    "search_query" to "eVar15", "error_type" to "eVar20",
)

val amplitudeKeyMap = mapOf(
    "product_id" to "Product ID", "product_name" to "Product Name",
    "price" to "Revenue", "search_query" to "Search Query",
)

TrackFlow.initialize(
    TrackFlow.Builder(applicationContext)
        // Providers with per-provider key remapping
        .addProvider(FirebaseProvider(
            keyMap = mapOf("product_id" to "item_id", "price" to "value")
        ))
        .addProvider(AdobeAnalyticsProvider(
            appId = "your-adobe-id",
            keyMap = adobeKeyMap
        ))
        .addProvider(MixpanelProvider(
            token = "your-mixpanel-token",
            keyMap = mapOf("product_id" to "\$product_id", "price" to "\$amount")
        ))
        .addProvider(AmplitudeProvider(
            apiKey = "your-amplitude-key",
            keyMap = amplitudeKeyMap
        ))

        // Super properties
        .superProperties(
            "app_version" to BuildConfig.VERSION_NAME,
            "build_type" to BuildConfig.BUILD_TYPE
        )

        // Middleware
        .addMiddleware { payload ->
            payload.copy(
                properties = payload.properties.filterKeys { it !in setOf("email", "ssn") }
            )
        }

        // Pipeline config
        .batchSize(25)
        .flushInterval(15_000L)
        .logLevel(LogLevel.DEBUG)

        .build()
)
```

### Full Example (iOS)

```swift
import TrackFlowCore
import trackflow_provider_firebase_ios
import trackflow_provider_amplitude_ios
import trackflow_provider_mixpanel_ios
import trackflow_provider_adobe_analytics_ios
import trackflow_provider_adobe_edge_ios

// Each provider gets its own key map — same event, different key names per provider
let adobeKeyMap: [String: String] = [
    "screen": "pageName", "product_id": "eVar5", "product_name": "eVar6",
    "price": "eVar12", "search_query": "eVar15", "error_type": "eVar20",
]
let firebaseKeyMap: [String: String] = [
    "product_id": "item_id", "product_name": "item_name",
    "category": "item_category", "price": "value",
]
let mixpanelKeyMap: [String: String] = [
    "product_id": "$product_id", "price": "$amount",
    "currency": "$currency", "screen": "mp_page",
]
let amplitudeKeyMap: [String: String] = [
    "product_id": "Product ID", "product_name": "Product Name",
    "price": "Revenue", "search_query": "Search Query",
]
let edgeKeyMap: [String: String] = [
    "product_id": "productListItems.SKU", "price": "productListItems.priceTotal",
    "screen": "web.webPageDetails.name", "search_query": "search.query",
]

// Register providers with key maps
FirebaseIosProviderKt.registerFirebaseProvider(keyMap: firebaseKeyMap)
AmplitudeIosProviderKt.registerAmplitudeProvider(apiKey: ampKey, keyMap: amplitudeKeyMap)
MixpanelIosProviderKt.registerMixpanelProvider(token: mpToken, keyMap:mixpanelKeyMap)
AdobeAnalyticsIosProviderKt.registerAdobeAnalyticsProvider(appId: adobeId, keyMap:adobeKeyMap)
AdobeEdgeIosProviderKt.registerAdobeEdgeProvider(appId: adobeId, datasetId: nil, keyMap:edgeKeyMap)

// Initialize
TrackFlowIos.shared.initialize(logLevel: .debug, batchSize: 10, flushIntervalMs: 15_000, licenseKey: nil)

// Now tracking sends the same event to all providers with provider-specific keys:
TrackFlow.shared.track(name: "product_viewed", properties_: [
    "product_id": "SKU-123",    // → eVar5 (Adobe), item_id (Firebase), Product ID (Amplitude), ...
    "price": "29.99",           // → eVar12 (Adobe), value (Firebase), Revenue (Amplitude), ...
    "currency": "USD",          // → pass-through (Adobe), $currency (Mixpanel), ...
])
```

---

## Custom Providers

Implement `AnalyticsProvider` to add any backend:

```kotlin
class MyCustomProvider : AnalyticsProvider {
    override val key = "custom"

    override val mapper = object : ProviderEventMapper {
        override fun mapTrack(payload: AnalyticsPayload): ProviderEvent {
            return ProviderEvent(payload.eventName, payload.properties)
        }

        // Optional: custom mapping for state/pageview events
        override fun mapState(payload: AnalyticsPayload): ProviderEvent? {
            return ProviderEvent("pageview_${payload.eventName}", payload.properties)
        }
    }

    override fun initialize(context: Context) {
        // Initialize your SDK
    }

    override fun track(event: ProviderEvent) {
        // Send action events
    }

    // Optional: different handling for state/pageview events
    override fun trackState(event: ProviderEvent) {
        // Send state/pageview events
    }

    // Optional: handle user identity
    override fun identify(userId: String, traits: Map<String, Any?>) {
        // Set user identity in your SDK
    }

    // Optional: handle logout/reset
    override fun reset() {
        // Clear user identity
    }
}
```

---

## Architecture

```
TrackFlow.track("event", "key" to "value")
    |
    v
[Super Properties merged]
    |
    v
[AnalyticsPayload created with context + session + user_id]
    |
    v
[Middleware chain] ── can transform or drop ──> (dropped)
    |
    v
[DebugEventSink records]
    |
    v
[EventBatcher accumulates]
    |-- batch size reached (default 20)
    |-- timer expires (default 30s)
    |-- manual flush()
    v
[EventDispatcher]
    |
    +-- Online --> [RetryPolicy (3x exponential backoff)]
    |                   |
    |                   v
    |              [Provider.mapper.mapTrack/mapState]
    |                   |
    |                   v
    |              [Provider.track/trackState]
    |                   |
    |                   v
    |              Firebase / Adobe / Mixpanel / Amplitude
    |
    +-- Offline --> [OfflineEventQueue (JSON file, max 500)]
                        |
                        +-- Drains automatically on reconnect
```

## Context Enrichment

Every event is automatically enriched with:

| Field | Example |
|-------|---------|
| `platform` | `"android"` |
| `device_model` | `"Pixel 8"` |
| `os_version` | `"14"` |
| `package_name` | `"com.example.app"` |
| `session_id` | `"a1b2c3d4-..."` |
| `user_id` | `"user_123"` (when identified) |

## Debug Events

Inspect all events that passed through the pipeline:

```kotlin
val events = TrackFlow.debugEvents()
events.forEach { payload ->
    Log.d("Debug", "${payload.eventName}: ${payload.properties}")
    Log.d("Debug", "  type=${payload.type}, timestamp=${payload.timestamp}")
    Log.d("Debug", "  context=${payload.context}")
}
```

## Lifecycle

```kotlin
// Force flush all pending events
TrackFlow.flush()

// Shutdown — flushes remaining events and stops the pipeline
TrackFlow.shutdown()
```

## Requirements

- **minSdk**: 24
- **Kotlin**: 2.0.21+
- **Android Gradle Plugin**: 8.7.3+
- **Gradle**: 8.12+
- **Java**: 17

## Project Structure

```
TrackFlow/
  trackflow-core/                            Core SDK (KMP: commonMain + androidMain + iosMain)
    src/commonMain/kotlin/com/trackflow/core/
      TrackFlow.kt                           Main entry point
      payload/AnalyticsPayload.kt            Internal payload model
      provider/ProviderModels.kt             Provider interfaces
      middleware/TrackFlowMiddleware.kt       Middleware pipeline
      pipeline/EventBatcher.kt               Event batching
      pipeline/EventDispatcher.kt            Dispatch orchestrator
      util/MapExtensions.kt                  Key remap utils

  trackflow-provider-firebase/               Firebase (Android)
  trackflow-provider-firebase-ios/           Firebase (iOS via CocoaPods)
  trackflow-provider-adobe-analytics/        Adobe Analytics (Android)
  trackflow-provider-adobe-analytics-ios/    Adobe Analytics (iOS via CocoaPods)
  trackflow-provider-adobe-edge/             Adobe Edge / CJA (Android)
  trackflow-provider-adobe-edge-ios/         Adobe Edge / CJA (iOS via CocoaPods)
  trackflow-provider-mixpanel/               Mixpanel (Android)
  trackflow-provider-mixpanel-ios/           Mixpanel (iOS via CocoaPods)
  trackflow-provider-amplitude/              Amplitude (Android)
  trackflow-provider-amplitude-ios/          Amplitude (iOS via CocoaPods)

  testapp/                                   Android sample app
  testapp-ios/                               iOS sample app
```

## License

Apache License 2.0

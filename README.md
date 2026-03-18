# TrackFlow Android SDK

A lightweight, modular analytics SDK for Android. Track events once, route them to any combination of analytics providers through a single unified pipeline.

## Features

- **Single API** — `track()` and `trackState()` for all providers
- **6 built-in providers** — Firebase, Adobe Analytics, Adobe Edge/CJA, Mixpanel, Amplitude
- **Automatic batching** — configurable batch size and flush interval
- **Offline persistence** — events queued to disk when offline, replayed on reconnect
- **Retry with backoff** — exponential backoff on provider failures
- **Super properties** — global properties attached to every event
- **User identity** — `identify()`/`resetIdentity()` propagated to all providers
- **Middleware pipeline** — intercept, transform, or filter events before dispatch
- **Key remapping** — per-provider property key mapping (e.g., `product_id` to `eVar21`)
- **Key prefixing** — per-provider key namespacing
- **Typed event catalog** — pre-built data classes for common events
- **Jetpack Compose** — `TrackScreen()` composable for automatic screen tracking
- **Zero-crash guarantee** — all provider errors handled internally
- **Thread-safe** singleton architecture

## Installation

```kotlin
dependencies {
    // Core SDK (required)
    implementation("com.trackflow:trackflow-core:4.0.0")

    // Analytics providers — pick one or more
    implementation("com.trackflow:trackflow-provider-firebase:4.0.0")
    implementation("com.trackflow:trackflow-provider-adobe-analytics:4.0.0")
    implementation("com.trackflow:trackflow-provider-adobe-edge:4.0.0")
    implementation("com.trackflow:trackflow-provider-mixpanel:4.0.0")
    implementation("com.trackflow:trackflow-provider-amplitude:4.0.0")
}
```

## Quick Start

### Initialize

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

### Track Events

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

### Track Page/Screen Views

```kotlin
// Routed to MobileCore.trackState() for Adobe, logEvent() for Firebase, etc.
TrackFlow.trackState("home_screen",
    "tab" to "featured"
)
```

### Compose Screen Tracking

```kotlin
@Composable
fun HomeScreen() {
    TrackScreen("home_screen")  // Automatically calls trackState()
    // ...
}
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
        "app_version" to "4.0.0",
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

Map app-level property names to provider-specific keys. Useful for Adobe eVars/props or platform-specific naming conventions.

```kotlin
// Adobe Analytics — map to eVars
AdobeAnalyticsProvider(
    appId = "your-app-id",
    keyMap = mapOf(
        "product_id" to "eVar21",
        "price" to "eVar22",
        "category" to "eVar23",
        "screen_name" to "prop4"
    )
)

// Firebase — map to Firebase param conventions
FirebaseProvider(
    keyMap = mapOf(
        "product_id" to "item_id",
        "product_name" to "item_name"
    )
)

// Mixpanel — map to Mixpanel conventions
MixpanelProvider(
    token = "your-token",
    keyMap = mapOf("product_id" to "Product ID")
)
```

Keys not in the `keyMap` pass through unchanged.

## Key Prefixing

Namespace property keys per-provider:

```kotlin
AdobeAnalyticsProvider(appId = "...", keyPrefix = "aa.")
// "product_id" becomes "aa.product_id"
```

Remapping runs before prefixing, so you can combine both:

```kotlin
AdobeAnalyticsProvider(
    appId = "...",
    keyMap = mapOf("product_id" to "eVar21"),
    keyPrefix = "tf."
)
// "product_id" -> remap -> "eVar21" -> prefix -> "tf.eVar21"
```

---

## Provider Reference

### Firebase

```kotlin
FirebaseProvider(
    keyPrefix = null,                    // Optional key prefix
    keyMap = null                        // Optional key remapping
)
```

- Calls `FirebaseAnalytics.logEvent()` with Bundle parameters
- Event names sanitized: non-alphanumeric replaced with `_`, max 40 chars
- `identify()` calls `setUserId()` + `setUserProperty()` for traits
- Requires `google-services.json` in your app module

### Adobe Analytics (Legacy + Edge Bridge)

```kotlin
AdobeAnalyticsProvider(
    appId = "your-environment-file-id",  // Required
    keyPrefix = null,                    // Optional
    keyMap = null                        // Optional (e.g., eVar mappings)
)
```

- `track()` calls `MobileCore.trackAction(name, contextData)`
- `trackState()` calls `MobileCore.trackState(name, contextData)`
- Properties flattened to String values for context data compatibility
- Registers: Analytics, Identity, Lifecycle, Signal, UserProfile, EdgeBridge
- Uses Adobe SDK BOM 3.17.0 for version management

### Adobe Edge / CJA (XDM)

```kotlin
AdobeEdgeProvider(
    appId = "your-environment-file-id",  // Required
    datasetId = null,                    // Optional dataset override
    keyPrefix = null,                    // Optional
    keyMap = null                        // Optional
)
```

- `track()` sends XDM ExperienceEvent with `eventType: "analytics.action"`
- `trackState()` sends with `eventType: "web.webpagedetails.pageViews"`
- Registers: Edge, Identity (Edge), Lifecycle, Signal, UserProfile
- Uses Adobe SDK BOM 3.17.0 for version management

### Mixpanel

```kotlin
MixpanelProvider(
    token = "your-project-token",        // Required
    keyPrefix = null,                    // Optional
    keyMap = null                        // Optional
)
```

- Calls `MixpanelAPI.track(name, jsonObject)`
- Adds `"source": "trackflow"` to all events
- `identify()` calls `MixpanelAPI.identify()` + `people.set()` for traits
- `reset()` calls `MixpanelAPI.reset()`

### Amplitude

```kotlin
AmplitudeProvider(
    apiKey = "your-api-key",             // Required
    keyPrefix = null,                    // Optional
    keyMap = null                        // Optional
)
```

- Calls `Amplitude.track(name, properties)`
- `identify()` calls `setUserId()` + `Identify.set()` for typed traits
- `reset()` calls `Amplitude.reset()` (clears userId + generates new deviceId)

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

### Full Example

```kotlin
TrackFlow.initialize(
    TrackFlow.Builder(applicationContext)
        // Providers
        .addProvider(FirebaseProvider())
        .addProvider(AdobeAnalyticsProvider(
            appId = "your-adobe-id",
            keyMap = mapOf("product_id" to "eVar21", "price" to "eVar22")
        ))
        .addProvider(MixpanelProvider("your-mixpanel-token"))
        .addProvider(AmplitudeProvider("your-amplitude-key"))

        // Super properties
        .superProperties(
            "app_version" to BuildConfig.VERSION_NAME,
            "build_type" to BuildConfig.BUILD_TYPE
        )

        // Middleware
        .addMiddleware { payload ->
            // Strip PII
            payload.copy(
                properties = payload.properties.filterKeys { it !in setOf("email", "ssn") }
            )
        }

        // Pipeline config
        .batchSize(25)
        .flushInterval(15_000L)

        // Logging
        .logLevel(LogLevel.DEBUG)
        .logListener { level, tag, message, _ ->
            Log.d("TrackFlow", "[$level] $message")
        }

        .build()
)
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
trackflow-android-sdk/
  trackflow-core/                        Core SDK
    src/main/java/com/trackflow/core/
      TrackFlow.kt                       Main entry point
      compose/TrackScreen.kt             Compose screen tracking
      context/DefaultContextProvider.kt  Device context
      debug/DebugEventSink.kt           Debug event capture
      event/TrackFlowEvent.kt           Event interface
      event/Events.kt                   Typed event catalog
      identity/IdentityManager.kt       User identity
      logging/TrackFlowLogger.kt        Internal logger
      middleware/TrackFlowMiddleware.kt  Middleware pipeline
      payload/AnalyticsPayload.kt       Internal payload model
      pipeline/EventBatcher.kt          Event batching
      pipeline/EventDispatcher.kt       Dispatch orchestrator
      pipeline/NetworkMonitor.kt        Connectivity detection
      pipeline/OfflineEventQueue.kt     Offline persistence
      pipeline/RetryPolicy.kt           Exponential backoff
      provider/ProviderModels.kt        Provider interfaces
      session/SessionManager.kt         Session management
      util/MapExtensions.kt             Key prefix/remap utils

  trackflow-provider-firebase/           Firebase Analytics
  trackflow-provider-adobe-analytics/    Adobe Analytics + Edge Bridge
  trackflow-provider-adobe-edge/         Adobe Edge / CJA (XDM)
  trackflow-provider-mixpanel/           Mixpanel
  trackflow-provider-amplitude/          Amplitude

  testapp/                               Sample app
```

## License

Apache License 2.0

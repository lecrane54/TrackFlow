package com.trackflow.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.trackflow.core.TrackFlow
import com.trackflow.core.event.TrackFlowEvent

/**
 * A Jetpack Compose utility that automatically tracks a screen view event
 * when the composable enters the composition.
 *
 * This function uses [LaunchedEffect] keyed on [name] to fire a single
 * [TrackFlow.trackState] call whenever the screen name changes. It creates
 * an anonymous [TrackFlowEvent] with the given name and empty properties.
 *
 * Usage:
 * ```
 * @Composable
 * fun HomeScreen() {
 *     TrackScreen(name = "Home")
 *     // ... screen content
 * }
 * ```
 *
 * @param name The name of the screen to track. The [LaunchedEffect] is keyed
 *   on this value, so a new state event is sent whenever [name] changes.
 */
@Composable
fun TrackScreen(name: String) {

    LaunchedEffect(name) {
        TrackFlow.trackState(object : TrackFlowEvent {
            override val name = name
            override val properties = emptyMap<String, Any?>()
        })
    }
}

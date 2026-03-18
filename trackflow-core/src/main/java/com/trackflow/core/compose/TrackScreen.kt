package com.trackflow.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.trackflow.core.TrackFlow
import com.trackflow.core.event.TrackFlowEvent

@Composable
fun TrackScreen(name: String) {

    LaunchedEffect(name) {
        TrackFlow.trackState(object : TrackFlowEvent {
            override val name = name
            override val properties = emptyMap<String, Any?>()
        })
    }
}

package com.trackflow.core.identity

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages user identity across the SDK.
 * Stores the current user ID and traits, and notifies listeners on changes.
 */
internal class IdentityManager {

    @Volatile
    var userId: String? = null
        private set

    private val traits = mutableMapOf<String, Any?>()
    private val lock = Any()

    private val listeners = CopyOnWriteArrayList<IdentityListener>()

    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        synchronized(lock) {
            this.userId = userId
            this.traits.putAll(traits)
        }
        listeners.forEach { it.onIdentify(userId, traits) }
    }

    fun traits(): Map<String, Any?> {
        synchronized(lock) {
            return traits.toMap()
        }
    }

    fun reset() {
        synchronized(lock) {
            userId = null
            traits.clear()
        }
        listeners.forEach { it.onReset() }
    }

    fun addListener(listener: IdentityListener) {
        listeners.add(listener)
    }
}

interface IdentityListener {
    fun onIdentify(userId: String, traits: Map<String, Any?>)
    fun onReset()
}

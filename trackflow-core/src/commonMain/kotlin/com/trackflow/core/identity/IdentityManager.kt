package com.trackflow.core.identity

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Manages user identity across the SDK.
 * Stores the current user ID and traits, and notifies listeners on changes.
 *
 * All read and write operations on [userId] and traits are synchronized to
 * ensure thread safety. Listeners are stored in a synchronized list so
 * that iteration during notification is safe even if listeners are added or
 * removed concurrently.
 */
internal class IdentityManager {

    /**
     * The currently identified user's ID, or `null` if no user has been identified.
     *
     * This value is updated via [identify] and cleared via [reset].
     * Reads are volatile to ensure visibility across threads.
     */
    @Volatile
    var userId: String? = null
        private set

    /** Mutable map of user traits (e.g., email, plan, name). Guarded by [lock]. */
    private val traits = mutableMapOf<String, Any?>()

    /** Synchronization lock for [userId] and [traits] mutations. */
    private val lock = SynchronizedObject()

    /** Thread-safe list of identity change listeners. Guarded by [listenersLock]. */
    private val listeners = mutableListOf<IdentityListener>()

    /** Synchronization lock for [listeners] mutations. */
    private val listenersLock = SynchronizedObject()

    /**
     * Identifies the current user with the given [userId] and optional [traits].
     *
     * Any existing traits are merged with the new ones (new values overwrite
     * existing keys). After updating the internal state, all registered
     * [IdentityListener] instances are notified via [IdentityListener.onIdentify].
     *
     * @param userId The unique identifier for the user.
     * @param traits A map of user traits to associate with this identity. Defaults to an empty map.
     */
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        synchronized(lock) {
            this.userId = userId
            this.traits.putAll(traits)
        }
        val snapshot = synchronized(listenersLock) { listeners.toList() }
        snapshot.forEach { it.onIdentify(userId, traits) }
    }

    /**
     * Returns a snapshot of the current user traits as an immutable map.
     *
     * The returned map is a defensive copy; modifications to it will not
     * affect the internally stored traits.
     *
     * @return An immutable [Map] of the current user traits.
     */
    fun traits(): Map<String, Any?> {
        synchronized(lock) {
            return traits.toMap()
        }
    }

    /**
     * Resets the identity by clearing the [userId] and all stored traits.
     *
     * After clearing internal state, all registered [IdentityListener]
     * instances are notified via [IdentityListener.onReset].
     */
    fun reset() {
        synchronized(lock) {
            userId = null
            traits.clear()
        }
        val snapshot = synchronized(listenersLock) { listeners.toList() }
        snapshot.forEach { it.onReset() }
    }

    /**
     * Registers a listener to be notified of identity changes.
     *
     * The listener will receive callbacks on [IdentityListener.onIdentify]
     * and [IdentityListener.onReset] whenever the identity state changes.
     *
     * @param listener The [IdentityListener] to register.
     */
    fun addListener(listener: IdentityListener) {
        synchronized(listenersLock) {
            listeners.add(listener)
        }
    }
}

/**
 * Listener interface for observing changes to the user identity managed by [IdentityManager].
 *
 * Implementations receive callbacks when a user is identified or when the identity is reset.
 */
interface IdentityListener {
    /**
     * Called when a user is identified.
     *
     * @param userId The unique identifier of the newly identified user.
     * @param traits The user traits associated with this identification call.
     */
    fun onIdentify(userId: String, traits: Map<String, Any?>)

    /**
     * Called when the current user identity is reset (e.g., on logout).
     */
    fun onReset()
}

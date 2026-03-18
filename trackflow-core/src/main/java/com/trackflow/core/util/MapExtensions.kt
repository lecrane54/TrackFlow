package com.trackflow.core.util

/**
 * Returns a new map with all keys prefixed by the given [prefix].
 * If [prefix] is null or empty, the original map is returned unchanged.
 */
fun <V> Map<String, V>.prefixKeys(prefix: String?): Map<String, V> {
    if (prefix.isNullOrEmpty()) return this
    return mapKeys { (key, _) -> "$prefix$key" }
}

/**
 * Remaps property keys using the given [keyMap].
 * Keys found in [keyMap] are replaced with their mapped value.
 * Keys not in [keyMap] are passed through unchanged.
 * If [keyMap] is null or empty, the original map is returned unchanged.
 *
 * Example:
 * ```
 * val keyMap = mapOf("product_id" to "eVar21", "price" to "eVar22")
 * mapOf("product_id" to "sku_123", "color" to "red")
 *     .remapKeys(keyMap)
 * // Result: {"eVar21" to "sku_123", "color" to "red"}
 * ```
 */
fun <V> Map<String, V>.remapKeys(keyMap: Map<String, String>?): Map<String, V> {
    if (keyMap.isNullOrEmpty()) return this
    return mapKeys { (key, _) -> keyMap[key] ?: key }
}

package com.trackflow.core.util

/**
 * Utility extension functions for [Map] transformations commonly used
 * when preparing analytics event properties for provider-specific formats.
 */

/**
 * Returns a new map with all keys prefixed by the given [prefix].
 * If [prefix] is null or empty, the original map is returned unchanged.
 *
 * @param V The value type of the map entries.
 * @param prefix The string to prepend to every key, or `null`/empty to skip prefixing.
 * @return A new [Map] with prefixed keys, or the original map if [prefix] is null or empty.
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
 *
 * @param V The value type of the map entries.
 * @param keyMap A mapping from original key names to replacement key names,
 *   or `null`/empty to skip remapping.
 * @return A new [Map] with remapped keys, or the original map if [keyMap] is null or empty.
 */
fun <V> Map<String, V>.remapKeys(keyMap: Map<String, String>?): Map<String, V> {
    if (keyMap.isNullOrEmpty()) return this
    return mapKeys { (key, _) -> keyMap[key] ?: key }
}

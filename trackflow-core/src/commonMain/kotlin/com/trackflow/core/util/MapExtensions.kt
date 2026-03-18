package com.trackflow.core.util

/**
 * Remaps property keys using the given [keyMap].
 * Keys found in [keyMap] are replaced with their mapped value.
 * Keys not in [keyMap] are passed through unchanged.
 * If [keyMap] is null or empty, the original map is returned unchanged.
 *
 * Only allocates a new map when at least one key actually needs remapping.
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
    // Fast path: check if any key actually needs remapping before allocating
    val needsRemap = keys.any { it in keyMap }
    if (!needsRemap) return this
    val result = LinkedHashMap<String, V>(size)
    for ((key, value) in this) {
        result[keyMap[key] ?: key] = value
    }
    return result
}

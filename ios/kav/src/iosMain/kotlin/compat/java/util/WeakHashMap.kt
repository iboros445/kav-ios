// java.util.WeakHashMap: a plain map here. Upstream keys it by the map's Style, of which a screen makes a handful.
package java.util

class WeakHashMap<K, V> private constructor(private val m: MutableMap<K, V>) : MutableMap<K, V> by m {
    constructor() : this(kotlin.collections.LinkedHashMap())
}

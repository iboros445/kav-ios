package android.util

/** Size-bounded cache, least recently used out first. `sizeOf` defaults to 1 per entry. Thread-safe. */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()   // insertion order is refreshed on access, so the head is the oldest use
    private val lock = platform.Foundation.NSLock()
    private var size = 0

    protected open fun sizeOf(key: K, value: V): Int = 1

    fun get(key: K): V? = sync { map.remove(key)?.also { map[key] = it } }

    fun put(key: K, value: V): V? = sync {
        val old = map.remove(key)
        if (old != null) size -= sizeOf(key, old)
        map[key] = value
        size += sizeOf(key, value)
        trim()
        old
    }

    fun remove(key: K): V? = sync { map.remove(key)?.also { size -= sizeOf(key, it) } }
    fun evictAll() = sync { map.clear(); size = 0 }
    fun size(): Int = sync { size }

    private fun trim() {
        while (size > maxSize && map.isNotEmpty()) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
            size -= sizeOf(eldest.key, eldest.value)
        }
    }

    private inline fun <T> sync(f: () -> T): T { lock.lock(); try { return f() } finally { lock.unlock() } }
}

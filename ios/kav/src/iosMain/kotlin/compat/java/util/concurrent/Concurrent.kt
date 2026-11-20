// java.util.concurrent for Kotlin/Native: NSCondition/NSLock for state, GCD for threads.
package java.util.concurrent

import java.lang.Runnable
import kotlinx.cinterop.convert
import platform.Foundation.NSCondition
import platform.Foundation.NSLock
import platform.darwin.DISPATCH_QUEUE_CONCURRENT
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

fun interface Callable<V> { fun call(): V }

class ExecutionException(cause: Throwable?) : Exception(cause?.message, cause)
class TimeoutException(message: String? = null) : Exception(message)
class CancellationException(message: String? = null) : IllegalStateException(message)

enum class TimeUnit(private val nanos: Long) {
    NANOSECONDS(1), MICROSECONDS(1_000), MILLISECONDS(1_000_000), SECONDS(1_000_000_000),
    MINUTES(60_000_000_000), HOURS(3_600_000_000_000), DAYS(86_400_000_000_000);

    fun toMillis(d: Long): Long = d * nanos / 1_000_000
    fun toSeconds(d: Long): Long = d * nanos / 1_000_000_000
    fun toNanos(d: Long): Long = d * nanos
}

interface Future<V> {
    fun get(): V
    fun get(timeout: Long, unit: TimeUnit): V
    fun isDone(): Boolean
    fun cancel(mayInterruptIfRunning: Boolean): Boolean
    fun isCancelled(): Boolean
}

interface Executor { fun execute(command: Runnable) }

interface ExecutorService : Executor {
    fun <T> submit(task: Callable<T>): Future<T>
    fun submit(task: Runnable): Future<*>
    fun shutdown()
    fun shutdownNow(): List<Runnable>
    fun isShutdown(): Boolean
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean
}

private class FutureTask<V> : Future<V> {
    private val lock = NSCondition()
    private var done = false
    private var cancelled = false
    private var value: V? = null
    private var failure: Throwable? = null

    fun complete(v: V) { lock.lock(); if (!done) { value = v; done = true }; lock.broadcast(); lock.unlock() }
    fun fail(t: Throwable) { lock.lock(); if (!done) { failure = t; done = true }; lock.broadcast(); lock.unlock() }

    @Suppress("UNCHECKED_CAST")
    override fun get(): V {
        lock.lock()
        try {
            while (!done) lock.wait()
            if (cancelled) throw CancellationException()
            failure?.let { throw ExecutionException(it) }
            return value as V
        } finally { lock.unlock() }
    }

    override fun get(timeout: Long, unit: TimeUnit): V = get()
    override fun isDone(): Boolean { lock.lock(); try { return done } finally { lock.unlock() } }
    override fun isCancelled(): Boolean { lock.lock(); try { return cancelled } finally { lock.unlock() } }
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        lock.lock()
        try { if (done) return false; cancelled = true; done = true; lock.broadcast(); return true } finally { lock.unlock() }
    }
}

private class Pool(threads: Int) : ExecutorService {
    private val queue = dispatch_queue_create("kav.pool", DISPATCH_QUEUE_CONCURRENT)
    private val permits = dispatch_semaphore_create(threads.coerceAtLeast(1).convert())
    private val lock = NSLock()
    private var down = false

    override fun execute(command: Runnable) { submit(command) }

    override fun <T> submit(task: Callable<T>): Future<T> {
        lock.lock(); val closed = down; lock.unlock()
        if (closed) throw IllegalStateException("Executor has been shut down")
        val f = FutureTask<T>()
        dispatch_async(queue) {
            dispatch_semaphore_wait(permits, DISPATCH_TIME_FOREVER)
            try { if (!f.isCancelled()) f.complete(task.call()) } catch (t: Throwable) { f.fail(t) } finally { dispatch_semaphore_signal(permits) }
        }
        return f
    }

    override fun submit(task: Runnable): Future<*> = submit(Callable { task.run() })
    override fun shutdown() { lock.lock(); down = true; lock.unlock() }
    override fun shutdownNow(): List<Runnable> { shutdown(); return emptyList() }
    override fun isShutdown(): Boolean { lock.lock(); try { return down } finally { lock.unlock() } }
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}

object Executors {
    fun newFixedThreadPool(threads: Int): ExecutorService = Pool(threads)
    fun newCachedThreadPool(): ExecutorService = Pool(32)
    fun newSingleThreadExecutor(): ExecutorService = Pool(1)
}

/** A synchronized map. Iteration (keys/values/entries) works on a snapshot, so it never throws on concurrent change. */
class ConcurrentHashMap<K, V>(@Suppress("UNUSED_PARAMETER") initialCapacity: Int = 16) : MutableMap<K, V> {
    private val lock = NSLock()
    private val m = HashMap<K, V>()

    private inline fun <T> sync(f: () -> T): T { lock.lock(); try { return f() } finally { lock.unlock() } }

    override val size: Int get() = sync { m.size }
    override fun isEmpty(): Boolean = sync { m.isEmpty() }
    override fun containsKey(key: K): Boolean = sync { m.containsKey(key) }
    override fun containsValue(value: V): Boolean = sync { m.containsValue(value) }
    override fun get(key: K): V? = sync { m[key] }
    override fun put(key: K, value: V): V? = sync { m.put(key, value) }
    override fun remove(key: K): V? = sync { m.remove(key) }
    override fun putAll(from: Map<out K, V>) { sync { m.putAll(from) } }
    override fun clear() { sync { m.clear() } }
    override val keys: MutableSet<K> get() = sync { LinkedHashSet(m.keys) }
    override val values: MutableCollection<V> get() = sync { ArrayList(m.values) }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = sync { LinkedHashMap(m).entries }

    fun remove(key: K, value: V): Boolean = sync { if (m[key] == value) { m.remove(key); true } else false }
    fun putIfAbsent(key: K, value: V): V? = sync { m[key] ?: run { m[key] = value; null } }
    fun computeIfAbsent(key: K, f: (K) -> V): V = sync { m[key] ?: f(key).also { m[key] = it } }
    fun getOrDefault(key: Any?, default: V): V = sync { @Suppress("UNCHECKED_CAST") (m[key as K] ?: default) }
    override fun toString(): String = sync { m.toString() }

    companion object {
        fun <T> newKeySet(): MutableSet<T> = object : AbstractMutableSet<T>() {
            private val inner = ConcurrentHashMap<T, Boolean>()
            override val size: Int get() = inner.size
            override fun add(element: T): Boolean = inner.put(element, true) == null
            override fun contains(element: T): Boolean = inner.containsKey(element)
            override fun remove(element: T): Boolean = inner.remove(element) != null
            override fun iterator(): MutableIterator<T> = inner.keys.iterator()
            override fun clear() = inner.clear()
        }
    }
}

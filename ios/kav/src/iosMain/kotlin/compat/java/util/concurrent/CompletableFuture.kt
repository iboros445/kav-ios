// java.util.concurrent.CompletableFuture, the blocking slice: complete it on one thread, get() it on another.
package java.util.concurrent

import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

open class CompletableFuture<T> {
    private val lock = NSCondition()
    private var done = false
    private var value: T? = null
    private var failure: Throwable? = null

    fun complete(v: T): Boolean = settle { value = v }
    fun completeExceptionally(t: Throwable): Boolean = settle { failure = t }
    fun cancel(@Suppress("UNUSED_PARAMETER") mayInterrupt: Boolean): Boolean = settle { failure = CancellationException("Cancelled") }
    val isDone: Boolean get() { lock.lock(); try { return done } finally { lock.unlock() } }

    private inline fun settle(set: () -> Unit): Boolean {
        lock.lock()
        try { if (done) return false; set(); done = true; lock.broadcast(); return true } finally { lock.unlock() }
    }

    fun get(): T { lock.lock(); try { while (!done) lock.wait(); return result() } finally { lock.unlock() } }

    fun get(timeout: Long, unit: TimeUnit): T {
        val until = NSDate.dateWithTimeIntervalSinceNow(unit.toMillis(timeout) / 1000.0)
        lock.lock()
        try {
            while (!done) if (!lock.waitUntilDate(until)) break
            if (!done) throw TimeoutException("Timed out")
            return result()
        } finally { lock.unlock() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun result(): T {
        failure?.let { throw if (it is CancellationException) it else ExecutionException(it) }
        return value as T
    }
}

package java.util.concurrent.atomic

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.concurrent.AtomicReference

class AtomicInteger(initial: Int = 0) {
    private val a = AtomicInt(initial)
    fun get(): Int = a.value
    fun set(v: Int) { a.value = v }
    fun incrementAndGet(): Int = a.incrementAndGet()
    fun decrementAndGet(): Int = a.decrementAndGet()
    fun getAndIncrement(): Int = a.getAndIncrement()
    fun getAndDecrement(): Int = a.getAndDecrement()
    fun addAndGet(d: Int): Int = a.addAndGet(d)
    fun getAndAdd(d: Int): Int = a.getAndAdd(d)
    fun getAndSet(v: Int): Int { while (true) { val c = a.value; if (a.compareAndSet(c, v)) return c } }
    fun compareAndSet(expect: Int, update: Int): Boolean = a.compareAndSet(expect, update)
    override fun toString(): String = a.value.toString()
}

class AtomicLong(initial: Long = 0) {
    private val a = AtomicLong(initial)
    fun get(): Long = a.value
    fun set(v: Long) { a.value = v }
    fun incrementAndGet(): Long = a.incrementAndGet()
    fun getAndIncrement(): Long = a.getAndIncrement()
    fun addAndGet(d: Long): Long = a.addAndGet(d)
    fun compareAndSet(expect: Long, update: Long): Boolean = a.compareAndSet(expect, update)
    override fun toString(): String = a.value.toString()
}

class AtomicBoolean(initial: Boolean = false) {
    private val a = AtomicInt(if (initial) 1 else 0)
    fun get(): Boolean = a.value != 0
    fun set(v: Boolean) { a.value = if (v) 1 else 0 }
    fun getAndSet(v: Boolean): Boolean { val n = if (v) 1 else 0; while (true) { val c = a.value; if (a.compareAndSet(c, n)) return c != 0 } }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = a.compareAndSet(if (expect) 1 else 0, if (update) 1 else 0)
}

class AtomicReference<V>(initial: V? = null) {
    private val a = AtomicReference<V?>(initial)
    fun get(): V? = a.value
    fun set(v: V?) { a.value = v }
    fun compareAndSet(expect: V?, update: V?): Boolean = a.compareAndSet(expect, update)
    fun getAndSet(v: V?): V? { while (true) { val c = a.value; if (a.compareAndSet(c, v)) return c } }
}

package android.os

import java.lang.Runnable
import platform.Foundation.NSProcessInfo
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.concurrent.AtomicInt

class Bundle {
    private val map = HashMap<String, Any?>()
    fun putBoolean(k: String, v: Boolean) { map[k] = v }
    fun putInt(k: String, v: Int) { map[k] = v }
    fun putLong(k: String, v: Long) { map[k] = v }
    fun putString(k: String, v: String?) { map[k] = v }
    fun getBoolean(k: String, d: Boolean = false): Boolean = map[k] as? Boolean ?: d
    fun getInt(k: String, d: Int = 0): Int = map[k] as? Int ?: d
    fun getLong(k: String, d: Long = 0): Long = map[k] as? Long ?: d
    fun getString(k: String): String? = map[k] as? String
    fun containsKey(k: String): Boolean = map.containsKey(k)
}

/** There is one looper: the main queue. */
class Looper private constructor() {
    companion object {
        private val MAIN = Looper()
        fun getMainLooper(): Looper = MAIN
    }
}

class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper = Looper.getMainLooper()) {
    constructor() : this(Looper.getMainLooper())

    private class Pending(val runnable: Runnable) { val cancelled = AtomicInt(0) }

    private val pending = ArrayList<Pending>()

    fun post(r: Runnable): Boolean = postDelayed(r, 0)

    fun postDelayed(r: Runnable, delayMs: Long): Boolean {
        val p = Pending(r)
        pending.add(p)
        val run = {
            if (p.cancelled.value == 0) { pending.remove(p); r.run() }
        }
        if (delayMs <= 0) dispatch_async(dispatch_get_main_queue(), run)
        else dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayMs * 1_000_000L), dispatch_get_main_queue(), run)
        return true
    }

    fun removeCallbacks(r: Runnable) {
        pending.filter { it.runnable === r }.forEach { it.cancelled.value = 1; pending.remove(it) }
    }

    fun removeCallbacksAndMessages(token: Any?) {
        pending.toList().forEach { it.cancelled.value = 1 }
        pending.clear()
    }
}

object SystemClock {
    fun elapsedRealtimeNanos(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()
    fun elapsedRealtime(): Long = elapsedRealtimeNanos() / 1_000_000
    fun uptimeMillis(): Long = elapsedRealtime()
}

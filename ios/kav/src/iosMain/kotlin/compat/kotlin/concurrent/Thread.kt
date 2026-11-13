// kotlin.concurrent.thread, which the JVM stdlib has and Native's lacks. Upstream calls it by its full name (Net reads
// the week on a few threads, RiderProxy drops connections off the main one), so it has to live in kotlin.concurrent:
// compiled with -Xallow-kotlin-package (ios/kav/build.gradle.kts, ios/tools/klib_check.sh).
@file:Suppress("UNUSED_PARAMETER")
package kotlin.concurrent

import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_group_async
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_wait

// What thread() returns: upstream only joins it.
class KavThread internal constructor(val name: String?, private val block: () -> Unit) {
    private val group = dispatch_group_create()
    private var started = false

    fun start() {
        if (started) return
        started = true
        dispatch_group_async(group, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { block() }
    }

    fun join() { if (started) dispatch_group_wait(group, DISPATCH_TIME_FOREVER) }
}

fun thread(
    start: Boolean = true,
    isDaemon: Boolean = false,
    contextClassLoader: Any? = null,
    name: String? = null,
    priority: Int = -1,
    block: () -> Unit,
): KavThread = KavThread(name, block).also { if (start) it.start() }

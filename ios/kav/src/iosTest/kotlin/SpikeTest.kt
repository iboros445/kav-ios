package uk.noammm.kav.compat.selftest

import java.io.File
import java.util.Locale
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

// Phase 0 spike. If this compiles and passes, Kotlin/Native accepts `package java.*` / `org.json`
// shims and the stamped JvmStd template resolves in a plain package (this one).
class SpikeTest {
    @Test fun javaPackagesAreAccepted() {
        assertEquals("c.kav", File(File("/a/b"), "c.kav").name)
        assertEquals("""{"k":"v","n":3}""", JSONObject().put("k", "v").put("n", 3).toString())
        assertEquals("en-US", Locale.US.toLanguageTag())
    }

    @Test fun stringFormat() {
        assertEquals("5:07", String.format(Locale.US, "%d:%02d", 5, 7))
        assertEquals("12.35", String.format(Locale.US, "%.2f", 12.345))
        assertEquals("-0.5", String.format(Locale.US, "%.1f", -0.5))
        assertEquals("35.12345,32.00010", String.format(Locale.US, "%.5f,%.5f", 35.12345, 32.0001))
        assertEquals("%2B", String.format("%%2B"))
        assertEquals("b a", "%2\$s %1\$s".format("a", "b"))
        assertEquals("  7|7  |007", String.format("%3d|%-3d|%03d", 7, 7, 7))
        assertEquals("ff", "%02x".format((-1).toByte()))
        assertEquals("05", "%02x".format(5.toByte()))
        assertEquals("80", "%02x".format((-128).toByte()))
    }

    @Test fun systemAndMath() {
        check(System.currentTimeMillis() > 1_700_000_000_000L)
        check(System.nanoTime() >= 0L)
        assertEquals(4L, Math.round(3.5))
        assertEquals("hé", String("hé".toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertEquals(3, "abc".toByteArray().size)
    }
}

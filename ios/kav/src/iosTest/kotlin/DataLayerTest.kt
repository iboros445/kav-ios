@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package uk.noammm.kav.data

import android.content.KavApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import android.graphics.Color
import androidx.core.text.HtmlCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject
import platform.Foundation.NSProcessInfo

// Phase 1: the shims against real iOS Foundation, and the upstream data layer on real data.
// Needs SIMCTL_CHILD_KAV_ASSETS_DIR=<dir containing il.kav> (ios.yml sets it).
class DataLayerTest {
    private val ctx = KavApplicationContext
    private fun scratch(name: String) = File(ctx.cacheDir, "test-$name").also { it.deleteRecursively(); it.mkdirs() }

    @Test fun files() {
        val d = scratch("files")
        val f = File(d, "a.txt")
        assertTrue(!f.exists())
        f.writeText("héllo\n")
        assertEquals("héllo\n", f.readText())
        assertEquals(7L, f.length())
        FileOutputStream(f, true).use { it.write("more".encodeToByteArray()) }
        assertEquals("héllo\nmore", f.readText())
        val g = File(d, "b.txt")
        assertTrue(f.renameTo(g))
        assertTrue(!f.exists() && g.exists() && g.isFile && !g.isDirectory && d.isDirectory)
        assertEquals(listOf("b.txt"), d.listFiles()!!.map { it.name })
        assertTrue(File(d, "x/y/z").mkdirs())
        assertEquals(listOf("b.txt", "x"), d.list()!!.toList())
        // partial reads
        FileInputStream(g).use { s ->
            val buf = ByteArray(4)
            assertEquals(4, s.read(buf, 0, 4))
            assertEquals("hé", buf.decodeToString(0, 3))
            assertEquals("lo\nmore", s.readBytes().decodeToString())
        }
        assertTrue(g.delete() && !g.exists())
        assertTrue(d.deleteRecursively() && !d.exists())
        assertEquals("c.kav", File(File("/a/b"), "c.kav").name)
        assertEquals("/a/b", File("/a/b/c.kav").parent)
        assertNull(File("c").parent)
    }

    @Test fun assetsFromEnvironment() {
        val dir = NSProcessInfo.processInfo.environment["KAV_ASSETS_DIR"] as? String
        assertNotNull(dir, "KAV_ASSETS_DIR not passed to the test process (set SIMCTL_CHILD_KAV_ASSETS_DIR)")
        assertTrue(ctx.assets.open("il.kav").use { it.readBytes().size } > 1_000_000)
    }

    @Test fun timetableParses() {
        val net = ctx.assets.open("il.kav").use { Net.read(it) }
        println("il.kav: ${net.nStops} stops, ${net.nRoutes} routes")
        assertTrue(net.nStops > 5_000, "stops ${net.nStops}")
        assertTrue(net.nRoutes > 500, "routes ${net.nRoutes}")
        val hits = net.searchStops("תל אביב", 20)
        assertTrue(hits.isNotEmpty(), "Hebrew stop search found nothing")
    }

    @Test fun gzipOfRealData() {
        val packed = kotlin.io.encoding.Base64.decode("H4sIAAAAAAAA/8tIzcnJV8hOLFO4vvz6VIWMUe4od5Q7yh3ljnJHuaPcUe4od5Q7+LgATavN4rgLAAA=")
        val text = GZIPInputStream(packed.inputStream()).readBytes().decodeToString()
        assertEquals("hello kav קו ".repeat(200), text)
    }

    @Test fun json() {
        val o = JSONObject("""{"a":[1,2,{"b":"ç"}],"n":null,"t":true}""")
        assertEquals(3, o.getJSONArray("a").length())
        assertEquals("ç", o.getJSONArray("a").getJSONObject(2).getString("b"))
        val f = File(scratch("json"), "o.json")
        f.writeText(o.toString(2))
        assertEquals(o.toString(), JSONObject(f.readText()).toString())
    }

    @Test fun urisAndLinks() {
        val u = URI("https://moovitapp.com/directions?dest_lat=32.1&dest_lon=34.8&dest_name=x%20y")
        assertEquals("moovitapp.com", u.host)
        assertEquals("/directions", u.path)
        assertEquals("https://a.com/b/d", URI("https://a.com/b/c").resolve("d").toString())
        assertEquals("https://a.com/x", URI("https://a.com/b/c").resolve("/x").toString())
        assertEquals("https://a.com/b/e?q=1", URI("https://a.com/b/c?z").resolve("e?q=1").toString())
        assertEquals("https://a.com/x", URL(URL("https://a.com/b/c"), "../x").toString())
    }

    @Test fun uuidDateFormat() {
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(UUID.randomUUID().toString()))
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        assertEquals("1970-01-02 00:00", f.format(Date(86_400_000L)))
        assertEquals(86_400_000L, f.parse("1970-01-02 00:00").time)
    }

    @Test fun preferences() {
        val p = ctx.getSharedPreferences("test-prefs", 0)
        p.edit().clear().putString("s", "x").putInt("i", 5).putLong("l", 1L shl 40).putBoolean("b", true).putFloat("f", 1.5f).apply()
        assertEquals("x", p.getString("s", null))
        assertEquals(5, p.getInt("i", 0))
        assertEquals(1L shl 40, p.getLong("l", 0))
        assertEquals(true, p.getBoolean("b", false))
        assertEquals(1.5f, p.getFloat("f", 0f))
        assertEquals("d", p.getString("missing", "d"))
        p.edit().remove("s").apply()
        assertTrue(!p.contains("s"))
    }

    @Test fun concurrency() {
        val pool = Executors.newFixedThreadPool(4)
        val futures = (1..20).map { i -> pool.submit<Int> { i * i } }
        assertEquals((1..20).sumOf { it * it }, futures.sumOf { it.get() })
        pool.shutdown()
        val m = ConcurrentHashMap<Int, String>()
        (1..50).map { i -> Executors.newFixedThreadPool(1).submit<Unit> { m[i] = "v$i" } }.forEach { it.get() }
        assertEquals(50, m.size)
        assertEquals("v7", m[7])
    }

    // ---- network (GitHub runners have it) ----

    @Test fun httpGetWithGzipHandledTheUpstreamWay() {
        val c = (URL("https://example.com/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 20000; readTimeout = 25000
            setRequestProperty("Accept-Encoding", "gzip;q=1.0, identity;q=0.5")
        }
        val code = c.responseCode
        assertEquals(200, code)
        println("example.com Content-Encoding: ${c.contentEncoding}")
        // exactly what Moovit.get does with the body
        val raw = c.inputStream.use { s ->
            val bytes = s.readBytes()
            if (c.contentEncoding == "gzip") GZIPInputStream(bytes.inputStream()).readBytes() else bytes
        }
        assertTrue(raw.decodeToString().contains("Example Domain"))
    }

    @Test fun httpRedirectRangeAndStreaming() {
        // MapFile resumes a download with a Range header through GitHub's redirect to the CDN.
        val c = (URL(MapFile.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Kav")
            setRequestProperty("Range", "bytes=0-4095")
        }
        assertEquals(206, c.responseCode)
        val out = ByteArrayOutputStream()
        c.inputStream.use { input ->
            val buf = ByteArray(1000)
            while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
        }
        assertEquals(4096, out.size())
        assertEquals("PMTiles", out.toByteArray().decodeToString(0, 7))
    }

    @Test fun httpNoFollowAndErrorStream() {
        val c = (URL("https://github.com/ImNoammm/kav/releases/latest").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Kav")
        }
        assertEquals(302, c.responseCode)
        assertTrue(c.getHeaderField("Location")!!.contains("/releases/tag/"))
        c.disconnect()
        val missing = (URL("https://github.com/ImNoammm/kav/this-does-not-exist-${UUID.randomUUID()}").openConnection() as HttpURLConnection)
        assertEquals(404, missing.responseCode)
        assertNotNull(missing.errorStream)
    }

    @Test fun httpPost() {
        val c = (URL("https://httpbingo.org/post").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20000; readTimeout = 25000
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        c.outputStream.use { it.write(byteArrayOf(1, 2, 3, 4)) }
        assertEquals(200, c.responseCode)
        assertTrue(c.inputStream.use { it.readBytes() }.decodeToString().contains("\"data\""))
    }

    @Test fun calendarAndJavaTime() {
        val tz = TimeZone.getTimeZone("Asia/Jerusalem")
        val c = Calendar.getInstance(tz).apply { timeInMillis = 1_735_725_600_000L }   // 2025-01-01 10:00Z = 12:00 in Israel
        assertEquals(2025, c.get(Calendar.YEAR))
        assertEquals(0, c.get(Calendar.MONTH))
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(4, c.get(Calendar.DAY_OF_WEEK))      // a Wednesday
        assertEquals(1, c.get(Calendar.DAY_OF_YEAR))
        c.add(Calendar.DAY_OF_YEAR, 31)
        assertEquals(1, c.get(Calendar.MONTH)); assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY)); assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0L, c.timeInMillis % 1000)
        val zone = ZoneId.of("Asia/Jerusalem")
        val a = Instant.ofEpochSecond(1_735_725_600L).atZone(zone)
        val b = Instant.ofEpochSecond(1_735_725_600L + 3 * 86_400).atZone(zone)
        assertEquals(3L, ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate()))
        assertEquals("Wednesday", DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("en-GB")).format(a))
        assertEquals(12 * 3600, Calendar.getInstance(tz).apply { timeInMillis = 1_735_725_600_000L }.let { it.get(Calendar.HOUR_OF_DAY) * 3600 + it.get(Calendar.MINUTE) * 60 + it.get(Calendar.SECOND) })
    }

    @Test fun cipherBase64ColorHtml() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val enc = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val packed = enc.iv!! + enc.doFinal("sealed ✓".encodeToByteArray())
        val text = Base64.encodeToString(packed, Base64.NO_WRAP)
        val back = Base64.decode(text, Base64.NO_WRAP)
        val dec = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, back, 0, 12)) }
        assertEquals("sealed ✓", dec.doFinal(back, 12, back.size - 12).decodeToString())
        assertEquals(0xFF9ABEFF.toInt(), Color.rgb(0x9A, 0xBE, 0xFF))
        val hsv = FloatArray(3); Color.colorToHSV(Color.rgb(255, 0, 0), hsv)
        assertEquals(0f, hsv[0]); assertEquals(1f, hsv[1]); assertEquals(1f, hsv[2])
        assertEquals(Color.rgb(0, 255, 0), Color.HSVToColor(floatArrayOf(120f, 1f, 1f)))
        assertEquals("Line one\nSecond & more", HtmlCompat.fromHtml("<p>Line one</p><b>Second</b> &amp; more", HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim())
    }
}

package android.graphics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Color {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TRANSPARENT = 0

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun alpha(c: Int): Int = (c ushr 24) and 0xFF
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun parseColor(s: String): Int {
        require(s.startsWith("#")) { "Unknown color" }
        val v = s.substring(1).toLong(16)
        return if (s.length == 7) (v or 0xFF000000L).toInt() else v.toInt()
    }

    fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = red(color) / 255f; val g = green(color) / 255f; val b = blue(color) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> ((g - b) / d) % 6f
            mx == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        if (h < 0f) h += 360f
        hsv[0] = h; hsv[1] = if (mx == 0f) 0f else d / mx; hsv[2] = mx
    }

    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)

    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f; val s = hsv[1].coerceIn(0f, 1f); val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s; val x = c * (1f - abs((h / 60f) % 2f - 1f)); val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
    }
}

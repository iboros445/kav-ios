package androidx.core.graphics

import kotlin.math.pow

object ColorUtils {
    /** WCAG relative luminance, 0..1. */
    fun calculateLuminance(color: Int): Double {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v < 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin((color shr 16) and 0xFF) + 0.7152 * lin((color shr 8) and 0xFF) + 0.0722 * lin(color and 0xFF)
    }

    fun setAlphaComponent(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun blendARGB(a: Int, b: Int, ratio: Float): Int {
        val r = 1f - ratio
        fun ch(shift: Int) = ((((a shr shift) and 0xFF) * r + ((b shr shift) and 0xFF) * ratio)).toInt().coerceIn(0, 255) shl shift
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

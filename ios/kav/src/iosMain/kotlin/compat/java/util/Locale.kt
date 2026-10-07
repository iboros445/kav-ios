// Minimal java.util.Locale. Formatting upstream always passes Locale.US or T.locale, and the
// shims format with fixed rules, so this only has to carry a language tag around.
package java.util

class Locale private constructor(private val tag: String) {
    val language: String get() = tag.substringBefore('-')
    fun toLanguageTag(): String = tag
    override fun toString(): String = tag.replace('-', '_')
    override fun equals(other: Any?): Boolean = other is Locale && other.tag == tag
    override fun hashCode(): Int = tag.hashCode()

    companion object {
        val US = Locale("en-US")
        val ENGLISH = Locale("en")
        val ROOT = Locale("und")
        fun forLanguageTag(tag: String): Locale = Locale(tag)
        fun getDefault(): Locale = US
    }
}

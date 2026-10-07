// Minimal java.util.Locale. Formatting upstream always passes Locale.US or T.locale, and the
// shims format with fixed rules, so this only has to carry a language tag around.
package java.util

class Locale private constructor(private val tag: String, @Suppress("UNUSED_PARAMETER") marker: Unit) {
    constructor(language: String) : this(language, Unit)
    constructor(language: String, country: String) : this(if (country.isEmpty()) language else "$language-$country", Unit)

    val language: String get() = tag.substringBefore('-')
    fun toLanguageTag(): String = tag
    override fun toString(): String = tag.replace('-', '_')
    override fun equals(other: Any?): Boolean = other is Locale && other.tag == tag
    override fun hashCode(): Int = tag.hashCode()

    companion object {
        val US = Locale("en-US", Unit)
        val ENGLISH = Locale("en", Unit)
        val ROOT = Locale("und", Unit)
        fun forLanguageTag(tag: String): Locale = Locale(tag, Unit)
        fun getDefault(): Locale = US
    }
}

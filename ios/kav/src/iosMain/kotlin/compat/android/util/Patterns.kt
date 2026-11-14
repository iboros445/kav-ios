package android.util

/** android.util.Patterns, with the java.util.regex calls upstream makes on them (matcher(...).matches()). */
object Patterns {
    class Pattern internal constructor(private val regex: Regex) {
        fun matcher(input: CharSequence): Matcher = Matcher(regex, input)
    }
    class Matcher internal constructor(private val regex: Regex, private val input: CharSequence) {
        fun matches(): Boolean = regex.matches(input)
        fun find(): Boolean = regex.containsMatchIn(input)
    }

    // Android's own expression.
    val EMAIL_ADDRESS = Pattern(Regex(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
    ))
    val WEB_URL = Pattern(Regex("https?://\\S+"))
}

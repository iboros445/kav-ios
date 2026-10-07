package java.text

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.timeZoneWithName
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping

class ParseException(message: String?, val errorOffset: Int) : Exception(message)

/** Pattern letters are Unicode TR35, which covers the ones Java code uses (yyyy MM dd HH mm ss EEE MMM d). */
class SimpleDateFormat(pattern: String, locale: Locale = Locale.getDefault()) {
    private val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        this.locale = NSLocale.localeWithLocaleIdentifier(locale.toLanguageTag())
        timeZone = NSTimeZone.localTimeZone
    }

    var timeZone: TimeZone
        get() = TimeZone(formatter.timeZone.name)
        set(v) { formatter.timeZone = NSTimeZone.timeZoneWithName(v.id) ?: NSTimeZone.localTimeZone }

    fun format(date: Date): String = formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(date.time / 1000.0))

    fun format(millis: Long): String = format(Date(millis))

    fun parse(text: String): Date {
        val d = formatter.dateFromString(text) ?: throw ParseException("Unparseable date: \"$text\"", 0)
        return Date((d.timeIntervalSince1970 * 1000).toLong())
    }
}

object Normalizer {
    enum class Form { NFC, NFD, NFKC, NFKD }

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun normalize(src: CharSequence, form: Form): String = (src.toString() as NSString).let {
        when (form) {
            Form.NFD -> it.decomposedStringWithCanonicalMapping
            Form.NFC -> it.precomposedStringWithCanonicalMapping
            Form.NFKD -> it.decomposedStringWithCompatibilityMapping
            Form.NFKC -> it.precomposedStringWithCompatibilityMapping
        }
    }
}

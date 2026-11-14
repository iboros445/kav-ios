package java.text

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSMakeRange
import platform.Foundation.compare
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
class SimpleDateFormat(private val pattern: String, locale: Locale = Locale.getDefault()) {
    fun toPattern(): String = pattern
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

    var isLenient: Boolean
        get() = formatter.lenient
        set(v) { formatter.lenient = v }

    fun parse(text: String): Date {
        val d = formatter.dateFromString(text) ?: throw ParseException("Unparseable date: \"$text\"", 0)
        return Date((d.timeIntervalSince1970 * 1000).toLong())
    }

    /** Parses from pos.index; NSDateFormatter reads whole strings, so a match consumes the rest of the text. */
    fun parse(text: String, pos: ParsePosition): Date? {
        val d = formatter.dateFromString(text.substring(pos.index)) ?: run { pos.errorIndex = pos.index; return null }
        pos.index = text.length
        return Date((d.timeIntervalSince1970 * 1000).toLong())
    }
}

class ParsePosition(var index: Int) {
    var errorIndex: Int = -1
}

/** Locale-aware string order, on NSString.compare(options:range:locale:). */
class Collator private constructor(private val locale: NSLocale) : Comparator<String> {
    override fun compare(a: String, b: String): Int =
        (a as NSString).compare(b, NSCaseInsensitiveSearch or NSDiacriticInsensitiveSearch, NSMakeRange(0u, (a as NSString).length), locale).toInt()

    companion object {
        fun getInstance(): Collator = getInstance(Locale.getDefault())
        fun getInstance(locale: Locale): Collator = Collator(NSLocale.localeWithLocaleIdentifier(locale.toLanguageTag()))
    }
}

class DateFormatSymbols(locale: Locale = Locale.getDefault()) {
    private val f = NSDateFormatter().apply { this.locale = NSLocale.localeWithLocaleIdentifier(locale.toLanguageTag()) }
    @Suppress("UNCHECKED_CAST")
    val months: Array<String> get() = ((f.standaloneMonthSymbols as? List<String>) ?: emptyList()).toTypedArray()
    @Suppress("UNCHECKED_CAST")
    val shortMonths: Array<String> get() = ((f.shortStandaloneMonthSymbols as? List<String>) ?: emptyList()).toTypedArray()
    // Java puts an empty string first, so weekdays[Calendar.SUNDAY] (1) is Sunday.
    @Suppress("UNCHECKED_CAST")
    val weekdays: Array<String> get() = (listOf("") + ((f.standaloneWeekdaySymbols as? List<String>) ?: emptyList())).toTypedArray()
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

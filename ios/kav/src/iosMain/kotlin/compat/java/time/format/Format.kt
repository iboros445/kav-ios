package java.time.format

import java.time.ZonedDateTime
import java.util.Locale
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeZoneWithName
import platform.Foundation.systemTimeZone

class DateTimeFormatter private constructor(private val pattern: String, private val locale: Locale) {
    fun format(t: ZonedDateTime): String {
        val f = NSDateFormatter()
        f.dateFormat = pattern
        f.locale = NSLocale.localeWithLocaleIdentifier(locale.toLanguageTag())
        f.timeZone = NSTimeZone.timeZoneWithName(t.zone.id) ?: NSTimeZone.systemTimeZone
        return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(t.epochSecond.toDouble()))
    }

    companion object {
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, Locale.getDefault())
    }
}

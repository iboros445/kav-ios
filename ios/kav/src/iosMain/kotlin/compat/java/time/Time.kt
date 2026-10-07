// The slice of java.time that Results.kt uses: whole-day differences and a weekday name, in a zone.
package java.time

import java.util.Locale
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeZoneWithName
import platform.Foundation.systemTimeZone

class ZoneId private constructor(val id: String) {
    override fun toString(): String = id
    companion object {
        fun of(id: String): ZoneId = ZoneId(id)
        fun systemDefault(): ZoneId = ZoneId(NSTimeZone.systemTimeZone.name)
    }
}

class Instant private constructor(val epochSecond: Long) {
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(epochSecond, zone)
    fun toEpochMilli(): Long = epochSecond * 1000
    companion object {
        fun ofEpochSecond(s: Long): Instant = Instant(s)
        fun ofEpochMilli(ms: Long): Instant = Instant(ms / 1000)
        fun now(): Instant = Instant(kotlin.time.Clock.System.now().epochSeconds)
    }
}

class LocalDate internal constructor(val epochDay: Long) : Comparable<LocalDate> {
    override fun compareTo(other: LocalDate): Int = epochDay.compareTo(other.epochDay)
    override fun equals(other: Any?): Boolean = other is LocalDate && other.epochDay == epochDay
    override fun hashCode(): Int = epochDay.hashCode()
}

class ZonedDateTime internal constructor(internal val epochSecond: Long, internal val zone: ZoneId) {
    /** The calendar day in this zone, counted as days since a fixed origin. */
    fun toLocalDate(): LocalDate {
        val offset = NSTimeZone.timeZoneWithName(zone.id)?.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochSecond.toDouble()))?.toLong() ?: 0L
        return LocalDate(Math.floorDiv(epochSecond + offset, 86_400L))
    }
}

private object Math { fun floorDiv(a: Long, b: Long): Long = kotlin.math.floor(a.toDouble() / b).toLong() }

// java.util.Calendar over NSCalendar (Gregorian), in a time zone. Only the fields Kav reads or sets.
package java.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitNanosecond
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import platform.Foundation.localTimeZone


class Calendar private constructor(private var zone: TimeZone, private var ms: Long) : Cloneable {
    private fun cal(): NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).also {
        it.timeZone = NSTimeZone.timeZoneWithName(zone.id) ?: NSTimeZone.localTimeZone
        it.firstWeekday = 1u
    }

    private fun date() = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)
    private fun msOf(d: NSDate) = kotlin.math.round(d.timeIntervalSince1970 * 1000).toLong()

    var timeInMillis: Long
        get() = ms
        set(v) { ms = v }

    var timeZone: TimeZone
        get() = zone
        set(v) { zone = v }

    fun getTimeInMillis(): Long = ms
    fun setTimeInMillis(v: Long) { ms = v }

    fun get(field: Int): Int = when (field) {
        YEAR -> cal().component(NSCalendarUnitYear, date()).toInt()
        MONTH -> cal().component(NSCalendarUnitMonth, date()).toInt() - 1
        DAY_OF_MONTH -> cal().component(NSCalendarUnitDay, date()).toInt()
        DAY_OF_WEEK -> cal().component(NSCalendarUnitWeekday, date()).toInt()
        HOUR_OF_DAY -> cal().component(NSCalendarUnitHour, date()).toInt()
        MINUTE -> cal().component(NSCalendarUnitMinute, date()).toInt()
        SECOND -> cal().component(NSCalendarUnitSecond, date()).toInt()
        MILLISECOND -> (((ms % 1000) + 1000) % 1000).toInt()
        DAY_OF_YEAR -> cal().ordinalityOfUnit(NSCalendarUnitDay, platform.Foundation.NSCalendarUnitYear, date()).toInt()
        else -> throw IllegalArgumentException("field $field")
    }

    fun set(field: Int, value: Int) {
        val c = cal()
        when (field) {
            MILLISECOND -> { ms = ms - (((ms % 1000) + 1000) % 1000) + value; return }
            YEAR -> unit(c, NSCalendarUnitYear, value)
            MONTH -> unit(c, NSCalendarUnitMonth, value + 1)
            DAY_OF_MONTH -> unit(c, NSCalendarUnitDay, value)
            HOUR_OF_DAY -> unit(c, NSCalendarUnitHour, value)
            MINUTE -> unit(c, NSCalendarUnitMinute, value)
            SECOND -> unit(c, NSCalendarUnitSecond, value)
            else -> throw IllegalArgumentException("field $field")
        }
    }

    private fun unit(c: NSCalendar, u: platform.Foundation.NSCalendarUnit, value: Int) {
        val keepMs = ((ms % 1000) + 1000) % 1000
        val d = c.dateBySettingUnit(u, value.toLong(), date(), 0u) ?: return
        ms = msOf(d) - (((msOf(d) % 1000) + 1000) % 1000) + keepMs
    }

    fun add(field: Int, amount: Int) {
        val u = when (field) {
            YEAR -> NSCalendarUnitYear
            MONTH -> NSCalendarUnitMonth
            DAY_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK -> NSCalendarUnitDay
            HOUR_OF_DAY -> NSCalendarUnitHour
            MINUTE -> NSCalendarUnitMinute
            SECOND -> NSCalendarUnitSecond
            MILLISECOND -> { ms += amount; return }
            else -> throw IllegalArgumentException("field $field")
        }
        val d = cal().dateByAddingUnit(u, amount.toLong(), date(), 0u) ?: return
        ms = msOf(d)
    }

    val time: Date get() = Date(ms)
    fun getTime(): Date = Date(ms)
    fun setTime(d: Date) { ms = d.time }
    fun clone(): Any = Calendar(zone, ms)

    fun before(o: Calendar) = ms < o.ms
    fun after(o: Calendar) = ms > o.ms

    companion object {
        const val YEAR = 1
        const val MONTH = 2
        const val DAY_OF_YEAR = 6
        const val DAY_OF_MONTH = 5
        const val DAY_OF_WEEK = 7
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14
        const val SUNDAY = 1
        const val MONDAY = 2
        const val SATURDAY = 7

        fun getInstance(): Calendar = Calendar(TimeZone.getDefault(), nowMs())
        fun getInstance(tz: TimeZone): Calendar = Calendar(tz, nowMs())
        fun getInstance(tz: TimeZone, @Suppress("UNUSED_PARAMETER") locale: Locale): Calendar = Calendar(tz, nowMs())
        private fun nowMs() = kotlin.time.Clock.System.now().toEpochMilliseconds()
    }
}

interface Cloneable

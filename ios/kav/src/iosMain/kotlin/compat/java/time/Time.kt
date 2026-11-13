// The slice of java.time that upstream uses: dates, times of day and zoned date-times in a named zone, with the
// ISO formats Kav sends and parses. Calendar maths is proleptic Gregorian on epoch days; a zone's offset comes from
// NSTimeZone, so daylight saving follows iOS's tz database.
package java.time

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.systemTimeZone
import platform.Foundation.timeZoneWithName

class DateTimeException(message: String) : RuntimeException(message)

class ZoneId private constructor(val id: String) {
    private val tz: NSTimeZone = NSTimeZone.timeZoneWithName(id) ?: NSTimeZone.systemTimeZone

    /** Seconds east of UTC at an instant. */
    internal fun offsetAt(epochSecond: Long): Long =
        tz.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochSecond.toDouble())).toLong()

    /** The instant a local date-time (seconds since the epoch on the local clock) names; the earlier offset in an overlap. */
    internal fun epochOf(localSecond: Long): Long {
        val first = localSecond - offsetAt(localSecond)
        val second = localSecond - offsetAt(first)
        return if (offsetAt(second) == localSecond - second) second else first
    }

    override fun toString(): String = id
    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun of(id: String): ZoneId = ZoneId(id)
        fun systemDefault(): ZoneId = ZoneId(NSTimeZone.systemTimeZone.name)
    }
}

class Instant private constructor(val epochSecond: Long, val nano: Int = 0) : Comparable<Instant> {
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(epochSecond, zone, nano)
    fun toEpochMilli(): Long = epochSecond * 1000 + nano / 1_000_000
    fun plusSeconds(s: Long): Instant = Instant(epochSecond + s, nano)
    fun minusSeconds(s: Long): Instant = Instant(epochSecond - s, nano)
    fun isBefore(other: Instant): Boolean = this < other
    fun isAfter(other: Instant): Boolean = this > other
    override fun compareTo(other: Instant): Int = compareValuesBy(this, other, { it.epochSecond }, { it.nano })
    override fun equals(other: Any?): Boolean = other is Instant && other.epochSecond == epochSecond && other.nano == nano
    override fun hashCode(): Int = epochSecond.hashCode() * 31 + nano
    override fun toString(): String = atZone(ZoneId.of("UTC")).toLocalDateTime().toString() + "Z"

    companion object {
        fun ofEpochSecond(s: Long): Instant = Instant(s)
        internal fun of(s: Long, nano: Int): Instant = Instant(s, nano)
        fun ofEpochMilli(ms: Long): Instant = Instant(TimeMath.floorDiv(ms, 1000L), (TimeMath.floorMod(ms, 1000L) * 1_000_000).toInt())
        fun now(): Instant = ofEpochMilli(kotlin.time.Clock.System.now().toEpochMilliseconds())
    }
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;
    val value: Int get() = ordinal + 1
    fun plus(days: Long): DayOfWeek = entries[TimeMath.floorMod(ordinal + days, 7L).toInt()]
    companion object { fun of(v: Int): DayOfWeek = entries[v - 1] }
}

class LocalDate internal constructor(val epochDay: Long) : Comparable<LocalDate> {
    private val ymd: IntArray by lazy { Civil.fromDays(epochDay) }
    val year: Int get() = ymd[0]
    val monthValue: Int get() = ymd[1]
    val dayOfMonth: Int get() = ymd[2]
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[TimeMath.floorMod(epochDay + 3, 7L).toInt()]   // 1970-01-01 was a Thursday
    val dayOfYear: Int get() = (epochDay - of(year, 1, 1).epochDay).toInt() + 1
    fun lengthOfMonth(): Int = Civil.monthLength(year, monthValue)

    fun toEpochDay(): Long = epochDay
    fun plusDays(n: Long): LocalDate = LocalDate(epochDay + n)
    fun minusDays(n: Long): LocalDate = LocalDate(epochDay - n)
    fun plusWeeks(n: Long): LocalDate = LocalDate(epochDay + 7 * n)
    fun minusWeeks(n: Long): LocalDate = LocalDate(epochDay - 7 * n)
    fun plusMonths(n: Long): LocalDate {
        val m0 = year * 12L + (monthValue - 1) + n
        val y = TimeMath.floorDiv(m0, 12L).toInt(); val m = TimeMath.floorMod(m0, 12L).toInt() + 1
        return of(y, m, minOf(dayOfMonth, Civil.monthLength(y, m)))
    }
    fun withDayOfMonth(d: Int): LocalDate = of(year, monthValue, d)
    fun isBefore(other: LocalDate): Boolean = epochDay < other.epochDay
    fun isAfter(other: LocalDate): Boolean = epochDay > other.epochDay
    fun isEqual(other: LocalDate): Boolean = epochDay == other.epochDay
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime(zone.epochOf(epochDay * 86_400), zone)
    fun atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime.MIDNIGHT)
    fun atTime(t: LocalTime): LocalDateTime = LocalDateTime(this, t)
    fun atTime(h: Int, m: Int): LocalDateTime = LocalDateTime(this, LocalTime.of(h, m))
    // Fields only, no zone: format it as the date's own noon in UTC so no offset can move the day.
    fun format(f: java.time.format.DateTimeFormatter): String = f.format(ZonedDateTime(epochDay * 86_400 + 43_200, ZoneId.of("UTC")))

    override fun compareTo(other: LocalDate): Int = epochDay.compareTo(other.epochDay)
    override fun equals(other: Any?): Boolean = other is LocalDate && other.epochDay == epochDay
    override fun hashCode(): Int = epochDay.hashCode()
    override fun toString(): String = "${pad(year, 4)}-${pad(monthValue, 2)}-${pad(dayOfMonth, 2)}"

    companion object {
        val EPOCH = LocalDate(0)
        fun of(year: Int, month: Int, day: Int): LocalDate {
            if (month !in 1..12 || day !in 1..Civil.monthLength(year, month)) throw DateTimeException("Invalid date $year-$month-$day")
            return LocalDate(Civil.toDays(year, month, day))
        }
        fun ofEpochDay(d: Long): LocalDate = LocalDate(d)
        fun now(zone: ZoneId): LocalDate = Instant.now().atZone(zone).toLocalDate()
        fun now(): LocalDate = now(ZoneId.systemDefault())
        /** ISO yyyy-MM-dd. */
        fun parse(s: CharSequence): LocalDate {
            val m = Regex("""(-?\d{4,})-(\d{2})-(\d{2})""").matchEntire(s) ?: throw DateTimeException("Text '$s' could not be parsed")
            return of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
    }
}

class LocalTime private constructor(val secondOfDay: Int, val nano: Int = 0) : Comparable<LocalTime> {
    val hour: Int get() = secondOfDay / 3600
    val minute: Int get() = secondOfDay / 60 % 60
    val second: Int get() = secondOfDay % 60
    fun toSecondOfDay(): Int = secondOfDay
    override fun compareTo(other: LocalTime): Int = compareValuesBy(this, other, { it.secondOfDay }, { it.nano })
    override fun equals(other: Any?): Boolean = other is LocalTime && other.secondOfDay == secondOfDay && other.nano == nano
    override fun hashCode(): Int = secondOfDay * 31 + nano
    // Like Java: HH:mm, then :ss only when there are seconds or nanos, then the fraction.
    override fun toString(): String = buildString {
        append(pad(hour, 2)).append(':').append(pad(minute, 2))
        if (second > 0 || nano > 0) {
            append(':').append(pad(second, 2))
            if (nano > 0) append('.').append(when {
                nano % 1_000_000 == 0 -> pad(nano / 1_000_000, 3)
                nano % 1_000 == 0 -> pad(nano / 1_000, 6)
                else -> pad(nano, 9)
            })
        }
    }

    companion object {
        val MIDNIGHT = LocalTime(0)
        val NOON = LocalTime(43_200)
        fun of(h: Int, m: Int, s: Int = 0): LocalTime {
            if (h !in 0..23 || m !in 0..59 || s !in 0..59) throw DateTimeException("Invalid time $h:$m:$s")
            return LocalTime(h * 3600 + m * 60 + s)
        }
        fun ofSecondOfDay(s: Long): LocalTime = LocalTime(s.toInt())
        internal fun ofSecond(secondOfDay: Int, nano: Int): LocalTime = LocalTime(secondOfDay, nano)
        fun parse(s: CharSequence): LocalTime {
            val m = Regex("""(\d{2}):(\d{2})(?::(\d{2}))?""").matchEntire(s) ?: throw DateTimeException("Text '$s' could not be parsed")
            return of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt())
        }
    }
}

class LocalDateTime internal constructor(val date: LocalDate, val time: LocalTime) : Comparable<LocalDateTime> {
    internal val localSecond: Long get() = date.epochDay * 86_400 + time.secondOfDay
    val year: Int get() = date.year
    val monthValue: Int get() = date.monthValue
    val dayOfMonth: Int get() = date.dayOfMonth
    val hour: Int get() = time.hour
    val minute: Int get() = time.minute
    val second: Int get() = time.second
    fun toLocalDate(): LocalDate = date
    fun toLocalTime(): LocalTime = time
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(zone.epochOf(localSecond), zone, time.nano)
    override fun compareTo(other: LocalDateTime): Int = compareValuesBy(this, other, { it.date }, { it.time })
    override fun equals(other: Any?): Boolean = other is LocalDateTime && other.date == date && other.time == time
    override fun hashCode(): Int = date.hashCode() * 31 + time.hashCode()
    override fun toString(): String = "${date}T$time"

    companion object {
        internal fun ofLocalSecond(s: Long, nano: Int = 0): LocalDateTime =
            LocalDateTime(LocalDate(TimeMath.floorDiv(s, 86_400L)), LocalTime.ofSecond(TimeMath.floorMod(s, 86_400L).toInt(), nano))
        fun of(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): LocalDateTime = LocalDateTime(LocalDate.of(y, mo, d), LocalTime.of(h, mi, s))
    }
}

class ZonedDateTime internal constructor(internal val epochSecond: Long, val zone: ZoneId, val nano: Int = 0) : Comparable<ZonedDateTime> {
    private val local: LocalDateTime by lazy { LocalDateTime.ofLocalSecond(epochSecond + zone.offsetAt(epochSecond), nano) }
    val year: Int get() = local.year
    val monthValue: Int get() = local.monthValue
    val dayOfMonth: Int get() = local.dayOfMonth
    val dayOfWeek: DayOfWeek get() = local.date.dayOfWeek
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
    val second: Int get() = local.second

    fun toLocalDate(): LocalDate = local.date
    fun toLocalTime(): LocalTime = local.time
    fun toLocalDateTime(): LocalDateTime = local
    fun toInstant(): Instant = Instant.of(epochSecond, nano)
    fun toEpochSecond(): Long = epochSecond

    private fun withLocal(l: LocalDateTime): ZonedDateTime = l.atZone(zone)
    fun plusDays(n: Long): ZonedDateTime = withLocal(LocalDateTime(local.date.plusDays(n), local.time))
    fun minusDays(n: Long): ZonedDateTime = plusDays(-n)
    fun plusHours(n: Long): ZonedDateTime = ZonedDateTime(epochSecond + n * 3600, zone, nano)
    fun minusHours(n: Long): ZonedDateTime = plusHours(-n)
    fun minusMinutes(n: Long): ZonedDateTime = plusMinutes(-n)
    fun plusMinutes(n: Long): ZonedDateTime = ZonedDateTime(epochSecond + n * 60, zone, nano)
    fun plusSeconds(n: Long): ZonedDateTime = ZonedDateTime(epochSecond + n, zone, nano)
    fun withHour(h: Int): ZonedDateTime = withLocal(LocalDateTime(local.date, LocalTime.of(h, local.minute, local.second)))
    fun withMinute(m: Int): ZonedDateTime = withLocal(LocalDateTime(local.date, LocalTime.of(local.hour, m, local.second)))
    fun withSecond(s: Int): ZonedDateTime = withLocal(LocalDateTime(local.date, LocalTime.of(local.hour, local.minute, s)))
    fun withNano(n: Int): ZonedDateTime = ZonedDateTime(epochSecond, zone, n)
    fun withZoneSameInstant(z: ZoneId): ZonedDateTime = ZonedDateTime(epochSecond, z, nano)
    fun isBefore(other: ZonedDateTime): Boolean = this < other
    fun isAfter(other: ZonedDateTime): Boolean = this > other
    fun format(f: java.time.format.DateTimeFormatter): String = f.format(this)

    override fun compareTo(other: ZonedDateTime): Int = compareValuesBy(this, other, { it.epochSecond }, { it.nano })
    override fun equals(other: Any?): Boolean = other is ZonedDateTime && other.epochSecond == epochSecond && other.nano == nano && other.zone == zone
    override fun hashCode(): Int = epochSecond.hashCode()
    override fun toString(): String = "$local${OffsetDateTime.offsetText(zone.offsetAt(epochSecond))}[$zone]"

    companion object {
        fun now(zone: ZoneId): ZonedDateTime = Instant.now().atZone(zone)
        fun now(): ZonedDateTime = now(ZoneId.systemDefault())
    }
}

/** Only what upstream needs: parse an ISO-8601 date-time with an offset, and read its instant. */
class OffsetDateTime private constructor(private val epochSecond: Long, private val nano: Int) {
    fun toEpochSecond(): Long = epochSecond
    fun toInstant(): Instant = Instant.of(epochSecond, nano)

    companion object {
        private val ISO = Regex("""(-?\d{4,})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?(Z|[+-]\d{2}(?::?\d{2})?)""", RegexOption.IGNORE_CASE)
        fun parse(s: CharSequence): OffsetDateTime {
            val m = ISO.matchEntire(s.trim()) ?: throw DateTimeException("Text '$s' could not be parsed")
            val g = m.groupValues
            val local = LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            val nano = g[7].ifEmpty { "0" }.padEnd(9, '0').toInt()
            val z = g[8]
            val off = if (z.equals("Z", ignoreCase = true)) 0L else {
                val digits = z.substring(1).replace(":", "")
                val secs = digits.substring(0, 2).toLong() * 3600 + digits.substring(2).ifEmpty { "0" }.toLong() * 60
                if (z[0] == '-') -secs else secs
            }
            return OffsetDateTime(local.localSecond - off, nano)
        }
        internal fun offsetText(seconds: Long): String {
            if (seconds == 0L) return "Z"
            val a = kotlin.math.abs(seconds)
            return (if (seconds < 0) "-" else "+") + pad((a / 3600).toInt(), 2) + ":" + pad((a / 60 % 60).toInt(), 2)
        }
    }
}

// Howard Hinnant's days_from_civil / civil_from_days.
internal object Civil {
    fun toDays(y0: Int, m: Int, d: Int): Long {
        val y = (if (m <= 2) y0 - 1 else y0).toLong()
        val era = TimeMath.floorDiv(y, 400L)
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
    fun fromDays(z0: Long): IntArray {
        val z = z0 + 719_468
        val era = TimeMath.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
        return intArrayOf(y, m, d)
    }
    fun leap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    fun monthLength(y: Int, m: Int): Int = when (m) { 2 -> if (leap(y)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
}

private fun pad(v: Int, n: Int): String = if (v < 0) "-" + (-v).toString().padStart(n, '0') else v.toString().padStart(n, '0')

internal object TimeMath {
    fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if (a % b != 0L && (a xor b) < 0) q - 1 else q }
    fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
}

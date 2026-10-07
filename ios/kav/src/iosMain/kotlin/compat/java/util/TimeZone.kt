package java.util

import java.time.ZoneId
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.NSDate
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

class TimeZone(val id: String) {
    fun toZoneId(): ZoneId = ZoneId.of(id)
    fun getID(): String = id
    fun getOffset(utcMs: Long): Int =
        NSTimeZone.timeZoneWithName(id)?.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(utcMs / 1000.0))?.toInt()?.times(1000) ?: 0

    companion object {
        fun getTimeZone(id: String): TimeZone = TimeZone(NSTimeZone.timeZoneWithName(id)?.name ?: "GMT")
        fun getDefault(): TimeZone = TimeZone(NSTimeZone.localTimeZone.name)
    }
}

package java.util

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

class TimeZone(val id: String) {
    companion object {
        fun getTimeZone(id: String): TimeZone = TimeZone(NSTimeZone.timeZoneWithName(id)?.name ?: "GMT")
        fun getDefault(): TimeZone = TimeZone(NSTimeZone.localTimeZone.name)
    }
}

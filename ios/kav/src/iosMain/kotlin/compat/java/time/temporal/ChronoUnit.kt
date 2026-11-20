package java.time.temporal

import java.time.LocalDate

enum class ChronoUnit {
    DAYS;

    fun between(from: LocalDate, to: LocalDate): Long = to.epochDay - from.epochDay
}

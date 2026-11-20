package java.util

import kotlin.random.Random

/** UUIDs here identify an install to Moovit's servers; they are random, not security-sensitive. */
class UUID private constructor(private val text: String) : Comparable<UUID> {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is UUID && other.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun compareTo(other: UUID): Int = text.compareTo(other.text)

    companion object {
        fun randomUUID(): UUID {
            val b = Random.Default.nextBytes(16)
            b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()   // version 4
            b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()   // variant 1
            val hex = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            return UUID("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        }

        fun fromString(s: String): UUID = UUID(s.lowercase())
    }
}

class Date(private val ms: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()) : Comparable<Date> {
    val time: Long get() = ms
    fun getTime(): Long = ms
    fun before(o: Date) = ms < o.ms
    fun after(o: Date) = ms > o.ms
    override fun compareTo(other: Date): Int = ms.compareTo(other.ms)
    override fun equals(other: Any?): Boolean = other is Date && other.ms == ms
    override fun hashCode(): Int = ms.hashCode()
    override fun toString(): String = ms.toString()
}

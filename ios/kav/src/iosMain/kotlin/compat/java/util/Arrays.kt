// java.util.Arrays: only what upstream calls (Net sorts each stop's departures in place).
package java.util

object Arrays {
    fun sort(a: LongArray) = a.sort()
    fun sort(a: LongArray, fromIndex: Int, toIndex: Int) = a.sort(fromIndex, toIndex)
    fun sort(a: IntArray) = a.sort()
    fun sort(a: IntArray, fromIndex: Int, toIndex: Int) = a.sort(fromIndex, toIndex)
    fun fill(a: IntArray, v: Int) = a.fill(v)
    fun fill(a: LongArray, v: Long) = a.fill(v)
}

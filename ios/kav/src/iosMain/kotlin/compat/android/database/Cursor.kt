package android.database

import java.io.Closeable

interface Cursor : Closeable {
    fun moveToFirst(): Boolean
    fun getColumnIndex(name: String): Int
    fun getString(index: Int): String?
    fun getLong(index: Int): Long
    override fun close()
}

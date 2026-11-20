// SharedPreferences over NSUserDefaults, one suite per prefs file name.
package android.content

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

interface SharedPreferences {
    val all: Map<String, Any?>
    fun contains(key: String): Boolean
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

internal class DefaultsPreferences(private val name: String) : SharedPreferences {
    private val d = NSUserDefaults(suiteName = "kav.$name")

    // Keys are prefixed with their type tag only where NSUserDefaults would blur it (Int vs Long vs Float vs Boolean all
    // come back as NSNumber), so reads convert from NSNumber and a type clash behaves like the JVM's ClassCastException-free reads.
    override val all: Map<String, Any?> get() =
        d.dictionaryRepresentation().entries.filter { (k, _) -> (k as String).startsWith(PREFIX) }
            .associate { (k, v) -> (k as String).removePrefix(PREFIX) to v }

    private fun k(key: String) = PREFIX + key
    override fun contains(key: String): Boolean = d.objectForKey(k(key)) != null
    override fun getString(key: String, defValue: String?): String? = d.stringForKey(k(key)) ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (d.objectForKey(k(key)) as? NSNumber)?.intValue ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (d.objectForKey(k(key)) as? NSNumber)?.longLongValue ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (d.objectForKey(k(key)) as? NSNumber)?.floatValue ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (d.objectForKey(k(key)) as? NSNumber)?.boolValue ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (d.arrayForKey(k(key)))?.map { it as String }?.toSet() ?: defValues

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val ops = ArrayList<() -> Unit>()
        private fun op(f: () -> Unit): SharedPreferences.Editor { ops.add(f); return this }
        override fun putString(key: String, value: String?) = op { if (value == null) d.removeObjectForKey(k(key)) else d.setObject(value, k(key)) }
        override fun putInt(key: String, value: Int) = op { d.setInteger(value.toLong(), k(key)) }
        override fun putLong(key: String, value: Long) = op { d.setInteger(value, k(key)) }
        override fun putFloat(key: String, value: Float) = op { d.setFloat(value, k(key)) }
        override fun putBoolean(key: String, value: Boolean) = op { d.setBool(value, k(key)) }
        override fun putStringSet(key: String, values: Set<String>?) = op { if (values == null) d.removeObjectForKey(k(key)) else d.setObject(values.toList(), k(key)) }
        override fun remove(key: String) = op { d.removeObjectForKey(k(key)) }
        override fun clear() = op { all.keys.forEach { d.removeObjectForKey(k(it)) } }
        override fun apply() { ops.forEach { it() } }
        override fun commit(): Boolean { apply(); return d.synchronize() }
    }

    private companion object { const val PREFIX = "p." }
}

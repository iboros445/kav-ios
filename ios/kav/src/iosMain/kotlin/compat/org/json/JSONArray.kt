package org.json

class JSONArray() {
    internal val list = ArrayList<Any?>()

    constructor(json: String) : this() {
        val v = JSONTokener(json).nextValue()
        if (v !is JSONArray) throw JSON.typeMismatch(v, v, "JSONArray")
        list.addAll(v.list)
    }

    constructor(source: Collection<*>) : this() { for (e in source) list.add(JSON.wrap(e)) }

    fun length(): Int = list.size

    fun put(value: Boolean): JSONArray { list.add(value); return this }
    fun put(value: Int): JSONArray { list.add(value); return this }
    fun put(value: Long): JSONArray { list.add(value); return this }
    fun put(value: Double): JSONArray {
        if (value.isNaN() || value.isInfinite()) throw JSONException("Numbers must be finite")
        list.add(value); return this
    }
    fun put(value: Any?): JSONArray { list.add(JSON.wrap(value)); return this }
    fun put(index: Int, value: Any?): JSONArray {
        while (list.size <= index) list.add(null)
        list[index] = JSON.wrap(value); return this
    }

    fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null
    fun isNull(index: Int): Boolean = opt(index).let { it == null || it === JSONObject.NULL }

    fun opt(index: Int): Any? = list.getOrNull(index)
    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("Index $index out of range [0..${list.size})")

    fun getBoolean(i: Int): Boolean = JSON.toBoolean(get(i)) ?: throw JSON.typeMismatch(i, list[i], "boolean")
    fun getInt(i: Int): Int = JSON.toInteger(get(i)) ?: throw JSON.typeMismatch(i, list[i], "int")
    fun getLong(i: Int): Long = JSON.toLong(get(i)) ?: throw JSON.typeMismatch(i, list[i], "long")
    fun getDouble(i: Int): Double = JSON.toDouble(get(i)) ?: throw JSON.typeMismatch(i, list[i], "double")
    fun getString(i: Int): String = JSON.toString(get(i)) ?: throw JSON.typeMismatch(i, list[i], "String")
    fun getJSONObject(i: Int): JSONObject = get(i) as? JSONObject ?: throw JSON.typeMismatch(i, list[i], "JSONObject")
    fun getJSONArray(i: Int): JSONArray = get(i) as? JSONArray ?: throw JSON.typeMismatch(i, list[i], "JSONArray")

    fun optBoolean(i: Int): Boolean = optBoolean(i, false)
    fun optBoolean(i: Int, fallback: Boolean): Boolean = JSON.toBoolean(opt(i)) ?: fallback
    fun optInt(i: Int): Int = optInt(i, 0)
    fun optInt(i: Int, fallback: Int): Int = JSON.toInteger(opt(i)) ?: fallback
    fun optLong(i: Int): Long = optLong(i, 0L)
    fun optLong(i: Int, fallback: Long): Long = JSON.toLong(opt(i)) ?: fallback
    fun optDouble(i: Int): Double = optDouble(i, Double.NaN)
    fun optDouble(i: Int, fallback: Double): Double = JSON.toDouble(opt(i)) ?: fallback
    fun optString(i: Int): String = optString(i, "")
    fun optString(i: Int, fallback: String): String = opt(i).let { if (it == null || it === JSONObject.NULL) fallback else JSON.toString(it) ?: fallback }
    fun optJSONObject(i: Int): JSONObject? = opt(i) as? JSONObject
    fun optJSONArray(i: Int): JSONArray? = opt(i) as? JSONArray

    fun join(separator: String): String = list.joinToString(separator) { StringBuilder().also { sb -> JSON.write(it, sb, 0, 0) }.toString() }

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()
    fun toString(indentSpaces: Int): String = StringBuilder().also { write(it, indentSpaces, 0) }.toString()

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        if (list.isEmpty()) { out.append("[]"); return }
        out.append('[')
        for ((i, v) in list.withIndex()) {
            if (i > 0) out.append(',')
            JSON.newline(out, indent, depth + 1)
            JSON.write(v, out, indent, depth + 1)
        }
        JSON.newline(out, indent, depth)
        out.append(']')
    }

    override fun equals(other: Any?): Boolean = other is JSONArray && other.list == list
    override fun hashCode(): Int = list.hashCode()
}

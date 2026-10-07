package org.json

class JSONObject() {
    internal val map = LinkedHashMap<String, Any?>()

    constructor(json: String?) : this() {
        val v = JSONTokener(json ?: throw NullPointerException("json == null")).nextValue()
        if (v !is JSONObject) throw JSON.typeMismatch(v, v, "JSONObject")
        map.putAll(v.map)
    }

    constructor(source: Map<*, *>) : this() {
        for ((k, v) in source) {
            if (k == null) throw NullPointerException("key == null")
            put(k.toString(), v)
        }
    }

    fun length(): Int = map.size

    fun put(name: String, value: Boolean): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Int): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Long): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Double): JSONObject {
        if (value.isNaN() || value.isInfinite()) throw JSONException("Numbers must be finite")
        map[name] = value; return this
    }
    /** A null value removes the name, as on Android. */
    fun put(name: String, value: Any?): JSONObject {
        if (value == null) map.remove(name) else map[name] = JSON.wrap(value)
        return this
    }
    fun putOpt(name: String?, value: Any?): JSONObject { if (name != null && value != null) put(name, value); return this }

    fun remove(name: String): Any? = map.remove(name)
    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name].let { it == null || it === NULL }

    fun opt(name: String): Any? = map[name]
    fun get(name: String): Any = map[name] ?: throw JSONException("No value for $name")

    fun getBoolean(name: String): Boolean = JSON.toBoolean(get(name)) ?: throw JSON.typeMismatch(name, map[name], "boolean")
    fun getInt(name: String): Int = JSON.toInteger(get(name)) ?: throw JSON.typeMismatch(name, map[name], "int")
    fun getLong(name: String): Long = JSON.toLong(get(name)) ?: throw JSON.typeMismatch(name, map[name], "long")
    fun getDouble(name: String): Double = JSON.toDouble(get(name)) ?: throw JSON.typeMismatch(name, map[name], "double")
    fun getString(name: String): String = JSON.toString(get(name)) ?: throw JSON.typeMismatch(name, map[name], "String")
    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw JSON.typeMismatch(name, map[name], "JSONObject")
    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw JSON.typeMismatch(name, map[name], "JSONArray")

    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = JSON.toBoolean(map[name]) ?: fallback
    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = JSON.toInteger(map[name]) ?: fallback
    fun optLong(name: String): Long = optLong(name, 0L)
    fun optLong(name: String, fallback: Long): Long = JSON.toLong(map[name]) ?: fallback
    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = JSON.toDouble(map[name]) ?: fallback
    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String): String = map[name].let { if (it == null || it === NULL) fallback else JSON.toString(it) ?: fallback }
    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject
    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray

    /** A snapshot, so the caller may modify the object while iterating. */
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())
    fun keySet(): Set<String> = map.keys.toSet()

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()
    fun toString(indentSpaces: Int): String = StringBuilder().also { write(it, indentSpaces, 0) }.toString()

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        if (map.isEmpty()) { out.append("{}"); return }
        out.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) out.append(',')
            first = false
            JSON.newline(out, indent, depth + 1)
            JSON.quote(k, out)
            out.append(if (indent > 0) ": " else ":")
            JSON.write(v, out, indent, depth + 1)
        }
        JSON.newline(out, indent, depth)
        out.append('}')
    }

    override fun equals(other: Any?): Boolean = other is JSONObject && other.map == map
    override fun hashCode(): Int = map.hashCode()

    companion object {
        /** Stands for an explicit JSON null, as `JSONObject.NULL` does on Android. */
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other == null || other === this
            override fun hashCode(): Int = 0
            override fun toString(): String = "null"
        }
    }
}

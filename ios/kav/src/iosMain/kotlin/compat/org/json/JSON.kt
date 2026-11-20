package org.json

// Coercions shared by JSONObject and JSONArray (same rules as Android's org.json.JSON).
internal object JSON {
    fun toBoolean(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> when (v.lowercase()) { "true" -> true; "false" -> false; else -> null }
        else -> null
    }

    fun toDouble(v: Any?): Double? = when (v) {
        is Double -> v
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    fun toInteger(v: Any?): Int? = when (v) {
        is Int -> v
        is Number -> v.toInt()
        is String -> v.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

    fun toLong(v: Any?): Long? = when (v) {
        is Long -> v
        is Number -> v.toLong()
        is String -> v.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    fun toString(v: Any?): String? = when (v) {
        null -> null
        is String -> v
        else -> v.toString()
    }

    fun typeMismatch(indexOrName: Any?, actual: Any?, requiredType: String): JSONException =
        if (actual == null) JSONException("Value at $indexOrName is null.")
        else JSONException("Value $actual at $indexOrName of type ${actual::class.simpleName} cannot be converted to $requiredType")

    fun numberToString(n: Number): String {
        if (n is Double || n is Float) {
            val d = n.toDouble()
            if (d.isNaN() || d.isInfinite()) throw JSONException("Numbers must be finite")
            val l = d.toLong()
            if (l.toDouble() == d && kotlin.math.abs(d) < 1e15) return l.toString()
        }
        return n.toString()
    }

    fun quote(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '/' -> out.append("\\/")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            ' ', ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> if (c.code <= 0x1F) out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
        }
        out.append('"')
    }

    fun write(v: Any?, out: StringBuilder, indent: Int, depth: Int) {
        when (v) {
            null -> out.append("null")
            is JSONObject -> v.write(out, indent, depth)
            is JSONArray -> v.write(out, indent, depth)
            is String -> quote(v, out)
            is Boolean -> out.append(v)
            is Number -> out.append(numberToString(v))
            is Map<*, *> -> JSONObject(v).write(out, indent, depth)
            is Collection<*> -> JSONArray(v).write(out, indent, depth)
            else -> if (v === JSONObject.NULL) out.append("null") else quote(v.toString(), out)
        }
    }

    fun newline(out: StringBuilder, indent: Int, depth: Int) {
        if (indent <= 0) return
        out.append('\n')
        repeat(depth * indent) { out.append(' ') }
    }

    /** Value to store: collections and maps become JSON containers, null stays null. */
    fun wrap(v: Any?): Any? = when (v) {
        null -> null
        is Map<*, *> -> JSONObject(v)
        is Collection<*> -> JSONArray(v)
        is IntArray -> JSONArray(v.toList())
        is LongArray -> JSONArray(v.toList())
        is DoubleArray -> JSONArray(v.toList())
        is Array<*> -> JSONArray(v.toList())
        else -> v
    }
}

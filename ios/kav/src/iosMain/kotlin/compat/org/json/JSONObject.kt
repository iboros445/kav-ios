// Spike shim (Phase 0): proves Kotlin/Native accepts declarations in `package org.json`.
// Phase 1 replaces this with the full pure-Kotlin org.json (parser, JSONArray, JSONTokener).
package org.json

class JSONObject {
    private val values = LinkedHashMap<String, Any?>()

    fun put(name: String, value: Any?): JSONObject { values[name] = value; return this }
    fun optString(name: String, fallback: String = ""): String = values[name]?.toString() ?: fallback
    fun has(name: String): Boolean = name in values
    override fun toString(): String = values.entries.joinToString(",", "{", "}") { (k, v) ->
        "\"$k\":" + (if (v is String) "\"$v\"" else v.toString())
    }
}

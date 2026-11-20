// MapLibre style expressions, as the JSON arrays the style spec defines. Only the operators TileMap.kt uses.
package org.maplibre.android.style.expressions

import org.json.JSONArray

class Expression internal constructor(internal val json: Any) {
    companion object {
        private fun op(name: String, vararg args: Any): Expression =
            Expression(JSONArray().also { a -> a.put(name); args.forEach { a.put(if (it is Expression) it.json else it) } })

        fun get(key: String) = op("get", key)
        fun toColor(e: Expression) = op("to-color", e)
        fun toNumber(e: Expression) = op("to-number", e)
        fun eq(a: Expression, b: Expression) = op("==", a, b)
        fun gt(a: Expression, b: Expression) = op(">", a, b)
        fun all(vararg e: Expression) = op("all", *e)
        fun product(vararg e: Expression) = op("*", *e)
        fun literal(v: Boolean) = Expression(v)
        fun literal(v: Number) = Expression(v.toDouble())
        fun literal(v: String) = Expression(v)
        fun has(key: String) = op("has", key)
        fun not(e: Expression) = op("!", e)
        fun coalesce(vararg e: Expression) = op("coalesce", *e)
        fun zoom() = op("zoom")
        fun linear() = op("linear")
        fun stop(input: Any, output: Any) = Stop(input, output)
        // ["interpolate", interpolation, input, in1, out1, in2, out2, ...]
        fun interpolate(interpolation: Expression, input: Expression, vararg stops: Stop): Expression =
            Expression(JSONArray().also { a ->
                a.put("interpolate"); a.put(interpolation.json); a.put(input.json)
                for (s in stops) { a.put(Stop.value(s.input)); a.put(Stop.value(s.output)) }
            })
    }

    class Stop internal constructor(internal val input: Any, internal val output: Any) {
        internal companion object {
            fun value(v: Any): Any = when (v) { is Expression -> v.json; is Number -> v.toDouble(); else -> v }
        }
    }
}

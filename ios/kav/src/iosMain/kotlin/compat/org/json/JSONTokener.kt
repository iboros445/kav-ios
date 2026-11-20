package org.json

class JSONTokener(input: String?) {
    // Android tolerates a leading BOM; so do we.
    // Java takes a null and fails reading it, as here.
    private val s = (input ?: throw NullPointerException("input")).let { if (it.startsWith("﻿")) it.substring(1) else it }
    private var pos = 0

    fun nextValue(): Any? {
        skipWs()
        if (pos >= s.length) throw error("End of input")
        return when (val c = s[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"', '\'' -> { pos++; readString(c) }
            else -> readLiteral()
        }
    }

    fun more(): Boolean { skipWs(); return pos < s.length }

    private fun error(msg: String) = JSONException("$msg at character $pos of $s")

    private fun skipWs() {
        while (pos < s.length) {
            val c = s[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++ else break
        }
    }

    private fun readObject(): JSONObject {
        pos++ // {
        val o = JSONObject()
        skipWs()
        if (pos < s.length && s[pos] == '}') { pos++; return o }
        while (true) {
            skipWs()
            if (pos >= s.length) throw error("Unterminated object")
            val q = s[pos]
            val name = if (q == '"' || q == '\'') { pos++; readString(q) } else throw error("Names must be strings")
            skipWs()
            if (pos >= s.length || s[pos] != ':') throw error("Expected ':' after $name")
            pos++
            val v = nextValue()
            o.map[name] = v
            skipWs()
            if (pos >= s.length) throw error("Unterminated object")
            when (s[pos++]) {
                ',' -> continue
                '}' -> return o
                else -> throw error("Unterminated object")
            }
        }
    }

    private fun readArray(): JSONArray {
        pos++ // [
        val a = JSONArray()
        skipWs()
        if (pos < s.length && s[pos] == ']') { pos++; return a }
        while (true) {
            a.list.add(nextValue())
            skipWs()
            if (pos >= s.length) throw error("Unterminated array")
            when (s[pos++]) {
                ',' -> continue
                ']' -> return a
                else -> throw error("Unterminated array")
            }
        }
    }

    private fun readString(quote: Char): String {
        val sb = StringBuilder()
        while (true) {
            if (pos >= s.length) throw error("Unterminated string")
            val c = s[pos++]
            if (c == quote) return sb.toString()
            if (c != '\\') { sb.append(c); continue }
            if (pos >= s.length) throw error("Unterminated escape sequence")
            when (val e = s[pos++]) {
                'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                'r' -> sb.append('\r'); 't' -> sb.append('\t')
                'u' -> {
                    if (pos + 4 > s.length) throw error("Unterminated escape sequence")
                    sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: throw error("Invalid escape"))
                    pos += 4
                }
                else -> sb.append(e) // \" \' \\ \/ and anything else map to themselves
            }
        }
    }

    private fun readLiteral(): Any? {
        val start = pos
        while (pos < s.length && s[pos] !in ",:]}/\\\"[{;=# \t\r\n") pos++
        if (pos == start) throw error("Expected literal value")
        val lit = s.substring(start, pos)
        when {
            lit.equals("null", ignoreCase = true) -> return JSONObject.NULL
            lit.equals("true", ignoreCase = true) -> return true
            lit.equals("false", ignoreCase = true) -> return false
        }
        if ('.' !in lit && 'e' !in lit && 'E' !in lit) {
            lit.toLongOrNull()?.let { return if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        }
        lit.toDoubleOrNull()?.let { if (!it.isNaN() && !it.isInfinite()) return it }
        return lit // unquoted string, as Android allows
    }
}

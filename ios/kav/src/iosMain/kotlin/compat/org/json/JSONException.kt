// org.json for Kotlin/Native: a pure-Kotlin port of the Android flavour (what upstream was written
// against: put(name, null) removes, opt* coerce, '/' is escaped, whole doubles print without ".0").
package org.json

class JSONException(message: String? = null) : Exception(message)

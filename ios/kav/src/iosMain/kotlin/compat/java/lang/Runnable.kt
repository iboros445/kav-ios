package java.lang

fun interface Runnable { fun run() }

class InterruptedException(message: String? = null) : Exception(message)

class SecurityException(message: String? = null) : RuntimeException(message)

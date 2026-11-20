// LOCAL-CHECK ONLY: the slice of kotlin.test that the iosTest sources use, so they type-check on Linux
// (the kotlin-test klib is not downloadable here). CI compiles against the real kotlin.test.
@file:Suppress("unused")
@file:OptIn(kotlin.contracts.ExperimentalContracts::class)
package kotlin.test

import kotlin.contracts.contract

@Target(AnnotationTarget.FUNCTION) annotation class Test

fun <T> assertEquals(expected: T, actual: T, message: String? = null) {}
fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double, message: String? = null) {}
fun <T> assertNotEquals(illegal: T, actual: T, message: String? = null) {}
fun assertTrue(actual: Boolean, message: String? = null) { contract { returns() implies actual } }
fun assertFalse(actual: Boolean, message: String? = null) { contract { returns() implies !actual } }
fun <T : Any> assertNotNull(actual: T?, message: String? = null): T { contract { returns() implies (actual != null) }; return actual!! }
fun assertNull(actual: Any?, message: String? = null) {}
fun fail(message: String? = null): Nothing = throw AssertionError(message)

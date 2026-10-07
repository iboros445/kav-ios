// LOCAL-CHECK ONLY (ios/tools/klib_check.sh): just enough of androidx.compose.runtime for the
// data-layer files to type-check on Linux, where Compose cannot be resolved (Google Maven is blocked).
// Never part of the real build.
@file:Suppress("unused")
package androidx.compose.runtime

import kotlin.reflect.KProperty

interface State<out T> { val value: T }
interface MutableState<T> : State<T> { override var value: T }

fun <T> mutableStateOf(value: T): MutableState<T> = object : MutableState<T> { override var value = value }
operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value
operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) { this.value = value }

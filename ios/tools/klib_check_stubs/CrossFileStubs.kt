// LOCAL-CHECK ONLY: declarations that live in files klib_check.sh does not compile (they need Compose).
@file:Suppress("unused")
package uk.noammm.kav.ui

fun metres(la1: Double, lo1: Double, la2: Double, lo2: Double): Double = 0.0

internal const val COFFEE_URL = "https://www.buymeacoffee.com/Noamm"

// ui/Theme.kt, for MapFile and MapTileProvider.
enum class Look { DARK, OLED, LIGHT }

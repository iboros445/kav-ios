// Moovit registers this install as an Android client (Moovit.kt sends these values), so they stay plausible
// Android values on purpose. Nothing here describes the real phone.
package android.os

object Build {
    const val MANUFACTURER = "Google"
    const val BRAND = "google"
    const val MODEL = "Pixel 8"
    const val DEVICE = "shiba"
    const val PRODUCT = "shiba"

    object VERSION {
        const val RELEASE = "14"
        const val SDK_INT = 34
    }

    object VERSION_CODES {
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
        const val BAKLAVA = 36
    }
}

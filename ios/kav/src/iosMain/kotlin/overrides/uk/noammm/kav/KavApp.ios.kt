// iOS override of android/app/src/main/java/uk/noammm/kav/KavApp.kt @ 449cd8a. Keep public API identical.
//
// There's no android.app.Application on iOS: MainViewController (kavkit) makes one and calls onCreate first thing.
package uk.noammm.kav

class KavApp {
    fun onCreate() {
        uk.noammm.kav.data.RiderProxy.install(android.content.KavApplicationContext)
    }
}

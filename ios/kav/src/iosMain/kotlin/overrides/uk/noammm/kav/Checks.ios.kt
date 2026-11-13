//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/Checks.kt @ 449cd8a. Keep public API identical.
//
// Android runs GitHub checks (Google's recipe, the timetable, updates) as a JobService about every six hours. iOS
// gives an app no such schedule without a background-task entitlement a free Apple ID can't sign, so here the same
// checks run when Kav comes to the front, on a background thread. Updates come from SideStore, not from here.
package uk.noammm.kav

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import uk.noammm.kav.data.GoogleRecipe
import uk.noammm.kav.data.SignedFile

class Checks {
    companion object {
        fun watch(activity: ComponentActivity) {
            val app = activity.applicationContext
            Thread {
                runCatching { uk.noammm.kav.data.Timetable.refresh(app) }.onFailure { Log.w("KavChecks", "timetable", it) }
            }.start()
        }

        fun ensure(ctx: Context) {}

        // Kav opening: Google's recipe as kept on the phone, refreshed when old. Blocks, so off the main thread.
        fun recipesAtOpen(ctx: Context) {
            GoogleRecipe.load(ctx)
            val installed = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            val now = System.currentTimeMillis()
            if (SignedFile.stale(GoogleRecipe.checkedAt(ctx), installed, now)) runCatching { GoogleRecipe.refresh(ctx) }
        }
    }
}

// Android's installer result receiver. Nothing installs from inside Kav on iOS.
class InstallStatus

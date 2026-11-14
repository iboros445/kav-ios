package android.content

import java.io.InputStream
import platform.UIKit.UIScreen

class DisplayMetrics { val density: Float get() = UIScreen.mainScreen.scale.toFloat() }

class Resources {
    val displayMetrics: DisplayMetrics = DisplayMetrics()

    class NotFoundException(message: String? = null) : RuntimeException(message)

    /** A file from res/raw, bundled by CI as assets/raw/<name>. */
    fun openRawResource(id: Int): InputStream {
        val name = uk.noammm.kav.R.raw.files[id] ?: throw NotFoundException("Resource ID #0x" + id.toString(16))
        return KavApplicationContext.assets.open("raw/$name")
    }
}

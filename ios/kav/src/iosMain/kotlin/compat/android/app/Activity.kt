package android.app

import android.content.Context
import android.view.Window

open class Activity : Context() {
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
    val window: Window = Window()
    val splashScreen: android.window.SplashScreen by lazy { android.window.SplashScreen() }
    open fun finish() {}
    /** iOS asks for a permission once; after a refusal only the Settings app can grant it, Android's "don't ask again". */
    @Suppress("UNUSED_PARAMETER")
    fun shouldShowRequestPermissionRationale(permission: String): Boolean = false
    fun setPictureInPictureParams(p: PictureInPictureParams): Boolean = true
    fun enterPictureInPictureMode(p: PictureInPictureParams): Boolean = false
}

class PictureInPictureParams private constructor() {
    class Builder {
        fun setAspectRatio(r: android.util.Rational?): Builder = this
        fun setAutoEnterEnabled(on: Boolean): Builder = this
        fun setSeamlessResizeEnabled(on: Boolean): Builder = this
        fun build(): PictureInPictureParams = PictureInPictureParams()
    }
}

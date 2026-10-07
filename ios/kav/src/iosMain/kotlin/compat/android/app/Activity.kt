package android.app

import android.content.Context
import android.view.Window

open class Activity : Context() {
    val window: Window = Window()
    open fun finish() {}
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

// The parts of Android's view tree upstream names: a parent to remove a view from, the animatable properties and
// ViewPropertyAnimator. Compose draws everything on iOS, so these only keep values; the splash screen they animate on
// Android doesn't exist here.
package android.view

interface ViewParent

open class ViewGroup : View(), ViewParent {
    @Suppress("UNUSED_PARAMETER")
    open fun removeView(view: View) {}
}

class Property<T> internal constructor(val name: String)

class ViewPropertyAnimator internal constructor(private val view: View) {
    private var alpha: Float? = null
    private var end: java.lang.Runnable? = null
    fun alpha(value: Float): ViewPropertyAnimator { alpha = value; return this }
    fun translationY(value: Float): ViewPropertyAnimator = this
    @Suppress("UNUSED_PARAMETER") fun setStartDelay(ms: Long): ViewPropertyAnimator = this
    @Suppress("UNUSED_PARAMETER") fun setDuration(ms: Long): ViewPropertyAnimator = this
    @Suppress("UNUSED_PARAMETER") fun setInterpolator(i: android.animation.TimeInterpolator?): ViewPropertyAnimator = this
    fun withEndAction(action: java.lang.Runnable): ViewPropertyAnimator { end = action; return this }
    fun start() { alpha?.let { view.alpha = it }; end?.run() }
}

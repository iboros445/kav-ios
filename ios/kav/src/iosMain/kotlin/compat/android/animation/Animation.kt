// android.animation, for the launch screen's exit (MainActivity). iOS shows its own launch screen and never calls
// that listener, so these only have to compile: start() puts each property at its end value.
package android.animation

import android.view.Property
import android.view.View

interface TimeInterpolator { fun getInterpolation(input: Float): Float }

abstract class Animator {
    var startDelay: Long = 0
    var interpolator: TimeInterpolator? = null
    open fun setDuration(ms: Long): Animator = this
    abstract fun start()
}

class ObjectAnimator private constructor(private val target: View, private val property: Property<View>, private val value: Float) : Animator() {
    override fun setDuration(ms: Long): ObjectAnimator = this
    override fun start() {
        when (property.name) {
            "alpha" -> target.alpha = value
            "translationY" -> target.translationY = value
            "scaleX" -> target.scaleX = value
            "scaleY" -> target.scaleY = value
        }
    }

    companion object {
        fun ofFloat(target: View, property: Property<View>, vararg values: Float): ObjectAnimator =
            ObjectAnimator(target, property, values.lastOrNull() ?: 0f)
    }
}

class AnimatorSet : Animator() {
    private val all = ArrayList<Animator>()
    fun playTogether(vararg items: Animator) { all.addAll(items) }
    override fun start() { all.forEach { it.start() } }
}

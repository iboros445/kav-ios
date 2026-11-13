// android.view.animation.PathInterpolator: a cubic Bezier easing from (0,0) to (1,1).
package android.view.animation

class PathInterpolator(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) :
    android.animation.TimeInterpolator {
    override fun getInterpolation(input: Float): Float {
        // Solve x(t) = input by bisection, then return y(t).
        fun bez(a: Float, b: Float, t: Float) = 3 * a * (1 - t) * (1 - t) * t + 3 * b * (1 - t) * t * t + t * t * t
        var lo = 0f; var hi = 1f
        repeat(24) { val mid = (lo + hi) / 2; if (bez(x1, x2, mid) < input) lo = mid else hi = mid }
        return bez(y1, y2, (lo + hi) / 2)
    }
}

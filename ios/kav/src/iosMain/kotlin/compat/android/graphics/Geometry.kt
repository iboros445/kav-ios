// android.graphics.RectF and PointF: plain holders.
package android.graphics

class RectF(var left: Float, var top: Float, var right: Float, var bottom: Float) {
    constructor() : this(0f, 0f, 0f, 0f)
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2
    fun centerY(): Float = (top + bottom) / 2
}

class PointF(var x: Float, var y: Float) {
    constructor() : this(0f, 0f)
}

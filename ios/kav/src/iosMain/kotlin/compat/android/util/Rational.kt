package android.util

class Rational(val numerator: Int, val denominator: Int) {
    fun toDouble(): Double = numerator.toDouble() / denominator
}

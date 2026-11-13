// The compass, as Android's rotation-vector sensor. CoreLocation gives a heading (degrees clockwise from north);
// it is delivered as a yaw-only rotation vector whose azimuth, after getRotationMatrixFromVector / getOrientation,
// is exactly that heading. The matrix helpers are ports of the AOSP ones so upstream's maths runs unchanged.
package android.hardware

import android.location.LocationHub
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Sensor internal constructor(val type: Int) {
    companion object {
        const val TYPE_ROTATION_VECTOR = 11
        // Android's rotation without the compass; iOS gives the same heading-based rotation for both.
        const val TYPE_GAME_ROTATION_VECTOR = 15
    }
}

class SensorEvent internal constructor(val values: FloatArray, val sensor: Sensor? = null)

interface SensorEventListener {
    fun onSensorChanged(event: SensorEvent)
    fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
}

class SensorManager constructor() {
    private val subscriptions = HashMap<SensorEventListener, (Double) -> Unit>()
    private val rotation = Sensor(Sensor.TYPE_ROTATION_VECTOR)

    fun getDefaultSensor(type: Int): Sensor? =
        if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GAME_ROTATION_VECTOR) rotation else null

    fun registerListener(listener: SensorEventListener, sensor: Sensor?, @Suppress("UNUSED_PARAMETER") delay: Int): Boolean {
        if (sensor == null) return false
        val cb: (Double) -> Unit = { headingDeg ->
            val half = -headingDeg * PI / 180.0 / 2.0   // azimuth = heading  <=>  rotation of -heading about "up"
            listener.onSensorChanged(SensorEvent(floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat()), sensor))
        }
        subscriptions[listener] = cb
        LocationHub.addHeadingListener(cb)
        return true
    }

    fun unregisterListener(listener: SensorEventListener) {
        subscriptions.remove(listener)?.let { LocationHub.removeHeadingListener(it) }
    }

    companion object {
        const val SENSOR_DELAY_UI = 2
        const val SENSOR_DELAY_GAME = 1
        const val AXIS_X = 1
        const val AXIS_Y = 2
        const val AXIS_Z = 3
        const val AXIS_MINUS_X = AXIS_X or 0x80
        const val AXIS_MINUS_Y = AXIS_Y or 0x80
        const val AXIS_MINUS_Z = AXIS_Z or 0x80

        fun getRotationMatrixFromVector(R: FloatArray, rotationVector: FloatArray) {
            val q1 = rotationVector[0]; val q2 = rotationVector[1]; val q3 = rotationVector[2]
            val q0 = if (rotationVector.size >= 4) rotationVector[3] else {
                val t = 1 - q1 * q1 - q2 * q2 - q3 * q3
                if (t > 0) sqrt(t) else 0f
            }
            val sq1 = 2 * q1 * q1; val sq2 = 2 * q2 * q2; val sq3 = 2 * q3 * q3
            val q1q2 = 2 * q1 * q2; val q3q0 = 2 * q3 * q0; val q1q3 = 2 * q1 * q3
            val q2q0 = 2 * q2 * q0; val q2q3 = 2 * q2 * q3; val q1q0 = 2 * q1 * q0
            if (R.size == 9) {
                R[0] = 1 - sq2 - sq3; R[1] = q1q2 - q3q0; R[2] = q1q3 + q2q0
                R[3] = q1q2 + q3q0; R[4] = 1 - sq1 - sq3; R[5] = q2q3 - q1q0
                R[6] = q1q3 - q2q0; R[7] = q2q3 + q1q0; R[8] = 1 - sq1 - sq2
            } else if (R.size == 16) {
                R[0] = 1 - sq2 - sq3; R[1] = q1q2 - q3q0; R[2] = q1q3 + q2q0; R[3] = 0f
                R[4] = q1q2 + q3q0; R[5] = 1 - sq1 - sq3; R[6] = q2q3 - q1q0; R[7] = 0f
                R[8] = q1q3 - q2q0; R[9] = q2q3 + q1q0; R[10] = 1 - sq1 - sq2; R[11] = 0f
                R[12] = 0f; R[13] = 0f; R[14] = 0f; R[15] = 1f
            }
        }

        fun remapCoordinateSystem(inR: FloatArray, X: Int, Y: Int, outR: FloatArray): Boolean {
            val length = outR.size
            if (inR.size != length) return false
            if ((X and 0x7C) != 0 || (Y and 0x7C) != 0) return false
            if ((X and 0x3) == 0 || (Y and 0x3) == 0) return false
            if ((X and 0x3) == (Y and 0x3)) return false
            var Z = X xor Y
            val x = (X and 0x3) - 1; val y = (Y and 0x3) - 1; val z = (Z and 0x3) - 1
            val axisY = (z + 1) % 3; val axisZ = (z + 2) % 3
            if (((x xor axisY) or (y xor axisZ)) != 0) Z = Z xor 0x80
            val sx = X >= 0x80; val sy = Y >= 0x80; val sz = Z >= 0x80
            val rowLength = if (length == 16) 4 else 3
            val src = inR.copyOf()
            for (j in 0 until 3) {
                val offset = j * rowLength
                for (i in 0 until 3) {
                    if (x == i) outR[offset + i] = if (sx) -src[offset + 0] else src[offset + 0]
                    if (y == i) outR[offset + i] = if (sy) -src[offset + 1] else src[offset + 1]
                    if (z == i) outR[offset + i] = if (sz) -src[offset + 2] else src[offset + 2]
                }
            }
            if (length == 16) {
                outR[3] = 0f; outR[7] = 0f; outR[11] = 0f
                outR[12] = 0f; outR[13] = 0f; outR[14] = 0f; outR[15] = 1f
            }
            return true
        }

        fun getOrientation(R: FloatArray, values: FloatArray): FloatArray {
            if (R.size == 9) {
                values[0] = atan2(R[1], R[4])
                values[1] = asin(-R[7])
                values[2] = atan2(-R[6], R[8])
            } else {
                values[0] = atan2(R[1], R[5])
                values[1] = asin(-R[9])
                values[2] = atan2(-R[8], R[10])
            }
            return values
        }
    }
}

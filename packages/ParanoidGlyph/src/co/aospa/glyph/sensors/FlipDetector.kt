/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import co.aospa.glyph.util.wakeUpSensor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Reports when the device is flipped face down, and when it is flipped back, and when it is wiggled
 * while flipped.
 *
 * The strips are on the back, so flipped is when they face the room.
 *
 * Where the device has a sensor that reports the screen facing down, flipped follows it, as stock
 * does. Otherwise it is worked out from the accelerometer, derived from
 * frameworks/base/services/core/java/com/android/server/power/FaceDownDetector.java, which the
 * framework keeps to itself.
 *
 * @property context the context to read the sensors of.
 */
class FlipDetector(private val context: Context) {
    private companion object {
        private const val TAG = "GlyphFlipDetector"

        /** What the flip sensor reports while the screen faces down. */
        private const val FACE_DOWN = 2
    }

    /**
     * Whether the device is flipped, each time that changes.
     *
     * The flip sensor, or the accelerometer where there is none, is read while the flow is
     * collected, and only then.
     */
    val isFlipped: Flow<Boolean> = flow {
        val sensorManager = context.requireSystemService<SensorManager>()
        val sensor =
            context.glyph.config.flipSensorType
                .takeIf { it.isNotEmpty() }
                ?.let(sensorManager::wakeUpSensor)

        emitAll(
            if (sensor != null) {
                facingDown(sensor)
            } else {
                accelerometer { channel -> Tracker(onFlipped = { channel.trySend(it) }) }
            }
        )
    }

    /**
     * Whether the device is being wiggled while flipped, each time that changes.
     *
     * The accelerometer is read while the flow is collected, and only then.
     */
    val isWiggling: Flow<Boolean> = accelerometer { channel ->
        Tracker(onWiggling = { channel.trySend(it) })
    }

    /** Whether the screen faces down, as [sensor] reports it, while the flow is collected. */
    private fun facingDown(sensor: Sensor): Flow<Boolean> =
        callbackFlow {
                val sensorManager = context.requireSystemService<SensorManager>()
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val state = event.values.firstOrNull() ?: return
                            trySend(state.roundToInt() == FACE_DOWN)
                        }

                        override fun onAccuracyChanged(changed: Sensor, accuracy: Int) {
                            // Do nothing.
                        }
                    }

                GlyphLog.d(TAG) { "Registered ${sensor.stringType} listener" }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

                awaitClose {
                    GlyphLog.d(TAG) { "Unregistered ${sensor.stringType} listener" }
                    sensorManager.unregisterListener(listener)
                }
            }
            .distinctUntilChanged()

    /** What the listener made by [create] sends, while the flow is collected. */
    private fun <T> accelerometer(create: (SendChannel<T>) -> SensorEventListener): Flow<T> =
        callbackFlow {
            val sensorManager = context.requireSystemService<SensorManager>()
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (accelerometer == null) {
                Log.e(TAG, "No accelerometer on this device")
                close()
                return@callbackFlow
            }

            val listener = create(this)

            GlyphLog.d(TAG) { "Registered accelerometer listener" }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

            awaitClose {
                GlyphLog.d(TAG) { "Unregistered accelerometer listener" }
                sensorManager.unregisterListener(listener)
            }
        }

    /**
     * Takes the device as face down once the screen has pointed down for [SETTLE_TIME] and it has
     * stopped moving, so that carrying it face down does not count.
     *
     * It takes the device as wiggling while it is face down and moving.
     *
     * @property onFlipped told of every change, and never twice with the same state.
     * @property onWiggling told of every change, and never twice with the same state.
     */
    private class Tracker(
        private val onFlipped: (Boolean) -> Unit = {},
        private val onWiggling: (Boolean) -> Unit = {},
    ) : SensorEventListener {
        private companion object {
            /** How long the screen has to point down, and movement to stop. */
            private val SETTLE_TIME = 400.milliseconds

            /** The change across the screen that counts as the device moving, in m/s². */
            private const val MOVING_THRESHOLD = 0.2f

            /** How far the screen has to point down, in m/s². */
            private const val TILT_THRESHOLD = -9.5f

            /** How far it has to tilt back up again, once face down. */
            private const val TILT_THRESHOLD_LENIENT = TILT_THRESHOLD + 1.0f
        }

        private val movement = ExponentialMovingAverage()
        private val tilt = ExponentialMovingAverage()

        private var isFlipped = false
        private var isWiggling = false
        private var restingMovement = 0f

        /**
         * When the device last moved, and when the screen last turned down, by the sensor clock.
         */
        private var movedAt = Duration.ZERO
        private var tiltedAt = Duration.ZERO
        private var wasTilted = false

        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size < 3) {
                GlyphLog.d(TAG) { "Ignoring sensor event with ${event.values.size} values" }
                return
            }

            val (x, y, z) = event.values
            val now = event.timestamp.nanoseconds
            movement.update(x * x + y * y)
            tilt.update(z)

            if (abs(movement.average - restingMovement) > MOVING_THRESHOLD) {
                restingMovement = movement.average
                movedAt = now
            }
            val isMoving = now - movedAt <= SETTLE_TIME

            val threshold = if (isFlipped) TILT_THRESHOLD_LENIENT else TILT_THRESHOLD
            val isTilted = tilt.average < threshold
            if (isTilted && !wasTilted) {
                tiltedAt = now
            }
            wasTilted = isTilted

            val isSettled = isTilted && now - tiltedAt > SETTLE_TIME
            if (isSettled && !isMoving) {
                setFlipped(true)
            } else if (!isSettled) {
                setFlipped(false)
            }

            setWiggling(isFlipped && isMoving)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Do nothing.
        }

        private fun setFlipped(isFlipped: Boolean) {
            if (isFlipped == this.isFlipped) {
                return
            }

            GlyphLog.d(TAG) { "Flipped changed to $isFlipped, tilt ${tilt.average}" }
            this.isFlipped = isFlipped
            onFlipped(isFlipped)
        }

        private fun setWiggling(isWiggling: Boolean) {
            if (isWiggling == this.isWiggling) {
                return
            }

            GlyphLog.d(TAG) { "Wiggling changed to $isWiggling" }
            this.isWiggling = isWiggling
            onWiggling(isWiggling)
        }
    }

    /** Smooths a reading, so that one noisy sample cannot turn the state over. */
    private class ExponentialMovingAverage {
        private companion object {
            /** How heavily a reading is weighed against the readings before it. */
            private const val SMOOTHING = 0.5f
        }

        var average = 0f
            private set

        fun update(value: Float) {
            average = value + SMOOTHING * (average - value)
        }
    }
}

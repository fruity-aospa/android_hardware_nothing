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
import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.util.requireSystemService
import co.aospa.glyph.util.wakeUpSensor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Picks the brightness the strips light at from the light on the back of the phone, which sees what
 * they are lit against.
 *
 * @property context the context to read the sensors of.
 * @property config the levels the strips accept, and the sensor and steps that pick one.
 */
class AmbientBrightness(private val context: Context, private val config: GlyphConfig) {
    private companion object {
        /**
         * How long the sensor may hold readings back, so that it need not wake the cpu for each.
         */
        private val MAX_REPORT_LATENCY = 1.seconds
    }

    private val sensor =
        context.requireSystemService<SensorManager>().wakeUpSensor(config.ambientSensorType)

    /** Whether this device has the sensor. */
    val isSupported = sensor != null

    /**
     * The level to light the strips at, each time it changes.
     *
     * The sensor is read while the flow is collected, and only then.
     */
    val level: Flow<Int> =
        callbackFlow {
                val sensorManager = context.requireSystemService<SensorManager>()
                if (sensor == null) {
                    close()
                    return@callbackFlow
                }

                val steps =
                    if (config.isWhiteShell) config.ambientStepsWhite else config.ambientSteps

                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val lux = event.values.firstOrNull() ?: return
                            if (lux.isFinite() && lux >= 0f) trySend(levelAt(lux, steps))
                        }

                        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                            // Do nothing.
                        }
                    }

                sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    MAX_REPORT_LATENCY.inWholeMicroseconds.toInt(),
                )
                awaitClose { sensorManager.unregisterListener(listener) }
            }
            .distinctUntilChanged()

    /** The level for [lux], scaled so that the shell's brightest step is the brightest level. */
    private fun levelAt(lux: Float, steps: Map<Float, Int>): Int {
        val level = steps.entries.lastOrNull { it.key <= lux }?.value ?: steps.values.first()
        return config.maxBrightness * level / steps.values.max()
    }
}

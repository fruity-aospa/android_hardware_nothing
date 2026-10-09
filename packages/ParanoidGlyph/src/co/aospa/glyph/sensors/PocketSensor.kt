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
import co.aospa.glyph.util.requireSystemService
import co.aospa.glyph.util.wakeUpSensor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells whether the phone is in a pocket. The sensor holds a tilt threshold, so a phone that lies
 * flat on a table is not in a pocket.
 *
 * @property context the context to read the sensors of.
 */
class PocketSensor(private val context: Context) {
    private companion object {
        /** The type of the sensor. */
        private const val SENSOR_TYPE = "android.sensor.pocket_mode"

        /** What the sensor reports while the phone is in a pocket. It reports 5 when it is not. */
        private const val IN_POCKET = 0f
    }

    private val sensor = context.requireSystemService<SensorManager>().wakeUpSensor(SENSOR_TYPE)

    /**
     * Whether the phone is in a pocket, each time that changes. It ends at once on a device without
     * the sensor.
     *
     * The sensor is read while the flow is collected, and only then.
     */
    val isInPocket: Flow<Boolean> =
        callbackFlow {
                val sensorManager = context.requireSystemService<SensorManager>()
                if (sensor == null) {
                    close()
                    return@callbackFlow
                }

                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val status = event.values.firstOrNull() ?: return
                            trySend(status == IN_POCKET)
                        }

                        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                            // Do nothing.
                        }
                    }

                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                awaitClose { sensorManager.unregisterListener(listener) }
            }
            .distinctUntilChanged()
}

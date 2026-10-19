/*
 * Copyright (C) 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.SystemProperties
import android.util.Log

class PocketSensor(
    private val context: Context,
    sensorType: String,
    private val sensorValue: Float,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)!!
    private val sensor = Utils.getSensor(sensorManager, sensorType)
    private val handler = Handler(Looper.getMainLooper())
    private val touchBlocker = TouchBlocker(context)

    private var lastPulseTimestamp = 0L
    private var isCovered = false
    private var isRegistered = false
    private var isInteractive = true

    private val uncoverRunnable = Runnable {
        if (isCovered) {
            isCovered = false
            Log.d(TAG, "Uncovered (debounce elapsed), resetting pocket mode to 0")
            touchBlocker.disable()
            try {
                SystemProperties.set("sys.touch.pocket_mode", "0")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset pocket mode", e)
            }
            if (!isInteractive) {
                val delta = SystemClock.elapsedRealtime() - lastPulseTimestamp
                if (delta >= MIN_PULSE_INTERVAL_MS) {
                    lastPulseTimestamp = SystemClock.elapsedRealtime()
                    Utils.launchDozePulse(context)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]
        if (DEBUG) Log.d(TAG, "Got sensor event: $value (interactive=$isInteractive)")

        val maxRange = sensor?.maximumRange ?: 5.0f
        val isNear = if (sensorValue > 0f) {
            value <= sensorValue
        } else {
            value < maxRange
        }

        if (isNear) {
            handler.removeCallbacks(uncoverRunnable)
            if (!isCovered) {
                isCovered = true
                Log.d(TAG, "Covered, setting pocket mode to 1")
                try {
                    SystemProperties.set("sys.touch.pocket_mode", "1")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set pocket mode", e)
                }
                if (isInteractive) {
                    touchBlocker.enable()
                }
            }
        } else {
            if (isCovered) {
                handler.removeCallbacks(uncoverRunnable)
                handler.postDelayed(uncoverRunnable, DEBOUNCE_DELAY_MS)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun onDisplayOn() {
        isInteractive = true
        enable()
        if (isCovered) {
            touchBlocker.enable()
        }
    }

    fun onDisplayOff() {
        isInteractive = false
        touchBlocker.disable()
        enable()
    }

    fun enable() {
        if (sensor != null && !isRegistered) {
            Log.d(TAG, "Enabling sensor listener")
            isRegistered = true
            handler.removeCallbacks(uncoverRunnable)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun disable() {
        if (sensor != null && isRegistered) {
            Log.d(TAG, "Disabling sensor listener")
            isRegistered = false
            handler.removeCallbacks(uncoverRunnable)
            sensorManager.unregisterListener(this, sensor)
            isCovered = false
            touchBlocker.disable()
            try {
                SystemProperties.set("sys.touch.pocket_mode", "0")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset pocket mode", e)
            }
        }
    }

    companion object {
        private const val TAG = "PocketSensor"
        private const val DEBUG = true

        private const val MIN_PULSE_INTERVAL_MS = 2500L
        private const val DEBOUNCE_DELAY_MS = 300L
    }
}

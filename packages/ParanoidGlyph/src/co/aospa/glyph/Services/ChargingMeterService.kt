/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.sensors.FlipDetector
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.broadcasts
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import com.android.settingslib.fuelgauge.BatteryStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shows how far charging has got on the strips, when a charger is plugged in and again whenever the
 * device is wiggled while it stays plugged in with the screen off.
 */
class ChargingMeterService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphChargingMeterService"
    }

    private val powerManager by lazy { requireSystemService<PowerManager>() }
    private val flipDetector by lazy { FlipDetector(this) }

    /** The battery, as it is right now and each time it changes. */
    private val battery: StateFlow<BatteryStatus?> by lazy {
        broadcasts(IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            .map { BatteryStatus(it) }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
    }

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            battery
                .map { it?.isPluggedIn == true }
                .distinctUntilChanged()
                .collectLatest { isPluggedIn ->
                    if (!isPluggedIn) return@collectLatest

                    GlyphLog.d(TAG) { "Power connected at ${battery.value?.level}%" }
                    showLevel()

                    // A wiggle shows it again, while the device stays plugged in.
                    flipDetector.isWiggling.collect { isWiggling ->
                        if (isWiggling && !powerManager.isInteractive) showLevel()
                    }
                }
        }
    }

    private fun showLevel() {
        glyph.animator.playCharging(battery.value?.level ?: 0)
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.Services.CallReceiverService
import co.aospa.glyph.Services.ChargingMeterService
import co.aospa.glyph.Services.FlipToGlyphService
import co.aospa.glyph.Services.MusicVisualizerService
import co.aospa.glyph.Services.PowershareService
import co.aospa.glyph.Services.ThirdPartyService
import co.aospa.glyph.Services.VolumeLevelService
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.glyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Runs the services the settings ask for, and only those. */
class GlyphServices(
    private val context: Context,
    private val preferences: GlyphPreferences,
    private val schedule: GlyphSchedule,
) {
    companion object {
        private const val TAG = "GlyphServices"
    }

    /** A service, and the setting of its own that switches it on, if it has one. */
    private class Feature(
        val service: Class<out Service>,
        val key: String?,
        val isEnabled: () -> Boolean,
    )

    /** Every service the app can run. */
    private val features =
        listOf(
            Feature(ThirdPartyService::class.java, null) { true },
            Feature(CallReceiverService::class.java, GlyphPreferences.KEY_CALL) {
                preferences.isCallEnabled
            },
            Feature(ChargingMeterService::class.java, GlyphPreferences.KEY_CHARGING_METER) {
                preferences.isChargingMeterEnabled
            },
            Feature(PowershareService::class.java, GlyphPreferences.KEY_POWERSHARE) {
                preferences.isPowershareEnabled
            },
            Feature(VolumeLevelService::class.java, GlyphPreferences.KEY_VOLUME_METER) {
                preferences.isVolumeMeterEnabled
            },
            Feature(FlipToGlyphService::class.java, GlyphPreferences.KEY_FLIP) {
                preferences.isFlipEnabled
            },
            Feature(MusicVisualizerService::class.java, GlyphPreferences.KEY_MUSIC_VISUALIZER) {
                preferences.isMusicVisualizerEnabled
            },
        )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Keeps the services in line with the settings from now on, whoever changes them. */
    fun watch() {
        scope.launch {
            preferences.changes
                .filter { key ->
                    key == GlyphPreferences.KEY_ENABLED || features.any { it.key == key }
                }
                .collect { sync() }
        }
        scope.launch { schedule.isQuiet.collect { sync() } }
    }

    /** Brings the running services in line with the settings. */
    fun sync() {
        val isQuiet = schedule.isQuiet.value
        for (feature in features) {
            if (preferences.isEnabled && !isQuiet && feature.isEnabled()) {
                start(feature.service)
            } else {
                stop(feature.service)
            }
        }

        // ThirdPartyService stays bound, so it never stops.
        if (!preferences.isEnabled) context.glyph.animator.endFrames(GlyphStream.CLIENT)
    }

    /** Starts a service as the current user. */
    private fun start(service: Class<out Service>) {
        GlyphLog.d(TAG) { "Starting ${service.simpleName}" }
        context.startServiceAsUser(Intent(context, service), UserHandle.CURRENT)
    }

    /** Stops a service as the current user. */
    private fun stop(service: Class<out Service>) {
        GlyphLog.d(TAG) { "Stopping ${service.simpleName}" }
        context.stopServiceAsUser(Intent(context, service), UserHandle.CURRENT)
    }
}

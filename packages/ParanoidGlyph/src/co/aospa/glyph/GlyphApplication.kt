/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph

import android.app.Application
import co.aospa.glyph.data.source.GlyphAnimations
import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.sensors.AmbientBrightness
import co.aospa.glyph.sensors.PocketSensor
import co.aospa.glyph.system.GlyphAnimator
import co.aospa.glyph.system.GlyphLights
import co.aospa.glyph.system.GlyphSchedule
import co.aospa.glyph.system.GlyphServices

/** Holds what the rest of the app needs to outlive a screen. */
class GlyphApplication : Application() {
    /** What this device's strips are. */
    val config by lazy { GlyphConfig(this) }

    /** The user's settings. */
    val preferences by lazy { GlyphPreferences(this, config) }

    /** The csv animations. */
    val animations by lazy { GlyphAnimations(this) }

    /** The strips. */
    val lights by lazy { GlyphLights(this, config) }

    /** The brightness the light around the phone calls for. */
    val ambientBrightness by lazy { AmbientBrightness(this, config) }

    /** Whether the phone is in a pocket. */
    val pocket by lazy { PocketSensor(this) }

    /** When the glyphs keep quiet. */
    val schedule by lazy { GlyphSchedule(this, preferences, pocket) }

    /** Plays the animations on them. */
    val animator by lazy {
        GlyphAnimator(lights, animations, config, preferences, ambientBrightness, schedule)
    }

    /** The services the settings ask for. */
    val services by lazy { GlyphServices(this, preferences, schedule) }

    override fun onCreate() {
        super.onCreate()
        schedule.watch()
        services.watch()

        // Make the animator now, so that brightness follows the light from boot.
        animator
    }
}

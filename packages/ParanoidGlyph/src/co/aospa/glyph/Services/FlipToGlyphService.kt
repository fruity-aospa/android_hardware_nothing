/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.media.AudioManager
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.sensors.FlipDetector
import co.aospa.glyph.util.acquireForAnimation
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import kotlinx.coroutines.launch

/**
 * Silences the device and plays an animation when it is turned face down, and puts the ringer back
 * when it is turned over again, while the setting is on.
 */
class FlipToGlyphService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphFlipToGlyphService"
    }

    private val audioManager by lazy { requireSystemService<AudioManager>() }
    private val flipDetector by lazy { FlipDetector(this) }

    /** The mode to put back, when this service is what silenced the device. */
    private var silencedFrom: Int? = null

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch { flipDetector.isFlipped.collect(::onFlip) }
    }

    override fun onDestroy() {
        restoreRingerMode()
        super.onDestroy()
    }

    private fun onFlip(isFlipped: Boolean) {
        if (!isFlipped) {
            restoreRingerMode()
            return
        }

        wakeLock.acquireForAnimation()
        glyph.animator.playCsv("flip")

        val mode = audioManager.ringerModeInternal
        if (mode != AudioManager.RINGER_MODE_SILENT) {
            silencedFrom = mode
            audioManager.ringerModeInternal = AudioManager.RINGER_MODE_SILENT
        }
    }

    /** Puts the mode back, unless it was changed while the device was face down. */
    private fun restoreRingerMode() {
        val mode = silencedFrom ?: return
        silencedFrom = null

        if (audioManager.ringerModeInternal == AudioManager.RINGER_MODE_SILENT) {
            audioManager.ringerModeInternal = mode
        }
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.content.IntentFilter
import android.media.AudioManager
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.broadcasts
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Shows the music volume on the strips as it changes. */
class VolumeLevelService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphVolumeLevelService"
    }

    private val audioManager by lazy { requireSystemService<AudioManager>() }

    override fun onCreate() {
        super.onCreate()

        broadcasts(IntentFilter(AudioManager.VOLUME_CHANGED_ACTION))
            .filter { intent ->
                intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) ==
                    AudioManager.STREAM_MUSIC
            }
            .map { intent ->
                val volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0)
                100 * volume / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
            .onEach { percent ->
                GlyphLog.d(TAG) { "Volume changed to $percent%" }
                glyph.animator.playVolume(percent)
            }
            .launchIn(lifecycleScope)
    }
}

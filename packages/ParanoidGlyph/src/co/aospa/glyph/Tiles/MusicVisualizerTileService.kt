/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Tiles

import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.util.glyph

/** Turns the music visualizer on and off. */
class MusicVisualizerTileService : GlyphTile() {
    override val isActive
        get() =
            glyph.preferences.observe {
                isFeatureChosen(GlyphPreferences.KEY_MUSIC_VISUALIZER, false)
            }

    override fun setActive(active: Boolean) {
        glyph.preferences.isMusicVisualizerEnabled = active
    }
}

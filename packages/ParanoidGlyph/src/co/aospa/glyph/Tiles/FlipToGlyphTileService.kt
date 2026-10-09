/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package co.aospa.glyph.Tiles

import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.util.glyph

/** Keeps the existing Flip to Glyph tile component across the app update. */
class FlipToGlyphTileService : GlyphTile() {
    override val isActive
        get() = glyph.preferences.observe { isFeatureChosen(GlyphPreferences.KEY_FLIP, false) }

    override fun setActive(active: Boolean) {
        glyph.preferences.isFlipEnabled = active
    }
}

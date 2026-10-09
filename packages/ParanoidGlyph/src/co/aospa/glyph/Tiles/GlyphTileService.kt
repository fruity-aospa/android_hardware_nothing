/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Tiles

import co.aospa.glyph.util.glyph
import kotlinx.coroutines.flow.flowOf

/** Turns the strips on and off. */
class GlyphTileService : GlyphTile() {
    override val isAvailable = flowOf(true)

    override val isActive
        get() = glyph.preferences.observe { isEnabled }

    override fun setActive(active: Boolean) {
        glyph.preferences.isEnabled = active
    }
}

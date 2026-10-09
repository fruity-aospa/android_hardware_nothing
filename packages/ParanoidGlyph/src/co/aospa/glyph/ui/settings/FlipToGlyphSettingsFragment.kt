/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Silences the device while the strips face the room. */
class FlipToGlyphSettingsFragment : GlyphToggleFragment() {
    override val screen = R.xml.glyph_flip_to_glyph_settings
    override val title = R.string.pref_flip_to_glyph_title
    override val switchKey = GlyphPreferences.KEY_FLIP
}

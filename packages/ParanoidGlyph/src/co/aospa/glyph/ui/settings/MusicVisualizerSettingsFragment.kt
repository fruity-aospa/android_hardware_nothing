/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Follows what is playing. */
class MusicVisualizerSettingsFragment : GlyphToggleFragment() {
    override val screen = R.xml.glyph_music_visualizer_settings
    override val title = R.string.pref_music_visualizer_title
    override val switchKey = GlyphPreferences.KEY_MUSIC_VISUALIZER
}

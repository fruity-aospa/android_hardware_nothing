/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Shows the volume as it changes. */
class VolumeIndicatorSettingsFragment : GlyphToggleFragment() {
    override val screen = R.xml.glyph_volume_indicator_settings
    override val title = R.string.pref_volume_indicator_title
    override val switchKey = GlyphPreferences.KEY_VOLUME_METER
}

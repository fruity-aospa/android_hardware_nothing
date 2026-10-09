/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Shows when the device charges another one. */
class PowershareSettingsFragment : GlyphToggleFragment() {
    override val screen = R.xml.glyph_powershare_settings
    override val title = R.string.pref_powershare_title
    override val switchKey = GlyphPreferences.KEY_POWERSHARE
    override val previewKey = "glyph_powershare_preview"
}

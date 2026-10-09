/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Shows how far charging has got. */
class ChargingMeterSettingsFragment : GlyphToggleFragment() {
    override val screen = R.xml.glyph_charging_meter_settings
    override val title = R.string.pref_charging_meter_title
    override val switchKey = GlyphPreferences.KEY_CHARGING_METER
    override val previewKey = "glyph_charging_meter_preview"
}

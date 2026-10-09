/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import androidx.fragment.app.Fragment

/** Hosts [ChargingMeterSettingsFragment]. */
class ChargingMeterSettingsActivity : GlyphSettingsActivity() {
    override fun createFragment(): Fragment = ChargingMeterSettingsFragment()
}

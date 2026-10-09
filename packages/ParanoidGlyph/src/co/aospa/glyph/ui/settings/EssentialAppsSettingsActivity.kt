/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import androidx.fragment.app.Fragment

/** Hosts [EssentialAppsSettingsFragment]. */
class EssentialAppsSettingsActivity : GlyphSettingsActivity() {
    override fun createFragment(): Fragment = EssentialAppsSettingsFragment()
}

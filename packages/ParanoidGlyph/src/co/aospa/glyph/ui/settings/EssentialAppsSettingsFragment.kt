/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Chooses which apps hold a glyph lit until their notification is read. */
class EssentialAppsSettingsFragment : GlyphAppsFragment() {
    override val screen = R.xml.glyph_essential_apps_settings
    override val title = R.string.pref_essential_apps_title
    override val categoryKey = GlyphPreferences.KEY_ESSENTIAL_APPS

    override fun isAppChecked(packageName: String) = packageName in preferences.essentialApps

    override fun setAppChecked(packageName: String, checked: Boolean) =
        preferences.setAppEssential(packageName, checked)
}

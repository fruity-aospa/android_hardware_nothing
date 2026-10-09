/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences

/** Chooses which apps light the strips when they post a notification. */
class AllowedAppsSettingsFragment : GlyphAppsFragment() {
    override val screen = R.xml.glyph_allowed_apps_settings
    override val title = R.string.pref_allowed_apps_title
    override val categoryKey = GlyphPreferences.KEY_NOTIFICATIONS_CATEGORY

    override fun isAppChecked(packageName: String) = preferences.isAppAllowed(packageName)

    override fun setAppChecked(packageName: String, checked: Boolean) =
        preferences.setAppAllowed(packageName, checked)
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.PreferenceCategory
import co.aospa.glyph.data.source.launchableApps
import co.aospa.glyph.util.glyph
import com.android.settingslib.widget.AppSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** A screen with one switch per app. */
abstract class GlyphAppsFragment : SettingsBasePreferenceFragment() {
    /** The screen's preferences. */
    @get:XmlRes protected abstract val screen: Int

    /** The title the activity shows. */
    @get:StringRes protected abstract val title: Int

    /** The category the switches are added to. */
    protected abstract val categoryKey: String

    protected val preferences by lazy { glyph.preferences }

    /** Whether the switch of the app with [packageName] starts checked. */
    protected abstract fun isAppChecked(packageName: String): Boolean

    /** Stores that the switch of the app with [packageName] was turned on or off. */
    protected abstract fun setAppChecked(packageName: String, checked: Boolean)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(screen)
        activity?.setTitle(title)

        val category = findPreference<PreferenceCategory>(categoryKey) ?: return
        val packageManager = requireContext().packageManager

        for (app in launchableApps(packageManager)) {
            category.addPreference(
                AppSwitchPreference(category.context).apply {
                    key = app.packageName
                    title = app.loadLabel(packageManager)
                    icon = app.loadIcon(packageManager)
                    isChecked = isAppChecked(app.packageName)
                    isPersistent = false
                    setOnPreferenceChangeListener { _, checked ->
                        setAppChecked(app.packageName, checked as Boolean)
                        true
                    }
                }
            )
        }
    }
}

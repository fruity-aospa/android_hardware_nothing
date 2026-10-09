/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import co.aospa.glyph.R
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** The hours and days that quiet hours run on. */
class ScheduleHoursSettingsFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.glyph_schedule_hours_settings)
        activity?.setTitle(R.string.pref_schedule_hours_title)
    }
}

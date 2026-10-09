/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import co.aospa.glyph.R
import co.aospa.glyph.data.model.ScheduleMode
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.util.formatTime
import co.aospa.glyph.util.glyph
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** When the glyphs keep quiet. */
class ScheduleSettingsFragment : SettingsBasePreferenceFragment() {
    private companion object {
        private const val KEY_BEDTIME = "glyph_schedule_bedtime"
        private const val KEY_HOURS = "glyph_schedule_hours"
        private const val KEY_PREVIEW = "glyph_schedule_preview"
    }

    private val preferences by lazy { glyph.preferences }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.glyph_schedule_settings)
        activity?.setTitle(R.string.pref_schedule_title)
        showPreview(KEY_PREVIEW)

        findPreference<ListPreference>(GlyphPreferences.KEY_SCHEDULE_MODE)?.apply {
            setOnPreferenceChangeListener { _, mode ->
                showMode(ScheduleMode.of(mode as String))
                true
            }
        }
        showMode(preferences.scheduleMode)
    }

    override fun onStart() {
        super.onStart()

        // The times and days may have been changed on the screen that sets them.
        findPreference<Preference>(KEY_HOURS)?.summary = hoursSummary()
    }

    /** The times quiet hours run between, and the days they run on. */
    private fun hoursSummary(): String {
        val locale = Locale.getDefault()
        val first = WeekFields.of(locale).firstDayOfWeek
        val days = (0L until 7L).map { first.plus(it) }.filter { it in preferences.scheduleDays }
        val daysText =
            when (days.size) {
                7 -> getString(R.string.schedule_every_day)
                0 -> getString(R.string.schedule_no_days)
                else -> days.joinToString { it.getDisplayName(TextStyle.SHORT, locale) }
            }
        return getString(
            R.string.schedule_hours_summary,
            formatTime(preferences.scheduleStart),
            formatTime(preferences.scheduleEnd),
            daysText,
        )
    }

    /** Shows the settings of the mode, and only those. */
    private fun showMode(mode: ScheduleMode) {
        findPreference<Preference>(KEY_BEDTIME)?.isVisible = mode == ScheduleMode.BEDTIME
        findPreference<Preference>(KEY_HOURS)?.isVisible = mode == ScheduleMode.CUSTOM
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.preference

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.widget.TextView
import android.widget.ToggleButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import co.aospa.glyph.R
import co.aospa.glyph.util.formatTime
import co.aospa.glyph.util.glyph
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.reflect.KMutableProperty0

/** Sets the hours quiet hours run between, and the days they run on, in one place. */
class ScheduleHoursPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private companion object {
        private val DAY_IDS =
            intArrayOf(R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6)
        private const val MINUTES_IN_A_DAY = 24 * 60
    }

    private val preferences by lazy { context.glyph.preferences }

    init {
        layoutResource = R.layout.preference_schedule_hours
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val start = holder.findViewById(R.id.start_time) as TextView
        val end = holder.findViewById(R.id.end_time) as TextView
        val duration = holder.findViewById(R.id.schedule_duration) as TextView

        fun render() {
            showTime(start, R.string.pref_schedule_start_title, preferences.scheduleStart)
            showTime(end, R.string.pref_schedule_end_title, preferences.scheduleEnd)
            duration.text = durationText()
        }
        render()

        start.setOnClickListener { pick(preferences::scheduleStart, ::render) }
        end.setOnClickListener { pick(preferences::scheduleEnd, ::render) }

        // The week starts on the day the locale says, as the system's own screens do.
        val locale = Locale.getDefault()
        val first = WeekFields.of(locale).firstDayOfWeek
        for ((index, id) in DAY_IDS.withIndex()) {
            val day = first.plus(index.toLong())
            val toggle = holder.findViewById(id) as ToggleButton
            val label = day.getDisplayName(TextStyle.NARROW, locale)

            toggle.textOn = label
            toggle.textOff = label
            toggle.contentDescription = day.getDisplayName(TextStyle.FULL, locale)

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = day in preferences.scheduleDays
            toggle.setOnCheckedChangeListener { _, isChecked -> setDay(day, isChecked) }
        }
    }

    /** Shows a time of day, which says what it is for when read out. */
    private fun showTime(view: TextView, label: Int, minute: Int) {
        val time = formatTime(minute)
        view.text = time
        view.contentDescription = "${context.getString(label)}\n$time"
    }

    /** Lets the user pick another time of day, and tells [onPicked] once they have. */
    private fun pick(time: KMutableProperty0<Int>, onPicked: () -> Unit) {
        TimePickerDialog(
                context,
                { _, hour, minute ->
                    time.set(hour * 60 + minute)
                    onPicked()
                },
                time.get() / 60,
                time.get() % 60,
                DateFormat.is24HourFormat(context),
            )
            .show()
    }

    private fun setDay(day: DayOfWeek, isChosen: Boolean) {
        preferences.scheduleDays =
            if (isChosen) preferences.scheduleDays + day else preferences.scheduleDays - day
    }

    /** How long the hours last, which is a whole day when they start as they end. */
    private fun durationText(): String {
        val minutes =
            (preferences.scheduleEnd - preferences.scheduleStart + MINUTES_IN_A_DAY) %
                MINUTES_IN_A_DAY
        val length = if (minutes == 0) MINUTES_IN_A_DAY else minutes
        return context.getString(R.string.schedule_duration, length / 60, length % 60)
    }
}

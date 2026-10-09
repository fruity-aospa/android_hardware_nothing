/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.SwitchPreferenceCompat
import co.aospa.glyph.R
import co.aospa.glyph.data.model.QuietReason
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.util.ensureNotificationAccess
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.hasNotificationAccess
import com.android.settingslib.PrimarySwitchPreference
import com.android.settingslib.widget.BannerMessagePreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** The glyph settings, and the way into each feature's own screen. */
class SettingsFragment : SettingsBasePreferenceFragment() {
    companion object {
        private const val KEY_QUIET = "glyph_settings_quiet"

        /** How long the strips show the brightness being changed. */
        private val BRIGHTNESS_PREVIEW_DURATION = 500.milliseconds
    }

    private val preferences by lazy { glyph.preferences }
    private val config by lazy { glyph.config }
    private val animator by lazy { glyph.animator }

    /** The switch of each feature, with the setting that says whether the feature is enabled. */
    private val features by lazy {
        mapOf(
            GlyphPreferences.KEY_CALL to preferences::isCallEnabled,
            GlyphPreferences.KEY_NOTIFICATIONS to preferences::areNotificationsEnabled,
            GlyphPreferences.KEY_CHARGING_METER to preferences::isChargingMeterEnabled,
            GlyphPreferences.KEY_POWERSHARE to preferences::isPowershareEnabled,
            GlyphPreferences.KEY_VOLUME_METER to preferences::isVolumeMeterEnabled,
            GlyphPreferences.KEY_FLIP to preferences::isFlipEnabled,
            GlyphPreferences.KEY_MUSIC_VISUALIZER to preferences::isMusicVisualizerEnabled,
        )
    }

    /** Why the strips are quiet, which is not why they are off while Glyph is turned off. */
    private val quietReason by lazy {
        preferences
            .observe { isEnabled }
            .combine(glyph.schedule.reason) { isOn, reason ->
                if (isOn) reason else QuietReason.NONE
            }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.glyph_settings)

        findPreference<MainSwitchPreference>(GlyphPreferences.KEY_ENABLED)?.apply {
            setOnPreferenceChangeListener { _, checked ->
                preferences.isEnabled = checked as Boolean
                true
            }
        }

        findPreference<BannerMessagePreference>(KEY_QUIET)?.apply {
            setPositiveButtonText(R.string.quiet_banner_action)
            setPositiveButtonOnClickListener {
                startActivity(Intent(requireContext(), ScheduleSettingsActivity::class.java))
            }
        }

        findPreference<SliderPreference>(GlyphPreferences.KEY_BRIGHTNESS)?.apply {
            min = 1
            max = config.brightnessLevels.size
            value = preferences.brightnessStep
            setUpdatesContinuously(true)
            sliderIncrement = 1
            setTickVisible(true)
        }

        findPreference<PrimarySwitchPreference>(GlyphPreferences.KEY_CALL)?.apply {
            setOnPreferenceChangeListener { _, checked ->
                preferences.isCallEnabled = checked as Boolean
                true
            }
        }

        findPreference<PrimarySwitchPreference>(GlyphPreferences.KEY_NOTIFICATIONS)?.apply {
            setOnPreferenceChangeListener { _, checked ->
                val enabled = checked as Boolean
                val isAllowed = !enabled || requireContext().ensureNotificationAccess()
                if (isAllowed) preferences.areNotificationsEnabled = enabled
                isAllowed
            }
        }

        findPreference<PrimarySwitchPreference>(GlyphPreferences.KEY_POWERSHARE)?.isVisible =
            config.isPowershareSupported

        findPreference<SwitchPreferenceCompat>(GlyphPreferences.KEY_AUTO_BRIGHTNESS)?.isVisible =
            glyph.ambientBrightness.isSupported

        glyph.services.sync()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The screen follows the settings for as long as it is shown, whoever changes them.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                render()

                launch { preferences.changes.collect { render() } }
                launch { quietReason.collect { showQuietBanner(it) } }
                launch {
                    preferences.changes
                        .filter { it == GlyphPreferences.KEY_BRIGHTNESS }
                        .collectLatest { previewBrightness() }
                }
            }
        }
    }

    /** Says that the strips are dark for quiet hours, and why, until they are not. */
    private fun showQuietBanner(reason: QuietReason) {
        val summary =
            when (reason) {
                QuietReason.SCHEDULE -> getString(R.string.quiet_banner_schedule)
                QuietReason.DND -> getString(R.string.quiet_banner_dnd)
                QuietReason.BATTERY_SAVER -> getString(R.string.quiet_banner_battery_saver)
                QuietReason.NONE,
                QuietReason.POCKET -> null
            }
        findPreference<BannerMessagePreference>(KEY_QUIET)?.apply {
            isVisible = summary != null
            this.summary = summary
        }
    }

    /** Shows what the settings say, with the features out of reach while Glyph is off. */
    private fun render() {
        findPreference<MainSwitchPreference>(GlyphPreferences.KEY_ENABLED)?.isChecked =
            preferences.isEnabled

        for ((key, setting) in features) {
            findPreference<PrimarySwitchPreference>(key)?.apply {
                isChecked = setting()
                isEnabled = preferences.isEnabled
                setSwitchEnabled(preferences.isEnabled)
            }
        }

        findPreference<SliderPreference>(GlyphPreferences.KEY_BRIGHTNESS)?.isEnabled =
            preferences.isEnabled &&
                !(glyph.ambientBrightness.isSupported && preferences.isAutoBrightnessEnabled)

        findPreference<PrimarySwitchPreference>(GlyphPreferences.KEY_NOTIFICATIONS)?.summary =
            getString(
                if (requireContext().hasNotificationAccess) {
                    R.string.pref_notification_animation_summary
                } else {
                    R.string.notifications_access_required
                }
            )
    }

    /** Lights the strips while the slider moves, so the level can be seen, then darkens them. */
    private suspend fun previewBrightness() {
        animator.previewBrightness()

        try {
            delay(BRIGHTNESS_PREVIEW_DURATION)
        } finally {
            animator.endBrightnessPreview()
        }
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.ui.preference.GlyphAnimationPreference
import co.aospa.glyph.util.ensureNotificationAccess
import co.aospa.glyph.util.glyph
import com.android.settingslib.widget.ButtonPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlin.time.Duration.Companion.seconds

/** Chooses which apps light the strips, and how. */
class NotificationAnimationSettingsFragment : SettingsBasePreferenceFragment() {
    companion object {
        /** How long the preview rests between plays, since these animations are longer. */
        private val PREVIEW_PAUSE = 1.5.seconds
    }

    private val preferences by lazy { glyph.preferences }
    private val animations by lazy { glyph.animations }

    private var preview: GlyphAnimationPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.glyph_notification_animation_settings)
        activity?.setTitle(R.string.pref_notification_animation_title)

        findPreference<MainSwitchPreference>(GlyphPreferences.KEY_NOTIFICATIONS_SWITCH)?.apply {
            isChecked = preferences.areNotificationsEnabled
            setOnPreferenceChangeListener { _, checked ->
                val enabled = checked as Boolean
                val isAllowed = !enabled || requireContext().ensureNotificationAccess()
                if (isAllowed) {
                    preferences.areNotificationsEnabled = enabled
                    preview?.updateAnimation(
                        enabled,
                        preferences.notificationAnimation,
                        PREVIEW_PAUSE,
                        preferences.isNotificationAnimationReversed,
                    )
                }
                isAllowed
            }
        }

        findPreference<ListPreference>(GlyphPreferences.KEY_NOTIFICATIONS_ANIMATION)?.apply {
            entries = animations.notifications
            entryValues = animations.notifications
            if (value !in animations.notifications) {
                value = preferences.notificationAnimation
            }
            setOnPreferenceChangeListener { _, animation ->
                preview?.updateAnimation(
                    preferences.areNotificationsEnabled,
                    animation.toString(),
                    PREVIEW_PAUSE,
                    preferences.isNotificationAnimationReversed,
                )
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(GlyphPreferences.KEY_NOTIFICATIONS_REVERSE)?.apply {
            setOnPreferenceChangeListener { _, reversed ->
                preview?.updateAnimation(
                    preferences.areNotificationsEnabled,
                    preferences.notificationAnimation,
                    PREVIEW_PAUSE,
                    reversed as Boolean,
                )
                true
            }
        }

        findPreference<ButtonPreference>(GlyphPreferences.KEY_NOTIFICATIONS_PLAY)?.apply {
            setOnClickListener {
                glyph.animator.playNotification(
                    preferences.notificationAnimation,
                    preferences.isNotificationAnimationReversed,
                    preferences.areNotificationTonesSynced,
                )
            }
        }

        findPreference<Preference>(GlyphPreferences.KEY_ESSENTIAL_APPS)?.apply {
            setOnPreferenceClickListener { !requireContext().ensureNotificationAccess() }
        }

        preview = findPreference(GlyphPreferences.KEY_NOTIFICATIONS_PREVIEW)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preview?.updateAnimation(
            preferences.areNotificationsEnabled,
            preferences.notificationAnimation,
            PREVIEW_PAUSE,
            preferences.isNotificationAnimationReversed,
        )
    }
}

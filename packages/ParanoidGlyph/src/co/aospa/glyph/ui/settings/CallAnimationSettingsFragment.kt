/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import co.aospa.glyph.R
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.ui.preference.GlyphAnimationPreference
import co.aospa.glyph.util.glyph
import com.android.settingslib.widget.ButtonPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Chooses the animation a call rings with. */
class CallAnimationSettingsFragment : SettingsBasePreferenceFragment() {
    private val preferences by lazy { glyph.preferences }
    private val animations by lazy { glyph.animations }

    private var preview: GlyphAnimationPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.glyph_call_animation_settings)
        activity?.setTitle(R.string.pref_call_animation_title)

        findPreference<MainSwitchPreference>(GlyphPreferences.KEY_CALL_SWITCH)?.apply {
            isChecked = preferences.isCallEnabled
            setOnPreferenceChangeListener { _, checked ->
                val enabled = checked as Boolean
                preferences.isCallEnabled = enabled
                preview?.updateAnimation(enabled, preferences.callAnimation)
                true
            }
        }

        findPreference<ListPreference>(GlyphPreferences.KEY_CALL_ANIMATION)?.apply {
            entries = animations.calls
            entryValues = animations.calls
            if (value !in animations.calls) {
                value = preferences.callAnimation
            }
            setOnPreferenceChangeListener { _, animation ->
                preview?.updateAnimation(preferences.isCallEnabled, animation.toString())
                true
            }
        }

        findPreference<ButtonPreference>(GlyphPreferences.KEY_CALL_PLAY)?.apply {
            setOnClickListener { glyph.animator.playCsv(preferences.callAnimation) }
        }

        preview = findPreference(GlyphPreferences.KEY_CALL_PREVIEW)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preview?.updateAnimation(preferences.isCallEnabled, preferences.callAnimation)
    }
}

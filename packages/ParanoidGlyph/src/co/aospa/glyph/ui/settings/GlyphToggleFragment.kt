/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.TwoStatePreference
import co.aospa.glyph.util.glyph
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** A screen whose only control is the switch that turns its feature on. */
abstract class GlyphToggleFragment : SettingsBasePreferenceFragment() {
    /** The screen's preferences. */
    @get:XmlRes protected abstract val screen: Int

    /** The title the activity shows. */
    @get:StringRes protected abstract val title: Int

    /** The key of the switch, which is also the key of the setting it saves. */
    protected abstract val switchKey: String

    /** The key of the preview, which is also the name of the raw it plays. */
    protected open val previewKey: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(screen)
        activity?.setTitle(title)
        previewKey?.let { showPreview(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switch = findPreference<TwoStatePreference>(switchKey) ?: return

        // A tile can change the setting while this screen is open.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                glyph.preferences.changes
                    .filter { it == switchKey }
                    .collect {
                        switch.isChecked =
                            glyph.preferences.isFeatureChosen(switchKey, switch.isChecked)
                    }
            }
        }
    }
}

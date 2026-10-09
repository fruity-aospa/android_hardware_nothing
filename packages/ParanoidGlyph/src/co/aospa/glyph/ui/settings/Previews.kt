/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.widget.IllustrationPreference

/** Plays the preview named after its key, or hides it where the device ships none. */
fun PreferenceFragmentCompat.showPreview(key: String) {
    val preview = findPreference<IllustrationPreference>(key) ?: return
    val raw = resources.getIdentifier(key, "raw", requireContext().packageName)
    // Base resources include empty placeholders where a device has no preview.
    if (raw != 0 && resources.openRawResource(raw).use { it.read() != -1 }) {
        preview.setLottieAnimationResId(raw)
    } else {
        preview.isVisible = false
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import androidx.fragment.app.Fragment

/** Hosts [CallAnimationSettingsFragment]. */
class CallAnimationSettingsActivity : GlyphSettingsActivity() {
    override fun createFragment(): Fragment = CallAnimationSettingsFragment()
}

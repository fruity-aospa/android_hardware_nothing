/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.collapsingtoolbar.R as CollapsingToolbarR

/** Hosts one preference screen under the Settings collapsing toolbar. */
abstract class GlyphSettingsActivity : CollapsingToolbarBaseActivity() {
    /** The screen this activity shows. */
    protected abstract fun createFragment(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (supportFragmentManager.findFragmentById(CollapsingToolbarR.id.content_frame) == null) {
            supportFragmentManager.commit {
                add(CollapsingToolbarR.id.content_frame, createFragment())
            }
        }
    }

    override fun onNavigateUp(): Boolean {
        finish()
        return true
    }
}

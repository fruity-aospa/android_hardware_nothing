/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Tiles

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import co.aospa.glyph.ui.settings.FlipToGlyphSettingsActivity
import co.aospa.glyph.ui.settings.MusicVisualizerSettingsActivity
import co.aospa.glyph.ui.settings.SettingsActivity
import co.aospa.glyph.util.GlyphLog

/** Opens the glyph settings from a long press on one of the tiles. */
class TileActivity : Activity() {
    companion object {
        private const val TAG = "GlyphTileActivity"

        /** The screen a long press on each tile opens. */
        private val SCREENS =
            mapOf(
                GlyphTileService::class.java.name to SettingsActivity::class.java,
                TorchTileService::class.java.name to SettingsActivity::class.java,
                FlipToGlyphTileService::class.java.name to FlipToGlyphSettingsActivity::class.java,
                MusicVisualizerTileService::class.java.name to
                    MusicVisualizerSettingsActivity::class.java,
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source =
            intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        GlyphLog.d(TAG) { "Opened from ${source?.className}" }

        val screen = SCREENS[source?.className]
        if (screen != null) {
            open(Intent(this, screen))
        } else {
            finish()
        }
    }

    private fun open(destination: Intent) {
        destination.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        // Finish first so this trampoline never stays in the back stack.
        finish()

        try {
            startActivity(destination)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity for $destination", e)
        }
    }
}

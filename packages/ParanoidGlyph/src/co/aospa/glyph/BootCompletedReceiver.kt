/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.glyph

/** Starts the services the settings ask for, once the device is up. */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "GlyphBootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        GlyphLog.d(TAG) { "Boot completed" }
        context.glyph.lights.darken()
        context.glyph.services.sync()
    }
}

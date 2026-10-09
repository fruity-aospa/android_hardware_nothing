/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.util

import android.util.Log

/**
 * Debug logs that stay quiet until asked for: set `log.tag.Glyph` to `DEBUG` for every tag at once,
 * or `log.tag.<tag>` for one.
 */
object GlyphLog {
    private const val TAG = "Glyph"

    /** Whether debug logs under [tag] are on. */
    fun isLoggable(tag: String) = Log.isLoggable(TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)

    /** Writes a debug log, building the message only while it is on. */
    inline fun d(tag: String, message: () -> String) {
        if (isLoggable(tag)) Log.d(tag, message())
    }
}

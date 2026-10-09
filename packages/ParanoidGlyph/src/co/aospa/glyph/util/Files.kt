/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.util

import android.util.Log
import java.io.File
import java.io.IOException

private const val TAG = "GlyphFiles"

/** The first line of a sysfs node, or null where it cannot be read. */
fun readFirstLine(path: String): String? =
    try {
        File(path).useLines { it.firstOrNull() }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read $path: $e")
        null
    }

/** The first line of a sysfs node as a number, or 0. */
fun readInt(path: String): Int = readFirstLine(path)?.removePrefix("0x")?.toIntOrNull() ?: 0

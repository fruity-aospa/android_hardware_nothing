/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.source

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import co.aospa.glyph.R
import co.aospa.glyph.Utils.OGGParser

/**
 * The csv animations, as the assets hold them. Each line is one frame, with a level per light.
 *
 * Reading throws [java.io.IOException] where an animation cannot be read.
 */
class GlyphAnimations(private val context: Context, private val config: GlyphConfig) {
    companion object {
        private const val TAG = "GlyphAnimations"
    }

    private val assets = context.assets

    private val defaultCall = context.getString(R.string.config_defaultCallAnimation)
    private val defaultNotification =
        context.getString(R.string.config_defaultNotificationAnimation)

    /** The ringtones to choose from. */
    val calls: Array<String> by lazy { list("call") }

    /** The notification animations to choose from. */
    val notifications: Array<String> by lazy { list("notification") }

    /** Reads a ringtone, falling back to the default. */
    fun readCall(name: String, useSynced: Boolean = true): List<List<String>> =
        (if (useSynced) synced(RingtoneManager.TYPE_RINGTONE) else null)
            ?: readAsset("call/${if (name in calls) name else defaultCall}.csv")

    /** Reads a notification animation, falling back to the default. */
    fun readNotification(name: String, useSynced: Boolean = true): List<List<String>> =
        (if (useSynced) synced(RingtoneManager.TYPE_NOTIFICATION) else null)
            ?: readAsset("notification/${if (name in notifications) name else defaultNotification}.csv")

    /** Reads an animation by name, wherever it lives. */
    fun read(name: String): List<List<String>> =
        when (name) {
            in calls -> readCall(name)
            in notifications -> readNotification(name)
            else -> readAsset("$name.csv")
        }

    private fun readAsset(path: String): List<List<String>> =
        assets.open(path).reader().useLines { lines ->
            lines.map(::parseLine).toList()
        }

    /** Reads valid Glyph CSV embedded in the current system tone, where it carries any. */
    private fun synced(type: Int): List<List<String>>? {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, type) ?: return null
        return try {
            val csv = context.contentResolver.openInputStream(uri)?.use { OGGParser(it).animation }
                ?: return null
            csv.lineSequence()
                .filter { it.isNotBlank() }
                .map(::parseLine)
                .toList()
                .takeIf { frames ->
                    frames.isNotEmpty() && frames.all { config.frameOf(it) != null }
                }
        } catch (e: Exception) {
            Log.d(TAG, "The selected tone has no compatible Glyph animation", e)
            null
        }
    }

    private fun parseLine(line: String) =
        line.replace(" ", "").removeSuffix(",").split(",")

    private fun list(directory: String): Array<String> =
        assets.list(directory).orEmpty().map { it.removeSuffix(".csv") }.toTypedArray()
}

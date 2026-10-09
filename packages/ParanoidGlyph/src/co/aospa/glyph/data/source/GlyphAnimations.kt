/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.source

import android.content.Context
import co.aospa.glyph.R

/**
 * The csv animations, as the assets hold them. Each line is one frame, with a level per light.
 *
 * Reading throws [java.io.IOException] where an animation cannot be read.
 */
class GlyphAnimations(context: Context) {
    private val assets = context.assets

    private val defaultCall = context.getString(R.string.config_defaultCallAnimation)
    private val defaultNotification =
        context.getString(R.string.config_defaultNotificationAnimation)

    /** The ringtones to choose from. */
    val calls: Array<String> by lazy { list("call") }

    /** The notification animations to choose from. */
    val notifications: Array<String> by lazy { list("notification") }

    /** Reads a ringtone, falling back to the default. */
    fun readCall(name: String): List<List<String>> =
        readAsset("call/${if (name in calls) name else defaultCall}.csv")

    /** Reads a notification animation, falling back to the default. */
    fun readNotification(name: String): List<List<String>> =
        readAsset("notification/${if (name in notifications) name else defaultNotification}.csv")

    /** Reads an animation by name, wherever it lives. */
    fun read(name: String): List<List<String>> =
        when (name) {
            in calls -> readCall(name)
            in notifications -> readNotification(name)
            else -> readAsset("$name.csv")
        }

    private fun readAsset(path: String): List<List<String>> =
        assets.open(path).reader().useLines { lines ->
            lines.map { it.replace(" ", "").removeSuffix(",").split(",") }.toList()
        }

    private fun list(directory: String): Array<String> =
        assets.list(directory).orEmpty().map { it.removeSuffix(".csv") }.toTypedArray()
}

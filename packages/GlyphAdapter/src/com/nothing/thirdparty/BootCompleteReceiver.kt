/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.thirdparty

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts [GlyphService] at boot, so that it is already running when apps bind to it. */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.startService(Intent(context, GlyphService::class.java))
    }
}

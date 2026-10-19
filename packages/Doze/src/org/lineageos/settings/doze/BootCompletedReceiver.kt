/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Starting")
        PreferenceManager.setDefaultValues(context, R.xml.doze_settings, false)
        Utils.checkDozeService(context)
    }

    companion object {
        private const val TAG = "NothingDoze"
    }
}

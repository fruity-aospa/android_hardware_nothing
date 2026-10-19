/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager

class TouchBlocker(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)!!
    private val handler = Handler(Looper.getMainLooper())
    private var blockerLayout: View? = null
    private var isBlockerAdded = false

    fun enable() {
        handler.post {
            if (!isBlockerAdded) {
                if (blockerLayout == null) {
                    blockerLayout = View(context).apply {
                        isClickable = true
                        isFocusable = false
                        setOnTouchListener { _, _ -> true }
                    }
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT,
                ).apply {
                    title = "PocketTouchBlocker"
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                try {
                    windowManager.addView(blockerLayout, params)
                    isBlockerAdded = true
                    Log.d(TAG, "Touch blocker view added to WindowManager")
                } catch (e: Exception)
                {
                    Log.e(TAG, "Failed to add touch blocker view", e)
                }
            }
        }
    }

    fun disable() {
        handler.post {
            if (isBlockerAdded && blockerLayout != null) {
                try {
                    windowManager.removeView(blockerLayout)
                    isBlockerAdded = false
                    Log.d(TAG, "Touch blocker view removed from WindowManager")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove touch blocker view", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TouchBlocker"
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.content.Intent
import androidx.lifecycle.LifecycleService
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.acquireForAnimation
import co.aospa.glyph.util.newWakeLock

/**
 * A service that runs for as long as the setting for its feature is on.
 *
 * Its work goes in `lifecycleScope`, which is canceled when the service stops, so whatever was
 * started there cleans up after itself.
 */
abstract class FeatureService(private val tag: String) : LifecycleService() {
    /** Keeps the device awake while an animation plays, through [acquireForAnimation]. */
    protected val wakeLock by lazy { newWakeLock(tag) }

    override fun onCreate() {
        super.onCreate()
        GlyphLog.d(tag) { "Started" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        GlyphLog.d(tag) { "Stopped" }
        super.onDestroy()
    }
}

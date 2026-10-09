/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Process
import android.os.IBinder
import co.aospa.glyph.system.GlyphStream
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.glyph
import com.nothing.thirdparty.IGlyphService

/**
 * Lets apps drive the strips through the glyph sdk, for as long as one is bound through the adapter
 * and the glyphs are enabled.
 */
class ThirdPartyService : Service() {
    companion object {
        private const val TAG = "GlyphThirdPartyService"
    }

    private fun enforceAdapterCaller() {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw SecurityException("Only the system Glyph adapter may drive this endpoint")
        }
    }

    private val service =
        object : IGlyphService.Stub() {
            override fun setFrameColors(levels: IntArray?) {
                enforceAdapterCaller()
                if (!glyph.preferences.isEnabled) return
                GlyphLog.d(TAG) { "Frame of ${levels.contentToString()}" }
                levels?.let { frame ->
                    glyph.animator.setFrame(
                        GlyphStream.CLIENT,
                        frame.map(Int::toFloat).toFloatArray(),
                    )
                }
            }

            override fun openSession() {
                enforceAdapterCaller()
                GlyphLog.d(TAG) { "Session opened" }
            }

            override fun closeSession() {
                enforceAdapterCaller()
                GlyphLog.d(TAG) { "Session closed" }
                glyph.animator.endFrames(GlyphStream.CLIENT)
            }

            override fun register(name: String): Boolean {
                enforceAdapterCaller()
                return true
            }

            override fun registerSDK(name: String, key: String): Boolean {
                enforceAdapterCaller()
                return true
            }

            // The matrix is the dot display of a later phone, which this is not.
            override fun registerMatrixSDK(name: String): Boolean {
                enforceAdapterCaller()
                return false
            }

            override fun setMatrixColors(levels: IntArray?) { enforceAdapterCaller() }

            override fun setGlyphMatrixTimeout(active: Boolean) { enforceAdapterCaller() }

            override fun setAppMatrixColors(levels: IntArray?) { enforceAdapterCaller() }

            override fun closeAppMatrix() { enforceAdapterCaller() }
        }

    override fun onBind(intent: Intent?): IBinder = service

    override fun onDestroy() {
        glyph.animator.endFrames(GlyphStream.CLIENT)
        super.onDestroy()
    }

    // The adapter, and every client behind it, is gone.
    override fun onUnbind(intent: Intent?): Boolean {
        glyph.animator.endFrames(GlyphStream.CLIENT)
        return super.onUnbind(intent)
    }
}

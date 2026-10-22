/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nothing.thirdparty

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

class IGlyphServiceImpl(private val context: Context) : IGlyphService.Stub() {
    @Volatile
    private var glyphService: IGlyphService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            glyphService = IGlyphService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            glyphService = null
        }

        override fun onBindingDied(name: ComponentName?) {
            glyphService = null
        }

        override fun onNullBinding(name: ComponentName?) {
            glyphService = null
        }
    }

    init {
        bindglyphService()
    }

    private fun bindglyphService() {
        val intent = Intent("com.nothing.thirdparty.IGlyphService").apply {
            component = ComponentName("co.aospa.glyph", "co.aospa.glyph.Services.ThirdPartyService")
        }
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            Log.e("IGlyphServiceImpl", "Failed to bind the glyph engine service")
        }
    }

    override fun setFrameColors(iArray: IntArray?) {
        Log.i("IGlyphServiceImpl", "updateLedFrame - ${iArray.contentToString()}")
        if (iArray != null) {
            glyphService?.setFrameColors(iArray)
        }
    }

    override fun openSession() {
        Log.i("IGlyphServiceImpl", "openSession")
        glyphService?.openSession()
    }

    override fun closeSession() {
        Log.i("IGlyphServiceImpl", "closeSession")
        glyphService?.closeSession()
    }


    override fun register(str: String) = true
    override fun registerSDK(str1: String, str2: String) = true

    override fun registerMatrixSDK(str: String) = glyphService?.registerMatrixSDK(str) ?: false

    override fun setMatrixColors(iArray: IntArray?) {
        glyphService?.setMatrixColors(iArray)
    }

    override fun setGlyphMatrixTimeout(active: Boolean) {
        glyphService?.setGlyphMatrixTimeout(active)
    }

    override fun setAppMatrixColors(iArray: IntArray?) {
        glyphService?.setAppMatrixColors(iArray)
    }

    override fun closeAppMatrix() {
        glyphService?.closeAppMatrix()
    }
}

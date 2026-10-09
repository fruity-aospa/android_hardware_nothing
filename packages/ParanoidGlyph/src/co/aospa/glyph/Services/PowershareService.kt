/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.os.FileObserver
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.R
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.acquireForAnimation
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.readInt
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Plays an animation each time the device starts charging another one. Reads the charging state
 * only while reverse charging is armed.
 */
class PowershareService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphPowershareService"

        /** How often the active node is read while reverse charging is armed. */
        private val POLL_INTERVAL = 1.seconds
    }

    // The nodes are read straight from sysfs.
    private val activePath by lazy { getString(R.string.config_powershareActivePath) }
    private val enabledPath by lazy { getString(R.string.config_powershareEnabledPath) }

    /** Whether reverse charging is armed, as it is now and each time the node changes. */
    private val isArmed: Flow<Boolean> =
        callbackFlow {
                val observer =
                    object : FileObserver(File(enabledPath), MODIFY) {
                        override fun onEvent(event: Int, path: String?) {
                            trySend(readInt(enabledPath) == 1)
                        }
                    }

                trySend(readInt(enabledPath) == 1)
                observer.startWatching()
                awaitClose { observer.stopWatching() }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * Whether something is being charged, which is only looked for while reverse charging is armed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val isCharging: Flow<Boolean> =
        isArmed
            .flatMapLatest { armed ->
                if (!armed) {
                    flowOf(false)
                } else {
                    flow {
                        while (true) {
                            emit(readInt(activePath) == 1)
                            delay(POLL_INTERVAL)
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Plays the animation once each time something starts charging.
        isCharging
            .filter { it }
            .onEach {
                GlyphLog.d(TAG) { "Charging another device" }
                wakeLock.acquireForAnimation()
                glyph.animator.playCsv("powershare")
            }
            .launchIn(lifecycleScope)
    }
}

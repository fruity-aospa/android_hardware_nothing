/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.util.awaitService
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Rings the strips for as long as a call is ringing, while the setting is on. */
class CallReceiverService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphCallReceiverService"

        /** Where call state listeners register. */
        private const val TELEPHONY_REGISTRY = "telephony.registry"
    }

    private val telephonyManager by lazy { requireSystemService<TelephonyManager>() }

    /** Whether a call is ringing, which silent and vibrating calls do as well. */
    private val isRinging: Flow<Boolean> =
        callbackFlow {
                val callback =
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            trySend(state == TelephonyManager.CALL_STATE_RINGING)
                        }
                    }

                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                awaitClose { telephonyManager.unregisterTelephonyCallback(callback) }
            }
            .distinctUntilChanged()

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            // Telephony can start after this app.
            if (!awaitService(TELEPHONY_REGISTRY)) {
                Log.e(TAG, "May not find $TELEPHONY_REGISTRY")
                return@launch
            }

            isRinging.collectLatest { ringing ->
                if (!ringing) return@collectLatest

                // The glyphs ring for as long as this block does, and stop when it is canceled.
                try {
                    glyph.animator.playCall(glyph.preferences.callAnimation)
                    awaitCancellation()
                } finally {
                    glyph.animator.stopCall()
                }
            }
        }
    }
}

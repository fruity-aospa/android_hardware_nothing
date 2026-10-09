/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import co.aospa.glyph.R
import co.aospa.glyph.util.glyph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A tile that turns one thing on and off. */
abstract class GlyphTile : TileService() {
    /** Whether the thing is on, as it is now and each time that changes. */
    protected abstract val isActive: Flow<Boolean>

    /** Whether the tile can be used, which it cannot while the glyphs are off. */
    protected open val isAvailable: Flow<Boolean>
        get() = glyph.preferences.observe { isEnabled }

    /** Turns the thing on or off. */
    protected abstract fun setActive(active: Boolean)

    /** Lasts while the tile is shown, which is the only time it needs to follow the thing. */
    private var scope: CoroutineScope? = null

    /** The state last emitted by [isActive], which a click flips. */
    private var isShownActive = false

    override fun onStartListening() {
        super.onStartListening()

        scope =
            MainScope().apply {
                launch {
                    isActive.combine(isAvailable, ::Pair).collect { (isOn, isAvailable) ->
                        isShownActive = isOn
                        show(isOn, isAvailable)
                    }
                }
            }
    }

    override fun onStopListening() {
        scope?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        setActive(!isShownActive)
    }

    private fun show(isOn: Boolean, isAvailable: Boolean) {
        qsTile?.apply {
            contentDescription =
                getString(
                    if (isOn) R.string.tile_on_content_description
                    else R.string.tile_off_content_description
                )
            state =
                when {
                    !isAvailable -> Tile.STATE_UNAVAILABLE
                    isOn -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
            updateTile()
        }
    }
}

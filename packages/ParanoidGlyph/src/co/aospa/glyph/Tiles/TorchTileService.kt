/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Tiles

import co.aospa.glyph.util.glyph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Lights every glyph as a torch. */
class TorchTileService : GlyphTile() {
    override val isActive
        get() = glyph.animator.isTorchWanted

    /** The torch lights nothing while the strips are quiet, so it cannot be used then either. */
    override val isAvailable: Flow<Boolean>
        get() =
            super.isAvailable.combine(glyph.schedule.isQuiet) { isOn, isQuiet -> isOn && !isQuiet }

    override fun setActive(active: Boolean) = glyph.animator.setTorch(active)

    override fun onTileRemoved() {
        super.onTileRemoved()
        glyph.animator.setTorch(false)
    }
}

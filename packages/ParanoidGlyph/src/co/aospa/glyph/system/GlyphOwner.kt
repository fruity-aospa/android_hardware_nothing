/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

/** Who can claim the strips, the one that outranks the rest first. */
internal enum class GlyphOwner {
    /** Every glyph at the brightness, for as long as it is on. */
    TORCH,

    /** The ringtone, until the call stops ringing. */
    CALL,

    /**
     * Something that just happened, shown once: a notification, a flip, reverse charging, or the
     * volume or charging meter. The newest replaces the one before.
     */
    EVENT,

    /** Every glyph at the level the brightness slider is being moved to. */
    PREVIEW,

    /** The streams, in the order of [GlyphStream]. */
    CLIENT,
    VISUALIZER,
}

/** The owner a stream claims the strips as. */
internal val GlyphStream.owner: GlyphOwner
    get() =
        when (this) {
            GlyphStream.CLIENT -> GlyphOwner.CLIENT
            GlyphStream.VISUALIZER -> GlyphOwner.VISUALIZER
        }

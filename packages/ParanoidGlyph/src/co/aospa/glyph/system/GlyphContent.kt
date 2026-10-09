/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import co.aospa.glyph.system.GlyphLights.Group
import kotlin.time.TimeSource
import kotlinx.coroutines.Job

/** What an owner puts on the strips. */
internal sealed interface GlyphContent {
    /** Whether this draws every light of [group], so that what was there need not be cleared. */
    fun fills(group: Group): Boolean

    /** Every glyph at the brightness: the torch, or the brightness slider's preview. */
    data object AllGlyphs : GlyphContent {
        override fun fills(group: Group) = false
    }

    /** The frame a stream last sent, held until its next. */
    class StreamFrame(val levels: FloatArray) : GlyphContent {
        override fun fills(group: Group) = group == Group.GLYPHS
    }

    /**
     * Frames the lights keep time for, on one group. Claims the strips before its frames are read,
     * so that it takes them at once.
     *
     * @property iterations how many times it plays, 0 to repeat until stopped
     */
    class Playback(val group: Group, val iterations: Int) : GlyphContent {
        /** Reads the frames, plays them, and releases the strips once they are done. */
        var job: Job? = null

        /** The frames, once they are read. */
        var frames: List<FloatArray>? = null

        private var startedAt: TimeSource.Monotonic.ValueTimeMark? = null

        override fun fills(group: Group) = group == this.group

        /** The frames from where it has got to, starting it the first time it is asked. */
        fun framesFromNow(): List<FloatArray> {
            val frames = frames ?: return emptyList()
            val started =
                startedAt ?: return frames.also { startedAt = TimeSource.Monotonic.markNow() }

            val played = (started.elapsedNow() / GlyphLights.FRAME_PERIOD).toInt()
            if (iterations != 0) return frames.drop(played.coerceAtMost(frames.size))

            val offset = played % frames.size
            return frames.drop(offset) + frames.take(offset)
        }
    }
}

/** Whether this draws every glyph, where there is anything at all. */
internal fun GlyphContent?.fillsGlyphs() = this?.fills(Group.GLYPHS) == true

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import android.content.Context
import android.graphics.Color
import android.hardware.lights.ColorSequence
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.hardware.lights.MultiLightEffect
import android.util.Log
import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.requireSystemService
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Drives the glyph strips through [LightsManager]. */
class GlyphLights(context: Context, private val config: GlyphConfig) {
    companion object {
        private const val TAG = "GlyphLights"

        /** The strips redraw at 60Hz. */
        val FRAME_PERIOD = 16.milliseconds

        /** The id of the light that lights every segment at once. */
        private const val ALL_GLYPHS_ID = 200
    }

    /** A run of lights that a frame is drawn on, by their ids. */
    enum class Group(
        /** The lowest light id in the group. */
        val firstId: Int,
        /** The light id the group stops short of. */
        val endId: Int,
    ) {
        GLYPHS(210, 250),
        VOLUME(250, 270),
        BATTERY(270, Int.MAX_VALUE),
    }

    /** The lights of each group, and the session that draws on them. */
    private class Strips(
        val groups: Map<Group, List<Light>>,
        val allGlyphs: Light?,
        val session: LightsManager.LightsSession,
    )

    private val lightsManager = context.requireSystemService<LightsManager>()

    /** The strips, or null if the hal has none. The framework only reads them once, at boot. */
    private val strips: Strips? by lazy { resolveStrips() }

    /** How many lights the group has, which is how wide its frames are. */
    fun size(group: Group): Int = strips?.groups?.getValue(group)?.size ?: 0

    /** Draws one frame on a group. */
    fun setFrame(group: Group, levels: FloatArray) {
        val strips = strips ?: return

        val request = LightsRequest.Builder()
        strips.groups.getValue(group).forEachIndexed { index, light ->
            request.addLight(light, stateOf(levels.getOrElse(index) { 0f }))
        }

        strips.session.requestLights(request.build())
    }

    /** Sets some glyphs to one level, leaving the others as they are. */
    fun setGlyphs(indices: Iterable<Int>, level: Float) {
        val strips = strips ?: return
        val lights = indices.mapNotNull(strips.groups.getValue(Group.GLYPHS)::getOrNull)
        if (lights.isEmpty()) return

        val request = LightsRequest.Builder()
        lights.forEach { request.addLight(it, stateOf(level)) }

        strips.session.requestLights(request.build())
    }

    /** Sets every glyph at once. */
    fun setAll(level: Float) {
        val strips = strips ?: return
        val light = strips.allGlyphs ?: return

        strips.session.requestLights(
            LightsRequest.Builder().addLight(light, stateOf(level)).build()
        )
    }

    /**
     * Hands a whole animation over at once, one level per light per frame.
     *
     * @param iterations how many times to play it, 0 to repeat until stopped
     */
    fun playAnimation(group: Group, frames: List<FloatArray>, iterations: Int) {
        val strips = strips ?: return
        if (frames.isEmpty()) {
            return
        }

        val effect = MultiLightEffect.Builder().setIterations(iterations).setPreemptive(true)

        strips.groups.getValue(group).forEachIndexed { index, light ->
            val sequence =
                ColorSequence.Builder().setInterpolationMode(ColorSequence.INTERPOLATION_MODE_NONE)
            frames.forEachIndexed { frameIndex, levels ->
                // The first control point is where the animation starts from.
                val offset = if (frameIndex == 0) Duration.ZERO else FRAME_PERIOD
                sequence.addControlPoint(
                    offset.inWholeMilliseconds,
                    colorOf(levels.getOrElse(index) { 0f }),
                )
            }

            // The last frame holds for a period too, so even a single one has a length.
            sequence.addControlPoint(
                FRAME_PERIOD.inWholeMilliseconds,
                colorOf(frames.last().getOrElse(index) { 0f }),
            )
            effect.addLightSequence(light, sequence.build())
        }

        GlyphLog.d(TAG) { "Playing ${frames.size} frames on $group" }
        strips.session.requestLights(LightsRequest.Builder().setEffect(effect.build()).build())
    }

    /** Stops whatever is playing on a group and darkens it. */
    fun stop(group: Group = Group.GLYPHS) {
        val strips = strips ?: return

        val request = LightsRequest.Builder()
        strips.groups.getValue(group).forEach { request.addLight(it, stateOf(0f)) }

        strips.session.requestLights(request.build())
    }

    /** Darkens every glyph, for a start that knows nothing of what a gone owner left lit. */
    fun darken() = Group.entries.forEach { stop(it) }

    private fun resolveStrips(): Strips? {
        val lights = lightsManager.lights

        val groups =
            Group.entries.associateWith { group ->
                lights.filter { it.id in group.firstId until group.endId }.sortedBy { it.ordinal }
            }
        if (groups.getValue(Group.GLYPHS).isEmpty()) {
            Log.e(TAG, "The hal reports no glyph lights")
            return null
        }

        GlyphLog.d(TAG) { "Opening a session over " + groups.map { "${it.value.size} ${it.key}" } }
        return Strips(
            groups,
            lights.firstOrNull { it.id == ALL_GLYPHS_ID },
            lightsManager.openSession(),
        )
    }

    /** Turns a brightness into a color. The strips are white only. */
    private fun colorOf(level: Float): Int {
        val channel = (level / config.maxBrightness * 255f).roundToInt().coerceIn(0, 255)
        return Color.rgb(channel, channel, channel)
    }

    /** Turns a brightness into the state of a light. */
    private fun stateOf(level: Float): LightState =
        LightState.Builder().setColor(colorOf(level)).build()
}

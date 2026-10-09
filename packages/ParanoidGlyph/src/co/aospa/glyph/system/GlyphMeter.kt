/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.system.GlyphLights.Group
import kotlin.time.Duration

/** Draws a meter: a bar that fills to a level, holds there, then drains away. */
class GlyphMeter(private val lights: GlyphLights, private val config: GlyphConfig) {
    /**
     * Fills a meter on [group] to [level], holds it there, then drains it away.
     *
     * @param whole whether it sweeps the whole bar, or only fills from dark
     * @param continuing whether the group is already showing a meter, which it fills on from
     */
    fun frames(
        group: Group,
        level: Int,
        hold: Duration,
        rest: Duration,
        whole: Boolean,
        continuing: Boolean,
    ): List<FloatArray> {
        val width = lights.size(group)
        val litLevel = config.maxBrightness.toFloat()

        // The battery meter keeps its dot, and the step beside it, lit throughout.
        val first = if (group == Group.BATTERY && config.hasBatteryDot) 2 else 1
        val lit =
            if (group == Group.VOLUME && level == 0) 0 else level * (width - first) / 100 + first

        // Charging sweeps the whole bar; the volume meter only fills from dark.
        val peak = if (whole) width else lit
        val sweep = whole || !continuing

        return buildList {
            val bar = FloatArray(width)

            fun show() {
                add(bar.copyOf())
            }

            for (i in 0..<peak) {
                bar[i] = litLevel
                if (sweep && i >= first - 1) show()
            }
            for (i in peak - 1 downTo lit) {
                bar[i] = 0f
                show()
            }
            if (isEmpty()) show()

            repeat((hold / GlyphLights.FRAME_PERIOD).toInt()) { show() }
            for (i in lit - 1 downTo 0) {
                bar[i] = 0f
                show()
            }
            repeat((rest / GlyphLights.FRAME_PERIOD).toInt()) { show() }
        }
    }
}

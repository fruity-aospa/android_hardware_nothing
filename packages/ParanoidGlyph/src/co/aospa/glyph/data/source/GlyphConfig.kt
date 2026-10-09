/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.source

import android.content.Context
import android.os.SystemProperties
import androidx.core.content.res.use
import co.aospa.glyph.R
import co.aospa.glyph.data.model.VisualizerBand
import kotlin.time.Duration.Companion.milliseconds

/** What this device's strips are, as its resources describe them. */
class GlyphConfig(context: Context) {
    private companion object {
        /** The property that holds the shell color, as black or white. */
        private const val SHELL_PROPERTY = "persist.vendor.glyph.color"
    }

    private val resources = context.resources

    /** The device the resources were built for. */
    val device: String = resources.getString(R.string.config_device)

    /** Highest level the strips accept. */
    val maxBrightness: Int = resources.getInteger(R.integer.config_brightnessMax)

    /** Whether the shell is white, which diffuses more light than a black one. */
    val isWhiteShell: Boolean
        get() = SystemProperties.get(SHELL_PROPERTY) == "white"

    private val blackBrightnessLevels = resources.getIntArray(R.array.config_brightnessLevels)
    private val whiteBrightnessLevels = resources.getIntArray(R.array.config_brightnessLevelsWhite)

    /** The levels the brightness slider steps through, the white shell's where it has its own. */
    val brightnessLevels: IntArray
        get() =
            whiteBrightnessLevels.takeIf { it.isNotEmpty() && isWhiteShell }
                ?: blackBrightnessLevels

    /** The type of the light sensor behind the strips, empty where there is none. */
    val ambientSensorType: String = resources.getString(R.string.config_ambientSensorType)

    /** The type of the sensor that reports the screen facing down, empty where there is none. */
    val flipSensorType: String = resources.getString(R.string.config_flipSensorType)

    /** The level auto brightness lights a black shell at, from the lux each step starts at. */
    val ambientSteps = steps(R.array.config_ambientLevels)

    /** The same for a white shell. */
    val ambientStepsWhite = steps(R.array.config_ambientLevelsWhite)

    init {
        check(ambientSensorType.isEmpty() || ambientSteps.isNotEmpty()) {
            "The ambient light sensor has no steps"
        }
    }

    /** How many levels a frame may carry, one entry per group of lights. */
    val patternLengths: IntArray = resources.getIntArray(R.array.config_patternLengths)

    /** The channels each zone of a narrower frame lights, in the order the frame lists them. */
    private val frameZones = zones(R.array.config_frameZones)

    /**
     * The channels each band of the music visualizer lights, in the order its tuning lists them.
     */
    private val visualizerZones = zones(R.array.config_visualizerZones)

    /**
     * How each band of the music visualizer listens and fades, in the order of its zones, or of the
     * glyphs where it has none.
     */
    val visualizerBands = visualizerBands()

    init {
        check(visualizerZones.isEmpty() || visualizerZones.size == visualizerBands.size) {
            "${visualizerZones.size} visualizer zones but ${visualizerBands.size} bands"
        }
    }

    /** The channels each glyph of the device drawing shows. */
    val glyphViewZones: List<IntRange> = zones(R.array.config_glyphViewZones)

    /** The channels an essential notification holds lit. */
    val essentialGlyphs: Set<Int> = zones(R.array.config_essentialZones).flatten().toSet()

    /** Whether the battery meter's first step is the dot beside it. */
    val hasBatteryDot: Boolean = resources.getBoolean(R.bool.config_batteryDot)

    /** Whether this device can charge another one. */
    val isPowershareSupported: Boolean =
        resources.getString(R.string.config_powershareActivePath).isNotEmpty()

    /** Whether a frame is of a width this device takes, with a finite level throughout. */
    fun accepts(levels: FloatArray) = levels.size in patternLengths && levels.all { it.isFinite() }

    /** Turns a csv line into a frame of every channel, or null where this device cannot draw it. */
    fun frameOf(line: List<String>): FloatArray? {
        val levels = line.map { it.toFloatOrNull() ?: return null }.toFloatArray()
        return if (accepts(levels)) widen(levels) else null
    }

    /** Spreads a frame of the narrower width over the channels of each of its zones. */
    fun widen(levels: FloatArray) = spread(levels, frameZones)

    /** Spreads the music visualizer's bands over the channels each one lights. */
    fun spreadBands(levels: FloatArray) = spread(levels, visualizerZones)

    private fun visualizerBands(): List<VisualizerBand> {
        val buckets = zones(R.array.config_visualizerBuckets)
        val decayWindows = resources.getIntArray(R.array.config_visualizerDecayWindows)
        val triggerRatios = doubles(R.array.config_visualizerTriggerRatios)
        val triggerDecays = doubles(R.array.config_visualizerTriggerDecays)
        val ceilingDecays = doubles(R.array.config_visualizerCeilingDecays)
        val retrigger = resources.getInteger(R.integer.config_visualizerRetrigger).milliseconds

        val lengths =
            setOf(
                buckets.size,
                decayWindows.size,
                triggerRatios.size,
                triggerDecays.size,
                ceilingDecays.size,
            )
        check(lengths.size == 1) { "The visualizer band arrays differ in length: $lengths" }

        return List(buckets.size) { band ->
            VisualizerBand(
                buckets = buckets[band],
                decayWindow = decayWindows[band].milliseconds,
                triggerRatio = triggerRatios[band],
                triggerDecay = triggerDecays[band],
                ceilingDecay = ceilingDecays[band],
                retrigger = retrigger,
            )
        }
    }

    /** Reads an array of decimals, which getIntArray cannot. */
    private fun doubles(array: Int): DoubleArray =
        resources.obtainTypedArray(array).use { values ->
            DoubleArray(values.length()) { index ->
                val value = values.getFloat(index, Float.NaN).toDouble()
                check(value.isFinite()) {
                    "Item $index of ${resources.getResourceEntryName(array)} is not a number"
                }
                value
            }
        }

    /** Each step's level, by the lux it starts at, dimmest first. */
    private fun steps(levels: Int): Map<Float, Int> {
        val lux = resources.getIntArray(R.array.config_ambientLux)
        val values = resources.getIntArray(levels)
        check(lux.size == values.size) { "${lux.size} ambient lux steps but ${values.size} levels" }
        check(lux.asList() == lux.sorted()) { "The ambient lux steps are not dimmest first" }
        return lux.indices.associate { lux[it].toFloat() to values[it] }
    }

    private fun zones(array: Int) =
        resources.getStringArray(array).map { zone ->
            val ends = zone.split('-').map { it.toInt() }
            ends.first()..ends.last()
        }

    /** Gives each zone's channels its level, or leaves a frame that is not of the zones' width. */
    private fun spread(levels: FloatArray, zones: List<IntRange>): FloatArray {
        if (zones.isEmpty() || levels.size != zones.size) return levels

        val spread = FloatArray(zones.maxOf { it.last } + 1)
        zones.forEachIndexed { zone, channels -> channels.forEach { spread[it] = levels[zone] } }
        return spread
    }
}

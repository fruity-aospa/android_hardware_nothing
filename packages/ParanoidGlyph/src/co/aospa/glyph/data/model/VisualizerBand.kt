/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.model

import kotlin.time.Duration

/**
 * How one band of the music visualizer listens, and how fast it lights and fades.
 *
 * @property buckets the fft buckets it averages.
 * @property decayWindow how long after a beat its ceiling holds before it starts to fall.
 * @property triggerRatio the part of its ceiling a value has to reach to count as a beat.
 * @property triggerDecay what its brightness is multiplied by each capture between beats.
 * @property ceilingDecay what its ceiling is multiplied by each capture once it falls.
 * @property retrigger how long it waits after a beat before it can beat again.
 */
data class VisualizerBand(
    val buckets: IntRange,
    val decayWindow: Duration,
    val triggerRatio: Double,
    val triggerDecay: Double,
    val ceilingDecay: Double,
    val retrigger: Duration,
)

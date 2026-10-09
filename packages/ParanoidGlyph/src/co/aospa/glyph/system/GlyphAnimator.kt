/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import android.util.Log
import co.aospa.glyph.data.source.GlyphAnimations
import co.aospa.glyph.data.source.GlyphConfig
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.sensors.AmbientBrightness
import co.aospa.glyph.system.GlyphContent.AllGlyphs
import co.aospa.glyph.system.GlyphContent.Playback
import co.aospa.glyph.system.GlyphContent.StreamFrame
import co.aospa.glyph.system.GlyphLights.Group
import co.aospa.glyph.util.GlyphLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays everything that lights the strips, one owner at a time.
 *
 * The torch outranks a ringing call, which outranks an event, which outranks the brightness
 * preview, which outranks the streams, as [GlyphOwner] orders them. Each claims the strips with
 * what it would show, and the one that outranks the rest is drawn; when it releases them, the next
 * is drawn again from where it has got to. A notification's essential glyphs are lit on top of
 * whatever is shown.
 */
class GlyphAnimator(
    private val lights: GlyphLights,
    private val animations: GlyphAnimations,
    private val config: GlyphConfig,
    private val preferences: GlyphPreferences,
    private val ambient: AmbientBrightness,
    private val schedule: GlyphSchedule,
) {
    companion object {
        private const val TAG = "GlyphAnimator"

        /** How long the charging meter holds at the level, and rests after. */
        private val CHARGING_HOLD = 2.seconds
        private val CHARGING_REST = 730.milliseconds

        /** How long the volume meter holds at the level, and rests after. */
        private val VOLUME_HOLD = 1800.milliseconds
        private val VOLUME_REST = 730.milliseconds

        /** The steps an essential notification fades in over, as a percentage. */
        private val ESSENTIAL_STEPS = intArrayOf(1, 2, 4, 7)
        private val ESSENTIAL_STEP = 25.milliseconds

        /** What an essential notification holds its glyph at, as a percentage. */
        private const val ESSENTIAL_FLOOR = 7
    }

    private val meter = GlyphMeter(lights, config)

    /** Everything below is only touched from here. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /**
     * What each owner that has claimed the strips would show, the one that outranks the rest first.
     */
    private val claims = sortedMapOf<GlyphOwner, GlyphContent>()

    /** What is on the strips now. */
    private var displayed: GlyphContent? = null

    /** The essential glyphs' fade in, which stopping it cancels. */
    private var essentialFade: Job? = null

    /** Whether a notification holds the essential glyphs. */
    private var isEssentialHeld = false

    private val _isTorchWanted = MutableStateFlow(false)

    /** Whether the torch was asked for, which quiet hours darken but do not cancel. */
    val isTorchWanted: StateFlow<Boolean> = _isTorchWanted.asStateFlow()

    /** Whether the glyphs may light at all, which they may not while off or quiet. */
    private val isOutputEnabled
        get() = preferences.isEnabled && !schedule.isQuiet.value

    /** The level the light around the phone calls for, while it sets the brightness. */
    private var ambientLevel: Int? = null

    private val brightness
        get() = ambientLevel ?: preferences.brightness

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ambientLevels =
        combine(
                preferences.observe { ambient.isSupported && isAutoBrightnessEnabled },
                schedule.isQuiet,
            ) { isAuto, isQuiet ->
                isAuto && !isQuiet
            }
            .flatMapLatest { if (it) ambient.level else flowOf(null) }

    init {
        scope.launch { preferences.observe { brightness }.collect { render(force = true) } }
        scope.launch {
            ambientLevels.collect {
                ambientLevel = it
                render(force = true)
            }
        }
        scope.launch {
            combine(preferences.observe { isEnabled }, schedule.isQuiet) { _, _ -> }
                .collect { applyOutputState() }
        }
    }

    /** Plays a csv animation once. */
    fun playCsv(name: String) = post {
        startPlayback(GlyphOwner.EVENT, name) { animations.read(name).toFrames(name) }
    }

    /** Rings until [stopCall], as the call does. */
    fun playCall(name: String) = post {
        startPlayback(GlyphOwner.CALL, name, iterations = 0) {
            animations.readCall(name).toFrames(name)
        }
    }

    /** Stops the ringing. */
    fun stopCall() = post {
        if (GlyphOwner.CALL !in claims) return@post

        GlyphLog.d(TAG) { "Stopping the ringtone" }
        release(GlyphOwner.CALL)
        render()
    }

    /** Shows how far charging has got, filling and draining to the level, unless it is shown. */
    fun playCharging(batteryLevel: Int) =
        playMeter(
            "charging",
            Group.BATTERY,
            batteryLevel,
            CHARGING_HOLD,
            CHARGING_REST,
            whole = true,
        )

    /** Shows the volume, filling and draining to the level. */
    fun playVolume(volumeLevel: Int) =
        playMeter("volume", Group.VOLUME, volumeLevel, VOLUME_HOLD, VOLUME_REST, whole = false)

    /** Holds the essential glyphs lit until [stopEssential]. */
    fun playEssential() = post {
        if (!isOutputEnabled) return@post

        if (isEssentialHeld) {
            if (isEssentialUncovered()) drawEssentialFloor()
            return@post
        }

        GlyphLog.d(TAG) { "Playing the essential glyphs" }
        isEssentialHeld = true

        // What is on the glyphs carries the floor once it is drawn again.
        if (displayed.fillsGlyphs()) {
            render(force = true)
            return@post
        }

        essentialFade = launch {
            for (step in ESSENTIAL_STEPS) {
                if (!isEssentialUncovered()) return@launch
                lights.setGlyphs(config.essentialGlyphs, scaledLevel(percent(step), true))
                delay(ESSENTIAL_STEP)
            }
        }
    }

    /** Lets the essential glyphs go dark. */
    fun stopEssential() = post {
        GlyphLog.d(TAG) { "Stopping the essential glyphs" }
        isEssentialHeld = false
        essentialFade?.cancel()

        when {
            displayed.fillsGlyphs() -> render(force = true)
            displayed != AllGlyphs -> lights.setGlyphs(config.essentialGlyphs, 0f)
        }
    }

    /** Lights every glyph for the torch, which outranks everything else. */
    fun setTorch(enabled: Boolean) = post {
        if (enabled && !isOutputEnabled) return@post

        _isTorchWanted.value = enabled
        if (enabled) {
            // The torch cancels an event, which would be over before the torch is put out.
            release(GlyphOwner.EVENT)
            claims[GlyphOwner.TORCH] = AllGlyphs
        } else {
            claims.remove(GlyphOwner.TORCH)
        }
        render()
    }

    /**
     * Lights every glyph at the brightness while its slider is moved, until [endBrightnessPreview].
     */
    fun previewBrightness() = post {
        claims[GlyphOwner.PREVIEW] = AllGlyphs
        render()
    }

    /** Ends the brightness preview, showing what it outranked. */
    fun endBrightnessPreview() = post { if (claims.remove(GlyphOwner.PREVIEW) != null) render() }

    /** Keeps a stream's frame, and draws it if nothing that outranks it is shown. */
    fun setFrame(stream: GlyphStream, levels: FloatArray) {
        if (!config.accepts(levels)) {
            Log.w(TAG, "Ignoring a frame of ${levels.size} levels")
            return
        }

        // The client may change its levels once it has handed them over.
        val frame = StreamFrame(config.widen(levels).copyOf())
        post {
            claims[stream.owner] = frame
            render()
        }
    }

    /** Ends a stream, showing what it outranked. */
    fun endFrames(stream: GlyphStream) = post { if (claims.remove(stream.owner) != null) render() }

    /** Darkens the strips when they may not light, and claims them for the torch when they may. */
    private fun applyOutputState() {
        if (isOutputEnabled) {
            if (isTorchWanted.value) claims[GlyphOwner.TORCH] = AllGlyphs
            render(force = true)
            return
        }

        // The streams keep their claims, to be shown again once the strips may light.
        if (!preferences.isEnabled) _isTorchWanted.value = false
        claims.remove(GlyphOwner.TORCH)
        release(GlyphOwner.CALL)
        release(GlyphOwner.EVENT)
        isEssentialHeld = false
        essentialFade?.cancel()

        // The all-glyphs light first, or its cached color skips the next torch.
        lights.setAll(0f)
        lights.darken()
        displayed = null
    }

    /** Runs a change after every one before it. */
    private fun post(change: CoroutineScope.() -> Unit) {
        scope.launch { change() }
    }

    /**
     * Draws whatever outranks the rest, after clearing what it replaced. The only place the strips
     * change hands.
     *
     * @param force whether to draw it even if it is already displayed, for a change of brightness
     *   or of the essential glyphs
     */
    private fun render(force: Boolean = false) {
        val top = if (isOutputEnabled) claims.values.firstOrNull() else null
        val previous = displayed
        if (top === previous && !force) return

        displayed = top
        if (previous != null && previous !== top) clearReplaced(previous, top)
        if (top != null) draw(top)
        if (isEssentialHeld && isEssentialUncovered()) drawEssentialFloor()
    }

    /** Clears what was displayed, where what comes next does not draw over it. */
    private fun clearReplaced(previous: GlyphContent, next: GlyphContent?) {
        when (previous) {
            // The all-glyphs light first, or its cached color skips the next torch.
            AllGlyphs -> lights.setAll(0f)
            is Playback -> if (next?.fills(previous.group) != true) clear(previous.group)
            is StreamFrame -> if (!next.fillsGlyphs()) clear(Group.GLYPHS)
        }
    }

    private fun draw(content: GlyphContent) {
        when (content) {
            AllGlyphs -> lights.setAll(brightness.toFloat())
            is StreamFrame -> lights.setFrame(Group.GLYPHS, scaled(content.levels))
            is Playback -> {
                val frames = content.framesFromNow()
                if (frames.isNotEmpty()) {
                    lights.playAnimation(
                        content.group,
                        frames.map { scaled(it, content.group) },
                        content.iterations,
                    )
                }
            }
        }
    }

    /** Draws the essential glyphs at their floor, by themselves. */
    private fun drawEssentialFloor() {
        lights.setGlyphs(config.essentialGlyphs, scaledLevel(0f, isEssential = true))
    }

    /** Whether the essential glyphs light by themselves, with nothing displayed on the glyphs. */
    private fun isEssentialUncovered() = displayed != AllGlyphs && !displayed.fillsGlyphs()

    /** Claims the strips for an animation, reads its frames off this thread, then plays them. */
    private fun CoroutineScope.startPlayback(
        owner: GlyphOwner,
        name: String,
        iterations: Int = 1,
        group: Group = Group.GLYPHS,
        build: () -> List<FloatArray>,
    ) {
        // A call rings under the torch, to be shown once it is off; an event does not wait.
        if (!isOutputEnabled || (owner == GlyphOwner.EVENT && isOutranked(owner))) {
            GlyphLog.d(TAG) { "The strips are in use, dropping $name" }
            return
        }

        release(owner)
        val playback = Playback(group, iterations)
        claims[owner] = playback
        render()

        playback.job = launch {
            val frames =
                try {
                    withContext(Dispatchers.IO) { build() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read $name: $e")
                    emptyList()
                }

            if (frames.isEmpty()) {
                claims.remove(owner)
                render()
                return@launch
            }

            GlyphLog.d(TAG) { "Playing $name, ${frames.size} frames" }
            playback.frames = frames
            if (displayed === playback) draw(playback)

            // Wait it out, so the next animation does not cut this one short.
            if (iterations == 0) awaitCancellation()
            delay(GlyphLights.FRAME_PERIOD * frames.size)

            claims.remove(owner)
            render()
            GlyphLog.d(TAG) { "Done playing $name" }
        }
    }

    /** Whether an owner above this one has claimed the strips. */
    private fun isOutranked(owner: GlyphOwner) = claims.keys.any { it < owner }

    /** Withdraws an owner's claim and stops its playback, without drawing what is next. */
    private fun release(owner: GlyphOwner) {
        (claims.remove(owner) as? Playback)?.job?.cancel()
    }

    /** Plays a meter on the group it was measured for. */
    private fun playMeter(
        name: String,
        group: Group,
        level: Int,
        hold: Duration,
        rest: Duration,
        whole: Boolean,
    ) = post {
        val continuing = (claims[GlyphOwner.EVENT] as? Playback)?.group == group

        // A meter that sweeps the whole bar is not started over while it is being shown.
        if (continuing && whole) return@post

        startPlayback(GlyphOwner.EVENT, name, group = group) {
            meter.frames(group, level, hold, rest, whole, continuing)
        }
    }

    /** Darkens a group, without dropping the essential glyphs a notification holds. */
    private fun clear(group: Group) {
        if (group == Group.GLYPHS && isEssentialHeld) {
            lights.setFrame(group, scaled(FloatArray(lights.size(group))))
        } else {
            lights.stop(group)
        }
    }

    /** Turns the lines of a csv into frames of levels, rejecting any that do not fit. */
    private fun List<List<String>>.toFrames(name: String): List<FloatArray> = map { line ->
        requireNotNull(config.frameOf(line)) {
            "$name has a line of ${line.size} levels it cannot draw"
        }
    }

    /** Applies the user's brightness, and the floor the essential glyphs hold. */
    private fun scaled(levels: FloatArray, group: Group = Group.GLYPHS) =
        FloatArray(levels.size) { glyph ->
            val isEssential = group == Group.GLYPHS && glyph in config.essentialGlyphs
            scaledLevel(levels[glyph], isEssential)
        }

    private fun scaledLevel(level: Float, isEssential: Boolean): Float {
        val floored =
            if (isEssential && isEssentialHeld) maxOf(level, percent(ESSENTIAL_FLOOR)) else level
        return floored / config.maxBrightness * brightness
    }

    /** Returns [of] percent of the brightest level the strips accept. */
    private fun percent(of: Int) = config.maxBrightness / 100f * of
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.lifecycle.lifecycleScope
import co.aospa.glyph.data.model.VisualizerBand
import co.aospa.glyph.system.GlyphStream
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.requireSystemService
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the strips from the live output mix's fft, while media is playing and the setting is on.
 *
 * Each band tracks its own adaptive peak, so a beat is a value clearing a fraction of that band's
 * recent ceiling rather than a fixed threshold, and brightness decays exponentially between beats
 * instead of a fixed-length flash. Captures are volume-normalized, while the unscaled rms level
 * gates silence and quiet track endings.
 */
class MusicVisualizerService : FeatureService(TAG) {
    companion object {
        private const val TAG = "GlyphMusicVisualizerService"

        /** How many buckets the fft is summed into. */
        private const val FFT_BUCKETS = 100

        /** What a bucket has to clear to count as more than noise. */
        private const val NOISE_FLOOR = 10.0f

        /** What a bucket is divided by and scaled to, to land in 0..1. */
        private const val NORMALIZE_DIVISOR = 195.0
        private const val NORMALIZE_SCALE = 2.5

        /** What a band has to hold to be worth lighting at all. */
        private const val MIN_BAND_ENERGY = 0.02

        /** Where silence begins and signal resumes, in millibels. */
        private const val SILENCE_RMS_MB = -6000
        private const val SIGNAL_RMS_MB = -5700

        /** How many frames a change has to hold for before it is taken. */
        private const val SIGNAL_CONFIRMATION_FRAMES = 3
        private const val SILENCE_CONFIRMATION_FRAMES = 5

        /** What a band is dark enough to be called dark at. */
        private const val DARK_BRIGHTNESS = 0.05
    }

    /** How bright one band is, against the loudest it has lately been. */
    private class BandState {
        private var ceiling = 0.0

        /** When the band last beat, or null if it has not since it was reset. */
        private var lastBeat: TimeMark? = null

        var brightness = 0.0
            private set

        fun reset() {
            ceiling = 0.0
            lastBeat = null
            brightness = 0.0
        }

        fun update(value: Double, config: VisualizerBand) {
            if (value > ceiling) ceiling = value

            val sinceBeat = lastBeat?.elapsedNow() ?: Duration.INFINITE
            if (
                sinceBeat >= config.retrigger &&
                    value > MIN_BAND_ENERGY &&
                    value >= ceiling * config.triggerRatio
            ) {
                brightness = 1.0
                lastBeat = TimeSource.Monotonic.markNow()
            } else {
                decayBrightness(config)
            }

            if (sinceBeat > config.decayWindow && value < ceiling * config.triggerRatio) {
                ceiling =
                    maxOf(ceiling * config.ceilingDecay, MIN_BAND_ENERGY / config.triggerRatio)
            }
        }

        fun decayBrightness(config: VisualizerBand) {
            brightness *= config.triggerDecay
            if (brightness < DARK_BRIGHTNESS) brightness = 0.0
        }
    }

    private val audioManager by lazy { requireSystemService<AudioManager>() }

    private val bandConfigs by lazy { glyph.config.visualizerBands }
    private val bands by lazy { List(bandConfigs.size) { BandState() } }
    private val measurement = Visualizer.MeasurementPeakRms()
    private var isSignalPresent = false
    private var signalConfirmationFrames = 0
    private var wasSilent = true

    /** The session media is playing on, as it is now and each time it changes, or null. */
    private val activeSessionId: Flow<Int?> =
        callbackFlow {
                val callback =
                    object : AudioManager.AudioPlaybackCallback() {
                        override fun onPlaybackConfigChanged(
                            configs: MutableList<AudioPlaybackConfiguration>
                        ) {
                            trySend(configs.activeMediaSessionId())
                        }
                    }

                audioManager.registerAudioPlaybackCallback(callback, null)
                trySend(audioManager.activePlaybackConfigurations.activeMediaSessionId())
                awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
            }
            .distinctUntilChanged()

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            // A new session cancels the one before it, which lets go of its visualizer.
            activeSessionId.collectLatest { sessionId -> sessionId?.let { visualize(it) } }
        }
    }

    /** The session media is playing on, or null where nothing is. */
    private fun List<AudioPlaybackConfiguration>.activeMediaSessionId(): Int? =
        firstOrNull { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA && it.isActive }
            ?.sessionId

    /** Lights the strips to a session's output, until this is canceled or the session ends. */
    private suspend fun visualize(sessionId: Int) =
        withContext(Dispatchers.Default) {
            val visualizer = openVisualizer(sessionId) ?: return@withContext

            try {
                frames(visualizer).collect { fft -> processFft(visualizer, fft) }
            } finally {
                bands.forEach { it.reset() }
                isSignalPresent = false
                signalConfirmationFrames = 0
                wasSilent = true
                glyph.animator.endFrames(GlyphStream.VISUALIZER)

                try {
                    visualizer.enabled = false
                } catch (e: RuntimeException) {
                    // The audio server may already be gone.
                } finally {
                    visualizer.release()
                }
            }
        }

    /** A visualizer set up to read a session's fft, or null where that cannot be done. */
    private fun openVisualizer(sessionId: Int): Visualizer? {
        val visualizer =
            try {
                Visualizer(sessionId)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not listen to session $sessionId: $e")
                return null
            }

        return try {
            visualizer.apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            }
        } catch (e: RuntimeException) {
            // It holds an audio effect until released, so a failed setup must not keep it.
            visualizer.release()
            Log.w(TAG, "Could not set up listening to session $sessionId: $e")
            null
        }
    }

    /** The fft of each capture, newest first where they come faster than they are read. */
    private fun frames(visualizer: Visualizer): Flow<ByteArray> =
        callbackFlow {
                visualizer.setServerDiedListener { close() }
                visualizer.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int,
                        ) {
                            // Only the fft is asked for.
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int,
                        ) {
                            // The capture is reused, so it is copied before it is read later.
                            trySend(fft.copyOf())
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true,
                )
                visualizer.enabled = true

                awaitClose {}
            }
            .conflate()

    private fun processFft(visualizer: Visualizer, fft: ByteArray) {
        val buckets = bucketMagnitudes(fft)
        val energies =
            DoubleArray(bandConfigs.size) { band ->
                val config = bandConfigs[band]
                buckets.slice(config.buckets).average()
            }

        if (!hasAudibleSignal(visualizer) || energies.all { it <= MIN_BAND_ENERGY }) {
            fadeToSilence()
            return
        }
        wasSilent = false

        bands.forEachIndexed { index, band -> band.update(energies[index], bandConfigs[index]) }

        writeFrame()
    }

    /** Whether something is playing, once a change has held for a few frames. */
    private fun hasAudibleSignal(visualizer: Visualizer): Boolean {
        if (visualizer.getMeasurementPeakRms(measurement) != Visualizer.SUCCESS) {
            return isSignalPresent
        }

        val threshold = if (isSignalPresent) SILENCE_RMS_MB else SIGNAL_RMS_MB
        val isDetected = measurement.mRms > threshold

        if (isDetected == isSignalPresent) {
            signalConfirmationFrames = 0
            return isSignalPresent
        }

        val needed = if (isDetected) SIGNAL_CONFIRMATION_FRAMES else SILENCE_CONFIRMATION_FRAMES
        if (++signalConfirmationFrames >= needed) {
            isSignalPresent = isDetected
            signalConfirmationFrames = 0
        }

        return isSignalPresent
    }

    /** Lets the bands decay rather than cutting them off at a track's end. */
    private fun fadeToSilence() {
        bands.forEachIndexed { i, band -> band.decayBrightness(bandConfigs[i]) }

        if (bands.all { it.brightness == 0.0 }) {
            if (!wasSilent) {
                bands.forEach { it.reset() }
                wasSilent = true
                writeFrame()
            }
            return
        }

        wasSilent = false
        writeFrame()
    }

    private fun writeFrame() {
        val maxBrightness = glyph.config.maxBrightness
        val levels = FloatArray(bands.size) { (bands[it].brightness * maxBrightness).toFloat() }
        glyph.animator.setFrame(GlyphStream.VISUALIZER, glyph.config.spreadBands(levels))
    }

    /** Sums the fft into [FFT_BUCKETS] magnitudes, above the noise floor. */
    private fun bucketMagnitudes(fft: ByteArray): DoubleArray {
        val bins = (fft.size / 2) - 1
        val binsPerBucket = bins / FFT_BUCKETS

        return DoubleArray(FFT_BUCKETS) { i ->
            var sum = 0f
            for (j in 0 until binsPerBucket) {
                val offset = (1 + (i * binsPerBucket) + j) * 2
                val real = fft[offset].toFloat()
                val imaginary = fft[offset + 1].toFloat()
                sum += (sqrt(real * real + imaginary * imaginary) - NOISE_FLOOR).coerceAtLeast(0f)
            }
            (sum / binsPerBucket / NORMALIZE_DIVISOR) * NORMALIZE_SCALE
        }
    }
}

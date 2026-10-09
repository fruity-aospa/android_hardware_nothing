/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.use
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import co.aospa.glyph.R
import co.aospa.glyph.system.GlyphLights
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.glyph
import java.io.IOException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows an animation on a drawing of the device. */
class GlyphAnimationPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {
    companion object {
        private const val TAG = "GlyphAnimationPreference"

        /** How dark an unlit glyph is drawn, and how much of the range is lit. */
        private const val DARK_ALPHA = 0.3f
        private const val LIT_FLOOR = 0.4f
        private const val LIT_RANGE = 0.6f
    }

    /** What the drawing is asked to show: an animation, played again after a pause, or nothing. */
    private data class Request(val name: String?, val shouldPlay: Boolean, val pause: Duration)

    private val config = context.glyph.config
    private val animations = context.glyph.animations

    private val root: View =
        LayoutInflater.from(context).inflate(R.layout.glyph_animation_preview, null, false)

    private val glyphs: List<ImageView> by lazy {
        context.resources.obtainTypedArray(R.array.config_glyphViews).use { views ->
            check(views.length() == config.glyphViewZones.size) {
                "${views.length()} glyph views but ${config.glyphViewZones.size} glyph view zones"
            }
            List(views.length()) { index ->
                root.findViewById<ImageView>(views.getResourceId(index, 0))
            }
        }
    }

    private val request =
        MutableStateFlow(Request(name = null, shouldPlay = false, pause = Duration.ZERO))

    /** Lasts while the preference is on screen, which is the only time it is drawn. */
    private var scope: CoroutineScope? = null

    init {
        layoutResource = R.layout.glyph_animation_preview_frame
        shouldDisableView = false

        context.obtainStyledAttributes(attrs, R.styleable.GlyphAnimationPreference).use { a ->
            val icon = a.getResourceId(R.styleable.GlyphAnimationPreference_badgeIcon, 0)
            if (icon != 0) {
                root.findViewById<ImageView>(R.id.preview_badge_icon).setImageResource(icon)
                root.findViewById<View>(R.id.preview_badge).isVisible = true
                root.findViewById<View>(R.id.preview_badge_balance).isVisible = true
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        holder.itemView.setOnClickListener { performClick(it) }
        holder.itemView.isFocusable = isSelectable
        holder.itemView.isClickable = isSelectable

        (root.parent as? ViewGroup)?.removeView(root)
        (holder.itemView as FrameLayout).apply {
            removeAllViews()
            addView(root)
        }
    }

    override fun onAttached() {
        super.onAttached()

        scope =
            MainScope().apply {
                // A new request replaces the one before it, mid-frame.
                launch { request.collectLatest { show(it) } }
            }
    }

    override fun onDetached() {
        super.onDetached()

        scope?.cancel()
        draw(null)
    }

    /**
     * Plays [name] on the drawing, or holds it dark when [shouldPlay] is false. The animation
     * repeats after [pauseBetween], and replaces whatever was showing.
     */
    fun updateAnimation(
        shouldPlay: Boolean,
        name: String? = request.value.name,
        pauseBetween: Duration = Duration.ZERO,
    ) {
        request.value = Request(name, shouldPlay, pauseBetween)
    }

    private suspend fun show(target: Request) {
        val name = target.name
        if (!target.shouldPlay || name == null) {
            draw(null)
            return
        }

        GlyphLog.d(TAG) { "Showing $name" }

        val frames =
            try {
                withContext(Dispatchers.IO) { animations.read(name).map(config::frameOf) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not show $name: $e")
                draw(null)
                return
            }

        if (null in frames) {
            Log.w(TAG, "$name has a line that does not fit ${config.device}")
            draw(null)
            return
        }

        while (true) {
            for (frame in frames) {
                draw(frame)
                delay(GlyphLights.FRAME_PERIOD)
            }
            delay(target.pause)
        }
    }

    /** Draws one frame, or darkens every glyph when [frame] is null. */
    private fun draw(frame: FloatArray?) {
        glyphs.forEachIndexed { index, view ->
            val level = frame?.let { config.glyphViewZones[index].maxOf(it::get) } ?: 0f
            view.alpha =
                if (level <= 0f) {
                    DARK_ALPHA
                } else {
                    LIT_FLOOR + LIT_RANGE * (level / config.maxBrightness.toFloat())
                }
        }
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.ui.preference

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.Preference
import co.aospa.glyph.R
import co.aospa.glyph.util.requireSystemService

/** Asks the system to add one of the app's tiles to Quick Settings, which shows its own prompt. */
class AddTilePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private val tileService: String
    private val tileLabel: Int
    private val tileIcon: Int

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.AddTilePreference)
        tileService = checkNotNull(attributes.getString(R.styleable.AddTilePreference_tileService))
        tileLabel = attributes.getResourceId(R.styleable.AddTilePreference_tileLabel, 0)
        tileIcon = attributes.getResourceId(R.styleable.AddTilePreference_tileIcon, 0)
        attributes.recycle()

        isPersistent = false
    }

    override fun onClick() {
        // A bitmap, because a resource icon can fail the permission check of the request.
        val icon = Icon.createWithBitmap(checkNotNull(context.getDrawable(tileIcon)).toBitmap())

        context.requireSystemService<StatusBarManager>().requestAddTileService(
            ComponentName.createRelative(context, tileService),
            context.getString(tileLabel),
            icon,
            context.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                Toast.makeText(context, R.string.tile_already_added, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

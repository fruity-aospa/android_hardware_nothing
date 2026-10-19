/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class DozeContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(
        authority: String,
        method: String,
        arg: String?,
        extras: Bundle?
    ): Bundle = call(method, arg, extras)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val bundle = Bundle()
        val ctx = context ?: return bundle

        val effectiveMethod = when {
            method.isNotEmpty() -> method
            arg != null -> Uri.parse(arg).pathSegments.firstOrNull().orEmpty()
            else -> ""
        }

        Log.d(TAG, "call: method=$method, effectiveMethod=$effectiveMethod, arg=$arg, extras=$extras")

        when (effectiveMethod) {
            METHOD_IS_CHECKED -> {
                val isChecked = Utils.isPocketEnabled(ctx)
                bundle.putBoolean(EXTRA_SWITCH_CHECKED_STATE, isChecked)
            }
            METHOD_ON_CHECKED_CHANGED -> {
                val checked = extras?.getBoolean(EXTRA_SWITCH_CHECKED_STATE, true) ?: true
                Utils.setPocketEnabled(ctx, checked)
                Utils.checkDozeService(ctx)
                notifyPocketChanged(ctx)
                bundle.putBoolean(EXTRA_SWITCH_SET_CHECKED_ERROR, false)
            }
        }

        return bundle
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "DozeContentProvider"

        const val AUTHORITY = "org.lineageos.settings.doze.provider"
        const val METHOD_IS_CHECKED = "isChecked"
        const val METHOD_ON_CHECKED_CHANGED = "onCheckedChanged"
        const val EXTRA_SWITCH_CHECKED_STATE = "checked_state"
        const val EXTRA_SWITCH_SET_CHECKED_ERROR = "set_checked_error"

        fun notifyPocketChanged(context: Context) {
            try {
                val uri = Uri.parse("content://$AUTHORITY/$METHOD_IS_CHECKED/${Utils.GESTURE_POCKET_KEY}")
                context.contentResolver.notifyChange(uri, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify pocket state change", e)
            }
        }
    }
}

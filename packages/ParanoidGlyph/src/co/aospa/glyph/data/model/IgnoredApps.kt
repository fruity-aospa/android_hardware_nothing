/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.model

/** Apps the notification screen does not offer. */
val APPS_TO_IGNORE =
    setOf(
        "android",
        "com.android.traceur",
        "com.google.android.setupwizard",
        "dev.kdrag0n.dyntheme.privileged.sys",
    )

/** Notifications the strips ignore, since something else already shows them. */
val NOTIFICATIONS_TO_IGNORE =
    setOf(
        "com.google.android.dialer:phone_incoming_call",
        "com.google.android.dialer:phone_ongoing_call",
        "com.android.systemui:BAT",
    )

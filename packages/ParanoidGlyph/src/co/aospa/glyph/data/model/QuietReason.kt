/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.model

/** Why the glyphs keep quiet. */
enum class QuietReason {
    /** They do not. */
    NONE,

    /** It is bedtime, or the hours the user chose. */
    SCHEDULE,

    /** Do Not Disturb is on. */
    DND,

    /** Battery Saver is on. */
    BATTERY_SAVER,

    /** The phone is in a pocket. */
    POCKET,
}

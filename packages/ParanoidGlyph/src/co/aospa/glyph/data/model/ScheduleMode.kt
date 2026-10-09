/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.model

/**
 * What decides when the glyphs keep quiet.
 *
 * @property value what the setting saves, which the settings screen lists the same way.
 */
enum class ScheduleMode(val value: String) {
    /** Nothing does. */
    NONE("none"),

    /** The system's Bedtime mode. */
    BEDTIME("bedtime"),

    /** Hours the user chose. */
    CUSTOM("custom");

    companion object {
        /** The mode saved as [value], or none if it is not one. */
        fun of(value: String?) = values().firstOrNull { it.value == value } ?: NONE
    }
}

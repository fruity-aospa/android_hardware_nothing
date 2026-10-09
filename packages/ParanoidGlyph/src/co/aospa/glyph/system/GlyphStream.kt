/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

/** What streams frames to the glyphs, the one shown first while several do. */
enum class GlyphStream {
    /** An app, through the glyph sdk. */
    CLIENT,

    /** The music visualizer. */
    VISUALIZER,
}

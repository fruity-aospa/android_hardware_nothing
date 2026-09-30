/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Brightness, as carried by the interface's colours.

/// Returns the level a colour asks for. The strips are white only, so the
/// brightest channel sets the level.
pub fn level(color: i32, max_brightness: u32) -> u32 {
    let (r, g, b) = ((color >> 16) & 0xff, (color >> 8) & 0xff, color & 0xff);
    r.max(g).max(b) as u32 * max_brightness / 255
}

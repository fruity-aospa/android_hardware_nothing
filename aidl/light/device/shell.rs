/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The color of a phone's shell, which sets how bright its strips may be.

use std::fs;

use log::warn;
use rustutils::android::system_properties;

/// A level for each color of shell.
#[derive(Clone, Copy)]
pub struct ShellLevel {
    pub black: u32,
    pub white: u32,
}

impl ShellLevel {
    /// The level for this shell.
    pub fn for_shell(self, shell: Shell) -> u32 {
        match shell {
            Shell::Black | Shell::Gray => self.black,
            Shell::White => self.white,
        }
    }
}

/// The color of a phone's shell. A white one diffuses more light, so its strips take less.
#[derive(Clone, Copy, PartialEq)]
pub enum Shell {
    Black,
    White,
    Gray,
}

impl Shell {
    /// The factory's record of the color.
    pub(crate) const RECORD: &'static str = "/mnt/vendor/persist/color";

    /// Where the color is published, for whatever reads it.
    const PROPERTY: &'static str = "persist.vendor.glyph.color";

    /// Reads the color, which is black where the record cannot be read.
    pub fn read() -> Shell {
        match fs::read_to_string(Self::RECORD) {
            Ok(color) => Self::parse(&color),
            Err(e) => {
                warn!("Failed to read the shell color: {e}");
                Shell::Black
            }
        }
    }

    /// Publishes the color, unless the property already says it.
    pub fn publish(self) {
        let value = self.name();
        let published = system_properties::read(Self::PROPERTY).unwrap_or_default();
        if published.as_deref() == Some(value) {
            return;
        }
        if let Err(e) = system_properties::write(Self::PROPERTY, value) {
            warn!("Failed to publish the shell color: {e}");
        }
    }

    fn name(self) -> &'static str {
        match self {
            Shell::Black => "black",
            Shell::Gray => "gray",
            Shell::White => "white",
        }
    }

    /// Only white is told apart; black and gray take the same levels.
    pub(crate) fn parse(color: &str) -> Shell {
        if color.to_ascii_lowercase().contains("white") {
            Shell::White
        } else if color.trim().to_ascii_uppercase().starts_with("GRAY") {
            Shell::Gray
        } else {
            Shell::Black
        }
    }
}

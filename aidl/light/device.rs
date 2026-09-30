/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The phone this is built for, and how it uses its glyph controller.

mod camera;
mod layout;
mod shell;

// Selected by the nothing_glyph device soong config.
#[cfg_attr(phone1, path = "device/phone1.rs")]
#[cfg_attr(phone2, path = "device/phone2.rs")]
#[cfg_attr(phone3a, path = "device/phone3a.rs")]
mod phone;

#[cfg(not(any(phone1, phone2, phone3a)))]
compile_error!("Set the nothing_glyph device soong config to a phone this service supports");

pub use camera::CameraCodes;
pub use layout::{Group, Layout, Segments};
pub use phone::DEVICE;
pub use shell::{Shell, ShellLevel};

use std::fs;
use std::io::Result;
use std::path::Path;

use log::warn;

use crate::controller::{Controller, Node};

/// The recording indicator, on an LED of its own that no frame reaches.
#[derive(Clone, Copy)]
pub struct IndicatorLed<Level = u32> {
    /// The LED it sits on.
    pub led: usize,

    /// The level it lights at.
    pub level: Level,
}

/// A phone: the controller it carries, and how it wires and uses it. Its levels are given for each
/// shell until it is fitted for its own.
pub struct Device<Level = u32> {
    pub controller: &'static Controller,

    /// The directory the driver registered its nodes in.
    dir: &'static str,

    /// The node that tells the driver the shell is black, where it takes one.
    shell_node: Option<&'static str>,

    /// Levels one frame carries.
    pub segments: usize,

    /// Highest level the nodes accept.
    pub max_brightness: Level,

    /// The recording indicator, where the phone has one.
    pub indicator: Option<IndicatorLed<Level>>,

    pub layout: Layout,

    /// What the camera app sends.
    pub camera: CameraCodes,
}

impl Device<ShellLevel> {
    /// Tells the driver the shell is black, where it takes the color. Stock only does so for a
    /// record of black, and leaves the driver as it is for any other.
    pub fn tell_shell(&self) {
        let Some(node) = self.shell_node else { return };
        let Ok(record) = fs::read_to_string(Shell::RECORD) else { return };
        if !record.trim().to_ascii_uppercase().starts_with("BLACK") {
            return;
        }

        if let Err(e) = fs::write(Path::new(self.dir).join(node), "BLACK") {
            warn!("Failed to tell the driver the shell color: {e}");
        }
    }

    /// This phone fitted for its shell.
    pub fn for_shell(&self, shell: Shell) -> Device {
        Device {
            controller: self.controller,
            dir: self.dir,
            shell_node: self.shell_node,
            segments: self.segments,
            max_brightness: self.max_brightness.for_shell(shell),
            indicator: self.indicator.map(|indicator| IndicatorLed {
                led: indicator.led,
                level: indicator.level.for_shell(shell),
            }),
            layout: self.layout,
            camera: CameraCodes {
                // The selected stock recording effect uses raw 140 for GRAY, 255 otherwise.
                recording_level: if self.camera.recording_segment.is_some() && shell == Shell::Gray {
                    140
                } else { self.camera.recording_level },
                ..self.camera
            },
        }
    }
}

impl Device {
    /// Draws one frame: every segment's level, in order.
    pub fn write_frame(&self, levels: &[u32]) -> Result<()> {
        if levels.len() != self.segments {
            return Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, "Wrong glyph frame length"));
        }
        let levels = levels.iter().map(|&level| self.clamp(level)).collect::<Vec<_>>();
        let value = levels.iter().map(u32::to_string).collect::<Vec<_>>().join(" ");
        self.controller.write(self.dir, Node::Frame, &value, levels.iter().any(|&level| level > 0))
    }

    /// Draws every segment at the same level, in one write.
    pub fn write_all(&self, level: u32) -> Result<()> {
        let level = self.clamp(level);
        self.controller.write(self.dir, Node::All, &level.to_string(), level > 0)
    }

    /// Lights one LED on its own, outside any frame.
    pub fn write_led(&self, led: usize, level: u32) -> Result<()> {
        self.controller.write(self.dir, Node::Single, &format!("{led} {level}"), level > 0)
    }

    fn clamp(&self, level: u32) -> u32 {
        level.min(self.max_brightness)
    }
}

#[cfg(test)]
impl Device {
    pub(crate) fn for_test(dir: &'static str, shell: Shell) -> &'static Device {
        let mut device = DEVICE.for_shell(shell);
        device.dir = dir;
        device.controller = Controller::for_test();
        Box::leak(Box::new(device))
    }
}

/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! What the camera app sends to its fill light, which a device may change.

/// What a fill command does to the strips.
#[derive(Clone, Copy)]
pub struct Fill {
    /// Whether the strips light or go dark.
    pub lit: bool,

    /// Whether only the camera glyph is drawn, rather than every segment.
    pub camera_only: bool,
}

/// The value the camera app sends for each command.
#[derive(Clone, Copy)]
pub struct CameraCodes {
    pub video_on: i32,
    pub video_off: i32,
    pub fill_on: i32,
    pub fill_off: i32,
    pub countdown_on: i32,
    pub countdown_off: i32,

    /// Sent when the camera session ends.
    pub session_cleared: i32,

    /// Recording LED sharing a strip channel; drawn by the same player.
    pub recording_segment: Option<usize>,
    pub recording_level: u32,
    pub hal_countdown: bool,
}

impl CameraCodes {
    /// What the camera app sends unless a device says otherwise.
    pub const DEFAULT: Self = Self {
        video_on: 1,
        video_off: 0,
        fill_on: 11,
        fill_off: 10,
        countdown_on: 7,
        countdown_off: 6,
        session_cleared: 0,
        recording_segment: None,
        recording_level: 255,
        hal_countdown: false,
    };

    /// Returns whether a video command lights the indicator, or `None` if it is not one.
    pub fn video(&self, command: i32) -> Option<bool> {
        [(self.video_on, true), (self.video_off, false)]
            .into_iter()
            .find_map(|(code, lit)| (code == command).then_some(lit))
    }

    /// Returns what a fill command does, or `None` if it is not one.
    pub fn fill(&self, command: i32) -> Option<Fill> {
        if self.hal_countdown && command == 11 {
            return Some(Fill {
                lit: true,
                camera_only: false,
            });
        }
        if self.hal_countdown && [3, 6, 10, 14, 42].contains(&command) {
            return Some(Fill {
                lit: false,
                camera_only: false,
            });
        }
        [
            (
                self.fill_on,
                Fill {
                    lit: true,
                    camera_only: false,
                },
            ),
            (
                self.fill_off,
                Fill {
                    lit: false,
                    camera_only: false,
                },
            ),
            (
                self.countdown_on,
                Fill {
                    lit: true,
                    camera_only: true,
                },
            ),
            (
                self.countdown_off,
                Fill {
                    lit: false,
                    camera_only: true,
                },
            ),
            (
                self.session_cleared,
                Fill {
                    lit: false,
                    camera_only: false,
                },
            ),
        ]
        .into_iter()
        .find_map(|(code, fill)| (code == command).then_some(fill))
    }
}

impl CameraCodes {
    /// Selected stock HAL samples, played at the Linux 6.1 driver's clock.
    pub fn countdown(&self, command: i32, level: u32) -> Option<crate::player::Animation> {
        if !self.hal_countdown {
            return None;
        }
        let samples: &[u8] = match command {
            15 => include_bytes!("phone3a/countdown-3s.bin"),
            43 => include_bytes!("phone3a/countdown-10s.bin"),
            _ => return None,
        };
        let tracks = (0..36)
            .map(|segment| crate::player::Track {
                segment,
                curve: std::sync::Arc::new(
                    crate::player::Curve::new(
                        samples
                            .chunks_exact(36)
                            .map(|frame| (1, frame[segment] as u32 * level / 255)),
                        false,
                    )
                    .expect("Stock countdown tables contain complete frames"),
                ),
            })
            .collect();
        Some(crate::player::Animation {
            tracks,
            frame_period: std::time::Duration::from_micros(16_666),
            iterations: 1,
        })
    }
}

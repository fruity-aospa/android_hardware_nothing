/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! An animation, as the player walks it.

use std::sync::Arc;
use std::time::Duration;

use super::Curve;

/// One segment's level over the animation. Segments of a light share its curve.
pub struct Track {
    pub segment: usize,
    pub curve: Arc<Curve>,
}

/// A series of frames over any number of segments.
pub struct Animation {
    pub tracks: Vec<Track>,
    pub frame_period: Duration,
    /// 0 repeats until something else is played.
    pub iterations: i32,
}

impl Animation {
    /// The length of the longest track.
    pub fn frames(&self) -> u64 {
        self.tracks.iter().map(|t| t.curve.frames()).max().unwrap_or(0)
    }

    /// The levels every segment shows on a frame. A short track holds its
    /// last value, and a segment no track drives keeps its resting level.
    pub fn frame(&self, frame: u64, resting: &[u32]) -> Vec<u32> {
        let mut levels = resting.to_vec();
        for track in &self.tracks {
            if let Some(level) = levels.get_mut(track.segment) {
                *level = track.curve.level(frame);
            }
        }
        levels
    }
}

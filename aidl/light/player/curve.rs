/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! A light's level over time, read one frame at a time.

/// Control points joined into ramps. Only the ramps are kept, so a long effect costs no more
/// than a short one.
pub struct Curve {
    ramps: Vec<Ramp>,
    linear: bool,
}

struct Ramp {
    from: u32,
    to: u32,

    /// The frames this ramp covers: `start..end`.
    start: u64,
    end: u64,
}

impl Curve {
    /// A positive first control point starts from the last value the HAL displayed.
    pub fn with_start(points: &[(i32, u32)], linear: bool, start: u32) -> Option<Curve> {
        let initial = points.first()?;
        if initial.0 == 0 {
            Self::new(points.iter().copied(), linear)
        } else {
            Self::new(
                std::iter::once((0, start)).chain(points.iter().copied()),
                linear,
            )
        }
    }
    /// Joins control points, each a number of frames to spend reaching a level. A first point
    /// of zero frames is where the effect starts, and is drawn for a frame. A negative count is
    /// not a curve.
    pub fn new(points: impl IntoIterator<Item = (i32, u32)>, linear: bool) -> Option<Curve> {
        let mut points = points.into_iter().peekable();
        let mut from = points.peek()?.1;
        let mut ramps = Vec::new();
        let mut end = 0;

        for (frames, to) in points {
            let mut count = u64::try_from(frames).ok()?;
            if count == 0 && ramps.is_empty() {
                count = 1;
            }
            if count > 0 {
                ramps.push(Ramp {
                    from,
                    to,
                    start: end,
                    end: end + count,
                });
                end += count;
            }
            from = to;
        }

        (!ramps.is_empty()).then_some(Curve { ramps, linear })
    }

    /// How many frames the curve lasts.
    pub fn frames(&self) -> u64 {
        self.ramps.last().map_or(0, |ramp| ramp.end)
    }

    /// The level on a frame. Past the end, the last level holds.
    pub fn level(&self, frame: u64) -> u32 {
        let index = self.ramps.partition_point(|ramp| ramp.end <= frame);
        match self.ramps.get(index) {
            Some(ramp) => ramp.level(frame, self.linear),
            None => self.ramps.last().map_or(0, |ramp| ramp.to),
        }
    }
}

impl Ramp {
    fn level(&self, frame: u64, linear: bool) -> u32 {
        if !linear {
            // NONE holds the previous colour until this point's deadline.
            return if frame + 1 < self.end {
                self.from
            } else {
                self.to
            };
        }

        let t = (frame - self.start + 1) as f32 / (self.end - self.start) as f32;
        (self.from as f32 + (self.to as f32 - self.from as f32) * t) as u32
    }
}

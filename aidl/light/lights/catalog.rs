/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The lights this service serves, and what an id resolves to.

use android_hardware_light::aidl::android::hardware::light::LightType::LightType;

use crate::controller::Controller;
use crate::device::{Device, Group, Segments};

/// A kind of light, numbered by the id of its first.
#[derive(Clone, Copy)]
pub enum Kind {
    /// The recording indicator, on an LED of its own, where the phone has one.
    Video = 105,

    /// The camera fill, either the ring or every segment.
    Camera = 106,

    /// Every segment at once.
    All = 200,

    /// The glyphs, one light each. Ids up to 249 are theirs.
    Glyphs = 210,

    /// The volume meter, one light per step.
    Volume = 250,

    /// The battery meter, one light per step.
    Battery = 270,
}

/// Every kind, in the order `getLights` reports them.
pub const KINDS: [Kind; 6] =
    [Kind::All, Kind::Video, Kind::Camera, Kind::Glyphs, Kind::Volume, Kind::Battery];

impl Kind {
    /// The id of this kind's first light.
    pub const fn first_id(self) -> i32 {
        self as i32
    }

    /// What the interface calls this kind.
    pub const fn light_type(self) -> LightType {
        match self {
            Kind::Video | Kind::Camera => LightType::CAMERA,
            _ => LightType::APPLICATION,
        }
    }

    /// How often a client may redraw it.
    pub fn min_update_period_ms(self, controller: &Controller) -> i32 {
        match self {
            Kind::Glyphs | Kind::Volume | Kind::Battery => controller.min_update_period_millis,
            _ => 0,
        }
    }

    /// How many lights of this kind the phone has.
    pub fn count(self, device: &'static Device) -> usize {
        match self {
            Kind::Video => usize::from(device.indicator.is_some() || device.camera.recording_segment.is_some()),
            Kind::All | Kind::Camera => 1,
            Kind::Glyphs | Kind::Volume | Kind::Battery => {
                self.group(device).map_or(0, |group| group.len())
            }
        }
    }

    /// The segments behind each light of a group, or `None` for a single light.
    pub fn group(self, device: &'static Device) -> Option<Group> {
        match self {
            Kind::Glyphs => Some(device.layout.glyphs),
            Kind::Volume => Some(device.layout.volume),
            Kind::Battery => Some(device.layout.battery),
            Kind::All | Kind::Video | Kind::Camera => None,
        }
    }
}

/// What a light id turns out to be.
pub enum Target {
    /// The segments this light covers.
    Segments(Segments),

    /// A light of its own, which no segments stand for.
    Single(Kind),
}

/// Resolves a light id, or `None` if the phone has no such light.
pub fn resolve(device: &'static Device, id: i32) -> Option<Target> {
    KINDS.into_iter().find_map(|kind| {
        let index = usize::try_from(id.checked_sub(kind.first_id())?).ok()?;
        if index >= kind.count(device) {
            return None;
        }

        match kind.group(device) {
            Some(group) => group.get(index).map(Target::Segments),
            None => Some(Target::Single(kind)),
        }
    })
}

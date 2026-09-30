/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The lights interface over the glyph strips.

mod catalog;
mod color;
mod effect;

use std::time::Duration;

use android_hardware_light::aidl::android::hardware::light::{
    HwLight::HwLight, HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
};
use binder::{ExceptionCode, Interface, Status};
use log::{error, warn};

use crate::device::Device;
use crate::indicator::Indicator;
use crate::player::{LightEffect, Player};
use catalog::{Kind, Target};

pub struct GlyphLights {
    device: &'static Device,
    player: Player,
    indicator: Indicator,
}

impl Interface for GlyphLights {}

impl GlyphLights {
    pub fn new(device: &'static Device) -> Self {
        GlyphLights {
            device,
            player: Player::new(device),
            indicator: Indicator::new(device),
        }
    }
}

impl ILights for GlyphLights {
    fn setLightState(&self, id: i32, state: &HwLightState) -> binder::Result<()> {
        let Some(target) = catalog::resolve(self.device, id) else {
            return Err(Status::new_exception(ExceptionCode::UNSUPPORTED_OPERATION, None));
        };
        let max_brightness = self.device.max_brightness;
        let level = color::level(state.color, max_brightness);

        match target {
            Target::Single(Kind::All) => self.player.set_all(level).map_err(|e| {
                error!("Failed to set light {id}: {e}");
                Status::new_exception(ExceptionCode::ILLEGAL_STATE, None)
            })?,
            Target::Segments(segments) => self.player.set_segments(segments, level),
            Target::Single(Kind::Video) => {
                let Some(lit) = self.device.camera.video(state.color) else {
                    warn!("Ignoring video command {}", state.color);
                    return Ok(());
                };

                if self.device.camera.recording_segment.is_some() {
                    self.player.set_recording(lit);
                } else {
                    self.indicator.set(lit);
                }
            }
            Target::Single(Kind::Camera) => {
                if let Some(animation) = self.device.camera.countdown(state.color, max_brightness) {
                    self.player.play(animation);
                    return Ok(());
                }
                let Some(fill) = self.device.camera.fill(state.color) else {
                    warn!("Ignoring camera command {}", state.color);
                    return Ok(());
                };

                let level = if fill.lit { max_brightness } else { 0 };
                if fill.camera_only {
                    self.player.set_segments(self.device.layout.camera, level);
                } else {
                    self.player.set_all(level).map_err(|e| {
                        error!("Failed to set the camera fill: {e}");
                        Status::new_exception(ExceptionCode::ILLEGAL_STATE, None)
                    })?;
                }
            }
            Target::Single(_) => {
                return Err(Status::new_exception(
                    ExceptionCode::UNSUPPORTED_OPERATION,
                    None,
                ))
            }
        }
        Ok(())
    }

    fn setLightEffects(&self, effects: &[HwLightEffect]) -> binder::Result<()> {
        if effects.is_empty() {
            self.player.stop();
            return Ok(());
        }

        let mut requests = Vec::with_capacity(effects.len());
        for effect in effects {
            let Some(Target::Segments(segments)) = catalog::resolve(self.device, effect.lightId) else {
                return Err(Status::new_exception(ExceptionCode::UNSUPPORTED_OPERATION, None));
            };
            if effect.iterations < 0
                || effect.framePeriodMillis <= 0
                || effect.framePeriodMillis < self.device.controller.min_update_period_millis
            {
                return Err(Status::new_exception(ExceptionCode::ILLEGAL_ARGUMENT, None));
            }
            let Some(points) = effect::controls(effect, self.device.max_brightness) else {
                return Err(Status::new_exception(ExceptionCode::ILLEGAL_ARGUMENT, None));
            };
            let frame_period = Duration::from_millis(effect.framePeriodMillis as u64);
            let frames = points
                .iter()
                .try_fold(0_u128, |sum, (frames, _)| sum.checked_add(*frames as u128));
            let duration = frames.and_then(|frames| {
                crate::player::frame_duration(
                    frame_period,
                    (frames + 1) * effect.iterations.max(1) as u128 - 1,
                )
            });
            if duration
                .and_then(|duration| std::time::Instant::now().checked_add(duration))
                .is_none()
            {
                return Err(Status::new_exception(ExceptionCode::ILLEGAL_ARGUMENT, None));
            }
            requests.push(LightEffect {
                id: effect.lightId,
                segments: segments.to_vec(),
                points,
                linear: matches!(effect.interpolationType,
                    android_hardware_light::aidl::android::hardware::light::InterpolationType::InterpolationType::LINEAR),
                frame_period,
                iterations: effect.iterations,
                preemptive: effect.preemptive,
            });
        }
        // Validation is atomic; no previous light state changes before every request is valid.
        self.player.effects(requests);
        Ok(())
    }

    fn getLights(&self) -> binder::Result<Vec<HwLight>> {
        let mut lights = Vec::new();

        // LightsManager only sees the non-system types.
        for kind in catalog::KINDS {
            for index in 0..kind.count(self.device) {
                lights.push(HwLight {
                    id: kind.first_id() + index as i32,
                    ordinal: lights.len() as i32,
                    r#type: kind.light_type(),
                    minUpdatePeriodMillis: kind.min_update_period_ms(self.device.controller),
                });
            }
        }

        Ok(lights)
    }
}
